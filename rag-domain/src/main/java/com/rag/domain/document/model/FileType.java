package com.rag.domain.document.model;

public enum FileType {
    PDF,
    WORD,
    EXCEL;

    public static FileType fromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return PDF;
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return WORD;
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return EXCEL;
        throw new IllegalArgumentException("Unsupported file type: " + fileName);
    }
}
