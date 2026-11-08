---
title: "Lax 쿠키에 Origin·JSON 검사를 더한 이유"
date: 2026-10-29T00:00:00+09:00
slug: "layer-cookie-csrf-request-checks"
author: "우동균"
description: "앱에서 쓰는 쿠키 인증에 출처 검사와 본문 규칙을 적용하고, HTTP 403과 415가 각각 무엇을 검증했는지 살펴봅니다."
categories: ["backend"]
tags: ["CSRF","쿠키","HTTP 테스트"]
thumbnail: "https://team-edo.github.io/blog-images/2026-10-layer-cookie-csrf-request-checks.webp"
draft: false
---

서비스에서는 브라우저와 네이티브 앱이 로그인 쿠키를 사용했습니다. 브라우저는 조건이 맞으면 쿠키를 요청에 자동으로 실으므로, 다른 페이지에서 시작된 요청도 상태를 바꿀 수 있는지 확인해야 했습니다. 팀에서 쿠키에 `SameSite=Lax`를 적용하고, 상태를 바꾸는 메서드에 출처와 본문 검사를 추가했습니다.

## 쿠키와 요청 필터의 역할 {#쿠키와-요청-필터가-각각-보는-것}

`Lax` 쿠키는 다른 사이트의 일반적인 POST에 실리지 않습니다. 다른 사이트에서 들어오는 최상위 GET 이동에는 실릴 수 있고, 호스트가 달라도 브라우저에서 같은 사이트로 분류될 수 있습니다. 상태 변경 API는 POST·PUT·PATCH·DELETE를 사용하고, 필터는 `Origin`이 있는 요청에 대해 허용한 출처 또는 서비스의 호스트 규칙을 검사합니다. 네이티브 앱처럼 헤더가 없는 경우에는 본문과 인증 검사로 이어집니다. [HTTP Working Group의 SameSite 사양 초안](https://httpwg.org/http-extensions/draft-ietf-httpbis-rfc6265bis.html)

일반 상태 변경 요청에서 본문이 있으면 JSON을 요구합니다. HTML 폼이 보내는 본문 타입과 달리 다른 출처의 JSON fetch는 브라우저의 CORS 사전 확인을 거칩니다. 녹음 업로드처럼 파일을 올리는 경로는 multipart를 허용하되 출처 검사도 받습니다. 본문 없는 POST도 있으므로 미디어 타입 검사만으로 모든 요청을 판별할 수는 없습니다. [Fetch 표준의 CORS safelist](https://fetch.spec.whatwg.org/#cors-safelisted-request-header)

## 403·415의 발생 지점 {#403과-415가-실제로-어디에서-나오는지-확인했다}

실제 HTTP 자동 검사에서 다른 사이트의 요청은 403, 허용되지 않은 폼·텍스트 본문은 415를 반환했습니다. JSON과 본문 없는 요청은 라우팅까지 도달했고, multipart는 허용 경로와 그 밖의 경로에서 서로 다른 결과를 냈습니다. 테스트에서 보이는 성공은 해당 필터를 통과했다는 뜻이며, 요청의 인증과 실제 기능 성공은 다음 단계가 판단합니다.

프록시 관련 검사에서는 CORS를 끈 상태에서 호스트 포트 비교만 검증했습니다. 포트 검사 조건을 빼자 403이어야 할 요청이 라우팅까지 진행해 404가 나왔고, 조건을 복원하자 통과했습니다. CORS가 켜진 테스트에서는 두 필터가 같은 403을 낼 수 있었습니다. 그래서 상태 숫자가 일치하는지에 더해, 보호하려는 조건을 빼면 검사도 함께 실패하는지 확인했습니다.
