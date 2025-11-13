package com.smartnotes.ai.SmartNotes.AI.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotesResponse {
    private List<TopicContent> topics;
    private String videoUrl;
    private String language;
    private String status;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicContent {
        private String title;
        private List<SubtopicContent> subtopics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubtopicContent {
        private String title;
        private String description;
        private String content;
        private List<ImagePlaceholder> images;
        private List<TableData> tables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImagePlaceholder {
        private Integer position;
        private String description;
        private String imageUrl;
        private Boolean placeholder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableData {
        private Integer position;
        private String title;
        private List<String> headers;
        private List<List<String>> rows;
    }
}