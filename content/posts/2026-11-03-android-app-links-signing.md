---
title: "Play에서 설치한 앱의 App Links 항목만 빠져 있던 이유"
date: 2026-11-03T00:00:00+09:00
slug: "android-app-links-signing"
author: "우동균"
categories: ["infra"]
tags: ["Android", "App Links", "Google Play", "Docker Compose"]
description: "운영 서버는 정상인데 Play 배포판만 App Links 문서에서 빠졌던 상황과, 공개 JSON에서 패키지 두 항목을 확인한 과정을 정리했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-android-app-links-signing.webp"
draft: false
---

얼쑤에서 Android 소셜 로그인은 외부 브라우저를 열고, 인증 뒤 App Links로 앱에 돌아오는 흐름을 사용했습니다. Play에서 설치한 일반 앱은 브라우저에서 앱으로 돌아오지 못했습니다. 운영 서버의 `assetlinks.json`을 읽어 보니 QA 패키지 한 항목만 있었습니다. 서버의 기동 상태는 정상으로 표시됐습니다.

## 설치한 앱에 찍힌 서명이 필요했다

App Links의 연결 문서에는 패키지 이름과 서명 인증서 지문이 함께 들어갑니다. 직접 빌드해 설치한 앱과 Play가 배포한 앱의 서명이 다르면, 한 쪽을 확인한 지문으로 다른 쪽까지 연결됐다고 볼 수 없습니다. Play에서 받은 설치본에 해당하는 지문은 Play Console의 앱 서명 정보에 나옵니다.

일반 앱 지문을 전달하는 Compose 변수는 `${VAR:-}` 형태의 선택 설정이었습니다. 환경 파일에 키가 없으면 빈 값으로 기동했고, 그 결과 QA 항목만 포함된 문서가 반환됐습니다.

## 응답에서 두 패키지를 확인했다

일반 앱에 대응하는 업로드 키와 Play 앱 서명 키의 지문 두 개를 설정하고 백엔드를 재생성했습니다. 실제 운영에서 다시 읽은 JSON에는 일반 앱과 QA 앱이 각각 한 항목씩 나왔습니다. 설정에 넣은 지문 수와 응답에 나온 패키지 수는 서로 다른 확인값입니다.

여기서 확인한 결과는 서버가 두 패키지의 연결 문서를 제공한 것까지입니다. 이 기록만으로는 Play 설치본의 기기 도메인 검증이나 각 소셜 로그인의 왕복이 끝까지 성공했는지 판단할 수 없습니다.
