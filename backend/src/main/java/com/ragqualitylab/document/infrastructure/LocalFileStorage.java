package com.ragqualitylab.document.infrastructure;

import com.ragqualitylab.document.application.DocumentProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 업로드 원본을 로컬 디렉터리(Docker 볼륨)에 저장한다. 저장 이름은 UUID이며 원본 파일명을 경로에 쓰지 않는다. */
@Component
public class LocalFileStorage {

    private final Path baseDir;

    public LocalFileStorage(DocumentProperties properties) {
        this.baseDir = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    public String store(byte[] content) {
        try {
            Files.createDirectories(baseDir);
            String name = UUID.randomUUID() + ".bin";
            Path temp = Files.createTempFile(baseDir, "upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, baseDir.resolve(name), StandardCopyOption.ATOMIC_MOVE);
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패", e);
        }
    }

    public byte[] read(String storedName) throws IOException {
        return Files.readAllBytes(resolve(storedName));
    }

    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(resolve(storedName));
    }

    private Path resolve(String storedName) {
        Path path = baseDir.resolve(storedName).normalize();
        if (!path.getParent().equals(baseDir)) {
            throw new IllegalArgumentException("Invalid stored path");
        }
        return path;
    }
}
