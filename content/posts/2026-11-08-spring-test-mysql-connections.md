---
title: "Spring 테스트 53개가 실패한 뒤 확인한 MySQL 연결 한도"
date: 2026-11-08T00:00:00+09:00
slug: "spring-test-mysql-connections"
author: "우동균"
categories: ["backend"]
tags: ["Spring", "MySQL", "HikariCP", "통합 테스트"]
description: "얼쑤의 MySQL 통합 테스트 174개 중 53개가 실패한 기록을 바탕으로, Spring이 캐시한 컨텍스트의 연결 풀이 어떻게 DB 한도에 닿는지 살펴봅니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-spring-test-mysql-connections.webp"
draft: false
---

얼쑤 백엔드의 `databaseTest`는 Spring 애플리케이션과 실제 MySQL을 연결해 실행하는 통합 테스트입니다. 앞선 실행에서 161개가 모두 통과했는데, 다른 변경을 병합한 뒤에는 174개 중 53개가 실패했습니다. 서로 다른 기능을 확인하는 테스트에서 같은 메시지가 45번 나왔습니다. `Too many connections`였습니다.

## 실패한 테스트보다 먼저 연결을 확인했다

Spring 테스트가 애플리케이션 컨텍스트를 올리는 도중 DB에 연결하지 못하면 테스트 코드까지 실행되지 못합니다. 같은 설정의 컨텍스트를 여는 데 이미 실패한 뒤에는 Spring이 다음 로딩 시도를 건너뛰면서 `ApplicationContext failure threshold exceeded`를 남길 수 있습니다. 이 메시지만 보면 원인이 비슷한 실패가 여러 개 생긴 것처럼 보입니다. 앞선 로딩 실패의 예외까지 내려가면 MySQL이 새 연결을 거부했다는 단서가 나옵니다. [Spring의 컨텍스트 로딩 실패 정책](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/failure-threshold.html) · [MySQL의 연결 초과 오류](https://dev.mysql.com/doc/refman/8.4/en/too-many-connections.html)

## 테스트가 끝나도 연결 풀이 남을 수 있다

Spring은 같은 설정의 테스트가 애플리케이션을 반복해서 띄우는 비용을 줄이려고 컨텍스트를 캐시합니다. 활성 프로필이나 테스트 속성, 대체한 빈이 다르면 다른 캐시 키가 생길 수 있습니다. 이때 같은 MySQL을 쓰는 여러 컨텍스트가 각자의 HikariCP 연결 풀을 띄우면, 먼저 끝난 테스트의 컨텍스트도 캐시에 남아 DB 연결을 유지할 수 있습니다. 테스트를 순서대로 실행해도 나중에 시작한 컨텍스트의 연결과 앞서 열어 둔 연결이 겹치는 이유입니다. [Spring의 컨텍스트 캐시](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)

HikariCP의 `maximumPoolSize`는 풀 하나가 열 수 있는 연결의 상한이고, `minimumIdle`은 풀이 유지하려는 유휴 연결 수입니다. MySQL의 `max_connections`는 같은 인스턴스에 붙는 클라이언트 전체에 적용됩니다. 풀 하나의 설정만 보면 여유가 있어도, 여러 풀이 사용하는 연결을 합하면 새 컨텍스트를 올릴 자리가 부족해질 수 있습니다. [HikariCP의 연결 풀 설정](https://github.com/brettwooldridge/HikariCP)

## 폐기용 MySQL의 한도를 바꾸고 같은 테스트를 다시 돌렸다

`max_connections`는 인스턴스 전체에 적용됩니다. 한도를 바꿀 실험은 테스트가 끝나면 폐기할 별도 MySQL에서 진행했습니다. 이 실행에서는 컨텍스트별 연결 수나 전체 최대 동시 연결 수까지 기록하지 않았습니다.

통합 테스트의 데이터도 실행 단위로 분리했습니다. 테스트를 실행할 때 임시 DB와 전용 사용자를 만들고, 그 사용자의 권한은 해당 DB로 한정했습니다. 테스트가 실패해도 정리 단계에서 실행용 DB와 사용자를 제거하는 절차를 둡니다. 스키마를 나누는 것만으로는 같은 인스턴스의 연결 한도가 나뉘지 않습니다. 이 실험에서 별도의 MySQL을 쓴 이유는 연결 한도도 운영 DB와 분리해 바꿀 수 있기 때문입니다.

폐기용 MySQL 8.4의 연결 한도를 기본값 **151**에서 **1,000**으로 올린 뒤, 병합 후의 같은 테스트 묶음을 다시 실행했습니다. 바꾼 조건은 테스트 인스턴스의 연결 한도였습니다. 처음에 **53개**가 실패했던 **174개**가 두 번째 실행에서는 모두 통과했습니다. [MySQL의 max_connections 설정](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html#sysvar_max_connections)
