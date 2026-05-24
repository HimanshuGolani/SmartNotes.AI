package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes_ai.smartnotes_ai.dto.TopicSection;
import com.smartnotes_ai.smartnotes_ai.dto.TranscriptSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotesGenerationService {

    private final OllamaChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotesGenerationService(@Qualifier("textChatModel") OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    private static final String TOPIC_PROMPT = """
            You are an expert note-taker. Analyze the following part of a video transcript
            and extract 1 to 3 main topic sections (depending on length and content density).
            
            VIDEO TITLE: {title}
            CONTEXT: {context}
            
            TRANSCRIPT WINDOW ({startSec}s - {endSec}s):
            {transcript}
            
            Return ONLY a valid JSON array with this exact structure (no markdown, no preamble):
            [
              {
                "title": "Concise topic title (max 60 chars)",
                "summary": "2-3 sentence summary",
                "bulletPoints": ["key point 1", "key point 2", "key point 3", "key point 4"],
                "startTime": <seconds as number>,
                "endTime": <seconds as number>
              }
            ]
            """;

    private static final String OVERALL_PROMPT = """
            Below are topic-wise notes from a video titled "{title}".
            Write a 3-4 sentence executive summary of the entire video.
            
            TOPICS:
            {topics}
            
            Return only the summary text.
            """;

    public List<TopicSection> generateTopics(List<TranscriptSegment> segments,
                                             String alignedContext,
                                             String title) {
        if (segments.isEmpty()) return List.of();

        double totalDuration = segments.get(segments.size() - 1).getEnd();
        log.info("Generating topics for {}s of video", totalDuration);

        // Adaptive window size: aim for ~5-12 windows max
        int windowSec = Math.max(120, (int) (totalDuration / 8));
        log.info("Using window size: {}s", windowSec);

        List<List<TranscriptSegment>> windows = splitIntoWindows(segments, windowSec);
        log.info("Split into {} time windows", windows.size());

        List<TopicSection> allTopics = new ArrayList<>();
        String contextSnippet = alignedContext.length() > 1500
                ? alignedContext.substring(0, 1500) + "..." : alignedContext;

        for (int i = 0; i < windows.size(); i++) {
            List<TranscriptSegment> window = windows.get(i);
            if (window.isEmpty()) continue;

            double startSec = window.get(0).getStart();
            double endSec = window.get(window.size() - 1).getEnd();

            String windowText = window.stream()
                    .map(s -> String.format(Locale.ROOT, "[%.0fs] %s", s.getStart(), s.getText()))
                    .collect(Collectors.joining("\n"));

            log.info("Generating topics for window {}/{} ({}s-{}s)",
                    i + 1, windows.size(), (int) startSec, (int) endSec);

            try {
                String prompt = new PromptTemplate(TOPIC_PROMPT).render(Map.of(
                        "title", title,
                        "context", contextSnippet,
                        "startSec", String.valueOf((int) startSec),
                        "endSec", String.valueOf((int) endSec),
                        "transcript", windowText
                ));

                String response = chatModel.call(prompt);
                List<TopicSection> windowTopics = parseTopics(response, startSec, endSec);
                allTopics.addAll(windowTopics);
            } catch (Exception e) {
                log.warn("Failed window {}: {}", i + 1, e.getMessage());
            }
        }

        // Deduplicate similar adjacent titles
        return deduplicate(allTopics);
    }

    public String overallSummary(List<TopicSection> topics, String title) {
        String topicsText = topics.stream()
                .map(t -> "- " + t.getTitle() + ": " + t.getSummary())
                .collect(Collectors.joining("\n"));
        String prompt = new PromptTemplate(OVERALL_PROMPT)
                .render(Map.of("title", title, "topics", topicsText));
        return chatModel.call(prompt);
    }

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

    private List<TopicSection> parseTopics(String response, double fallbackStart, double fallbackEnd) {
        List<TopicSection> topics = new ArrayList<>();
        try {
            // Strip markdown fences if present
            String json = response.trim();
            int firstBracket = json.indexOf('[');
            int lastBracket = json.lastIndexOf(']');
            if (firstBracket >= 0 && lastBracket > firstBracket) {
                json = json.substring(firstBracket, lastBracket + 1);
            }
            JsonNode arr = mapper.readTree(json);
            for (JsonNode node : arr) {
                TopicSection t = new TopicSection();
                t.setTitle(node.path("title").asText("Topic"));
                t.setSummary(node.path("summary").asText(""));
                List<String> bullets = new ArrayList<>();
                for (JsonNode bp : node.path("bulletPoints")) bullets.add(bp.asText());
                t.setBulletPoints(bullets);
                t.setStartTime(node.path("startTime").asDouble(fallbackStart));
                t.setEndTime(node.path("endTime").asDouble(fallbackEnd));
                topics.add(t);
            }
        } catch (Exception e) {
            log.warn("Failed to parse topic JSON, using fallback: {}", e.getMessage());
            TopicSection t = new TopicSection();
            t.setTitle("Section " + (int) fallbackStart + "s");
            t.setSummary(response.length() > 300 ? response.substring(0, 300) : response);
            t.setBulletPoints(List.of());
            t.setStartTime(fallbackStart);
            t.setEndTime(fallbackEnd);
            topics.add(t);
        }
        return topics;
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