package com.rag.adapter.outbound.docparser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.domain.document.port.DocParserPort;
import com.rag.infrastructure.config.ServiceRegistryConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")
public class DoclingJavaAdapter implements DocParserPort {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DoclingJavaAdapter(ServiceRegistryConfig.DocParserProperties props,
                               ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
            .baseUrl(props.getUrl())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
            .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ParseResult parse(String fileName, InputStream content) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new InputStreamResource(content))
            .filename(fileName)
            .contentType(MediaType.APPLICATION_OCTET_STREAM);

        String response = webClient.post()
            .uri("/v1/convert")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return parseDoclingResponse(response);
    }

    private ParseResult parseDoclingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode document = root.has("document") ? root.get("document") : root;

            List<ParsedChunk> chunks = new ArrayList<>();
            int totalPages = 0;

            // Docling returns structured content with pages and text elements
            if (document.has("pages")) {
                totalPages = document.get("pages").size();
            }

            // Extract text content from main_text or body
            JsonNode mainText = document.has("main_text") ? document.get("main_text") :
                                document.has("body") ? document.get("body") : null;

            if (mainText != null && mainText.isArray()) {
                StringBuilder currentChunk = new StringBuilder();
                String currentSection = "";
                int currentPage = 1;
                int chunkIndex = 0;

                for (JsonNode element : mainText) {
                    String text = element.has("text") ? element.get("text").asText() : "";
                    String type = element.has("type") ? element.get("type").asText() : "paragraph";
                    int page = element.has("prov") && element.get("prov").isArray()
                        && element.get("prov").size() > 0
                        && element.get("prov").get(0).has("page")
                        ? element.get("prov").get(0).get("page").asInt() : currentPage;

                    if (text.isEmpty()) continue;

                    // Section headers start new chunks (semantic chunking)
                    if (type.contains("header") || type.contains("title")) {
                        if (!currentChunk.isEmpty()) {
                            chunks.add(new ParsedChunk(
                                currentChunk.toString().trim(), currentPage,
                                currentSection, estimateTokens(currentChunk.toString())));
                            chunkIndex++;
                            currentChunk = new StringBuilder();
                        }
                        currentSection = currentSection.isEmpty() ? text :
                            currentSection + " > " + text;
                        currentPage = page;
                    }

                    currentChunk.append(text).append("\n");

                    // Also split on size: if chunk exceeds ~1500 tokens, cut
                    if (estimateTokens(currentChunk.toString()) > 1500) {
                        chunks.add(new ParsedChunk(
                            currentChunk.toString().trim(), currentPage,
                            currentSection, estimateTokens(currentChunk.toString())));
                        chunkIndex++;
                        currentChunk = new StringBuilder();
                    }
                }

                // Flush remaining
                if (!currentChunk.isEmpty()) {
                    chunks.add(new ParsedChunk(
                        currentChunk.toString().trim(), currentPage,
                        currentSection, estimateTokens(currentChunk.toString())));
                }
            }

            // Fallback: if no structured content, use raw text with simple splitting
            if (chunks.isEmpty() && document.has("text")) {
                String fullText = document.get("text").asText();
                chunks.addAll(splitBySize(fullText, 1500));
                totalPages = 1;
            }

            return new ParseResult(chunks, totalPages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse docling response", e);
        }
    }

    private List<ParsedChunk> splitBySize(String text, int maxTokens) {
        List<ParsedChunk> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (estimateTokens(current.toString() + para) > maxTokens && !current.isEmpty()) {
                chunks.add(new ParsedChunk(current.toString().trim(), 1, "", estimateTokens(current.toString())));
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }
        if (!current.isEmpty()) {
            chunks.add(new ParsedChunk(current.toString().trim(), 1, "", estimateTokens(current.toString())));
        }
        return chunks;
    }

    private int estimateTokens(String text) {
        // Rough estimate: 1 token ≈ 4 chars for English, ~2 chars for Chinese
        return text.length() / 3;
    }
}
