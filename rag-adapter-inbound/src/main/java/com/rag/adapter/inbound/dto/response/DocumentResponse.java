package com.rag.adapter.inbound.dto.response;

import com.rag.domain.document.model.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
    UUID documentId, UUID spaceId, String title, String fileType,
    String securityLevel, String status, int chunkCount,
    String currentVersionNo, List<String> tags,
    UUID uploadedBy, Instant createdAt, Instant updatedAt
) {
    public static DocumentResponse from(Document d) {
        String versionNo = d.getCurrentVersion() != null
            ? "v" + d.getCurrentVersion().versionNo() : null;
        return new DocumentResponse(d.getDocumentId(), d.getSpaceId(), d.getTitle(),
            d.getFileType().name(), d.getSecurityLevel().name(), d.getStatus().name(),
            d.getChunkCount(), versionNo, d.getTags(),
            d.getUploadedBy(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
