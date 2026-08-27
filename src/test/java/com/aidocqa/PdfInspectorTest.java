package com.aidocqa;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class PdfInspectorTest {

    @Test
    public void inspectDesignPatternPdf() throws Exception {
        File file = new File("uploads/1155cd09-e470-4187-8d52-58f77268a015_Design Pattern.pdf");
        if (!file.exists()) {
            File uploadsDir = new File("uploads");
            File[] files = uploadsDir.listFiles((dir, name) -> name.endsWith(".pdf") && name.contains("Design Pattern"));
            if (files != null && files.length > 0) {
                file = files[0];
            }
        }
        System.out.println("Testing with file: " + file.getAbsolutePath());
        if (!file.exists()) return;

        try (PDDocument doc = Loader.loadPDF(file)) {
            System.out.println("Total pages: " + doc.getNumberOfPages());
            
            // Check Bookmarks
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();
            if (outline != null && outline.hasChildren()) {
                System.out.println("--- PDF BOOKMARKS FOUND ---");
                PDOutlineItem item = outline.getFirstChild();
                while (item != null) {
                    PDPage page = item.findDestinationPage(doc);
                    int pageNum = page != null ? doc.getPages().indexOf(page) + 1 : -1;
                    System.out.println("Bookmark: " + item.getTitle() + " -> Page " + pageNum);
                    
                    // Check children
                    PDOutlineItem child = item.getFirstChild();
                    while (child != null) {
                        PDPage childPage = child.findDestinationPage(doc);
                        int childPageNum = childPage != null ? doc.getPages().indexOf(childPage) + 1 : -1;
                        System.out.println("   Child: " + child.getTitle() + " -> Page " + childPageNum);
                        child = child.getNextSibling();
                    }
                    item = item.getNextSibling();
                }
            } else {
                System.out.println("--- NO EMBEDDED PDF BOOKMARKS ---");
            }

            // Print First 15 pages text snippets to see Table of Contents
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= Math.min(15, doc.getNumberOfPages()); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                System.out.println("=== PAGE " + p + " ===");
                String[] lines = text.split("\n");
                for (int l = 0; l < Math.min(10, lines.length); l++) {
                    System.out.println("  " + lines[l].trim());
                }
            }
        }
    }
}
