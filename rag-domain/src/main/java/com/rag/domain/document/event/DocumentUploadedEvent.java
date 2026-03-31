package com.rag.domain.document.event;

import com.rag.domain.shared.event.DomainEvent;
import java.util.UUID;

public class DocumentUploadedEvent extends DomainEvent {
    private final UUID documentId;
    private final UUID versionId;
    private final UUID spaceId;
    private final String filePath;
    private final String fileName;

    public DocumentUploadedEvent(UUID documentId, UUID versionId, UUID spaceId,
                                  String filePath, String fileName) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.spaceId = spaceId;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getVersionId() { return versionId; }
    public UUID getSpaceId() { return spaceId; }
    public String getFilePath() { return filePath; }
    public String getFileName() { return fileName; }
}
