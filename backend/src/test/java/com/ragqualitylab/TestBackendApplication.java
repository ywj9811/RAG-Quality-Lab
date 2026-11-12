package com.ragqualitylab;

import org.springframework.boot.SpringApplication;

/** 로컬 실행 보조: DB를 Testcontainers로 띄워 앱을 실행한다 (./gradlew bootTestRun). */
public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
