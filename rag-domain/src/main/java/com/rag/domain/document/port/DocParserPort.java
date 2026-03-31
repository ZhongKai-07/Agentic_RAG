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
