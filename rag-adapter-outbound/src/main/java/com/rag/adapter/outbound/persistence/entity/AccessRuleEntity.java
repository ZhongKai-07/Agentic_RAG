package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_access_rule")
public class AccessRuleEntity {
    @Id
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_value", nullable = false, length = 64)
    private String targetValue;

    @Column(name = "doc_security_clearance", nullable = false, length = 16)
    private String docSecurityClearance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (ruleId == null) ruleId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
    public String getDocSecurityClearance() { return docSecurityClearance; }
    public void setDocSecurityClearance(String docSecurityClearance) { this.docSecurityClearance = docSecurityClearance; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
