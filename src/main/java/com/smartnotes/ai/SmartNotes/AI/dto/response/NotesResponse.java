package com.smartnotes.ai.SmartNotes.AI.dto.response;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class NotesResponse {
    private String notes;
    private String videoUrl;
    private String language;
    private String status;
    private String error;
}