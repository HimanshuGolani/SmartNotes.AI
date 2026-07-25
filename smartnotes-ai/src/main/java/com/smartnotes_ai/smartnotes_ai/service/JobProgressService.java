package com.smartnotes_ai.smartnotes_ai.service;

import com.smartnotes_ai.smartnotes_ai.dto.NotesResponse;
import com.smartnotes_ai.smartnotes_ai.exception.ErrorCode;
import com.smartnotes_ai.smartnotes_ai.exception.SmartNotesException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages async processing jobs and their SSE progress streams.
 *
 * Events are buffered so clients connecting after processing has already started
 * receive all past progress on subscribe (no events are lost to race conditions).
 */
@Slf4j
@Service
public class JobProgressService {

    private static final long SSE_TIMEOUT_MS = 900_000L; // 15 minutes

    private final Map<String, ProcessingJob> jobs = new ConcurrentHashMap<>();

    // ── Public API ───────────────────────────────────────────────────────────

    public String createJob() {
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jobs.put(jobId, new ProcessingJob(jobId));
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
        job.attach(emitter);
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

        ProcessingJob(String jobId) {
            this.jobId = jobId;
        }

        /**
         * Attaches a new emitter and replays all buffered events immediately.
         * If the job is already finished, the emitter is completed right away.
         */
        synchronized void attach(SseEmitter newEmitter) {
            this.emitter = newEmitter;
            for (SseEvent event : buffer) {
                try {
                    newEmitter.send(SseEmitter.event().name(event.name()).data(event.data()));
                } catch (Exception e) {
                    this.emitter = null;
                    return;
                }
            }
            if (done) {
                try { newEmitter.complete(); } catch (Exception ignored) {}
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
