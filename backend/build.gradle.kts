plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ragqualitylab"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        // Apple Silicon용 arm64 빌드를 받기 위해 vendor를 고정한다 (로컬 x86_64 JBR 21 사용 방지, 승인 #9)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

// ADR-0001: Spring AI 2.0.x는 Spring Boot 4.0.x/4.1.x 지원
val springAiVersion = "2.0.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    // PDF 텍스트 추출 (Spring AI BOM 관리, PDFBox 3.x)
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

// 테스트는 개발자 로컬 .env(비밀값·개인 설정)를 읽지 않는다
tasks.withType<Test>().configureEach {
    environment("RQL_ENV_FILE", layout.buildDirectory.file("no-env-file").get().asFile.absolutePath)
}

// 기본 test: 외부 서비스 없이 실행 (Docker는 Testcontainers용으로 필요)
tasks.test {
    useJUnitPlatform {
        excludeTags("ollama", "external")
    }
}

// 로컬 Ollama(127.0.0.1:11434)가 필요한 테스트
val ollamaTest by tasks.registering(Test::class) {
    description = "Runs tests that require a local Ollama server."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("ollama") }
}

// 실제 외부 API(Gemini 무료 티어)를 호출하는 테스트. 수동 실행 전용
val externalTest by tasks.registering(Test::class) {
    description = "Runs tests that call external APIs (Gemini free tier). Manual only."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("external") }
}
