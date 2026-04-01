package com.rag.application.event;

import com.rag.domain.document.event.DocumentParsedEvent;
import com.rag.domain.document.event.DocumentUploadedEvent;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentStatus;
import com.rag.domain.document.port.DocParserPort;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.document.port.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ParseEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ParseEventHandler.class);

    private final DocParserPort docParserPort;
    private final FileStoragePort fileStoragePort;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ParseEventHandler(DocParserPort docParserPort,
                              FileStoragePort fileStoragePort,
                              DocumentRepository documentRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.docParserPort = docParserPort;
        this.fileStoragePort = fileStoragePort;
        this.documentRepository = documentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Async("documentProcessingExecutor")
    @EventListener
    public void handle(DocumentUploadedEvent event) {
        log.info("Parsing document: {} ({})", event.getDocumentId(), event.getFileName());

        Document document = documentRepository.findById(event.getDocumentId()).orElse(null);
        if (document == null) {
            log.error("Document not found: {}", event.getDocumentId());
            return;
        }

        try {
            // Transition: UPLOADED → PARSING
            document.transitionTo(DocumentStatus.PARSING);
            documentRepository.save(document);

            // Parse via docling
            DocParserPort.ParseResult result;
            try (InputStream fileStream = fileStoragePort.retrieve(event.getFilePath())) {
                result = docParserPort.parse(event.getFileName(), fileStream);
            }

            // Transition: PARSING → PARSED
            document.transitionTo(DocumentStatus.PARSED);
            document.setChunkCount(result.chunks().size());
            documentRepository.save(document);

            log.info("Parsed {} chunks from document {}", result.chunks().size(), event.getDocumentId());

            // Publish next event
            eventPublisher.publishEvent(new DocumentParsedEvent(
                event.getDocumentId(), event.getVersionId(), event.getSpaceId(),
                document.getTitle(), result.chunks()
            ));
        } catch (Exception e) {
            log.error("Failed to parse document: {}", event.getDocumentId(), e);
            if (document.getStatus() != DocumentStatus.FAILED) {
                document.transitionTo(DocumentStatus.FAILED);
                documentRepository.save(document);
            }
        }
    }
}
