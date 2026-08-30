package com.aidocqa.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class PdfExtractorService {

    /**
     * Automatically detects if a PDF book has a front-matter offset (e.g. 20 pages of Cover/Preface/TOC).
     * Searches the first 35 pages for the start of "Chapter 1" or printed page "1".
     * E.g., if Chapter 1 (Book Page 1) begins on PDF Page 21, the offset is 20 pages.
     */
    public int detectBookPageOffset(PDDocument document, int totalPages) {
        if (document == null || totalPages <= 5) return 0;
        int checkLimit = Math.min(35, totalPages);
        PDFTextStripper stripper = new PDFTextStripper();

        try {
            for (int p = 1; p <= checkLimit; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(document);
                if (text != null) {
                    String lower = text.toLowerCase();
                    if (lower.contains("chapter 1") || lower.contains("chapter i\n") || lower.contains("chapter one") ||
                        (p > 1 && (text.trim().endsWith("\n1") || text.trim().startsWith("1\n") || text.matches("(?s).*\\n\\s*1\\s*$")))) {
                        int offset = p - 1;
                        log.info("Detected PDF front-matter offset of {} pages (Chapter 1 / Book Page 1 starts on PDF Page {})", offset, p);
                        return offset;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error detecting book page offset: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Extracts all text content from a PDF file using Apache PDFBox,
     * annotating both physical PDF page numbers and book printed page numbers.
     */
    public String extractText(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) return "";
        log.info("Extracting page-annotated text from PDF: {}", pdfFile.getName());

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int totalPages = document.getNumberOfPages();
            int offset = detectBookPageOffset(document, totalPages);
            PDFTextStripper textStripper = new PDFTextStripper();
            StringBuilder sb = new StringBuilder();

            if (offset > 0) {
                sb.append("=== DOCUMENT PAGE MAPPING (FRONT MATTER OFFSET = ").append(offset).append(" PAGES) ===\n");
                sb.append("NOTE: Book Page 1 corresponds to physical PDF Document Page ").append(offset + 1).append(".\n");
                sb.append("ALWAYS cite using the PDF Document Page number (e.g. [Page ").append(offset + 87).append("] for Book Page 87) for viewer navigation.\n");
                sb.append("=========================================================================\n\n");
            }

            for (int p = 1; p <= totalPages; p++) {
                textStripper.setStartPage(p);
                textStripper.setEndPage(p);
                String pageText = textStripper.getText(document);

                if (pageText != null && !pageText.isBlank()) {
                    if (offset > 0 && p > offset) {
                        int bookPage = p - offset;
                        sb.append("\n--- PDF PAGE ").append(p).append(" (Book Page ").append(bookPage).append(") ---\n");
                    } else {
                        sb.append("\n--- PDF PAGE ").append(p).append(" ---\n");
                    }
                    sb.append(pageText.trim()).append("\n");
                }
            }

            String fullText = sb.toString().trim();
            log.info("Successfully extracted {} characters across {} pages (offset={}) from PDF: {}",
                    fullText.length(), totalPages, offset, pdfFile.getName());
            return fullText;
        }
    }

    /**
     * Extracts structured whole-document context across long documents (1 to 400+ pages),
     * ensuring each page is strictly labeled with its exact 1-based PDF page number and book page.
     */
    public String extractStructuredDocContext(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) return "";

        try (PDDocument pdDocument = Loader.loadPDF(pdfFile)) {
            int totalPages = pdDocument.getNumberOfPages();
            int offset = detectBookPageOffset(pdDocument, totalPages);
            log.info("Analyzing PDF '{}' with total {} pages (offset={}) for structured context", pdfFile.getName(), totalPages, offset);

            PDFTextStripper stripper = new PDFTextStripper();

            // Small to Medium documents (<= 30 pages): Return full page-annotated text
            if (totalPages <= 30) {
                return extractText(pdfFile);
            }

            StringBuilder contextBuilder = new StringBuilder();

            if (offset > 0) {
                contextBuilder.append("=== DOCUMENT PAGE MAPPING (FRONT MATTER OFFSET = ").append(offset).append(" PAGES) ===\n");
                contextBuilder.append("NOTE: Book Page 1 corresponds to physical PDF Document Page ").append(offset + 1).append(".\n");
                contextBuilder.append("ALWAYS cite using the PDF Document Page number (e.g. [Page ").append(offset + 87).append("] for Book Page 87) for viewer navigation.\n");
                contextBuilder.append("=========================================================================\n\n");
            }

            // 1. Beginning section (Pages 1 to 10): Title, Cover, Table of Contents, Preface, Abstract
            int startLimit = Math.min(10, totalPages);
            contextBuilder.append("=== DOCUMENT TITLE & FRONT MATTER (PAGES 1-").append(startLimit).append(") ===\n");
            for (int p = 1; p <= startLimit; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pText = stripper.getText(pdDocument);
                if (pText != null && !pText.isBlank()) {
                    appendPageWithOffset(contextBuilder, p, offset, pText.trim());
                }
            }

            // 2. Representative Middle Sections (Sample key regions across the document body)
            int step = (totalPages - 15) / 5;
            if (step > 0) {
                contextBuilder.append("\n=== REPRESENTATIVE CHAPTER HIGHLIGHTS & BODY SECTIONS ===\n");
                for (int i = 1; i <= 4; i++) {
                    int samplePage = 10 + (i * step);
                    if (samplePage < totalPages - 6) {
                        for (int p = samplePage; p <= Math.min(samplePage + 2, totalPages - 6); p++) {
                            stripper.setStartPage(p);
                            stripper.setEndPage(p);
                            String midText = stripper.getText(pdDocument);
                            if (midText != null && !midText.isBlank()) {
                                appendPageWithOffset(contextBuilder, p, offset, midText.trim());
                            }
                        }
                    }
                }
            }

            // 3. Ending Section (Final 6 pages): Conclusion, Summary, Index, Key Takeaways
            int startEnd = Math.max(11, totalPages - 5);
            contextBuilder.append("\n=== CONCLUDING SECTIONS & SUMMARY (PAGES ").append(startEnd).append("-").append(totalPages).append(") ===\n");
            for (int p = startEnd; p <= totalPages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String tailText = stripper.getText(pdDocument);
                if (tailText != null && !tailText.isBlank()) {
                    appendPageWithOffset(contextBuilder, p, offset, tailText.trim());
                }
            }

            String structuredText = contextBuilder.toString().trim();
            log.info("Extracted structured whole-document context ({} chars) for long PDF '{}'", structuredText.length(), pdfFile.getName());
            return structuredText;
        }
    }

    private void appendPageWithOffset(StringBuilder sb, int p, int offset, String text) {
        if (offset > 0 && p > offset) {
            int bookPage = p - offset;
            sb.append("\n--- PDF PAGE ").append(p).append(" (Book Page ").append(bookPage).append(") ---\n");
        } else {
            sb.append("\n--- PDF PAGE ").append(p).append(" ---\n");
        }
        sb.append(text).append("\n");
    }

    /**
     * Renders up to maxPages of PDF pages into PNG images as Base64 strings.
     * Enables Multimodal Vision / OCR analysis for scanned PDFs, tables, diagrams, and image-based PDFs.
     */
    public List<String> renderPdfPagesAsBase64(File pdfFile, int maxPages) {
        List<String> base64Images = new ArrayList<>();
        if (pdfFile == null || !pdfFile.exists()) {
            return base64Images;
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), maxPages);

            for (int i = 0; i < pageCount; i++) {
                try {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 150);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bim, "png", baos);
                    String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                    base64Images.add(base64Image);
                } catch (Exception e) {
                    log.warn("Failed to render page {} for PDF {}: {}", i, pdfFile.getName(), e.getMessage());
                }
            }
            log.info("Rendered {} page images from PDF for vision/OCR processing: {}", base64Images.size(), pdfFile.getName());
        } catch (Exception e) {
            log.error("Error rendering PDF pages for file {}: {}", pdfFile.getName(), e.getMessage());
        }
        return base64Images;
    }
}
