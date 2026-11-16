FROM eclipse-temurin:21-jre
# Match backend/Dockerfile's recording probe dependency. Mount the verified QA JAR read-only.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg=7:8.0.1-3ubuntu2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
