package com.rag.domain.document.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersion(
    UUID versionId,
    UUID documentId,
    int versionNo,
    String filePath,
    long fileSize,
    String checksum,
    Instant createdAt,
    UUID createdBy
) {}
