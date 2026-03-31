package com.rag.adapter.inbound.dto.response;

import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
    UUID documentId, UUID spaceId, String title, String fileType,
    String securityLevel, String status, int chunkCount,
    List<String> tags, UUID uploadedBy,
    List<VersionResponse> versions,
    Instant createdAt, Instant updatedAt
) {
    public static DocumentDetailResponse from(Document d) {
        var versions = d.getVersions().stream().map(VersionResponse::from).toList();
        return new DocumentDetailResponse(d.getDocumentId(), d.getSpaceId(), d.getTitle(),
            d.getFileType().name(), d.getSecurityLevel().name(), d.getStatus().name(),
            d.getChunkCount(), d.getTags(), d.getUploadedBy(),
            versions, d.getCreatedAt(), d.getUpdatedAt());
    }
}
