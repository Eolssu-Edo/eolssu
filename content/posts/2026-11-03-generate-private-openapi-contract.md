---
title: "문서 주소를 열지 않고 내부 FastAPI 계약을 확인하기"
date: 2026-11-03T00:00:00+09:00
slug: "generate-private-openapi-contract"
author: "우동균"
description: "내부 분석 API의 OpenAPI 문서를 앱에서 생성하고, 문서용 응답 모델과 실제 JSON이 일치하는지 검사했습니다."
categories: ["ai"]
tags: ["FastAPI", "OpenAPI", "계약 테스트"]
thumbnail: "https://team-edo.github.io/blog-images/2026-11-generate-private-openapi-contract.webp"
draft: false
---

백엔드가 호출하는 분석 API의 요청과 응답을 한곳에서 읽을 수 있게 만들고 싶었습니다. 분석 서버는 내부에서만 호출하는 구성이라 운영에 문서 조회 주소를 열 필요는 없었습니다. 또 이미 사용 중인 응답에는 상태마다 다른 필드와 JSON 이름이 있어서, 문서를 붙이는 작업이 기존 직렬화까지 바꾸면 호출자에 영향이 갈 수 있었습니다.

## 스키마 생성과 문서 조회 주소를 분리했다

FastAPI의 `docs_url`, `redoc_url`, `openapi_url` 설정은 꺼 둔 채, 앱 객체의 `app.openapi()`에서 문서를 생성하도록 했습니다. 개발 환경에서 JSON을 파일로 저장해 문서를 읽을 수 있습니다. HTTP 테스트에는 문서 관련 경로 세 곳이 모두 404를 반환하는지도 추가했습니다. [FastAPI의 문서 URL 설정](https://fastapi.tiangolo.com/tutorial/metadata/#openapi-url)

응답을 내보내는 기존 코드는 유지하고, `responses`에 상태 코드별 문서 모델을 연결했습니다. 성공적으로 HTTP 200을 받은 경우에도 분석이 끝난 `DONE`과 녹음을 분석할 수 없었던 `FAILED`의 본문이 다릅니다. 두 형태를 상태 필드로 구분하는 모델로 문서에 표현했습니다. [FastAPI의 OpenAPI 응답 설정](https://fastapi.tiangolo.com/advanced/additional-responses/)

## 문서와 응답이 따로 움직이지 않도록 검사했다

문서에만 붙인 모델은 실제 응답을 변환하지 않으므로 둘이 어긋날 수 있습니다. 기존 응답 테스트에 상태 코드에 맞는 모델로 JSON을 읽는 검사를 넣었습니다. 성공 응답에서는 음표 항목까지 내려가 문서 모델의 JSON 별칭과 실제 키 집합을 비교했습니다. 알 수 없는 필드를 무시하는 모델이라면 값을 읽는 검사만으로는 실제 응답에 남는 키를 발견하기 어렵기 때문입니다.

스키마 검사와 HTTP 검사는 각각 다른 조건을 보호합니다. 하나는 문서에 약속한 요청과 응답 형태를 보고, 다른 하나는 응답이 그 약속에 맞는지와 문서 URL이 닫혀 있는지를 봅니다. 내부 API의 계약을 읽을 수 있게 하면서도 외부에 공개할 경로의 범위를 따로 관리할 수 있었습니다.
