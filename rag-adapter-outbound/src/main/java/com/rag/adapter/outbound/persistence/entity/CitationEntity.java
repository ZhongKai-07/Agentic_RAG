package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "t_citation")
public class CitationEntity {
    @Id
    @Column(name = "citation_id")
    private UUID citationId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "citation_index", nullable = false)
    private int citationIndex;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_id", nullable = false, length = 128)
    private String chunkId;

    @Column(name = "document_title", nullable = false, length = 512)
    private String documentTitle;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section_path", length = 512)
    private String sectionPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snippet;

    @PrePersist
    protected void onCreate() {
        if (citationId == null) citationId = UUID.randomUUID();
    }

    public UUID getCitationId() { return citationId; }
    public void setCitationId(UUID citationId) { this.citationId = citationId; }
    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public int getCitationIndex() { return citationIndex; }
    public void setCitationIndex(int citationIndex) { this.citationIndex = citationIndex; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getSectionPath() { return sectionPath; }
    public void setSectionPath(String sectionPath) { this.sectionPath = sectionPath; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
