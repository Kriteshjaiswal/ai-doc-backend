package com.aidocqa.utility;

import com.aidocqa.exception.InvalidFileException;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public final class FileUtil {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PDF_EXTENSION = ".pdf";

    private FileUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Validates that the uploaded file is a PDF.
     *
     * @param file the uploaded file
     * @throws InvalidFileException if the file is not a valid PDF
     */
    public static void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty. Please upload a valid PDF file.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase(PDF_CONTENT_TYPE)) {
            throw new InvalidFileException("Invalid file type. Only PDF files are accepted.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(PDF_EXTENSION)) {
            throw new InvalidFileException("Invalid file extension. Only .pdf files are accepted.");
        }
    }

    /**
     * Generates a unique file name by prepending a UUID to the original file name.
     *
     * @param originalFileName the original file name
     * @return a unique file name
     */
    public static String generateUniqueFileName(String originalFileName) {
        return UUID.randomUUID().toString() + "_" + originalFileName;
    }
}
