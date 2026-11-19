package com.ragqualitylab.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ragqualitylab.TestcontainersConfiguration;
import com.ragqualitylab.document.application.DocumentService;
import com.ragqualitylab.document.domain.DocumentRepository;
import com.ragqualitylab.document.domain.DocumentStatus;
import com.ragqualitylab.llm.generation.GenerationFailure;
import com.ragqualitylab.support.FakeEmbeddingPort;
import com.ragqualitylab.support.FakeGenerationModelPort;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
    // 테스트끼리 같은 인메모리 한도를 공유하므로 전역·IP 상한은 넉넉하게, 세션 상한은 기본값(5)
    "app.chat.per-ip-per-minute=1000",
    "app.chat.sessions-per-ip-per-hour=1000",
    "app.quota.generation-per-minute=1000",
    // 가짜 임베딩(단어 해시)은 실제 모델보다 점수가 낮다
    "app.rag.min-score=0.05",
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ChatIntegrationTests.Fakes.class})
class ChatIntegrationTests {

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.document.upload-dir", () -> uploadDir.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean @Primary FakeEmbeddingPort fakeEmbeddingPort() { return new FakeEmbeddingPort(); }
        @Bean @Primary FakeGenerationModelPort fakeGenerationModelPort() { return new FakeGenerationModelPort(); }
    }

    static final String DOC = """
        # 트랜잭션

        ## 전파 속성

        기본 전파 속성은 REQUIRED 이며 기존 트랜잭션에 참여한다.

        ## 롤백 규칙

        체크 예외는 커밋하고 언체크 예외는 롤백한다.
        """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeGenerationModelPort generation;
    @Autowired DocumentService documents;
    @Autowired DocumentRepository documentRepository;

    @BeforeEach
    void setUp() throws Exception {
        generation.reset();
        jdbc.update("DELETE FROM chat_sessions");
        jdbc.update("DELETE FROM llm_usage_records");
        if (documentRepository.count() == 0) {
            UUID id = documents.upload("tx.md", DOC.getBytes(StandardCharsets.UTF_8), null, "test").getId();
            long deadline = System.currentTimeMillis() + 15_000;
            while (documentRepository.findById(id).orElseThrow().getStatus() != DocumentStatus.READY) {
                assertThat(System.currentTimeMillis()).isLessThan(deadline);
                Thread.sleep(100);
            }
        }
    }

    @Test
    void createIssuesHttpOnlyStrictSessionCookie() throws Exception {
        mvc.perform(post("/api/chat/sessions").with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.idleTimeoutSeconds").value(1800))
            .andExpect(jsonPath("$.notice").value(containsString("개인정보")))
            .andExpect(header().string("Set-Cookie", containsString("rql_chat=")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/chat")));
        String hash = jdbc.queryForObject("SELECT access_token_hash FROM chat_sessions", String.class);
        assertThat(hash).hasSize(64);
    }

    @Test
    void answersWithValidCitationsAndStoresSnapshotAndUsage() throws Exception {
        Session s = newSession();
        generation.answerWith("기본값은 REQUIRED입니다 [1]. 존재하지 않는 근거 [9].");

        ask(s, "기본 전파 속성은 무엇인가")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answerType").value("ANSWERED"))
            .andExpect(jsonPath("$.answer").value("기본값은 REQUIRED입니다 [1]. 존재하지 않는 근거 ."))
            .andExpect(jsonPath("$.citations.length()").value(1))
            .andExpect(jsonPath("$.citations[0].index").value(1))
            .andExpect(jsonPath("$.citations[0].filename").value("tx.md"))
            .andExpect(jsonPath("$.citations[0].snippet").value(containsString("REQUIRED")));

        assertThat(generation.lastRequest().userPrompt()).contains("<sources>", "<question>", "기본 전파 속성은 무엇인가");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM retrieval_records", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM retrieval_records WHERE cited", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM llm_usage_records", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject(
            "SELECT prompt_version FROM chat_messages WHERE role = 'ASSISTANT'", String.class)).startsWith("gen-v1@");
        assertThat(jdbc.queryForObject("SELECT model_id FROM chat_messages WHERE role = 'ASSISTANT'", String.class))
            .isEqualTo("fake-model");
    }

    @Test
    void insufficientMarkerBecomesInsufficientEvidenceWithoutCitations() throws Exception {
        Session s = newSession();
        generation.answerWith("[근거 부족] 등록된 문서에서 답을 찾지 못했습니다. [1]");
        ask(s, "트랜잭션 격리 수준 구현 세부는?")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answerType").value("INSUFFICIENT_EVIDENCE"))
            .andExpect(jsonPath("$.answer").value(containsString("등록된 문서에서 답을 찾지 못했습니다")))
            .andExpect(jsonPath("$.citations.length()").value(0));
    }

    @Test
    void noRetrievableDocumentsAnswersWithoutCallingTheModel() throws Exception {
        Session s = newSession();
        jdbc.update("UPDATE documents SET deleted_at = now()");
        try {
            ask(s, "기본 전파 속성은 무엇인가")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerType").value("INSUFFICIENT_EVIDENCE"));
            assertThat(generation.calls()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM llm_usage_records", Integer.class)).isZero();
        } finally {
            jdbc.update("UPDATE documents SET deleted_at = NULL");
        }
    }

    @Test
    void followUpIncludesHistoryAndRetrievalQuery() throws Exception {
        Session s = newSession();
        ask(s, "기본 전파 속성은 무엇인가").andExpect(status().isOk());
        ask(s, "그럼 체크 예외는?").andExpect(status().isOk());

        assertThat(generation.lastRequest().userPrompt()).contains("<history>", "기본 전파 속성은 무엇인가");
        String query = jdbc.queryForObject(
            "SELECT retrieval_query FROM chat_messages WHERE role='ASSISTANT' ORDER BY seq DESC LIMIT 1", String.class);
        assertThat(query).isEqualTo("기본 전파 속성은 무엇인가\n그럼 체크 예외는?");

        mvc.perform(get("/api/chat/sessions/" + s.id).cookie(s.cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(4))
            .andExpect(jsonPath("$.messages[0].role").value("USER"))
            .andExpect(jsonPath("$.messages[1].citations[0].index").value(1));
    }

    @Test
    void otherPeopleCannotReadOrWriteWithSessionIdOnly() throws Exception {
        Session s = newSession();
        Session other = newSession();
        mvc.perform(get("/api/chat/sessions/" + s.id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/sessions/" + s.id).cookie(other.cookie)).andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/sessions/" + s.id + "/messages").with(csrf()).cookie(other.cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"질문\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/sessions/" + UUID.randomUUID()).cookie(s.cookie)).andExpect(status().isNotFound());
    }

    @Test
    void closedSessionRejectsReadAndWriteAndClearsCookie() throws Exception {
        Session s = newSession();
        mvc.perform(post("/api/chat/sessions/" + s.id + "/close").with(csrf()).cookie(s.cookie))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        ask(s, "질문").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
        mvc.perform(get("/api/chat/sessions/" + s.id).cookie(s.cookie))
            .andExpect(status().isGone());
        mvc.perform(post("/api/chat/sessions/" + s.id + "/close").with(csrf()).cookie(s.cookie))
            .andExpect(status().isGone());
    }

    @Test
    void idleSessionExpires() throws Exception {
        Session s = newSession();
        jdbc.update("UPDATE chat_sessions SET last_activity_at = now() - interval '31 minutes'");
        ask(s, "질문").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void newSessionClosesThePreviousOne() throws Exception {
        Session first = newSession();
        mvc.perform(post("/api/chat/sessions").with(csrf()).cookie(first.cookie)).andExpect(status().isCreated());
        ask(first, "질문").andExpect(status().isGone());
    }

    @Test
    void validatesQuestion() throws Exception {
        Session s = newSession();
        ask(s, "   ").andExpect(status().isBadRequest());
        ask(s, "가".repeat(1001)).andExpect(status().isBadRequest());
        ask(s, "제 메일은 hong@example.com 입니다").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PERSONAL_INFO_DETECTED"));
        ask(s, "010-1234-5678로 연락 주세요").andExpect(jsonPath("$.code").value("PERSONAL_INFO_DETECTED"));
        assertThat(generation.calls()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isZero();
    }

    @Test
    void dailyQuotaFailsClosedAndSessionStaysUsable() throws Exception {
        jdbc.update("""
            INSERT INTO llm_usage_records (id, operation_type, provider, model_id, status, created_at)
            SELECT gen_random_uuid(), 'GENERATE', 'fake', 'fake-model', 'SUCCESS', now() FROM generate_series(1, 300)
            """);
        Session s = newSession();
        ask(s, "기본 전파 속성은 무엇인가")
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DAILY_QUOTA_EXCEEDED"));
        assertThat(generation.calls()).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM llm_usage_records WHERE status = 'QUOTA_BLOCKED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT error_code FROM chat_messages WHERE role = 'ASSISTANT'", String.class)).isEqualTo("DAILY_QUOTA_EXCEEDED");
        // 답변(ERROR)이 남아 있으므로 다음 질문이 "처리 중"으로 막히지 않는다
        ask(s, "기본 전파 속성은 무엇인가").andExpect(jsonPath("$.code").value("DAILY_QUOTA_EXCEEDED"));
    }

    @Test
    void upstreamFailuresMapToErrorsAndAreRecorded() throws Exception {
        Session s = newSession();
        generation.failWith(GenerationFailure.Kind.RATE_LIMITED);
        ask(s, "기본 전파 속성은 무엇인가").andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("UPSTREAM_RATE_LIMITED"));
        generation.failWith(GenerationFailure.Kind.TIMEOUT);
        ask(s, "기본 전파 속성은 무엇인가").andExpect(status().isGatewayTimeout());
        assertThat(jdbc.queryForList("SELECT status FROM llm_usage_records ORDER BY created_at", String.class))
            .containsExactly("RATE_LIMITED", "TIMEOUT");
        generation.answerWith("이제 됩니다 [1]");
        ask(s, "기본 전파 속성은 무엇인가").andExpect(status().isOk());
    }

    @Test
    void perSessionRateLimit() throws Exception {
        Session s = newSession();
        for (int i = 0; i < 5; i++) {
            ask(s, "기본 전파 속성은 무엇인가").andExpect(status().isOk());
        }
        ask(s, "기본 전파 속성은 무엇인가")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void chatRequestsRequireCsrf() throws Exception {
        mvc.perform(post("/api/chat/sessions")).andExpect(status().isForbidden());
    }

    // --- helpers ---

    record Session(String id, Cookie cookie) {
    }

    Session newSession() throws Exception {
        MvcResult r = mvc.perform(post("/api/chat/sessions").with(csrf())).andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(r.getResponse().getContentAsString(), "$.sessionId");
        return new Session(id, r.getResponse().getCookie("rql_chat"));
    }

    ResultActions ask(Session s, String content) throws Exception {
        return mvc.perform(post("/api/chat/sessions/" + s.id + "/messages").with(csrf()).cookie(s.cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":" + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(content) + "}"));
    }
}
