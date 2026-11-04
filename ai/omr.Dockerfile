# 악보 인식 서비스(omr). 채점(ai)과 다른 컨테이너로 띄운다(docs/specs/score-recognition.md '실행 위치').
# 빌드 맥락은 저장소 루트다(data/src/scores·data/reference를 함께 쓴다). 넣을 파일은 omr.Dockerfile.dockerignore가 정한다.
#   docker build -f ai/omr.Dockerfile -t eolssu-omr .
FROM ghcr.io/astral-sh/uv:0.13.6@sha256:af22a52f5eafef3d78e551599d405c02df61919d18028f0cea8eda93bc9986d0 AS uv

# Audiveris 5.11은 Ubuntu 24.04용 deb만 낸다.
FROM ubuntu:24.04 AS runtime
ARG AUDIVERIS_VERSION=5.11.0
# jeongganbo-generator(MIT) tools/import-ngc-omr.mjs. 저장소 문서(jeongganbo-omr.md)와 같은 커밋
ARG JGB_GENERATOR_COMMIT=9d4a24e
ADD https://github.com/Audiveris/audiveris/releases/download/${AUDIVERIS_VERSION}/Audiveris-${AUDIVERIS_VERSION}-ubuntu24.04-x86_64.deb /tmp/audiveris.deb
# Audiveris deb의 데스크톱 메뉴 등록(postinst)은 화면 없는 컨테이너에서 실패해 빼고 다시 묶는다. 화면 없이 돌아도 GTK가 필요하다.
RUN dpkg-deb -R /tmp/audiveris.deb /tmp/a && rm -f /tmp/a/DEBIAN/postinst /tmp/a/DEBIAN/prerm \
    && dpkg-deb -b /tmp/a /tmp/aud.deb \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libgtk-3-0 nodejs git ca-certificates /tmp/aud.deb \
    && git clone --quiet https://github.com/danbinaerinHan/jeongganbo-generator.git /opt/jeongganbo-generator \
    && git -C /opt/jeongganbo-generator checkout --quiet ${JGB_GENERATOR_COMMIT} \
    && rm -rf /opt/jeongganbo-generator/.git \
    && apt-get purge -y git && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* /tmp/a /tmp/*.deb
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_PYTHON_INSTALL_DIR=/opt/python \
    UV_PYTHON_PREFERENCE=only-managed \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1
WORKDIR /app/ai
COPY ai/.python-version ai/pyproject.toml ai/uv.lock ./
# 서비스 의존성과 omr 그룹(PDF·이미지). 프로젝트는 설치하지 않고 PYTHONPATH로 읽어 data/ 경로를 저장소와 같게 둔다.
RUN uv sync --locked --no-default-groups --group omr --no-install-project
COPY ai/src/ src/
COPY data/src/scores/ /app/data/src/scores/
COPY data/reference/ /app/data/reference/
ENV PATH=/app/ai/.venv/bin:$PATH \
    PYTHONPATH=/app/ai/src \
    JGB_GENERATOR_DIR=/opt/jeongganbo-generator \
    AUDIVERIS_CMD=/opt/audiveris/bin/Audiveris \
    AUDIVERIS_VERSION=${AUDIVERIS_VERSION} \
    HOME=/tmp/home
# Audiveris는 홈 폴더에 설정을 쓴다. 실행 사용자는 /app에 쓸 수 없어 /tmp 아래로 둔다.
USER 10001:10001
# 인증이 없으므로 backend만 닿는 내부망에서만 연다. 호스트 포트로 공개하지 않는다.
EXPOSE 8000
CMD ["uvicorn", "omr_service.api:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
