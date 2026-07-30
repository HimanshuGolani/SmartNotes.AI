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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generates a rich, semantically-themed Excalidraw mind-map document.
 * <p>
 * Despite the legacy class name, this is NOT an MCP server client — it directly
 * builds and serializes Excalidraw v2 JSON. The output is loadable at
 * <a href="https://excalidraw.com">excalidraw.com</a>.
 * <p>
 * Layout strategy:
 * <ul>
 *   <li>Left side: legend with color swatches + theme glossary</li>
 *   <li>Top: title banner + executive summary</li>
 *   <li>Center: hub node connected to first-row topics</li>
 *   <li>Below: grid or hierarchical layout of topic cards with sticky bullets</li>
 * </ul>
 * Z-order is enforced: arrows → shapes → text (text never occluded by arrows).
 */
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
    private record Theme(String name, Set<String> keywords, String[] colors) {}

    /** Pre-compiled keyword patterns for word-boundary matching (fixes Q4). */
    private static final List<Theme> THEMES;
    private static final Map<String, Pattern> KEYWORD_PATTERNS = new HashMap<>();

    static {
        List<Theme> tmp = new ArrayList<>();
        tmp.add(new Theme("technical", Set.of("code", "function", "class", "api", "method", "library",
                "framework", "implementation", "compile", "build", "deploy", "server",
                "database", "query", "algorithm", "syntax", "variable"),
                new String[]{"#a5d8ff", "#1971c2", "#0b3d7a", "#e7f5ff"}));
        tmp.add(new Theme("conceptual", Set.of("concept", "theory", "idea", "principle", "definition",
                "abstract", "model", "paradigm", "philosophy"),
                new String[]{"#d0bfff", "#7048e8", "#3b1d80", "#f3f0ff"}));
        tmp.add(new Theme("process", Set.of("step", "process", "workflow", "procedure",
                "approach", "strategy", "phase", "stage", "pipeline", "iteration"),
                new String[]{"#b2f2bb", "#2f9e44", "#1b5e20", "#ebfbee"}));
        tmp.add(new Theme("warning", Set.of("warning", "caution", "issue", "problem", "error",
                "bug", "fail", "broken", "danger", "risk", "pitfall", "mistake"),
                new String[]{"#ffc9c9", "#e03131", "#7a0e0e", "#fff5f5"}));
        tmp.add(new Theme("tip", Set.of("tip", "trick", "advice", "recommend", "hint",
                "useful", "helpful", "remember"),
                new String[]{"#ffec99", "#f08c00", "#7a4500", "#fff9db"}));
        tmp.add(new Theme("example", Set.of("example", "demo", "demonstration", "instance",
                "illustration", "sample", "scenario"),
                new String[]{"#ffd8a8", "#e8590c", "#7a2e0a", "#fff4e6"}));
        tmp.add(new Theme("comparison", Set.of("versus", "compare", "difference", "similar",
                "contrast", "alternative", "tradeoff"),
                new String[]{"#c5f6fa", "#0c8599", "#08484f", "#e3fafc"}));
        tmp.add(new Theme("general", Set.of(),
                new String[]{"#fcc2d7", "#c2255c", "#6b1233", "#fff0f6"}));
        THEMES = Collections.unmodifiableList(tmp);

        for (Theme t : THEMES) {
            for (String kw : t.keywords()) {
                // Word-boundary match (fixes Q4: "api" no longer matches "capital")
                KEYWORD_PATTERNS.put(kw,
                        Pattern.compile("\\b" + Pattern.quote(kw) + "\\b", Pattern.CASE_INSENSITIVE));
            }
        }
    }

    // ============== LAYOUT CONSTANTS ==============
    private static final int CANVAS_PAD = 80;
    private static final int TOPIC_W = 440;
    private static final int TOPIC_H = 260;
    private static final int H_GAP = 220;
    private static final int V_GAP = 280;
    private static final int HUB_W = 280;
    private static final int HUB_H = 110;
    private static final int LEGEND_W = 240;
    private static final int LEGEND_PAD = 24;
    private static final int FRAME_PAD = 40;
    private static final int BULLET_W = 200;
    private static final int BULLET_H = 78;
    private static final int BULLET_GAP = 20;
    private static final int STICKY_OFFSET = 90;
    private static final int BANNER_H = 110;
    private static final int BANNER_GAP = 30;
    private static final int OVERVIEW_GAP = 40;
    private static final int FOOTER_GAP = 100;
    private static final int BADGE_SIZE = 40;
    private static final int MAX_BULLETS_PER_TOPIC = 6;
    private static final int MAX_CROSS_REFS = 3;
    private static final double SIMILARITY_THRESHOLD = 0.72;
    private static final int CAPTION_H = 60;
    private static final int CAPTION_GAP = 14;
    private static final int TOPIC_H_MIN = 200;

    // ============== PUBLIC ENTRY POINTS ==============

    public Path export(VideoMetadata meta, String overallSummary, List<TopicSection> topics) {
        return export(meta, overallSummary, topics, null);
    }

    public Path export(VideoMetadata meta, String overallSummary, List<TopicSection> topics,
                       Path pdfPath) {
        if (meta == null || meta.getVideoId() == null) {
            throw new IllegalArgumentException("VideoMetadata with videoId is required");
        }

        Path outDir = Path.of(workspace, meta.getVideoId());
        Path file = outDir.resolve("notes.excalidraw");
        try {
            Files.createDirectories(outDir);

            List<TopicSection> safeTopics = (topics == null) ? List.of() : topics;
            ExportContext ctx = new ExportContext(meta, overallSummary, safeTopics, pdfPath);

            // Phase 1: Semantic analysis
            analyzeSemantics(ctx);

            // Phase 2: Layout — use max dynamic card height for consistent row spacing
            int maxTopicH = ctx.topicHeights.isEmpty() ? TOPIC_H : Collections.max(ctx.topicHeights);
            boolean anyCaption = ctx.topics.stream()
                    .anyMatch(t -> t.getScreenshotCaption() != null && !t.getScreenshotCaption().isBlank());
            int cellTopicH = maxTopicH + (anyCaption ? CAPTION_H + CAPTION_GAP : 0);
            ctx.layout = (ctx.topicCount > 8)
                    ? computeHierarchicalLayout(ctx.topicCount, cellTopicH)
                    : computeGridLayout(ctx.topicCount, cellTopicH);

            // Phase 3: Build elements (in z-order via 3 layers)
            int contentX = LEGEND_W + LEGEND_PAD * 2 + CANVAS_PAD;
            buildLegend(ctx, CANVAS_PAD, CANVAS_PAD);
            int afterBanner = buildBanner(ctx, contentX, CANVAS_PAD);
            int afterOverview = buildOverview(ctx, contentX, afterBanner);
            int afterHub = buildHub(ctx, contentX, afterOverview);
            buildTopics(ctx, contentX, afterHub);
            buildHubArrows(ctx, contentX, afterHub);
            buildSemanticArrows(ctx, contentX, afterHub);
            buildFooter(ctx, contentX, afterHub);

            if (ctx.topicCount == 0) {
                buildEmptyStateMessage(ctx, contentX, afterOverview);
            }

            // Phase 4: Merge layers (arrows → shapes → text)
            ArrayNode finalElements = mapper.createArrayNode();
            finalElements.addAll(ctx.arrowsLayer);
            finalElements.addAll(ctx.shapesLayer);
            finalElements.addAll(ctx.textLayer);

            // Phase 5: Serialize
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

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(file, json, StandardCharsets.UTF_8);

            log.info("Excalidraw mind-map written: path={}, elements={}, topics={}, cross-refs={}, layout={}",
                    file, finalElements.size(), ctx.topicCount, ctx.semanticLinks.size(),
                    ctx.layout.strategy);
            return file;
        } catch (Exception e) {
            log.error("Excalidraw export failed for video {}: {}", meta.getVideoId(), e.getMessage(), e);
            throw new RuntimeException("Excalidraw export failed", e);
        }
    }

    // ====================================================================
    //                  CONTEXT (carries all per-export state)
    // ====================================================================

    private class ExportContext {
        final VideoMetadata meta;
        final String overallSummary;
        final List<TopicSection> topics;
        final int topicCount;
        final Path pdfPath;

        String hubId;
        int hubX;
        int hubY;

        // Layers
        final ArrayNode arrowsLayer = mapper.createArrayNode();
        final ArrayNode shapesLayer = mapper.createArrayNode();
        final ArrayNode textLayer = mapper.createArrayNode();

        // Index for O(1) binding registration (fixes I6)
        final Map<String, ObjectNode> shapeIndex = new HashMap<>();

        // Semantic data
        List<Theme> themes;
        List<int[]> semanticLinks = List.of();

        // Per-topic computed card heights (populated in analyzeSemantics)
        final List<Integer> topicHeights = new ArrayList<>();

        // Layout
        Layout layout;
        final List<String> topicIds = new ArrayList<>();

        ExportContext(VideoMetadata meta, String summary, List<TopicSection> topics, Path pdfPath) {
            this.meta = meta;
            this.overallSummary = summary;
            this.topics = topics;
            this.topicCount = topics.size();
            this.pdfPath = pdfPath;
        }

        void addShape(ObjectNode shape) {
            shapesLayer.add(shape);
            shapeIndex.put(shape.path("id").asText(), shape);
        }

        void addArrow(ObjectNode arrow) {
            arrowsLayer.add(arrow);
        }

        void addText(ObjectNode txt) {
            textLayer.add(txt);
        }
    }

    // ====================================================================
    //                       PHASE METHODS (Q10 fix)
    // ====================================================================

    private void analyzeSemantics(ExportContext ctx) {
        ctx.themes = assignThemes(ctx.topics);
        if (ctx.topicCount > 1) {
            try {
                List<double[]> embeddings = computeEmbeddings(ctx.topics);
                ctx.semanticLinks = findSemanticLinks(embeddings);
                log.debug("Detected {} semantic cross-references for {} topics",
                        ctx.semanticLinks.size(), ctx.topicCount);
            } catch (Exception e) {
                log.warn("Semantic analysis skipped: {}", e.getMessage());
                ctx.semanticLinks = List.of();
            }
        }
        computeTopicHeights(ctx);
    }

    private void computeTopicHeights(ExportContext ctx) {
        ctx.topicHeights.clear();
        for (TopicSection t : ctx.topics) {
            String summary = t.getSummary() == null ? "" : t.getSummary();
            // title row + metadata row + summary text + top/bottom padding
            int textH = computeTextHeight(summary, TOPIC_W - 48, 13);
            int h = Math.max(TOPIC_H_MIN, textH + 80);
            ctx.topicHeights.add(h);
        }
    }

    private int buildBanner(ExportContext ctx, int x, int y) {
        int bannerW = ctx.layout.contentWidth;
        String bannerId = newId();
        ctx.addShape(rectangle(bannerId, x, y, bannerW, BANNER_H,
                "#d0ebff", "#1864ab", "solid", 3, 8, null));

        String title = safeTrim(ctx.meta.getTitle(), 100);
        if (title.isEmpty()) title = "(untitled)";
        String uploader = ctx.meta.getUploader() == null ? "unknown" : ctx.meta.getUploader();
        String content = "📺  " + title + "\n\n"
                + "by " + uploader
                + "   •   " + formatDuration(ctx.meta.getDuration())
                + "   •   " + ctx.topicCount + " topic" + (ctx.topicCount == 1 ? "" : "s");

        String textId = newId();
        ctx.addText(boundText(textId, bannerId, content,
                x, y, bannerW, BANNER_H, 16, "#0b3d7a", "left"));
        registerBoundText(ctx, bannerId, textId);
        return y + BANNER_H + BANNER_GAP;
    }

    private int buildOverview(ExportContext ctx, int x, int y) {
        String overview = ctx.overallSummary == null ? "" : ctx.overallSummary.trim();
        if (overview.isEmpty()) return y;

        int bannerW = ctx.layout.contentWidth;
        int oh = Math.max(160, Math.min(260,
                computeTextHeight(overview, bannerW - 60, 13) + 90));
        String ovId = newId();
        ctx.addShape(rectangle(ovId, x, y, bannerW, oh,
                "#fff9db", "#f59f00", "solid", 2, 6, null));

        String content = "📋  EXECUTIVE SUMMARY\n\n" + safeTrim(overview, 700);
        String textId = newId();
        ctx.addText(boundText(textId, ovId, content, x, y, bannerW, oh, 13, "#5c3a09", "left"));
        registerBoundText(ctx, ovId, textId);
        return y + oh + OVERVIEW_GAP;
    }

    private int buildHub(ExportContext ctx, int x, int y) {
        if (ctx.topicCount == 0) return y;

        int bannerW = ctx.layout.contentWidth;
        int hubX = x + (bannerW - HUB_W) / 2;
        String hubId = newId();
        ctx.hubId = hubId;
        ctx.hubX = hubX;
        ctx.hubY = y;
        ctx.addShape(ellipse(hubId, hubX, y, HUB_W, HUB_H,
                "#7048e8", "#e5dbff", "solid", 3, null));

        String content = "🎯  KEY TOPICS\n(" + ctx.topicCount + " section"
                + (ctx.topicCount == 1 ? "" : "s") + ")";
        String textId = newId();
        ctx.addText(boundText(textId, hubId, content, hubX, y, HUB_W, HUB_H,
                16, "#3b1d80", "center"));
        registerBoundText(ctx, hubId, textId);

        return y + HUB_H + V_GAP;
    }

    private void buildTopics(ExportContext ctx, int x, int topicsTopY) {
        for (int i = 0; i < ctx.topicCount; i++) {
            buildSingleTopic(ctx, i, x, topicsTopY);
        }
    }

    private void buildSingleTopic(ExportContext ctx, int i, int x, int topicsTopY) {
        TopicSection t = ctx.topics.get(i);
        Theme theme = ctx.themes.get(i);
        String[] colors = theme.colors();
        String fill = colors[0], stroke = colors[1], darkText = colors[2], tint = colors[3];

        int[] pos = ctx.layout.positions.get(i);
        int bx = x + pos[0];
        int by = topicsTopY + pos[1];

        String groupId = "grp-" + i + "-" + newId();
        ArrayNode grpArr = listToJsonArray(List.of(groupId));

        // Dynamic card height computed per-topic
        int topicH = ctx.topicHeights.isEmpty() ? TOPIC_H : ctx.topicHeights.get(i);
        boolean hasCaption = t.getScreenshotCaption() != null && !t.getScreenshotCaption().isBlank();
        int captionExtra = hasCaption ? CAPTION_H + CAPTION_GAP : 0;

        // Frame sizing
        int bulletCount = countBullets(t);
        int bulletColH = bulletCount * (BULLET_H + BULLET_GAP);
        int frameW = TOPIC_W + BULLET_W + STICKY_OFFSET + 2 * FRAME_PAD;
        int frameH = Math.max(topicH, bulletColH) + 2 * FRAME_PAD + 30 + captionExtra;
        int frameX = bx - FRAME_PAD;
        int frameY = by - FRAME_PAD - 25;

        String frameId = newId();
        ObjectNode frame = frameElement(frameId, frameX, frameY, frameW, frameH,
                "Topic " + (i + 1) + " · " + theme.name());
        frame.set("groupIds", grpArr.deepCopy());
        ctx.addShape(frame);

        // Topic card
        String boxId = newId();
        ctx.topicIds.add(boxId);
        String pdfLink = (ctx.pdfPath != null) ? buildPdfLink(ctx.pdfPath, i + 1) : null;
        ObjectNode card = rectangle(boxId, bx, by, TOPIC_W, topicH,
                fill, stroke, "solid", 2, 12, frameId);
        card.set("groupIds", grpArr.deepCopy());
        if (pdfLink != null) card.put("link", pdfLink);
        ctx.addShape(card);

        // Card text content
        String titleStr = safeTrim(t.getTitle() == null ? "Topic " + (i + 1) : t.getTitle(), 80);
        String summary = safeTrim(t.getSummary() == null ? "" : t.getSummary(), 360);
        String content = titleStr + "\n"
                + "🏷 " + theme.name()
                + "   ⏱ " + formatTime(t.getStartTime())
                + " — " + formatTime(t.getEndTime())
                + "\n\n" + summary;

        String cardTextId = newId();
        ObjectNode cardText = boundText(cardTextId, boxId, content,
                bx, by, TOPIC_W, topicH, 13, darkText, "left");
        cardText.set("groupIds", grpArr.deepCopy());
        ctx.addText(cardText);
        registerBoundText(ctx, boxId, cardTextId);

        // Numbered badge (I1 fix: vertically center the number)
        String badgeId = newId();
        ObjectNode badge = ellipse(badgeId, bx - BADGE_SIZE / 2, by - BADGE_SIZE / 2,
                BADGE_SIZE, BADGE_SIZE, stroke, stroke, "solid", 2, frameId);
        badge.set("groupIds", grpArr.deepCopy());
        ctx.addShape(badge);

        String badgeTextId = newId();
        ObjectNode badgeText = boundText(badgeTextId, badgeId, String.valueOf(i + 1),
                bx - BADGE_SIZE / 2, by - BADGE_SIZE / 2, BADGE_SIZE, BADGE_SIZE,
                16, "#ffffff", "center");
        badgeText.set("groupIds", grpArr.deepCopy());
        ctx.addText(badgeText);
        registerBoundText(ctx, badgeId, badgeTextId);

        // Bullet stickies
        int stickyX = bx + TOPIC_W + STICKY_OFFSET;
        if (t.getBulletPoints() != null) {
            int max = Math.min(MAX_BULLETS_PER_TOPIC, t.getBulletPoints().size());
            for (int b = 0; b < max; b++) {
                String bp = safeTrim(t.getBulletPoints().get(b), 120);
                int sy = by + b * (BULLET_H + BULLET_GAP);

                String stickyId = newId();
                ObjectNode sticky = rectangle(stickyId, stickyX, sy, BULLET_W, BULLET_H,
                        tint, stroke, "solid", 1, 6, frameId);
                sticky.set("groupIds", grpArr.deepCopy());
                ctx.addShape(sticky);

                String stickyTextId = newId();
                ObjectNode stickyText = boundText(stickyTextId, stickyId, "▸ " + bp,
                        stickyX, sy, BULLET_W, BULLET_H, 11, darkText, "left");
                stickyText.set("groupIds", grpArr.deepCopy());
                ctx.addText(stickyText);
                registerBoundText(ctx, stickyId, stickyTextId);

                // Card → sticky arrow (gap=8 for visual connection — fixes B3)
                String arrId = newId();
                ObjectNode arr = elbowArrow(arrId,
                        bx + TOPIC_W, by + 40 + b * 32,
                        stickyX, sy + BULLET_H / 2,
                        stroke, boxId, stickyId, frameId, 8);
                arr.set("groupIds", grpArr.deepCopy());
                ctx.addArrow(arr);
                registerBinding(ctx, boxId, arrId);
                registerBinding(ctx, stickyId, arrId);
            }
        }

        // Screenshot caption sticky — rendered below the topic card
        if (hasCaption) {
            int captionY = by + topicH + CAPTION_GAP;
            String captionId = newId();
            ObjectNode captionBox = rectangle(captionId, bx, captionY, TOPIC_W, CAPTION_H,
                    "#f8f9fa", stroke, "dashed", 1, 4, frameId);
            captionBox.set("groupIds", grpArr.deepCopy());
            ctx.addShape(captionBox);

            String captionTextId = newId();
            ObjectNode captionText = boundText(captionTextId, captionId,
                    "📸 " + safeTrim(t.getScreenshotCaption(), 200),
                    bx, captionY, TOPIC_W, CAPTION_H, 10, "#495057", "left");
            captionText.set("groupIds", grpArr.deepCopy());
            ctx.addText(captionText);
            registerBoundText(ctx, captionId, captionTextId);
        }
    }

    private void buildHubArrows(ExportContext ctx, int x, int topicsTopY) {
        if (ctx.topicCount == 0 || ctx.hubId == null) return;

        int gridCols = computeGridCols(ctx.topicCount);
        int firstRowCount = Math.min(gridCols, ctx.topicCount);
        int hubBottomX = ctx.hubX + HUB_W / 2;
        int hubBottomY = ctx.hubY + HUB_H;

        for (int i = 0; i < firstRowCount; i++) {
            String stroke = ctx.themes.get(i).colors()[1];
            int[] pos = ctx.layout.positions.get(i);
            int bx = x + pos[0];
            int by = topicsTopY + pos[1];
            int targetX = bx + TOPIC_W / 2;

            String arrId = newId();
            // gap=10 instead of 60 so arrow visually connects (B3 fix)
            ObjectNode arr = elbowArrow(arrId, hubBottomX, hubBottomY, targetX, by,
                    stroke, ctx.hubId, ctx.topicIds.get(i), null, 10);
            arr.put("opacity", 70);
            ctx.addArrow(arr);
            registerBinding(ctx, ctx.hubId, arrId);
            registerBinding(ctx, ctx.topicIds.get(i), arrId);
        }
    }

    private void buildSemanticArrows(ExportContext ctx, int x, int topicsTopY) {
        for (int[] link : ctx.semanticLinks) {
            int from = link[0], to = link[1];
            int[] fromPos = ctx.layout.positions.get(from);
            int[] toPos = ctx.layout.positions.get(to);
            int fromX = x + fromPos[0];
            int fromCardH = ctx.topicHeights.isEmpty() ? TOPIC_H : ctx.topicHeights.get(from);
            int toCardH = ctx.topicHeights.isEmpty() ? TOPIC_H : ctx.topicHeights.get(to);
            int fromY = topicsTopY + fromPos[1] + fromCardH / 2;
            int toX = x + toPos[0];
            int toY = topicsTopY + toPos[1] + toCardH / 2;

            String arrId = newId();
            ObjectNode arr = dashedReferenceArrow(arrId, fromX, fromY, toX, toY,
                    "#adb5bd", ctx.topicIds.get(from), ctx.topicIds.get(to));
            ctx.addArrow(arr);
            registerBinding(ctx, ctx.topicIds.get(from), arrId);
            registerBinding(ctx, ctx.topicIds.get(to), arrId);
        }
    }

    private void buildFooter(ExportContext ctx, int x, int topicsTopY) {
        int footerY = topicsTopY + ctx.layout.contentHeight + FOOTER_GAP;
        String linkHint = (ctx.pdfPath != null) ? "   •   Click any topic card to open PDF" : "";
        String content = "Generated by SmartNotes.ai   •   " + ctx.semanticLinks.size()
                + " cross-reference" + (ctx.semanticLinks.size() == 1 ? "" : "s")
                + "   •   " + new SimpleDateFormat("yyyy-MM-dd").format(new Date())
                + linkHint;
        ctx.addText(text(content, x, footerY, 11, ctx.layout.contentWidth, 24,
                "#495057", "center", null, null));
    }

    private void buildEmptyStateMessage(ExportContext ctx, int x, int y) {
        String msg = "⚠️  No topics could be extracted from this video.\n"
                + "Try a longer or higher-quality video, or check the transcript.";
        ctx.addText(text(msg, x, y + 100, 18, ctx.layout.contentWidth, 80,
                "#e03131", "center", null, null));
    }

    // ====================================================================
    //                       SEMANTIC ANALYSIS
    // ====================================================================

    private List<Theme> assignThemes(List<TopicSection> topics) {
        List<Theme> result = new ArrayList<>(topics.size());
        for (TopicSection t : topics) {
            String haystack = ((t.getTitle() == null ? "" : t.getTitle()) + " "
                    + (t.getSummary() == null ? "" : t.getSummary()) + " "
                    + (t.getBulletPoints() == null ? "" : String.join(" ", t.getBulletPoints())))
                    .toLowerCase(Locale.ROOT);

            Theme best = THEMES.get(THEMES.size() - 1); // general fallback
            int bestScore = 0;
            for (Theme th : THEMES) {
                if (th.keywords().isEmpty()) continue;
                int score = 0;
                for (String kw : th.keywords()) {
                    Pattern p = KEYWORD_PATTERNS.get(kw);
                    if (p != null && p.matcher(haystack).find()) score++;
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
        List<String> texts = new ArrayList<>(topics.size());
        for (TopicSection t : topics) {
            String txt = (t.getTitle() == null ? "" : t.getTitle()) + ". "
                    + (t.getSummary() == null ? "" : t.getSummary());
            texts.add(txt.isBlank() ? " " : txt);
        }
        List<double[]> result = embeddingService.embedAll(texts);
        if (result == null || result.size() != topics.size()) {
            log.warn("Embedding service returned mismatched sizes; skipping semantic links");
            return Collections.emptyList();
        }
        return result;
    }

    private List<int[]> findSemanticLinks(List<double[]> embeddings) {
        List<int[]> links = new ArrayList<>();
        if (embeddings.isEmpty()) return links;

        // Track scores so we can pick the TOP-K most similar (fixes B4)
        record Pair(int i, int j, double sim) {}
        List<Pair> all = new ArrayList<>();
        int n = embeddings.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double[] a = embeddings.get(i);
                double[] b = embeddings.get(j);
                if (a.length == 0 || b.length == 0) continue;
                double sim = EmbeddingService.cosineSimilarity(a, b);
                if (sim >= SIMILARITY_THRESHOLD) {
                    all.add(new Pair(i, j, sim));
                }
            }
        }
        // Sort by similarity desc, take top MAX_CROSS_REFS (avoid clutter)
        all.sort((p1, p2) -> Double.compare(p2.sim, p1.sim));
        for (int k = 0; k < Math.min(MAX_CROSS_REFS, all.size()); k++) {
            Pair p = all.get(k);
            links.add(new int[]{p.i, p.j});
        }
        return links;
    }

    // ====================================================================
    //                          LAYOUT
    // ====================================================================

    private enum LayoutStrategy {GRID, HIERARCHICAL}

    private static class Layout {
        final List<int[]> positions = new ArrayList<>();
        int contentWidth;
        int contentHeight;
        LayoutStrategy strategy;
    }

    private static int computeGridCols(int n) {
        if (n <= 0) return 1;
        if (n <= 3) return n;
        if (n <= 6) return 3;
        return 4;
    }

    private Layout computeGridLayout(int n, int topicH) {
        Layout l = new Layout();
        l.strategy = LayoutStrategy.GRID;
        if (n == 0) {
            l.contentWidth = 1200;
            l.contentHeight = 200;
            return l;
        }
        int cols = computeGridCols(n);
        int rows = (int) Math.ceil((double) n / cols);
        int cellW = TOPIC_W + BULLET_W + STICKY_OFFSET + 2 * FRAME_PAD;
        int cellH = topicH + 2 * FRAME_PAD + 60;

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

    private Layout computeHierarchicalLayout(int n, int topicH) {
        Layout l = new Layout();
        l.strategy = LayoutStrategy.HIERARCHICAL;
        if (n == 0) {
            l.contentWidth = 1200;
            l.contentHeight = 200;
            return l;
        }

        int layers = Math.min(6, Math.max(2, (int) Math.ceil(Math.sqrt(n / 2.0))));
        int perLayer = (int) Math.ceil((double) n / layers);

        int cellW = TOPIC_W + BULLET_W + STICKY_OFFSET + 2 * FRAME_PAD;
        int cellH = topicH + 2 * FRAME_PAD + 60;
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
    //                          LEGEND
    // ====================================================================

    private void buildLegend(ExportContext ctx, int x, int y) {
        if (ctx.topicCount == 0) return;

        int rowH = 38;
        int headerH = 80;
        Set<String> usedThemes = new LinkedHashSet<>();
        for (Theme t : ctx.themes) usedThemes.add(t.name());
        int themeHeaderH = 40 + usedThemes.size() * 22 + 14;
        int legendH = headerH + ctx.topicCount * rowH + themeHeaderH + 40;

        String legendBgId = newId();
        ctx.addShape(rectangle(legendBgId, x, y, LEGEND_W, legendH,
                "#ffffff", "#adb5bd", "solid", 1, 6, null));

        ctx.addText(text("🎨 LEGEND", x + 16, y + 16, 16, LEGEND_W - 32, 24,
                "#212529", "left", null, null));
        ctx.addText(text("Topics & semantic themes", x + 16, y + 40, 11, LEGEND_W - 32, 16,
                "#868e96", "left", null, null));

        ctx.addShape(line(x + 12, y + 64, x + LEGEND_W - 12, y + 64, "#dee2e6", null));

        int curY = y + headerH;
        for (int i = 0; i < ctx.topicCount; i++) {
            String[] colors = ctx.themes.get(i).colors();
            String fill = colors[0], stroke = colors[1], darkText = colors[2];

            String swatchId = newId();
            ctx.addShape(rectangle(swatchId, x + 16, curY + 8, 22, 22,
                    fill, stroke, "solid", 2, 4, null));

            String swatchTextId = newId();
            ObjectNode swText = boundText(swatchTextId, swatchId, String.valueOf(i + 1),
                    x + 16, curY + 8, 22, 22, 11, darkText, "center");
            ctx.addText(swText);
            registerBoundText(ctx, swatchId, swatchTextId);

            String label = safeTrim(ctx.topics.get(i).getTitle() == null
                    ? "Topic " + (i + 1) : ctx.topics.get(i).getTitle(), 22);
            ctx.addText(text(label, x + 48, curY + 10, 11, LEGEND_W - 60, 20,
                    "#343a40", "left", null, null));

            curY += rowH;
        }

        curY += 12;
        ctx.addShape(line(x + 12, curY, x + LEGEND_W - 12, curY, "#dee2e6", null));
        curY += 10;
        ctx.addText(text("THEMES IN USE", x + 16, curY, 10, LEGEND_W - 32, 16,
                "#495057", "left", null, null));
        curY += 22;
        for (String themeName : usedThemes) {
            Theme th = THEMES.stream().filter(t -> t.name().equals(themeName))
                    .findFirst().orElse(THEMES.get(THEMES.size() - 1));
            ctx.addShape(ellipse(newId(), x + 18, curY + 4, 10, 10,
                    th.colors()[1], th.colors()[1], "solid", 1, null));
            ctx.addText(text(th.name(), x + 36, curY, 10, LEGEND_W - 52, 16,
                    "#495057", "left", null, null));
            curY += 22;
        }
    }

    // ====================================================================
    //                       BINDING REGISTRATION (O(1) — fixes I6)
    // ====================================================================

    private void registerBinding(ExportContext ctx, String shapeId, String arrowId) {
        ObjectNode shape = ctx.shapeIndex.get(shapeId);
        if (shape == null) {
            log.warn("registerBinding: shape {} not found", shapeId);
            return;
        }
        ArrayNode bound = ensureBoundElements(shape);
        ObjectNode entry = mapper.createObjectNode();
        entry.put("id", arrowId);
        entry.put("type", "arrow");
        bound.add(entry);
    }

    private void registerBoundText(ExportContext ctx, String containerId, String textId) {
        ObjectNode shape = ctx.shapeIndex.get(containerId);
        if (shape == null) {
            log.warn("registerBoundText: container {} not found", containerId);
            return;
        }
        ArrayNode bound = ensureBoundElements(shape);
        ObjectNode entry = mapper.createObjectNode();
        entry.put("id", textId);
        entry.put("type", "text");
        bound.add(entry);
    }

    private ArrayNode ensureBoundElements(ObjectNode shape) {
        JsonNode existing = shape.get("boundElements");
        if (existing != null && existing.isArray()) return (ArrayNode) existing;
        ArrayNode fresh = mapper.createArrayNode();
        shape.set("boundElements", fresh);
        return fresh;
    }

    // ====================================================================
    //                       UTILITIES
    // ====================================================================

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String safeTrim(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, Math.max(1, max - 1)) + "…" : s;
    }

    private String formatTime(double sec) {
        int s = Math.max(0, (int) sec);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private String formatDuration(double sec) {
        int s = Math.max(0, (int) sec);
        if (s < 3600) return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private int computeTextHeight(String text, int width, int fontSize) {
        if (text == null || text.isEmpty()) return fontSize * 2;
        int charsPerLine = Math.max(20, width / Math.max(1, fontSize / 2));
        int lines = Math.max(1, (int) Math.ceil((double) text.length() / charsPerLine));
        return lines * (int) (fontSize * 1.4);
    }

    private int countBullets(TopicSection t) {
        if (t.getBulletPoints() == null) return 0;
        return Math.min(MAX_BULLETS_PER_TOPIC, t.getBulletPoints().size());
    }

    private String buildPdfLink(Path pdfPath, int pageNumber) {
        try {
            return pdfPath.toUri().toString() + "#page=" + Math.max(1, pageNumber);
        } catch (Exception e) {
            log.debug("Failed to build PDF link: {}", e.getMessage());
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
                                 int strokeWidth, int roundnessValue, String frameId) {
        ObjectNode n = baseShape(id, "rectangle", x, y, w, h, stroke, bg, fillStyle, strokeWidth, frameId);
        if (roundnessValue > 0) {
            ObjectNode r = mapper.createObjectNode();
            r.put("type", 3);
            // FIX B1: Excalidraw rectangles use roundness {type: 3} only — NO "value"
            // Custom radii would need value, but stock Excalidraw ignores it for type=3.
            // Keep it simple: just type=3 for "rounded".
            n.set("roundness", r);
        } else {
            n.putNull("roundness");
        }
        return n;
    }

    private ObjectNode ellipse(String id, int x, int y, int w, int h,
                               String stroke, String bg, String fillStyle,
                               int strokeWidth, String frameId) {
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
        if (frameId != null) n.put("frameId", frameId);
        else n.set("frameId", null);
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

    /** Free-floating text (not bound to any container). */
    private ObjectNode text(String t, int x, int y, int fontSize, int width, int height,
                            String color, String align, String frameId, String link) {
        ObjectNode n = textBase(newId(), t, x, y, width, height, fontSize, color, align,
                "top", null, frameId, link);
        return n;
    }

    /** Text bound to a container shape — auto-wraps, auto-centers vertically. */
    private ObjectNode boundText(String textId, String containerId, String t,
                                 int containerX, int containerY,
                                 int containerW, int containerH,
                                 int fontSize, String color, String align) {
        // I7 fix: bound text inherits frame from container — don't set frameId here.
        // B8 fix: still pass container dims as text bounds; Excalidraw will handle layout.
        return textBase(textId, t, containerX, containerY, containerW, containerH,
                fontSize, color, align, "middle", containerId, null, null);
    }

    private ObjectNode textBase(String id, String t, int x, int y, int w, int h,
                                int fontSize, String color, String align, String vAlign,
                                String containerId, String frameId, String link) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("type", "text");
        n.put("x", x);
        n.put("y", y);
        n.put("width", w);
        n.put("height", h);
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
        if (frameId != null) n.put("frameId", frameId);
        else n.set("frameId", null);
        n.set("boundElements", mapper.createArrayNode());
        n.put("updated", System.currentTimeMillis());
        if (link != null) n.put("link", link);
        else n.set("link", null);
        n.put("locked", false);
        n.put("text", t == null ? "" : t);
        n.put("fontSize", fontSize);
        n.put("fontFamily", 2);  // Helvetica
        n.put("textAlign", align);
        n.put("verticalAlign", vAlign);
        n.put("baseline", (int) (fontSize * 0.85));
        if (containerId != null) {
            n.put("containerId", containerId);
            // autoResize lets the container expand when text overflows — fixes box sizing issue
            n.put("autoResize", true);
        } else {
            n.set("containerId", null);
        }
        n.put("originalText", t == null ? "" : t);
        n.put("lineHeight", containerId != null ? 1.4 : 1.25);
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
        if (frameId != null) n.put("frameId", frameId);
        else n.set("frameId", null);
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
        // I2 FIX: Excalidraw uses signed deltas; abs() was causing reverse rendering for some paths.
        n.put("width", x2 - x1);
        n.put("height", y2 - y1);
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
        if (frameId != null) n.put("frameId", frameId);
        else n.set("frameId", null);
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
        if (endHead != null) n.put("endArrowhead", endHead);
        else n.putNull("endArrowhead");
        n.put("elbowed", elbowed);
        n.putNull("roundness");
        return n;
    }
}