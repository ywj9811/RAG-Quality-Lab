package com.ragqualitylab.llm.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class OllamaHealthIndicatorTests {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void upWhenVersionEndpointResponds() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> {
            byte[] body = "{\"version\":\"0.40.13\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var indicator = new OllamaHealthIndicator(
            "http://127.0.0.1:" + server.getAddress().getPort(), new OllamaHealthProperties(Duration.ofSeconds(1)));

        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("version", "0.40.13");
    }

    @Test
    void downWhenServerUnreachable() throws IOException {
        int freePort;
        try (var socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        var indicator = new OllamaHealthIndicator(
            "http://127.0.0.1:" + freePort, new OllamaHealthProperties(Duration.ofMillis(500)));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
