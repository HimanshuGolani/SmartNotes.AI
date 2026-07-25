package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes_ai.smartnotes_ai.dto.TopicSection;
import com.smartnotes_ai.smartnotes_ai.dto.TranscriptSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotesGenerationService {

    private final OllamaChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotesGenerationService(@Qualifier("textChatModel") OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // Use simple placeholders like %%TITLE%% that won't conflict with JSON braces
    private static final String TOPIC_PROMPT = """
            You are an expert note-taker. Analyze the following part of a video transcript
            and extract 1 to 3 main topic sections.

            VIDEO TITLE: %%TITLE%%
            CONTEXT: %%CONTEXT%%

            TRANSCRIPT WINDOW (%%START%%s - %%END%%s):
            %%TRANSCRIPT%%

            CRITICAL: Return ONLY a valid JSON array. No markdown fences. No explanation. No preamble.
            Start your response with [ and end with ].

            Required structure:
            [
              {
                "title": "Concise topic title (max 60 chars)",
                "summary": "2-3 sentence summary of what is discussed",
                "bulletPoints": ["key point 1", "key point 2", "key point 3", "key point 4"],
                "startTime": <number>,
                "endTime": <number>
              }
            ]
            """;

    private static final String OVERALL_PROMPT = """
            Below are topic-wise notes from a video titled "%%TITLE%%".
            Write a 3-4 sentence executive summary of the entire video.

            TOPICS:
            %%TOPICS%%

            Return only the summary text, no preamble.
            """;

    public List<TopicSection> generateTopics(List<TranscriptSegment> segments,
                                             String alignedContext,
                                             String title) {
        if (segments == null || segments.isEmpty()) {
            log.warn("generateTopics skipped | reason=no-segments");
            return List.of();
        }

        double totalDuration = segments.get(segments.size() - 1).getEnd();
        log.info("generateTopics | duration={}s segments={}", (int) totalDuration, segments.size());

        int windowSec = Math.max(60, (int) (totalDuration / 8));
        log.info("Window config | windowSec={}", windowSec);

        List<List<TranscriptSegment>> windows = splitIntoWindows(segments, windowSec);
        log.info("Windows created | count={}", windows.size());

        List<TopicSection> allTopics = new ArrayList<>();
        String contextSnippet = alignedContext == null ? "" :
                (alignedContext.length() > 1500 ? alignedContext.substring(0, 1500) : alignedContext);

        for (int i = 0; i < windows.size(); i++) {
            List<TranscriptSegment> window = windows.get(i);
            if (window.isEmpty()) continue;

            double startSec = window.get(0).getStart();
            double endSec = window.get(window.size() - 1).getEnd();

            String windowText = window.stream()
                    .map(s -> String.format(Locale.ROOT, "[%.0fs] %s", s.getStart(), s.getText()))
                    .collect(Collectors.joining("\n"));

            log.info("Processing window | index={}/{} start={}s end={}s chars={}",
                    i + 1, windows.size(), (int) startSec, (int) endSec, windowText.length());

            try {
                String prompt = TOPIC_PROMPT
                        .replace("%%TITLE%%", title == null ? "" : title)
                        .replace("%%CONTEXT%%", contextSnippet)
                        .replace("%%START%%", String.valueOf((int) startSec))
                        .replace("%%END%%", String.valueOf((int) endSec))
                        .replace("%%TRANSCRIPT%%", windowText);

                String response = chatModel.call(prompt);
                log.debug("LLM response | window={} chars={}",
                        i + 1, response.length());

                List<TopicSection> windowTopics = parseTopics(response, startSec, endSec, windowText);
                if (windowTopics.isEmpty()) {
                    log.warn("No topics parsed | window={} using=fallback", i + 1);
                    windowTopics.add(createFallbackTopic(startSec, endSec, windowText, response));
                }
                log.info("Topics extracted | window={} count={}", i + 1, windowTopics.size());
                allTopics.addAll(windowTopics);
            } catch (Exception e) {
                log.error("Window processing failed | window={} error={}", i + 1, e.getMessage(), e);
                allTopics.add(createFallbackTopic(startSec, endSec, windowText, ""));
            }
        }

        List<TopicSection> deduped = deduplicate(allTopics);
        log.info("Topics finalized | total={} (after dedup)", deduped.size());
        return deduped;
    }

    public String overallSummary(List<TopicSection> topics, String title) {
        if (topics == null || topics.isEmpty()) {
            return "Summary unavailable - no topics extracted.";
        }
        String topicsText = topics.stream()
                .map(t -> "- " + t.getTitle() + ": " + (t.getSummary() == null ? "" : t.getSummary()))
                .collect(Collectors.joining("\n"));
        try {
            String prompt = OVERALL_PROMPT
                    .replace("%%TITLE%%", title == null ? "" : title)
                    .replace("%%TOPICS%%", topicsText);
            return chatModel.call(prompt);
        } catch (Exception e) {
            log.warn("Overall summary failed: {}", e.getMessage());
            return "This video covers: " + topics.stream()
                    .map(TopicSection::getTitle)
                    .limit(5)
                    .collect(Collectors.joining(", ")) + ".";
        }
    }

    // ============ HELPERS (unchanged) ============

    private List<List<TranscriptSegment>> splitIntoWindows(List<TranscriptSegment> segments, int windowSec) {
        List<List<TranscriptSegment>> windows = new ArrayList<>();
        if (segments.isEmpty()) return windows;

        List<TranscriptSegment> current = new ArrayList<>();
        double windowStart = segments.get(0).getStart();

        for (TranscriptSegment seg : segments) {
            if (seg.getStart() - windowStart >= windowSec && !current.isEmpty()) {
                windows.add(current);
                current = new ArrayList<>();
                windowStart = seg.getStart();
            }
            current.add(seg);
        }
        if (!current.isEmpty()) windows.add(current);
        return windows;
    }

    private List<TopicSection> parseTopics(String response, double fallbackStart, double fallbackEnd, String windowText) {
        List<TopicSection> topics = new ArrayList<>();
        if (response == null || response.isBlank()) return topics;

        String jsonCandidate = extractJsonArray(response);
        if (jsonCandidate != null) {
            try {
                JsonNode arr = mapper.readTree(jsonCandidate);
                if (arr.isArray()) {
                    for (JsonNode node : arr) {
                        TopicSection t = nodeToTopic(node, fallbackStart, fallbackEnd);
                        if (t != null) topics.add(t);
                    }
                }
                if (!topics.isEmpty()) return topics;
            } catch (Exception e) {
                log.debug("Strategy 1 (regex JSON) failed: {}", e.getMessage());
            }
        }

        String objCandidate = extractJsonObject(response);
        if (objCandidate != null) {
            try {
                JsonNode obj = mapper.readTree(objCandidate);
                TopicSection t = nodeToTopic(obj, fallbackStart, fallbackEnd);
                if (t != null) topics.add(t);
                if (!topics.isEmpty()) return topics;
            } catch (Exception e) {
                log.debug("Strategy 2 (single object) failed: {}", e.getMessage());
            }
        }

        String cleaned = cleanJson(response);
        if (cleaned != null && !cleaned.equals(response)) {
            try {
                JsonNode arr = mapper.readTree(cleaned);
                if (arr.isArray()) {
                    for (JsonNode node : arr) {
                        TopicSection t = nodeToTopic(node, fallbackStart, fallbackEnd);
                        if (t != null) topics.add(t);
                    }
                }
                if (!topics.isEmpty()) return topics;
            } catch (Exception e) {
                log.debug("Strategy 3 (cleaned JSON) failed: {}", e.getMessage());
            }
        }

        log.warn("All JSON parse strategies failed. Raw response (first 300 chars): {}",
                response.length() > 300 ? response.substring(0, 300) : response);
        return topics;
    }

    private String extractJsonArray(String text) {
        int first = text.indexOf('[');
        int last = text.lastIndexOf(']');
        if (first >= 0 && last > first) {
            return text.substring(first, last + 1);
        }
        return null;
    }

    private String extractJsonObject(String text) {
        Pattern p = Pattern.compile("\\{[^{}]*\"title\"[^{}]*\\}", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) return m.group();
        return null;
    }

    private String cleanJson(String text) {
        if (text == null) return null;
        String s = text;
        s = s.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("```", "");
        int idx = s.indexOf('[');
        if (idx > 0) s = s.substring(idx);
        int last = s.lastIndexOf(']');
        if (last > 0 && last < s.length() - 1) s = s.substring(0, last + 1);
        s = s.replaceAll(",(\\s*[\\]}])", "$1");
        s = s.replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"');
        return s.trim();
    }

    private TopicSection nodeToTopic(JsonNode node, double fallbackStart, double fallbackEnd) {
        if (node == null || !node.isObject()) return null;
        TopicSection t = new TopicSection();
        t.setTitle(node.path("title").asText("Topic"));
        t.setSummary(node.path("summary").asText(""));

        List<String> bullets = new ArrayList<>();
        JsonNode bp = node.path("bulletPoints");
        if (bp.isArray()) {
            for (JsonNode b : bp) bullets.add(b.asText());
        }
        t.setBulletPoints(bullets);

        t.setStartTime(node.path("startTime").asDouble(fallbackStart));
        t.setEndTime(node.path("endTime").asDouble(fallbackEnd));

        if (t.getTitle() == null || t.getTitle().isBlank()) return null;
        return t;
    }

    private TopicSection createFallbackTopic(double startSec, double endSec, String windowText, String llmRaw) {
        TopicSection t = new TopicSection();
        t.setTitle(String.format("Section %ds-%ds", (int) startSec, (int) endSec));

        String summary;
        if (llmRaw != null && !llmRaw.isBlank() && llmRaw.length() < 1000) {
            summary = llmRaw.replaceAll("[\\[\\]{}]", "").trim();
            if (summary.length() > 250) summary = summary.substring(0, 250) + "...";
        } else {
            String snippet = windowText.replaceAll("\\[\\d+s\\]\\s*", "").trim();
            summary = snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet;
        }
        t.setSummary(summary);

        List<String> bullets = new ArrayList<>();
        String[] sentences = windowText.replaceAll("\\[\\d+s\\]\\s*", "").split("[.!?]+");
        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.length() > 20 && trimmed.length() < 150) {
                bullets.add(trimmed);
                if (bullets.size() >= 4) break;
            }
        }
        t.setBulletPoints(bullets);
        t.setStartTime(startSec);
        t.setEndTime(endSec);
        return t;
    }

    private List<TopicSection> deduplicate(List<TopicSection> topics) {
        List<TopicSection> result = new ArrayList<>();
        String lastTitle = "";
        for (TopicSection t : topics) {
            if (t.getTitle() == null) continue;
            String norm = t.getTitle().toLowerCase().trim();
            if (norm.equals(lastTitle)) continue;
            result.add(t);
            lastTitle = norm;
        }
        return result;
    }
}