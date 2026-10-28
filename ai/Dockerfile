FROM ghcr.io/astral-sh/uv:0.13.6 AS uv

# 백엔드 이미지(eclipse-temurin:21)와 같은 Ubuntu 26.04라야 같은 ffmpeg 패키지를 쓸 수 있다.
FROM ubuntu:26.04 AS runtime
# 녹음 디코딩(ffmpeg·ffprobe). 백엔드 업로드 검사와 같은 버전으로 고정한다(PRA-02).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg=7:8.0.1-3ubuntu2 \
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
RUN uv sync --locked --no-dev --no-install-project
COPY src/ src/
RUN uv sync --locked --no-dev --no-editable
ENV PATH=/app/.venv/bin:$PATH \
    NUMBA_CACHE_DIR=/tmp/numba-cache
# librosa(numba)는 첫 호출 때 컴파일 결과를 저장한다. 실행 사용자는 /app에 쓸 수 없어 /tmp 아래로 둔다.
USER 10001:10001
# 인증이 없으므로 백엔드만 닿는 내부망에서만 연다. 호스트 포트로 공개하지 않는다.
EXPOSE 8000
CMD ["uvicorn", "ai_service.api:app", "--host", "0.0.0.0", "--port", "8000"]
