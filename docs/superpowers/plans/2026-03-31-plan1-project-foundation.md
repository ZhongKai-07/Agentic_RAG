# Plan 1: Project Foundation & Infrastructure

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up the Maven multi-module project skeleton with Docker infrastructure, database migrations, SPI pluggable mechanism, and a verified application startup.

**Architecture:** DDD hexagonal architecture with 6 Maven modules. Domain core has zero framework dependencies. Outbound adapters implement SPI ports. Infrastructure layer manages configuration and SPI auto-wiring via Spring Profiles. CQRS and event-driven patterns are structurally prepared but not yet wired.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring AI 1.0.0, Maven, PostgreSQL 16, Redis 7, OpenSearch 2.17, Flyway, Docker Compose

**This is Plan 1 of 5.** Subsequent plans build on this foundation:
- Plan 2: Identity & Document Management
- Plan 3: Document Processing Pipeline
- Plan 4: Conversation & Agent Engine
- Plan 5: React Frontend

---

## File Structure

```
agentic-rag-claude/
├── pom.xml                                          # Parent POM
├── rag-domain/
│   ├── pom.xml
│   └── src/main/java/com/rag/domain/
│       ├── shared/
│       │   ├── model/SecurityLevel.java
│       │   ├── model/PageResult.java
│       │   └── event/DomainEvent.java
│       ├── identity/
│       │   └── port/UserRepository.java
│       ├── document/
│       │   └── port/
│       │       ├── DocumentRepository.java
│       │       ├── FileStoragePort.java
│       │       └── DocParserPort.java
│       ├── knowledge/
│       │   └── port/
│       │       ├── VectorStorePort.java
│       │       ├── EmbeddingPort.java
│       │       └── RerankPort.java
│       └── conversation/
│           └── port/
│               ├── LlmPort.java
│               └── SessionRepository.java
├── rag-application/
│   ├── pom.xml
│   └── src/main/java/com/rag/application/
│       └── package-info.java
├── rag-adapter-inbound/
│   ├── pom.xml
│   └── src/main/java/com/rag/adapter/inbound/
│       └── rest/HealthController.java
├── rag-adapter-outbound/
│   ├── pom.xml
│   └── src/main/java/com/rag/adapter/outbound/
│       └── persistence/
│           └── package-info.java
├── rag-infrastructure/
│   ├── pom.xml
│   └── src/main/java/com/rag/infrastructure/
│       ├── config/
│       │   ├── ServiceRegistryConfig.java
│       │   ├── RedisConfig.java
│       │   └── OpenSearchConfig.java
│       └── spi/SpiAutoConfiguration.java
├── rag-boot/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/rag/RagApplication.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       └── db/migration/
│       │           └── V1__initial_schema.sql
│       └── test/
│           └── java/com/rag/RagApplicationTest.java
└── docker/
    └── docker-compose.yml
```

---

### Task 1: Maven Parent POM

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Create parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.5</version>
        <relativePath/>
    </parent>

    <groupId>com.rag</groupId>
    <artifactId>rag-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Agentic RAG Knowledge Base</name>

    <modules>
        <module>rag-domain</module>
        <module>rag-application</module>
        <module>rag-adapter-inbound</module>
        <module>rag-adapter-outbound</module>
        <module>rag-infrastructure</module>
        <module>rag-boot</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.0</spring-ai.version>
        <opensearch-java.version>2.17.0</opensearch-java.version>
        <flyway.version>10.15.0</flyway.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Internal modules -->
            <dependency>
                <groupId>com.rag</groupId>
                <artifactId>rag-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.rag</groupId>
                <artifactId>rag-application</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.rag</groupId>
                <artifactId>rag-adapter-inbound</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.rag</groupId>
                <artifactId>rag-adapter-outbound</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.rag</groupId>
                <artifactId>rag-infrastructure</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>
</project>
```

- [ ] **Step 2: Verify POM is valid**

Run: `cd E:/AIProject/agentic-rag-claude && mvn help:effective-pom -N > /dev/null 2>&1 && echo "OK" || echo "FAIL"`
Expected: OK (or Maven not installed yet — if so, skip verification and continue)

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add Maven parent POM with module structure and dependency management"
```

---

### Task 2: rag-domain Module (Domain Core — Zero Framework Dependencies)

**Files:**
- Create: `rag-domain/pom.xml`
- Create: `rag-domain/src/main/java/com/rag/domain/shared/model/SecurityLevel.java`
- Create: `rag-domain/src/main/java/com/rag/domain/shared/model/PageResult.java`
- Create: `rag-domain/src/main/java/com/rag/domain/shared/event/DomainEvent.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/port/LlmPort.java`
- Create: `rag-domain/src/main/java/com/rag/domain/conversation/port/SessionRepository.java`
- Create: `rag-domain/src/main/java/com/rag/domain/knowledge/port/VectorStorePort.java`
- Create: `rag-domain/src/main/java/com/rag/domain/knowledge/port/EmbeddingPort.java`
- Create: `rag-domain/src/main/java/com/rag/domain/knowledge/port/RerankPort.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/port/DocumentRepository.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/port/FileStoragePort.java`
- Create: `rag-domain/src/main/java/com/rag/domain/document/port/DocParserPort.java`
- Create: `rag-domain/src/main/java/com/rag/domain/identity/port/UserRepository.java`

- [ ] **Step 1: Create rag-domain POM (only Reactor Core, no Spring/JPA)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.rag</groupId>
        <artifactId>rag-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>rag-domain</artifactId>
    <name>RAG Domain Core</name>
    <description>Domain models, services, ports — zero framework dependency</description>

    <dependencies>
        <!-- Only Reactor Core for Flux/Mono streaming -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create shared domain models**

`rag-domain/src/main/java/com/rag/domain/shared/model/SecurityLevel.java`:
```java
package com.rag.domain.shared.model;

public enum SecurityLevel {
    ALL,
    MANAGEMENT
}
```

`rag-domain/src/main/java/com/rag/domain/shared/model/PageResult.java`:
```java
package com.rag.domain.shared.model;

import java.util.List;

public record PageResult<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
```

`rag-domain/src/main/java/com/rag/domain/shared/event/DomainEvent.java`:
```java
package com.rag.domain.shared.event;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {
    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredAt = Instant.now();

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
}
```

- [ ] **Step 3: Create LLM port (core SPI interface for streaming)**

`rag-domain/src/main/java/com/rag/domain/conversation/port/LlmPort.java`:
```java
package com.rag.domain.conversation.port;

import reactor.core.publisher.Flux;
import java.util.List;

public interface LlmPort {

    Flux<String> streamChat(LlmRequest request);

    String chat(LlmRequest request);

    record LlmRequest(
        String systemPrompt,
        List<ChatMessage> history,
        String userMessage,
        double temperature
    ) {}

    record ChatMessage(String role, String content) {}
}
```

`rag-domain/src/main/java/com/rag/domain/conversation/port/SessionRepository.java`:
```java
package com.rag.domain.conversation.port;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    // Will be populated in Plan 2
}
```

- [ ] **Step 4: Create embedding, rerank, and vector store ports**

`rag-domain/src/main/java/com/rag/domain/knowledge/port/EmbeddingPort.java`:
```java
package com.rag.domain.knowledge.port;

import java.util.List;

public interface EmbeddingPort {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}
```

`rag-domain/src/main/java/com/rag/domain/knowledge/port/RerankPort.java`:
```java
package com.rag.domain.knowledge.port;

import java.util.List;

public interface RerankPort {

    List<RerankResult> rerank(String query, List<String> documents, int topN);

    record RerankResult(int index, double score) {}
}
```

`rag-domain/src/main/java/com/rag/domain/knowledge/port/VectorStorePort.java`:
```java
package com.rag.domain.knowledge.port;

import java.util.List;
import java.util.Map;

public interface VectorStorePort {

    void upsertChunks(String indexName, List<ChunkDocument> chunks);

    void deleteByDocumentId(String indexName, String documentId);

    List<SearchHit> hybridSearch(String indexName, HybridSearchRequest request);

    record ChunkDocument(
        String chunkId,
        String documentId,
        String content,
        float[] embedding,
        Map<String, Object> metadata
    ) {}

    record HybridSearchRequest(
        String query,
        float[] queryVector,
        Map<String, Object> filters,
        int topK
    ) {}

    record SearchHit(
        String chunkId,
        String documentId,
        String content,
        double score,
        Map<String, Object> metadata,
        Map<String, List<String>> highlights
    ) {}
}
```

- [ ] **Step 5: Create document ports**

`rag-domain/src/main/java/com/rag/domain/document/port/DocumentRepository.java`:
```java
package com.rag.domain.document.port;

public interface DocumentRepository {
    // Will be populated in Plan 2
}
```

`rag-domain/src/main/java/com/rag/domain/document/port/FileStoragePort.java`:
```java
package com.rag.domain.document.port;

import java.io.InputStream;

public interface FileStoragePort {
    String store(String path, InputStream content);
    InputStream retrieve(String path);
    void delete(String path);
}
```

`rag-domain/src/main/java/com/rag/domain/document/port/DocParserPort.java`:
```java
package com.rag.domain.document.port;

import java.io.InputStream;
import java.util.List;

public interface DocParserPort {

    ParseResult parse(String fileName, InputStream content);

    record ParseResult(
        List<ParsedChunk> chunks,
        int totalPages
    ) {}

    record ParsedChunk(
        String content,
        int pageNumber,
        String sectionPath,
        int tokenCount
    ) {}
}
```

`rag-domain/src/main/java/com/rag/domain/identity/port/UserRepository.java`:
```java
package com.rag.domain.identity.port;

public interface UserRepository {
    // Will be populated in Plan 2
}
```

- [ ] **Step 6: Verify rag-domain compiles**

Run: `cd E:/AIProject/agentic-rag-claude && mvn compile -pl rag-domain -q && echo "OK" || echo "FAIL"`
Expected: OK

- [ ] **Step 7: Commit**

```bash
git add rag-domain/
git commit -m "feat(domain): add domain core module with shared models, events, and SPI port interfaces"
```

---

### Task 3: rag-application Module

**Files:**
- Create: `rag-application/pom.xml`
- Create: `rag-application/src/main/java/com/rag/application/package-info.java`

- [ ] **Step 1: Create rag-application POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.rag</groupId>
        <artifactId>rag-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>rag-application</artifactId>
    <name>RAG Application Services</name>

    <dependencies>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create package-info placeholder**

`rag-application/src/main/java/com/rag/application/package-info.java`:
```java
/**
 * Application services layer — orchestrates domain services.
 * Contains command handlers, query handlers, and event handlers.
 * No business logic here; delegates to domain layer.
 */
package com.rag.application;
```

- [ ] **Step 3: Commit**

```bash
git add rag-application/
git commit -m "feat(application): add application services module skeleton"
```

---

### Task 4: rag-adapter-inbound Module

**Files:**
- Create: `rag-adapter-inbound/pom.xml`
- Create: `rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/HealthController.java`

- [ ] **Step 1: Create POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.rag</groupId>
        <artifactId>rag-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>rag-adapter-inbound</artifactId>
    <name>RAG Inbound Adapters</name>

    <dependencies>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-application</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create health check controller**

`rag-adapter-inbound/src/main/java/com/rag/adapter/inbound/rest/HealthController.java`:
```java
package com.rag.adapter.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "service", "agentic-rag-knowledge-base"
        );
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add rag-adapter-inbound/
git commit -m "feat(adapter-inbound): add inbound adapter module with health check endpoint"
```

---

### Task 5: rag-adapter-outbound Module

**Files:**
- Create: `rag-adapter-outbound/pom.xml`
- Create: `rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/package-info.java`

- [ ] **Step 1: Create POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.rag</groupId>
        <artifactId>rag-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>rag-adapter-outbound</artifactId>
    <name>RAG Outbound Adapters</name>

    <dependencies>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.opensearch.client</groupId>
            <artifactId>opensearch-java</artifactId>
            <version>${opensearch-java.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create package-info placeholder**

`rag-adapter-outbound/src/main/java/com/rag/adapter/outbound/persistence/package-info.java`:
```java
/**
 * JPA persistence adapters implementing domain repository ports.
 * Contains JPA entities (separate from domain models) and Spring Data repositories.
 */
package com.rag.adapter.outbound.persistence;
```

- [ ] **Step 3: Commit**

```bash
git add rag-adapter-outbound/
git commit -m "feat(adapter-outbound): add outbound adapter module with JPA, Redis, OpenSearch, Spring AI deps"
```

---

### Task 6: rag-infrastructure Module

**Files:**
- Create: `rag-infrastructure/pom.xml`
- Create: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/ServiceRegistryConfig.java`
- Create: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/RedisConfig.java`
- Create: `rag-infrastructure/src/main/java/com/rag/infrastructure/config/OpenSearchConfig.java`
- Create: `rag-infrastructure/src/main/java/com/rag/infrastructure/spi/SpiAutoConfiguration.java`

- [ ] **Step 1: Create POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.rag</groupId>
        <artifactId>rag-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>rag-infrastructure</artifactId>
    <name>RAG Infrastructure</name>

    <dependencies>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.opensearch.client</groupId>
            <artifactId>opensearch-java</artifactId>
            <version>${opensearch-java.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create ServiceRegistryConfig (unified config entry)**

`rag-infrastructure/src/main/java/com/rag/infrastructure/config/ServiceRegistryConfig.java`:
```java
package com.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unified service/API configuration entry point.
 * All external service connection details are managed here.
 * SPI adapter implementations inject these properties.
 */
@Configuration
public class ServiceRegistryConfig {

    @Bean
    @ConfigurationProperties("rag.services.llm")
    public LlmProperties llmProperties() {
        return new LlmProperties();
    }

    @Bean
    @ConfigurationProperties("rag.services.embedding")
    public EmbeddingProperties embeddingProperties() {
        return new EmbeddingProperties();
    }

    @Bean
    @ConfigurationProperties("rag.services.rerank")
    public RerankProperties rerankProperties() {
        return new RerankProperties();
    }

    @Bean
    @ConfigurationProperties("rag.services.vector-store")
    public VectorStoreProperties vectorStoreProperties() {
        return new VectorStoreProperties();
    }

    @Bean
    @ConfigurationProperties("rag.services.doc-parser")
    public DocParserProperties docParserProperties() {
        return new DocParserProperties();
    }

    @Bean
    @ConfigurationProperties("rag.services.file-storage")
    public FileStorageProperties fileStorageProperties() {
        return new FileStorageProperties();
    }

    public static class LlmProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class EmbeddingProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class RerankProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class VectorStoreProperties {
        private String url;
        private String username;
        private String password;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class DocParserProperties {
        private String url;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class FileStorageProperties {
        private String basePath = "./uploads";
        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
    }
}
```

- [ ] **Step 3: Create RedisConfig**

`rag-infrastructure/src/main/java/com/rag/infrastructure/config/RedisConfig.java`:
```java
package com.rag.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

- [ ] **Step 4: Create OpenSearchConfig**

`rag-infrastructure/src/main/java/com/rag/infrastructure/config/OpenSearchConfig.java`:
```java
package com.rag.infrastructure.config;

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(ServiceRegistryConfig.VectorStoreProperties props) {
        HttpHost host = HttpHost.create(props.getUrl());
        var transport = ApacheHttpClient5TransportBuilder
            .builder(host)
            .build();
        return new OpenSearchClient(transport);
    }
}
```

- [ ] **Step 5: Create SpiAutoConfiguration**

`rag-infrastructure/src/main/java/com/rag/infrastructure/spi/SpiAutoConfiguration.java`:
```java
package com.rag.infrastructure.spi;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that scans outbound adapters for SPI implementations.
 * The correct implementation is selected via @Profile annotations on each adapter.
 *
 * Switch environment: --spring.profiles.active=local (or aws)
 *
 * SPI Port            | local Profile          | aws Profile
 * --------------------|------------------------|---------------------------
 * LlmPort            | AliCloudLlmAdapter     | GatewayLlmAdapter
 * EmbeddingPort      | AliCloudEmbeddingAdapter| GatewayEmbeddingAdapter
 * RerankPort         | AliCloudRerankAdapter  | GatewayRerankAdapter
 * VectorStorePort    | LocalOpenSearchAdapter | AwsOpenSearchAdapter
 * DocParserPort      | DoclingJavaAdapter     | AwsBedrockDocAdapter
 * FileStoragePort    | LocalFileStorageAdapter| S3FileStorageAdapter
 */
@Configuration
@ComponentScan(basePackages = "com.rag.adapter.outbound")
public class SpiAutoConfiguration {
}
```

- [ ] **Step 6: Commit**

```bash
git add rag-infrastructure/
git commit -m "feat(infrastructure): add config module with ServiceRegistryConfig, Redis, OpenSearch, SPI auto-configuration"
```

---

### Task 7: rag-boot Module + Application Config

**Files:**
- Create: `rag-boot/pom.xml`
- Create: `rag-boot/src/main/java/com/rag/RagApplication.java`
- Create: `rag-boot/src/main/resources/application.yml`
- Create: `rag-boot/src/main/resources/application-local.yml`

- [ ] **Step 1: Create rag-boot POM (aggregates all modules)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.rag</groupId>
        <artifactId>rag-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>rag-boot</artifactId>
    <name>RAG Boot</name>

    <dependencies>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-adapter-inbound</artifactId>
        </dependency>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-adapter-outbound</artifactId>
        </dependency>
        <dependency>
            <groupId>com.rag</groupId>
            <artifactId>rag-infrastructure</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create RagApplication.java**

`rag-boot/src/main/java/com/rag/RagApplication.java`:
```java
package com.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application.yml (common config)**

`rag-boot/src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: agentic-rag-knowledge-base
  profiles:
    active: local

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
```

- [ ] **Step 4: Create application-local.yml**

`rag-boot/src/main/resources/application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: rag_user
    password: rag_password
    driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: localhost
      port: 6379

rag:
  services:
    llm:
      api-key: ${ALICLOUD_LLM_API_KEY:sk-placeholder}
      model: qwen-plus
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    embedding:
      api-key: ${ALICLOUD_EMB_API_KEY:sk-placeholder}
      model: text-embedding-v3
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    rerank:
      api-key: ${ALICLOUD_RERANK_API_KEY:sk-placeholder}
      model: gte-rerank
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    vector-store:
      url: http://localhost:9200
    doc-parser:
      url: http://localhost:5001
    file-storage:
      base-path: ./uploads
```

- [ ] **Step 5: Commit**

```bash
git add rag-boot/
git commit -m "feat(boot): add boot module with Spring Boot app, application configs, and Flyway setup"
```

---

### Task 8: Docker Compose

**Files:**
- Create: `docker/docker-compose.yml`

- [ ] **Step 1: Create docker-compose.yml**

`docker/docker-compose.yml`:
```yaml
version: "3.9"
services:
  opensearch:
    image: opensearchproject/opensearch:2.17.0
    container_name: rag-opensearch
    environment:
      - discovery.type=single-node
      - DISABLE_SECURITY_PLUGIN=true
      - "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9600:9600"
    volumes:
      - opensearch-data:/usr/share/opensearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:2.17.0
    container_name: rag-opensearch-dashboards
    environment:
      - 'OPENSEARCH_HOSTS=["http://opensearch:9200"]'
      - DISABLE_SECURITY_DASHBOARDS_PLUGIN=true
    ports:
      - "5601:5601"
    depends_on:
      opensearch:
        condition: service_healthy

  docling:
    image: ds4sd/docling-serve:latest
    container_name: rag-docling
    ports:
      - "5001:5001"
    deploy:
      resources:
        limits:
          memory: 4G

  postgresql:
    image: postgres:16
    container_name: rag-postgresql
    environment:
      POSTGRES_DB: rag_db
      POSTGRES_USER: rag_user
      POSTGRES_PASSWORD: rag_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U rag_user -d rag_db"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: rag-redis
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  opensearch-data:
  pgdata:
```

- [ ] **Step 2: Verify Docker Compose is valid**

Run: `cd E:/AIProject/agentic-rag-claude/docker && docker compose config > /dev/null 2>&1 && echo "OK" || echo "FAIL"`
Expected: OK

- [ ] **Step 3: Commit**

```bash
git add docker/
git commit -m "infra: add Docker Compose for PostgreSQL, Redis, OpenSearch, and docling-serve"
```

---

### Task 9: Flyway Database Migration

**Files:**
- Create: `rag-boot/src/main/resources/db/migration/V1__initial_schema.sql`

- [ ] **Step 1: Create V1 migration with full schema**

`rag-boot/src/main/resources/db/migration/V1__initial_schema.sql`:
```sql
-- ============================================================
-- Identity Context
-- ============================================================

CREATE TABLE t_user (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  NOT NULL UNIQUE,
    display_name  VARCHAR(128) NOT NULL,
    email         VARCHAR(256),
    bu            VARCHAR(32)  NOT NULL,
    team          VARCHAR(64)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_bu_team ON t_user(bu, team);

CREATE TABLE t_knowledge_space (
    space_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(128)  NOT NULL,
    description       TEXT,
    owner_team        VARCHAR(64)   NOT NULL,
    language          VARCHAR(8)    NOT NULL DEFAULT 'zh',
    index_name        VARCHAR(128)  NOT NULL UNIQUE,
    retrieval_config  JSONB         NOT NULL DEFAULT '{}',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE t_access_rule (
    rule_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id               UUID        NOT NULL REFERENCES t_knowledge_space(space_id),
    target_type            VARCHAR(16) NOT NULL,
    target_value           VARCHAR(64) NOT NULL,
    doc_security_clearance VARCHAR(16) NOT NULL DEFAULT 'ALL',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_rule_space  ON t_access_rule(space_id);
CREATE INDEX idx_access_rule_target ON t_access_rule(target_type, target_value);

CREATE TABLE t_space_permission (
    permission_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES t_user(user_id),
    space_id       UUID        NOT NULL REFERENCES t_knowledge_space(space_id),
    access_level   VARCHAR(16) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, space_id)
);

-- ============================================================
-- Document Context
-- ============================================================

CREATE TABLE t_document (
    document_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id           UUID         NOT NULL REFERENCES t_knowledge_space(space_id),
    title              VARCHAR(512) NOT NULL,
    file_type          VARCHAR(16)  NOT NULL,
    security_level     VARCHAR(16)  NOT NULL DEFAULT 'ALL',
    status             VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED',
    current_version_id UUID,
    chunk_count        INTEGER      NOT NULL DEFAULT 0,
    uploaded_by        UUID         NOT NULL REFERENCES t_user(user_id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_space        ON t_document(space_id);
CREATE INDEX idx_document_space_status ON t_document(space_id, status);

CREATE TABLE t_document_version (
    version_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID          NOT NULL REFERENCES t_document(document_id) ON DELETE CASCADE,
    version_no    INTEGER       NOT NULL,
    file_path     VARCHAR(1024) NOT NULL,
    file_size     BIGINT        NOT NULL,
    checksum      VARCHAR(64)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID          NOT NULL REFERENCES t_user(user_id),
    UNIQUE(document_id, version_no)
);

CREATE TABLE t_document_tag (
    tag_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES t_document(document_id) ON DELETE CASCADE,
    tag_name     VARCHAR(64) NOT NULL,
    UNIQUE(document_id, tag_name)
);
CREATE INDEX idx_document_tag_name ON t_document_tag(tag_name);

CREATE TABLE t_document_process_log (
    log_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID         NOT NULL REFERENCES t_document(document_id),
    version_id   UUID         NOT NULL REFERENCES t_document_version(version_id),
    action       VARCHAR(32)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    message      TEXT,
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

-- ============================================================
-- Conversation Context
-- ============================================================

CREATE TABLE t_chat_session (
    session_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES t_user(user_id),
    space_id       UUID         NOT NULL REFERENCES t_knowledge_space(space_id),
    title          VARCHAR(256),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_user_space ON t_chat_session(user_id, space_id);
CREATE INDEX idx_session_active     ON t_chat_session(user_id, status, last_active_at DESC);

CREATE TABLE t_message (
    message_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID        NOT NULL REFERENCES t_chat_session(session_id) ON DELETE CASCADE,
    role          VARCHAR(16) NOT NULL,
    content       TEXT        NOT NULL,
    agent_trace   JSONB,
    token_count   INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_session ON t_message(session_id, created_at);

CREATE TABLE t_citation (
    citation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id     UUID         NOT NULL REFERENCES t_message(message_id) ON DELETE CASCADE,
    citation_index INTEGER      NOT NULL,
    document_id    UUID         NOT NULL REFERENCES t_document(document_id),
    chunk_id       VARCHAR(128) NOT NULL,
    document_title VARCHAR(512) NOT NULL,
    page_number    INTEGER,
    section_path   VARCHAR(512),
    snippet        TEXT         NOT NULL
);
CREATE INDEX idx_citation_message ON t_citation(message_id);
```

- [ ] **Step 2: Commit**

```bash
git add rag-boot/src/main/resources/db/
git commit -m "feat(db): add Flyway V1 migration with full PostgreSQL schema"
```

---

### Task 10: Add .gitignore and Start Infrastructure

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Create .gitignore**

`.gitignore`:
```
# Maven
target/
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS
.DS_Store
Thumbs.db

# Env
.env
*.env.local

# Uploads
uploads/

# Logs
*.log
logs/

# Node (frontend)
rag-frontend/node_modules/
rag-frontend/dist/
```

- [ ] **Step 2: Start Docker infrastructure**

Run: `cd E:/AIProject/agentic-rag-claude/docker && docker compose up -d postgresql redis`
Expected: Both containers start. Verify with:
Run: `docker compose ps`
Expected: `rag-postgresql` and `rag-redis` show status `Up` / `healthy`

Note: OpenSearch and docling are not needed for Plan 1 verification. Start them only when needed (Plan 3+) to save resources.

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: add .gitignore"
```

---

### Task 11: Full Build & Startup Verification

- [ ] **Step 1: Run Maven full build**

Run: `cd E:/AIProject/agentic-rag-claude && mvn clean install -DskipTests`
Expected: `BUILD SUCCESS` for all 7 modules

If it fails, check:
- Java 21 is installed: `java -version`
- Maven is installed: `mvn -version`
- All module POMs reference correct parent version `0.1.0-SNAPSHOT`

- [ ] **Step 2: Start the application**

Run: `cd E:/AIProject/agentic-rag-claude && mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local`
Expected: Application starts on port 8080. Console shows:
- `Flyway` migration V1 applied successfully
- `Started RagApplication in X seconds`

If Flyway fails, check PostgreSQL is running: `docker compose -f docker/docker-compose.yml ps`

- [ ] **Step 3: Test health endpoint**

Run: `curl -s http://localhost:8080/api/v1/health | python -m json.tool`
Expected:
```json
{
    "status": "UP",
    "timestamp": "2026-03-31T...",
    "service": "agentic-rag-knowledge-base"
}
```

- [ ] **Step 4: Verify database tables were created**

Run: `docker exec rag-postgresql psql -U rag_user -d rag_db -c "\dt t_*"`
Expected: All tables listed:
```
t_access_rule
t_chat_session
t_citation
t_document
t_document_process_log
t_document_tag
t_document_version
t_knowledge_space
t_message
t_space_permission
t_user
```

- [ ] **Step 5: Stop the application (Ctrl+C) and commit**

```bash
git add -A
git commit -m "chore: verify full build and startup — Plan 1 complete"
```

---

## Plan 1 Summary

After completing all 11 tasks, you will have:
- 6 Maven modules with correct dependency graph (`rag-domain` depends on nothing)
- All SPI port interfaces defined in domain layer
- Unified `ServiceRegistryConfig` as single config entry point
- SPI auto-configuration via Spring Profile
- Docker Compose for PostgreSQL + Redis (+ OpenSearch + docling ready)
- Full database schema via Flyway migration
- Application starts and serves health endpoint
- Ready for Plan 2 (Identity & Document Management)
