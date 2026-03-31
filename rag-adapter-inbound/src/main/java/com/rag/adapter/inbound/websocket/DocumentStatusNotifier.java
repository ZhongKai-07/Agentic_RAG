package com.rag.adapter.inbound.websocket;

import com.rag.domain.document.event.ChunksIndexedEvent;
import com.rag.domain.document.event.DocumentParsedEvent;
import com.rag.domain.document.event.DocumentUploadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentStatusNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public DocumentStatusNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        notify(event.getDocumentId().toString(), "UPLOADED", 0, "File uploaded, queued for parsing");
    }

    @EventListener
    public void onDocumentParsed(DocumentParsedEvent event) {
        notify(event.getDocumentId().toString(), "PARSED", 50,
            "Parsed " + event.getChunks().size() + " chunks, starting indexing");
    }

    @EventListener
    public void onChunksIndexed(ChunksIndexedEvent event) {
        notify(event.getDocumentId().toString(), "INDEXED", 100,
            "Successfully indexed " + event.getChunkCount() + " chunks");
    }

    private void notify(String documentId, String status, int progress, String message) {
        messagingTemplate.convertAndSend("/topic/documents/" + documentId, Map.of(
            "type", "DOCUMENT_STATUS_CHANGED",
            "payload", Map.of(
                "documentId", documentId,
                "status", status,
                "progress", progress,
                "message", message
            )
        ));
    }
}
