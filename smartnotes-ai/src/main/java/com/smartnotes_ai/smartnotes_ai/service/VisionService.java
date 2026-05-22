package com.smartnotes_ai.smartnotes_ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
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

            String text = "Describe this video frame in 1-2 short sentences. " +
                    "Focus on key text, diagrams, or actions visible. " +
                    "Topic context: " + topicHint;

            UserMessage userMessage = UserMessage.builder()
                    .text(text)
                    .media(List.of(media))
                    .build();

            String response = visionModel.call(new Prompt(List.of(userMessage)))
                    .getResult().getOutput().getText();
            return response.trim();
        } catch (Exception e) {
            log.warn("Vision captioning failed for {}: {}", imagePath, e.getMessage());
            return "Screenshot from video.";
        }
    }
}