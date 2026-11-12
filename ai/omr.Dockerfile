# syntax=docker/dockerfile:1.7
# ADD --checksum은 Dockerfile 문법 1.6 이상(BuildKit)이 필요하다. 위 줄이 빌더에 그 문법을 쓰게 한다.
# 악보 인식 서비스(omr). 채점(ai)과 다른 컨테이너로 띄운다(docs/specs/score-recognition.md '실행 위치').
# 빌드 맥락은 저장소 루트다(data/src/scores·data/reference를 함께 쓴다). 넣을 파일은 omr.Dockerfile.dockerignore가 정한다.
#   docker build -f ai/omr.Dockerfile -t eolssu-omr .
ARG OMR_SCANNER_BASE=runtime
FROM ghcr.io/astral-sh/uv:0.13.6@sha256:af22a52f5eafef3d78e551599d405c02df61919d18028f0cea8eda93bc9986d0 AS uv

# Audiveris 5.11은 Ubuntu 24.04용 deb만 낸다.
FROM ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852 AS runtime
ARG AUDIVERIS_VERSION=5.11.0
# jeongganbo-generator(MIT) tools/import-ngc-omr.mjs. 저장소 문서(jeongganbo-omr.md)의 9d4a24e
ARG JGB_GENERATOR_COMMIT=9d4a24e54bf36093d4d264d0271d214500d36db7
# Audiveris uses Tesseract's legacy engine; tessdata_fast contains LSTM weights only.
ADD --checksum=sha256:daa0c97d651c19fba3b25e81317cd697e9908c8208090c94c3905381c23fc047 \
    https://raw.githubusercontent.com/tesseract-ocr/tessdata/4.1.0/eng.traineddata /opt/tessdata/eng.traineddata
# 받은 파일이 바뀌면 빌드가 멈추게 체크섬을 고정한다(2026-11-04 내려받아 잰 값)
ADD --checksum=sha256:f20113aaa33b3149ec8d6a09b2a7963360e65fafd92d69389987a85bbc3ec7a3 \
    https://github.com/Audiveris/audiveris/releases/download/${AUDIVERIS_VERSION}/Audiveris-${AUDIVERIS_VERSION}-ubuntu24.04-x86_64.deb /tmp/audiveris.deb
# Audiveris deb의 데스크톱 메뉴 등록(postinst)은 화면 없는 컨테이너에서 실패해 빼고 다시 묶는다. 화면 없이 돌아도 GTK가 필요하다.
RUN dpkg-deb -R /tmp/audiveris.deb /tmp/a && rm -f /tmp/a/DEBIAN/postinst /tmp/a/DEBIAN/prerm \
    && dpkg-deb -b /tmp/a /tmp/aud.deb \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libgtk-3-0t64=3.24.41-4ubuntu1.3 nodejs=18.19.1+dfsg-6ubuntu5 \
        ca-certificates=20260816ubuntu0.24.04.2 git /tmp/aud.deb \
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
    TESSDATA_PREFIX=/opt/tessdata \
    HOME=/tmp/home
# Audiveris는 홈 폴더에 설정을 쓴다. 실행 사용자는 /app에 쓸 수 없어 /tmp 아래로 둔다.
USER 10001:10001
# 인증이 없으므로 backend만 닿는 내부망에서만 연다. 호스트 포트로 공개하지 않는다.
EXPOSE 8000
CMD ["uvicorn", "omr_service.api:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]

# 독립 스캔 확장. 기본은 위 runtime, 로컬 확장 검사는 검증한 런타임 이미지를 사용한다.
FROM ${OMR_SCANNER_BASE} AS scanner
USER 0
ADD --checksum=sha256:eb4fe63d313f08afd66eebabc714edd35555993e4874051c44d36272972d35c7 \
    https://codeload.github.com/MALerLab/jeongganbo-omr/tar.gz/e95045fce5ab151fc4b87d5be86c4ac5f7621f4c /tmp/jgb-source.tar.gz
ADD --checksum=sha256:f0e0d44a7ad54b7389b02f871e825d3ae439c5dda1a8f189916c727795a251bb \
    https://github.com/MALerLab/jeongganbo-omr/releases/download/v1.0.0/checkpoints.tar.gz /tmp/jgb-checkpoints.tar.gz
COPY ai/omr-scan-requirements.txt /tmp/omr-scan-requirements.txt
RUN mkdir -p /opt/jeongganbo-omr \
    && tar -xzf /tmp/jgb-source.tar.gz --strip-components=1 -C /opt/jeongganbo-omr \
    && tar -xzf /tmp/jgb-checkpoints.tar.gz -C /opt/jeongganbo-omr \
    && printf 'from .inferencer import INFERENCER_DEFAULT_KWARGS\nfrom .jeonggan_utils.jeongganbo_reader import JeongganboReader\n' > /opt/jeongganbo-omr/jngbomr/__init__.py \
    && printf 'from .jeonggan_processor import JeongganProcessor\nfrom .jeonggan_synthesizer import JeongganSynthesizer, get_img_paths\n' > /opt/jeongganbo-omr/jngbomr/jeonggan_utils/__init__.py \
    && uv pip install --python /app/ai/.venv/bin/python --require-hashes -r /tmp/omr-scan-requirements.txt \
    && chmod -R a+rX /opt/jeongganbo-omr \
    && rm /tmp/jgb-source.tar.gz /tmp/jgb-checkpoints.tar.gz /tmp/omr-scan-requirements.txt
# 로컬 기본 이미지 재사용 시에도 이번 소스로 실행한다.
COPY ai/src/ /app/ai/src/
COPY data/src/scores/ /app/data/src/scores/
COPY data/reference/ /app/data/reference/
ENV JEONGGANBO_OMR_DIR=/opt/jeongganbo-omr \
    OMP_NUM_THREADS=2 \
    OPENBLAS_NUM_THREADS=2
USER 10001:10001
