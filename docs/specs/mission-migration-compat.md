# V25 적용 전 호환 백엔드 릴리스

- 작업: S15P31E102-499 · Story S15P31E102-40 · Epic S15P31E102-21
- 근거: [공유된 배포 장애 대화](https://chatgpt.com/share/6aebe39d-7dec-83ec-99e8-553060f3814e), 원본 V25와 배포 도구.
- 공유 대화 시점 운영 SHA는 e335ac20891301867129e3a8e1662b833e7e2e83, 실패 후보는 c630fec40507f61fab5514d8faedacf64a1d5f47이다. 2026-11-05 11시대(KST) 읽기 전용 재조회에서도 e335ac JAR의 running/healthy를 확인했다. 운영 DB 이력·데이터·의존성은 미조회다.

## 동작과 보존 조건

운영 도구는 후보 검사 → DB 마이그레이션 → 백엔드 교체 순서다. 구 백엔드는 mission_id를 읽으므로 컬럼 삭제 전에 참조가 제거된 코드를 먼저 배포한다.

1차 호환 릴리스 A는 원본 V1~V25를 저장소와 테스트 classpath에 보존하고 실행 JAR에만 V1~V24를 싣는다. Mapper와 Row의 미션 참조 제거는 유지한다. V26 이상이 생기면 A 패키징을 거부한다. V25가 이미 적용된 환경에는 A를 배포하지 않는다.

공유 대화는 processResources 제외를 제안했다. 실제 구현은 bootJar 제외로 한정한다. databaseTest가 최종 스키마를 계속 검사하도록 하기 위함이다. 로컬 bootRun/db 프로필은 여전히 V25를 적용하므로 운영 호환 릴리스 검증은 반드시 실행 JAR로 한다.

A가 실제 운영에서 실행되고 세션 생성·조회가 정상임을 확인한 뒤에만 임시 bootJar 블록을 제거하는 후속 릴리스 B를 준비한다. 삭제 대상 데이터·의존성·백업을 확인하고 B JAR의 원본 V25 SHA-256을 승인한다. A와 B를 함께 반영하지 않는다.

V25 적용 뒤 A JAR은 migration 누락 검사로 정상 재배포할 수 없다. 복구 후보는 컬럼을 읽지 않는 코드와 원본 V25를 모두 포함해야 한다. 원본 SQL, Flyway 이력·checksum, 승인 검사·배포 순서는 변경하지 않는다.

## 완료 조건과 검증

- clean bootJar의 마이그레이션은 정확히 V1~V24이고 각 바이트가 원본과 같다.
- JAR의 PracticeSessionMapper.xml에 mission_id가 없고 Row에 missionId가 없다.
- build/resources/main에는 원본 V25가 남는다.
- 임시 V26을 넣으면 bootJar가 실패한다.
- 백엔드 test/build와 배포 마이그레이션 회귀 검사 통과. 운영 확인은 별도 증거 없이 완료로 보고하지 않는다.
