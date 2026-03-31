package com.rag.domain.document.event;

import com.rag.domain.document.port.DocParserPort;
import com.rag.domain.shared.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public class DocumentParsedEvent extends DomainEvent {
    private final UUID documentId;
    private final UUID versionId;
    private final UUID spaceId;
    private final String documentTitle;
    private final List<DocParserPort.ParsedChunk> chunks;

    public DocumentParsedEvent(UUID documentId, UUID versionId, UUID spaceId,
                                String documentTitle, List<DocParserPort.ParsedChunk> chunks) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.spaceId = spaceId;
        this.documentTitle = documentTitle;
        this.chunks = chunks;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getVersionId() { return versionId; }
    public UUID getSpaceId() { return spaceId; }
    public String getDocumentTitle() { return documentTitle; }
    public List<DocParserPort.ParsedChunk> getChunks() { return chunks; }
}
