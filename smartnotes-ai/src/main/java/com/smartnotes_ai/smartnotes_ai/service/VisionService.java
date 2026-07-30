package com.smartnotes_ai.smartnotes_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class VisionService {

    private final OllamaChatModel visionModel;

    public VisionService(@Qualifier("visionChatModel") OllamaChatModel visionModel) {
        this.visionModel = visionModel;
    }

    public String describeFrame(Path imagePath, String topicHint) {
        try {
            Media media = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_JPEG)
                    .data(new FileSystemResource(imagePath))
                    .build();

            String text = "Describe only what is visible in this frame. " +
                    "Output a single plain sentence with no preamble — " +
                    "do not start with 'I can see', 'The image shows', 'In this frame', or similar phrases. " +
                    "Focus on: text on screen, diagrams, UI elements, code, or speaker actions. " +
                    "Topic context: " + topicHint;

            UserMessage userMessage = UserMessage.builder()
                    .text(text)
                    .media(List.of(media))
                    .build();

            // temperature=0.1 for factual captions; numPredict=160 caps at ~2 sentences
            OllamaOptions opts = OllamaOptions.builder()
                    .temperature(0.1)
                    .numPredict(160)
                    .build();
            String response = visionModel.call(new Prompt(List.of(userMessage), opts))
                    .getResult().getOutput().getText();
            return response.trim();
        } catch (Exception e) {
            log.warn("Vision captioning failed for {}: {}", imagePath, e.getMessage());
            return "Screenshot from video.";
        }
    }
}