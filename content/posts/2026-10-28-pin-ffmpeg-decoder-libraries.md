---
title: "FFmpeg 버전을 고정했는데 디코딩 라이브러리는 열려 있었다"
date: 2026-10-28T00:00:00+09:00
slug: "pin-ffmpeg-decoder-libraries"
author: "우동균"
description: "패키지 메타데이터를 읽어 FFmpeg가 라이브러리의 최소 버전만 요구한다는 점을 확인했습니다. 디코딩 라이브러리 7개도 함께 고정하고 이미지 안의 설치 결과를 대조했습니다."
categories: ["infra"]
tags: ["FFmpeg", "Docker", "빌드 재현성"]
thumbnail: "https://team-edo.github.io/blog-images/2026-10-pin-ffmpeg-decoder-libraries.webp"
draft: false
---

녹음 파일은 백엔드의 업로드 검사와 분석 서버의 디코딩을 거칩니다. 두 곳에서 판단이 어긋나지 않게 하려면 어떤 FFmpeg로 실행했는지부터 알 수 있어야 했습니다. 분석 이미지의 Dockerfile에는 FFmpeg 패키지 버전을 이미 적어 뒀습니다. 리뷰에서 그 패키지가 설치하는 라이브러리까지 같은 버전으로 남는지 확인했습니다.

## 고정된 패키지에도 하한만 있는 의존성이 있었다

`apt-cache show`에서 FFmpeg의 의존 관계를 확인하자 `libavcodec62`에는 **`>= 7:8.0.1`**이 적혀 있었습니다. FFmpeg 자체의 설치 버전을 고정해도, 이 조건을 만족하는 라이브러리가 여러 버전이면 다시 빌드할 때 더 새 버전이 들어올 수 있었습니다. 디코딩 동작에 영향을 주는 패키지의 경계가 Dockerfile에 보이지 않았습니다. [Debian 패키지 의존 관계 문서](https://www.debian.org/doc/debian-policy/ch-relationships.html#syntax-of-relationship-fields)

FFmpeg와 디코딩 관련 라이브러리 **7개**에 같은 패키지 버전 인자를 사용했습니다. 기반 이미지와 빌드에 쓰는 도구 이미지도 태그 대신 다이제스트로 고정했습니다. 이미지 참조와 패키지 의존성은 서로 다른 층이어서, 둘을 따로 고정해야 다시 빌드할 때 바뀔 수 있는 범위가 줄어듭니다.

## 이미지 안에 실제로 설치된 버전을 읽었다

개발 노트북에서 Linux arm64 이미지를 빌드한 뒤 컨테이너 안에서 `dpkg-query`를 실행했습니다. 지정한 라이브러리들의 설치 버전이 FFmpeg 패키지와 같은 **`7:8.0.1-3ubuntu2`**인지 확인했습니다. 실행 파일의 버전 출력만 읽어서는 놓쳤을 부분입니다.

이 작업에서 확인한 것은 빌드에 들어간 라이브러리 버전입니다. 이전·이후 라이브러리로 같은 녹음을 비교하거나 운영 amd64 이미지까지 검사한 기록은 없습니다. 다음에 버전을 올릴 때는 실행 파일 이름 하나가 아니라 함께 바뀌는 패키지 집합을 기준으로 확인할 수 있게 됐습니다.
