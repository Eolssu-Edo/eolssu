---
title: "FFmpeg를 유지하며 Python 3.12로 맞추기"
date: 2026-10-28T00:00:00+09:00
slug: "pin-managed-python-in-shared-base-image"
author: "우동균"
categories: ["infra"]
tags: ["Docker", "uv", "Python", "FFmpeg"]
description: "분석 이미지에서 Python과 FFmpeg의 버전을 맞추고, 실행 파일뿐 아니라 설치된 디코딩 라이브러리까지 확인했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-pin-managed-python-in-shared-base-image.webp"
draft: false
aliases:
  - /posts/2026/10/pin-ffmpeg-decoder-libraries/
---

녹음 업로드는 백엔드에서 검사하고, 채점은 Python 분석 서비스에서 실행합니다. 두 서비스에서 같은 FFmpeg 패키지를 쓰기 위해 분석 이미지의 기반을 백엔드와 같은 Ubuntu **26.04**로 맞췄습니다. 당시 기반 이미지의 시스템 Python은 **3.14**였고, 분석 프로젝트는 **3.12**를 사용했습니다. Python 버전을 맞추려고 기반 이미지를 바꾸면 함께 맞춰 둔 FFmpeg 패키지부터 다시 확인해야 했습니다.

## 시스템 Python과 프로젝트 Python 분리 {#시스템-python과-프로젝트-python을-분리했다}

이미지에 uv를 넣고 `UV_PYTHON_PREFERENCE=only-managed`로 설정했습니다. 프로젝트의 `.python-version`에 있는 버전을 읽어 uv가 관리하는 Python을 설치하게 했습니다. 런타임 계정도 읽을 수 있는 공용 위치에 설치하고, 앱이 시작될 때는 그 Python으로 만든 가상 환경을 사용하도록 했습니다. uv를 가져오는 이미지도 다이제스트로 지정해 빌드가 참조하는 도구의 버전을 고정했습니다. [uv의 관리형 Python 설정](https://docs.astral.sh/uv/concepts/python-versions/#adjusting-python-version-preferences)

## 의존성과 앱을 별도 층에 설치 {#소스와-의존성이-바뀌는-단계를-나눴다}

빌드에서는 `.python-version`, `pyproject.toml`, `uv.lock`을 먼저 복사했습니다. 첫 `uv sync`에 `--no-install-project`를 주면 이 층에는 의존성만 들어갑니다. 다음 층에서 소스를 복사하고 `--no-editable`로 프로젝트를 설치했습니다. 두 단계에서 `--locked`를 써 잠금 파일과 프로젝트 설정이 맞지 않으면 빌드가 멈추도록 했습니다.

소스 파일만 바뀌면 의존성을 설치한 층의 입력은 같습니다. 빌드 캐시가 남아 있다면 그 층을 다시 쓸 수 있습니다. 소스를 일반 패키지로 설치하므로 실행 환경에서 편집 설치의 소스 경로에도 기대지 않습니다. 이 사례에서 캐시가 줄인 빌드 시간은 따로 재지 않았습니다. [uv의 Docker 빌드 층 예시](https://docs.astral.sh/uv/guides/integration/docker/#intermediate-layers)

## FFmpeg 이름표 뒤의 디코딩 라이브러리

FFmpeg 자체의 패키지 버전을 고정하고 `apt-cache show`로 의존성을 읽었습니다. 디코딩 라이브러리 `libavcodec62`의 조건은 `>= 7:8.0.1`이었습니다. 이 하한을 만족하는 버전이 여러 개면, 같은 FFmpeg 패키지를 지정해도 나중에 이미지를 다시 만들 때 더 새 라이브러리가 들어올 수 있습니다. [Debian 패키지 의존 관계 문서](https://www.debian.org/doc/debian-policy/ch-relationships.html#syntax-of-relationship-fields)

FFmpeg와 디코딩 관련 라이브러리 **7개**에 같은 패키지 버전 인자를 사용했습니다. 기반 이미지와 빌드 도구 이미지의 참조에는 다이제스트를 사용했습니다. 다이제스트는 이미지 내용의 지문이므로 같은 태그가 다른 이미지를 가리키는 일을 막을 수 있습니다. 기반 이미지의 내용과 패키지 관리자가 나중에 가져오는 의존성은 서로 다른 입력이어서 둘을 각각 고정했습니다.

## 이미지 버전과 분석 API 확인 {#완성된-이미지-안에서-버전을-읽었다}

Apple Silicon 개발 노트북에서 Linux arm64 이미지를 빌드하고, 이미지 안에서 **Python 3.12.16**과 **FFmpeg 8.0.1**을 확인했습니다. 컨테이너 안에서 `dpkg-query`로 읽은 디코딩 라이브러리의 설치 버전은 FFmpeg 패키지와 같은 **`7:8.0.1-3ubuntu2`**였습니다. 실행 파일이 출력하는 버전과 이미지 안에 설치된 패키지 목록을 따로 대조한 결과입니다.

같은 로컬 이미지에서 기본 계정으로 합성 녹음 한 건을 넣었을 때 HTTP 200과 `DONE`을 확인했습니다. 이 실행으로 기본 계정에서 디코딩과 분석까지 이어지는 경로를 확인했습니다. 라이브러리 버전별 결과 차이, 운영 amd64 이미지의 설치 목록, 캐시가 줄인 빌드 시간은 이 비교의 측정 범위 밖입니다. 첫 분석 요청이 기본 계정에서 500을 냈던 경위는 별도 글에 정리했습니다. [컨테이너 기동 후 첫 분석 요청이 500인 이유](https://team-edo.github.io/posts/2026/10/numba-cache-nonroot-first-request/)
