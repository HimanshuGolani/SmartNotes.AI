package com.smartnotes.ai.SmartNotes.AI.controller;

import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse;
import com.smartnotes.ai.SmartNotes.AI.service.yt.YouTubeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class NotesController {

    private final YouTubeService youTubeService;

    @PostMapping("/generate")
    public ResponseEntity<NotesResponse> createNotes(@RequestBody NotesRequest request) {
        log.info("📨 Request received: {}", request.getVideoUrl());

        try {
            NotesResponse response = youTubeService.generateStructuredNotesFromVideo(
                    request.getVideoUrl(),
                    request.getLanguage()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(NotesResponse.builder()
                            .status("error")
                            .error(e.getMessage())
                            .videoUrl(request.getVideoUrl())
                            .build());
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NotesRequest {
        private String videoUrl;
        private String language;
    }
}