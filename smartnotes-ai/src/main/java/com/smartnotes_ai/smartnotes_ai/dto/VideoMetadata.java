package com.smartnotes_ai.smartnotes_ai.dto;

import lombok.Data;
import java.nio.file.Path;
import java.util.List;

@Data
public class VideoMetadata {
    private String videoId;
    private String title;
    private String uploader;
    private String description;
    private double duration;
    private Path videoFile;
    private Path audioFile;
    private List<Path> frameFiles;
}