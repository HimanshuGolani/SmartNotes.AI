package com.smartnotes_ai.smartnotes_ai.service;

import com.smartnotes_ai.smartnotes_ai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Value("${smartnotes.workspace}")
    private String workspace;

    // Matches ?v=VIDEO_ID, /shorts/VIDEO_ID, /embed/VIDEO_ID, youtu.be/VIDEO_ID
    private static final Pattern YT_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:[^/\\n\\s]+/\\S+/|(?:v|e(?:mbed)?|shorts)/|\\S*?[?&]v=)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    public NotesResponse process(VideoRequest request) {
        long start = System.currentTimeMillis();

        // ============================================================
        // 0. CACHE CHECK — extract videoId from URL & test if cached
        // ============================================================
        String videoId = extractVideoId(request.getYoutubeUrl());
        if (videoId != null) {
            NotesResponse cached = tryReturnCached(videoId, request, start);
            if (cached != null) {
                log.info("✓ Cache HIT for videoId={} — returning instantly ({}ms)",
                        videoId, cached.getProcessingTimeMs());
                return cached;
            }
            log.info("✗ Cache MISS for videoId={} — proceeding with full pipeline", videoId);
        } else {
            log.warn("Could not extract videoId from URL — skipping cache check");
        }

        // ============================================================
        // 1. Download video + audio + metadata
        // ============================================================
        log.info("Step 1: Downloading video & metadata from YouTube...");
        VideoMetadata meta = downloadService.download(request.getYoutubeUrl());
        log.info("Downloaded: '{}' by {} (duration: {}s)",
                meta.getTitle(), meta.getUploader(), meta.getDuration());

        // Re-check cache after download (in case URL was a different form of same video)
        if (videoId == null || !videoId.equals(meta.getVideoId())) {
            NotesResponse cached = tryReturnCached(meta.getVideoId(), request, start);
            if (cached != null) {
                log.info("✓ Post-download cache HIT for videoId={}", meta.getVideoId());
                cached.setTitle(meta.getTitle());
                return cached;
            }
        }

        // ============================================================
        // 2. Determine frame interval
        // ============================================================
        int interval;
        if (request.getFrameIntervalSeconds() != null) {
            interval = request.getFrameIntervalSeconds();
        } else {
            if (meta.getDuration() < 60) interval = 3;
            else if (meta.getDuration() < 600) interval = 15;
            else interval = 30;
        }
        log.info("Using frame interval: {}s for {}s video", interval, meta.getDuration());

        // 3. Extract frames
        log.info("Step 2: Extracting frames...");
        List<Path> frames = frameService.extractFrames(meta.getVideoFile(), meta.getVideoId(), interval);
        meta.setFrameFiles(frames);
        log.info("Extracted {} frames", frames.size());

        // 4. Transcribe audio
        log.info("Step 3: Transcribing audio...");
        List<TranscriptSegment> segments = transcriptionService.transcribe(meta.getAudioFile());

        // 5. Align transcript with description
        log.info("Step 4: Aligning transcript with video description...");
        String videoDescription = meta.getDescription() != null ? meta.getDescription() : "";
        String alignedContext = alignmentService.align(segments, videoDescription);

        // 6. Generate topic sections
        log.info("Step 5: Generating topic-wise notes...");
        List<TopicSection> topics = notesService.generateTopics(segments, alignedContext, meta.getTitle());

        // 7. Caption frames per topic
        log.info("Step 6: Captioning frames per topic...");
        for (TopicSection topic : topics) {
            Path bestFrame = pickFrameForTopic(frames, topic, meta.getDuration(), interval);
            if (bestFrame != null) {
                topic.setScreenshotPath(bestFrame.toString());
                String caption = visionService.describeFrame(bestFrame, topic.getTitle());
                topic.setScreenshotCaption(caption);
            }
        }

        // 8. Overall summary
        String overall = notesService.overallSummary(topics, meta.getTitle());

        // 9. Export
        String pdfPath = null, excaliPath = null;
        if (request.isExportPdf()) {
            log.info("Step 7: Exporting PDF...");
            pdfPath = pdfExportService.export(meta, overall, topics).toString();
        }
        if (request.isExportExcalidraw()) {
            log.info("Step 8: Exporting Excalidraw...");
            excaliPath = excalidrawService.export(meta, overall, topics).toString();
        }

        return NotesResponse.builder()
                .videoId(meta.getVideoId())
                .title(meta.getTitle())
                .overallSummary(overall)
                .topics(topics)
                .pdfPath(pdfPath)
                .excalidrawPath(excaliPath)
                .processingTimeMs(System.currentTimeMillis() - start)
                .cached(false)
                .build();
    }

    // ====================================================================
    //                          CACHE LOGIC
    // ====================================================================

    /**
     * Returns a cached NotesResponse if all requested artifacts already exist on disk.
     * Returns null if any required artifact is missing → caller must run full pipeline.
     */
    private NotesResponse tryReturnCached(String videoId, VideoRequest request, long startMs) {
        Path videoDir = Paths.get(workspace, videoId);
        if (!Files.isDirectory(videoDir)) {
            return null;
        }

        Path pdfPath = getPdfPath(videoId);
        Path excalidrawPath = getExcalidrawPath(videoId);
        Path metaPath = videoDir.resolve("meta.json");  // optional, see note below

        boolean needPdf = request.isExportPdf();
        boolean needExcalidraw = request.isExportExcalidraw();

        boolean hasPdf = Files.exists(pdfPath) && isNonEmpty(pdfPath);
        boolean hasExcalidraw = Files.exists(excalidrawPath) && isNonEmpty(excalidrawPath);

        // If user asked for a format but it doesn't exist on disk → cache miss
        if (needPdf && !hasPdf) return null;
        if (needExcalidraw && !hasExcalidraw) return null;

        // At least one requested artifact must exist
        if (!hasPdf && !hasExcalidraw) return null;

        log.info("Cache artifacts found: pdf={}, excalidraw={}", hasPdf, hasExcalidraw);

        return NotesResponse.builder()
                .videoId(videoId)
                .title("(cached) " + videoId)   // overwritten by caller if download runs
                .overallSummary("Loaded from cache. Re-run with a different videoId to regenerate.")
                .topics(List.of())
                .pdfPath(hasPdf ? pdfPath.toString() : null)
                .excalidrawPath(hasExcalidraw ? excalidrawPath.toString() : null)
                .processingTimeMs(System.currentTimeMillis() - startMs)
                .cached(true)
                .build();
    }

    private boolean isNonEmpty(Path p) {
        try {
            return Files.size(p) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extracts YouTube video ID from various URL formats:
     *   https://www.youtube.com/watch?v=ABC123
     *   https://youtu.be/ABC123
     *   https://www.youtube.com/shorts/ABC123
     *   https://www.youtube.com/embed/ABC123
     */
    private String extractVideoId(String url) {
        if (url == null) return null;
        Matcher m = YT_ID_PATTERN.matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private Path pickFrameForTopic(List<Path> frames, TopicSection topic,
                                   double totalDuration, int interval) {
        return frameSelector.pickBestFrame(frames, topic.getStartTime(),
                topic.getEndTime(), interval);
    }

    public Path getPdfPath(String videoId) {
        return Paths.get(workspace, videoId, "notes.pdf");
    }

    public Path getExcalidrawPath(String videoId) {
        return Paths.get(workspace, videoId, "notes.excalidraw");
    }
}