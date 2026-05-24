package com.smartnotes_ai.smartnotes_ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotesResponse {
    private String videoId;
    private String title;
    private String overallSummary;
    private List<TopicSection> topics;
    private String pdfPath;
    private String excalidrawPath;
    private long processingTimeMs;
    private boolean cached;          // NEW

    // Convenience getter your frontend already uses
    public int getTopicCount() {
        return topics == null ? 0 : topics.size();
    }

    public double getProcessingTimeSeconds() {
        return processingTimeMs / 1000.0;
    }
}