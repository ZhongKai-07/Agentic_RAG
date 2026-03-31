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
