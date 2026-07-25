package com.smartnotes_ai.smartnotes_ai.controller;

import com.smartnotes_ai.smartnotes_ai.dto.JobResponse;
import com.smartnotes_ai.smartnotes_ai.dto.NotesResponse;
import com.smartnotes_ai.smartnotes_ai.dto.VideoRequest;
import com.smartnotes_ai.smartnotes_ai.service.JobProgressService;
import com.smartnotes_ai.smartnotes_ai.service.NotesOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NotesController {

    private final NotesOrchestrationService orchestrator;
    private final JobProgressService jobProgressService;

    /**
     * Accepts a processing request and returns a jobId immediately.
     * Processing runs asynchronously; clients stream progress via /progress/{jobId}.
     */
    @PostMapping("/generate")
    public ResponseEntity<JobResponse> generate(@Valid @RequestBody VideoRequest request) {
        String jobId = jobProgressService.createJob();
        log.info("Processing request accepted | jobId={} url={}", jobId, request.getYoutubeUrl());
        orchestrator.processAsync(request, jobId);
        return ResponseEntity.accepted().body(new JobResponse(jobId));
    }

    /**
     * SSE stream that pushes progress events as the pipeline runs.
     * Events: progress | complete | error
     */
    @GetMapping("/progress/{jobId}")
    public SseEmitter streamProgress(@PathVariable String jobId) {
        log.info("SSE subscription | jobId={}", jobId);
        return jobProgressService.subscribe(jobId);
    }

    /**
     * Returns the final NotesResponse once processing completes.
     * Returns 202 Accepted if the job is still running.
     */
    @GetMapping("/result/{jobId}")
    public ResponseEntity<NotesResponse> getResult(@PathVariable String jobId) {
        if (!jobProgressService.exists(jobId)) {
            return ResponseEntity.notFound().build();
        }
        return jobProgressService.getResult(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.ACCEPTED).build());
    }

    @GetMapping("/download/pdf/{videoId}")
    public ResponseEntity<FileSystemResource> downloadPdf(
            @PathVariable String videoId,
            @RequestParam(defaultValue = "inline") String disposition) {
        Path pdf = orchestrator.getPdfPath(videoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + videoId + "-notes.pdf\"")
                .body(new FileSystemResource(pdf));
    }

    @GetMapping("/download/excalidraw/{videoId}")
    public ResponseEntity<FileSystemResource> downloadExcalidraw(@PathVariable String videoId) {
        Path file = orchestrator.getExcalidrawPath(videoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + videoId + "-notes.excalidraw\"")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
