package com.smartnotes_ai.smartnotes_ai.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartnotes_ai.smartnotes_ai.dto.TopicSection;
import com.smartnotes_ai.smartnotes_ai.dto.VideoMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ExcalidrawMcpService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${smartnotes.workspace}")
    private String workspace;

    public Path export(VideoMetadata meta, String overallSummary, List<TopicSection> topics) {
        Path outDir = Path.of(workspace, meta.getVideoId());
        Path file = outDir.resolve("notes.excalidraw");

        try {
            Files.createDirectories(outDir);

            ObjectNode root = mapper.createObjectNode();
            root.put("type", "excalidraw");
            root.put("version", 2);
            root.put("source", "smartnotes-ai");

            ArrayNode elements = mapper.createArrayNode();

            int x = 100, y = 100;

            // Title
            elements.add(textElement(meta.getTitle(), x, y, 28, 700, 50));
            y += 80;

            // Summary
            elements.add(textElement(overallSummary, x, y, 14, 700, 100));
            y += 140;

            // Topic boxes
            for (int i = 0; i < topics.size(); i++) {
                TopicSection t = topics.get(i);
                int boxX = x + (i % 2) * 380;
                int boxY = y + (i / 2) * 240;

                elements.add(rectangle(boxX, boxY, 340, 200));
                elements.add(textElement((i + 1) + ". " + t.getTitle(),
                        boxX + 10, boxY + 10, 16, 320, 30));
                elements.add(textElement(t.getSummary(),
                        boxX + 10, boxY + 50, 12, 320, 60));

                StringBuilder bullets = new StringBuilder();
                if (t.getBulletPoints() != null) {
                    for (String b : t.getBulletPoints()) bullets.append("• ").append(b).append("\n");
                }
                elements.add(textElement(bullets.toString(),
                        boxX + 10, boxY + 115, 11, 320, 80));
            }

            root.set("elements", elements);
            root.set("appState", mapper.createObjectNode()
                    .put("viewBackgroundColor", "#ffffff").putNull("gridSize"));
            root.set("files", mapper.createObjectNode());

            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            log.info("Excalidraw file generated at: {}", file);
            return file;
        } catch (Exception e) {
            throw new RuntimeException("Excalidraw export failed", e);
        }
    }

    private ObjectNode textElement(String text, int x, int y, int fontSize, int width, int height) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("type", "text");
        n.put("x", x); n.put("y", y);
        n.put("width", width); n.put("height", height);
        n.put("text", text == null ? "" : text);
        n.put("fontSize", fontSize);
        n.put("fontFamily", 1);
        n.put("textAlign", "left");
        n.put("verticalAlign", "top");
        n.put("strokeColor", "#000000");
        n.put("backgroundColor", "transparent");
        n.put("seed", (int)(Math.random()*100000));
        n.put("version", 1);
        n.put("versionNonce", (int)(Math.random()*100000));
        n.put("isDeleted", false);
        n.put("opacity", 100);
        n.put("angle", 0);
        n.put("strokeWidth", 1);
        n.put("strokeStyle", "solid");
        n.put("roughness", 1);
        n.put("fillStyle", "solid");
        return n;
    }

    private ObjectNode rectangle(int x, int y, int w, int h) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("type", "rectangle");
        n.put("x", x); n.put("y", y);
        n.put("width", w); n.put("height", h);
        n.put("strokeColor", "#1971c2");
        n.put("backgroundColor", "#a5d8ff");
        n.put("fillStyle", "hachure");
        n.put("strokeWidth", 2);
        n.put("strokeStyle", "solid");
        n.put("roughness", 1);
        n.put("opacity", 30);
        n.put("angle", 0);
        n.put("seed", (int)(Math.random()*100000));
        n.put("version", 1);
        n.put("versionNonce", (int)(Math.random()*100000));
        n.put("isDeleted", false);
        return n;
    }
}