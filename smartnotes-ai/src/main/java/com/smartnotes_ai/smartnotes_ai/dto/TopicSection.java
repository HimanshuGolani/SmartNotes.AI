package com.smartnotes_ai.smartnotes_ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class TopicSection {
    private String title;
    private String summary;
    private List<String> bulletPoints;
    private double startTime;
    private double endTime;
    private String screenshotPath;     // best frame for this topic
    private String screenshotCaption;
}