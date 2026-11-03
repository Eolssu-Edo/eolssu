# 4악기 카탈로그 운영 Flyway V17 적용

2026-11-03 12:08 KST, [S15P31E102-407](https://ssafy.atlassian.net/browse/S15P31E102-407). 사용자가407 MR 준비와 함께 운영 Flyway 적용을 요청했다. [V17 계약](../../specs/four-instrument-catalog.md)의 DB 변경만 먼저 적용했으며 앱 배포·MR 병합은 실행하지 않았다.

## 적용과 확인

- 적용 파일: `V17__four_instrument_guide_catalog.sql`, Flyway checksum `1603256994`.
- 실제 실행한 JAR 내부 SQL 바이트의 SHA-256: `e8b99f93823281d6428766225a7e8a12a9bdd40cd45fb4086a948bb9ec260b46`.
- 검증한 후보 JAR SHA-256: `618fbe95d02ec111f9ef2c135b5685cc71e79c9bf5e0a47a9edcd2e7c92ed2ed`. 운영 서비스 JAR로 교체하지 않고 일회성 db 프로필 컨테이너에서 Flyway target17만 실행했다.
- 기존 운영 앱: `05d1bb7600f2770fadf9fa348c5346046c5e3100`. 배포 journal·실제 JAR mount·해시 일치와 컨테이너 healthy를 확인했다.
- 공용 `/run/lock/eolssu-backend-deploy.lock` 안에서 V1~V16 성공·checksum, 대상 DB·네트워크·임시 컨테이너 미존재를 확인했다. 임시 테이블과 기존 비교 컬럼의 collation은 모두 `utf8mb4_0900_ai_ci`였다.
- 기존 백업 도구의 `send prechange`가 DB·설정 백업의 edo2 수신을 확인한 뒤 실행했다. run: `20261103T030730Z-d231badee6ab`, `20261103T030733Z-4df166f985c0`.
- Flyway 이력은 V1~V17 모두 성공이며 V17 checksum이 폐기 DB 검사와 같다. 실제 운영의 율명24개는 기존 행을 모두 보존하며36개로 늘었다.

| 코드 / 기존 ID | 이전 공개·음역 | 적용 후 공개·음역 | 실제 API 율명 수 |
| --- | --- | --- | --- |
| DANSO / 1 | 공개, [5,29] | 공개, [3,29] | 26 |
| SOGEUM / 2 | 비공개, [7,28] | 공개, [7,33] | 24 |
| JEONGAK_DAEGEUM / 3 | 비공개, [-5,24] | 공개, [-5,29] | 34 |
| SANJO_DAEGEUM / 4 | 비공개, [-5,22] | 공개, [-5,29] | 34 |

공개 HTTPS의 악기 목록과 네 음역 조회가 모두200이고 readiness는200/UP이다. DB의 기존 pitch24개 전체 값과 기존 악기의 ID·code·이름·허용 오차를 대조했다. 곡111개·모범 음원107개·녹음430개·역할별 음높이 관계315개의 전체 컬럼 행 해시는 변경 전후 동일하다. 운영 backend·frontend·MySQL의 ID·기동 시각과 MySQL mount도 그대로다. 작업 소유의 종료된 일회성 컨테이너만 제거했다.

운영 root 전용 증거는 `/opt/eolssu/ops407-v17-20261103/`의 `before.json`, `after.json`, `migration.log`, `receipt.json`, 사후 백업까지 확인한 `completion.json`과 백업 로그에 남겼다. 접속 비밀값·SQL 전체 백업은 저장소에 복사하지 않는다.

사후 `send db`를 배포 잠금 안에서 호출해 백업 도구의 동일 잠금 재획득이 timeout으로 끝났다. V17 적용과 readback은 이미 완료됐으므로 마이그레이션을 반복하지 않았다. 잠금을 놓은 뒤 백업 단계만 다시 실행해 run `20261103T031235Z-a2123a81f85e`의 edo2 수신을 확인했다(12:12 KST,431445바이트). 성공 증거는 `postchange-backup-retry.log`, 최초 실패는 `postchange-backup.log`에 구분해 보존했다.

## 후속 적용과 복구

V17은 운영에 적용됐으므로 파일명·내용·checksum을 바꾸거나 다른 업무의 V17로 교체하지 않는다. 후속409·410·411은407 이후의 미적용 번호로 정렬하고407을 포함한 마이그레이션 묶음으로 검증해야 한다. 다음 운영 db 프로필 작업 전에 V17 성공 이력을 재조회하며 `repair`로 맞추지 않는다.

가입·마이페이지의4종 선택과 대금 운지 코드 연결은407 앱 코드 배포가 남아 있다. DB 적용만으로 새 UI가 배포됐다고 보고하지 않는다.

문제가 발견되면 운영 적용 담당자가 저장된 사전 악기 행·현재 참조와 백업을 대조해 활성 상태·음역만 전진 수정하는 절차를 우선 검토한다. 새 pitch나 기존 곡·녹음·회원 참조, Flyway 이력을 임의 삭제하지 않는다. 전체 DB 복원은 서비스 쓰기 중지와 사후 변경 보존을 포함하는 별도 복구 작업이다. 앱 코드 되돌리기만으로 이번 데이터 변경은 되돌아가지 않는다.
