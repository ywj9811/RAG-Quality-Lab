package com.ragqualitylab.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ragqualitylab.TestcontainersConfiguration;
import com.ragqualitylab.admin.domain.AdminUser;
import com.ragqualitylab.admin.domain.AdminUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "ADMIN_INITIAL_USERNAME=admin",
    "ADMIN_INITIAL_PASSWORD=correct-horse-battery",
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminAuthIntegrationTests {

    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    AdminUserRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetLockState() {
        jdbc.update("UPDATE admin_users SET failed_attempts = 0, locked_until = NULL");
    }

    @Test
    void initialAdminIsCreatedWithBcryptHash() {
        AdminUser admin = repository.findByUsername("admin").orElseThrow();
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void loginCreatesSessionAndMeReturnsUsername() throws Exception {
        MvcResult login = login("admin", PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/admin/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"));
        assertThat(repository.findByUsername("admin").orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void loginChangesSessionIdToPreventFixation() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String originalId = before.getId();

        MvcResult result = mvc.perform(post("/api/admin/auth/login").session(before).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("admin", PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(originalId);
    }

    @Test
    void wrongPasswordAndUnknownUserReturnTheSameError() throws Exception {
        login("admin", "wrong-password-123")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.detail").value("아이디 또는 비밀번호가 올바르지 않습니다."));
        login("nobody", "wrong-password-123")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.detail").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("admin", "wrong-password-123").andExpect(status().isUnauthorized());
        }
        // 5번째 실패에서 잠금
        login("admin", "wrong-password-123").andExpect(status().isUnauthorized());

        login("admin", PASSWORD)
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void loginSucceedsAfterLockExpires() throws Exception {
        jdbc.update("UPDATE admin_users SET locked_until = ? WHERE username = 'admin'",
            java.sql.Timestamp.from(Instant.now().minusSeconds(1)));

        login("admin", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successResetsFailureCount() throws Exception {
        login("admin", "wrong-password-123");
        login("admin", "wrong-password-123");
        login("admin", PASSWORD).andExpect(status().isOk());

        assertThat(repository.findByUsername("admin").orElseThrow().getFailedAttempts()).isZero();
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("admin", PASSWORD)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void blankFieldsAreValidationErrors() throws Exception {
        login("", "").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = (MockHttpSession) login("admin", PASSWORD).andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/admin/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutLoginIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/admin/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(username, password)));
    }

    private static String json(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
