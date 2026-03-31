package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "t_document_tag", uniqueConstraints =
    @UniqueConstraint(columnNames = {"document_id", "tag_name"}))
public class DocumentTagEntity {
    @Id
    @Column(name = "tag_id")
    private UUID tagId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tag_name", nullable = false, length = 64)
    private String tagName;

    @PrePersist
    protected void onCreate() {
        if (tagId == null) tagId = UUID.randomUUID();
    }

    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
}
