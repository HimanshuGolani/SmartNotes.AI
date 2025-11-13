package com.smartnotes.ai.SmartNotes.AI.service.transcript;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Service
public class VoskTranscriptionService {

    @Value("${vosk.model.path:C:\\vosk-model-en-us-0.22}")
    private String modelPath;

    private Model model;

    @PostConstruct
    public void init() {
        try {
            log.info("🔧 Loading Vosk model from: {}", modelPath);
            model = new Model(modelPath);
            log.info("✅ Vosk model loaded successfully");
        } catch (Exception e) {
            log.error("❌ Failed to load Vosk model: {}", e.getMessage());
            throw new RuntimeException("Vosk model initialization failed", e);
        }
    }

    /**
     * Transcribe WAV audio file using Vosk
     */
    public String transcribe(File wavFile) {
        log.info("🎤 Starting Vosk transcription for: {}", wavFile.getName());

        StringBuilder transcript = new StringBuilder();

        try (InputStream ais = new FileInputStream(wavFile);
             Recognizer recognizer = new Recognizer(model, 16000)) {

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = ais.read(buffer)) != -1) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String result = recognizer.getResult();
                    String text = extractText(result);
                    if (!text.isBlank()) {
                        transcript.append(text).append(" ");
                    }
                }
            }

            // Get final result
            String finalResult = recognizer.getFinalResult();
            String finalText = extractText(finalResult);
            if (!finalText.isBlank()) {
                transcript.append(finalText);
            }

            log.info("✅ Transcription completed. Length: {} characters", transcript.length());
            return transcript.toString().trim();

        } catch (Exception e) {
            log.error("❌ Transcription failed: {}", e.getMessage(), e);
            throw new RuntimeException("Vosk transcription failed", e);
        }
    }

    /**
     * Extract text from Vosk JSON result
     */
    private String extractText(String jsonResult) {
        try {
            // Simple JSON parsing (you can use Jackson for more robust parsing)
            if (jsonResult.contains("\"text\"")) {
                int startIndex = jsonResult.indexOf("\"text\" : \"") + 10;
                int endIndex = jsonResult.indexOf("\"", startIndex);
                return jsonResult.substring(startIndex, endIndex);
            }
            return "";
        } catch (Exception e) {
            log.warn("⚠️ Failed to extract text from result: {}", jsonResult);
            return "";
        }
    }

    @PreDestroy
    public void cleanup() {
        if (model != null) {
            model.close();
            log.info("🗑️ Vosk model closed");
        }
    }
}