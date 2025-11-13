package com.smartnotes.ai.SmartNotes.AI.service.notes_gen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes.ai.SmartNotes.AI.dto.internal.TopicStructure;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse.ImagePlaceholder;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse.SubtopicContent;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse.TableData;
import com.smartnotes.ai.SmartNotes.AI.dto.response.NotesResponse.TopicContent;
import com.smartnotes.ai.SmartNotes.AI.prompts.Prompts;
import com.smartnotes.ai.SmartNotes.AI.service.ollama.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotesGenerationService {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * Generate structured notes with topics, subtopics, images, and tables
     */
    public NotesResponse generateNotes(String transcript, String language) {
        log.info("==================================================");
        log.info("📚 STARTING MULTI-STEP NOTES GENERATION");
        log.info("==================================================");
        log.info("📊 Transcript length: {} characters", transcript.length());
        log.info("🌐 Language: {}", language);

        try {
            // STEP 1: Extract topics
            log.info("\n🔍 ===== STEP 1: EXTRACTING TOPICS =====");
            List<TopicStructure> topicStructures = extractTopicsAndSubtopics(transcript, language);

            if (topicStructures == null || topicStructures.isEmpty()) {
                log.error("❌ No topics extracted! Falling back to simple notes generation...");
                return createFallbackResponse(transcript, language);
            }

            log.info("✅ Successfully extracted {} main topics:", topicStructures.size());
            for (int i = 0; i < topicStructures.size(); i++) {
                TopicStructure topic = topicStructures.get(i);
                log.info("   {}. {} (with {} subtopics)",
                        i + 1, topic.getMainTopic(), topic.getSubtopics().size());
            }

            // STEP 2: Generate content
            log.info("\n🚀 ===== STEP 2: GENERATING DETAILED CONTENT =====");
            List<TopicContent> topicContents = generateContentForAllTopics(topicStructures, transcript, language);

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
            return createFallbackResponse(transcript, language);
        }
    }

    /**
     * Extract topics with retry logic
     */
    private List<TopicStructure> extractTopicsAndSubtopics(String transcript, String language) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("🎯 Attempt {}/{} - Calling llama3 for topic extraction...", attempt, maxRetries);

                String prompt = Prompts.getTopicExtractionPrompt(transcript, language);
                String response = ollamaService.generate("llama3:latest", prompt).block();

                log.info("📥 Received response from llama3 (length: {} chars)",
                        response != null ? response.length() : 0);

                // Save raw response to file for debugging
                log.info("📄 RAW RESPONSE FROM LLAMA3:");
                log.info("==================== START ====================");
                log.info("{}", response);
                log.info("==================== END ====================");

                if (response == null || response.isBlank()) {
                    log.error("❌ Empty response from llama3!");
                    continue;
                }

                // Try to extract and parse JSON
                String jsonResponse = extractJsonFromResponse(response);
                log.info("📋 Extracted JSON:");
                log.info("{}", jsonResponse);

                List<TopicStructure> topics = objectMapper.readValue(
                        jsonResponse,
                        new TypeReference<List<TopicStructure>>() {}
                );

                if (topics != null && !topics.isEmpty()) {
                    log.info("✅ Successfully parsed {} topics", topics.size());
                    return topics;
                }

                log.warn("⚠️  Parsed empty topics list, retrying...");

            } catch (Exception e) {
                log.error("❌ Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("❌ All attempts exhausted for topic extraction");
                    return null;
                }

                try {
                    Thread.sleep(2000); // Wait 2 seconds before retry
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

        log.info("\n--- Processing Topic {}/{}: {} ---",
                topicIndex, totalTopics, topicStructure.getMainTopic());

        int maxRetries = 2;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("🎯 Attempt {}/{} for topic: {}", attempt, maxRetries, topicStructure.getMainTopic());

                String subtopicsStr = String.join(", ", topicStructure.getSubtopics());
                String prompt = Prompts.getContentGenerationPrompt(
                        topicStructure.getMainTopic(),
                        subtopicsStr,
                        transcript,
                        language
                );

                String response = ollamaService.generate("llama3:latest", prompt).block();

                log.info("📥 Received response for '{}' (length: {} chars)",
                        topicStructure.getMainTopic(),
                        response != null ? response.length() : 0);

                log.info("📄 RAW RESPONSE FOR '{}' :", topicStructure.getMainTopic());
                log.info("==================== START ====================");
                log.info("{}", response);
                log.info("==================== END ====================");

                if (response == null || response.isBlank()) {
                    log.error("❌ Empty response from llama3");
                    continue;
                }

                // Parse with enhanced error handling
                TopicContent content = parseTopicContentFromResponse(response, topicStructure);

                if (content != null) {
                    log.info("✅ Successfully generated content for: {}", content.getTitle());
                    return content;
                }

                log.warn("⚠️  Failed to parse content, retrying...");

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
     * Enhanced JSON parsing with multiple fallback strategies
     */
    private TopicContent parseTopicContentFromResponse(String response, TopicStructure topicStructure) {
        try {
            // Strategy 1: Try to extract and parse JSON normally
            String jsonResponse = extractJsonFromResponse(response);
            log.info("📋 Extracted JSON for '{}': {}", topicStructure.getMainTopic(), jsonResponse);

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // Strategy 2: Flexible field name matching
            String title = extractTitle(rootNode, topicStructure.getMainTopic());
            List<SubtopicContent> subtopics = extractSubtopics(rootNode, topicStructure);

            if (subtopics.isEmpty()) {
                log.warn("⚠️  No subtopics found, creating manual content from response");
                return createContentFromPlainText(response, topicStructure);
            }

            return TopicContent.builder()
                    .title(title)
                    .subtopics(subtopics)
                    .build();

        } catch (Exception e) {
            log.error("❌ JSON parsing failed: {}", e.getMessage());
            log.info("🔄 Falling back to plain text parsing");
            return createContentFromPlainText(response, topicStructure);
        }
    }

    /**
     * Extract title from JSON with multiple field name attempts
     */
    private String extractTitle(JsonNode node, String defaultTitle) {
        // Try different possible field names
        String[] titleFields = {"title", "mainTopic", "topic", "name", "heading"};

        for (String field : titleFields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText();
                if (value != null && !value.isBlank()) {
                    log.debug("✅ Found title in field: {}", field);
                    return value;
                }
            }
        }

        log.warn("⚠️  No title found in JSON, using default: {}", defaultTitle);
        return defaultTitle;
    }

    /**
     * Extract subtopics with flexible parsing
     */
    private List<SubtopicContent> extractSubtopics(JsonNode node, TopicStructure topicStructure) {
        List<SubtopicContent> subtopics = new ArrayList<>();

        // Try different possible field names for subtopics array
        String[] subtopicFields = {"subtopics", "subTopics", "topics", "sections", "content", "items"};

        JsonNode subtopicsNode = null;
        for (String field : subtopicFields) {
            if (node.has(field) && node.get(field).isArray()) {
                subtopicsNode = node.get(field);
                log.debug("✅ Found subtopics array in field: {}", field);
                break;
            }
        }

        if (subtopicsNode == null || !subtopicsNode.isArray()) {
            log.warn("⚠️  No subtopics array found in JSON");
            return subtopics;
        }

        for (int i = 0; i < subtopicsNode.size(); i++) {
            JsonNode subtopicNode = subtopicsNode.get(i);

            try {
                String title = extractFieldValue(subtopicNode,
                        new String[]{"title", "name", "heading", "topic"},
                        "Subtopic " + (i + 1));

                String description = extractFieldValue(subtopicNode,
                        new String[]{"description", "desc", "summary", "overview"},
                        "");

                String content = extractFieldValue(subtopicNode,
                        new String[]{"content", "text", "body", "details", "explanation"},
                        "");

                SubtopicContent subtopic = SubtopicContent.builder()
                        .title(title)
                        .description(description)
                        .content(content)
                        .images(parseImages(subtopicNode.get("imagePositions")))
                        .tables(parseTables(subtopicNode.get("tablePositions")))
                        .build();

                subtopics.add(subtopic);
                log.debug("   ✅ Parsed subtopic: {}", title);

            } catch (Exception e) {
                log.error("❌ Failed to parse subtopic {}: {}", i + 1, e.getMessage());
            }
        }

        return subtopics;
    }

    /**
     * Extract field value trying multiple field names
     */
    private String extractFieldValue(JsonNode node, String[] possibleFields, String defaultValue) {
        for (String field : possibleFields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return defaultValue;
    }

    /**
     * Create content from plain text response when JSON parsing fails
     */
    private TopicContent createContentFromPlainText(String response, TopicStructure topicStructure) {
        log.info("🔧 Creating content from plain text for: {}", topicStructure.getMainTopic());

        // Split response into sections if possible
        String[] sections = response.split("\\n\\n");

        List<SubtopicContent> subtopics = new ArrayList<>();

        // Create subtopics from the structure or from parsed text
        if (topicStructure.getSubtopics() != null && !topicStructure.getSubtopics().isEmpty()) {
            // Use predefined subtopics
            for (String subtopicTitle : topicStructure.getSubtopics()) {
                SubtopicContent subtopic = SubtopicContent.builder()
                        .title(subtopicTitle)
                        .description("Generated from video transcript")
                        .content(response) // Use full response for each
                        .images(new ArrayList<>())
                        .tables(new ArrayList<>())
                        .build();
                subtopics.add(subtopic);
            }
        } else {
            // Create single subtopic with all content
            SubtopicContent subtopic = SubtopicContent.builder()
                    .title("Summary")
                    .description("Generated content from video transcript")
                    .content(response)
                    .images(new ArrayList<>())
                    .tables(new ArrayList<>())
                    .build();
            subtopics.add(subtopic);
        }

        return TopicContent.builder()
                .title(topicStructure.getMainTopic())
                .subtopics(subtopics)
                .build();
    }

    /**
     * Parse images with null safety
     */
    private List<ImagePlaceholder> parseImages(JsonNode imagesNode) {
        List<ImagePlaceholder> images = new ArrayList<>();

        if (imagesNode == null || !imagesNode.isArray()) {
            return images;
        }

        for (JsonNode imageNode : imagesNode) {
            try {
                ImagePlaceholder image = ImagePlaceholder.builder()
                        .position(imageNode.has("position") ? imageNode.get("position").asInt() : 0)
                        .description(imageNode.has("description") ? imageNode.get("description").asText() : "")
                        .imageUrl(null)
                        .placeholder(true)
                        .build();
                images.add(image);
            } catch (Exception e) {
                log.warn("⚠️  Failed to parse image: {}", e.getMessage());
            }
        }

        return images;
    }

    /**
     * Parse tables with null safety
     */
    private List<TableData> parseTables(JsonNode tablesNode) {
        List<TableData> tables = new ArrayList<>();

        if (tablesNode == null || !tablesNode.isArray()) {
            return tables;
        }

        for (JsonNode tableNode : tablesNode) {
            try {
                List<String> headers = new ArrayList<>();
                JsonNode headersNode = tableNode.get("headers");
                if (headersNode != null && headersNode.isArray()) {
                    headersNode.forEach(h -> headers.add(h.asText()));
                }

                List<List<String>> rows = new ArrayList<>();
                JsonNode rowsNode = tableNode.get("rows");
                if (rowsNode != null && rowsNode.isArray()) {
                    for (JsonNode rowNode : rowsNode) {
                        List<String> row = new ArrayList<>();
                        rowNode.forEach(cell -> row.add(cell.asText()));
                        rows.add(row);
                    }
                }

                TableData table = TableData.builder()
                        .position(tableNode.has("position") ? tableNode.get("position").asInt() : 0)
                        .title(tableNode.has("title") ? tableNode.get("title").asText() : "")
                        .headers(headers)
                        .rows(rows)
                        .build();

                tables.add(table);
            } catch (Exception e) {
                log.warn("⚠️  Failed to parse table: {}", e.getMessage());
            }
        }

        return tables;
    }

    /**
     * Enhanced JSON extraction
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return "[]";
        }

        // Remove markdown code blocks
        response = response.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Try to find JSON
        int arrayStart = response.indexOf("[");
        int arrayEnd = response.lastIndexOf("]");
        int objectStart = response.indexOf("{");
        int objectEnd = response.lastIndexOf("}");

        // Prefer array over object
        if (arrayStart != -1 && arrayEnd > arrayStart) {
            return response.substring(arrayStart, arrayEnd + 1);
        } else if (objectStart != -1 && objectEnd > objectStart) {
            return response.substring(objectStart, objectEnd + 1);
        }

        log.warn("⚠️  Could not find JSON markers, returning full response");
        return response;
    }

    /**
     * Create empty topic content as fallback
     */
    private TopicContent createEmptyTopicContent(TopicStructure topicStructure) {
        return TopicContent.builder()
                .title(topicStructure.getMainTopic())
                .subtopics(topicStructure.getSubtopics().stream()
                        .map(st -> SubtopicContent.builder()
                                .title(st)
                                .description("Content generation in progress")
                                .content("Detailed content will be added here.")
                                .images(new ArrayList<>())
                                .tables(new ArrayList<>())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Create fallback response
     */
    private NotesResponse createFallbackResponse(String transcript, String language) {
        log.info("🔄 Creating fallback response with simple notes");

        try {
            String prompt = Prompts.getSimpleNotesGenerationPrompt(transcript, language);
            String simpleNotes = ollamaService.generate("llama3:latest", prompt).block();

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

            // Ultimate fallback
            TopicContent emergency = TopicContent.builder()
                    .title("Video Notes")
                    .subtopics(List.of(SubtopicContent.builder()
                            .title("Transcript")
                            .description("Raw transcript from video")
                            .content(transcript.substring(0, Math.min(5000, transcript.length())))
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