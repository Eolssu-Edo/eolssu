# 비행기 56의 판본 보존 게시

게시기 소유: S15P31E102-745. 데이터 release: S15P31E102-707. 단소 132·소금 133·산조대금 134는 운영 v1·77 BPM CAT를 유지한다. 정악대금 56만 25음·음표 ID·tick·숨표를 유지하고 정간당 1박인 v2·77 BPM CAT로 게시한다. [동작·판본 계약](../../docs/specs/published-score-history.md)

2026-11-11 후속 선택은 네 악기 모두 `4박 기본 반주`(ID 12)의 `덩·덕·궁·덕`, 묶음 `[1,1,1,1]`이다. CAT 독주 음원·수행 시간표와 아래 불변 release는 그대로 사용한다. 이전 단모리 혼합 청취본이나 검증 영수증은 새 반주의 증거로 사용하지 않는다.

## 원본 MR !576에 기록된 생성물과 검증

아래는 원본 작업의 증적이다. 이번 코드 분리에서 운영 상태·객체를 다시 조회하거나 적용하지 않았으며, 적용 시점에는 다시 확인한다.

- 최종 생성 기준 `d2c96cfc`, 공통 악보 → MIDI → CAT. Git의 LF 규칙으로 정규화한 정악대금 v2 SHA-256 `29296c1a903566f46904a465f90a4c00ae21be61d03ec7dff4f5ece45cd2d4a7`, M4A `3cee9f208a1725af045a04e6054ae8915e2114832f2e130f9077f62851844118`. 줄바꿈 정규화 전후 악보 JSON 내용과 네 M4A 해시는 동일하다.
- 최종 불변 release SHA-256 `2ef33bb3384c2da06d9d53ad9fd97032e958a5ea76e91b3f0df85f8279863241`. 원본 source의 case `jeongak-daegeum-006-p1`을 유지한다. 카탈로그 ID 56을 case 번호로 대체하지 않는다.
- edo1 제한 경로 `/opt/eolssu/ops707-airplane-history-20261111/`에 `release-bundle-final`, `upload-final.json`, `staged-readback-final.json`, `preview-final-before-migration.json`이 있다. 사용자 녹음·계정 식별자는 복사하지 않았다. 초기 CRLF 초안의 release는 보존하지만 게시에는 이 최종 묶음을 사용한다.
- R2 업로드 4개(악보·M4A·수행 시간표·release)는 완료했다. 전체 GET SHA-256·크기·MIME·비만료와 M4A Range 206을 확인했다. 업로드만으로 공개 곡 행이 교체되지 않는다.
- 운영 사전 조회는 56 v1·90 BPM·완료 세션 3개·활성 0개이며 V37이었다. 게시 dry-run은 `pending=[56]`, `archiveMigrationReady=false`다. 적용 직전에 다시 조회해야 한다.
- 실제 격리 MySQL 8.4에서 운영과 같은 DDL로 v1 보관→v2 음원 삽입→현재 악보 갱신, 구판 세션·다른 세 곡 보존, 재실행, 다른 내용의 archive 충돌 시 전체 rollback을 확인했다.

## 순서

1. 사람이 **745 → 453 → 454 → 707** 순서와 707의 4박 기본 반주 후속 수정을 develop에 병합한다. 기존 관리 배포는 develop 이력의 SHA만 허용한다. 임의 feature SHA 배포나 보호 규칙 우회는 하지 않는다.
2. 같은 develop SHA의 backend 배포·Flyway V38을 확인한다. 새 결과 화면에는 수정된 Web 또는 앱 설치본이 필요하다. 기존 설치본이 서버 배포만으로 바뀌지는 않는다. Web/APK/IPA 게시 성공 여부와 기존 설치본의 전환 방안을 각각 확인한다. 새 앱 업로드만으로 이전 앱 사용자가 바뀌지는 않는다. 453의 판본 미지정 신규 요청 거부를 배포하고, v1 녹음 중 v2 게시 후 제출하는 경우에도 잘못된 v2 채점이 시작되지 않는지 검증한다.
3. 공개 `GET /api/songs/56?scoreVersion=1`과 `/score?scoreVersion=1`이 기존 악보를 반환하고, 잘못된 판본 400·없는 판본 404를 반환하는지 확인한다. 아직 현재 판본은 v1이어야 한다. 운영 카탈로그 ID 12의 이름·네 구음·묶음·QUARTER 단위를 확인하고, 배포한 서버가 다른 세 곡에 ID 12를 반환하는지 확인한다. 카탈로그 기본 160 BPM을 곡의 77 BPM으로 복사하지 않는다.
4. 아래 dry-run으로 원본·음원·다른 세 곡이 release의 before와 같은지 확인한다. 기존 receipt는 덮어쓰지 않고 새 파일명을 쓴다.

```sh
sudo python3 /opt/eolssu/ops707-airplane-history-20261111/publish_airplane_revision.py \
  --bundle /opt/eolssu/ops707-airplane-history-20261111/release-bundle-final \
  --receipt /opt/eolssu/ops707-airplane-history-20261111/preview-after-migration-final.json
```

5. 활성 RECORDING/PENDING이 0이고 구 설치본 전환 및 미제출 녹음의 판본 보호를 확인한 상태에서 아래 적용기를 실행한다. 기존 공통 배포 lock, Nginx 임시 gate, 이전 요청 drain, 변경 전 백업을 사용한다. 같은 `tempo-728-gate.conf`를 쓰므로 Task728과 동시에 실행할 수 없다. gate는 성공·실패 모두 finally에서 복구한다.

```sh
sudo python3 /opt/eolssu/ops707-airplane-history-20261111/publish_airplane_revision_quiesced.py \
  --bundle /opt/eolssu/ops707-airplane-history-20261111/release-bundle-final \
  --receipt /opt/eolssu/ops707-airplane-history-20261111/apply-final.json \
  --upload-receipt /opt/eolssu/ops707-airplane-history-20261111/upload-final.json
```

6. 현재 56 v2·77 BPM, 정확한 v1 조회·음표·BPM, 4박 기본 반주 12·24tick/4타점, 다른 세 곡의 같은 반주 12·4tick/4타점을 대조한다. 네 곡 모두 한 주기 4분음표 네 박·약 3.116883초다. 현재 모범 음원·수행 시간표의 판본과 전체/부분 재생 해시를 확인한다. 기존 세션의 판본·점수·녹음·음표 결과를 변경하지 않았는지 확인하고 사후 DB 백업을 수행한다.

7. 6번 확인과 사후 백업이 끝난 뒤 [곡 설명 정리 SQL](airplane-description-cleanup.sql)을 실행한다(S15P31E102-938). 단소 132·소금 133·산조대금 134의 곡 설명에 붙은 CAT 음원 고지를 빼고 `세 음으로 연주하는 입문곡이에요.`만 남긴다. 게시기는 세 곡의 설명까지 release의 before와 비교하므로 56 게시 전에 실행하면 게시가 `Other Airplane rows changed`로 멈춘다. SQL은 56이 v2가 아니거나 세 곡의 설명이 옛 문구·새 문구가 아니면 CHECK 위반으로 아무 행도 바꾸지 않는다. 마지막 SELECT의 `new_ok`가 세 행 모두 1인지, 공개 `GET /api/songs/132`·`133`·`134`의 `description`이 새 문구인지 확인한다. 고지는 앱 마이페이지 `자료 및 데이터 출처`와 음원 파일의 comment 메타데이터에 남는다.

DB 이름은 다른 게시 도구와 같이 활성 backend 컨테이너의 `DB_URL`에서 확인해 `DB`에 넣는다.

```sh
sudo docker exec -i eolssu-runtime-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --no-defaults --default-character-set=utf8mb4 --user=root --database="$0"' "$DB" \
  < infra/catalog/airplane-description-cleanup.sql
```

## 실패와 복구

- before가 달라졌거나 archive 내용이 충돌하면 적용하지 않는다. 정확한 생성물과 운영 상태를 재검토한다. 기존 receipt와 불변 객체를 보존하며 재생성으로 성공 결과를 덮지 않는다.
- SQL 실패는 단일 InnoDB 트랜잭션이 rollback한다. 결과가 불명확하면 재조회하고 성공한 단계를 반복하지 않는다.
- gate 복구 실패 시 receipt의 `gateFileExists`·inode·내용을 확인하고 이 작업이 소유한 임시 gate만 제거한 뒤 기존 문서의 `nginx -t`·reload·readiness·POST 복구 확인 절차를 따른다.
- 적용 후 구판으로 복구할 때 새 v2 세션이 있는지 먼저 확인한다. 있다면 v2도 같은 불변 표에 보관해 해당 결과를 보호한 뒤 v1을 현재로 복구한다. v1 archive·구판 음원·변경 전 백업과 모든 세션은 삭제하지 않는다. 자동 복구를 위해 세션 판본을 고쳐 쓰지 않는다.
