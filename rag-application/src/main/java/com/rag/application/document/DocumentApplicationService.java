package com.rag.application.document;

import com.rag.domain.document.event.DocumentUploadedEvent;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.document.port.FileStoragePort;
import com.rag.domain.document.service.DocumentLifecycleService;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.knowledge.port.VectorStorePort;
import com.rag.domain.shared.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApplicationService.class);

    private final DocumentRepository documentRepository;
    private final FileStoragePort fileStoragePort;
    private final SpaceRepository spaceRepository;
    private final VectorStorePort vectorStorePort;
    private final DocumentLifecycleService lifecycleService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentApplicationService(DocumentRepository documentRepository,
                                       FileStoragePort fileStoragePort,
                                       SpaceRepository spaceRepository,
                                       VectorStorePort vectorStorePort,
                                       ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.fileStoragePort = fileStoragePort;
        this.spaceRepository = spaceRepository;
        this.vectorStorePort = vectorStorePort;
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
        // Keep reference to version before save() clears it during round-trip
        DocumentVersion version = document.getCurrentVersion();
        documentRepository.save(document);           // persist document row first (FK target)
        documentRepository.saveVersion(version);      // persist version row (FK satisfied)
        // Re-read so the returned object has currentVersion populated from DB
        Document saved = documentRepository.findById(document.getDocumentId())
            .orElseThrow();

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

    public Document getDocumentInSpace(UUID documentId, UUID spaceId) {
        Document doc = getDocument(documentId);
        if (!doc.getSpaceId().equals(spaceId)) {
            throw new IllegalArgumentException("Document " + documentId + " does not belong to space " + spaceId);
        }
        return doc;
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
        // Delete all version files (not just current)
        deleteAllVersionFiles(documentId);
        // Delete vector store chunks
        deleteVectorChunks(doc.getSpaceId(), documentId);
        documentRepository.deleteById(documentId);
    }

    @Transactional
    public void batchDelete(List<UUID> documentIds) {
        for (UUID id : documentIds) {
            Document doc = getDocument(id);
            deleteAllVersionFiles(id);
            deleteVectorChunks(doc.getSpaceId(), id);
        }
        documentRepository.deleteByIds(documentIds);
    }

    private void deleteAllVersionFiles(UUID documentId) {
        List<DocumentVersion> versions = documentRepository.findVersionsByDocumentId(documentId);
        for (DocumentVersion v : versions) {
            try {
                fileStoragePort.delete(v.filePath());
            } catch (Exception e) {
                log.warn("Failed to delete version file {}: {}", v.filePath(), e.getMessage());
            }
        }
    }

    private void deleteVectorChunks(UUID spaceId, UUID documentId) {
        try {
            KnowledgeSpace space = spaceRepository.findById(spaceId).orElse(null);
            if (space != null) {
                vectorStorePort.deleteByDocumentId(space.getIndexName(), documentId.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to delete vector chunks for document {}: {}", documentId, e.getMessage());
        }
    }

    @Transactional
    public void batchUpdateTags(List<UUID> documentIds, List<String> tagsToAdd, List<String> tagsToRemove) {
        documentRepository.batchUpdateTags(documentIds, tagsToAdd, tagsToRemove);
    }

    @Transactional
    public Document retryParse(UUID documentId) {
        Document document = getDocument(documentId);
        var status = document.getStatus();
        if (status != com.rag.domain.document.model.DocumentStatus.FAILED
            && status != com.rag.domain.document.model.DocumentStatus.PARSING) {
            throw new IllegalStateException(
                "Only FAILED or stuck PARSING documents can be retried, current status: " + status);
        }
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
