package com.smartnotes_ai.smartnotes_ai.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartnotes_ai.smartnotes_ai.dto.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${smartnotes.whisper.url}")
    private String whisperUrl;

    public List<TranscriptSegment> transcribe(Path audioFile) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));

            String response = restClient.post()
                    .uri(whisperUrl + "/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            JsonNode segs = root.path("segments");
            List<TranscriptSegment> result = new ArrayList<>();
            for (JsonNode s : segs) {
                result.add(new TranscriptSegment(
                        s.path("start").asDouble(),
                        s.path("end").asDouble(),
                        s.path("text").asText().trim()
                ));
            }
            log.info("Transcribed {} segments", result.size());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Transcription failed", e);
        }
    }
}