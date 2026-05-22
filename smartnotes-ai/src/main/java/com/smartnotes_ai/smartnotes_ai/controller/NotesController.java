package com.smartnotes_ai.smartnotes_ai.controller;


import com.smartnotes_ai.smartnotes_ai.dto.NotesResponse;
import com.smartnotes_ai.smartnotes_ai.dto.VideoRequest;
import com.smartnotes_ai.smartnotes_ai.service.NotesOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NotesController {

    private final NotesOrchestrationService orchestrator;

    @PostMapping("/generate")
    public ResponseEntity<NotesResponse> generate(@Valid @RequestBody VideoRequest request) {
        log.info("Received generate request: {}", request.getYoutubeUrl());
        NotesResponse response = orchestrator.process(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/pdf/{videoId}")
    public ResponseEntity<FileSystemResource> downloadPdf(@PathVariable String videoId) {
        Path pdf = orchestrator.getPdfPath(videoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + videoId + "-notes.pdf\"")
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
    public String health() { return "OK"; }
}