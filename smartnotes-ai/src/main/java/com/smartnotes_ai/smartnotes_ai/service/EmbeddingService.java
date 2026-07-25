package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient restClient;

    @Value("${smartnotes.ollama.url}")
    private String ollamaUrl;

    @Value("${smartnotes.ollama.embed-model}")
    private String embedModel;

    @PostConstruct
    private void init() {
        this.restClient = RestClient.create();
    }

    public double[] embed(String text) {
        try {
            String requestBody = mapper.writeValueAsString(Map.of(
                    "model", embedModel,
                    "prompt", text == null ? "" : text
            ));

            String response = restClient.post()
                    .uri(ollamaUrl + "/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            JsonNode emb = root.path("embedding");
            if (!emb.isArray()) return new double[0];

            double[] vec = new double[emb.size()];
            for (int i = 0; i < emb.size(); i++) vec[i] = emb.get(i).asDouble();
            return vec;

        } catch (Exception e) {
            log.warn("Embedding request failed | model={} error={}", embedModel, e.getMessage());
            return new double[0];
        }
    }

    public List<double[]> embedAll(List<String> texts) {
        List<double[]> result = new ArrayList<>();
        for (String t : texts) result.add(embed(t));
        return result;
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
