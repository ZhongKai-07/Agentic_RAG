package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "t_knowledge_space")
public class KnowledgeSpaceEntity {
    @Id
    @Column(name = "space_id")
    private UUID spaceId;

    @Column(nullable = false, length = 128)
    private String name;

    private String description;

    @Column(name = "owner_team", nullable = false, length = 64)
    private String ownerTeam;

    @Column(nullable = false, length = 8)
    private String language;

    @Column(name = "index_name", nullable = false, unique = true, length = 128)
    private String indexName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieval_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> retrievalConfig;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (spaceId == null) spaceId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }

    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerTeam() { return ownerTeam; }
    public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public Map<String, Object> getRetrievalConfig() { return retrievalConfig; }
    public void setRetrievalConfig(Map<String, Object> retrievalConfig) { this.retrievalConfig = retrievalConfig; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
