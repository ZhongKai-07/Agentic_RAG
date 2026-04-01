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
            .uri("/v1alpha/convert/file")
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

            // docling-serve 0.5.1 returns md_content (Markdown string) by default
            String mdContent = document.has("md_content") && !document.get("md_content").isNull()
                ? document.get("md_content").asText() : null;

            if (mdContent != null && !mdContent.isBlank()) {
                return new ParseResult(splitByMarkdownHeaders(mdContent), 1);
            }

            // Fallback: try text_content
            String textContent = document.has("text_content") && !document.get("text_content").isNull()
                ? document.get("text_content").asText() : null;
            if (textContent != null && !textContent.isBlank()) {
                return new ParseResult(splitBySize(textContent, 1500), 1);
            }

            throw new RuntimeException("Docling response contains no content for file");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse docling response", e);
        }
    }

    /**
     * Split markdown content by headers for semantic chunking.
     * Each top-level section (## or #) becomes a separate chunk.
     */
    private List<ParsedChunk> splitByMarkdownHeaders(String markdown) {
        List<ParsedChunk> chunks = new ArrayList<>();
        String[] lines = markdown.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        String currentSection = "";

        for (String line : lines) {
            // Detect markdown headers (# or ##)
            if (line.matches("^#{1,3}\\s+.+")) {
                // Flush previous chunk
                if (!currentChunk.isEmpty()) {
                    String text = currentChunk.toString().trim();
                    if (!text.isEmpty()) {
                        chunks.add(new ParsedChunk(text, 1, currentSection, estimateTokens(text)));
                    }
                    currentChunk = new StringBuilder();
                }
                currentSection = line.replaceAll("^#+\\s+", "").trim();
            }

            currentChunk.append(line).append("\n");

            // Split on size: if chunk exceeds ~1500 tokens, cut
            if (estimateTokens(currentChunk.toString()) > 1500) {
                String text = currentChunk.toString().trim();
                chunks.add(new ParsedChunk(text, 1, currentSection, estimateTokens(text)));
                currentChunk = new StringBuilder();
            }
        }

        // Flush remaining
        if (!currentChunk.isEmpty()) {
            String text = currentChunk.toString().trim();
            if (!text.isEmpty()) {
                chunks.add(new ParsedChunk(text, 1, currentSection, estimateTokens(text)));
            }
        }

        // If no headers found, fall back to size-based splitting
        if (chunks.isEmpty()) {
            chunks.addAll(splitBySize(markdown, 1500));
        }

        return chunks;
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
        // Conservative: 1 token ≈ 2 chars (accounts for CJK text)
        // DashScope limit is 8192 tokens; chunk at 1500 tokens = ~3000 chars
        return text.length() / 2;
    }
}
