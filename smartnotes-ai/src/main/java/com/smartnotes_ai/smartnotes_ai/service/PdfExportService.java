package com.smartnotes_ai.smartnotes_ai.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.smartnotes_ai.smartnotes_ai.dto.TopicSection;
import com.smartnotes_ai.smartnotes_ai.dto.VideoMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class PdfExportService {

    @Value("${smartnotes.workspace}")
    private String workspace;

    public Path export(VideoMetadata meta, String overallSummary, List<TopicSection> topics) {
        Path outDir = Path.of(workspace, meta.getVideoId());
        try { Files.createDirectories(outDir); } catch (Exception e) { throw new RuntimeException(e); }
        Path pdfFile = outDir.resolve("notes.pdf");

        try (PdfWriter writer = new PdfWriter(pdfFile.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.add(new Paragraph(meta.getTitle())
                    .setBold().setFontSize(20).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("By " + meta.getUploader())
                    .setItalic().setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()));

            doc.add(new Paragraph("Overview").setBold().setFontSize(16).setMarginTop(10));
            doc.add(new Paragraph(overallSummary));

            for (int i = 0; i < topics.size(); i++) {
                TopicSection t = topics.get(i);
                doc.add(new Paragraph((i + 1) + ". " + t.getTitle())
                        .setBold().setFontSize(14).setMarginTop(15));

                doc.add(new Paragraph(String.format("Time: %.0fs - %.0fs",
                        t.getStartTime(), t.getEndTime()))
                        .setFontColor(ColorConstants.GRAY).setFontSize(9));

                doc.add(new Paragraph(t.getSummary()));

                com.itextpdf.layout.element.List elem = new com.itextpdf.layout.element.List().setSymbolIndent(12);
                if (t.getBulletPoints() != null) {
                    for (String b : t.getBulletPoints()) {
                        elem.add(new ListItem(b));
                    }
                }
                doc.add(elem);

                if (t.getScreenshotPath() != null) {
                    try {
                        Image img = new Image(ImageDataFactory.create(t.getScreenshotPath()));
                        img.setWidth(UnitValue.createPercentValue(80));
                        img.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                        doc.add(img);
                        if (t.getScreenshotCaption() != null) {
                            doc.add(new Paragraph(t.getScreenshotCaption())
                                    .setItalic().setFontSize(9)
                                    .setTextAlignment(TextAlignment.CENTER)
                                    .setFontColor(ColorConstants.DARK_GRAY));
                        }
                    } catch (Exception e) {
                        log.warn("Could not embed image: {}", e.getMessage());
                    }
                }
            }

            log.info("PDF generated at: {}", pdfFile);
            return pdfFile;
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed", e);
        }
    }
}