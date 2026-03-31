package com.rag.domain.identity.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KnowledgeSpace {
    private UUID spaceId;
    private String name;
    private String description;
    private String ownerTeam;
    private String language;
    private String indexName;
    private RetrievalConfig retrievalConfig;
    private SpaceStatus status;
    private List<AccessRule> accessRules;
    private Instant createdAt;
    private Instant updatedAt;

    public KnowledgeSpace() {
        this.accessRules = new ArrayList<>();
    }

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
    public RetrievalConfig getRetrievalConfig() { return retrievalConfig; }
    public void setRetrievalConfig(RetrievalConfig retrievalConfig) { this.retrievalConfig = retrievalConfig; }
    public SpaceStatus getStatus() { return status; }
    public void setStatus(SpaceStatus status) { this.status = status; }
    public List<AccessRule> getAccessRules() { return accessRules; }
    public void setAccessRules(List<AccessRule> accessRules) { this.accessRules = accessRules; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
