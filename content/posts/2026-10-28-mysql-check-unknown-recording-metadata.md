---
title: "녹음 정보 한 칸이 비어도 CHECK가 통과한 이유"
date: 2026-10-28T00:00:00+09:00
slug: "mysql-check-unknown-recording-metadata"
author: "우동균"
categories: ["backend"]
tags: ["MySQL", "CHECK", "NULL", "Kotlin"]
description: "SQL의 UNKNOWN을 만나 부분적으로 비어 있는 녹음 정보가 저장됐습니다. DB 제약과 파일 검사에서 각각 허용 범위를 정했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-mysql-check-unknown-recording-metadata.webp"
draft: false
---

연습 세션은 녹음이 올라오기 전에 먼저 만들어집니다. 그래서 파일 크기, 길이, 샘플레이트, 채널 수는 처음에는 모두 비어 있을 수 있습니다. 업로드를 마친 뒤에는 네 값이 모두 양수여야 합니다. 이 두 가지 상태만 허용하도록 MySQL에 `CHECK`를 추가했습니다.

## NULL과 양수 비교가 만든 UNKNOWN {#양수-비교는-null을-거짓으로-만들지-않는다}

처음 식은 네 칸이 모두 비었거나, 네 칸 모두 0보다 크면 허용하는 모양이었습니다. 그런데 채널 수만 NULL로 바꾼 테스트 행이 저장됐습니다. 네 칸 모두 비었다는 조건은 거짓이었지만, `NULL > 0`은 거짓 대신 SQL의 `UNKNOWN`이 됩니다. 그 결과 식 전체도 `UNKNOWN`이었고, MySQL의 `CHECK`는 평가가 거짓일 때 위반으로 처리합니다.

양수인 경우의 조건에 각 칸이 `IS NOT NULL`이라는 조건을 더했습니다. 이제 일부만 비었을 때는 첫 갈래와 둘째 갈래가 모두 거짓입니다. 임시 MySQL에서 비어 있는 신규 세션과 네 칸이 양수인 갱신은 통과했고, 채널만 비운 갱신은 해당 제약의 **3819**로 거절됐습니다. 네 칸을 각각 0으로 만드는 경우도 같은 제약에서 막혔습니다. [MySQL의 CHECK 제약 문서](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)

## 파일 검사에서 메타데이터 확인 {#파일-검사에서-값을-보장한-뒤-저장으로-넘겼다}

서버가 호출한 ffprobe가 파일에서 샘플레이트나 채널 수를 읽지 못하면 검사 결과에 값이 없을 수 있습니다. 저장까지 넘기면 DB가 거부해 같은 파일을 계속 보내도 저장 오류가 납니다. 파일 검증 단계에서 두 값이 비었거나 0 이하이면 지원하지 않는 파일 형식인 415로 처리하고, 검증이 끝난 메타데이터에는 non-null 정수 타입을 썼습니다.

DB는 어떤 쓰기 경로에서도 네 칸의 조합을 지킵니다. 업로드 API는 파일 정보를 갖춘 경우에만 저장으로 넘깁니다. 두 곳에서 같은 조건을 보는 이유는 누락을 설명할 수 있는 시점과 잘못된 행을 끝까지 막아야 하는 시점이 다르기 때문입니다.
