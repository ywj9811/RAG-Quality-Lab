package com.ragqualitylab.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.TestcontainersConfiguration;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * 실제 HTTP로 SPA의 CSRF 흐름을 검증한다 (MockMvc의 테스트용 CSRF 저장소를 거치지 않는다).
 * 브라우저처럼: GET /csrf → XSRF-TOKEN 쿠키 → 로그인 POST에 X-XSRF-TOKEN 헤더.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "RQL_API_BIND=127.0.0.1",
    "ADMIN_INITIAL_USERNAME=admin",
    "ADMIN_INITIAL_PASSWORD=correct-horse-battery",
})
@Import(TestcontainersConfiguration.class)
class AdminCsrfHttpFlowTests {

    @LocalServerPort
    int port;

    @Test
    void browserLikeLoginFlow() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> csrf = client.send(get("/api/admin/auth/csrf"), HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(204);
        String setCookie = csrf.headers().firstValue("set-cookie").orElseThrow();
        assertThat(setCookie).startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly");
        String token = cookies.getCookieStore().getCookies().stream()
            .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElseThrow();

        HttpResponse<String> noHeader = client.send(login(null), HttpResponse.BodyHandlers.ofString());
        assertThat(noHeader.statusCode()).isEqualTo(403);
        assertThat(noHeader.body()).contains("CSRF_INVALID");

        HttpResponse<String> ok = client.send(login(token), HttpResponse.BodyHandlers.ofString());
        assertThat(ok.statusCode()).isEqualTo(200);
        String sessionCookie = ok.headers().allValues("set-cookie").stream()
            .filter(c -> c.startsWith("rql_admin=")).findFirst().orElseThrow();
        assertThat(sessionCookie).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Strict");

        HttpResponse<String> me = client.send(get("/api/admin/auth/me"), HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"admin\"");
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    }

    private HttpRequest login(String xsrfToken) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"correct-horse-battery\"}"));
        if (xsrfToken != null) {
            builder.header("X-XSRF-TOKEN", xsrfToken);
        }
        return builder.build();
    }
}
