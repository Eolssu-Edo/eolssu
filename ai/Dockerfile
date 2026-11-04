FROM ghcr.io/astral-sh/uv:0.13.6@sha256:af22a52f5eafef3d78e551599d405c02df61919d18028f0cea8eda93bc9986d0 AS uv

# 백엔드 이미지(eclipse-temurin:21)와 같은 Ubuntu 26.04라야 같은 ffmpeg 패키지를 쓸 수 있다.
FROM ubuntu:26.04@sha256:0e73b71479e7bc5f006bca872e25c3cd680dc052a0116f90d63ae363537db0dd AS runtime
# 녹음 디코딩(ffmpeg·ffprobe). 백엔드 업로드 검사와 같은 버전으로 고정한다(PRA-02).
# ffmpeg 패키지는 라이브러리를 '이 버전 이상'으로만 요구해서, 실제 디코딩을 하는 라이브러리도 같이 고정한다.
ARG FFMPEG_VERSION=7:8.0.1-3ubuntu2
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg=${FFMPEG_VERSION} libavcodec62=${FFMPEG_VERSION} libavdevice62=${FFMPEG_VERSION} \
        libavfilter11=${FFMPEG_VERSION} libavformat62=${FFMPEG_VERSION} libavutil60=${FFMPEG_VERSION} \
        libswresample6=${FFMPEG_VERSION} libswscale9=${FFMPEG_VERSION} \
    && rm -rf /var/lib/apt/lists/*
COPY --from=uv /uv /usr/local/bin/uv
# 시스템 Python은 3.14라 uv가 받는 Python 3.12(.python-version)를 쓴다.
ENV UV_PYTHON_INSTALL_DIR=/opt/python \
    UV_PYTHON_PREFERENCE=only-managed \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1
WORKDIR /app
COPY .python-version pyproject.toml uv.lock ./
RUN uv sync --locked --no-default-groups --no-install-project
COPY src/ src/
RUN uv sync --locked --no-default-groups --no-editable
ENV PATH=/app/.venv/bin:$PATH \
    NUMBA_CACHE_DIR=/tmp/numba-cache
# librosa(numba)는 첫 호출 때 컴파일 결과를 저장한다. 실행 사용자는 /app에 쓸 수 없어 /tmp 아래로 둔다.
USER 10001:10001
# 인증이 없으므로 백엔드만 닿는 내부망에서만 연다. 호스트 포트로 공개하지 않는다.
EXPOSE 8000
CMD ["uvicorn", "ai_service.api:app", "--host", "0.0.0.0", "--port", "8000"]
