# Plan 2: Identity & Document Management

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Identity context (User, KnowledgeSpace, AccessRule, SpacePermission) and Document context (Document CRUD, version management, batch operations) with full REST APIs, JPA persistence, and domain services.

**Architecture:** Domain models in `rag-domain`, application command/query handlers in `rag-application`, REST controllers in `rag-adapter-inbound`, JPA entities + repositories in `rag-adapter-outbound`. File upload triggers `DocumentUploadedEvent` (consumed by Plan 3). This plan does NOT implement async parsing or vector indexing — only the file management side.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Data JPA, PostgreSQL 16, Flyway, Multipart file upload

**Depends on:** Plan 1 (project skeleton, DB schema, Docker infrastructure)

---

## File Structure

```
rag-domain/src/main/java/com/rag/domain/
├── identity/
│   ├── model/
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── UserStatus.java
│   │   ├── KnowledgeSpace.java
│   │   ├── SpaceStatus.java
│   │   ├── AccessRule.java
│   │   ├── AccessLevel.java
│   │   ├── TargetType.java
│   │   └── RetrievalConfig.java
│   ├── service/SpaceAuthorizationService.java
│   └── port/UserRepository.java              (modify — add methods)
├── document/
│   ├── model/
│   │   ├── Document.java
│   │   ├── DocumentVersion.java
│   │   ├── DocumentStatus.java
│   │   └── FileType.java
│   ├── event/DocumentUploadedEvent.java
│   ├── service/DocumentLifecycleService.java
│   └── port/DocumentRepository.java          (modify — add methods)

rag-application/src/main/java/com/rag/application/
├── identity/
│   ├── command/
│   │   ├── CreateSpaceCommand.java
│   │   └── UpdateAccessRulesCommand.java
│   └── query/
│       ├── ListAccessibleSpacesQuery.java
│       └── SpaceDetailQuery.java
├── document/
│   ├── command/
│   │   ├── UploadDocumentCommand.java
│   │   ├── UploadNewVersionCommand.java
│   │   ├── BatchUpdateTagsCommand.java
│   │   ├── BatchDeleteCommand.java
│   │   └── RetryParseCommand.java
│   └── query/
│       ├── ListDocumentsQuery.java
│       └── DocumentDetailQuery.java

rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/
├── rest/
│   ├── SpaceController.java
│   ├── DocumentController.java
│   └── UserController.java
└── dto/
    ├── request/
    │   ├── CreateSpaceRequest.java
    │   ├── UpdateSpaceRequest.java
    │   ├── UpdateAccessRulesRequest.java
    │   ├── BatchUpdateTagsRequest.java
    │   └── BatchDeleteRequest.java
    └── response/
        ├── SpaceResponse.java
        ├── DocumentResponse.java
        ├── DocumentDetailResponse.java
        ├── VersionResponse.java
        └── UserResponse.java

rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/
├── persistence/
│   ├── entity/
│   │   ├── UserEntity.java
│   │   ├── KnowledgeSpaceEntity.java
│   │   ├── AccessRuleEntity.java
│   │   ├── SpacePermissionEntity.java
│   │   ├── DocumentEntity.java
│   │   ├── DocumentVersionEntity.java
│   │   └── DocumentTagEntity.java
│   ├── repository/
│   │   ├── UserJpaRepository.java
│   │   ├── KnowledgeSpaceJpaRepository.java
│   │   ├── AccessRuleJpaRepository.java
│   │   ├── SpacePermissionJpaRepository.java
│   │   ├── DocumentJpaRepository.java
│   │   ├── DocumentVersionJpaRepository.java
│   │   └── DocumentTagJpaRepository.java
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   ├── SpaceMapper.java
│   │   └── DocumentMapper.java
│   └── adapter/
│       ├── UserRepositoryAdapter.java
│       └── DocumentRepositoryAdapter.java
└── storage/
    └── LocalFileStorageAdapter.java
```

---

### Task 1: Identity Domain Models

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/Role.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/UserStatus.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/User.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/SpaceStatus.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/TargetType.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/AccessLevel.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/AccessRule.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/RetrievalConfig.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/model/KnowledgeSpace.java`

- [ ] **Step 1: Create enums**

`rag-domain/src/main/java/com/rag/domain/identity/model/Role.java`:
```java
package com.rag.domain.identity.model;

public enum Role {
    ADMIN,
    MANAGER,
    MEMBER
}
```

`rag-domain/src/main/java/com/rag/domain/identity/model/UserStatus.java`:
```java
package com.rag.domain.identity.model;

public enum UserStatus {
    ACTIVE,
    INACTIVE
}
```

`rag-domain/src/main/java/com/rag/domain/identity/model/SpaceStatus.java`:
```java
package com.rag.domain.identity.model;

public enum SpaceStatus {
    ACTIVE,
    ARCHIVED
}
```

`rag-domain/src/main/java/com/rag/domain/identity/model/TargetType.java`:
```java
package com.rag.domain.identity.model;

public enum TargetType {
    BU,
    TEAM,
    USER
}
```

`rag-domain/src/main/java/com/rag/domain/identity/model/AccessLevel.java`:
```java
package com.rag.domain.identity.model;

public enum AccessLevel {
    OWNER,
    EDITOR,
    VIEWER
}
```

- [ ] **Step 2: Create User model**

`rag-domain/src/main/java/com/rag/domain/identity/model/User.java`:
```java
package com.rag.domain.identity.model;

import java.time.Instant;
import java.util.UUID;

public class User {
    private UUID userId;
    private String username;
    private String displayName;
    private String email;
    private String bu;
    private String team;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {}

    public User(UUID userId, String username, String displayName, String email,
                String bu, String team, Role role, UserStatus status,
                Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.bu = bu;
        this.team = team;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBu() { return bu; }
    public void setBu(String bu) { this.bu = bu; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: Create AccessRule and RetrievalConfig**

`rag-domain/src/main/java/com/rag/domain/identity/model/AccessRule.java`:
```java
package com.rag.domain.identity.model;

import com.rag.domain.shared.model.SecurityLevel;
import java.util.UUID;

public record AccessRule(
    UUID ruleId,
    UUID spaceId,
    TargetType targetType,
    String targetValue,
    SecurityLevel docSecurityClearance
) {}
```

`rag-domain/src/main/java/com/rag/domain/identity/model/RetrievalConfig.java`:
```java
package com.rag.domain.identity.model;

public record RetrievalConfig(
    int maxAgentRounds,
    String chunkingStrategy,
    String metadataExtractionPrompt
) {
    public RetrievalConfig() {
        this(3, "semantic_header", "");
    }

    public int maxAgentRounds(int defaultValue) {
        return maxAgentRounds > 0 ? maxAgentRounds : defaultValue;
    }
}
```

- [ ] **Step 4: Create KnowledgeSpace model**

`rag-domain/src/main/java/com/rag/domain/identity/model/KnowledgeSpace.java`:
```java
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
```

- [ ] **Step 5: Verify compilation**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

- [ ] **Step 6: Commit**

```bash
git add rag-domain/src/main/java/com/rag/domain/identity/model/
git commit -m "feat(domain): add Identity context domain models — User, KnowledgeSpace, AccessRule"
```

---

### Task 2: Document Domain Models + Events

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/document/model/FileType.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/model/DocumentStatus.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/model/DocumentVersion.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/model/Document.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/event/DocumentUploadedEvent.java`

- [ ] **Step 1: Create enums**

`rag-domain/src/main/java/com/rag/domain/document/model/FileType.java`:
```java
package com.rag.domain.document.model;

public enum FileType {
    PDF,
    WORD,
    EXCEL;

    public static FileType fromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return PDF;
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return WORD;
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return EXCEL;
        throw new IllegalArgumentException("Unsupported file type: " + fileName);
    }
}
```

`rag-domain/src/main/java/com/rag/domain/document/model/DocumentStatus.java`:
```java
package com.rag.domain.document.model;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    PARSED,
    INDEXING,
    INDEXED,
    FAILED;

    public boolean canTransitionTo(DocumentStatus next) {
        return switch (this) {
            case UPLOADED -> next == PARSING || next == FAILED;
            case PARSING -> next == PARSED || next == FAILED;
            case PARSED -> next == INDEXING || next == FAILED;
            case INDEXING -> next == INDEXED || next == FAILED;
            case INDEXED -> next == UPLOADED; // re-upload new version
            case FAILED -> next == UPLOADED;  // retry
        };
    }
}
```

- [ ] **Step 2: Create DocumentVersion**

`rag-domain/src/main/java/com/rag/domain/document/model/DocumentVersion.java`:
```java
package com.rag.domain.document.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersion(
    UUID versionId,
    UUID documentId,
    int versionNo,
    String filePath,
    long fileSize,
    String checksum,
    Instant createdAt,
    UUID createdBy
) {}
```

- [ ] **Step 3: Create Document aggregate root**

`rag-domain/src/main/java/com/rag/domain/document/model/Document.java`:
```java
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
```

- [ ] **Step 4: Create DocumentUploadedEvent**

`rag-domain/src/main/java/com/rag/domain/document/event/DocumentUploadedEvent.java`:
```java
package com.rag.domain.document.event;

import com.rag.domain.shared.event.DomainEvent;
import java.util.UUID;

public class DocumentUploadedEvent extends DomainEvent {
    private final UUID documentId;
    private final UUID versionId;
    private final UUID spaceId;
    private final String filePath;
    private final String fileName;

    public DocumentUploadedEvent(UUID documentId, UUID versionId, UUID spaceId,
                                  String filePath, String fileName) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.spaceId = spaceId;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getVersionId() { return versionId; }
    public UUID getSpaceId() { return spaceId; }
    public String getFilePath() { return filePath; }
    public String getFileName() { return fileName; }
}
```

- [ ] **Step 5: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/document/
git commit -m "feat(domain): add Document context models — Document, DocumentVersion, DocumentStatus, DocumentUploadedEvent"
```

---

### Task 3: Domain Port Interfaces (populate placeholders)

**Files:**
- Modify: `rag-domain/src/main/java/com/rag/domain/identity/port/UserRepository.java`
- Modify: `rag-domain/src/main/java/com/rag/domain/document/port/DocumentRepository.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/port/SpaceRepository.java`

- [ ] **Step 1: Populate UserRepository**

Replace the entire content of `rag-domain/src/main/java/com/rag/domain/identity/port/UserRepository.java`:
```java
package com.rag.domain.identity.port;

import com.rag.domain.identity.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID userId);
    Optional<User> findByUsername(String username);
    User save(User user);
}
```

- [ ] **Step 2: Create SpaceRepository**

`rag-domain/src/main/java/com/rag/domain/identity/port/SpaceRepository.java`:
```java
package com.rag.domain.identity.port;

import com.rag.domain.identity.model.AccessRule;
import com.rag.domain.identity.model.KnowledgeSpace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository {
    KnowledgeSpace save(KnowledgeSpace space);
    Optional<KnowledgeSpace> findById(UUID spaceId);
    List<KnowledgeSpace> findAccessibleSpaces(String bu, String team, UUID userId);
    void saveAccessRules(UUID spaceId, List<AccessRule> rules);
    void deleteAccessRulesBySpaceId(UUID spaceId);
}
```

- [ ] **Step 3: Populate DocumentRepository**

Replace the entire content of `rag-domain/src/main/java/com/rag/domain/document/port/DocumentRepository.java`:
```java
package com.rag.domain.document.port;

import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import com.rag.domain.shared.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(UUID documentId);
    PageResult<Document> findBySpaceId(UUID spaceId, int page, int size, String search);
    void deleteById(UUID documentId);
    void deleteByIds(List<UUID> documentIds);
    DocumentVersion saveVersion(DocumentVersion version);
    List<DocumentVersion> findVersionsByDocumentId(UUID documentId);
    void updateTags(UUID documentId, List<String> tags);
    void batchUpdateTags(List<UUID> documentIds, List<String> tagsToAdd, List<String> tagsToRemove);
    long countBySpaceId(UUID spaceId);
    long countBySpaceIdAndStatus(UUID spaceId, String status);
}
```

- [ ] **Step 4: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/identity/port/ rag-domain/src/main/java/com/rag/domain/document/port/
git commit -m "feat(domain): populate UserRepository, SpaceRepository, DocumentRepository port interfaces"
```

---

### Task 4: Domain Services

**Files:**
- Create: `rag-domain/src/main/java/com/rag/domain/identity/service/SpaceAuthorizationService.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/service/DocumentLifecycleService.java`

- [ ] **Step 1: Create SpaceAuthorizationService**

`rag-domain/src/main/java/com/rag/domain/identity/service/SpaceAuthorizationService.java`:
```java
package com.rag.domain.identity.service;

import com.rag.domain.identity.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.util.List;

public class SpaceAuthorizationService {

    public boolean canAccessSpace(User user, KnowledgeSpace space) {
        return space.getAccessRules().stream().anyMatch(rule -> matchesRule(user, rule));
    }

    public SecurityLevel resolveSecurityClearance(User user, KnowledgeSpace space) {
        return space.getAccessRules().stream()
            .filter(rule -> matchesRule(user, rule))
            .map(AccessRule::docSecurityClearance)
            .reduce(SecurityLevel.ALL, (a, b) ->
                a == SecurityLevel.MANAGEMENT || b == SecurityLevel.MANAGEMENT
                    ? SecurityLevel.MANAGEMENT : SecurityLevel.ALL);
    }

    private boolean matchesRule(User user, AccessRule rule) {
        return switch (rule.targetType()) {
            case BU -> rule.targetValue().equals(user.getBu());
            case TEAM -> rule.targetValue().equals(user.getTeam());
            case USER -> rule.targetValue().equals(user.getUserId().toString());
        };
    }
}
```

- [ ] **Step 2: Create DocumentLifecycleService**

`rag-domain/src/main/java/com/rag/domain/document/service/DocumentLifecycleService.java`:
```java
package com.rag.domain.document.service;

import com.rag.domain.document.event.DocumentUploadedEvent;
import com.rag.domain.document.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class DocumentLifecycleService {

    public Document createDocument(UUID spaceId, String fileName, long fileSize,
                                    String filePath, String checksum, UUID uploadedBy) {
        Document doc = new Document();
        doc.setDocumentId(UUID.randomUUID());
        doc.setSpaceId(spaceId);
        doc.setTitle(fileName);
        doc.setFileType(FileType.fromFileName(fileName));
        doc.setSecurityLevel(SecurityLevel.ALL);
        doc.setUploadedBy(uploadedBy);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        DocumentVersion version = new DocumentVersion(
            UUID.randomUUID(), doc.getDocumentId(), 1,
            filePath, fileSize, checksum,
            Instant.now(), uploadedBy
        );
        doc.addVersion(version);

        return doc;
    }

    public DocumentVersion createNewVersion(Document document, String filePath,
                                             long fileSize, String checksum, UUID createdBy) {
        int nextVersionNo = document.getVersions().stream()
            .mapToInt(DocumentVersion::versionNo)
            .max()
            .orElse(0) + 1;

        DocumentVersion version = new DocumentVersion(
            UUID.randomUUID(), document.getDocumentId(), nextVersionNo,
            filePath, fileSize, checksum,
            Instant.now(), createdBy
        );
        document.addVersion(version);
        return version;
    }

    public DocumentUploadedEvent buildUploadedEvent(Document document) {
        DocumentVersion cv = document.getCurrentVersion();
        return new DocumentUploadedEvent(
            document.getDocumentId(),
            cv.versionId(),
            document.getSpaceId(),
            cv.filePath(),
            document.getTitle()
        );
    }

    public static String computeChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK"`
Expected: OK

```bash
git add rag-domain/src/main/java/com/rag/domain/identity/service/ rag-domain/src/main/java/com/rag/domain/document/service/
git commit -m "feat(domain): add SpaceAuthorizationService and DocumentLifecycleService"
```

---

### Task 5: JPA Entities

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/UserEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/KnowledgeSpaceEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/AccessRuleEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/SpacePermissionEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/DocumentEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/DocumentVersionEntity.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/DocumentTagEntity.java`

- [ ] **Step 1: Create UserEntity**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/UserEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_user")
public class UserEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(length = 256)
    private String email;

    @Column(nullable = false, length = 32)
    private String bu;

    @Column(nullable = false, length = 64)
    private String team;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (userId == null) userId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }

    // Getters and setters
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBu() { return bu; }
    public void setBu(String bu) { this.bu = bu; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Create KnowledgeSpaceEntity + AccessRuleEntity + SpacePermissionEntity**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/KnowledgeSpaceEntity.java`:
```java
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
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/AccessRuleEntity.java`:
```java
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
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/SpacePermissionEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_space_permission", uniqueConstraints =
    @UniqueConstraint(columnNames = {"user_id", "space_id"}))
public class SpacePermissionEntity {
    @Id
    @Column(name = "permission_id")
    private UUID permissionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "access_level", nullable = false, length = 16)
    private String accessLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (permissionId == null) permissionId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getPermissionId() { return permissionId; }
    public void setPermissionId(UUID permissionId) { this.permissionId = permissionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Create Document entities**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/DocumentEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_document")
public class DocumentEntity {
    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "file_type", nullable = false, length = 16)
    private String fileType;

    @Column(name = "security_level", nullable = false, length = 16)
    private String securityLevel;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (documentId == null) documentId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(String securityLevel) { this.securityLevel = securityLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(UUID currentVersionId) { this.currentVersionId = currentVersionId; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/DocumentVersionEntity.java`:
```java
package com.rag.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_document_version", uniqueConstraints =
    @UniqueConstraint(columnNames = {"document_id", "version_no"}))
public class DocumentVersionEntity {
    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @PrePersist
    protected void onCreate() {
        if (versionId == null) versionId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/DocumentTagEntity.java`:
```java
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
```

- [ ] **Step 4: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/entity/
git commit -m "feat(persistence): add JPA entities for User, KnowledgeSpace, AccessRule, Document, DocumentVersion, DocumentTag"
```

---

### Task 6: JPA Repositories + Mappers + Adapters

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/UserJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/KnowledgeSpaceJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/AccessRuleJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/SpacePermissionJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/DocumentJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/DocumentVersionJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/DocumentTagJpaRepository.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/UserMapper.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/SpaceMapper.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/DocumentMapper.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/UserRepositoryAdapter.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/SpaceRepositoryAdapter.java`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/DocumentRepositoryAdapter.java`

- [ ] **Step 1: Create Spring Data JPA repository interfaces**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/UserJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/KnowledgeSpaceJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.KnowledgeSpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface KnowledgeSpaceJpaRepository extends JpaRepository<KnowledgeSpaceEntity, UUID> {
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/AccessRuleJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.AccessRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AccessRuleJpaRepository extends JpaRepository<AccessRuleEntity, UUID> {
    List<AccessRuleEntity> findBySpaceId(UUID spaceId);
    void deleteBySpaceId(UUID spaceId);

    @Query("SELECT DISTINCT r.spaceId FROM AccessRuleEntity r WHERE " +
           "(r.targetType = 'BU' AND r.targetValue = :bu) OR " +
           "(r.targetType = 'TEAM' AND r.targetValue = :team) OR " +
           "(r.targetType = 'USER' AND r.targetValue = :userId)")
    List<UUID> findAccessibleSpaceIds(@Param("bu") String bu,
                                      @Param("team") String team,
                                      @Param("userId") String userId);
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/SpacePermissionJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.SpacePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpacePermissionJpaRepository extends JpaRepository<SpacePermissionEntity, UUID> {
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/DocumentJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    @Query("SELECT d FROM DocumentEntity d WHERE d.spaceId = :spaceId " +
           "AND (:search IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<DocumentEntity> findBySpaceId(@Param("spaceId") UUID spaceId,
                                        @Param("search") String search,
                                        Pageable pageable);

    long countBySpaceId(UUID spaceId);
    long countBySpaceIdAndStatus(UUID spaceId, String status);
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/DocumentVersionJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentVersionJpaRepository extends JpaRepository<DocumentVersionEntity, UUID> {
    List<DocumentVersionEntity> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/DocumentTagJpaRepository.java`:
```java
package com.rag.adapter.outbound.persistence.repository;

import com.rag.adapter.outbound.persistence.entity.DocumentTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentTagJpaRepository extends JpaRepository<DocumentTagEntity, UUID> {
    List<DocumentTagEntity> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
    void deleteByDocumentIdAndTagNameIn(UUID documentId, List<String> tagNames);
}
```

- [ ] **Step 2: Create mappers (Entity <-> Domain)**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/UserMapper.java`:
```java
package com.rag.adapter.outbound.persistence.mapper;

import com.rag.adapter.outbound.persistence.entity.UserEntity;
import com.rag.domain.identity.model.Role;
import com.rag.domain.identity.model.User;
import com.rag.domain.identity.model.UserStatus;

public class UserMapper {

    public static User toDomain(UserEntity e) {
        return new User(e.getUserId(), e.getUsername(), e.getDisplayName(), e.getEmail(),
            e.getBu(), e.getTeam(), Role.valueOf(e.getRole()),
            UserStatus.valueOf(e.getStatus()), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static UserEntity toEntity(User u) {
        UserEntity e = new UserEntity();
        e.setUserId(u.getUserId());
        e.setUsername(u.getUsername());
        e.setDisplayName(u.getDisplayName());
        e.setEmail(u.getEmail());
        e.setBu(u.getBu());
        e.setTeam(u.getTeam());
        e.setRole(u.getRole().name());
        e.setStatus(u.getStatus().name());
        e.setCreatedAt(u.getCreatedAt());
        e.setUpdatedAt(u.getUpdatedAt());
        return e;
    }
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/SpaceMapper.java`:
```java
package com.rag.adapter.outbound.persistence.mapper;

import com.rag.adapter.outbound.persistence.entity.AccessRuleEntity;
import com.rag.adapter.outbound.persistence.entity.KnowledgeSpaceEntity;
import com.rag.domain.identity.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.util.HashMap;
import java.util.Map;

public class SpaceMapper {

    public static KnowledgeSpace toDomain(KnowledgeSpaceEntity e) {
        KnowledgeSpace s = new KnowledgeSpace();
        s.setSpaceId(e.getSpaceId());
        s.setName(e.getName());
        s.setDescription(e.getDescription());
        s.setOwnerTeam(e.getOwnerTeam());
        s.setLanguage(e.getLanguage());
        s.setIndexName(e.getIndexName());
        s.setStatus(SpaceStatus.valueOf(e.getStatus()));
        s.setCreatedAt(e.getCreatedAt());
        s.setUpdatedAt(e.getUpdatedAt());

        Map<String, Object> rc = e.getRetrievalConfig();
        if (rc != null && !rc.isEmpty()) {
            s.setRetrievalConfig(new RetrievalConfig(
                rc.getOrDefault("maxAgentRounds", 3) instanceof Number n ? n.intValue() : 3,
                (String) rc.getOrDefault("chunkingStrategy", "semantic_header"),
                (String) rc.getOrDefault("metadataExtractionPrompt", "")
            ));
        } else {
            s.setRetrievalConfig(new RetrievalConfig());
        }
        return s;
    }

    public static KnowledgeSpaceEntity toEntity(KnowledgeSpace s) {
        KnowledgeSpaceEntity e = new KnowledgeSpaceEntity();
        e.setSpaceId(s.getSpaceId());
        e.setName(s.getName());
        e.setDescription(s.getDescription());
        e.setOwnerTeam(s.getOwnerTeam());
        e.setLanguage(s.getLanguage());
        e.setIndexName(s.getIndexName());
        e.setStatus(s.getStatus().name());
        e.setCreatedAt(s.getCreatedAt());
        e.setUpdatedAt(s.getUpdatedAt());

        RetrievalConfig rc = s.getRetrievalConfig();
        if (rc != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("maxAgentRounds", rc.maxAgentRounds());
            map.put("chunkingStrategy", rc.chunkingStrategy());
            map.put("metadataExtractionPrompt", rc.metadataExtractionPrompt());
            e.setRetrievalConfig(map);
        } else {
            e.setRetrievalConfig(Map.of());
        }
        return e;
    }

    public static AccessRule toAccessRuleDomain(AccessRuleEntity e) {
        return new AccessRule(e.getRuleId(), e.getSpaceId(),
            TargetType.valueOf(e.getTargetType()), e.getTargetValue(),
            SecurityLevel.valueOf(e.getDocSecurityClearance()));
    }

    public static AccessRuleEntity toAccessRuleEntity(AccessRule r) {
        AccessRuleEntity e = new AccessRuleEntity();
        e.setRuleId(r.ruleId());
        e.setSpaceId(r.spaceId());
        e.setTargetType(r.targetType().name());
        e.setTargetValue(r.targetValue());
        e.setDocSecurityClearance(r.docSecurityClearance().name());
        return e;
    }
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/DocumentMapper.java`:
```java
package com.rag.adapter.outbound.persistence.mapper;

import com.rag.adapter.outbound.persistence.entity.*;
import com.rag.domain.document.model.*;
import com.rag.domain.shared.model.SecurityLevel;

import java.util.List;

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
```

- [ ] **Step 3: Create repository adapters (implements domain ports)**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/UserRepositoryAdapter.java`:
```java
package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.mapper.UserMapper;
import com.rag.adapter.outbound.persistence.repository.UserJpaRepository;
import com.rag.domain.identity.model.User;
import com.rag.domain.identity.port.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return jpa.findById(userId).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(UserMapper::toDomain);
    }

    @Override
    public User save(User user) {
        return UserMapper.toDomain(jpa.save(UserMapper.toEntity(user)));
    }
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/SpaceRepositoryAdapter.java`:
```java
package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.mapper.SpaceMapper;
import com.rag.adapter.outbound.persistence.repository.AccessRuleJpaRepository;
import com.rag.adapter.outbound.persistence.repository.KnowledgeSpaceJpaRepository;
import com.rag.domain.identity.model.AccessRule;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.port.SpaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SpaceRepositoryAdapter implements SpaceRepository {

    private final KnowledgeSpaceJpaRepository spaceJpa;
    private final AccessRuleJpaRepository ruleJpa;

    public SpaceRepositoryAdapter(KnowledgeSpaceJpaRepository spaceJpa,
                                   AccessRuleJpaRepository ruleJpa) {
        this.spaceJpa = spaceJpa;
        this.ruleJpa = ruleJpa;
    }

    @Override
    public KnowledgeSpace save(KnowledgeSpace space) {
        var saved = spaceJpa.save(SpaceMapper.toEntity(space));
        KnowledgeSpace result = SpaceMapper.toDomain(saved);
        List<AccessRule> rules = ruleJpa.findBySpaceId(space.getSpaceId())
            .stream().map(SpaceMapper::toAccessRuleDomain).toList();
        result.setAccessRules(new java.util.ArrayList<>(rules));
        return result;
    }

    @Override
    public Optional<KnowledgeSpace> findById(UUID spaceId) {
        return spaceJpa.findById(spaceId).map(e -> {
            KnowledgeSpace s = SpaceMapper.toDomain(e);
            List<AccessRule> rules = ruleJpa.findBySpaceId(spaceId)
                .stream().map(SpaceMapper::toAccessRuleDomain).toList();
            s.setAccessRules(new java.util.ArrayList<>(rules));
            return s;
        });
    }

    @Override
    public List<KnowledgeSpace> findAccessibleSpaces(String bu, String team, UUID userId) {
        List<UUID> spaceIds = ruleJpa.findAccessibleSpaceIds(bu, team, userId.toString());
        return spaceJpa.findAllById(spaceIds).stream().map(e -> {
            KnowledgeSpace s = SpaceMapper.toDomain(e);
            List<AccessRule> rules = ruleJpa.findBySpaceId(e.getSpaceId())
                .stream().map(SpaceMapper::toAccessRuleDomain).toList();
            s.setAccessRules(new java.util.ArrayList<>(rules));
            return s;
        }).toList();
    }

    @Override
    public void saveAccessRules(UUID spaceId, List<AccessRule> rules) {
        ruleJpa.saveAll(rules.stream().map(SpaceMapper::toAccessRuleEntity).toList());
    }

    @Override
    @Transactional
    public void deleteAccessRulesBySpaceId(UUID spaceId) {
        ruleJpa.deleteBySpaceId(spaceId);
    }
}
```

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/DocumentRepositoryAdapter.java`:
```java
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
```

- [ ] **Step 4: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-outbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/repository/ \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/mapper/ \
        rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/adapter/
git commit -m "feat(persistence): add JPA repositories, entity-domain mappers, and repository adapter implementations"
```

---

### Task 7: LocalFileStorageAdapter

**Files:**
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/storage/LocalFileStorageAdapter.java`

- [ ] **Step 1: Create local file storage implementation**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/storage/LocalFileStorageAdapter.java`:
```java
package com.rag.adapter.outbound.storage;

import com.rag.domain.document.port.FileStoragePort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Profile("local")
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path basePath;

    public LocalFileStorageAdapter(ServiceRegistryConfig.FileStorageProperties props) {
        this.basePath = Paths.get(props.getBasePath());
    }

    @Override
    public String store(String path, InputStream content) {
        try {
            Path fullPath = basePath.resolve(path);
            Files.createDirectories(fullPath.getParent());
            Files.copy(content, fullPath);
            return fullPath.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + path, e);
        }
    }

    @Override
    public InputStream retrieve(String path) {
        try {
            return Files.newInputStream(basePath.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to retrieve file: " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(basePath.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file: " + path, e);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/storage/
git commit -m "feat(storage): add LocalFileStorageAdapter implementing FileStoragePort"
```

---

### Task 8: Application Layer — Command & Query Handlers

**Files:**
- Create: `rag-application/src/main/java/com/rag/application/identity/SpaceApplicationService.java`
- Create: `rag-application/src/main/java/com/rag/application/document/DocumentApplicationService.java`

- [ ] **Step 1: Create SpaceApplicationService**

`rag-application/src/main/java/com/rag/application/identity/SpaceApplicationService.java`:
```java
package com.rag.application.identity;

import com.rag.domain.identity.model.*;
import com.rag.domain.identity.port.SpaceRepository;
import com.rag.domain.identity.port.UserRepository;
import com.rag.domain.shared.model.SecurityLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SpaceApplicationService {

    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;

    public SpaceApplicationService(SpaceRepository spaceRepository, UserRepository userRepository) {
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public KnowledgeSpace createSpace(String name, String description, String ownerTeam,
                                       String language, String indexName) {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(UUID.randomUUID());
        space.setName(name);
        space.setDescription(description);
        space.setOwnerTeam(ownerTeam);
        space.setLanguage(language);
        space.setIndexName(indexName);
        space.setRetrievalConfig(new RetrievalConfig());
        space.setStatus(SpaceStatus.ACTIVE);
        space.setCreatedAt(Instant.now());
        space.setUpdatedAt(Instant.now());
        return spaceRepository.save(space);
    }

    public KnowledgeSpace getSpace(UUID spaceId) {
        return spaceRepository.findById(spaceId)
            .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
    }

    public List<KnowledgeSpace> listAccessibleSpaces(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return spaceRepository.findAccessibleSpaces(user.getBu(), user.getTeam(), userId);
    }

    @Transactional
    public KnowledgeSpace updateAccessRules(UUID spaceId, List<AccessRule> newRules) {
        KnowledgeSpace space = getSpace(spaceId);
        spaceRepository.deleteAccessRulesBySpaceId(spaceId);
        List<AccessRule> rulesWithIds = newRules.stream()
            .map(r -> new AccessRule(UUID.randomUUID(), spaceId,
                r.targetType(), r.targetValue(), r.docSecurityClearance()))
            .toList();
        spaceRepository.saveAccessRules(spaceId, rulesWithIds);
        return spaceRepository.findById(spaceId).orElseThrow();
    }
}
```

- [ ] **Step 2: Create DocumentApplicationService**

`rag-application/src/main/java/com/rag/application/document/DocumentApplicationService.java`:
```java
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
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-application -q && echo "OK"`
Expected: OK

```bash
git add rag-application/src/main/java/com/rag/application/
git commit -m "feat(application): add SpaceApplicationService and DocumentApplicationService with CQRS command handling"
```

---

### Task 9: DTOs (Request/Response)

**Files:**
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/CreateSpaceRequest.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/UpdateAccessRulesRequest.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/BatchUpdateTagsRequest.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/BatchDeleteRequest.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/SpaceResponse.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/DocumentResponse.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/DocumentDetailResponse.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/VersionResponse.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/UserResponse.java`

- [ ] **Step 1: Create request DTOs**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/CreateSpaceRequest.java`:
```java
package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSpaceRequest(
    @NotBlank String name,
    String description,
    @NotBlank String ownerTeam,
    @NotBlank String language,
    @NotBlank String indexName
) {}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/UpdateAccessRulesRequest.java`:
```java
package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAccessRulesRequest(
    @NotNull List<AccessRuleDto> rules
) {
    public record AccessRuleDto(
        @NotNull String targetType,
        @NotNull String targetValue,
        String docSecurityClearance
    ) {}
}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/BatchUpdateTagsRequest.java`:
```java
package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BatchUpdateTagsRequest(
    @NotNull List<UUID> documentIds,
    List<String> tagsToAdd,
    List<String> tagsToRemove
) {}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/request/BatchDeleteRequest.java`:
```java
package com.rag.adapter.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BatchDeleteRequest(@NotNull List<UUID> documentIds) {}
```

- [ ] **Step 2: Create response DTOs**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/SpaceResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.identity.model.KnowledgeSpace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpaceResponse(
    UUID spaceId, String name, String description, String ownerTeam,
    String language, String indexName, String status,
    List<AccessRuleResponse> accessRules, Instant createdAt, Instant updatedAt
) {
    public record AccessRuleResponse(UUID ruleId, String targetType, String targetValue, String docSecurityClearance) {}

    public static SpaceResponse from(KnowledgeSpace s) {
        var rules = s.getAccessRules().stream().map(r ->
            new AccessRuleResponse(r.ruleId(), r.targetType().name(),
                r.targetValue(), r.docSecurityClearance().name())
        ).toList();
        return new SpaceResponse(s.getSpaceId(), s.getName(), s.getDescription(),
            s.getOwnerTeam(), s.getLanguage(), s.getIndexName(), s.getStatus().name(),
            rules, s.getCreatedAt(), s.getUpdatedAt());
    }
}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/DocumentResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.document.model.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
    UUID documentId, UUID spaceId, String title, String fileType,
    String securityLevel, String status, int chunkCount,
    String currentVersionNo, List<String> tags,
    UUID uploadedBy, Instant createdAt, Instant updatedAt
) {
    public static DocumentResponse from(Document d) {
        String versionNo = d.getCurrentVersion() != null
            ? "v" + d.getCurrentVersion().versionNo() : null;
        return new DocumentResponse(d.getDocumentId(), d.getSpaceId(), d.getTitle(),
            d.getFileType().name(), d.getSecurityLevel().name(), d.getStatus().name(),
            d.getChunkCount(), versionNo, d.getTags(),
            d.getUploadedBy(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/DocumentDetailResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.document.model.Document;
import com.rag.domain.document.model.DocumentVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
    UUID documentId, UUID spaceId, String title, String fileType,
    String securityLevel, String status, int chunkCount,
    List<String> tags, UUID uploadedBy,
    List<VersionResponse> versions,
    Instant createdAt, Instant updatedAt
) {
    public static DocumentDetailResponse from(Document d) {
        var versions = d.getVersions().stream().map(VersionResponse::from).toList();
        return new DocumentDetailResponse(d.getDocumentId(), d.getSpaceId(), d.getTitle(),
            d.getFileType().name(), d.getSecurityLevel().name(), d.getStatus().name(),
            d.getChunkCount(), d.getTags(), d.getUploadedBy(),
            versions, d.getCreatedAt(), d.getUpdatedAt());
    }
}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/VersionResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.document.model.DocumentVersion;
import java.time.Instant;
import java.util.UUID;

public record VersionResponse(
    UUID versionId, int versionNo, String filePath,
    long fileSize, String checksum, Instant createdAt, UUID createdBy
) {
    public static VersionResponse from(DocumentVersion v) {
        return new VersionResponse(v.versionId(), v.versionNo(), v.filePath(),
            v.fileSize(), v.checksum(), v.createdAt(), v.createdBy());
    }
}
```

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/response/UserResponse.java`:
```java
package com.rag.adapter.inbound.dto.response;

import com.rag.domain.identity.model.User;
import java.util.UUID;

public record UserResponse(
    UUID userId, String username, String displayName, String email,
    String bu, String team, String role
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getUserId(), u.getUsername(), u.getDisplayName(),
            u.getEmail(), u.getBu(), u.getTeam(), u.getRole().name());
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-inbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/dto/
git commit -m "feat(api): add request/response DTOs for Space, Document, and User APIs"
```

---

### Task 10: REST Controllers

**Files:**
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/SpaceController.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/DocumentController.java`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/UserController.java`

- [ ] **Step 1: Create SpaceController**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/SpaceController.java`:
```java
package com.rag.adapter.inbound.rest;

import com.rag.adapter.inbound.dto.request.CreateSpaceRequest;
import com.rag.adapter.inbound.dto.request.UpdateAccessRulesRequest;
import com.rag.adapter.inbound.dto.response.SpaceResponse;
import com.rag.application.identity.SpaceApplicationService;
import com.rag.domain.identity.model.AccessRule;
import com.rag.domain.identity.model.TargetType;
import com.rag.domain.shared.model.SecurityLevel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceController {

    private final SpaceApplicationService spaceService;

    public SpaceController(SpaceApplicationService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse createSpace(@Valid @RequestBody CreateSpaceRequest req) {
        var space = spaceService.createSpace(
            req.name(), req.description(), req.ownerTeam(), req.language(), req.indexName());
        return SpaceResponse.from(space);
    }

    @GetMapping
    public List<SpaceResponse> listSpaces(@RequestHeader("X-User-Id") UUID userId) {
        return spaceService.listAccessibleSpaces(userId).stream()
            .map(SpaceResponse::from).toList();
    }

    @GetMapping("/{spaceId}")
    public SpaceResponse getSpace(@PathVariable UUID spaceId) {
        return SpaceResponse.from(spaceService.getSpace(spaceId));
    }

    @PutMapping("/{spaceId}/access-rules")
    public SpaceResponse updateAccessRules(@PathVariable UUID spaceId,
                                            @Valid @RequestBody UpdateAccessRulesRequest req) {
        List<AccessRule> rules = req.rules().stream().map(r ->
            new AccessRule(null, spaceId,
                TargetType.valueOf(r.targetType()), r.targetValue(),
                r.docSecurityClearance() != null
                    ? SecurityLevel.valueOf(r.docSecurityClearance())
                    : SecurityLevel.ALL)
        ).toList();
        return SpaceResponse.from(spaceService.updateAccessRules(spaceId, rules));
    }
}
```

- [ ] **Step 2: Create DocumentController**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/DocumentController.java`:
```java
package com.rag.adapter.inbound.rest;

import com.rag.adapter.inbound.dto.request.BatchDeleteRequest;
import com.rag.adapter.inbound.dto.request.BatchUpdateTagsRequest;
import com.rag.adapter.inbound.dto.response.DocumentDetailResponse;
import com.rag.adapter.inbound.dto.response.DocumentResponse;
import com.rag.adapter.inbound.dto.response.VersionResponse;
import com.rag.application.document.DocumentApplicationService;
import com.rag.domain.shared.model.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/documents")
public class DocumentController {

    private final DocumentApplicationService documentService;

    public DocumentController(DocumentApplicationService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@PathVariable UUID spaceId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestHeader("X-User-Id") UUID userId) throws IOException {
        var doc = documentService.uploadDocument(
            spaceId, file.getOriginalFilename(), file.getSize(),
            file.getBytes(), userId);
        return DocumentResponse.from(doc);
    }

    @PostMapping("/batch-upload")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DocumentResponse> batchUpload(@PathVariable UUID spaceId,
                                               @RequestParam("files") MultipartFile[] files,
                                               @RequestHeader("X-User-Id") UUID userId) throws IOException {
        List<DocumentResponse> results = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            var doc = documentService.uploadDocument(
                spaceId, file.getOriginalFilename(), file.getSize(),
                file.getBytes(), userId);
            results.add(DocumentResponse.from(doc));
        }
        return results;
    }

    @GetMapping
    public PageResult<DocumentResponse> list(@PathVariable UUID spaceId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String search) {
        var result = documentService.listDocuments(spaceId, page, size, search);
        return new PageResult<>(
            result.items().stream().map(DocumentResponse::from).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @GetMapping("/{docId}")
    public DocumentDetailResponse getDocument(@PathVariable UUID spaceId,
                                               @PathVariable UUID docId) {
        return DocumentDetailResponse.from(documentService.getDocument(docId));
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID spaceId, @PathVariable UUID docId) {
        documentService.deleteDocument(docId);
    }

    @PostMapping("/{docId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadNewVersion(@PathVariable UUID spaceId,
                                              @PathVariable UUID docId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestHeader("X-User-Id") UUID userId) throws IOException {
        var doc = documentService.uploadNewVersion(
            docId, file.getOriginalFilename(), file.getSize(), file.getBytes(), userId);
        return DocumentResponse.from(doc);
    }

    @GetMapping("/{docId}/versions")
    public List<VersionResponse> listVersions(@PathVariable UUID spaceId,
                                               @PathVariable UUID docId) {
        return documentService.listVersions(docId).stream()
            .map(VersionResponse::from).toList();
    }

    @PostMapping("/{docId}/retry")
    public DocumentResponse retryParse(@PathVariable UUID spaceId, @PathVariable UUID docId) {
        return DocumentResponse.from(documentService.retryParse(docId));
    }

    @PutMapping("/batch-tags")
    public void batchUpdateTags(@PathVariable UUID spaceId,
                                 @Valid @RequestBody BatchUpdateTagsRequest req) {
        documentService.batchUpdateTags(req.documentIds(), req.tagsToAdd(), req.tagsToRemove());
    }

    @DeleteMapping("/batch-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void batchDelete(@PathVariable UUID spaceId,
                             @Valid @RequestBody BatchDeleteRequest req) {
        documentService.batchDelete(req.documentIds());
    }
}
```

- [ ] **Step 3: Create UserController**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/UserController.java`:
```java
package com.rag.adapter.inbound.rest;

import com.rag.adapter.inbound.dto.response.UserResponse;
import com.rag.domain.identity.port.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@RequestHeader("X-User-Id") UUID userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return UserResponse.from(user);
    }
}
```

- [ ] **Step 4: Verify and commit**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-adapter-inbound -q && echo "OK"`
Expected: OK

```bash
git add rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/
git commit -m "feat(api): add SpaceController, DocumentController, UserController with full REST APIs"
```

---

### Task 11: Global Exception Handler

**Files:**
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/GlobalExceptionHandler.java`

- [ ] **Step 1: Create exception handler**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/GlobalExceptionHandler.java`:
```java
package com.rag.adapter.inbound.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", "NOT_FOUND",
            "message", e.getMessage(),
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleBadState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "error", "CONFLICT",
            "message", e.getMessage(),
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", "VALIDATION_ERROR",
            "messages", errors,
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
            "error", "FILE_TOO_LARGE",
            "message", "Maximum upload size exceeded (100MB limit)",
            "timestamp", Instant.now().toString()
        ));
    }
}
```

- [ ] **Step 2: Add file upload size config to application.yml**

Append to `rag-boot/src/main/resources/application.yml` under the `spring:` section:

```yaml
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 200MB
```

- [ ] **Step 3: Commit**

```bash
git add rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/GlobalExceptionHandler.java \
        rag-boot/src/main/resources/application.yml
git commit -m "feat(api): add global exception handler and file upload size limits"
```

---

### Task 12: Full Build & API Smoke Test

- [ ] **Step 1: Full Maven build**

Run: `cd E:/AIProject/agentic-rag-claude && mvn clean install -DskipTests`
Expected: BUILD SUCCESS for all modules

- [ ] **Step 2: Start Docker infrastructure (if not running)**

Run: `cd E:/AIProject/agentic-rag-claude/docker && docker compose up -d postgresql redis`

- [ ] **Step 3: Start application**

Run: `cd E:/AIProject/agentic-rag-claude && mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local`
Expected: Application starts on port 8080

- [ ] **Step 4: Seed a test user**

Run:
```bash
docker exec rag-postgresql psql -U rag_user -d rag_db -c "
INSERT INTO t_user (user_id, username, display_name, email, bu, team, role, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'testuser', 'Test User', 'test@example.com', 'OPS', 'COB', 'ADMIN', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;"
```

- [ ] **Step 5: Test Space API**

Create a space:
```bash
curl -s -X POST http://localhost:8080/api/v1/spaces \
  -H "Content-Type: application/json" \
  -d '{"name":"Compliance Q&A","description":"AML/KYC policies","ownerTeam":"COB","language":"zh","indexName":"kb_compliance_v1"}' | python -m json.tool
```
Expected: 201 response with space details

List spaces:
```bash
curl -s http://localhost:8080/api/v1/spaces \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" | python -m json.tool
```
Expected: Empty list (no access rules yet)

Add access rules:
```bash
SPACE_ID=$(curl -s http://localhost:8080/api/v1/spaces | python -c "import sys,json; print(json.load(sys.stdin))" 2>/dev/null || echo "get-space-id-from-create-response")
# Use the spaceId from the create response
curl -s -X PUT "http://localhost:8080/api/v1/spaces/${SPACE_ID}/access-rules" \
  -H "Content-Type: application/json" \
  -d '{"rules":[{"targetType":"BU","targetValue":"OPS","docSecurityClearance":"ALL"},{"targetType":"BU","targetValue":"FICC","docSecurityClearance":"ALL"}]}' | python -m json.tool
```
Expected: Space with access rules

- [ ] **Step 6: Test Document API**

Upload a test file:
```bash
echo "Test document content" > /tmp/test.pdf
curl -s -X POST "http://localhost:8080/api/v1/spaces/${SPACE_ID}/documents/upload" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -F "file=@/tmp/test.pdf" | python -m json.tool
```
Expected: 201 response with document details, status = UPLOADED

List documents:
```bash
curl -s "http://localhost:8080/api/v1/spaces/${SPACE_ID}/documents" | python -m json.tool
```
Expected: Document list with 1 item

- [ ] **Step 7: Stop application and commit**

```bash
git add -A
git commit -m "chore: verify Plan 2 complete — Identity & Document Management APIs working"
```

---

## Plan 2 Summary

After completing all 12 tasks, you will have:
- **Identity Context**: User, KnowledgeSpace, AccessRule domain models + SpaceAuthorizationService
- **Document Context**: Document, DocumentVersion with state machine + DocumentLifecycleService
- **Persistence**: 7 JPA entities, 7 JPA repositories, 3 mappers, 3 repository adapters
- **File Storage**: LocalFileStorageAdapter (SPI, @Profile("local"))
- **Application Services**: SpaceApplicationService + DocumentApplicationService
- **REST APIs**: SpaceController (5 endpoints), DocumentController (9 endpoints), UserController (1 endpoint)
- **Infrastructure**: Global exception handler, file upload config
- **Events**: DocumentUploadedEvent published on upload (consumed by Plan 3)
- Ready for Plan 3 (Document Processing Pipeline)
