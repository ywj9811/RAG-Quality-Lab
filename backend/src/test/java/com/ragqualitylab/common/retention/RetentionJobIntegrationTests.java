package com.ragqualitylab.common.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.TestcontainersConfiguration;
import com.ragqualitylab.document.infrastructure.LocalFileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RetentionJobIntegrationTests {

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.document.upload-dir", () -> uploadDir.toString());
    }

    @Autowired RetentionJob job;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalFileStorage storage;

    @Test
    void deletesExpiredChatsAndDocumentsButKeepsSnapshotsText() throws Exception {
        jdbc.update("DELETE FROM chat_sessions");
        jdbc.update("DELETE FROM documents");
        UUID oldSession = session("now() - interval '91 days'");
        UUID newSession = session("now() - interval '10 days'");
        UUID newAnswer = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_messages (id, session_id, seq, role, content, created_at) VALUES (?, ?, 1, 'USER', 'q', now())",
            UUID.randomUUID(), oldSession);
        jdbc.update("INSERT INTO chat_messages (id, session_id, seq, role, content, answer_type, created_at) VALUES (?, ?, 2, 'ASSISTANT', 'a', 'ANSWERED', now())",
            newAnswer, newSession);

        String expiredFile = storage.store("old".getBytes());
        String recentFile = storage.store("recent".getBytes());
        UUID expiredDoc = document(expiredFile, "now() - interval '31 days'");
        UUID recentDoc = document(recentFile, "now() - interval '5 days'");
        UUID chunk = UUID.randomUUID();
        jdbc.update("INSERT INTO document_chunks (id, document_id, chunk_index, content, source_locator, embedding, embedding_model, embedding_dim, created_at) "
            + "VALUES (?, ?, 0, 'c', '{}'::jsonb, array_fill(0.1::real, ARRAY[1024])::vector, 'm', 1024, now())", chunk, expiredDoc);
        jdbc.update("INSERT INTO retrieval_records (id, answer_message_id, document_id, chunk_id, rank, score, text_snapshot, source_locator, document_filename_snapshot, document_version_snapshot) "
            + "VALUES (?, ?, ?, ?, 1, 0.9, '스냅샷 본문', '{}'::jsonb, 'old.md', 1)", UUID.randomUUID(), newAnswer, expiredDoc, chunk);

        RetentionJob.Result result = job.run();

        assertThat(result.sessions()).isEqualTo(1);
        assertThat(result.documents()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_sessions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id = ?", Integer.class, oldSession)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE id = ?", Integer.class, recentDoc)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks", Integer.class)).isZero();
        assertThat(Files.exists(uploadDir.resolve(expiredFile))).isFalse();
        assertThat(Files.exists(uploadDir.resolve(recentFile))).isTrue();
        // 과거 답변의 근거 텍스트는 남고 원본 참조만 끊긴다 (평가 재현성)
        assertThat(jdbc.queryForMap("SELECT document_id, chunk_id, text_snapshot FROM retrieval_records"))
            .containsEntry("document_id", null).containsEntry("chunk_id", null).containsEntry("text_snapshot", "스냅샷 본문");
    }

    private UUID session(String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_sessions (id, access_token_hash, status, created_at, last_activity_at, expires_at) "
            + "VALUES (?, ?, 'EXPIRED', " + createdAt + ", " + createdAt + ", now())", id, id.toString().replace("-", "") + "d".repeat(32));
        return id;
    }

    private UUID document(String storedPath, String deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, document_key, version, original_filename, stored_path, content_type, size_bytes, "
            + "content_sha256, status, uploaded_at, deleted_at) VALUES (?, ?, 1, 'x.md', ?, 'text/markdown', 3, ?, 'READY', now(), " + deletedAt + ")",
            id, UUID.randomUUID(), storedPath, "e".repeat(64));
        return id;
    }
}
