package com.smartnotes.ai.SmartNotes.AI.controller;

import com.smartnotes.ai.SmartNotes.AI.dto.request.NotesRequest;
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
    public ResponseEntity<NotesResponse> createNotes(
            @RequestBody NotesRequest request) {

        log.info("📨 Received notes generation request for video: {}", request.getVideoUrl());

        try {
            String notes = youTubeService.generateNotesFromVideo(
                    request.getVideoUrl(),
                    request.getLanguage()
            );

            NotesResponse response = NotesResponse.builder()
                    .notes(notes)
                    .videoUrl(request.getVideoUrl())
                    .language(request.getLanguage())
                    .status("success")
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("❌ Error generating notes: {}", e.getMessage(), e);

            NotesResponse errorResponse = NotesResponse.builder()
                    .status("error")
                    .error(e.getMessage())
                    .videoUrl(request.getVideoUrl())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


}