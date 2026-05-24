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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class ExcalidrawMcpService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Random rnd = new Random();

    @Value("${smartnotes.workspace}")
    private String workspace;

    // Colorful palette (Excalidraw-friendly)
    private static final String[][] PALETTE = {
            {"#a5d8ff", "#1971c2"},  // blue
            {"#b2f2bb", "#2f9e44"},  // green
            {"#ffd8a8", "#e8590c"},  // orange
            {"#ffc9c9", "#e03131"},  // red
            {"#d0bfff", "#7048e8"},  // purple
            {"#ffec99", "#f08c00"},  // yellow
            {"#c5f6fa", "#0c8599"},  // teal
            {"#fcc2d7", "#c2255c"},  // pink
    };

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

            // === TITLE BANNER (gradient-like header) ===
            String titleBgId = UUID.randomUUID().toString();
            elements.add(rectangle(titleBgId, 100, 50, 1100, 90,
                    "#1864ab", "#a5d8ff", "solid", 3, 0, 0));
            elements.add(textElement(meta.getTitle(), 120, 70, 26, 1060, 40,
                    "#0b3d7a", "left", 800));
            elements.add(textElement("by " + (meta.getUploader() == null ? "unknown" : meta.getUploader())
                            + "  •  " + (int) meta.getDuration() + "s",
                    120, 105, 13, 1060, 25, "#1864ab", "left", 400));

            // === OVERVIEW BUBBLE (yellow sticky-note style) ===
            String overviewBoxId = UUID.randomUUID().toString();
            elements.add(rectangle(overviewBoxId, 100, 170, 1100, 120,
                    "#fff3bf", "#fab005", "cross-hatch", 2, 5, 0));
            elements.add(textElement("📋 OVERVIEW", 120, 185, 14, 200, 25,
                    "#e67700", "left", 700));
            String overview = overallSummary == null ? "" : overallSummary;
            if (overview.length() > 600) overview = overview.substring(0, 600) + "...";
            elements.add(textElement(overview, 120, 215, 12, 1060, 70,
                    "#5c3a09", "left", 400));

            // === CENTER NODE (hub of the mind map) ===
            int hubX = 600, hubY = 380, hubW = 220, hubH = 100;
            String hubId = UUID.randomUUID().toString();
            elements.add(ellipse(hubId, hubX, hubY, hubW, hubH,
                    "#7048e8", "#d0bfff", "solid", 4));
            String hubTitle = meta.getTitle();
            if (hubTitle.length() > 40) hubTitle = hubTitle.substring(0, 40) + "...";
            elements.add(textElement("🎯 " + hubTitle, hubX + 10, hubY + 35, 13, hubW - 20, 30,
                    "#3b1d80", "center", 700));

            // === TOPIC NODES (radial layout) ===
            int n = topics.size();
            int radiusX = 480;
            int radiusY = 320;
            int hubCenterX = hubX + hubW / 2;
            int hubCenterY = hubY + hubH / 2;

            for (int i = 0; i < n; i++) {
                TopicSection t = topics.get(i);
                String[] colors = PALETTE[i % PALETTE.length];
                String fill = colors[0];
                String stroke = colors[1];

                // Compute radial position around hub
                double angle = 2 * Math.PI * i / Math.max(n, 1) - Math.PI / 2;
                int boxW = 360, boxH = 220;
                int boxCx = hubCenterX + (int) (radiusX * Math.cos(angle));
                int boxCy = hubCenterY + (int) (radiusY * Math.sin(angle));
                int boxX = boxCx - boxW / 2;
                int boxY = boxCy - boxH / 2;

                // Push to a clean grid below hub if too cramped
                if (n > 6) {
                    int cols = 3;
                    int row = i / cols;
                    int col = i % cols;
                    boxX = 100 + col * 380;
                    boxY = 550 + row * 260;
                    boxCx = boxX + boxW / 2;
                    boxCy = boxY + boxH / 2;
                }

                // Topic box (rounded rectangle)
                String boxId = UUID.randomUUID().toString();
                elements.add(rectangle(boxId, boxX, boxY, boxW, boxH,
                        fill, stroke, "hachure", 2, 12, 0));

                // Topic number badge
                String badgeId = UUID.randomUUID().toString();
                elements.add(ellipse(badgeId, boxX - 18, boxY - 18, 40, 40,
                        stroke, "#ffffff", "solid", 2));
                elements.add(textElement(String.valueOf(i + 1), boxX - 13, boxY - 12, 18, 30, 30,
                        "#ffffff", "center", 700));

                // Topic title
                String titleStr = (t.getTitle() == null ? "Topic" : t.getTitle());
                if (titleStr.length() > 50) titleStr = titleStr.substring(0, 50) + "...";
                elements.add(textElement(titleStr, boxX + 16, boxY + 14, 15, boxW - 32, 28,
                        stroke, "left", 700));

                // Time range pill
                String timeRange = String.format("⏱ %ds - %ds",
                        (int) t.getStartTime(), (int) t.getEndTime());
                elements.add(textElement(timeRange, boxX + 16, boxY + 44, 10, boxW - 32, 16,
                        stroke, "left", 400));

                // Summary
                String summary = t.getSummary() == null ? "" : t.getSummary();
                if (summary.length() > 220) summary = summary.substring(0, 220) + "...";
                elements.add(textElement(summary, boxX + 16, boxY + 64, 11, boxW - 32, 60,
                        "#1a1a1a", "left", 400));

                // Bullet points (compact)
                StringBuilder bullets = new StringBuilder();
                if (t.getBulletPoints() != null) {
                    int max = Math.min(4, t.getBulletPoints().size());
                    for (int b = 0; b < max; b++) {
                        String bp = t.getBulletPoints().get(b);
                        if (bp.length() > 60) bp = bp.substring(0, 60) + "...";
                        bullets.append("• ").append(bp).append("\n");
                    }
                }
                elements.add(textElement(bullets.toString(), boxX + 16, boxY + 130, 10, boxW - 32, 80,
                        "#212121", "left", 400));

                // === ARROW from HUB to topic box ===
                int targetX = boxX + boxW / 2;
                int targetY = boxY + boxH / 2;
                elements.add(arrow(hubCenterX, hubCenterY, targetX, targetY, stroke));
            }

            // === LEGEND / FOOTER ===
            int footerY = (n > 6 ? 550 + ((n + 2) / 3) * 260 + 40 : 1000);
            elements.add(textElement(
                    "🎨 Mind-map generated by SmartNotes.ai  •  " + n + " topics  •  Drag to rearrange",
                    100, footerY, 12, 1100, 30, "#868e96", "center", 400));

            root.set("elements", elements);

            ObjectNode appState = mapper.createObjectNode();
            appState.put("viewBackgroundColor", "#fafafa");
            appState.putNull("gridSize");
            root.set("appState", appState);
            root.set("files", mapper.createObjectNode());

            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root));
            log.info("Excalidraw mind-map generated at: {}", file);
            return file;
        } catch (Exception e) {
            throw new RuntimeException("Excalidraw export failed", e);
        }
    }

    // ===== Element factories =====

    private ObjectNode rectangle(String id, int x, int y, int w, int h,
                                 String bg, String stroke, String fillStyle,
                                 int strokeWidth, int roundness, int angle) {
        ObjectNode n = baseShape(id, "rectangle", x, y, w, h, stroke, bg, fillStyle, strokeWidth);
        n.put("angle", angle);
        if (roundness > 0) {
            ObjectNode r = mapper.createObjectNode();
            r.put("type", 3);
            r.put("value", roundness);
            n.set("roundness", r);
        } else {
            n.putNull("roundness");
        }
        return n;
    }

    private ObjectNode ellipse(String id, int x, int y, int w, int h,
                               String stroke, String bg, String fillStyle, int strokeWidth) {
        ObjectNode n = baseShape(id, "ellipse", x, y, w, h, stroke, bg, fillStyle, strokeWidth);
        n.put("angle", 0);
        n.putNull("roundness");
        return n;
    }

    private ObjectNode baseShape(String id, String type, int x, int y, int w, int h,
                                 String stroke, String bg, String fillStyle, int strokeWidth) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("type", type);
        n.put("x", x); n.put("y", y);
        n.put("width", w); n.put("height", h);
        n.put("strokeColor", stroke);
        n.put("backgroundColor", bg);
        n.put("fillStyle", fillStyle);
        n.put("strokeWidth", strokeWidth);
        n.put("strokeStyle", "solid");
        n.put("roughness", 1);
        n.put("opacity", 100);
        n.put("seed", rnd.nextInt(100000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(100000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        n.set("link", null);
        n.put("locked", false);
        return n;
    }

    private ObjectNode textElement(String text, int x, int y, int fontSize, int width, int height,
                                   String color, String align, int weight) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("type", "text");
        n.put("x", x); n.put("y", y);
        n.put("width", width); n.put("height", height);
        n.put("text", text == null ? "" : text);
        n.put("fontSize", fontSize);
        n.put("fontFamily", weight >= 700 ? 3 : 1);  // 3 = Cascadia (bold-ish), 1 = Virgil
        n.put("textAlign", align);
        n.put("verticalAlign", "top");
        n.put("strokeColor", color);
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", 1);
        n.put("strokeStyle", "solid");
        n.put("roughness", 0);
        n.put("opacity", 100);
        n.put("angle", 0);
        n.put("seed", rnd.nextInt(100000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(100000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        n.set("link", null);
        n.put("locked", false);
        n.set("containerId", null);
        n.set("originalText", n.get("text"));
        n.set("lineHeight", mapper.getNodeFactory().numberNode(1.25));
        n.put("baseline", fontSize - 2);
        n.putNull("roundness");
        return n;
    }

    private ObjectNode arrow(int x1, int y1, int x2, int y2, String color) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("type", "arrow");
        n.put("x", x1); n.put("y", y1);
        n.put("width", x2 - x1); n.put("height", y2 - y1);
        n.put("strokeColor", color);
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", 2);
        n.put("strokeStyle", "solid");
        n.put("roughness", 1);
        n.put("opacity", 80);
        n.put("angle", 0);
        n.put("seed", rnd.nextInt(100000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(100000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        n.set("link", null);
        n.put("locked", false);

        ArrayNode pts = mapper.createArrayNode();
        ArrayNode p1 = mapper.createArrayNode();
        p1.add(0); p1.add(0);
        ArrayNode p2 = mapper.createArrayNode();
        p2.add(x2 - x1); p2.add(y2 - y1);
        pts.add(p1); pts.add(p2);
        n.set("points", pts);
        n.set("lastCommittedPoint", null);
        n.set("startBinding", null);
        n.set("endBinding", null);
        n.put("startArrowhead", (String) null);

        ObjectNode endHead = mapper.createObjectNode();
        n.put("endArrowhead", "arrow");

        n.set("elbowed", mapper.getNodeFactory().booleanNode(false));
        n.putNull("roundness");
        return n;
    }
}