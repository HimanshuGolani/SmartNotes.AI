package com.smartnotes.ai.SmartNotes.AI.service.yt;

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
     * Main entry point - Get transcript and generate notes
     */
    public String generateNotesFromVideo(String videoUrl, String language) {
        log.info("🎬 Starting notes generation for video: {}", videoUrl);

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

            // Step 3️⃣ Generate notes from transcript using NotesGenerationService
            log.info("📄 Transcript length: {} characters", transcript.length());
            log.info("📄 Preview: {}...", transcript.substring(0, Math.min(200, transcript.length())));

            log.info("📚 Calling NotesGenerationService to generate notes...");
            String notes = notesGenerationService.generateNotes(transcript, language);

            log.info("✅ Notes generated successfully!");
            return notes;

        } catch (Exception e) {
            log.error("❌ Notes generation failed for {}: {}", videoUrl, e.getMessage(), e);
            throw new RuntimeException("Notes generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Try to get existing YouTube transcript/captions
     */
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

            // Look for transcript file
            String[] possibleExtensions = {".en.txt", ".txt", ".en.vtt", ".en.srv3"};
            for (String ext : possibleExtensions) {
                File transcriptFile = outputDir.resolve(fileName + ext).toFile();
                if (transcriptFile.exists() && transcriptFile.length() > 0) {
                    log.info("✅ Found transcript file: {}", transcriptFile.getName());
                    String content = Files.readString(transcriptFile.toPath());
                    cleanupTemp(transcriptFile);

                    // Clean up VTT/SRT formatting
                    content = cleanTranscript(content);

                    if (content.length() > 100) { // Valid transcript
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

    /**
     * Clean transcript from VTT/SRT timestamps and formatting
     */
    private String cleanTranscript(String transcript) {
        // Remove VTT headers
        transcript = transcript.replaceAll("WEBVTT.*\\n", "");
        transcript = transcript.replaceAll("Kind:.*\\n", "");
        transcript = transcript.replaceAll("Language:.*\\n", "");

        // Remove timestamps
        transcript = transcript.replaceAll("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}", "");
        transcript = transcript.replaceAll("<\\d{2}:\\d{2}:\\d{2}\\.\\d{3}>", "");
        transcript = transcript.replaceAll("\\d+\\n\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}", "");

        // Remove HTML tags
        transcript = transcript.replaceAll("<[^>]+>", "");

        // Remove excessive newlines
        transcript = transcript.replaceAll("\\n{3,}", "\n\n");

        return transcript.trim();
    }

    /**
     * Download audio from YouTube
     */
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

    /**
     * Convert MP3 to WAV format (required by Vosk)
     */
    private File convertToWav(File mp3File) throws IOException, InterruptedException {
        String wavFileName = mp3File.getName().replace(".mp3", ".wav");
        Path wavPath = mp3File.toPath().getParent().resolve(wavFileName);

        log.info("🔄 Converting to WAV format...");

        ProcessBuilder pb = new ProcessBuilder(
                FFMPEG_PATH + "\\ffmpeg.exe",
                "-i", mp3File.getAbsolutePath(),
                "-ar", "16000",  // 16kHz sample rate (required by Vosk)
                "-ac", "1",       // Mono
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