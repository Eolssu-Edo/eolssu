# 내장 운지 자료와 CAT-07 연결

- 작업: S15P31E102-409. 2026-11-03 사용자 승인으로 로컬 구현.
- 기준: develop `6899998b`의 V17·4악기 공개·이체자 대응. 실제 계약은 [스펙](../../specs/builtin-fingering-catalog.md), 저장 정본은 [V18](../../../backend/src/main/resources/db/migration/V18__builtin_fingering_patterns.sql).
- 구현 단계 기록: 로컬 구현·검증·자체 점검 후 커밋했으며 당시 Push·MR·운영 적용·독립 리뷰는 미실행이었다. 후속 MR 준비의 독립 검토는 문서 끝에 기록한다.

## 원본과 보존

[FingeringTables.kt](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/FingeringTables.kt)의 단소12·소금20·대금30행을 사용한다. [Fingering.kt](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/Fingering.kt)는 산조도 같은30행을 쓰되 출처·설명과 명목상 황종을 구분한다. 총92행이며 새 운지를 추정하지 않았다.

API 이미지 URL만으로는 취법과 구멍 상태를 대조할 수 없어 nullable pattern을 추가했다. 원본의 구멍 순서·반 막음·미확인 상태·기본음 여부·한자·읽는 법·출처·악기 설명을 보존한다. 구멍 상태는 CLOSED/OPEN/HALF/UNKNOWN이다. 단소는 뒷구멍·앞1~4공, 나머지는1~6공 순서다. 단소 청태의10100과 대금·단소의 반 막음을 원본대로 유지한다.

FK는 [YulNotation.kt](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/YulNotation.kt)의 율명·옥타브로 연결한다. 원본 한자를 서버 표기로 덮어쓰지 않는다. 산조의 F와 다른 악기의 E♭는 명목상 참고 기준이며 실측 주파수나 채점 정책이 아니다. BUILTIN_REFERENCE는 자료의 출처 유형이고 전문가 검수/권리 승인/채점 승인으로 해석하지 않는다.

## 검사와 재현

Windows·JDK21·MySQL8.4의409 전용 폐기 컨테이너에서 실행했다. 다른 Task와 운영 DB를 사용하지 않았다. backend의 실제 Gradle 명령과 TEST_DB_* 설정은 [실행 안내](../../../backend/README.md#실제-mysql-통합-검사)를 따른다.

| 검사 | 확인할 동작 |
| --- | --- |
| `BuiltinFingeringApiDatabaseTests` | FE 소스를 SQL과 독립적으로 읽어92행의 모든 필드·FK·취법·구멍·출처·정렬·개별 pitchId 필터를 실제 공개 HTTP와 대조 |
| `CatalogReferenceApiDatabaseTests` | 이미지형 기존 자료와 null 설명, 비활성404·잘못된 ID400·빈 목록, UNKNOWN/HALF 보존, 손상 pattern의 비공개500 |
| `ReferenceCatalogRepositoryTest` | nullable pattern 역직렬화·기존 이미지형 응답, 미확인/반 막음 보존, 잘못된 필드·상태·구멍 수 거절 |
| 폐기 DB 업그레이드 | V1~V17에 기존 이미지형 운지를 넣은 뒤 V18 실행, ID·URL·설명 보존과92행 연결 |
| 제약/사전 확인 | 내용 없는 행·배열 pattern·중복 FK 쌍·고아 FK 거절, 기준 pitch 하나 누락 시 새 영구 열 생성 전에 실패 |

관련 DB HTTP와 FE DTO 검사는 수정 전 누락/소실 실패를 확인한 뒤 통과했다. MyBatis의 기본 인자 생성자가 자동 매핑과 충돌한 문제는 Row 기본 인자를 제거해 수정했다. 기존 Mapper fixture는 트랜잭션 안에서 시드 운지를 치우고 고정 이미지형 행을 넣도록 조정하여 테스트 롤백 뒤 참고 자료가 복구된다.

최종 결과는 backend `clean build`의 단위534개 통과, `databaseTest`210개 중209개 통과·1개 skipped, FE `:shared:testAndroidHostTest`650개 중647개 통과·3개 skipped다. 추가 OpenAPI 검사는 imageUrl/pattern의 nullable 표시와 취법·구멍 enum을 실제 문서 HTTP로 확인했다. FE 최종 복구 검사는 동일 코드에 대한 Gradle 캐시 결과를 재사용했다.

전체 DB 검사 첫 시도는 기본 연결 풀이 여러 Spring context에 누적되어 MySQL 한도151개를 넘었다(Max_used_connections152, 실제 Too many connections). 해당 시도를 중단하고 테스트 프로세스 환경에만 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`·`SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0`을 지정해 전체 DB 검사를 통과했다. 제품 풀 설정·운영 DB는 바꾸지 않았다.

FE에서 pattern을 버리는 임시 변형이 새 두 검사를 실패시키는 것을 확인한 뒤 원본을 복구했다. 서버의 pattern 소실·OpenAPI 누락 임시 변형에서는 원본92행·UNKNOWN/HALF·OpenAPI의 새3검사가 실패했다. 변형은 모두 복구했다. 로컬 실행 로그와 업그레이드 확인 스크립트는 `output/fingering-409/`에 보존하며 커밋하지 않는다.

## 적용 경계와 남은 확인

FE는 카탈로그 자료를 받아 보존하고, 운지 화면의 기존 내장 표·그림은 유지한다. 이번 검사로 Android/iOS 실기기의 화면·소리·전문가 운지 검수를 완료했다고 주장하지 않는다. iOS 컴파일은 Windows에서 실행하지 않았다.

V18은 이전 이미지 필수 서버로의 코드 롤백과 호환되지 않을 수 있다. 운영 적용은 별도 절차이며 적용 전 구버전 null 처리 호환성·DB 백업·대상 번호를 확인해야 한다. 병행 브랜치의 Flyway 번호는 통합 전 중복을 정리한다. 이미 적용된 마이그레이션 checksum을 고치지 않는다.

원본이 바뀌면 과거 V18을 수정하지 않고 새 마이그레이션과 전체 대조 검사를 추가한다.

## MR 준비 독립 검토

2026-11-03 최신 develop의408 병합 커밋7c57baa8을 통합한 뒤 API/FE 계약과 DB 마이그레이션·호환성을 각각 읽기 전용으로 독립 검토했다. 차단할 코드 결함은 발견되지 않았다. 기존 이미지형 자료의 pattern이 항상 null인 것처럼 읽히던 문구는, 내장표와 연결된 행에는 기존 이미지·설명과 pattern을 함께 반환한다는 실제 동작으로 정렬했다. 데이터나 응답 동작은 바꾸지 않았다. README 충돌은408·409 설명을 모두 보존했다.
