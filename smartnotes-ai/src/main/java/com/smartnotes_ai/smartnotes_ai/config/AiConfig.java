package com.smartnotes_ai.smartnotes_ai.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${smartnotes.ollama.url}")
    private String ollamaUrl;

    @Value("${smartnotes.ollama.text-model}")
    private String textModel;

    @Value("${smartnotes.ollama.vision-model}")
    private String visionModel;

    @Bean
    public OllamaApi ollamaApi() {
        return OllamaApi.builder()
                .baseUrl(ollamaUrl)
                .build();
    }

    @Bean(name = "textChatModel")
    public OllamaChatModel textChatModel(OllamaApi api) {
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(textModel)
                        .temperature(Double.valueOf(0.3))
                        .build())
                .build();
    }

    @Bean(name = "visionChatModel")
    public OllamaChatModel visionChatModel(OllamaApi api) {
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(visionModel)
                        .temperature(Double.valueOf(0.2))
                        .build())
                .build();
    }
}