package com.smartnotes_ai.smartnotes_ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrameExtractionService {

    @Value("${smartnotes.workspace}")
    private String workspace;

    @Value("${smartnotes.ffmpeg.binary}")
    private String ffmpeg;

    public List<Path> extractFrames(Path videoFile, String videoId, int intervalSeconds) {
        try {
            Path framesDir = videoFile.getParent().resolve("frames");
            Files.createDirectories(framesDir);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y", "-i", videoFile.toString(),
                    "-vf", "fps=1/" + intervalSeconds + ",scale=1920:-1",  // Full HD
                    "-q:v", "2",  // higher quality (2 = best for JPEG)
                    framesDir.resolve("frame_%04d.jpg").toString()
            ).redirectErrorStream(true);

            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.debug(line);
                }
            }
            p.waitFor();

            List<Path> frames = new ArrayList<>();
            try (Stream<Path> stream = Files.list(framesDir)) {
                stream.filter(f -> f.toString().endsWith(".jpg"))
                        .sorted()
                        .forEach(frames::add);
            }
            Collections.sort(frames);
            log.info("Found {} frame files in {}", Optional.of(frames.size()), framesDir);
            return frames;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract frames", e);
        }
    }
}