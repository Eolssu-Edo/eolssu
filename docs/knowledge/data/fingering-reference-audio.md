# 운지 패널 역취 예시음 제작 (603)

[S15P31E102-603](https://ssafy.atlassian.net/browse/S15P31E102-603)은 추가한 역취 운지의 기본음과 요성 예시를 준비한다. 두 묶음의 역할을 구분한다. CAT 14개는 로컬 미리듣기 자료다. 운영용은 국립국악원 음원을 가공한 단소 10개와 산조대금 2개, 기존 소금 녹음 2개를 사용한다.

| 악기 | 율명 | 예시 음높이 | 운영용 가공에 사용한 기존 자료 |
| --- | --- | --- | --- |
| 단소 | 청협 | F♯5 (MIDI 78) | `ngc-danso-2338.tone-005` 평취 태 |
| 단소 | 청고 | G5 (79) | `ngc-danso-2341.tone-001` 역취 중 |
| 단소 | 청남 | C6 (84) | `ngc-danso-2341.tone-003` 역취 무 |
| 단소 | 중청협 | F♯6 (90) | `ngc-danso-2341.tone-005` 역취 태 |
| 단소 | 중청중 | G♯6 (92) | `ngc-danso-2341.tone-005` 역취 태 |
| 소금 | 중청태 | F6 (89) | `ngc-sogeum-2654.group-013`, `ngc-sogeum-2657.group-011` 원음 재생 |
| 산조대금 | 중청태 | G6 (91) | `sanjo.yeokchwi.mu` 역취 무 |

단소·소금은 황종을 E♭4로 잡았다. 산조대금은 [운지 안내의 F](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/Fingering.kt)를 F4로 잡은 단독 예시다. 곡별 악보의 조율은 해당 악보의 `hwangHz`를 따른다. 산조대금 예시의 G6는 실물 악기마다 정해진 주파수나 채점 기준이 아니다.

소금의 공개 `역취 태` 기본음·요성 강 녹음은 카탈로그 실측값이 각각 1438.998Hz, 1430.949Hz로 F6 음역이다. 운지 화면의 `중청태`는 기존 `태` 별칭을 쓴다. [기본음 상세](https://k15e102.p.ssafy.io/api/reference-audios/191a33213a5ad02abb36452e297cd3a65979381c332a22217f66982c924cda07) · [요성 상세](https://k15e102.p.ssafy.io/api/reference-audios/de2fdf7ceafe1a80a9f267d1d68fe1eb5f5f97c4cf94a0675c70531ce541c62c).

## CAT 로컬 미리듣기

각 단음을 공통 악보로 직접 작성해 기존 MIDI/CAT 파이프라인으로 렌더한다. 파일마다 3초 음을 적고 파이프라인의 MIDI 끝 여백이 추가된다. 기본음에서 단소의 자동 진동을 끄고, 요성에만 [교육용 요성표 정책](common-score-expression-preview.md)을 적용한다. 기존 악보 재생은 기본 자동 진동을 그대로 사용한다. 단소 MIDI 92 기본음의 첫 렌더가 51.8센트 높게 측정되어 해당 CAT 음역에만 -49센트를 적용했다. CAT 샘플을 렌더한 음이며 원음의 운지나 요성 주법을 검수한 연주는 아니다.

CAT 공식 안내는 개인 비상업 이용과 2차 저작물의 지정 문구 표시를 요구한다. 팀 운영 앱의 음원 제공 권한은 확인되지 않았다. 로컬 M4A의 comment 태그와 별도 `CAT-attribution.txt`에 지정 문구를 넣는다. [CAT 국악 라이브러리](https://www.catseoul.com/gugak-library/).

## 운영용 음높이 가공

기존 카탈로그에서 같은 악기의 가까운 기본음을 고른다. 원본 M4A의 해시와 자산 상세의 `KOGL-1`을 확인한다. 구간 선택, 음높이 변환, 레벨·페이드 처리, PCM16 및 AAC-LC 변환을 하고 요성에만 4Hz, 최대 ±50센트 변화를 추가한다. 국립국악원 원본 출처와 가공 내역을 M4A의 comment, 카탈로그 `source`·`derivative`에 기록한다. 예시는 3초이며 `기본음계`·`역취`, `기본음 · 음높이 가공` 또는 `요성 · 음높이 가공`, `inferred`, 채점 사용 불가로 등록한다. 운지 패널에서 출처와 가공 사실을 확인할 수 있어야 한다. [국립국악원 국악기 디지털 음원](https://www.gugak.go.kr/digitaleum/front/monotone/list.do) · [공공누리 제1유형 안내](https://www.kogl.or.kr/info/introduce.do).

12개 AAC-LC M4A 전체 디코딩 결과의 목표 대비 중앙 음고 오차는 최대 1.11센트였다. 기본음의 4Hz 성분은 최대 0.56센트, 요성의 4Hz 진폭은 48.47~49.10센트였다. 실제 목표음 연주의 운지·음색을 검수하는 대신 쓸 수는 없다. 가공 전후 피치 이동은 -1.01~+3.27반음이다. [기존 적재 절차](../../../infra/catalog/README.md)는 같은 악기·라벨의 다른 파일을 덮어쓰지 않고 기본 DRAFT로 적재한다.

## 재현

기존 재생 도구의 [격리 Python 환경과 FFmpeg](../../../data/src/playback/README.md#설치와-빠른-검사)를 준비하고 저장소 루트에서 실행한다.

```powershell
python -m data.src.playback.fingering_reference --assets assets.json --out data/processed/playback/fingering-reference-603
python -m data.src.playback.fingering_derivatives --sources data/processed/playback/603-ngc-input --out data/processed/playback/603-kogl
python -m infra.catalog.import_reference_audio --manifest data/processed/playback/603-kogl/manifest.json --output data/processed/playback/603-kogl/import-draft.sql
```

두 번째 명령의 입력은 `fingering_derivatives.py`에 고정한 기존 음원 ID의 `/api/reference-audios/{assetId}` 결과를 `<labelId>.json`으로, `/api/reference-audios/{assetId}/play`의 음원을 `<labelId>.m4a`로 보관한 폴더다. 해시 불일치, 출처 누락, 디코딩·음고 검사 실패 시 게시용 manifest를 생성하지 않는다. 원본, 합성 결과, 게시 후보 파일은 ignored 경로에 둔다.
