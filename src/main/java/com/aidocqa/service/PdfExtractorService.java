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
     * Extracts all text content from a PDF file using Apache PDFBox.
     *
     * @param pdfFile the PDF file to extract text from
     * @return the extracted text content
     * @throws IOException if the file cannot be read or parsed
     */
    public String extractText(File pdfFile) throws IOException {
        log.info("Extracting text from PDF: {}", pdfFile.getName());

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String text = textStripper.getText(document);

            log.info("Successfully extracted {} characters from PDF: {}",
                    text != null ? text.length() : 0, pdfFile.getName());
            return text != null ? text.trim() : "";
        }
    }

    /**
     * Extracts structured whole-document context across long documents (1 to 400+ pages).
     * Ensures books, multi-chapter manuals, syllabi, reports, and contracts are analyzed
     * across Beginning (TOC/Front matter), Middle Chapters, and Ending (Conclusion).
     */
    public String extractStructuredDocContext(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) return "";

        try (PDDocument pdDocument = Loader.loadPDF(pdfFile)) {
            int totalPages = pdDocument.getNumberOfPages();
            log.info("Analyzing PDF '{}' with total {} pages", pdfFile.getName(), totalPages);

            PDFTextStripper stripper = new PDFTextStripper();

            // Small documents (<= 15 pages): Return full text
            if (totalPages <= 15) {
                String fullText = stripper.getText(pdDocument);
                return fullText != null ? fullText.trim() : "";
            }

            // Long documents (16 to 400+ pages): Sample whole-document structural regions
            StringBuilder contextBuilder = new StringBuilder();

            // 1. Beginning section (Pages 1 to 8): Title, Cover, Table of Contents, Preface, Abstract
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(8, totalPages));
            String headText = stripper.getText(pdDocument);
            contextBuilder.append("=== DOCUMENT TITLE, FRONT MATTER & TABLE OF CONTENTS (PAGES 1-")
                          .append(Math.min(8, totalPages)).append(") ===\n")
                          .append(headText != null ? headText.trim() : "").append("\n\n");

            // 2. Representative Middle Sections (Sample 3 key regions across the document body)
            int step = (totalPages - 12) / 4;
            if (step > 0) {
                contextBuilder.append("=== REPRESENTATIVE CHAPTER HIGHLIGHTS & BODY SECTIONS ===\n");
                for (int i = 1; i <= 3; i++) {
                    int samplePage = 8 + (i * step);
                    if (samplePage < totalPages - 4) {
                        stripper.setStartPage(samplePage);
                        stripper.setEndPage(Math.min(samplePage + 2, totalPages - 4));
                        String midText = stripper.getText(pdDocument);
                        contextBuilder.append("--- Section Sample (Page ").append(samplePage).append(" to ").append(Math.min(samplePage + 2, totalPages - 4)).append(") ---\n")
                                      .append(midText != null ? midText.trim() : "").append("\n\n");
                    }
                }
            }

            // 3. Ending Section (Final 4 pages): Conclusion, Summary, Index, Key Takeaways
            int startEnd = Math.max(9, totalPages - 3);
            stripper.setStartPage(startEnd);
            stripper.setEndPage(totalPages);
            String tailText = stripper.getText(pdDocument);
            contextBuilder.append("=== CONCLUDING SECTIONS & SUMMARY (PAGES ").append(startEnd).append("-").append(totalPages).append(") ===\n")
                          .append(tailText != null ? tailText.trim() : "").append("\n");

            String structuredText = contextBuilder.toString();
            log.info("Extracted structured whole-document context ({} chars) for long PDF '{}'", structuredText.length(), pdfFile.getName());
            return structuredText;
        }
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
