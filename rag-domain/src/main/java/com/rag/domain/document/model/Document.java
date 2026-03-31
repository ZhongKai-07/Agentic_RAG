package com.rag.domain.document.model;

import com.rag.domain.shared.model.SecurityLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Document {
    private UUID documentId;
    private UUID spaceId;
    private String title;
    private FileType fileType;
    private SecurityLevel securityLevel;
    private List<String> tags;
    private DocumentStatus status;
    private DocumentVersion currentVersion;
    private List<DocumentVersion> versions;
    private int chunkCount;
    private UUID uploadedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Document() {
        this.tags = new ArrayList<>();
        this.versions = new ArrayList<>();
        this.securityLevel = SecurityLevel.ALL;
        this.status = DocumentStatus.UPLOADED;
    }

    public void addVersion(DocumentVersion version) {
        this.versions.add(version);
        this.currentVersion = version;
        this.status = DocumentStatus.UPLOADED;
        this.updatedAt = Instant.now();
    }

    public void transitionTo(DocumentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    public SecurityLevel getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(SecurityLevel securityLevel) { this.securityLevel = securityLevel; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public DocumentVersion getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(DocumentVersion currentVersion) { this.currentVersion = currentVersion; }
    public List<DocumentVersion> getVersions() { return versions; }
    public void setVersions(List<DocumentVersion> versions) { this.versions = versions; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
