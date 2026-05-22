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
            You are an expert note-taker. Given the video title, cleaned context, and
            timestamped transcript, divide the content into 4-8 logical TOPICS.
            
            For EACH topic produce JSON with these fields:
              - title: short topic title
              - summary: 2-3 sentence summary
              - bulletPoints: array of 3-6 concise bullet strings
              - startTime: approx start time in seconds
              - endTime: approx end time in seconds
            
            Return ONLY a JSON array. No markdown, no commentary.
            
            VIDEO TITLE: {title}
            
            CLEANED CONTEXT:
            {context}
            
            TIMESTAMPED TRANSCRIPT:
            {transcript}
            """;

    private static final String OVERALL_PROMPT = """
            Write a concise 4-6 sentence overall summary of this video based on these topics.
            VIDEO TITLE: {title}
            TOPICS:
            {topics}
            """;

    public List<TopicSection> generateTopics(List<TranscriptSegment> segments,
                                             String context, String title) {
        String transcript = segments.stream()
                .map(s -> String.format("[%.0f-%.0fs] %s", s.getStart(), s.getEnd(), s.getText()))
                .collect(Collectors.joining("\n"));
        if (transcript.length() > 10000) transcript = transcript.substring(0, 10000);
        if (context.length() > 4000) context = context.substring(0, 4000);

        String prompt = new PromptTemplate(TOPIC_PROMPT).render(Map.of(
                "title", title, "context", context, "transcript", transcript));

        String response = chatModel.call(prompt);
        return parseTopics(response);
    }

    public String overallSummary(List<TopicSection> topics, String title) {
        String t = topics.stream()
                .map(x -> "- " + x.getTitle() + ": " + x.getSummary())
                .collect(Collectors.joining("\n"));
        String prompt = new PromptTemplate(OVERALL_PROMPT).render(Map.of("title", title, "topics", t));
        return chatModel.call(prompt);
    }

    private List<TopicSection> parseTopics(String raw) {
        List<TopicSection> list = new ArrayList<>();
        try {
            int s = raw.indexOf('[');
            int e = raw.lastIndexOf(']');
            if (s == -1 || e == -1) {
                log.error("No JSON array in LLM output: {}", raw);
                return fallback();
            }
            String json = raw.substring(s, e + 1);
            JsonNode arr = mapper.readTree(json);
            for (JsonNode n : arr) {
                TopicSection t = new TopicSection();
                t.setTitle(n.path("title").asText("Untitled"));
                t.setSummary(n.path("summary").asText(""));
                List<String> bullets = new ArrayList<>();
                n.path("bulletPoints").forEach(b -> bullets.add(b.asText()));
                t.setBulletPoints(bullets);
                t.setStartTime(n.path("startTime").asDouble(0));
                t.setEndTime(n.path("endTime").asDouble(0));
                list.add(t);
            }
        } catch (Exception ex) {
            log.error("Failed to parse topics JSON", ex);
            return fallback();
        }
        return list.isEmpty() ? fallback() : list;
    }

    private List<TopicSection> fallback() {
        TopicSection t = new TopicSection();
        t.setTitle("Video Summary");
        t.setSummary("Auto-generation failed; please retry.");
        t.setBulletPoints(List.of("No content available"));
        return List.of(t);
    }
}