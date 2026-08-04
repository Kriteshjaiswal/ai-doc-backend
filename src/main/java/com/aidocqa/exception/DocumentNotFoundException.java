package com.aidocqa.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }

    public DocumentNotFoundException(Long id) {
        super("Document not found with ID: " + id);
    }
}
