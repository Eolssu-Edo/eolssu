---
title: "FFmpeg 때문에 기반 OS를 유지하면서 Python 버전만 맞춘 방법"
date: 2026-10-28T00:00:00+09:00
slug: "pin-managed-python-in-shared-base-image"
author: "우동균"
categories: ["infra"]
tags: ["Docker", "uv", "Python", "FFmpeg"]
description: "백엔드와 FFmpeg 패키지를 맞추기 위해 기반 OS를 공유하고, 분석 서비스의 Python과 의존성은 별도로 고정한 과정입니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-pin-managed-python-in-shared-base-image.webp"
draft: false
---

녹음 업로드는 백엔드에서 검사하고, 채점은 Python 분석 서비스에서 실행합니다. 두 서비스에서 같은 FFmpeg 패키지를 쓰기 위해 분석 이미지의 기반을 백엔드와 같은 Ubuntu **26.04**로 맞췄습니다. 당시 기반 이미지의 시스템 Python은 **3.14**였고, 분석 프로젝트는 **3.12**를 사용했습니다. Python 버전을 맞추려고 기반 이미지를 바꾸면 함께 맞춰 둔 FFmpeg 패키지부터 다시 확인해야 했습니다.

## 시스템 Python과 프로젝트 Python을 분리했다

이미지에 uv를 넣고 `UV_PYTHON_PREFERENCE=only-managed`로 설정했습니다. 프로젝트의 `.python-version`에 있는 버전을 읽어 uv가 관리하는 Python을 설치하게 했습니다. 런타임 계정도 읽을 수 있는 공용 위치에 설치하고, 앱이 시작될 때는 그 Python으로 만든 가상 환경을 사용하도록 했습니다. uv를 가져오는 이미지도 다이제스트로 지정해 빌드가 참조하는 도구의 버전을 고정했습니다. [uv의 관리형 Python 설정](https://docs.astral.sh/uv/concepts/python-versions/#adjusting-python-version-preferences)

## 소스와 의존성이 바뀌는 단계를 나눴다

빌드에서는 `.python-version`, `pyproject.toml`, `uv.lock`을 먼저 복사했습니다. 첫 `uv sync`에 `--no-install-project`를 주면 이 층에는 의존성만 들어갑니다. 다음 층에서 소스를 복사하고 `--no-editable`로 프로젝트를 설치했습니다. 두 단계에서 `--locked`를 써 잠금 파일과 프로젝트 설정이 맞지 않으면 빌드가 멈추도록 했습니다.

소스 파일만 바뀌면 의존성을 설치한 층의 입력은 같습니다. 빌드 캐시가 남아 있다면 그 층을 다시 쓸 수 있습니다. 소스를 일반 패키지로 설치하므로 실행 환경에서 편집 설치의 소스 경로에도 기대지 않습니다. 이 사례에서 캐시가 줄인 빌드 시간은 따로 재지 않았습니다. [uv의 Docker 빌드 층 예시](https://docs.astral.sh/uv/guides/integration/docker/#intermediate-layers)

## 완성된 이미지 안에서 버전을 읽었다

Apple Silicon 개발 노트북에서 Linux arm64 이미지를 빌드했습니다. 이미지 안에서 **Python 3.12.16**과 **FFmpeg 8.0.1**을 확인했습니다. 기본 실행 계정으로 분석 API를 띄워 저장소 대역의 합성 녹음 한 건을 요청했을 때는 HTTP 200과 `DONE`을 받았습니다. 이 검증은 개발 노트북에서 빌드한 이미지가 정해 둔 Python을 사용해 분석 요청을 처리하는 범위였습니다. 운영 amd64 이미지의 빌드와 실행은 별도의 확인 범위입니다.
