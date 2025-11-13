package com.smartnotes.ai.SmartNotes.AI.service.notes_gen;

import com.smartnotes.ai.SmartNotes.AI.service.ollama.OllamaService;
import com.smartnotes.ai.SmartNotes.AI.prompts.Prompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotesGenerationService {

    private final OllamaService ollamaService;

    /**
     * Generate notes from transcript using llama3
     * Based on the transcript, derive a plan and generate images and text accordingly.
     */
    public String generateNotes(String transcript, String language) {

        log.info("📚 Starting notes generation...");
        log.info("📊 Transcript length: {} characters", transcript.length());
        log.info("🌐 Language: {}", language);

        try {
            // Generate notes using llama3
            String notes = generateNotesUsingLlama3(transcript, language).block();

            log.info("✅ Notes generation completed successfully!");
            return notes;

        } catch (Exception e) {
            log.error("❌ Notes generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate notes: " + e.getMessage(), e);
        }
    }

    /**
     * Generate notes using llama3 model via Ollama
     */
    private Mono<String> generateNotesUsingLlama3(String transcript, String language) {
        String prompt = Prompts.getNotesGenerationPrompt(transcript, language);

        return ollamaService.generate("llama3:latest", prompt)
                .doOnSubscribe(sub -> log.info("📨 Sending request to llama3..."))
                .doOnSuccess(resp -> log.info("✅ llama3 response received"))
                .doOnError(err -> log.error("❌ llama3 error: {}", err.getMessage()));
    }

    // Future methods for image generation, planning, etc. can be added here
    // based on the transcript derive a plan based it, and generate images, and text accordingly.
}