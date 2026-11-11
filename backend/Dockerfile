FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/backend

COPY backend/gradlew backend/build.gradle.kts backend/settings.gradle.kts backend/gradle.properties ./
COPY backend/gradle/ gradle/
COPY backend/src/main/ src/main/
# Canonical catalogs are shared with DATA/AI and bundled by processResources.
COPY data/reference/jangdans.json /workspace/data/reference/jangdans.json
COPY data/src/playback/profiles/ /workspace/data/src/playback/profiles/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre AS runtime
# 녹음 파일 검사(ffprobe). 로컬·CI·운영의 판정을 맞추려고 패키지 버전을 고정한다(PRA-02).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg=7:8.0.1-3ubuntu2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
