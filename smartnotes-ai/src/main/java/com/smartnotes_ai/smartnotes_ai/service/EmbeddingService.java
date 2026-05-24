package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EmbeddingService {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${smartnotes.ollama.url}")
    private String ollamaUrl;

    @Value("${smartnotes.ollama.embed-model}")
    private String embedModel;

    /** Get embedding for a single text. Returns empty array on failure. */
    public double[] embed(String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", embedModel);
            body.put("prompt", text == null ? "" : text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);

            String response = rest.postForObject(ollamaUrl + "/api/embeddings", entity, String.class);
            JsonNode root = mapper.readTree(response);
            JsonNode emb = root.path("embedding");
            if (!emb.isArray()) return new double[0];

            double[] vec = new double[emb.size()];
            for (int i = 0; i < emb.size(); i++) vec[i] = emb.get(i).asDouble();
            return vec;
        } catch (Exception e) {
            log.warn("Embedding failed: {}", e.getMessage());
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