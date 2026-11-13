package com.ragqualitylab;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 통합 테스트용 PostgreSQL + pgvector. Compose와 같은 이미지 태그를 쓴다 (ADR-0001). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String PGVECTOR_IMAGE = "pgvector/pgvector:0.8.7-pg17-bookworm";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(PGVECTOR_IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
