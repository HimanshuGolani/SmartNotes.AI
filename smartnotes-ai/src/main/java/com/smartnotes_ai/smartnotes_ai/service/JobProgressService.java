package com.smartnotes_ai.smartnotes_ai.service;

import com.smartnotes_ai.smartnotes_ai.dto.NotesResponse;
import com.smartnotes_ai.smartnotes_ai.exception.ErrorCode;
import com.smartnotes_ai.smartnotes_ai.exception.SmartNotesException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages async processing jobs and their SSE progress streams.
 *
 * Events are buffered so clients connecting after processing has already started
 * receive all past progress on subscribe (no events are lost to race conditions).
 *
 * A heartbeat "ping" event is sent every 15 s to keep idle connections alive through
 * proxies and load-balancers that drop quiet long-lived connections.
 */
@Slf4j
@Service
public class JobProgressService {

    private static final long SSE_TIMEOUT_MS = 900_000L; // 15 minutes
    private static final long HEARTBEAT_INTERVAL_SEC = 15L;
    private static final long DEFERRED_COMPLETE_MS = 150L; // see attach() for rationale

    private final Map<String, ProcessingJob> jobs = new ConcurrentHashMap<>();

    /** Single-threaded executor for heartbeats and deferred completes. */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-scheduler");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public String createJob() {
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ProcessingJob job = new ProcessingJob(jobId);
        jobs.put(jobId, job);
        // Buffer an immediate "queued" event so the client sees feedback on first connect
        job.publish("progress", Map.of("stage", 0, "total", 8, "message", "Queued…", "percent", 0));
        log.info("Job created | jobId={}", jobId);
        return jobId;
    }

    /**
     * Returns an SseEmitter for the given job.
     * All buffered events are replayed immediately so late subscribers miss nothing.
     */
    public SseEmitter subscribe(String jobId) {
        ProcessingJob job = jobs.get(jobId);
        if (job == null) {
            throw new SmartNotesException(ErrorCode.JOB_NOT_FOUND, "Job not found: " + jobId);
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> log.debug("SSE stream completed | jobId={}", jobId));
        emitter.onTimeout(() -> log.warn("SSE stream timed out | jobId={}", jobId));
        job.attach(emitter, scheduler);
        return emitter;
    }

    public void sendProgress(String jobId, int stage, int total, String message) {
        ProcessingJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("sendProgress called for unknown job | jobId={}", jobId);
            return;
        }
        Map<String, Object> data = Map.of(
                "stage", stage,
                "total", total,
                "message", message,
                "percent", total > 0 ? (stage * 100) / total : 0
        );
        job.publish("progress", data);
    }

    public void complete(String jobId, NotesResponse result) {
        ProcessingJob job = jobs.get(jobId);
        if (job == null) return;
        job.setResult(result);
        job.publish("complete", Map.of("jobId", jobId));
        job.finish();
        log.info("Job completed | jobId={}", jobId);
    }

    public void fail(String jobId, String message, ErrorCode code) {
        ProcessingJob job = jobs.get(jobId);
        if (job == null) return;
        job.publish("error", Map.of("message", message, "code", code.name()));
        job.finish();
        log.error("Job failed | jobId={} code={} message={}", jobId, code, message);
    }

    public Optional<NotesResponse> getResult(String jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(ProcessingJob::getResult);
    }

    public boolean exists(String jobId) {
        return jobs.containsKey(jobId);
    }

    // ── Inner types ──────────────────────────────────────────────────────────

    private static class ProcessingJob {

        final String jobId;
        private final List<SseEvent> buffer = new CopyOnWriteArrayList<>();
        private volatile SseEmitter emitter;
        private volatile NotesResponse result;
        private volatile boolean done = false;
        private ScheduledFuture<?> heartbeat;

        ProcessingJob(String jobId) {
            this.jobId = jobId;
        }

        /**
         * Attaches a new emitter and replays all buffered events immediately.
         *
         * When the job is already finished, {@code emitter.complete()} is scheduled
         * asynchronously (150 ms delay) rather than called inline. This prevents a
         * Spring-MVC async-dispatch race: if we call complete() before Spring has
         * committed the response headers, the stream closes before the browser has
         * established the SSE connection and events are lost.
         */
        synchronized void attach(SseEmitter newEmitter, ScheduledExecutorService sched) {
            this.emitter = newEmitter;

            // Replay all past events so a late-connecting client misses nothing
            for (SseEvent event : buffer) {
                try {
                    newEmitter.send(SseEmitter.event().name(event.name()).data(event.data()));
                } catch (Exception e) {
                    this.emitter = null;
                    return;
                }
            }

            if (done) {
                // Defer close: give Spring MVC ~150 ms to commit the response before we
                // complete the emitter, so the browser actually receives the buffered events.
                final SseEmitter toClose = newEmitter;
                sched.schedule(() -> {
                    try { toClose.complete(); } catch (Exception ignored) {}
                }, JobProgressService.DEFERRED_COMPLETE_MS, TimeUnit.MILLISECONDS);
            } else {
                // Schedule a periodic heartbeat to keep the connection alive through proxies
                if (heartbeat == null || heartbeat.isDone()) {
                    heartbeat = sched.scheduleAtFixedRate(
                            this::sendHeartbeat,
                            HEARTBEAT_INTERVAL_SEC,
                            HEARTBEAT_INTERVAL_SEC,
                            TimeUnit.SECONDS);
                }
            }
        }

        private void sendHeartbeat() {
            synchronized (this) {
                if (done || emitter == null) return;
                try {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("alive", true)));
                } catch (Exception e) {
                    emitter = null;
                }
            }
        }

        /** Adds the event to the buffer and pushes it to the live emitter if connected. */
        synchronized void publish(String eventName, Object data) {
            buffer.add(new SseEvent(eventName, data));
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (Exception e) {
                    emitter = null; // client disconnected
                }
            }
        }

        synchronized void finish() {
            done = true;
            if (heartbeat != null) {
                heartbeat.cancel(false);
                heartbeat = null;
            }
            if (emitter != null) {
                try { emitter.complete(); } catch (Exception ignored) {}
                emitter = null;
            }
        }

        NotesResponse getResult() { return result; }
        void setResult(NotesResponse r) { this.result = r; }

        record SseEvent(String name, Object data) {}
    }
}
