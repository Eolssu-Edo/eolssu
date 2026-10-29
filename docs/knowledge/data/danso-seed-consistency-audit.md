# 단소 4곡 공통 악보와 V6 시드 일치 검수

- 작업: [S15P31E102-281](https://ssafy.atlassian.net/browse/S15P31E102-281), Story 26 관련·Epic 15, 담당 우동균.
- 상태: 기계적 일치 검수 완료(2026-10-29). **Data 담당자의 변환 결과 최종 확인은 미확인**이다.
- 기준 커밋: `58d9fe95b4fa0fc69e5c899403080abee18bdc79`(origin/develop). 검수 중 제품 데이터·변환기·V6·운영 DB는 변경하지 않았다.

## 결론과 범위

V6의 4개 `score_json`은 현재 정답 JGB·검토 기록·reference를 읽어 `score_data@1`로 다시 만든 JSON과 전체 필드가 같다. 총 305 NOTE·11 REST·252정간·숨표 17개를 확인했다. NOTE 순서와 율/옥타브, 시김새 목록, 정간·음표 위치 식별자, 이벤트 시간과 이음·빈 정간의 반영도 재변환 결과와 같다. 이 결과는 **운영 적용에 사용된 저장소 V6의 일치**이며, 이번에 운영 DB를 읽어 현재 행의 변경 여부까지 확인한 것은 아니다.

기존 [154 정답 검수](danso-scoring-answers.md)와 각 `*.review.json`에는 사지한의 2026-10-27 원보 대조·승인이 남아 있다. 이번 검수는 그 승인된 정답에서 시드로 옮기는 과정의 일치 확인이며, 원본 PDF의 독립 재판독·음악적 타당성 승인·실연 인증을 대신하지 않는다. 기존 154·155·156·249 Task는 재개하지 않았다.

## 곡별 관찰

| DB ID | 곡 | 원본 쪽 | 장단 | NOTE | REST | 정간 | 이음 글자 `-` | 빈 정간 | 숨표 `<` | 총 박 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 아리랑 | p173 | 세마치 | 61 | 4 | 48 | 28 | 3 | 4 | 48 |
| 2 | 도라지타령 | p171 | 세마치 | 91 | 3 | 84 | 38 | 8 | 11 | 84 |
| 3 | 밀양아리랑 | p172 | 세마치 | 85 | 0 | 48 | 29 | 0 | 0 | 48 |
| 4 | 늴리리야 | p174 | 굿거리 | 68 | 4 | 72 | 2 | 14 | 2 | 24 |

- NOTE는 소리가 새로 시작하는 이벤트 수다. 이음·빈 정간을 새 음으로 세지 않는다. 정답의 명시적 율명 토큰을 독립적으로 펼친 순서와 305 NOTE의 율·옥타브 순서를 대조했다.
- 시김새는 4곡 정답과 4개 시드 모두 0개다. 따라서 이번 결과로 시김새가 있는 다른 곡의 변환을 검증했다고 볼 수 없다.
- 세마치 3곡은 정간당 1박, 늴리리야는 정간당 1/3박이다. 각각 review의 `beatsPerJeonggan`과 시드 `totalTick/ticksPerBeat`를 비교했다. 각/열 수를 장단 횟수나 음표 번호로 대신 쓰지 않는다.
- 모든 이벤트 길이는 양수이고 시작·끝이 연속이며, 총 길이는 정간 수×정간당 박과 같다. eventId 중복·없는 eventId/cellId·율명 표 밖 음이 없다.
- 숨표 4·11·0·2개는 정답과 이벤트에 보존된다. 실제 호흡에 따른 감점 정책은 이 검수 범위가 아니다.

## 메타데이터와 이력

- 4곡 제목·난이도·기본 BPM·악기·장단·출처·채점 가능 여부는 reference와 같다. 세마치 곡 BPM 96, 굿거리 곡 BPM 66은 서비스 설정이며 원보 빠르기 표기라는 뜻이 아니다.
- 악기 DANSO의 음역 중(0)~중(2), 허용 ±50cent·활성 값과 V5 pitch ID 1·11의 대응을 확인했다. 세 장단의 pattern·기본 BPM·bpm_unit·source도 reference와 같다.
- reference `DRAFT`→V6 `PUBLISHED`는 249의 사용자 결정으로 기록된 차이다. 오류로 분류하지 않는다.
- `score_version=1`, `description=NULL`, `umulsai_text=NULL`이다. 우물사이 원문 저장까지 끝났다는 뜻이 아니다. 설명·원문은 별도 근거와 범위가 필요하다.
- JGB 실제 바이트 SHA-256은 provenance와 같다. review의 PDF 해시는 catalog의 같은 원본 ID 해시와 같다. 원본 PDF 파일을 새로 읽어 해시를 다시 낸 것은 아니다.
- 아리랑은 저장소 고정 fixture와 정규 pretty JSON 바이트까지 같다. 나머지 3곡은 시드↔독립 재변환 JSON 전체 비교로 확인했으며 이번에 새 정답 fixture를 만들지는 않았다.
- 시드 생성 기준 `f891dfa` 이후 기준 커밋까지 정답 4곡·reference·변환기 변경은 없다(V6 추가 제외).

## 재현과 실제 결과

저장소 루트에서 아래 명령을 실행한다. 로컬 Python 3.12.13, jsonschema 4.26.0을 사용했다. 의존성은 기존 uv 캐시에서 읽었고 프로젝트 의존성 파일은 바꾸지 않았다.

```bash
uv run --offline --no-project --with jsonschema==4.26.0 python data/src/scores/verify_danso_seed.py
uv run --offline --no-project --with jsonschema==4.26.0 python -m unittest discover -s data/tests -p test_score_data.py -q
```

- 검수 스크립트: exit 0, `all assertions passed`. 4곡 전체 JSON 비교, JSON Schema, 변환 불변식·독립 율명 순서·메타데이터·이력 검사 통과.
- 기존 변환기 테스트: 27개, 실패 0·오류 0, exit 0.
- 음 수 오류 음성 대조: 임시 입력 사본에서 V6 아리랑 `note_count` 61만 62로 바꾸면 AssertionError·exit 1. 실제 저장소 V6는 그대로다.
- 스크립트의 SQLite `:memory:`는 V5/V6의 현재 INSERT 리터럴을 읽기 위한 격리 도구다. MySQL/Flyway 배포·제약·운영 상태 검증을 대체하지 않는다. 운영 주소·계정·네트워크 접근은 없다.

| 항목 | SHA-256 |
| --- | --- |
| V6 SQL | `4d64f1fa7a311ce52b1301b7513b9570d69f39519264ff26177b5f2a4964a2c7` |
| score_data.py | `e860a0ff45a287e68db27178ba6b200f3fc9313df2f14990da31ca46e0343173` |

공통 악보 SHA는 `score_data.dumps()`가 만든 UTF-8 pretty JSON 바이트 기준이며, SQL 안의 compact JSON 바이트 해시와 혼동하지 않는다.

| 원본 ID | 정답 JGB SHA-256 | 공통 악보 SHA-256 |
| --- | --- | --- |
| ngc-standard-2022-p173 | `b3e9d743df9cb5b1b64149378dcf14ba30e54387334080c5d9a5262db67a048d` | `51cd874652d705511cfaa0f2c03ba6d3632ae10b4a483036d6a438a8eac73f1b` |
| ngc-standard-2022-p171 | `85c38e15e55d3436ec061097e7cfeb6e2d0c125ae3e38f46323d3484f3153d34` | `0388505dedf429b080e7706f06caa15af001f37438db04fa02caca3fec24ff80` |
| ngc-standard-2022-p172 | `6cd7fdcb50f3fcc90371ec61692343436f26a90d0a3b09fd12466680949c6b75` | `01c57a27fe8e92e176d252ffdf0800614af708c92f356d3b57f60fb8143a5659` |
| ngc-standard-2022-p174 | `b1d60729a1d3d5af8aa44da40e29ffc6e96bf429e8c9ee0c86ccc655828e18e4` | `e43ad1bd7c32e397b7bd231aeadfdc03c854639a156b81432d8328c44138d3fe` |

## 발견한 문서 차이와 최종 확인 요청

1. 기존 `danso-scoring-answers.md`의 정답 파일 표는 도라지타령 빈 정간을 8개로 세지만, 아래 교정 설명은 이음표 6개를 비웠다고 쓴다. 현 `*.review.json` 승인 메모는 8칸의 이음표를 비웠다고 쓴다. **현재 JGB·시드가 빈 정간 8개로 일치하는 것은 확인했지만, 입력본 대비 실제 교정 수가 6인지 8인지 이번 비교만으로 확정하지 않았다.** 담당자가 기존 승인 기록의 표현을 확인해야 한다. 기존 검수 문서는 임의로 고치지 않았다.
2. Data 담당자 확인 요청 초안(미전송): “현재 정답 JGB에서 V6로의 재변환 일치는 4/4 확인했습니다. 도라지타령의 빈 정간 교정 수 6/8 표현 차이, 기존 검수에서 유보한 도라지타령 숨표 위치와 늴리리야 빈 정간의 음악적 해석을 확인 부탁드립니다. 변환 시김새는 4곡 모두 0개입니다.”
3. 음악적 해석·승인 범위를 넘어 자동 완료로 표시하지 않는다. 실제 변경이 필요하면 적용된 V6를 수정하지 않고 새 마이그레이션과 필요 시 score_version 변경을 BE·Data가 합의해야 한다.

## 근거 위치

- [정답 검수 근거](danso-scoring-answers.md), `data/annotations/scores/ngc-standard-2022-p171`~`p174`의 JGB·review
- [곡 기준값](../../../data/reference/songs.json), [장단 기준값](../../../data/reference/jangdans.json), [악기 기준값](../../../data/reference/instruments.json)
- [변환기](../../../data/src/scores/score_data.py), [재현 검수](../../../data/src/scores/verify_danso_seed.py), [Schema](../../../data/src/scores/score_data.schema.json)
- [원본 목록](../../../data/catalog/collected-scores/sources.json), [V6](../../../backend/src/main/resources/db/migration/V6__seed_danso_songs.sql), [249 운영 적용 기록](../../plans/S15P31E102-249.md)
