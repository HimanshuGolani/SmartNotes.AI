package com.smartnotes.ai.SmartNotes.AI.service.ollama;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
public class OllamaService {

    private WebClient webClient;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @PostConstruct
    public void init() {
        this.webClient = WebClient
                .builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<String> generate(String model, String prompt) {
        log.info("📤 Sending request to Ollama: model={}", model);

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(Map.of(
                        "model", model,
                        "prompt", prompt,
                        "stream", false
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("response"))
                .doOnError(error -> log.error("❌ Ollama API error: {}", error.getMessage()));
    }
}