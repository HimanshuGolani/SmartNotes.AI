package com.smartnotes_ai.smartnotes_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class ExcalidrawMcpService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Random rnd = new Random();
    private final EmbeddingService embeddingService;

    @Value("${smartnotes.workspace}")
    private String workspace;

    public ExcalidrawMcpService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    // ============== SEMANTIC THEME PALETTE ==============
    private static final List<Theme> THEMES = List.of(
            new Theme("technical", Set.of("code", "function", "class", "api", "method", "library",
                    "framework", "implementation", "compile", "build", "deploy", "server",
                    "database", "query", "algorithm", "data structure", "syntax", "variable"),
                    new String[]{"#a5d8ff", "#1971c2", "#0b3d7a", "#e7f5ff"}),
            new Theme("conceptual", Set.of("concept", "theory", "idea", "principle", "definition",
                    "meaning", "abstract", "framework", "model", "paradigm", "philosophy"),
                    new String[]{"#d0bfff", "#7048e8", "#3b1d80", "#f3f0ff"}),
            new Theme("process", Set.of("step", "process", "workflow", "procedure", "method",
                    "approach", "strategy", "phase", "stage", "pipeline", "iteration"),
                    new String[]{"#b2f2bb", "#2f9e44", "#1b5e20", "#ebfbee"}),
            new Theme("warning", Set.of("warning", "caution", "issue", "problem", "error",
                    "bug", "fail", "broken", "danger", "risk", "pitfall", "mistake"),
                    new String[]{"#ffc9c9", "#e03131", "#7a0e0e", "#fff5f5"}),
            new Theme("tip", Set.of("tip", "trick", "advice", "recommend", "best practice",
                    "hint", "useful", "helpful", "remember", "note"),
                    new String[]{"#ffec99", "#f08c00", "#7a4500", "#fff9db"}),
            new Theme("example", Set.of("example", "demo", "demonstration", "instance",
                    "case study", "illustration", "sample", "scenario"),
                    new String[]{"#ffd8a8", "#e8590c", "#7a2e0a", "#fff4e6"}),
            new Theme("comparison", Set.of("vs", "versus", "compare", "difference", "similar",
                    "contrast", "alternative", "choice", "option", "tradeoff"),
                    new String[]{"#c5f6fa", "#0c8599", "#08484f", "#e3fafc"}),
            new Theme("general", Set.of(),
                    new String[]{"#fcc2d7", "#c2255c", "#6b1233", "#fff0f6"})
    );

    private record Theme(String name, Set<String> keywords, String[] colors) {}

    // ============== LAYOUT CONSTANTS (tuned for arrow clearance) ==============
    private static final int CANVAS_PAD = 80;
    private static final int TOPIC_W = 440;          // wider cards
    private static final int TOPIC_H = 260;          // taller for content
    private static final int H_GAP = 220;            // huge horizontal gap for arrow channels
    private static final int V_GAP = 280;            // huge vertical gap for hub arrow routing
    private static final int HUB_W = 280;
    private static final int HUB_H = 110;
    private static final int LEGEND_W = 240;
    private static final int LEGEND_PAD = 24;
    private static final int FRAME_PAD = 40;         // more frame breathing room
    private static final int BULLET_W = 200;
    private static final int BULLET_H = 78;
    private static final int BULLET_GAP = 20;
    private static final int STICKY_OFFSET = 90;     // gap between card and sticky column
    private static final double SIMILARITY_THRESHOLD = 0.72;

    // ============== PUBLIC ENTRY ==============
    public Path export(VideoMetadata meta, String overallSummary, List<TopicSection> topics) {
        return export(meta, overallSummary, topics, null);
    }

    public Path export(VideoMetadata meta, String overallSummary, List<TopicSection> topics,
                       Path pdfPath) {
        Path outDir = Path.of(workspace, meta.getVideoId());
        Path file = outDir.resolve("notes.excalidraw");
        try {
            Files.createDirectories(outDir);

            // We accumulate elements into THREE buckets to control z-order:
            //   1. arrowsLayer  — drawn first  → bottom of stack
            //   2. shapesLayer  — drawn second → middle
            //   3. textLayer    — drawn last   → top (text never hidden by arrows)
            ArrayNode arrowsLayer = mapper.createArrayNode();
            ArrayNode shapesLayer = mapper.createArrayNode();
            ArrayNode textLayer   = mapper.createArrayNode();

            if (topics == null) topics = List.of();
            int n = topics.size();

            // ============ SEMANTIC ANALYSIS ============
            log.info("Computing semantic themes & embeddings for {} topics...", n);
            List<Theme> assignedThemes = assignThemes(topics);
            List<double[]> topicEmbeddings = computeEmbeddings(topics);
            List<int[]> semanticLinks = findSemanticLinks(topicEmbeddings);
            log.info("Detected {} semantic cross-references", semanticLinks.size());

            // ============ LAYOUT ============
            LayoutStrategy strategy = (n > 8) ? LayoutStrategy.HIERARCHICAL : LayoutStrategy.GRID;
            log.info("Excalidraw layout: {} for {} topics", strategy, n);
            Layout layout = (strategy == LayoutStrategy.HIERARCHICAL)
                    ? computeHierarchicalLayout(n)
                    : computeGridLayout(n);

            int contentX = LEGEND_W + LEGEND_PAD * 2 + CANVAS_PAD;

            // ============ LEGEND ============
            buildLegend(shapesLayer, textLayer, CANVAS_PAD, CANVAS_PAD, n, topics, assignedThemes);

            // ============ TITLE BANNER ============
            int x = contentX;
            int y = CANVAS_PAD;
            int bannerW = layout.contentWidth;
            int bannerH = 110;
            String bannerId = newId();
            shapesLayer.add(rectangle(bannerId, x, y, bannerW, bannerH,
                    "#d0ebff", "#1864ab", "solid", 3, 8, null));

            String bannerContent = "📺  " + safeTrim(meta.getTitle(), 100) + "\n\n"
                    + "by " + (meta.getUploader() == null ? "unknown" : meta.getUploader())
                    + "   •   " + formatDuration(meta.getDuration())
                    + "   •   " + n + " topics";
            String bannerTextId = newId();
            ObjectNode bannerText = boundText(bannerTextId, bannerId, bannerContent,
                    x, y, bannerW, bannerH, 16, "#0b3d7a", "left", null);
            textLayer.add(bannerText);
            registerBoundText(shapesLayer, bannerId, bannerTextId);
            y += bannerH + 30;

            // ============ EXECUTIVE SUMMARY ============
            String overview = overallSummary == null ? "" : overallSummary.trim();
            if (!overview.isEmpty()) {
                int oh = Math.max(160, Math.min(260,
                        computeTextHeight(overview, bannerW - 60, 13) + 90));
                String ovId = newId();
                shapesLayer.add(rectangle(ovId, x, y, bannerW, oh,
                        "#fff9db", "#f59f00", "solid", 2, 6, null));

                String ovContent = "📋  EXECUTIVE SUMMARY\n\n" + safeTrim(overview, 700);
                String ovTextId = newId();
                textLayer.add(boundText(ovTextId, ovId, ovContent,
                        x, y, bannerW, oh, 13, "#5c3a09", "left", null));
                registerBoundText(shapesLayer, ovId, ovTextId);
                y += oh + 40;
            }

            // ============ HUB ============
            int hubX = x + (bannerW - HUB_W) / 2;
            int hubY = y;
            String hubId = newId();
            shapesLayer.add(ellipse(hubId, hubX, hubY, HUB_W, HUB_H,
                    "#7048e8", "#e5dbff", "solid", 3, null));

            String hubContent = "🎯  KEY TOPICS\n(" + n + " sections)";
            String hubTextId = newId();
            textLayer.add(boundText(hubTextId, hubId, hubContent,
                    hubX, hubY, HUB_W, HUB_H, 16, "#3b1d80", "center", null));
            registerBoundText(shapesLayer, hubId, hubTextId);

            int hubBottomX = hubX + HUB_W / 2;
            int hubBottomY = hubY + HUB_H;
            int topicsTopY = hubY + HUB_H + V_GAP;

            // ============ TOPIC CARDS + STICKIES + FRAMES + GROUPS ============
            List<String> topicIds = new ArrayList<>();
            List<int[]> topicCoords = new ArrayList<>();

            // Determine layout grid info for hub-arrow routing
            int gridCols = (n <= 3) ? n : (n <= 6 ? 3 : 4);

            for (int i = 0; i < n; i++) {
                TopicSection t = topics.get(i);
                String[] colors = assignedThemes.get(i).colors();
                String fill = colors[0], stroke = colors[1], darkText = colors[2], tint = colors[3];

                int[] pos = layout.positions.get(i);
                int bx = x + pos[0];
                int by = topicsTopY + pos[1];

                String groupId = "grp-" + i + "-" + newId();
                List<String> grp = List.of(groupId);

                // Frame
                int bulletCount = (t.getBulletPoints() == null) ? 0
                        : Math.min(4, t.getBulletPoints().size());
                int bulletColH = bulletCount * (BULLET_H + BULLET_GAP);
                int frameW = TOPIC_W + BULLET_W + STICKY_OFFSET + 2 * FRAME_PAD;
                int frameH = Math.max(TOPIC_H, bulletColH) + 2 * FRAME_PAD + 30;
                int frameX = bx - FRAME_PAD;
                int frameY = by - FRAME_PAD - 25;

                String frameId = newId();
                ObjectNode frame = frameElement(frameId, frameX, frameY, frameW, frameH,
                        "Topic " + (i + 1) + " · " + assignedThemes.get(i).name());
                frame.set("groupIds", listToJsonArray(grp));
                shapesLayer.add(frame);

                // Topic card
                String boxId = newId();
                topicIds.add(boxId);
                topicCoords.add(new int[]{bx, by});
                String pdfLink = (pdfPath != null)
                        ? buildPdfLink(pdfPath, i + 1, t.getStartTime())
                        : null;
                ObjectNode card = rectangle(boxId, bx, by, TOPIC_W, TOPIC_H,
                        fill, stroke, "solid", 2, 12, frameId);
                card.set("groupIds", listToJsonArray(grp));
                if (pdfLink != null) card.put("link", pdfLink);
                shapesLayer.add(card);

                // ---- COMBINED BOUND TEXT (auto-wraps) ----
                StringBuilder cardContent = new StringBuilder();
                String titleStr = safeTrim(t.getTitle() == null ? "Topic " + (i + 1) : t.getTitle(), 80);
                cardContent.append(titleStr).append("\n");
                cardContent.append("🏷 ").append(assignedThemes.get(i).name())
                        .append("   ⏱ ").append(formatTime(t.getStartTime()))
                        .append(" — ").append(formatTime(t.getEndTime()))
                        .append("\n\n");
                String summary = safeTrim(t.getSummary() == null ? "" : t.getSummary(), 360);
                cardContent.append(summary);

                String cardTextId = newId();
                ObjectNode cardText = boundText(
                        cardTextId, boxId, cardContent.toString(),
                        bx, by, TOPIC_W, TOPIC_H,
                        13, darkText, "left", frameId
                );
                cardText.set("groupIds", listToJsonArray(grp));
                textLayer.add(cardText);
                registerBoundText(shapesLayer, boxId, cardTextId);

                // Numbered badge
                String badgeId = newId();
                ObjectNode badge = ellipse(badgeId, bx - 18, by - 18, 40, 40,
                        stroke, stroke, "solid", 2, frameId);
                badge.set("groupIds", listToJsonArray(grp));
                shapesLayer.add(badge);
                ObjectNode badgeText = text(String.valueOf(i + 1),
                        bx - 18, by - 12, 18, 40, 28, "#ffffff", "center", 700, frameId, null);
                badgeText.set("groupIds", listToJsonArray(grp));
                textLayer.add(badgeText);

                // ---- BULLET STICKIES (auto-wrapping) ----
                int stickyX = bx + TOPIC_W + STICKY_OFFSET;
                if (t.getBulletPoints() != null) {
                    int max = Math.min(4, t.getBulletPoints().size());
                    for (int b = 0; b < max; b++) {
                        String bp = safeTrim(t.getBulletPoints().get(b), 120);
                        int sy = by + b * (BULLET_H + BULLET_GAP);

                        String stickyId = newId();
                        ObjectNode sticky = rectangle(stickyId, stickyX, sy, BULLET_W, BULLET_H,
                                tint, stroke, "solid", 1, 6, frameId);
                        sticky.set("groupIds", listToJsonArray(grp));
                        shapesLayer.add(sticky);

                        String stickyTextId = newId();
                        ObjectNode stickyText = boundText(stickyTextId, stickyId, "▸ " + bp,
                                stickyX, sy, BULLET_W, BULLET_H,
                                11, darkText, "left", frameId);
                        stickyText.set("groupIds", listToJsonArray(grp));
                        textLayer.add(stickyText);
                        registerBoundText(shapesLayer, stickyId, stickyTextId);

                        // Card → sticky arrow (short, horizontal — clean)
                        String arrId = newId();
                        ObjectNode arr = elbowArrow(arrId,
                                bx + TOPIC_W, by + 40 + b * 32,
                                stickyX, sy + BULLET_H / 2,
                                stroke, boxId, stickyId, frameId, 70);
                        arr.set("groupIds", listToJsonArray(grp));
                        arrowsLayer.add(arr);
                        registerBinding(shapesLayer, boxId, arrId);
                        registerBinding(shapesLayer, stickyId, arrId);
                    }
                }
            }

            // ============ HUB → TOPIC ARROWS (only to first row) ============
            // Drawing arrows from hub to ALL cards causes them to cut through other cards.
            // Solution: only draw hub arrows to FIRST ROW topics; let frames+numbering
            // visually communicate the rest of the hierarchy.
            int firstRowCount = Math.min(gridCols, n);
            for (int i = 0; i < firstRowCount; i++) {
                String stroke = assignedThemes.get(i).colors()[1];
                int[] pos = layout.positions.get(i);
                int bx = x + pos[0];
                int by = topicsTopY + pos[1];
                int targetX = bx + TOPIC_W / 2;
                int targetY = by;

                String arrId = newId();
                ObjectNode arr = elbowArrow(arrId,
                        hubBottomX, hubBottomY, targetX, targetY,
                        stroke, hubId, topicIds.get(i), null, 60);
                // Hub arrows are slightly translucent so they don't dominate
                arr.put("opacity", 70);
                arrowsLayer.add(arr);
                registerBinding(shapesLayer, hubId, arrId);
                registerBinding(shapesLayer, topicIds.get(i), arrId);
            }

            // ============ SEMANTIC CROSS-REFERENCE ARROWS (side channel routing) ============
            // Route cross-refs along the LEFT side of the layout to avoid cutting through cards.
            // Use FROM-card LEFT edge → TO-card LEFT edge through a left margin channel.
            int leftChannelX = x - 60;   // dedicated channel left of all cards
            for (int[] link : semanticLinks) {
                int from = link[0], to = link[1];
                int[] fromPos = layout.positions.get(from);
                int[] toPos = layout.positions.get(to);
                int fromX = x + fromPos[0];                       // left edge of FROM card
                int fromY = topicsTopY + fromPos[1] + TOPIC_H / 2;
                int toX = x + toPos[0];                           // left edge of TO card
                int toY = topicsTopY + toPos[1] + TOPIC_H / 2;

                String arrId = newId();
                ObjectNode arr = dashedReferenceArrow(arrId, fromX, fromY, toX, toY,
                        "#adb5bd", topicIds.get(from), topicIds.get(to));
                arrowsLayer.add(arr);
                registerBinding(shapesLayer, topicIds.get(from), arrId);
                registerBinding(shapesLayer, topicIds.get(to), arrId);
            }

            // ============ FOOTER ============
            int footerY = topicsTopY + layout.contentHeight + 100;
            textLayer.add(text(
                    "Generated by SmartNotes.ai   •   " + semanticLinks.size() + " cross-references   •   "
                            + new SimpleDateFormat("yyyy-MM-dd").format(new Date())
                            + "   •   Click any topic title to open PDF",
                    x, footerY, 11, bannerW, 24,
                    "#495057", "center", 400, null, null));

            // ============ MERGE LAYERS IN Z-ORDER ============
            // Final order: arrows → shapes → text  (so text is always on top)
            ArrayNode finalElements = mapper.createArrayNode();
            finalElements.addAll(arrowsLayer);
            finalElements.addAll(shapesLayer);
            finalElements.addAll(textLayer);

            // ============ ROOT ============
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "excalidraw");
            root.put("version", 2);
            root.put("source", "https://smartnotes.ai");
            root.set("elements", finalElements);

            ObjectNode appState = mapper.createObjectNode();
            appState.put("viewBackgroundColor", "#f1f3f5");
            appState.put("gridSize", 20);
            appState.put("currentItemFontFamily", 2);
            root.set("appState", appState);
            root.set("files", mapper.createObjectNode());

            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            log.info("Excalidraw mind-map generated at: {} ({} elements, {} cross-refs, layout={})",
                    file, finalElements.size(), semanticLinks.size(), strategy);
            return file;
        } catch (Exception e) {
            throw new RuntimeException("Excalidraw export failed", e);
        }
    }

    // ====================================================================
    //               SEMANTIC ANALYSIS (themes + cross-refs)
    // ====================================================================

    private List<Theme> assignThemes(List<TopicSection> topics) {
        List<Theme> result = new ArrayList<>();
        for (TopicSection t : topics) {
            String haystack = ((t.getTitle() == null ? "" : t.getTitle()) + " "
                    + (t.getSummary() == null ? "" : t.getSummary()) + " "
                    + (t.getBulletPoints() == null ? "" : String.join(" ", t.getBulletPoints())))
                    .toLowerCase(Locale.ROOT);

            Theme best = THEMES.get(THEMES.size() - 1);
            int bestScore = 0;
            for (Theme th : THEMES) {
                if (th.keywords().isEmpty()) continue;
                int score = 0;
                for (String kw : th.keywords()) {
                    if (haystack.contains(kw)) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = th;
                }
            }
            result.add(best);
        }
        return result;
    }

    private List<double[]> computeEmbeddings(List<TopicSection> topics) {
        List<String> texts = new ArrayList<>();
        for (TopicSection t : topics) {
            String txt = (t.getTitle() == null ? "" : t.getTitle()) + ". "
                    + (t.getSummary() == null ? "" : t.getSummary());
            texts.add(txt);
        }
        try {
            return embeddingService.embedAll(texts);
        } catch (Exception e) {
            log.warn("Embeddings unavailable: {}", e.getMessage());
            List<double[]> empty = new ArrayList<>();
            for (int i = 0; i < topics.size(); i++) empty.add(new double[0]);
            return empty;
        }
    }

    private List<int[]> findSemanticLinks(List<double[]> embeddings) {
        List<int[]> links = new ArrayList<>();
        int n = embeddings.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1) continue;
                double sim = EmbeddingService.cosineSimilarity(embeddings.get(i), embeddings.get(j));
                if (sim >= SIMILARITY_THRESHOLD) {
                    links.add(new int[]{i, j});
                }
            }
        }
        // Cap at 3 cross-refs to avoid visual clutter
        if (links.size() > 3) {
            links = links.subList(0, 3);
        }
        return links;
    }

    // ====================================================================
    //                          LAYOUT ENGINES
    // ====================================================================

    private enum LayoutStrategy { GRID, HIERARCHICAL }

    private static class Layout {
        List<int[]> positions = new ArrayList<>();
        int contentWidth;
        int contentHeight;
    }

    private Layout computeGridLayout(int n) {
        Layout l = new Layout();
        if (n == 0) {
            l.contentWidth = 1200;
            l.contentHeight = 200;
            return l;
        }
        int cols = n <= 3 ? n : (n <= 6 ? 3 : 4);
        int rows = (int) Math.ceil((double) n / cols);
        int cellW = TOPIC_W + BULLET_W + STICKY_OFFSET + 2 * FRAME_PAD;
        int cellH = TOPIC_H + 2 * FRAME_PAD + 60;

        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            int dx = col * (cellW + H_GAP) + FRAME_PAD;
            int dy = row * (cellH + V_GAP) + FRAME_PAD + 30;
            l.positions.add(new int[]{dx, dy});
        }
        l.contentWidth = cols * cellW + (cols - 1) * H_GAP;
        l.contentHeight = rows * cellH + (rows - 1) * V_GAP;
        return l;
    }

    private Layout computeHierarchicalLayout(int n) {
        Layout l = new Layout();
        int layers = Math.min(6, Math.max(2, (int) Math.ceil(Math.sqrt(n / 2.0))));
        int perLayer = (int) Math.ceil((double) n / layers);

        int cellW = TOPIC_W + BULLET_W + STICKY_OFFSET + 2 * FRAME_PAD;
        int cellH = TOPIC_H + 2 * FRAME_PAD + 60;
        int laneSpacing = H_GAP;
        int layerSpacing = V_GAP + 60;

        int maxLayerWidth = perLayer * cellW + (perLayer - 1) * laneSpacing;

        for (int i = 0; i < n; i++) {
            int layer = i / perLayer;
            int posInLayer = i % perLayer;
            int itemsInThisLayer = Math.min(perLayer, n - layer * perLayer);
            int layerWidth = itemsInThisLayer * cellW + (itemsInThisLayer - 1) * laneSpacing;
            int layerOffsetX = (maxLayerWidth - layerWidth) / 2;

            int dx = layerOffsetX + posInLayer * (cellW + laneSpacing) + FRAME_PAD;
            int dy = layer * (cellH + layerSpacing) + FRAME_PAD + 30;
            l.positions.add(new int[]{dx, dy});
        }
        l.contentWidth = maxLayerWidth;
        l.contentHeight = layers * cellH + (layers - 1) * layerSpacing;
        return l;
    }

    // ====================================================================
    //                          LEGEND BUILDER
    // ====================================================================

    private void buildLegend(ArrayNode shapesLayer, ArrayNode textLayer,
                             int x, int y, int n,
                             List<TopicSection> topics, List<Theme> themes) {
        int rowH = 38;
        int headerH = 80;

        Set<String> usedThemes = new LinkedHashSet<>();
        for (Theme t : themes) usedThemes.add(t.name());
        int themeHeaderH = 40 + usedThemes.size() * 22 + 14;

        int legendH = headerH + Math.max(1, n) * rowH + themeHeaderH + 40;

        String legendBgId = newId();
        shapesLayer.add(rectangle(legendBgId, x, y, LEGEND_W, legendH,
                "#ffffff", "#adb5bd", "solid", 1, 6, null));

        textLayer.add(text("🎨 LEGEND", x + 16, y + 16, 16, LEGEND_W - 32, 24,
                "#212529", "left", 700, null, null));
        textLayer.add(text("Topics & semantic themes", x + 16, y + 40, 11, LEGEND_W - 32, 16,
                "#868e96", "left", 400, null, null));

        shapesLayer.add(line(x + 12, y + 64, x + LEGEND_W - 12, y + 64, "#dee2e6", null));

        int curY = y + headerH;
        for (int i = 0; i < n && i < topics.size(); i++) {
            String[] colors = themes.get(i).colors();
            String fill = colors[0], stroke = colors[1], darkText = colors[2];

            String swatchId = newId();
            shapesLayer.add(rectangle(swatchId, x + 16, curY + 8, 22, 22,
                    fill, stroke, "solid", 2, 4, null));
            textLayer.add(text(String.valueOf(i + 1), x + 16, curY + 11, 11, 22, 18,
                    darkText, "center", 700, null, null));

            String label = safeTrim(topics.get(i).getTitle() == null
                    ? "Topic " + (i + 1) : topics.get(i).getTitle(), 22);
            textLayer.add(text(label, x + 48, curY + 10, 11, LEGEND_W - 60, 20,
                    "#343a40", "left", 500, null, null));

            curY += rowH;
        }

        curY += 12;
        shapesLayer.add(line(x + 12, curY, x + LEGEND_W - 12, curY, "#dee2e6", null));
        curY += 10;
        textLayer.add(text("THEMES IN USE", x + 16, curY, 10, LEGEND_W - 32, 16,
                "#495057", "left", 700, null, null));
        curY += 22;
        for (String themeName : usedThemes) {
            Theme th = THEMES.stream().filter(t -> t.name().equals(themeName)).findFirst()
                    .orElse(THEMES.get(THEMES.size() - 1));
            String dotId = newId();
            shapesLayer.add(ellipse(dotId, x + 18, curY + 4, 10, 10,
                    th.colors()[1], th.colors()[1], "solid", 1, null));
            textLayer.add(text(th.name(), x + 36, curY, 10, LEGEND_W - 52, 16,
                    "#495057", "left", 400, null, null));
            curY += 22;
        }
    }

    // ====================================================================
    //                       BINDING REGISTRATION
    // ====================================================================

    private void registerBinding(ArrayNode elements, String shapeId, String arrowId) {
        for (JsonNode el : elements) {
            if (shapeId.equals(el.path("id").asText())) {
                ObjectNode shape = (ObjectNode) el;
                ArrayNode bound = shape.get("boundElements") != null && shape.get("boundElements").isArray()
                        ? (ArrayNode) shape.get("boundElements")
                        : mapper.createArrayNode();
                ObjectNode entry = mapper.createObjectNode();
                entry.put("id", arrowId);
                entry.put("type", "arrow");
                bound.add(entry);
                shape.set("boundElements", bound);
                return;
            }
        }
    }

    private void registerBoundText(ArrayNode elements, String containerId, String textId) {
        for (JsonNode el : elements) {
            if (containerId.equals(el.path("id").asText())) {
                ObjectNode shape = (ObjectNode) el;
                ArrayNode bound = shape.get("boundElements") != null && shape.get("boundElements").isArray()
                        ? (ArrayNode) shape.get("boundElements")
                        : mapper.createArrayNode();
                ObjectNode entry = mapper.createObjectNode();
                entry.put("id", textId);
                entry.put("type", "text");
                bound.add(entry);
                shape.set("boundElements", bound);
                return;
            }
        }
    }

    // ====================================================================
    //                       UTILITY HELPERS
    // ====================================================================

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String safeTrim(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String formatTime(double sec) {
        int s = (int) sec;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private String formatDuration(double sec) {
        int s = (int) sec;
        if (s < 3600) return String.format("%d:%02d", s / 60, s % 60);
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private int computeTextHeight(String text, int width, int fontSize) {
        int charsPerLine = Math.max(20, width / Math.max(1, fontSize / 2));
        int lines = (int) Math.ceil((double) text.length() / charsPerLine);
        return lines * (int) (fontSize * 1.4);
    }

    private String buildPdfLink(Path pdfPath, int topicNumber, double startTime) {
        try {
            String uri = pdfPath.toUri().toString();
            return uri + "#page=" + Math.max(1, topicNumber);
        } catch (Exception e) {
            return null;
        }
    }

    private ArrayNode listToJsonArray(List<String> items) {
        ArrayNode arr = mapper.createArrayNode();
        for (String s : items) arr.add(s);
        return arr;
    }

    // ====================================================================
    //                       ELEMENT FACTORIES
    // ====================================================================

    private ObjectNode rectangle(String id, int x, int y, int w, int h,
                                 String bg, String stroke, String fillStyle,
                                 int strokeWidth, int roundness, String frameId) {
        ObjectNode n = baseShape(id, "rectangle", x, y, w, h, stroke, bg, fillStyle, strokeWidth, frameId);
        if (roundness > 0) {
            ObjectNode r = mapper.createObjectNode();
            r.put("type", 3);
            n.set("roundness", r);
        } else {
            n.putNull("roundness");
        }
        return n;
    }

    private ObjectNode ellipse(String id, int x, int y, int w, int h,
                               String stroke, String bg, String fillStyle, int strokeWidth, String frameId) {
        ObjectNode n = baseShape(id, "ellipse", x, y, w, h, stroke, bg, fillStyle, strokeWidth, frameId);
        ObjectNode r = mapper.createObjectNode();
        r.put("type", 2);
        n.set("roundness", r);
        return n;
    }

    private ObjectNode baseShape(String id, String type, int x, int y, int w, int h,
                                 String stroke, String bg, String fillStyle, int strokeWidth,
                                 String frameId) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("type", type);
        n.put("x", x);
        n.put("y", y);
        n.put("width", w);
        n.put("height", h);
        n.put("angle", 0);
        n.put("strokeColor", stroke);
        n.put("backgroundColor", bg);
        n.put("fillStyle", fillStyle);
        n.put("strokeWidth", strokeWidth);
        n.put("strokeStyle", "solid");
        n.put("roughness", 1);
        n.put("opacity", 100);
        n.put("seed", rnd.nextInt(2_000_000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(2_000_000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        if (frameId != null) n.put("frameId", frameId); else n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        n.set("link", null);
        n.put("locked", false);
        return n;
    }

    private ObjectNode frameElement(String id, int x, int y, int w, int h, String name) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("type", "frame");
        n.put("x", x);
        n.put("y", y);
        n.put("width", w);
        n.put("height", h);
        n.put("angle", 0);
        n.put("strokeColor", "#bababa");
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", 2);
        n.put("strokeStyle", "solid");
        n.put("roughness", 0);
        n.put("opacity", 100);
        n.put("seed", rnd.nextInt(2_000_000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(2_000_000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        n.set("link", null);
        n.put("locked", false);
        n.put("name", name);
        n.putNull("roundness");
        return n;
    }

    private ObjectNode text(String t, int x, int y, int fontSize, int width, int height,
                            String color, String align, int weight, String frameId, String link) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", newId());
        n.put("type", "text");
        n.put("x", x);
        n.put("y", y);
        n.put("width", width);
        n.put("height", height);
        n.put("angle", 0);
        n.put("strokeColor", color);
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", 1);
        n.put("strokeStyle", "solid");
        n.put("roughness", 0);
        n.put("opacity", 100);
        n.put("seed", rnd.nextInt(2_000_000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(2_000_000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        if (frameId != null) n.put("frameId", frameId); else n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        if (link != null) n.put("link", link); else n.set("link", null);
        n.put("locked", false);
        n.put("text", t == null ? "" : t);
        n.put("fontSize", fontSize);
        n.put("fontFamily", 2);
        n.put("textAlign", align);
        n.put("verticalAlign", "top");
        n.put("baseline", (int) (fontSize * 0.85));
        n.set("containerId", null);
        n.put("originalText", t == null ? "" : t);
        n.put("lineHeight", 1.25);
        n.putNull("roundness");
        return n;
    }

    private ObjectNode boundText(String textId, String containerId, String t,
                                 int containerX, int containerY,
                                 int containerW, int containerH,
                                 int fontSize, String color, String align, String frameId) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", textId);
        n.put("type", "text");
        n.put("x", containerX);
        n.put("y", containerY);
        n.put("width", containerW);
        n.put("height", containerH);
        n.put("angle", 0);
        n.put("strokeColor", color);
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", 1);
        n.put("strokeStyle", "solid");
        n.put("roughness", 0);
        n.put("opacity", 100);
        n.put("seed", rnd.nextInt(2_000_000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(2_000_000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        if (frameId != null) n.put("frameId", frameId); else n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        n.set("link", null);
        n.put("locked", false);
        n.put("text", t == null ? "" : t);
        n.put("fontSize", fontSize);
        n.put("fontFamily", 2);
        n.put("textAlign", align);
        n.put("verticalAlign", "middle");
        n.put("baseline", (int) (fontSize * 0.85));
        n.put("containerId", containerId);
        n.put("originalText", t == null ? "" : t);
        n.put("lineHeight", 1.4);
        n.putNull("roundness");
        return n;
    }

    private ObjectNode line(int x1, int y1, int x2, int y2, String color, String frameId) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", newId());
        n.put("type", "line");
        n.put("x", x1);
        n.put("y", y1);
        n.put("width", x2 - x1);
        n.put("height", y2 - y1);
        n.put("angle", 0);
        n.put("strokeColor", color);
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", 1);
        n.put("strokeStyle", "solid");
        n.put("roughness", 0);
        n.put("opacity", 60);
        n.put("seed", rnd.nextInt(2_000_000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(2_000_000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        if (frameId != null) n.put("frameId", frameId); else n.set("frameId", null);
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
        n.putNull("startArrowhead");
        n.putNull("endArrowhead");
        n.putNull("roundness");
        return n;
    }

    /** Standard elbow arrow with configurable end-point gap (controls how close to bound shape). */
    private ObjectNode elbowArrow(String id, int x1, int y1, int x2, int y2,
                                  String color, String startId, String endId,
                                  String frameId, int gap) {
        return arrowBase(id, x1, y1, x2, y2, color, startId, endId, frameId,
                "solid", 100, 2, true, "arrow", gap);
    }

    private ObjectNode dashedReferenceArrow(String id, int x1, int y1, int x2, int y2,
                                            String color, String startId, String endId) {
        ObjectNode n = arrowBase(id, x1, y1, x2, y2, color, startId, endId, null,
                "dashed", 45, 1, false, null, 12);
        n.put("startArrowhead", "dot");
        n.put("endArrowhead", "dot");
        return n;
    }

    private ObjectNode arrowBase(String id, int x1, int y1, int x2, int y2,
                                 String color, String startId, String endId, String frameId,
                                 String strokeStyle, int opacity, int strokeWidth,
                                 boolean elbowed, String endHead, int gap) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("type", "arrow");
        n.put("x", x1);
        n.put("y", y1);
        n.put("width", Math.abs(x2 - x1));
        n.put("height", Math.abs(y2 - y1));
        n.put("angle", 0);
        n.put("strokeColor", color);
        n.put("backgroundColor", "transparent");
        n.put("fillStyle", "solid");
        n.put("strokeWidth", strokeWidth);
        n.put("strokeStyle", strokeStyle);
        n.put("roughness", 0);
        n.put("opacity", opacity);
        n.put("seed", rnd.nextInt(2_000_000));
        n.put("version", 1);
        n.put("versionNonce", rnd.nextInt(2_000_000));
        n.put("isDeleted", false);
        n.set("groupIds", mapper.createArrayNode());
        if (frameId != null) n.put("frameId", frameId); else n.set("frameId", null);
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

        if (startId != null) {
            ObjectNode sb = mapper.createObjectNode();
            sb.put("elementId", startId);
            sb.put("focus", 0.0);
            sb.put("gap", gap);
            n.set("startBinding", sb);
        } else {
            n.set("startBinding", null);
        }
        if (endId != null) {
            ObjectNode eb = mapper.createObjectNode();
            eb.put("elementId", endId);
            eb.put("focus", 0.0);
            eb.put("gap", gap);
            n.set("endBinding", eb);
        } else {
            n.set("endBinding", null);
        }

        n.putNull("startArrowhead");
        if (endHead != null) n.put("endArrowhead", endHead); else n.putNull("endArrowhead");
        n.put("elbowed", elbowed);
        n.putNull("roundness");
        return n;
    }
}