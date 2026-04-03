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
        private int timeoutSeconds = 30;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class EmbeddingProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        private int dimension = 1024;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
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
