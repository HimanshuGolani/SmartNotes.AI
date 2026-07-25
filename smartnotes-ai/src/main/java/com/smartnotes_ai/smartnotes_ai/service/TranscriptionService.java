package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes_ai.smartnotes_ai.dto.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${smartnotes.whisper.url}")
    private String whisperUrl;

    /** Prevents multiple concurrent startup attempts. */
    private final AtomicBoolean whisperStarting = new AtomicBoolean(false);

    /** Max time we'll wait for Whisper to come up (10 minutes). */
    private static final int MAX_HEALTH_CHECK_ATTEMPTS = 120;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5000;

    // ====================================================================
    //                          PUBLIC API
    // ====================================================================

    public List<TranscriptSegment> transcribe(Path audioFile) {
        try {
            // Step 1: Make sure Whisper is up (auto-starts if not)
            ensureWhisperRunning();

            // Step 2: POST audio file to Whisper
            log.info("Sending audio | file={}", audioFile.getFileName());

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));

            String response = restClient.post()
                    .uri(whisperUrl + "/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            // Step 3: Parse segments
            JsonNode root = mapper.readTree(response);
            JsonNode segs = root.path("segments");

            List<TranscriptSegment> result = new ArrayList<>();
            for (JsonNode s : segs) {
                result.add(new TranscriptSegment(
                        s.path("start").asDouble(),
                        s.path("end").asDouble(),
                        s.path("text").asText().trim()
                ));
            }

            log.info("Transcription complete | segments={}", result.size());
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Transcription failed: " + e.getMessage(), e);
        }
    }

    // ====================================================================
    //                       HEALTH CHECK LOGIC
    // ====================================================================

    /**
     * Performs a strict HTTP health check.
     * Returns true ONLY if Whisper responds with HTTP 200.
     * Any other response (4xx, 5xx, connection refused, timeout) returns false.
     */
    public boolean isWhisperHealthy() {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(whisperUrl + "/health")
                    .retrieve()
                    .toEntity(String.class);

            HttpStatusCode status = response.getStatusCode();

            if (status.value() == 200) {
                log.debug("Whisper health check: HTTP 200 ✓");
                return true;
            } else {
                log.warn("Whisper health check returned non-200 status: {}", status.value());
                return false;
            }

        } catch (ResourceAccessException e) {
            // Connection refused, timeout, host unreachable, etc.
            log.debug("Whisper unreachable: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            // 4xx, 5xx, or any other error
            log.debug("Whisper health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensures Whisper is healthy.
     * If health check returns 200 → returns immediately (fast path).
     * If health check fails → launches the .bat script and polls until healthy.
     */
    public void ensureWhisperRunning() {
        // Fast path: if it's already healthy (HTTP 200), proceed immediately
        if (isWhisperHealthy()) {
            log.info("Whisper healthy | url={}", whisperUrl);
            return;
        }

        log.warn("Whisper unhealthy | triggering auto-start url={}", whisperUrl);

        // Atomic flag prevents two parallel requests from both spawning the .bat
        if (whisperStarting.compareAndSet(false, true)) {
            try {
                startWhisper();
            } catch (Exception e) {
                whisperStarting.set(false);
                throw e;
            }
        } else {
            log.info("Another thread is already starting Whisper. Waiting...");
        }

        // Poll health endpoint until 200 OK or timeout
        pollUntilHealthy();
    }

    /**
     * Polls /health every 5s until HTTP 200 OR max attempts reached.
     */
    private void pollUntilHealthy() {
        int attempts = 0;
        long startTime = System.currentTimeMillis();

        try {
            while (!isWhisperHealthy()) {
                attempts++;

                if (attempts > MAX_HEALTH_CHECK_ATTEMPTS) {
                    whisperStarting.set(false);
                    throw new RuntimeException(String.format(
                            "Whisper failed to become healthy within %d minutes (%d attempts)",
                            (MAX_HEALTH_CHECK_ATTEMPTS * HEALTH_CHECK_INTERVAL_MS) / 60000,
                            attempts
                    ));
                }

                log.info("Waiting for Whisper to start... attempt {}/{} ({}s elapsed)",
                        attempts,
                        MAX_HEALTH_CHECK_ATTEMPTS,
                        Duration.ofMillis(System.currentTimeMillis() - startTime).toSeconds());

                try {
                    Thread.sleep(HEALTH_CHECK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    whisperStarting.set(false);
                    throw new RuntimeException("Interrupted while waiting for Whisper", e);
                }
            }

            long totalSeconds = (System.currentTimeMillis() - startTime) / 1000;
            log.info("Whisper ready | elapsed={}s attempts={}", totalSeconds, attempts);

        } finally {
            whisperStarting.set(false);
        }
    }

    // ====================================================================
    //                       WHISPER STARTUP (.bat)
    // ====================================================================

    /**
     * Launches start-whisper.bat in a NEW visible terminal window.
     * Synchronized to prevent double-launch race conditions.
     */
    public synchronized void startWhisper() {
        // Double-check after acquiring lock — another thread may have already started it
        if (isWhisperHealthy()) {
            log.info("Whisper became healthy while waiting for lock. No restart needed.");
            return;
        }

        try {
            String userHome = System.getProperty("user.home");
            File workingDir = new File(userHome + File.separator + "yt-notes-system");

            if (!workingDir.exists()) {
                throw new RuntimeException(
                        "Whisper working directory not found: " + workingDir.getAbsolutePath()
                );
            }

            File batFile = new File(workingDir, "start-whisper.bat");
            if (!batFile.exists()) {
                throw new RuntimeException(
                        "start-whisper.bat not found at: " + batFile.getAbsolutePath()
                );
            }

            log.info("Launching Whisper from: {}", workingDir.getAbsolutePath());
            log.info("Executing: {}", batFile.getAbsolutePath());

            // Open a new visible terminal window so user can see Whisper logs
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "start",
                    "\"Whisper Server\"",   // window title
                    "cmd.exe",
                    "/k",                   // keep window open after .bat finishes
                    "start-whisper.bat"
            );

            processBuilder.directory(workingDir);
            processBuilder.inheritIO();   // optional: also pipe to Spring console
            processBuilder.start();

            log.info("✓ Whisper terminal launched. Polling for health...");

            // Give the .bat a head start before we begin polling
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (IOException e) {
            whisperStarting.set(false);
            throw new RuntimeException("Failed to launch Whisper .bat: " + e.getMessage(), e);
        }
    }

    // ====================================================================
    //              LEGACY METHOD ALIAS (backward compat)
    // ====================================================================

    /** @deprecated use {@link #ensureWhisperRunning()} */
    @Deprecated
    public void waitForWhisper() {
        ensureWhisperRunning();
    }
}