package com.smartnotes.ai.SmartNotes.AI.service;

import com.smartnotes.ai.SmartNotes.AI.service.ollama.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpellCorrectionService {

    private final OllamaService ollamaService;

    @Value("${ollama.modelName:llama3}")
    private String modelName;

    /**
     * Uses the LLM to perform context-aware spell correction on a transcript.
     * This keeps meaning and technical words intact while fixing obvious typos.
     */
    public String correctSpelling(String transcript, String language) {
        if (transcript == null || transcript.isBlank()) return transcript;
        try {
            String prompt = buildSpellCorrectionPrompt(transcript, language);
            log.info("📝 Running context-aware spell correction (length {})", transcript.length());
            String response = ollamaService.generate(modelName, prompt).block();
            if (response == null || response.isBlank()) {
                log.warn("⚠️ Spell correction returned empty. Returning original transcript.");
                return transcript;
            }
            // response expected to be corrected plain text
            return response.trim();
        } catch (Exception e) {
            log.error("❌ Spell correction failed: {}", e.getMessage(), e);
            return transcript;
        }
    }

    private String buildSpellCorrectionPrompt(String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return """
                You are a careful editor specialized in fixing spelling mistakes while preserving technical terms, code tokens, acronyms, names, and context.
                INSTRUCTIONS:
                - Correct obvious spelling mistakes, repeated letters, missing letters, and simple OCR errors.
                - Preserve technical words, code snippets, commands, package names, class names, acronyms, and URLs exactly as they appear unless they are clearly misspelled.
                - Maintain original sentence meaning and punctuation where possible.
                - Output only the corrected transcript (plain text), no explanations, no JSON, no extra commentary.
                LANGUAGE: %s
                                
                TRANSCRIPT:
                %s
                """.formatted(lang, transcript);
    }
}
