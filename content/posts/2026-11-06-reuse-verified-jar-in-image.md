---
title: "테스트한 JAR을 이미지에 그대로 넣기"
date: 2026-11-06T00:00:00+09:00
slug: "reuse-verified-jar-in-image"
author: "우동균"
categories: ["infra"]
tags: ["Jenkins", "Gradle", "Docker", "JAR"]
description: "CI에서 만든 JAR을 이미지 빌드에 재사용하고, 이미지에서 꺼낸 바이트를 SHA-256으로 대조한 과정입니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-reuse-verified-jar-in-image.webp"
draft: false
---

얼쑤의 백엔드 CI는 Jenkins에서 Gradle로 검사하고 실행 JAR을 만든 뒤, Docker로 배포 후보 이미지를 빌드했습니다. 오래 걸린 빌드의 로그를 보니 Dockerfile이 소스와 Gradle Wrapper를 받아 `bootJar`를 한 번 더 실행하고 있었습니다. Docker 레이어 캐시가 적중하지 않은 한 실행에서 이미지 안의 재빌드만 1분 51초가 걸렸습니다.

## 실행할 JAR 먼저 확정 {#실행할-jar을-먼저-확정했다}

배포 후보 이미지의 역할을 확인해 보니, 배포 도구는 후보를 띄워 확인한 뒤 그 안에서 JAR을 꺼내 운영 실행 이미지에 연결하고 있었습니다. 후보 안에서 JAR을 다시 만들어야 할 이유가 없어, Jenkins가 이미 검증한 실행 JAR을 이미지에 넣도록 바꿨습니다.

빌드 입력에는 실행 JAR 하나만 넣었습니다. 이미지 생성 뒤에는 정지 컨테이너에서 JAR을 꺼내 원본과 SHA-256을 비교했습니다. 이렇게 하면 이미지 빌드가 성공한 경우에도 다른 JAR을 복사했다면 배포 전에 발견할 수 있습니다.

## 산출물 일치와 시간 비교 {#산출물-일치와-소요-시간을-따로-봤다}

격리된 환경에서 실제 Docker로 77,555,250바이트의 JAR을 담고 꺼내 보니 해시가 같았습니다. 생성과 검증에는 14.508초가 걸렸습니다. 운영 이력의 다른 두 실행에서는 이미지 단계가 각각 약 94초와 12초였습니다. 서로 다른 실행에서 나온 관측값이라 전체 CI 단축률을 계산할 때 합치지 않았습니다. 변경 뒤 확인한 핵심은 이미지에 들어간 파일이 CI에서 검증한 JAR과 바이트 단위로 일치한다는 점입니다.
