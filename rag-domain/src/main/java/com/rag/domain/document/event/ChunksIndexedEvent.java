package com.rag.domain.document.event;

import com.rag.domain.shared.event.DomainEvent;
import java.util.UUID;

public class ChunksIndexedEvent extends DomainEvent {
    private final UUID documentId;
    private final UUID spaceId;
    private final int chunkCount;

    public ChunksIndexedEvent(UUID documentId, UUID spaceId, int chunkCount) {
        this.documentId = documentId;
        this.spaceId = spaceId;
        this.chunkCount = chunkCount;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getSpaceId() { return spaceId; }
    public int getChunkCount() { return chunkCount; }
}
