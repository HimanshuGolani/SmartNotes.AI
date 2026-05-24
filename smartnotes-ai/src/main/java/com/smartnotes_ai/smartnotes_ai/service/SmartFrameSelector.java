package com.smartnotes_ai.smartnotes_ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartFrameSelector {

    /**
     * Pick best frame for a topic by considering:
     * 1. Temporal position (avoid first/last 10% of segment)
     * 2. File size (very small = blank/black screen → skip)
     * 3. Multiple candidates from middle 60% of segment
     */
    public Path pickBestFrame(List<Path> allFrames, double startSec, double endSec, int frameIntervalSec) {
        if (allFrames.isEmpty()) return null;

        double duration = endSec - startSec;
        double safeStart = startSec + duration * 0.2;   // skip first 20%
        double safeEnd = endSec - duration * 0.1;       // skip last 10%

        int startIdx = (int) Math.max(0, safeStart / frameIntervalSec);
        int endIdx = (int) Math.min(allFrames.size() - 1, safeEnd / frameIntervalSec);
        if (startIdx > endIdx) {
            int mid = (int) ((startSec + endSec) / 2.0 / frameIntervalSec);
            return allFrames.get(Math.min(allFrames.size() - 1, Math.max(0, mid)));
        }

        // Collect candidates and pick the largest (proxy for "most content")
        List<Path> candidates = new ArrayList<>();
        for (int i = startIdx; i <= endIdx; i++) candidates.add(allFrames.get(i));

        Path best = null;
        long bestSize = 0;
        for (Path cand : candidates) {
            try {
                long size = Files.size(cand);
                // Skip very small frames (blank/black/static screens)
                if (size < 8 * 1024) continue;
                if (size > bestSize) {
                    bestSize = size;
                    best = cand;
                }
            } catch (IOException ignored) {}
        }

        if (best == null) best = candidates.get(candidates.size() / 2);
        log.debug("Picked frame {} (size={}KB) for window {}-{}s",
                best.getFileName(), bestSize / 1024, (int) startSec, (int) endSec);
        return best;
    }
}