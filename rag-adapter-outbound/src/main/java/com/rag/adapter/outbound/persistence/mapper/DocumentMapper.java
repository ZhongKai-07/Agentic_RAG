package com.rag.adapter.outbound.persistence.mapper;

import com.rag.adapter.outbound.persistence.entity.*;
import com.rag.domain.document.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.util.List;
import java.util.Objects;

public class DocumentMapper {

    public static Document toDomain(DocumentEntity e, List<DocumentVersionEntity> versions,
                                     List<DocumentTagEntity> tags) {
        Document d = new Document();
        d.setDocumentId(e.getDocumentId());
        d.setSpaceId(e.getSpaceId());
        d.setTitle(e.getTitle());
        d.setFileType(FileType.valueOf(e.getFileType()));
        d.setSecurityLevel(SecurityLevel.valueOf(e.getSecurityLevel()));
        d.setStatus(DocumentStatus.valueOf(e.getStatus()));
        d.setChunkCount(e.getChunkCount());
        d.setUploadedBy(e.getUploadedBy());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());

        List<DocumentVersion> domainVersions = versions.stream()
            .map(DocumentMapper::toVersionDomain).toList();
        d.setVersions(new java.util.ArrayList<>(domainVersions));

        if (e.getCurrentVersionId() != null) {
            domainVersions.stream()
                .filter(v -> v.versionId().equals(e.getCurrentVersionId()))
                .findFirst()
                .ifPresent(d::setCurrentVersion);
        }

        d.setTags(new java.util.ArrayList<>(tags.stream().map(DocumentTagEntity::getTagName).toList()));
        return d;
    }

    public static Document toDomainBasic(DocumentEntity e, List<DocumentTagEntity> tags) {
        Document d = new Document();
        d.setDocumentId(e.getDocumentId());
        d.setSpaceId(e.getSpaceId());
        d.setTitle(e.getTitle());
        d.setFileType(FileType.valueOf(e.getFileType()));
        d.setSecurityLevel(SecurityLevel.valueOf(e.getSecurityLevel()));
        d.setStatus(DocumentStatus.valueOf(e.getStatus()));
        d.setChunkCount(e.getChunkCount());
        d.setUploadedBy(e.getUploadedBy());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        d.setTags(new java.util.ArrayList<>(tags.stream().map(DocumentTagEntity::getTagName).toList()));
        return d;
    }

    public static DocumentEntity toEntity(Document d) {
        DocumentEntity e = new DocumentEntity();
        e.setDocumentId(d.getDocumentId());
        e.setSpaceId(d.getSpaceId());
        e.setTitle(d.getTitle());
        e.setFileType(d.getFileType().name());
        e.setSecurityLevel(d.getSecurityLevel().name());
        e.setStatus(d.getStatus().name());
        e.setChunkCount(d.getChunkCount());
        e.setUploadedBy(d.getUploadedBy());
        if (d.getCurrentVersion() != null) {
            e.setCurrentVersionId(d.getCurrentVersion().versionId());
        }
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    public static DocumentVersion toVersionDomain(DocumentVersionEntity e) {
        return new DocumentVersion(e.getVersionId(), e.getDocumentId(), e.getVersionNo(),
            e.getFilePath(), e.getFileSize(), e.getChecksum(), e.getCreatedAt(), e.getCreatedBy());
    }

    public static DocumentVersionEntity toVersionEntity(DocumentVersion v) {
        Objects.requireNonNull(v,
            "DocumentVersion must not be null — ensure version is captured before document.save() clears it");
        DocumentVersionEntity e = new DocumentVersionEntity();
        e.setVersionId(v.versionId());
        e.setDocumentId(v.documentId());
        e.setVersionNo(v.versionNo());
        e.setFilePath(v.filePath());
        e.setFileSize(v.fileSize());
        e.setChecksum(v.checksum());
        e.setCreatedAt(v.createdAt());
        e.setCreatedBy(v.createdBy());
        return e;
    }
}
