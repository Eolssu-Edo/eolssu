---
title: "MySQL 한글이 물음표로 보일 때"
date: 2026-10-29T00:00:00+09:00
slug: "diagnose-mysql-result-charset"
author: "우동균"
description: "MySQL 조회에서 보이는 물음표가 저장된 값인지, 결과를 내보내는 연결에서 생긴 것인지 나눠 봅니다."
categories: ["backend"]
tags: ["MySQL","문자 집합","디버깅"]
thumbnail: "https://team-edo.github.io/blog-images/2026-10-diagnose-mysql-result-charset.webp"
draft: false
---

곡 제목을 MySQL 클라이언트에서 읽을 때, 일부 한글이 `???` 또는 `?????`로 표시됐습니다. 화면만 보고 저장값을 고치면 멀쩡한 행까지 바꿀 수 있습니다. 이 기록에서 확인한 것은 조회 화면에 물음표가 있었다는 사실입니다. 저장된 바이트에 물음표가 있었는지는 별도의 확인이 필요했습니다.

## 표시와 저장값 분리 {#출력과-저장을-따로-읽는다}

MySQL은 연결에서 사용하는 문자 집합에 맞춰 결과를 변환할 수 있습니다. 클라이언트가 요청한 결과 문자 집합으로 한글을 표현하지 못하면 저장값이 정상이어도 화면에는 물음표가 나올 수 있습니다. 먼저 같은 행을 `utf8mb4`를 지정한 세션에서 다시 읽고, 연결이 실제로 어떤 문자 집합을 쓰는지 확인하는 방식이 있습니다. [MySQL 연결 문자 집합 문서](https://dev.mysql.com/doc/refman/8.4/en/charset-connection.html)

```sql
SELECT @@character_set_client, @@character_set_connection, @@character_set_results;
SELECT title, HEX(title) FROM songs WHERE id = 1;
```

두 번째 쿼리의 테이블과 ID는 예시입니다. MySQL CLI는 `--default-character-set=utf8mb4`를 지정해 다시 연결할 수 있습니다. `HEX(title)`은 결과를 ASCII로 표현하므로, 값에 들어 있는 바이트를 표시용 문자 변환과 분리해 볼 수 있습니다. 컬럼이 UTF-8이라면 의심한 위치에 유효한 한글 바이트가 있는지, 실제로 `3F`가 반복되는지 확인합니다.

## 같은 물음표로 보인 두 행

별도의 폐기용 MySQL **8.4.9**에서 `utf8mb4` 제목 컬럼에 `아리랑`과 `???`를 한 행씩 넣었습니다. 두 세션 모두 클라이언트와 연결 문자 집합을 `utf8mb4`로 유지했습니다. 한 세션에서만 `SET SESSION character_set_results = latin1`을 실행한 뒤, 두 세션에서 같은 두 행의 `title`, `HEX(title)`, `LENGTH(title)`을 조회했습니다.

| 저장한 값 | `utf8mb4` 세션의 `title` | `latin1` 세션의 `title` | 두 세션에서 읽은 저장 바이트 |
| --- | --- | --- | --- |
| `아리랑` | `아리랑` | `???` | `EC9584EBA6ACEB9E91` · 9바이트 |
| `???` | `???` | `???` | `3F3F3F` · 3바이트 |

`latin1` 세션에서는 두 행이 모두 `???`로 보였습니다. 결과를 `utf8mb4`로 받은 세션에서는 원래 한글을 다시 읽을 수 있었고, 물음표를 직접 넣은 행은 두 세션에서 같은 `3F3F3F`를 돌려줬습니다.

## 조회 결과별 진단 {#결과에-따라-조사-방향을-바꾼다}

한글 바이트가 남아 있고 새 세션에서 정상으로 읽히면 결과 문자 집합 쪽으로 범위를 좁힐 수 있습니다. 두 세션의 결과가 같고 저장값에 `3F`가 들어 있다면 가져오기나 저장 시점에 어떤 인코딩을 썼는지 추적합니다. 컬럼과 연결이 모두 UTF-8이어도 터미널 표시가 깨진다면 같은 결과를 다른 출력 환경에서 읽어 볼 수 있습니다.

위 표는 새로 만든 두 행의 결과입니다. 처음 물음표가 보였던 행은 당시 세션의 문자 집합과 저장 바이트를 함께 확인한 기록이 없어 원인을 열어 둡니다.
