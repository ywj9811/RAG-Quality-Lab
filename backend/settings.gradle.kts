plugins {
    // Gradle toolchain이 arm64 JDK 21을 자동으로 내려받도록 한다 (ADR-0001, 승인 #9)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
