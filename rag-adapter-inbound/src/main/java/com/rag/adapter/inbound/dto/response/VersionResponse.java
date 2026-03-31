package com.rag.adapter.inbound.dto.response;

import com.rag.domain.document.model.DocumentVersion;
import java.time.Instant;
import java.util.UUID;

public record VersionResponse(
    UUID versionId, int versionNo, String filePath,
    long fileSize, String checksum, Instant createdAt, UUID createdBy
) {
    public static VersionResponse from(DocumentVersion v) {
        return new VersionResponse(v.versionId(), v.versionNo(), v.filePath(),
            v.fileSize(), v.checksum(), v.createdAt(), v.createdBy());
    }
}
