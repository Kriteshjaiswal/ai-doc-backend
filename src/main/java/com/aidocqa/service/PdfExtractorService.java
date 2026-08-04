package com.aidocqa.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

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
                    text.length(), pdfFile.getName());
            return text;
        }
    }
}
