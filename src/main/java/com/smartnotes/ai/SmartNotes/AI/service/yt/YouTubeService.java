package com.smartnotes.ai.SmartNotes.AI.service.yt;

import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse;
import com.smartnotes.ai.SmartNotes.AI.service.notes_gen.NotesGenerationService;
import com.smartnotes.ai.SmartNotes.AI.service.transcript.VoskTranscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeService {

    private final VoskTranscriptionService voskTranscriptionService;
    private final NotesGenerationService notesGenerationService;

    private static final String YT_DLP_PATH = "C:\\Program Files\\yt-dlp\\yt-dlp.exe";
    private static final String FFMPEG_PATH = "C:\\Program Files\\ffmpeg\\bin";

    /**
     * Main entry point - Get transcript and generate structured notes
     */
    public NotesResponse generateStructuredNotesFromVideo(String videoUrl, String language) {
        log.info("🎬 Starting structured notes generation for video: {}", videoUrl);

        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid YouTube URL");
        }

        try {
            // Step 1️⃣ Try to get existing YouTube transcript
            log.info("📝 Checking if YouTube transcript already exists...");
            String transcript = getExistingTranscript(videoUrl);

            // Step 2️⃣ If no transcript, download audio and transcribe using Vosk
            if (transcript == null || transcript.isBlank()) {
                log.info("❌ No existing transcript found. Will download and transcribe audio...");

                File audioFile = downloadAudio(videoUrl);
                log.info("✅ Audio downloaded: {}", audioFile.getAbsolutePath());

                // Convert to WAV for Vosk
                File wavFile = convertToWav(audioFile);
                log.info("✅ Converted to WAV format");

                // Transcribe using Vosk
                log.info("🧠 Transcribing audio using Vosk...");
                transcript = voskTranscriptionService.transcribe(wavFile);

                // Cleanup
                cleanupTemp(audioFile);
                cleanupTemp(wavFile);
                log.info("🧹 Cleaned up temporary files");
            } else {
                log.info("✅ Found existing YouTube transcript!");
            }

            // Step 3️⃣ Generate structured notes
            log.info("📄 Transcript length: {} characters", transcript.length());
            log.info("📄 Preview: {}...", transcript.substring(0, Math.min(200, transcript.length())));

            log.info("📚 Calling NotesGenerationService to generate structured notes...");
            NotesResponse response = notesGenerationService.generateNotes(transcript, language);
            response.setVideoUrl(videoUrl);

            log.info("✅ Structured notes generated successfully!");
            return response;

        } catch (Exception e) {
            log.error("❌ Notes generation failed for {}: {}", videoUrl, e.getMessage(), e);
            throw new RuntimeException("Notes generation failed: " + e.getMessage(), e);
        }
    }

    // ... (rest of the methods remain the same - getExistingTranscript, cleanTranscript,
    //      downloadAudio, convertToWav, logProcessOutput, cleanupTemp)

    private String getExistingTranscript(String videoUrl) {
        try {
            String fileName = "yt_transcript_" + UUID.randomUUID();
            Path outputDir = Paths.get(System.getProperty("user.home"), "Downloads");
            Path outputPath = outputDir.resolve(fileName);

            log.info("🔍 Attempting to fetch existing YouTube transcript...");

            ProcessBuilder pb = new ProcessBuilder(
                    YT_DLP_PATH,
                    "--write-auto-sub",
                    "--write-sub",
                    "--skip-download",
                    "--sub-lang", "en",
                    "--sub-format", "txt",
                    "--convert-subs", "txt",
                    "-o", outputPath.toString(),
                    videoUrl
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            logProcessOutput(process);
            int exitCode = process.waitFor();

            String[] possibleExtensions = {".en.txt", ".txt", ".en.vtt", ".en.srv3"};
            for (String ext : possibleExtensions) {
                File transcriptFile = outputDir.resolve(fileName + ext).toFile();
                if (transcriptFile.exists() && transcriptFile.length() > 0) {
                    log.info("✅ Found transcript file: {}", transcriptFile.getName());
                    String content = Files.readString(transcriptFile.toPath());
                    cleanupTemp(transcriptFile);

                    content = cleanTranscript(content);

                    if (content.length() > 100) {
                        return content;
                    }
                }
            }

            log.info("❌ No valid transcript found");
            return null;

        } catch (Exception e) {
            log.warn("⚠️ Could not fetch existing transcript: {}", e.getMessage());
            return null;
        }
    }

    private String cleanTranscript(String transcript) {
        transcript = transcript.replaceAll("WEBVTT.*\\n", "");
        transcript = transcript.replaceAll("Kind:.*\\n", "");
        transcript = transcript.replaceAll("Language:.*\\n", "");
        transcript = transcript.replaceAll("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}", "");
        transcript = transcript.replaceAll("<\\d{2}:\\d{2}:\\d{2}\\.\\d{3}>", "");
        transcript = transcript.replaceAll("\\d+\\n\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}", "");
        transcript = transcript.replaceAll("<[^>]+>", "");
        transcript = transcript.replaceAll("\\n{3,}", "\n\n");
        return transcript.trim();
    }

    private File downloadAudio(String videoUrl) throws IOException, InterruptedException {
        String fileName = "yt_audio_" + UUID.randomUUID() + ".mp3";
        Path outputPath = Paths.get(System.getProperty("user.home"), "Downloads", fileName);

        log.info("📥 Downloading audio using yt-dlp...");

        ProcessBuilder pb = new ProcessBuilder(
                YT_DLP_PATH,
                "-x",
                "--audio-format", "mp3",
                "--audio-quality", "5",
                "--ffmpeg-location", FFMPEG_PATH,
                "-o", outputPath.toString(),
                videoUrl
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        logProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("yt-dlp failed with exit code " + exitCode);
        }

        File audioFile = outputPath.toFile();
        if (!audioFile.exists()) {
            throw new IOException("Audio file not found: " + audioFile.getAbsolutePath());
        }

        return audioFile;
    }

    private File convertToWav(File mp3File) throws IOException, InterruptedException {
        String wavFileName = mp3File.getName().replace(".mp3", ".wav");
        Path wavPath = mp3File.toPath().getParent().resolve(wavFileName);

        log.info("🔄 Converting to WAV format...");

        ProcessBuilder pb = new ProcessBuilder(
                FFMPEG_PATH + "\\ffmpeg.exe",
                "-i", mp3File.getAbsolutePath(),
                "-ar", "16000",
                "-ac", "1",
                "-f", "wav",
                wavPath.toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        logProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("FFmpeg conversion failed with exit code " + exitCode);
        }

        return wavPath.toFile();
    }

    private void logProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[Process] {}", line);
            }
        }
    }

    private void cleanupTemp(File file) {
        try {
            Files.deleteIfExists(file.toPath());
            log.info("🗑️ Deleted: {}", file.getName());
        } catch (IOException e) {
            log.warn("⚠️ Failed to delete: {}", file.getName());
        }
    }
}