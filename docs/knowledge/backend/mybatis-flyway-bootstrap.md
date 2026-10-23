# MyBatis·Flyway 초기 구성

- 관련 Task: [S15P31E102-74](https://ssafy.atlassian.net/browse/S15P31E102-74)
- 기준: [정본 V1과 ERD](../../contracts/db-schema.md), Spring Boot 4.1.1, Kotlin 2.3.21, JDK 21, MySQL 8.4
- 선택 근거: 2026-10-22 사용자가 이 작업의 채팅에서 MyBatis 채택을 명시했다. JPA와의 일반적인 우열을 이번 결정의 근거로 삼지 않는다.

## 확인한 호환성과 적용점

- [MyBatis Starter 공식 문서](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)의 호환 표에 따라 Boot 4에는 Starter 4.0.0을 사용한다. `@Mapper` 스캔과 `mapper-locations`로 XML을 연결한다.
- [Spring Boot 초기화 문서](https://docs.spring.io/spring-boot/how-to/data-initialization.html)에 따라 `spring-boot-starter-flyway`와 MySQL 전용 `flyway-mysql`을 사용한다. Flyway와 Connector/J 버전은 Boot BOM에 맡긴다. 스키마 초기화는 Flyway 한 경로로 실행한다.
- Kotlin 불변 Row 생성자에 열 이름을 대응시키므로 Java 파라미터 이름 보존과 MyBatis의 이름 기반 생성자 매핑을 함께 설정한다. 열 순서에만 의존하지 않는다.
- JSON은 저장소에서 문자열로만 운반한다. 상태 전이·JSON 구조·공개 필터·재채점 정책은 아직 이 저장소 계층이 결정하지 않는다.

## 기동 경계

기본 프로필은 DataSource 자동 구성을 제외한다. `db` 프로필은 이 제외를 해제하고 명시적인 `DB_*`로 연결한다. 이는 현재 서버의 앱에 DB 접속 정보가 없고, 기존 배포가 무DB HTTP 기동을 검사하기 때문이다. 업무 API를 연결할 때 배포의 프로필·접속 정보·실제 DB 스키마를 함께 준비해야 한다.

기존 V1을 그대로 사용하고 baseline은 자동 실행하지 않는다. 빈 DB에는 V1이 적용되며, 이력이 없는 기존 DB를 자동으로 채택하지 않는다. Flyway clean은 금지한다. 실제 운영 DB나 집계 DB에는 이 작업에서 접속하거나 DDL을 적용하지 않는다.

## 검증과 한계

재현 명령과 준비 조건은 [백엔드 README](../../../backend/README.md#실제-mysql-통합-검사), 실행 결과는 [작업 기록](../../plans/S15P31E102-74.md)에 둔다. 기본 HTTP 기동과 실제 MySQL 검사를 분리한다. 2026-10-22 이 작업의 macOS/JDK 21·MySQL 8.4.9에서 기본 1개·통합 3개 검사가 통과했다. 실제 JAR의 DB 환경변수 기동과 Flyway의 checksum 불일치·이력 없는 기존 스키마 거부도 확인했다. 통합 검사의 고정 데이터는 롤백되며 V1 DDL과 이력은 테스트 DB에 남는다.

이 구성은 12개 테이블의 PK 조회와 매핑을 제공한다. 업무 CRUD·상태 전이·권한·AI 호출·운영 배포는 검증 범위가 아니다. ERD 문서의 재채점 등 미결 정책은 유지한다.
