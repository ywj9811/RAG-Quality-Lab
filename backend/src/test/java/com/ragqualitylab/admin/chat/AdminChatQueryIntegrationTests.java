package com.ragqualitylab.admin.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ragqualitylab.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminChatQueryIntegrationTests {

    static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID closed;
    UUID active;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM chat_sessions");
        // 2026-11-10 10:00 KST, 2026-11-18 10:00 KST
        closed = session("CLOSED", Instant.parse("2026-11-10T01:00:00Z"));
        active = session("ACTIVE", Instant.parse("2026-11-18T01:00:00Z"));
        UUID q = message(closed, 1, "USER", "기본 전파 속성은?", null, null);
        UUID a = message(closed, 2, "ASSISTANT", "REQUIRED입니다 [1]", "ANSWERED", null);
        message(active, 1, "USER", "질문", null, null);
        message(active, 2, "ASSISTANT", "답변을 생성하지 못했습니다.", "ERROR", "UPSTREAM_TIMEOUT");
        jdbc.update("""
            INSERT INTO retrieval_records (id, answer_message_id, document_id, chunk_id, rank, score, cited,
                text_snapshot, source_locator, document_filename_snapshot, document_version_snapshot)
            VALUES (?, ?, NULL, NULL, 1, 0.82, true, '트랜잭션 > 전파 속성\nREQUIRED가 기본', '{"heading":"전파 속성","page":null,"charStart":0,"charEnd":10}'::jsonb, '04_transaction.md', 2)
            """, UUID.randomUUID(), a);
    }

    @Test
    void listsNewestFirstWithCounts() throws Exception {
        mvc.perform(get("/api/admin/chats").with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].id").value(active.toString()))
            .andExpect(jsonPath("$.items[0].errorCount").value(1))
            .andExpect(jsonPath("$.items[1].questionCount").value(1))
            .andExpect(jsonPath("$.items[1].firstQuestion").value("기본 전파 속성은?"));
    }

    @Test
    void filtersByDateAndStatusAndPages() throws Exception {
        mvc.perform(get("/api/admin/chats?from=2026-11-18&to=2026-11-18").with(ADMIN))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].id").value(active.toString()));
        mvc.perform(get("/api/admin/chats?status=CLOSED").with(ADMIN))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].status").value("CLOSED"));
        mvc.perform(get("/api/admin/chats?page=1&size=1").with(ADMIN))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/admin/chats?size=500").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/chats?status=WRONG").with(ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/chats?from=not-a-date").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void detailIncludesClosedSessionMessagesAndSnapshots() throws Exception {
        mvc.perform(get("/api/admin/chats/" + closed).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[1].answerType").value("ANSWERED"))
            .andExpect(jsonPath("$.messages[1].retrieval[0].filename").value("04_transaction.md"))
            .andExpect(jsonPath("$.messages[1].retrieval[0].documentVersion").value(2))
            .andExpect(jsonPath("$.messages[1].retrieval[0].cited").value(true))
            .andExpect(jsonPath("$.messages[1].retrieval[0].chunkAvailable").value(false))
            .andExpect(jsonPath("$.messages[1].retrieval[0].sourceLocator.heading").value("전파 속성"));
        mvc.perform(get("/api/admin/chats/" + active).with(ADMIN))
            .andExpect(jsonPath("$.messages[1].errorCode").value("UPSTREAM_TIMEOUT"));
        mvc.perform(get("/api/admin/chats/" + UUID.randomUUID()).with(ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void requiresAdmin() throws Exception {
        mvc.perform(get("/api/admin/chats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/chats/" + closed)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/chats").with(user("visitor").roles("USER"))).andExpect(status().isForbidden());
    }

    private UUID session(String status, Instant created) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO chat_sessions (id, access_token_hash, status, created_at, last_activity_at, expires_at, closed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, "a".repeat(32) + id.toString().replace("-", "").substring(0, 32), status, Timestamp.from(created),
            Timestamp.from(created), Timestamp.from(created.plusSeconds(86400)),
            status.equals("CLOSED") ? Timestamp.from(created.plusSeconds(600)) : null);
        return id;
    }

    private UUID message(UUID session, int seq, String role, String content, String answerType, String errorCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO chat_messages (id, session_id, seq, role, content, answer_type, error_code, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            """, id, session, seq, role, content, answerType, errorCode);
        return id;
    }
}
