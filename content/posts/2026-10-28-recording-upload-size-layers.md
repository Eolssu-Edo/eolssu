---
title: "21MiB와 26MiB가 받은 서로 다른 413"
date: 2026-10-28T00:00:00+09:00
slug: "recording-upload-size-layers"
author: "우동균"
categories: ["backend"]
tags: ["Nginx", "Spring Boot", "multipart", "업로드"]
description: "녹음 파일의 20MiB 한도와 요청 본문의 25MiB 한도를 나누고, 21MiB와 26MiB 요청을 실제 Nginx로 보내 확인했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-recording-upload-size-layers.webp"
draft: false
---

얼쑤의 녹음 업로드를 확인하면서 **21MiB**와 **26MiB** 크기의 입력을 같은 Nginx를 거쳐 보냈습니다. 두 요청 모두 HTTP 413이었습니다. 21MiB 요청에는 백엔드가 만든 JSON 오류 코드 `RECORDING_TOO_LARGE`가 들어 있었고, 26MiB 요청에는 Nginx가 만든 HTML 응답이 돌아왔습니다. 상태 코드는 같았지만 요청을 멈춘 곳이 달랐습니다.

## 파일 한도와 요청 본문 한도 {#파일-하나의-한도와-요청-본문의-한도를-나눴다}

연주 녹음 한 파일에는 **20MiB**, 즉 20,971,520바이트까지 허용하기로 했습니다. 앱은 녹음 파일을 multipart 요청의 한 파트로 보냅니다. 이 요청의 본문에는 파일 바이트 외에도 파트를 구분하는 경계와 파트 헤더가 붙습니다. 요청 본문은 Nginx를 지나고, Spring이 multipart를 파싱한 뒤에야 백엔드의 녹음 검사기가 파일 파트의 크기를 확인할 수 있습니다.

앞의 두 계층에도 파일 한도와 같은 20MiB를 두면, 경계에 맞는 파일도 포장된 본문이 먼저 한도를 넘길 수 있습니다. 그래서 저는 앞 계층에 여유를 두고 20MiB 부근의 파일 크기 판정은 녹음 검사기에 맡겼습니다.

| 계층 | 제한하는 대상 | 설정 |
| --- | --- | --- |
| Nginx의 API 경로 | HTTP 요청 본문 전체 | `client_max_body_size 25m` |
| Spring multipart | 개별 파일과 multipart 요청 전체 | `max-file-size`, `max-request-size` 각각 `25MB` |
| 백엔드 녹음 검사기 | 녹음 파일 파트의 바이트 수 | 20MiB 초과 시 HTTP 413 |

Nginx의 `client_max_body_size` 기본값은 `1m`입니다. 서비스의 API 경로에 25MiB 한도를 명시해, 그 범위의 요청이 백엔드까지 도달하도록 했습니다. Spring의 크기 설정에서 `1MB`도 1,048,576바이트를 뜻합니다. [Nginx의 요청 본문 한도](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size) · [Spring의 크기 단위](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/unit/DataSize.html)

백엔드까지 온 요청은 소유한 연습 세션인지, 업로드 기한이 지났는지, 아직 녹음을 받을 수 있는 상태인지부터 확인합니다. 세션이 유효하면 파일 크기를 확인하고, 그다음에야 `ffprobe`로 컨테이너 형식과 오디오 길이를 검사합니다. 크기에서 거부되면 원본을 객체 저장소에 올리지 않으므로 같은 세션에 더 작은 파일을 보낼 수 있습니다.

## 백엔드의 JSON 413과 Nginx의 HTML 413 {#실제-응답에서-어느-계층이-거절했는지-확인했다}

로컬에서 빌드한 백엔드 JAR을 임시 MySQL 8.4와 저장소 대역에 연결하고, 실제 Nginx에 저장소의 프록시 설정을 적용했습니다. 그 경로에서 요청 크기에 따라 다음 응답이 돌아왔습니다.

| Nginx를 거쳐 보낸 입력 | 응답 | 응답을 만든 곳 |
| --- | --- | --- |
| 정상 AAC 녹음 | HTTP 202 | 백엔드 |
| 21MiB 크기로 준비한 업로드 | HTTP 413, JSON `RECORDING_TOO_LARGE` | 백엔드 |
| 26MiB 크기로 준비한 업로드 | HTTP 413, HTML | Nginx |

백엔드에 직접 보낸 경계 검증에서는 **20,971,521바이트**인 파일이 413을 받았습니다. 검사기 단위 테스트에서도 20MiB를 1바이트 넘긴 파일은 `ffprobe` 호출 전에 거절됐고, 정확히 20MiB인 입력은 크기 검사를 지나갔습니다. 21MiB 요청의 JSON에는 클라이언트가 읽을 오류 코드가 있었습니다. 26MiB 요청은 백엔드까지 도달하지 않아, 클라이언트가 본문 형식과 관계없이 HTTP 413 자체를 크기 초과로 처리하도록 계약을 정했습니다.

## 백엔드 대역으로 Nginx 한도 검증 {#프록시만-떼어-확인하는-검사도-남겼다}

Nginx 설정만 확인하는 별도 검사에서는 요청 본문 한도를 끄고 HTTP 200을 돌려주는 대역을 프록시 뒤에 붙였습니다. 이때는 multipart 대신 정확히 21MiB와 26MiB인 바이너리 본문을 보냈습니다. 운영용 HTTP, 운영용 TLS, 개발용 세 가지 설정 모두에서 **21MiB는 200**, **26MiB는 413**이었습니다. 25MiB 한도 줄을 임시로 빼자 세 설정의 검사가 모두 실패했습니다.

대역의 200은 21MiB 본문이 Nginx를 통과했음을 보여줍니다. 실제 백엔드를 연결한 검사에서는 같은 크기 구간이 녹음 파일 한도에 걸려 JSON 413을 받았습니다. 두 검증을 나눠 둔 덕분에 프록시 설정이 바뀌었을 때와 백엔드의 오류 계약이 달라졌을 때를 따로 확인할 수 있었습니다. 여기서 확인한 것은 로컬 HTTP 경로와 저장소 대역까지이며, 운영 프록시 설정과 실제 객체 저장소에서의 업로드는 별도 검증 범위였습니다.
