package com.smartnotes.ai.SmartNotes.AI.service.transcript;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Service
public class VoskTranscriptionService {

    @Value("${vosk.model.path:C:\\vosk-model-en-us-0.22}")
    private String modelPath;

    private Model model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            log.info("🔧 Loading Vosk model from: {}", modelPath);
            model = new Model(modelPath);
            log.info("✅ Vosk model loaded successfully");
        } catch (Exception e) {
            log.error("❌ Failed to load Vosk model: {}", e.getMessage(), e);
            throw new RuntimeException("Vosk model initialization failed", e);
        }
    }

    /**
     * Transcribe WAV audio file using Vosk
     */
    public String transcribe(File wavFile) {
        log.info("🎤 Starting Vosk transcription for: {}", wavFile.getName());
        StringBuilder transcript = new StringBuilder();
        int sampleRate = detectSampleRate(wavFile);
        log.info("ℹ️ Detected sample rate: {} Hz", sampleRate);

        try (InputStream ais = new FileInputStream(wavFile);
             Recognizer recognizer = new Recognizer(model, sampleRate)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = ais.read(buffer)) != -1) {
                // recognizer.acceptWaveForm accepts (byte[], int)
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String resultJson = recognizer.getResult();
                    String text = extractTextFromVoskJson(resultJson);
                    if (!text.isBlank()) {
                        transcript.append(text).append(" ");
                    }
                } else {
                    // partial result can be optionally used
                    String partial = recognizer.getPartialResult();
                    // usually partial contains "partial": "text"
                    String partialText = extractTextFromVoskJson(partial);
                    if (!partialText.isBlank()) {
                        // append partial as well if you want; here we skip to avoid duplicates
                    }
                }
            }

            // final result
            String finalResult = recognizer.getFinalResult();
            String finalText = extractTextFromVoskJson(finalResult);
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
     * Try to parse Vosk JSON and return the "text" field; otherwise return empty.
     */
    private String extractTextFromVoskJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return "";
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            if (node.has("text")) {
                return node.get("text").asText("").trim();
            }
            // fallback: some results may have "result" array; we keep it robust
            return "";
        } catch (Exception e) {
            log.debug("⚠️ Failed to parse Vosk JSON: {}. Raw: {}", e.getMessage(), jsonStr);
            // best-effort: try naive parsing
            try {
                int idx = jsonStr.indexOf("\"text\"");
                if (idx != -1) {
                    int start = jsonStr.indexOf('"', idx + 6);
                    start = jsonStr.indexOf('"', start + 1) + 1;
                    int end = jsonStr.indexOf('"', start);
                    if (start > 0 && end > start) {
                        return jsonStr.substring(start, end);
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
            return "";
        }
    }

    /**
     * Detect sample rate from WAV header using AudioSystem. Falls back to 16000.
     */
    private int detectSampleRate(File wavFile) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = ais.getFormat();
            float rate = format.getSampleRate();
            // Round to nearest int
            if (rate <= 0 || Float.isNaN(rate)) {
                return 16000;
            }
            return Math.round(rate);
        } catch (Exception e) {
            log.warn("⚠️ Could not detect sample rate from file {}, defaulting to 16000 Hz: {}", wavFile.getName(), e.getMessage());
            return 16000;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (model != null) {
            try {
                model.close();
            } catch (Exception e) {
                log.warn("⚠️ Error while closing Vosk model: {}", e.getMessage());
            }
            log.info("🗑️ Vosk model closed");
        }
    }
}
