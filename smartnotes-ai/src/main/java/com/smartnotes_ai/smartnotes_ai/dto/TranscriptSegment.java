package com.smartnotes_ai.smartnotes_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment {
    private double start;
    private double end;
    private String text;
}