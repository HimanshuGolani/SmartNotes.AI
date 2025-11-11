package com.smartnotes.ai.SmartNotes.AI.service.ollama;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class OllamaService {

    private WebClient webClient;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @PostConstruct
    public void init(){
        this.webClient = WebClient
                .builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    public Mono<String> generate(String model, String prompt){
        return webClient.post()
                .uri("/api/generate")
                .bodyValue(Map.of(
                        "model",model,
                        "prompt",prompt
                ))
                .retrieve()
                .bodyToMono(String.class);
                .
    }

}
