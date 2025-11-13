package com.smartnotes.ai.SmartNotes.AI.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicStructure {
    private String mainTopic;
    private List<String> subtopics;
}