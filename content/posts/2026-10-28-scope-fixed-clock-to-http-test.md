---
title: "HTTP 테스트에서만 수신 시각 고정하기"
date: 2026-10-28T00:00:00+09:00
slug: "scope-fixed-clock-to-http-test"
author: "우동균"
categories: ["backend"]
tags: ["Spring Boot", "Clock", "통합 테스트", "Kotlin"]
description: "서버 수신 시각이 결과를 바꾸는 체류 기록 HTTP 테스트에서 시계를 고정하고, 다른 업무의 시계와 적용 범위를 나눈 방법입니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-scope-fixed-clock-to-http-test.webp"
draft: false
---

악보 화면을 본 시간을 서버에 보낼 때, 서버는 앱이 보낸 시간과 실제로 요청을 받은 시각을 대조합니다. 화면을 본 시간이 수신 시각에서 시작 시각을 뺀 값보다 지나치게 길면 저장하지 않습니다. 이런 API를 고정된 날짜로 테스트하면, 테스트를 실행하는 날짜에 따라 같은 요청이 허용되거나 거절될 수 있습니다.

## 테스트 설정에 고정 `Clock` 주입 {#실제-http-테스트에만-수신-시계를-고정했다}

체류 기록 테스트는 애플리케이션을 띄우고 실제 HTTP로 요청합니다. 서비스만 직접 호출하는 테스트처럼 생성자에 시계를 건네는 것으로는 서버의 수신 시각을 고정할 수 없습니다. 그래서 이 테스트가 가져오는 `@TestConfiguration`에 고정된 `Clock`을 만들고 `@Primary`를 붙였습니다. 이 설정을 가져온 테스트에서는 같은 타입을 주입할 때 고정 시계를 우선합니다.

테스트의 설정과 연결 부분만 뽑으면 아래와 같습니다. `@Import`는 이 HTTP 테스트에 설정을 포함하고, `@Primary`는 같은 타입의 빈 중 여기서 만든 시계를 우선하게 합니다.

```kotlin
@Import(ScoreViewApiDatabaseTests.FixedClockConfig::class)
class ScoreViewApiDatabaseTests {
    // HTTP 요청 검사 본문 생략

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfig {
        @Bean
        @Primary
        fun fixedScoreViewClock(): Clock =
            Clock.fixed(Instant.parse("2026-10-28T05:05:01Z"), ZoneOffset.UTC)
    }
}
```

이 시계는 테스트 클라이언트가 날짜를 바꿔서 보내는 장치가 아닙니다. 실제 HTTP를 받은 서버의 저장 경로에서 수신 시각을 읽을 때 사용됩니다.

수신 시각을 **05:05:01 UTC**로 고정한 상태에서 시작 시각이 05:00 UTC 부근이고 보인 시간이 300초인 구간을 보냈습니다. 첫 요청은 **201**, 같은 키와 내용의 재전송은 **200**, 같은 키로 인정 시간을 바꾼 요청은 **409**를 받았습니다. MySQL에는 구간이 **1행** 남았습니다. 이 검사는 고정 수신 시각에서 API와 DB가 연결된 동작을 확인한 결과입니다.

## 성장 통계에는 별도 UTC 시계 {#시계가-필요한-곳에서-적용-범위를-정했다}

성장 통계의 ‘오늘’과 약관 조회의 시간도 같은 타입을 쓰지만 각각 쓰임새가 다릅니다.

| 읽는 곳 | 이 시계를 정한 위치 |
| --- | --- |
| 체류 기록 HTTP 테스트의 수신 시각 | 그 테스트에서만 고정 `Clock`을 가져옵니다. |
| 성장 통계의 오늘 날짜 | 통계를 조립하는 설정에서 UTC 시계를 전달합니다. 단위 테스트는 생성자로 고정 시계를 넣을 수 있습니다. |

HTTP 테스트에서 얻은 201·200·409와 MySQL 한 행은 첫 행의 구성으로 관찰한 결과입니다. 테스트 컨텍스트를 늘리는 비용과 다른 업무가 같은 테스트 설정을 가져온 경우의 동작은 이 기록에서 측정하지 않았습니다.
