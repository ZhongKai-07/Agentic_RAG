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
