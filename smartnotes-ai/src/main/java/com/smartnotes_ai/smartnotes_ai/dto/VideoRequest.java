package com.smartnotes_ai.smartnotes_ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoRequest {
    @NotBlank
    private String youtubeUrl;
    private boolean exportPdf = true;
    private boolean exportExcalidraw = false;
    private Integer frameIntervalSeconds; // optional override
}