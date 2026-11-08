---
title: "설정값 30이 30밀리초가 된 이유"
date: 2026-10-28T00:00:00+09:00
slug: "duration-configuration-units"
author: "우동균"
description: "Spring Boot 설정의 숫자 30은 30ms로 바인딩됐습니다. 단위를 명시하고 실제 문자열 바인딩으로 분석 제한 시간과 응답 대기를 함께 검사했습니다."
categories: ["backend"]
tags: ["Spring Boot", "설정", "테스트"]
thumbnail: "https://team-edo.github.io/blog-images/2026-10-duration-configuration-units.webp"
draft: false
---

녹음 분석 설정을 정리하면서 숫자만 적은 제한 시간은 초 단위로 받기로 했습니다. 설정에 `30`을 넣어 검사하자 바인딩 결과는 `PT0.03S`, 즉 **30밀리초**였습니다. `Duration.ofSeconds(30)`으로 설정 객체를 직접 만드는 테스트였다면 이 차이는 드러나지 않았을 겁니다.

## 숫자 설정의 기본 단위를 초로 {#테스트도-문자열에서-시작했다}

Spring Boot는 단위가 붙은 `90s`와 ISO 8601 형식을 읽습니다. 숫자만 있으면 기본 단위는 밀리초입니다. 그래서 초 단위로 쓰기로 한 필드에 `@DurationUnit(ChronoUnit.SECONDS)`를 붙였습니다. 숫자로만 입력한 값의 기본 단위가 바뀌고, `90s`처럼 단위를 적은 값은 그대로 읽힙니다. [Spring Boot의 Duration 바인딩 문서](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.conversion.durations)

또 분석 제한 시간과 백엔드가 AI 응답을 기다리는 시간은 따로 둡니다. 응답 대기 기본값은 분석 제한보다 **5초** 길게 계산하게 했습니다. 설정 하나를 늘렸을 때 응답 대기가 예전 값에 머무르면, 분석이 결과를 돌려줄 시간을 주지 못할 수 있기 때문입니다.

## 문자열을 바인딩해 결과 비교 {#바인딩-결과를-직접-비교했다}

테스트에서 Spring Boot의 `Binder`에 운영 설정과 같은 형태의 문자열을 넘겼습니다. 수정 전 실패하던 `30`은 수정 뒤 **30초**로 읽혔습니다. 숫자로 적은 응답 대기 **40**도 40초로 읽혔습니다. 분석 제한에 `90s`만 지정한 검사에서는 계산된 응답 대기 기본값이 **95초**였습니다.

30밀리초는 바인더에서 관측한 값이고, 5초는 응답 대기에 둔 여유 설정입니다. 테스트는 실제 분석의 처리 시간을 재는 대신, 문자열이 설정 객체가 될 때 두 시간의 단위와 관계가 보존되는지를 확인했습니다.
