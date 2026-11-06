# syntax=docker/dockerfile:1.7
# Build context: repository root. CAT archives are mounted read-only at runtime.
FROM ghcr.io/astral-sh/uv:0.13.6@sha256:af22a52f5eafef3d78e551599d405c02df61919d18028f0cea8eda93bc9986d0 AS uv
FROM ubuntu:26.04@sha256:0e73b71479e7bc5f006bca872e25c3cd680dc052a0116f90d63ae363537db0dd
ARG FFMPEG_VERSION=7:8.0.1-3ubuntu2
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates \
    ffmpeg=${FFMPEG_VERSION} libavcodec62=${FFMPEG_VERSION} libavdevice62=${FFMPEG_VERSION} \
    libavfilter11=${FFMPEG_VERSION} libavformat62=${FFMPEG_VERSION} libavutil60=${FFMPEG_VERSION} \
    libswresample6=${FFMPEG_VERSION} libswscale9=${FFMPEG_VERSION} \
    && rm -rf /var/lib/apt/lists/*
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_PYTHON_INSTALL_DIR=/opt/python UV_PYTHON_PREFERENCE=only-managed UV_NO_CACHE=1
COPY data/src/playback/requirements.txt /tmp/requirements.txt
RUN uv venv --python 3.12 /opt/venv && uv pip install --python /opt/venv/bin/python -r /tmp/requirements.txt
WORKDIR /app
COPY data/src/playback /app/data/src/playback
COPY data/src/audio/jangdan_audio.py /app/data/src/audio/jangdan_audio.py
COPY data/src/scores /app/data/src/scores
COPY data/reference /app/data/reference
ENV PATH=/opt/venv/bin:$PATH PYTHONDONTWRITEBYTECODE=1
USER 10001:10001
EXPOSE 8000
CMD ["python", "-m", "data.src.playback.service"]
