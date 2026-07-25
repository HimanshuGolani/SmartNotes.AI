package com.smartnotes_ai.smartnotes_ai.service;

import com.smartnotes_ai.smartnotes_ai.dto.*;
import com.smartnotes_ai.smartnotes_ai.exception.ErrorCode;
import com.smartnotes_ai.smartnotes_ai.exception.SmartNotesException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotesOrchestrationService {

    private final YouTubeDownloadService downloadService;
    private final FrameExtractionService frameService;
    private final TranscriptionService transcriptionService;
    private final AlignmentService alignmentService;
    private final VisionService visionService;
    private final NotesGenerationService notesService;
    private final PdfExportService pdfExportService;
    private final ExcalidrawMcpService excalidrawService;
    private final SmartFrameSelector frameSelector;
    private final JobProgressService jobProgressService;

    @Value("${smartnotes.workspace}")
    private String workspace;

    private static final int TOTAL_STAGES = 8;
    private static final Pattern YT_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:[^/\\n\\s]+/\\S+/|(?:v|e(?:mbed)?|shorts)/|\\S*?[?&]v=)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    // ── Async entry point ────────────────────────────────────────────────────

    @Async("notesProcessingExecutor")
    public void processAsync(VideoRequest request, String jobId) {
        String videoId = extractVideoId(request.getYoutubeUrl());
        MDC.put("jobId", jobId);
        MDC.put("videoId", videoId != null ? videoId : "unknown");
        try {
            NotesResponse result = runPipeline(request, jobId);
            jobProgressService.complete(jobId, result);
        } catch (SmartNotesException e) {
            log.error("Pipeline failed | code={} message={}", e.getCode(), e.getMessage());
            jobProgressService.fail(jobId, e.getMessage(), e.getCode());
        } catch (Exception e) {
            log.error("Unexpected pipeline failure | message={}", e.getMessage(), e);
            jobProgressService.fail(jobId, "Unexpected error: " + e.getMessage(), ErrorCode.INVALID_REQUEST);
        } finally {
            MDC.clear();
        }
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────

    private NotesResponse runPipeline(VideoRequest request, String jobId) {
        long start = System.currentTimeMillis();

        // Stage 0 — Cache check
        progress(jobId, 0, "Checking cache...");
        String videoId = extractVideoId(request.getYoutubeUrl());
        if (videoId != null && !request.isForceRefresh()) {
            NotesResponse cached = tryReturnCached(videoId, request, start);
            if (cached != null) {
                log.info("Cache HIT | videoId={} elapsed={}ms", videoId, elapsed(start));
                return cached;
            }
            log.info("Cache MISS | videoId={}", videoId);
        }

        // Stage 1 — Download
        progress(jobId, 1, "Downloading video & metadata...");
        VideoMetadata meta = downloadVideo(request.getYoutubeUrl());

        // Post-download cache re-check (handles canonical videoId resolution)
        if (!request.isForceRefresh() && (videoId == null || !videoId.equals(meta.getVideoId()))) {
            NotesResponse cached = tryReturnCached(meta.getVideoId(), request, start);
            if (cached != null) {
                cached.setTitle(meta.getTitle());
                return cached;
            }
        }

        // Stage 2 — Frames
        int interval = resolveFrameInterval(request.getFrameIntervalSeconds(), meta.getDuration());
        progress(jobId, 2, "Extracting frames (interval=" + interval + "s)...");
        List<Path> frames = extractFrames(meta, interval);

        // Stage 3 — Transcribe
        progress(jobId, 3, "Transcribing audio...");
        List<TranscriptSegment> segments = transcribeAudio(meta.getAudioFile());

        // Stage 4 — Align
        progress(jobId, 4, "Aligning transcript with video context...");
        String alignedContext = alignTranscript(segments, meta.getDescription());

        // Stage 5 — Generate topics
        progress(jobId, 5, "Generating topic notes with AI...");
        List<TopicSection> topics = generateTopics(segments, alignedContext, meta.getTitle());

        // Stage 6 — Caption frames
        progress(jobId, 6, "Analysing key frames with vision AI...");
        captionFrames(topics, frames, meta.getDuration(), interval);

        String overallSummary = notesService.overallSummary(topics, meta.getTitle());

        // Stage 7 — Export
        progress(jobId, 7, "Exporting PDF & mind-map...");
        String pdfPath = exportPdf(request, meta, overallSummary, topics);
        String excaliPath = exportExcalidraw(request, meta, overallSummary, topics);

        log.info("Pipeline complete | videoId={} topics={} elapsed={}ms",
                meta.getVideoId(), topics.size(), elapsed(start));

        return NotesResponse.builder()
                .videoId(meta.getVideoId())
                .title(meta.getTitle())
                .overallSummary(overallSummary)
                .topics(topics)
                .pdfPath(pdfPath)
                .excalidrawPath(excaliPath)
                .processingTimeMs(elapsed(start))
                .cached(false)
                .build();
    }

    // ── Stage helpers ────────────────────────────────────────────────────────

    private VideoMetadata downloadVideo(String url) {
        try {
            VideoMetadata meta = downloadService.download(url);
            log.info("Download complete | videoId={} title={} duration={}s",
                    meta.getVideoId(), meta.getTitle(), (int) meta.getDuration());
            return meta;
        } catch (Exception e) {
            throw new SmartNotesException(ErrorCode.VIDEO_DOWNLOAD_FAILED,
                    "Failed to download video: " + e.getMessage(), e);
        }
    }

    private List<Path> extractFrames(VideoMetadata meta, int interval) {
        try {
            List<Path> frames = frameService.extractFrames(meta.getVideoFile(), meta.getVideoId(), interval);
            meta.setFrameFiles(frames);
            log.info("Frames extracted | count={} interval={}s videoId={}", frames.size(), interval, meta.getVideoId());
            return frames;
        } catch (Exception e) {
            throw new SmartNotesException(ErrorCode.FRAME_EXTRACTION_FAILED,
                    "Frame extraction failed: " + e.getMessage(), e);
        }
    }

    private List<TranscriptSegment> transcribeAudio(Path audioFile) {
        try {
            List<TranscriptSegment> segments = transcriptionService.transcribe(audioFile);
            log.info("Transcription complete | segments={}", segments.size());
            return segments;
        } catch (Exception e) {
            throw new SmartNotesException(ErrorCode.TRANSCRIPTION_FAILED,
                    "Transcription failed: " + e.getMessage(), e);
        }
    }

    private String alignTranscript(List<TranscriptSegment> segments, String description) {
        try {
            return alignmentService.align(segments, description != null ? description : "");
        } catch (Exception e) {
            log.warn("Alignment failed — using raw transcript | error={}", e.getMessage());
            return segments.stream().map(TranscriptSegment::getText).collect(Collectors.joining(" "));
        }
    }

    private List<TopicSection> generateTopics(List<TranscriptSegment> segments,
                                               String context, String title) {
        try {
            return notesService.generateTopics(segments, context, title);
        } catch (Exception e) {
            throw new SmartNotesException(ErrorCode.NOTES_GENERATION_FAILED,
                    "Topic generation failed: " + e.getMessage(), e);
        }
    }

    private void captionFrames(List<TopicSection> topics, List<Path> frames,
                                double duration, int interval) {
        for (TopicSection topic : topics) {
            Path bestFrame = frameSelector.pickBestFrame(
                    frames, topic.getStartTime(), topic.getEndTime(), interval);
            if (bestFrame != null) {
                topic.setScreenshotPath(bestFrame.toString());
                topic.setScreenshotCaption(visionService.describeFrame(bestFrame, topic.getTitle()));
            }
        }
        log.info("Frame captioning complete | topics={}", topics.size());
    }

    private String exportPdf(VideoRequest request, VideoMetadata meta,
                              String summary, List<TopicSection> topics) {
        if (!request.isExportPdf()) return null;
        try {
            return pdfExportService.export(meta, summary, topics).toString();
        } catch (Exception e) {
            throw new SmartNotesException(ErrorCode.PDF_EXPORT_FAILED,
                    "PDF export failed: " + e.getMessage(), e);
        }
    }

    private String exportExcalidraw(VideoRequest request, VideoMetadata meta,
                                     String summary, List<TopicSection> topics) {
        if (!request.isExportExcalidraw()) return null;
        try {
            return excalidrawService.export(meta, summary, topics).toString();
        } catch (Exception e) {
            throw new SmartNotesException(ErrorCode.EXCALIDRAW_EXPORT_FAILED,
                    "Excalidraw export failed: " + e.getMessage(), e);
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────

    private NotesResponse tryReturnCached(String videoId, VideoRequest request, long startMs) {
        Path videoDir = Paths.get(workspace, videoId);
        if (!Files.isDirectory(videoDir)) return null;

        Path pdfPath = getPdfPath(videoId);
        Path excalidrawPath = getExcalidrawPath(videoId);

        boolean hasPdf = Files.exists(pdfPath) && isNonEmpty(pdfPath);
        boolean hasExcalidraw = Files.exists(excalidrawPath) && isNonEmpty(excalidrawPath);

        if (request.isExportPdf() && !hasPdf) return null;
        if (request.isExportExcalidraw() && !hasExcalidraw) return null;
        if (!hasPdf && !hasExcalidraw) return null;

        log.info("Cache artifacts found | videoId={} hasPdf={} hasExcalidraw={}",
                videoId, hasPdf, hasExcalidraw);

        return NotesResponse.builder()
                .videoId(videoId)
                .title("(cached) " + videoId)
                .overallSummary("Loaded from cache. Use forceRefresh=true to regenerate.")
                .topics(List.of())
                .pdfPath(hasPdf ? pdfPath.toString() : null)
                .excalidrawPath(hasExcalidraw ? excalidrawPath.toString() : null)
                .processingTimeMs(elapsed(startMs))
                .cached(true)
                .build();
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private void progress(String jobId, int stage, String message) {
        log.info("Stage {}/{} | {}", stage, TOTAL_STAGES, message);
        jobProgressService.sendProgress(jobId, stage, TOTAL_STAGES, message);
    }

    private int resolveFrameInterval(Integer requested, double videoDurationSec) {
        if (requested != null && requested > 0) return requested;
        if (videoDurationSec < 60) return 3;
        if (videoDurationSec < 600) return 15;
        return 30;
    }

    private long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }

    private boolean isNonEmpty(Path p) {
        try { return Files.size(p) > 0; } catch (IOException e) { return false; }
    }

    private String extractVideoId(String url) {
        if (url == null) return null;
        Matcher m = YT_ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public Path getPdfPath(String videoId) {
        return Paths.get(workspace, videoId, "notes.pdf");
    }

    public Path getExcalidrawPath(String videoId) {
        return Paths.get(workspace, videoId, "notes.excalidraw");
    }
}
