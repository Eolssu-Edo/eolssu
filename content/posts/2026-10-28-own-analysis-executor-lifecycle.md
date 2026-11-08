---
title: "분석 실행기를 공용 Bean으로 등록하지 않은 이유"
date: 2026-10-28T00:00:00+09:00
slug: "own-analysis-executor-lifecycle"
author: "우동균"
categories: ["backend"]
tags: ["Spring Boot","실행기","리소스 정리"]
description: "분석 전용 스레드 풀을 기능 내부에 두고, Spring이 관리하는 객체가 풀을 닫도록 연결했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-own-analysis-executor-lifecycle.webp"
draft: false
---

얼쑤는 녹음 업로드가 커밋된 뒤 분석을 실행기에 넘깁니다. 처음 분석하는 경우와 같은 녹음으로 다시 시도하는 경우가 이 실행기를 함께 사용합니다. 동시에 돌릴 작업과 대기열 크기를 분석 기능에 맞게 정하면서, 이 풀이 앱의 다른 비동기 작업에 어떻게 보이는지도 확인해야 했습니다.

## 분석 전용 풀의 범위를 정했다

프로젝트에서 사용한 Spring Boot 4.1.1의 기본 설정에서는 컨텍스트에 사용자 정의 `Executor` Bean이 있으면 자동 구성되는 기본 작업 실행기가 물러납니다. 그래서 분석 전용 풀을 그 타입의 Bean으로 등록하는 결정은 분석 밖의 비동기 설정에도 영향을 줄 수 있었습니다. 앱 전체에 적용하는 실행기 설정을 변경할 이유는 없었습니다. [Spring Boot 작업 실행기 자동 구성](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)

분석을 연결하는 설정에서만 `ThreadPoolTaskExecutor`를 만들고 `AnalysisOnUpload`에 넘겼습니다. 분석 워커 수와 대기열 크기는 이 풀의 설정입니다. 업로드 완료 이벤트와 분석 재시도는 모두 `AnalysisOnUpload`를 통해 작업을 제출합니다. 실행기를 사용하는 경로가 한 곳으로 모이면서, 기능의 실행 정책도 그 객체에서 관리할 수 있었습니다.

## Spring이 닫는 객체에서 풀을 정리했다

풀을 설정 메서드 안에서 직접 만들면 누군가 종료도 맡아야 합니다. Spring이 관리하는 `AnalysisOnUpload`가 `AutoCloseable`을 구현하고, `close()`에서 풀의 `destroy()`를 호출하도록 했습니다. 저장소 쪽에도 같은 소유 원칙을 적용했습니다. 저장소 객체가 닫힐 때 보유한 S3 클라이언트와 서명기를 함께 닫습니다. [Spring Bean의 종료 콜백](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)

앱 종료 때 실행 중인 분석을 끝까지 기다리는 옵션은 껐습니다. 작업이 중단되어 세션이 `PENDING`으로 남으면, 이후 상태 조회가 마감 시각을 확인해 `TIMEOUT`으로 전환하는 기존 규칙을 적용합니다. 종료 때 작업이 남을 수 있음을 서비스의 상태 규칙에 포함한 선택입니다.

## 검사에서 확인한 범위

실행기를 즉시 실행하는 대역으로 바꾼 검사에서는 업로드 완료와 재시도가 각각 지정한 세션을 실행 경로에 넘기는지 확인했습니다. 대기열 거절을 던지는 대역에서는 업로드 완료 리스너가 예외를 자체 처리하고, 재시도 제출은 거절을 호출자에게 전달했습니다. 설정 검사에서는 의존성이 준비된 경우 만든 `AnalysisOnUpload`를 `use`로 감싸 검사 뒤 닫았습니다. 기본 실행기에 미치는 영향은 당시 버전의 자동 구성 조건으로 판단했고, 운영에서 다른 실행기에 얼마나 많은 작업이 전달되는지나 종료 뒤 남은 스레드 수는 측정하지 않았습니다.
