package com.smartnotes_ai.smartnotes_ai.service;

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
public class AlignmentService {

    private final OllamaChatModel chatModel;

    public AlignmentService(@Qualifier("textChatModel") OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    private static final int CHUNK_CHAR_LIMIT = 8000;

    private static final String CHUNK_TEMPLATE = """
            You are a technical editor. Below is part {chunkIdx} of {chunkTotal} from a video transcript.
            
            VIDEO DESCRIPTION (context):
            {description}
            
            TRANSCRIPT CHUNK (with timestamps):
            {transcript}
            
            Produce a CLEAN narrative for this chunk:
            - Fix transcription errors
            - Preserve [timestamps] in seconds
            - Keep technical terms accurate
            - 1-2 paragraphs only
            
            Return only the cleaned text.
            """;

    private static final String MERGE_TEMPLATE = """
            Merge these per-chunk summaries into one cohesive narrative document.
            Preserve all [timestamps] and chronological order.
            
            CHUNK SUMMARIES:
            {chunks}
            
            Return the merged narrative only.
            """;

    public String align(List<TranscriptSegment> segments, String description) {
        String fullTranscript = segments.stream()
                .map(s -> String.format(Locale.ROOT, "[%.0fs] %s", s.getStart(), s.getText()))
                .collect(Collectors.joining("\n"));

        log.info("Transcript: {} chars, {} segments", fullTranscript.length(), segments.size());

        // Short transcript? Single pass.
        if (fullTranscript.length() <= CHUNK_CHAR_LIMIT) {
            return alignSingle(fullTranscript, description);
        }

        // Long transcript? Chunk + merge.
        log.info("Long transcript detected, using chunked alignment");
        List<String> chunks = chunkTranscript(fullTranscript, CHUNK_CHAR_LIMIT);
        log.info("Split into {} chunks", chunks.size());

        List<String> chunkSummaries = new ArrayList<>();
        String descSnippet = description == null ? "" :
                (description.length() > 1500 ? description.substring(0, 1500) : description);

        for (int i = 0; i < chunks.size(); i++) {
            log.info("Aligning chunk {}/{}", i + 1, chunks.size());
            String prompt = new PromptTemplate(CHUNK_TEMPLATE).render(Map.of(
                    "chunkIdx", String.valueOf(i + 1),
                    "chunkTotal", String.valueOf(chunks.size()),
                    "description", descSnippet,
                    "transcript", chunks.get(i)
            ));
            chunkSummaries.add(chatModel.call(prompt));
        }

        // Merge step
        String merged = chunkSummaries.stream()
                .map(s -> "---\n" + s)
                .collect(Collectors.joining("\n\n"));

        if (merged.length() < 12000) {
            String mergePrompt = new PromptTemplate(MERGE_TEMPLATE)
                    .render(Map.of("chunks", merged));
            return chatModel.call(mergePrompt);
        }
        return merged;  // already concise enough
    }

    private String alignSingle(String transcript, String description) {
        String desc = description == null ? "" :
                (description.length() > 2000 ? description.substring(0, 2000) : description);
        String template = """
                You are a technical editor. Clean this transcript using the description as context.
                Preserve [timestamps]. Fix errors. Keep chronological order.
                
                DESCRIPTION:
                {description}
                
                TRANSCRIPT:
                {transcript}
                
                Return cleaned narrative only.
                """;
        return chatModel.call(new PromptTemplate(template)
                .render(Map.of("description", desc, "transcript", transcript)));
    }

    private List<String> chunkTranscript(String transcript, int limit) {
        List<String> chunks = new ArrayList<>();
        String[] lines = transcript.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.length() + line.length() > limit && current.length() > 0) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }
}