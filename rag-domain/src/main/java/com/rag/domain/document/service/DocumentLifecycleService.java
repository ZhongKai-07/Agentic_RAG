package com.rag.domain.document.service;

import com.rag.domain.document.event.DocumentUploadedEvent;
import com.rag.domain.document.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class DocumentLifecycleService {

    public Document createDocument(UUID spaceId, String fileName, long fileSize,
                                    String filePath, String checksum, UUID uploadedBy) {
        Document doc = new Document();
        doc.setDocumentId(UUID.randomUUID());
        doc.setSpaceId(spaceId);
        doc.setTitle(fileName);
        doc.setFileType(FileType.fromFileName(fileName));
        doc.setSecurityLevel(SecurityLevel.ALL);
        doc.setUploadedBy(uploadedBy);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        DocumentVersion version = new DocumentVersion(
            UUID.randomUUID(), doc.getDocumentId(), 1,
            filePath, fileSize, checksum,
            Instant.now(), uploadedBy
        );
        doc.addVersion(version);

        return doc;
    }

    public DocumentVersion createNewVersion(Document document, String filePath,
                                             long fileSize, String checksum, UUID createdBy) {
        int nextVersionNo = document.getVersions().stream()
            .mapToInt(DocumentVersion::versionNo)
            .max()
            .orElse(0) + 1;

        DocumentVersion version = new DocumentVersion(
            UUID.randomUUID(), document.getDocumentId(), nextVersionNo,
            filePath, fileSize, checksum,
            Instant.now(), createdBy
        );
        document.addVersion(version);
        return version;
    }

    public DocumentUploadedEvent buildUploadedEvent(Document document) {
        DocumentVersion cv = document.getCurrentVersion();
        return new DocumentUploadedEvent(
            document.getDocumentId(),
            cv.versionId(),
            document.getSpaceId(),
            cv.filePath(),
            document.getTitle()
        );
    }

    /**
     * @deprecated Use {@link #createSha256Digest()} + {@link #formatChecksum(MessageDigest)} for streaming.
     */
    @Deprecated
    public static String computeChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }

    public static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String formatChecksum(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }
}
