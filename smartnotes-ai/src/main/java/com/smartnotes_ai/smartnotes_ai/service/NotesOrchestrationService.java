package com.smartnotes_ai.smartnotes_ai.service;

import com.smartnotes_ai.smartnotes_ai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

    public NotesResponse process(VideoRequest request) {
        long start = System.currentTimeMillis();

        // 1. Download video + audio + metadata (description fetched from YouTube)
        log.info("Step 1: Downloading video & metadata from YouTube...");
        VideoMetadata meta = downloadService.download(request.getYoutubeUrl());
        log.info("Downloaded: '{}' by {} (duration: {}s)",
                meta.getTitle(), meta.getUploader(), meta.getDuration());

        // 2. Determine frame interval
        int interval;
        if (request.getFrameIntervalSeconds() != null) {
            interval = request.getFrameIntervalSeconds();
        } else {
            // Auto: shorts/tiny videos = 3s, normal = 15s, long = 30s
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

        // 5. Align transcript with VIDEO'S description (auto-fetched)
        log.info("Step 4: Aligning transcript with video description...");
        String videoDescription = meta.getDescription() != null ? meta.getDescription() : "";
        String alignedContext = alignmentService.align(segments, videoDescription);

        // 6. Generate topic sections
        log.info("Step 5: Generating topic-wise notes...");
        List<TopicSection> topics = notesService.generateTopics(segments, alignedContext, meta.getTitle());

        // 7. For each topic, pick best frame + caption
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
                .build();
    }

    private Path pickFrameForTopic(List<Path> frames, TopicSection topic, double totalDuration, int interval) {
        return frameSelector.pickBestFrame(frames, topic.getStartTime(), topic.getEndTime(), interval);
    }

    public Path getPdfPath(String videoId) {
        return Paths.get(workspace, videoId, "notes.pdf");
    }

    public Path getExcalidrawPath(String videoId) {
        return Paths.get(workspace, videoId, "notes.excalidraw");
    }
}