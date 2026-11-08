---
title: "곡 목록은 200, 콘솔 오류는 어디서 왔나"
date: 2026-11-03T00:00:00+09:00
slug: "console-error-origin-before-api"
author: "우동균"
categories: ["frontend"]
tags: ["브라우저 디버깅", "Chrome DevTools", "HTTP"]
description: "곡 목록의 응답과 같은 시점에 찍힌 콘솔 오류를 대조해, 브라우저 확장과 문서 UI가 남긴 오류의 출처를 구분했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-console-error-origin-before-api.webp"
draft: false
---

얼쑤의 곡 목록 API를 점검할 때 Chrome 콘솔에 `Cannot read properties of undefined` 오류가 **아홉 건** 찍혔습니다. 곡 목록을 요청하는 동안 보인 오류라 처음에는 응답을 처리하는 코드부터 봐야 할지 판단하기 어려웠습니다. 같은 요청의 응답은 **HTTP 200**이었고 본문에는 `isSuccess: true`가 들어 있었습니다.

## 스택에서 파일 출처 확인 {#콘솔에서는-스택의-파일-출처를-봤습니다}

오류 아홉 건의 스택에는 브라우저 확장의 `contentScript.js`가 있었습니다. 파일명만으로는 앱의 스크립트인지 알기 어려웠고, 출처가 확장 프로그램의 주소라는 점이 단서였습니다. 같은 기록에는 Swagger UI의 번들에서 발생한 응답 표시 오류도 따로 있었습니다. 두 종류의 메시지는 비슷한 시점에 떴지만 서로 다른 스크립트에서 실행된 코드였습니다. [Chrome DevTools 콘솔 문서](https://developer.chrome.com/docs/devtools/console/reference)

## 네트워크에서 응답 확인 {#네트워크에서는-그-요청의-응답을-봤습니다}

API 응답을 확인할 때는 문서 UI에 보이는 결과와 별도로 같은 요청의 상태 코드와 본문을 읽었습니다. 그 요청에서 확인한 200과 성공 표시는 서버가 곡 목록을 정상 응답으로 보냈다는 근거였습니다. 확장 스크립트와 문서 UI가 왜 그 뒤에 각각 오류를 냈는지는 이 두 값으로 알 수 없었습니다. 상태와 본문을 먼저 확보하면 어느 쪽을 재현할지 범위를 좁힐 수 있습니다. [Chrome DevTools 네트워크 문서](https://developer.chrome.com/docs/devtools/network/reference)

확장 쪽에서 확인한 것은 오류 아홉 건의 스크립트 출처와 같은 요청의 HTTP 응답입니다. 확장을 끈 상태에서 같은 요청의 스택과 응답을 비교하는 단계는 당시 실행 기록에 남아 있지 않습니다.

Swagger UI에서 응답을 표시하다 생긴 오류는 별도로 목록 구조를 추적했습니다. 후속 작업에서 응답 크기의 대부분을 차지하던 악보를 목록에서 분리한 뒤, 실제 HTTP와 MySQL을 사용해 목록에서 본문이 빠지고 곡별 조회에서 원본을 받을 수 있음을 확인했습니다. [1.76MB 곡 목록에서 악보 덜어내기](https://team-edo.github.io/posts/2026/11/song-list-payload-and-gzip/)
