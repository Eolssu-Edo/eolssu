# 화면별 자료 출처를 마이페이지로 통합

- 작업: S15P31E102-550. 근거: 2026-11-07 사용자 요청(편곡·정간보 쉽게 읽기 화면 정리, 출처를 마이페이지 하단 푸터로 통합).
- 결과: 화면 본문의 출처 문구를 지우고 `feature/profile/DataSources.kt` 한 목록과 `values/data_sources.xml` 문구로 옮겼다. 마이페이지 푸터 → `자료 및 데이터 출처`(경로 `my/sources`)에서 본다.

## 옮긴 출처와 원래 위치

| 통합 항목 | 원래 화면·코드 | 링크 |
| --- | --- | --- |
| 국립국악원 국악기 디지털 음원 (공공누리 제1유형, 가공 내역) | 운지법 보기·기초부터 배우기 음 듣기 카드의 `출처·가공 정보`(`ReferenceAudioControls`), 연습 장단 패널의 `jangdan_source` | 원본 `https://www.gugak.go.kr/digitaleum/front/monotone/list.do`, 이용 조건 `https://www.kogl.or.kr/info/license.do` |
| 우물사이 jeongganbo-generator (MIT) | 쉽게 읽기 4번 시김새·더 많은 시김새 카드의 `guide_sigimsae_credit`, `SampleSongs.kt` 주석(예시 곡 정간 수·대강 분절) | `https://github.com/danbinaerinHan/jeongganbo-generator` |
| 국립국악원 e국악아카데미 | 단소 운지표 출처(`GuideInstrument.source`), 기초부터 배우기 목록 `learn_list_source` | 없음 |
| 국립국악원 『창작을 위한 국악기 이해와 활용1』 | 소금 운지표 출처 | 없음 |
| 국립국악원 『2021 대금정악보』 (공공누리 제4유형) | 정악대금·산조대금 운지표 출처 | 없음 |

- 같은 국악원 음원 페이지를 쓰던 음 듣기와 장단 소리는 한 항목으로 합쳤다. 음 듣기 문구는 `국악기 디지털 음원`, 장단 `ASSET_LICENSE.md`는 `국악 디지털 음원`으로 이름이 달라 앞의 화면 문구를 따랐다.
- 링크는 앱 코드·리소스에 있던 주소만 넣었다. 운지표 3종과 e국악아카데미는 앱에 주소가 없어 `원본 링크가 아직 등록되지 않은 자료`로 표시한다. `docs/knowledge/data/`의 조사 문서에는 국악원 주소가 여럿 있으나 어느 자료와 1:1인지 확인하지 않아 넣지 않았다.
- 기초부터 배우기 단계 상세의 `근거: …` 줄(서버 LRN-02 `source`, 예: `단소 지도서 발음법 3항, 소금 교실 5~7차시`)은 화면에서 뺐다. 값은 서버 `V14__learning_tracks_and_progress.sql`과 응답 DTO에 그대로 남는다.

## 그대로 둔 고지

- 라이선스 전문은 위치를 바꾸지 않았다: `composeResources/files/licenses/`(시김새 그림 MIT, 글꼴 OFL 4종), `files/jangdan/ASSET_LICENSE.md`·`manifest.json`.
- 공공누리 제1유형은 출처와 변경 사실 표시를 요구하지만 표시 위치를 정하지 않는다고 보고, 출처 화면에 가공 내역을 함께 적었다. 팀·법무 확인은 하지 않았다.
- 『2021 대금정악보』는 공공누리 제4유형(변경금지)이다. 안공표를 앱 운지 그림으로 다시 그린 것이 변경에 해당하는지는 이번 작업에서 판단하지 않았다.

## CAT 음원 고지 추가 (938)

- [S15P31E102-938](https://ssafy.atlassian.net/browse/S15P31E102-938): 곡 모범 연주와 내 악보 `음원 만들기`는 서울예술과학센터 CAT 국악 가상악기 라이브러리로 합성한다. [CAT 이용 조사](../data/fingering-reference-audio.md)는 2차 저작물에 지정 문구 표시를 요구한다고 적었지만 출처 화면에는 항목이 없었다.
- 운영 DB의 비행기 단소 132·소금 133·산조대금 134만 곡 설명에 이 문구를 붙여 곡 정보 화면에 그대로 보였다(2026-11-15 공개 API 108곡 조회). 저장소의 시드·마이그레이션·적재 도구에는 이 설명이 없다.
- 지정 문구는 `data/src/playback/sampler.py`의 `CREDIT`과 같게 출처 화면 비고에 넣고, 링크는 조사 문서에서 확인한 `https://www.catseoul.com/gugak-library/`를 쓴다. 음원 파일 comment 메타데이터의 고지는 그대로 둔다.
- 세 곡 설명 정리는 56 판본 게시 뒤 [정리 SQL](../../../infra/catalog/airplane-description-cleanup.sql)로 한다. 순서는 [비행기 게시 문서](../../../infra/catalog/airplane-revision.md) 7번을 따른다.

## 함께 지운 화면 요소

- 쉽게 읽기 `장단·기호 자료` 버튼·창(`ReadingCatalogDialog`)과 `ReadingCatalogViewModel`·DI·`toggleJangdanAudio`. 서버 장단·기호 조회(`ReferenceCatalogRepository.jangdans/jangdan/symbols`)와 연습 장단 재생은 다른 테스트·화면이 쓰므로 남겼다.
- 음 듣기 카드의 `출처·가공 정보` 펼침에만 있던 안내 `음마다 기본음과 요성 연주 예시를 들어 보세요. 연주 예시는 채점 기준음과 다를 수 있어요.`도 함께 빠졌다.
- 편곡(악보 편집) 자판의 `↑ 이전 정간`·`다음 정간 ↓`와 `previousCell`·`nextCell`·`moveSelection`. 정간은 악보를 눌러 고르고, 입력 후 다음 정간으로 넘어가는 동작은 그대로다.
- 쉽게 읽기 휴대폰 카드의 제목 줄. 목차 단추가 같은 제목을 보여 준다. 목차가 없는 태블릿 격자는 카드 제목을 유지한다.

## 검증

- 2026-11-07 Windows: `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`(995개, 실패 0, skip 4), `:shared:compileKotlinWasmJs` 통과. `DataSourcesTest`가 항목·링크 중복과 원래 주소 보존을 검사한다.
- 미검증: iOS 컴파일·실행, Android·iOS 실기기의 외부 링크 열기, 태블릿 실기기 배치.
