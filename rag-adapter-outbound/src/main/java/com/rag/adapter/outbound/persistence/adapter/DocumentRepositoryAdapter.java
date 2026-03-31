package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.entity.DocumentTagEntity;
import com.rag.adapter.outbound.persistence.mapper.DocumentMapper;
import com.rag.adapter.outbound.persistence.repository.*;
import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import com.rag.domain.document.port.DocumentRepository;
import com.rag.domain.shared.model.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DocumentRepositoryAdapter implements DocumentRepository {

    private final DocumentJpaRepository docJpa;
    private final DocumentVersionJpaRepository versionJpa;
    private final DocumentTagJpaRepository tagJpa;

    public DocumentRepositoryAdapter(DocumentJpaRepository docJpa,
                                      DocumentVersionJpaRepository versionJpa,
                                      DocumentTagJpaRepository tagJpa) {
        this.docJpa = docJpa;
        this.versionJpa = versionJpa;
        this.tagJpa = tagJpa;
    }

    @Override
    @Transactional
    public Document save(Document document) {
        var savedEntity = docJpa.save(DocumentMapper.toEntity(document));
        // Save tags
        tagJpa.deleteByDocumentId(document.getDocumentId());
        for (String tag : document.getTags()) {
            DocumentTagEntity te = new DocumentTagEntity();
            te.setDocumentId(document.getDocumentId());
            te.setTagName(tag);
            tagJpa.save(te);
        }
        var tags = tagJpa.findByDocumentId(savedEntity.getDocumentId());
        var versions = versionJpa.findByDocumentIdOrderByVersionNoDesc(savedEntity.getDocumentId());
        return DocumentMapper.toDomain(savedEntity, versions, tags);
    }

    @Override
    public Optional<Document> findById(UUID documentId) {
        return docJpa.findById(documentId).map(e -> {
            var versions = versionJpa.findByDocumentIdOrderByVersionNoDesc(documentId);
            var tags = tagJpa.findByDocumentId(documentId);
            return DocumentMapper.toDomain(e, versions, tags);
        });
    }

    @Override
    public PageResult<Document> findBySpaceId(UUID spaceId, int page, int size, String search) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<com.rag.adapter.outbound.persistence.entity.DocumentEntity> result =
            docJpa.findBySpaceId(spaceId, search, pageable);
        List<Document> docs = result.getContent().stream().map(e -> {
            var tags = tagJpa.findByDocumentId(e.getDocumentId());
            return DocumentMapper.toDomainBasic(e, tags);
        }).toList();
        return new PageResult<>(docs, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public void deleteById(UUID documentId) {
        tagJpa.deleteByDocumentId(documentId);
        docJpa.deleteById(documentId);
    }

    @Override
    @Transactional
    public void deleteByIds(List<UUID> documentIds) {
        documentIds.forEach(this::deleteById);
    }

    @Override
    public DocumentVersion saveVersion(DocumentVersion version) {
        var saved = versionJpa.save(DocumentMapper.toVersionEntity(version));
        return DocumentMapper.toVersionDomain(saved);
    }

    @Override
    public List<DocumentVersion> findVersionsByDocumentId(UUID documentId) {
        return versionJpa.findByDocumentIdOrderByVersionNoDesc(documentId)
            .stream().map(DocumentMapper::toVersionDomain).toList();
    }

    @Override
    @Transactional
    public void updateTags(UUID documentId, List<String> tags) {
        tagJpa.deleteByDocumentId(documentId);
        for (String tag : tags) {
            DocumentTagEntity te = new DocumentTagEntity();
            te.setDocumentId(documentId);
            te.setTagName(tag);
            tagJpa.save(te);
        }
    }

    @Override
    @Transactional
    public void batchUpdateTags(List<UUID> documentIds, List<String> tagsToAdd, List<String> tagsToRemove) {
        for (UUID docId : documentIds) {
            if (tagsToRemove != null && !tagsToRemove.isEmpty()) {
                tagJpa.deleteByDocumentIdAndTagNameIn(docId, tagsToRemove);
            }
            if (tagsToAdd != null) {
                for (String tag : tagsToAdd) {
                    var existing = tagJpa.findByDocumentId(docId).stream()
                        .anyMatch(t -> t.getTagName().equals(tag));
                    if (!existing) {
                        DocumentTagEntity te = new DocumentTagEntity();
                        te.setDocumentId(docId);
                        te.setTagName(tag);
                        tagJpa.save(te);
                    }
                }
            }
        }
    }

    @Override
    public long countBySpaceId(UUID spaceId) {
        return docJpa.countBySpaceId(spaceId);
    }

    @Override
    public long countBySpaceIdAndStatus(UUID spaceId, String status) {
        return docJpa.countBySpaceIdAndStatus(spaceId, status);
    }
}
