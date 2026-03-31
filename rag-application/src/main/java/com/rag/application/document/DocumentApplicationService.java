package com.rag.application.document;

import com.rag.domain.document.event.DocumentUploadedEvent;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.document.port.FileStoragePort;
import com.rag.domain.document.service.DocumentLifecycleService;
import com.rag.domain.shared.model.PageResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentApplicationService {

    private final DocumentRepository documentRepository;
    private final FileStoragePort fileStoragePort;
    private final DocumentLifecycleService lifecycleService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentApplicationService(DocumentRepository documentRepository,
                                       FileStoragePort fileStoragePort,
                                       ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.fileStoragePort = fileStoragePort;
        this.lifecycleService = new DocumentLifecycleService();
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Document uploadDocument(UUID spaceId, String fileName, long fileSize,
                                    byte[] fileContent, UUID uploadedBy) {
        String checksum = DocumentLifecycleService.computeChecksum(fileContent);
        String storagePath = spaceId + "/" + UUID.randomUUID() + "/" + fileName;
        fileStoragePort.store(storagePath, new java.io.ByteArrayInputStream(fileContent));

        Document document = lifecycleService.createDocument(
            spaceId, fileName, fileSize, storagePath, checksum, uploadedBy);
        Document saved = documentRepository.save(document);
        documentRepository.saveVersion(saved.getCurrentVersion());

        eventPublisher.publishEvent(lifecycleService.buildUploadedEvent(saved));
        return saved;
    }

    @Transactional
    public Document uploadNewVersion(UUID documentId, String fileName, long fileSize,
                                      byte[] fileContent, UUID createdBy) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        String checksum = DocumentLifecycleService.computeChecksum(fileContent);
        String storagePath = document.getSpaceId() + "/" + UUID.randomUUID() + "/" + fileName;
        fileStoragePort.store(storagePath, new java.io.ByteArrayInputStream(fileContent));

        DocumentVersion newVersion = lifecycleService.createNewVersion(
            document, storagePath, fileSize, checksum, createdBy);
        documentRepository.saveVersion(newVersion);
        Document saved = documentRepository.save(document);

        eventPublisher.publishEvent(lifecycleService.buildUploadedEvent(saved));
        return saved;
    }

    public Document getDocument(UUID documentId) {
        return documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    public PageResult<Document> listDocuments(UUID spaceId, int page, int size, String search) {
        return documentRepository.findBySpaceId(spaceId, page, size, search);
    }

    public List<DocumentVersion> listVersions(UUID documentId) {
        return documentRepository.findVersionsByDocumentId(documentId);
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        Document doc = getDocument(documentId);
        if (doc.getCurrentVersion() != null) {
            fileStoragePort.delete(doc.getCurrentVersion().filePath());
        }
        documentRepository.deleteById(documentId);
    }

    @Transactional
    public void batchDelete(List<UUID> documentIds) {
        documentIds.forEach(this::deleteDocument);
    }

    @Transactional
    public void batchUpdateTags(List<UUID> documentIds, List<String> tagsToAdd, List<String> tagsToRemove) {
        documentRepository.batchUpdateTags(documentIds, tagsToAdd, tagsToRemove);
    }

    @Transactional
    public Document retryParse(UUID documentId) {
        Document document = getDocument(documentId);
        document.transitionTo(com.rag.domain.document.model.DocumentStatus.UPLOADED);
        Document saved = documentRepository.save(document);
        eventPublisher.publishEvent(lifecycleService.buildUploadedEvent(saved));
        return saved;
    }

    public InputStream downloadFile(UUID documentId) {
        Document doc = getDocument(documentId);
        return fileStoragePort.retrieve(doc.getCurrentVersion().filePath());
    }
}
