# 빌드: Gradle Wrapper + Temurin 21 (toolchain 요구와 일치)
FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data/uploads && chown app:app /data/uploads
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
