# CI packages the JAR already verified by the preceding Gradle stages.
FROM eclipse-temurin:21-jre
# Keep recording inspection compatible with backend/Dockerfile.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg=7:8.0.1-3ubuntu2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --chown=10001:10001 app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
