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

별도 QA APK를 설치한 실기기에서는 대상 도메인의 `verified` 상태를 확인했고, 사용자가 Google 로그인 뒤 앱으로 돌아와 닉네임이 표시되는 것을 확인했습니다. 일반 Play 설치판의 실기기 결과는 아직 확보하지 못했습니다.

Play 설치판은 앱 버전과 Android 버전을 기록하고, `pm get-app-links --user cur`에서 대상 도메인의 `verified`와 현재 사용자의 링크 열기 상태를 확인합니다. 이어서 서비스에 연결된 계정으로 간편 로그인을 새로 시작합니다. 제공자 인증 뒤 Play 앱이 열리고 로그인 완료 API가 한 번 HTTP 200을 반환해 회원 화면이 표시되면 왕복을 확인할 수 있습니다. 도메인 상태와 로그인 왕복은 각각 기록합니다. [Android App Links 공식 검증 절차](https://developer.android.com/training/app-links/verify-applinks#manual-verification)
