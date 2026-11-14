---
title: "제가 올린 AAB와 Play 스토어 설치본의 서명이 다른 이유"
date: 2026-11-14
slug: "aab-upload-key-and-installed-apk"
author: "오은서"
categories: ["frontend"]
tags: ["Android", "Google Play", "앱 서명", "App Links"]
description: "AAB 업로드부터 APK 설치까지 서명이 어디서 달라지는지 살펴보고, Android App Links에 필요한 인증서 지문을 찾는 순서를 정리했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-aab-upload-key-and-installed-apk.webp"
draft: false
---

얼쑤의 Android 앱에서 간편 로그인을 시작하면 외부 브라우저가 열립니다. 인증을 마치면 HTTPS 콜백 링크를 타고 앱으로 돌아오는 흐름입니다. 그런데 개인 PC에서 빌드한 APK를 폰에 설치해 보니, 인증 뒤 브라우저에 그대로 머물렀습니다. 당시 서버의 연결 문서에는 팀 QA 앱의 패키지와 서명 지문만 올라가 있었습니다. 내가 설치한 앱은 일반 패키지였고, 로컬 디버그 키로 서명돼 있었습니다.

이 사례를 이해하려면 Git에서 어떤 코드를 받아 빌드했는지에 더해, **폰에 설치된 APK에 어떤 인증서가 붙어 있는지** 봐야 합니다. Play 출시를 준비하면 같은 질문이 한 번 더 나옵니다. 내가 서명해 올리는 파일은 AAB인데, 사용자의 폰에 들어가는 파일은 Play가 배포하는 APK이기 때문입니다.

## AAB를 올릴 때와 APK를 배포할 때

Play App Signing을 사용할 때 팀은 **업로드 키**로 AAB에 서명합니다. Play는 등록된 업로드 인증서로 제출물을 확인하고, 번들로부터 배포용 APK를 만들어 **앱 서명 키**로 서명합니다. 사용자가 Play에서 설치하는 APK에는 이 앱 서명 키의 인증서가 들어갑니다. 두 키를 분리해 쓰면 업로드 키를 분실해도 재설정을 요청할 수 있고, 사용자에게 배포하는 APK의 서명은 유지할 수 있습니다. [Android 앱 서명 공식 문서](https://developer.android.com/studio/publish/app-signing)

| 확인할 파일 | 서명에 쓰이는 키 | 확인할 지문 |
| --- | --- | --- |
| 개인 PC에서 빌드한 디버그 APK | 해당 빌드의 디버그 키 | 그 APK에 들어 있는 인증서 지문 |
| 팀에서 직접 설치하는 QA APK | QA 빌드에 설정한 키 | 팀 QA APK에 들어 있는 인증서 지문 |
| Play Console에 올리는 AAB | 업로드 키 | Console의 업로드 키 인증서 지문 |
| Play에서 배포하는 APK | 앱 서명 키 | Console의 앱 서명 키 인증서 지문 |

직접 설치하는 릴리스 APK도 표에 넣을 수 있습니다. 그때는 그 APK를 빌드할 때 사용한 키의 지문을 적으면 됩니다. 팀에서 직접 설치하는 APK를 업로드 키로 서명하는 경우에만, 그 직접 설치본에 업로드 키 지문을 확인합니다.

## 링크는 패키지 이름과 인증서를 함께 확인합니다

Android App Links에서 웹사이트는 `/.well-known/assetlinks.json`을 통해 어떤 앱이 해당 링크를 열 수 있는지 선언합니다. 앱을 지정할 때 쓰는 값은 **패키지 이름과 APK 서명 인증서의 SHA-256 지문**입니다. 앱 쪽에도 해당 HTTPS 호스트와 경로를 처리할 인텐트 필터가 있어야 합니다. Play 배포용 지문은 Play Console의 앱 서명 화면에서 확인할 수 있습니다. [Android App Links 설정 문서](https://developer.android.com/studio/write/app-link-indexing)

얼쑤의 로컬 APK 사례에서는 폰에 설치된 앱과 서버가 등록한 QA 앱의 패키지와 지문이 모두 달랐습니다. 기기에서 도메인 검증 상태를 조회했을 때도 `verified`가 아니었습니다. 따라서 처음 확인할 곳은 브라우저에서 인증이 성공했는지 보여주는 화면과 별개로, **그 링크를 Android가 어떤 앱에 넘길 수 있는지**였습니다.

## 손에 든 설치본에서 확인하는 순서

**1. 패키지와 인증서부터 확인합니다.** 직접 설치한 APK 파일이 있다면 Android SDK의 `apksigner`로 서명을 검증하면서 인증서 정보를 출력합니다. 출력에서 SHA-256 지문을 찾습니다. Play에서 설치했다면 Play Console의 앱 서명 인증서 항목을 확인했습니다. [apksigner 공식 문서](https://developer.android.com/tools/apksigner)

~~~bash
apksigner verify --print-certs app.apk
~~~

**2. 콜백 도메인의 연결 문서와 대조합니다.** 서비스의 `/.well-known/assetlinks.json`에서 설치본의 패키지 이름에 해당하는 항목을 찾고, 그 항목의 지문 목록에 1단계에서 찾은 지문이 있는지 봅니다. QA와 일반 앱처럼 패키지가 다르면 각 패키지에 해당하는 항목을 확인했습니다.

**3. 기기가 검증한 결과와 로그인 흐름을 각각 확인합니다.** Android 12 이상에서는 다음 명령으로 도메인 검증 상태와 현재 사용자의 링크 열기 설정을 살펴볼 수 있습니다. 패키지 이름은 실제 설치본의 값으로 바꿉니다. 대상 도메인의 `verified`를 확인한 뒤, 로그인 화면에서 시작해 브라우저 인증, 앱 복귀, 로그인 완료까지 직접 따라갑니다. [Android App Links 검증 문서](https://developer.android.com/training/app-links/verify-applinks)

~~~bash
adb shell pm get-app-links --user cur com.example.app
~~~

인증서 지문은 **설치본의 배포 경로를 따라가면** 찾기 쉽습니다. QA APK는 QA 빌드에 사용한 인증서, Play 설치본은 Play 앱 서명 인증서부터 보면 됩니다. 이 순서로 설치본을 특정하면 브라우저에서 돌아오지 않는 문제를 조사할 때 앱의 콜백 처리와 Android의 링크 검증 중 어디를 먼저 살펴볼지 정할 수 있습니다.

운영 연결 문서를 수정한 과정은 [Play 배포판의 App Links가 빠진 이유](https://team-edo.github.io/posts/2026/11/android-app-links-signing/)에 정리돼 있습니다.
