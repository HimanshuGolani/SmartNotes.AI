package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes_ai.smartnotes_ai.dto.VideoMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeDownloadService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${smartnotes.workspace}")
    private String workspace;

    @Value("${smartnotes.ytdlp.binary}")
    private String ytdlp;

    @Value("${smartnotes.ffmpeg.binary}")
    private String ffmpeg;

    public VideoMetadata download(String url) {
        try {
            // Step 1: Fetch metadata (incl. description)
            log.info("Fetching metadata for: {}", url);
            String json = exec(
                    ytdlp,
                    "--dump-json",
                    "--no-warnings",
                    "--no-playlist",
                    "--skip-download",
                    "--extractor-args", "youtube:player_client=android,web",
                    url
            );

            JsonNode root = mapper.readTree(json);
            String videoId = root.path("id").asText();
            String title = root.path("title").asText();
            String uploader = root.path("uploader").asText();
            String description = root.path("description").asText();
            double duration = root.path("duration").asDouble();

            log.info("Title: {}", title);
            log.info("Uploader: {}", uploader);

            Path dir = Paths.get(workspace, videoId);
            Files.createDirectories(dir);

            Path videoFile = dir.resolve("video.mp4");
            Path audioFile = dir.resolve("audio.mp3");

            // Step 2: Download video with anti-403 args
            if (!Files.exists(videoFile)) {
                log.info("Downloading video to {}", videoFile);
                exec(
                        ytdlp,
                        "--no-playlist",
                        "--no-warnings",
                        "--no-update",
                        "--retries", "5",
                        "--fragment-retries", "5",
                        "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "--extractor-args", "youtube:player_client=android,web",
                        "-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/best[height<=720]/best",
                        "--merge-output-format", "mp4",
                        "-o", videoFile.toString(),
                        url
                );
            }

            // Step 3: Extract audio
            if (!Files.exists(audioFile)) {
                log.info("Extracting audio to {}", audioFile);
                exec(
                        ffmpeg, "-y",
                        "-i", videoFile.toString(),
                        "-vn",
                        "-acodec", "libmp3lame",
                        "-q:a", "2",
                        audioFile.toString()
                );
            }

            VideoMetadata meta = new VideoMetadata();
            meta.setVideoId(videoId);
            meta.setTitle(title);
            meta.setUploader(uploader);
            meta.setDescription(description);
            meta.setDuration(duration);
            meta.setVideoFile(videoFile);
            meta.setAudioFile(audioFile);
            return meta;

        } catch (Exception e) {
            throw new RuntimeException("Failed to download YouTube video: " + e.getMessage(), e);
        }
    }

    private String exec(String... cmd) throws Exception {
        log.debug("Executing: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append("\n");
                if (line.startsWith("[download]") || line.startsWith("frame=")
                        || line.startsWith("[youtube]") || line.startsWith("[info]")) {
                    log.debug(line);
                }
            }
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("Command failed (" + code + "): " + out);
        }
        return out.toString();
    }
}