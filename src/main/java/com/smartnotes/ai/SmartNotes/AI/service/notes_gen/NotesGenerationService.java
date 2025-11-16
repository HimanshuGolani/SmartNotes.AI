package com.smartnotes.ai.SmartNotes.AI.service.notes_gen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes.ai.SmartNotes.AI.dto.internal.TopicStructure;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse.SubtopicContent;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse.TopicContent;
import com.smartnotes.ai.SmartNotes.AI.prompts.Prompts;
import com.smartnotes.ai.SmartNotes.AI.service.ollama.OllamaService;
import com.smartnotes.ai.SmartNotes.AI.service.SpellCorrectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotesGenerationService {

    private final OllamaService ollamaService;
    private final SpellCorrectionService spellCorrectionService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Value("${ollama.modelName:llama3}")
    private String currentModel;

    /**
     * Generate structured notes with topics, subtopics, images, and tables
     */
    public NotesResponse generateNotes(String transcript, String language) {
        log.info("==================================================");
        log.info("📚 STARTING MULTI-STEP NOTES GENERATION");
        log.info("==================================================");
        log.info("📊 Original transcript length: {} characters", transcript == null ? 0 : transcript.length());
        log.info("🌐 Language: {}", language);

        // 1) Spell-correct transcript first (context-aware)
        String corrected = transcript;
        try {
            corrected = spellCorrectionService.correctSpelling(transcript, language);
            log.info("✅ Spell correction completed. New length: {}", corrected.length());
        } catch (Exception e) {
            log.warn("⚠️ Spell correction failed or skipped: {}", e.getMessage());
            corrected = transcript;
        }

        try {
            // STEP 1: Extract topics
            log.info("\n🔍 ===== STEP 1: EXTRACTING TOPICS =====");
            List<TopicStructure> topicStructures = extractTopicsAndSubtopics(corrected, language);
            if (topicStructures == null || topicStructures.isEmpty()) {
                log.error("❌ No topics extracted! Falling back to simple notes generation...");
                return createFallbackResponse(corrected, language);
            }
            log.info("✅ Successfully extracted {} main topics:", topicStructures.size());
            for (int i = 0; i < topicStructures.size(); i++) {
                TopicStructure topic = topicStructures.get(i);
                log.info(" {}. {} (with {} subtopics)", i + 1, topic.getMainTopic(), topic.getSubtopics().size());
            }

            // STEP 2: Generate content
            log.info("\n🚀 ===== STEP 2: GENERATING DETAILED CONTENT =====");
            List<TopicContent> topicContents = generateContentForAllTopics(topicStructures, corrected, language);
            log.info("✅ Successfully generated content for {} topics", topicContents.size());

            // STEP 3: Build response
            NotesResponse response = NotesResponse.builder()
                    .topics(topicContents)
                    .language(language)
                    .status("success")
                    .build();

            log.info("==================================================");
            log.info("✅ NOTES GENERATION COMPLETED SUCCESSFULLY");
            log.info("==================================================");
            return response;
        } catch (Exception e) {
            log.error("❌ NOTES GENERATION FAILED: {}", e.getMessage(), e);
            return createFallbackResponse(corrected, language);
        }
    }

    /**
     * Extract topics with retry logic
     */
    private List<TopicStructure> extractTopicsAndSubtopics(String transcript, String language) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("🎯 Attempt {}/{} - Calling LLM for topic extraction...", attempt, maxRetries);
                String prompt = Prompts.getTopicExtractionPrompt(transcript, language);
                String response = ollamaService.generate(currentModel, prompt).block();
                log.info("📥 Received response from LLM (length: {} chars)", response != null ? response.length() : 0);

                if (response == null || response.isBlank()) {
                    log.error("❌ Empty response from LLM!");
                    continue;
                }

                // Clean and extract JSON
                String jsonResponse = sanitizeAndExtractJson(response);
                log.info("📋 Extracted JSON (topic extraction): {}", jsonResponse);

                List<TopicStructure> topics = objectMapper.readValue(
                        jsonResponse,
                        new TypeReference<List<TopicStructure>>() {}
                );

                if (topics != null && !topics.isEmpty()) {
                    log.info("✅ Successfully parsed {} topics", topics.size());
                    return topics;
                }
                log.warn("⚠️ Parsed empty topics list, retrying...");
            } catch (Exception e) {
                log.error("❌ Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("❌ All attempts exhausted for topic extraction");
                    return null;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return null;
    }

    /**
     * Generate content for all topics using multithreading
     */
    private List<TopicContent> generateContentForAllTopics(
            List<TopicStructure> topicStructures,
            String transcript,
            String language) {

        List<Future<TopicContent>> futures = new ArrayList<>();
        for (int i = 0; i < topicStructures.size(); i++) {
            TopicStructure topicStructure = topicStructures.get(i);
            int topicIndex = i + 1;
            Future<TopicContent> future = executorService.submit(() ->
                    generateContentForTopic(topicStructure, transcript, language, topicIndex, topicStructures.size())
            );
            futures.add(future);
        }

        List<TopicContent> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                TopicContent content = futures.get(i).get(5, TimeUnit.MINUTES);
                results.add(content);
            } catch (Exception e) {
                log.error("❌ Topic {}/{} failed: {}", i + 1, futures.size(), e.getMessage());
                // Add empty content as fallback
                if (i < topicStructures.size()) {
                    results.add(createEmptyTopicContent(topicStructures.get(i)));
                }
            }
        }
        return results;
    }

    /**
     * Generate content for single topic with enhanced error handling
     */
    private TopicContent generateContentForTopic(
            TopicStructure topicStructure,
            String transcript,
            String language,
            int topicIndex,
            int totalTopics) {

        log.info("\n--- Processing Topic {}/{}: {} ---", topicIndex, totalTopics, topicStructure.getMainTopic());
        int maxRetries = 2;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("🎯 Attempt {}/{} for topic: {}", attempt, maxRetries, topicStructure.getMainTopic());

                String subtopicsStr = topicStructure.getSubtopics() == null
                        ? "[]"
                        : topicStructure.getSubtopics().toString();

                String prompt = Prompts.getContentGenerationPrompt(
                        topicStructure.getMainTopic(),
                        subtopicsStr,
                        transcript,
                        language
                );

                String response = ollamaService.generate(currentModel, prompt).block();

                log.info("📥 Received response for '{}' (length: {} chars)",
                        topicStructure.getMainTopic(),
                        response != null ? response.length() : 0);

                if (response == null || response.isBlank()) {
                    log.error("❌ Empty response from LLM");
                    continue;
                }

                // Parse the response
                TopicContent content = parseTopicContentFromResponse(response, topicStructure);
                if (content != null && content.getSubtopics() != null && !content.getSubtopics().isEmpty()) {
                    log.info("✅ Successfully generated content for: {} with {} subtopics",
                            content.getTitle(), content.getSubtopics().size());
                    return content;
                }

                log.warn("⚠️ Failed to parse content properly, retrying...");
            } catch (Exception e) {
                log.error("❌ Attempt {}/{} failed for topic '{}': {}",
                        attempt, maxRetries, topicStructure.getMainTopic(), e.getMessage());
                if (attempt == maxRetries) {
                    log.error("❌ All attempts exhausted, returning empty content");
                    return createEmptyTopicContent(topicStructure);
                }
            }
        }
        return createEmptyTopicContent(topicStructure);
    }

    /**
     * Enhanced JSON parsing with proper structure handling
     */
    private TopicContent parseTopicContentFromResponse(String response, TopicStructure topicStructure) {
        try {
            // Extract and sanitize JSON
            String jsonResponse = sanitizeAndExtractJson(response);

            // Log first 1000 chars for debugging
            log.info("📋 Sanitized JSON for '{}': {}",
                    topicStructure.getMainTopic(),
                    jsonResponse.substring(0, Math.min(1000, jsonResponse.length())) + "...");

            // Parse directly to TopicContent
            TopicContent content = objectMapper.readValue(jsonResponse, TopicContent.class);

            // Validate we got subtopics
            if (content.getSubtopics() == null || content.getSubtopics().isEmpty()) {
                log.warn("⚠️ No subtopics in parsed content, trying alternative parsing");
                return parseAlternativeStructure(jsonResponse, topicStructure);
            }

            // Ensure title is set
            if (content.getTitle() == null || content.getTitle().isBlank()) {
                content.setTitle(topicStructure.getMainTopic());
            }

            // Initialize empty lists if null
            for (SubtopicContent sub : content.getSubtopics()) {
                if (sub.getImages() == null) sub.setImages(new ArrayList<>());
                if (sub.getTables() == null) sub.setTables(new ArrayList<>());
            }

            log.info("✅ Successfully parsed {} subtopics", content.getSubtopics().size());
            return content;

        } catch (Exception e) {
            log.error("❌ JSON parsing failed: {}", e.getMessage());
            log.info("🔄 Falling back to plain text parsing");
            return createContentFromPlainText(response, topicStructure);
        }
    }

    /**
     * Alternative parsing for different JSON structures
     */
    private TopicContent parseAlternativeStructure(String jsonResponse, TopicStructure topicStructure) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            String title = topicStructure.getMainTopic();
            if (rootNode.has("title") && !rootNode.get("title").isNull()) {
                title = rootNode.get("title").asText();
            }

            List<SubtopicContent> subtopics = new ArrayList<>();

            // Try to find subtopics array
            JsonNode subtopicsNode = null;
            String[] possibleFields = {"subtopics", "subTopics", "topics", "sections", "content"};
            for (String field : possibleFields) {
                if (rootNode.has(field) && rootNode.get(field).isArray()) {
                    subtopicsNode = rootNode.get(field);
                    break;
                }
            }

            if (subtopicsNode != null) {
                for (JsonNode subNode : subtopicsNode) {
                    SubtopicContent sub = objectMapper.treeToValue(subNode, SubtopicContent.class);
                    if (sub != null) {
                        if (sub.getImages() == null) sub.setImages(new ArrayList<>());
                        if (sub.getTables() == null) sub.setTables(new ArrayList<>());
                        subtopics.add(sub);
                    }
                }
            }

            if (subtopics.isEmpty()) {
                return createContentFromPlainText(jsonResponse, topicStructure);
            }

            return TopicContent.builder()
                    .title(title)
                    .subtopics(subtopics)
                    .build();

        } catch (Exception e) {
            log.error("❌ Alternative parsing failed: {}", e.getMessage());
            return createContentFromPlainText(jsonResponse, topicStructure);
        }
    }

    /**
     * Create content from plain text response when JSON parsing fails
     */
    private TopicContent createContentFromPlainText(String response, TopicStructure topicStructure) {
        log.info("🔧 Creating content from plain text for: {}", topicStructure.getMainTopic());

        // Remove any JSON artifacts
        String cleanedContent = response
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        // Try to extract meaningful content
        if (cleanedContent.length() > 500) {
            cleanedContent = cleanedContent.substring(0, 500) + "...";
        }

        List<SubtopicContent> subtopics = new ArrayList<>();

        if (topicStructure.getSubtopics() != null && !topicStructure.getSubtopics().isEmpty()) {
            // Create one subtopic per expected subtopic
            for (String subtopicTitle : topicStructure.getSubtopics()) {
                subtopics.add(SubtopicContent.builder()
                        .title(subtopicTitle)
                        .description("Content extracted from video transcript")
                        .content(cleanedContent)
                        .images(new ArrayList<>())
                        .tables(new ArrayList<>())
                        .build());
            }
        } else {
            // Create a single summary subtopic
            subtopics.add(SubtopicContent.builder()
                    .title("Summary")
                    .description("Generated content from video transcript")
                    .content(cleanedContent)
                    .images(new ArrayList<>())
                    .tables(new ArrayList<>())
                    .build());
        }

        return TopicContent.builder()
                .title(topicStructure.getMainTopic())
                .subtopics(subtopics)
                .build();
    }

    /**
     * CRITICAL: Sanitize JSON to fix common LLM mistakes
     */
    private String sanitizeAndExtractJson(String response) {
        if (response == null || response.isBlank()) return "{}";

        // Step 1: Remove markdown code fences
        String cleaned = response
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        // Step 2: Extract JSON (object or array)
        int objStart = cleaned.indexOf('{');
        int objEnd = cleaned.lastIndexOf('}');
        int arrStart = cleaned.indexOf('[');
        int arrEnd = cleaned.lastIndexOf(']');

        String extracted;
        if (objStart != -1 && objEnd > objStart && (arrStart == -1 || objStart < arrStart)) {
            extracted = cleaned.substring(objStart, objEnd + 1);
        } else if (arrStart != -1 && arrEnd > arrStart) {
            extracted = cleaned.substring(arrStart, arrEnd + 1);
        } else {
            log.warn("⚠️ Could not find JSON markers");
            return cleaned;
        }

        // Step 3: Fix common JSON errors

        // Fix trailing commas before closing braces/brackets
        extracted = extracted.replaceAll(",\\s*}", "}");
        extracted = extracted.replaceAll(",\\s*]", "]");

        // Fix missing commas between array elements (heuristic)
        extracted = extracted.replaceAll("}\\s*\\{", "},{");
        extracted = extracted.replaceAll("]\\s*\\[", "],[");

        // Fix unescaped quotes in strings (basic attempt)
        // This is tricky and may need refinement

        // Remove comments (if any)
        extracted = extracted.replaceAll("//.*", "");
        extracted = extracted.replaceAll("/\\*.*?\\*/", "");

        // Remove trailing commas in arrays more aggressively
        Pattern trailingCommaPattern = Pattern.compile(",\\s*([}\\]])");
        extracted = trailingCommaPattern.matcher(extracted).replaceAll("$1");

        log.debug("🧹 Sanitized JSON: {}", extracted.substring(0, Math.min(500, extracted.length())));

        return extracted.trim();
    }

    /**
     * Create empty topic content as fallback
     */
    private TopicContent createEmptyTopicContent(TopicStructure topicStructure) {
        List<SubtopicContent> subtopics = new ArrayList<>();

        if (topicStructure.getSubtopics() != null) {
            subtopics = topicStructure.getSubtopics().stream()
                    .map(st -> SubtopicContent.builder()
                            .title(st)
                            .description("Content generation in progress")
                            .content("Detailed content will be added here.")
                            .images(new ArrayList<>())
                            .tables(new ArrayList<>())
                            .build())
                    .collect(Collectors.toList());
        }

        if (subtopics.isEmpty()) {
            subtopics.add(SubtopicContent.builder()
                    .title("Summary")
                    .description("Content not available")
                    .content("Unable to generate content at this time.")
                    .images(new ArrayList<>())
                    .tables(new ArrayList<>())
                    .build());
        }

        return TopicContent.builder()
                .title(topicStructure.getMainTopic())
                .subtopics(subtopics)
                .build();
    }

    /**
     * Create fallback response
     */
    private NotesResponse createFallbackResponse(String transcript, String language) {
        log.info("🔄 Creating fallback response with simple notes");
        try {
            String prompt = Prompts.getSimpleNotesGenerationPrompt(transcript, language);
            String simpleNotes = ollamaService.generate(currentModel, prompt).block();

            TopicContent fallbackTopic = TopicContent.builder()
                    .title("Video Notes")
                    .subtopics(List.of(SubtopicContent.builder()
                            .title("Summary")
                            .description("Generated notes from video transcript")
                            .content(simpleNotes != null ? simpleNotes : "Failed to generate notes")
                            .images(new ArrayList<>())
                            .tables(new ArrayList<>())
                            .build()))
                    .build();

            return NotesResponse.builder()
                    .topics(List.of(fallbackTopic))
                    .language(language)
                    .status("fallback")
                    .build();
        } catch (Exception e) {
            log.error("❌ Fallback also failed: {}", e.getMessage());
            TopicContent emergency = TopicContent.builder()
                    .title("Video Notes")
                    .subtopics(List.of(SubtopicContent.builder()
                            .title("Transcript")
                            .description("Raw transcript from video")
                            .content(transcript == null ? "" :
                                    transcript.substring(0, Math.min(5000, transcript.length())))
                            .images(new ArrayList<>())
                            .tables(new ArrayList<>())
                            .build()))
                    .build();

            return NotesResponse.builder()
                    .topics(List.of(emergency))
                    .language(language)
                    .status("emergency_fallback")
                    .build();
        }
    }

    public String generateImage(String description) {
        log.info("🖼️ Image generation requested for: {}", description);
        return null;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}