package com.rag.domain.document.port;

import java.io.InputStream;

public interface FileStoragePort {
    String store(String path, InputStream content);
    InputStream retrieve(String path);
    void delete(String path);
}
