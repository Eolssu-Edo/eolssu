FROM eclipse-temurin:21-jre
# 녹음 파일 검사(ffprobe). backend/Dockerfile 실행 단계와 같은 버전(PRA-02)
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg=7:8.0.1-3ubuntu2 \
    && rm -rf /var/lib/apt/lists/*
