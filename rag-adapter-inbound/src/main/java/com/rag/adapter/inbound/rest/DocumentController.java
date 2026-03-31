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
