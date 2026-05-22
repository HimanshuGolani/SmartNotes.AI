package com.smartnotes_ai.smartnotes_ai.service;

import com.smartnotes_ai.smartnotes_ai.dto.TranscriptSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlignmentService {

    private final OllamaChatModel chatModel;

    public AlignmentService(@Qualifier("textChatModel") OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    private static final String TEMPLATE_WITH_DESC = """
            You are an expert technical editor. Below is a YouTube video's official
            description and the raw transcript segments. Produce a CLEAN, COHERENT
            context document that:
            1. Reconciles the transcript with the description
            2. Fixes obvious transcription errors using the description as ground truth
            3. Preserves chronological structure
            4. Identifies the main themes and terminology

            VIDEO DESCRIPTION:
            {description}

            TRANSCRIPT:
            {transcript}

            Return only the cleaned narrative text (no preamble).
            """;

    private static final String TEMPLATE_NO_DESC = """
            You are an expert technical editor. Below are the raw transcript segments
            of a video. Produce a CLEAN, COHERENT context document that:
            1. Fixes obvious transcription errors
            2. Preserves chronological structure
            3. Identifies the main themes and terminology

            TRANSCRIPT:
            {transcript}

            Return only the cleaned narrative text (no preamble).
            """;

    public String align(List<TranscriptSegment> segments, String description) {

        String transcript = segments.stream()
                .map(s -> "[" + Math.round(s.getStart()) + "s] " + s.getText())
                .collect(Collectors.joining("\n"));

        if (transcript.length() > 12000) {
            transcript = transcript.substring(0, 12000) + "\n...[truncated]";
        }

        String prompt;

        if (description == null || description.isBlank()) {

            log.info("No description available, aligning transcript only");

            prompt = new PromptTemplate(TEMPLATE_NO_DESC)
                    .render(Map.of(
                            "transcript", transcript
                    ));

        } else {

            String desc = description.length() > 3000
                    ? description.substring(0, 3000)
                    : description;

            prompt = new PromptTemplate(TEMPLATE_WITH_DESC)
                    .render(Map.of(
                            "description", desc,
                            "transcript", transcript
                    ));
        }

        return chatModel.call(prompt);
    }
}