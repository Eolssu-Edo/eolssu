---
title: "Play 배포판의 App Links가 빠진 이유"
date: 2026-11-03T00:00:00+09:00
slug: "android-app-links-signing"
author: "우동균"
categories: ["infra"]
tags: ["Android", "App Links", "Google Play", "Docker Compose"]
description: "운영 서버는 정상인데 Play 배포판만 App Links 문서에서 빠졌던 상황과, 공개 JSON에서 패키지 두 항목을 확인한 과정을 정리했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-android-app-links-signing.webp"
draft: false
---

얼쑤에서 Android 소셜 로그인은 외부 브라우저를 열고, 인증 뒤 App Links로 앱에 돌아오는 흐름을 사용했습니다. Play에서 설치한 일반 앱이 브라우저에서 돌아오지 못해 운영 서버의 `assetlinks.json`을 읽었습니다. 서버는 기동 상태가 정상이었고, 연결 문서에는 QA 패키지 한 항목만 있었습니다. 우선 Play 설치판에 해당하는 연결 항목을 복구했습니다.

## Play 앱 서명 지문 확인 {#설치한-앱에-찍힌-서명이-필요했다}

App Links의 연결 문서에는 패키지 이름과 서명 인증서 지문이 함께 들어갑니다. 직접 빌드해 설치한 앱과 Play가 배포한 앱의 서명이 다르면, 한 쪽을 확인한 지문으로 다른 쪽까지 연결됐다고 볼 수 없습니다. Play에서 받은 설치본에 해당하는 지문은 Play Console의 앱 서명 정보에 나옵니다.

일반 앱 지문을 전달하는 Compose 변수는 `${VAR:-}` 형태의 선택 설정이었습니다. 환경 파일에 키가 없으면 빈 값으로 기동했고, 그 결과 QA 항목만 포함된 문서가 반환됐습니다.

## 응답에서 두 패키지 확인 {#응답에서-두-패키지를-확인했다}

일반 앱에 대응하는 업로드 키와 Play 앱 서명 키의 지문 두 개를 설정하고 백엔드를 재생성했습니다. 운영 서버에서 JSON을 다시 읽으니 QA 한 항목에서 일반 앱과 QA 앱 **두 패키지 항목**으로 바뀌었습니다. 두 지문은 일반 앱 설정에 들어간 값이고, 패키지 항목 수는 운영 응답에서 센 값입니다.

여기서 확인한 결과는 서버 연결 문서의 Play 앱 항목 복구까지입니다. 실기기에서는 Play 설치판에 대해 `pm get-app-links`가 대상 도메인을 `verified`로 표시하는지 먼저 확인해야 합니다. 이어서 실제 소셜 로그인 제공자에서 인증한 뒤 앱으로 돌아와 서버가 인증 완료 응답을 보내는지도 확인해야 합니다. 두 단계의 실행 결과는 이 작업 기록에 남아 있지 않습니다.
