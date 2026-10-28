FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre AS runtime
# 녹음 파일 검사(ffprobe). 로컬·CI·운영의 판정을 맞추려고 패키지 버전을 고정한다(PRA-02).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg=7:8.0.1-3ubuntu2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
