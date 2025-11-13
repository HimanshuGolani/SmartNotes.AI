package com.smartnotes.ai.SmartNotes.AI.dto.request;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class NotesRequest {
    private String videoUrl;
    private String language;
}