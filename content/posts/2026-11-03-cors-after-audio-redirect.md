---
title: "206 응답 뒤에 숨은 CORS 오류"
date: 2026-11-03T00:00:00+09:00
slug: "cors-after-audio-redirect"
author: "우동균"
categories: ["infra"]
tags: ["CORS", "Cloudflare R2", "HTTP", "서명 URL"]
description: "음원 API의 302를 따라간 브라우저 요청이 실패한 지점을 찾고, R2의 CORS 정책을 바꾼 뒤 실제 Range 응답으로 확인했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-cors-after-audio-redirect.webp"
draft: false
---

얼쑤의 모범 연주 음원 API를 Swagger에서 확인하다가 `Failed to fetch`를 만났습니다. 같은 음원의 처음 2바이트를 `curl`로 요청하니 저장소는 HTTP 206을 반환했습니다. 저장소가 바이트를 보내고도 브라우저에서 읽을 수 없는 이유를 찾으려면, 처음 요청한 API와 음원을 실제로 돌려주는 서버를 나눠 봐야 했습니다.

## 302 이후의 응답 추적 {#302-뒤의-응답을-확인했다}

Swagger 페이지와 음원 API는 같은 출처에 있었습니다. API는 곡과 음원의 게시 상태를 확인한 뒤 HTTP 302의 `Location` 헤더에 R2 서명 URL을 담았습니다. 브라우저가 리다이렉트를 따라가면 음원 본문은 다른 출처인 R2에서 내려옵니다. 그 응답에는 Swagger 페이지의 출처를 허용하는 `Access-Control-Allow-Origin`이 없었습니다. 브라우저는 저장소 응답을 받았지만 페이지의 자바스크립트에 넘기지 않았습니다. [Fetch 표준의 CORS 검사](https://fetch.spec.whatwg.org/#cors-check)

브라우저에서 음원을 어떻게 쓰는지도 중요했습니다. `crossorigin` 없이 `<audio src>`로 불러오는 경우 브라우저가 미디어를 재생할 수 있습니다. `fetch`로 받아 자바스크립트에서 응답을 읽는 Swagger는 최종 응답의 CORS 허용이 필요했습니다. [HTML 표준의 미디어 요청](https://html.spec.whatwg.org/multipage/media.html#concept-media-load-resource)

## 최종 응답 버킷에 CORS 적용 {#최종-응답을-보내는-버킷에-정책을-적용했다}

재생 요청마다 API가 현재 악보와 게시 상태를 확인하고 새 서명 URL을 발급하는 흐름을 사용하고 있었습니다. 저는 그 계약을 유지하고 R2 버킷에 CORS 규칙을 추가했습니다. 운영 웹과 개발 웹 두 출처를 명시하고, 브라우저에 허용하는 메서드는 `GET`과 `HEAD`로 제한했습니다. 자바스크립트가 범위 응답을 해석할 수 있도록 `Content-Range`와 `Accept-Ranges`도 노출했습니다. 이 규칙은 버킷에 적용되므로, 같은 버킷의 다른 객체에도 어떤 출처를 허용하게 되는지 함께 확인했습니다. [Cloudflare R2의 CORS 설정](https://developers.cloudflare.com/r2/buckets/cors/)

## 같은 2바이트 요청으로 비교 {#같은-2바이트-요청으로-정책-전후를-비교했다}

정책을 바꾸기 전과 후에 각각 API에서 새로 발급받은 서명 URL을 사용했습니다. 먼저 `curl`로 `Origin`, 요청 메서드 `GET`, 요청 헤더 `range`를 담은 `OPTIONS`를 직접 보내 버킷의 사전 요청 응답을 확인했습니다. 이어서 같은 출처 값과 `Range: bytes=0-1`을 붙인 GET으로 처음 2바이트를 요청했습니다. 조건에 맞는 단일 `Range` 헤더는 브라우저가 사전 요청 없이 보낼 수도 있습니다. 여기서 직접 보낸 `OPTIONS`는 버킷이 메서드와 헤더 조합을 어떻게 판단하는지 확인하기 위한 요청이었습니다. [Fetch 표준의 사전 요청 없이 보낼 수 있는 헤더](https://fetch.spec.whatwg.org/#cors-safelisted-request-header)

| 최종 저장소에 보낸 요청 | 적용 전 | 적용 후 |
| --- | --- | --- |
| 허용 대상 출처 두 곳에서 GET 사전 요청 | 각각 403 | 각각 204, 요청 출처와 같은 허용 헤더 |
| 같은 두 출처에서 처음 2바이트 GET | 각각 206, CORS 헤더 없음 | 각각 206, 출처 허용 및 범위 헤더 노출 |
| 허용 목록 밖 출처에서 GET 사전 요청 | — | 403 |
| 허용 목록 밖 출처에서 처음 2바이트 GET | — | 206, CORS 헤더 없음 |
| 허용 출처에서 PUT 사전 요청 | — | 403 |

허용 목록 밖의 출처를 붙인 GET에도 R2는 206을 보냈습니다. 이 요청에는 유효한 읽기 서명이 들어 있었습니다. 저장소에서 읽을 권한은 서명으로 확인하고, 브라우저에서 어떤 페이지의 자바스크립트가 응답을 읽을지는 CORS 헤더로 정합니다. [Cloudflare R2의 서명 URL](https://developers.cloudflare.com/r2/api/s3/presigned-urls/)

마지막으로 실제 운영 Swagger 페이지에서 `fetch`를 실행했습니다. API의 302를 따라간 뒤 자바스크립트에서 HTTP 206과 `audio/mp4`, `Content-Range: bytes 0-1/554262`를 읽을 수 있었습니다. 처음 확인했던 오류는 같은 페이지에서 더 이상 발생하지 않았습니다. 이 검증은 Swagger 페이지에서 보낸 범위 요청 한 건의 결과이며, UI의 실행 버튼과 음원 전체 재생, 서명 만료 후 재생 위치 이동은 별도 확인 범위였습니다.
