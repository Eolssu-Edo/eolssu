# 강원도아리랑·옹헤야 빠르기를 정간 단위로 다시 표기 (S15P31E102-1019)

## 결정과 이유

악보 빠르기 기준이 40~200이 되면서(S15P31E102-1018), 제공 곡 중 강원도아리랑(285)과 옹헤야(240)만 범위를 벗어났다. 두 곡은 정간 하나를 4분음표 1.5개(`ticksPerBeat` 4, `ticksPerJeonggan` 6)로 저장해서 숫자만 컸다. 실제 빠르기는 S15P31E102-728 감사에서 채택한 엇모리 ♪=190, 단모리 ♩=160이다([근거표](common/rhythm-metronome/score-tempo-audit.csv)).

정간 하나를 한 박으로 다시 표기했다(`bpmUnit` `JEONGGAN`, `ticksPerBeat` 6).

| 곡 | 이전 | 이후 | 실제 속도 |
| --- | --- | --- | --- |
| 강원도아리랑(5, 단소, 엇모리) | 285 QUARTER, tpb 4 | **190 JEONGGAN, tpb 6** | 285×4 = 190×6, 정간 0.316초 |
| 옹헤야(99, 소금, 단모리) | 240 QUARTER, tpb 4 | **160 JEONGGAN, tpb 6** | 240×4 = 160×6, 정간 0.375초 |

- 서버(`CommonScoreTimelineDecoder`)·앱·AI 채점 모두 시간을 `tick ÷ ticksPerBeat ÷ bpm`으로 계산한다. AI 채점은 받자마자 초로 바꾼다(`beat_s = 60 / bpm`). 그래서 음표 시각과 채점은 바뀌지 않는다.
- 연습 반주는 악보에 적힌 장단과 `ticksPerJeonggan`으로 주기를 정한다(엇모리 10정간, 단모리 4정간). 반주 주기도 그대로다.
- 연습 빠르기 0.6~1.2배를 곱해도 114~228, 96~192라 연주 평가 범위 20~240 안이다. 이전에는 강원도아리랑이 기본 빠르기부터 평가 범위(240)를 넘었다.

## 도구 변경

- `data/src/scores/prepare_tempo_corrections.py`: 결정에 `bpmUnit`이 있으면, 새 `ticksPerBeat`가 `ticksPerJeonggan`과 같을 때만 단위를 `JEONGGAN`으로 바꾼다. 묶음의 작업 ID와 음원 행 `bpm_unit`은 결정 파일·새 악보를 따른다.
- `infra/catalog/publish_score_tempo.py`: 근거 있는 박 표기 교정(`beatNotationCorrection`)과 함께일 때만, 한 박이 정간 하나가 되는 단위 변경을 허용한다. release 작업으로 `S15P31E102-1019`를 받는다.
- 결정 파일: `data/reference/score-tempo-1019.json`

## 모범 음원

728 때와 같이 생성기로 새 판본 음원을 다시 만들었다. 표현 입력은 운영 음원을 만든 339 묶음의 `expressions.common.json`을 그대로 썼다. 원본 악보 해시가 운영 `source_common_sha256`과 같은 것을 확인했다.

- 음표 시각: 88개·52개 구간 모두 0ms 차이. 길이는 25,814ms(이전 25,813ms), 12,550ms(같음)다.
- 지금 생성기로 이전 악보(285, QUARTER)를 다시 만들면 운영 음원과 샘플 단위로 같다. 생성기 자체는 바뀌지 않았다.
- 달라진 것은 표현이다. 생성기는 꾸밈음 길이와 숨 자리를 "한 박" 기준으로 정한다. 그래서 한 박이 정간 하나가 되자 강원도아리랑 꾸밈음이 약 13ms에서 20ms로 길어졌다. 옹헤야는 악구 끝 4곳의 숨이 약 44ms 길어졌다. 귀로 비교하지는 않았다.

## 운영 적용 (2026-11-16, edo1)

[728 절차](../../../infra/catalog/README.md#장단빠르기와-모범-음원-동시-교정-728)를 그대로 따랐다. 도구는 새 폴더 `/home/ubuntu/score-tempo-1019-tools`에 두었다.

1. 읽기 전용 스냅샷: 두 곡 모두 PUBLISHED·채점 가능, 활성 세션 0
2. 사전 검사 `1019-check.json`: validated-dry-run, pending [5, 99]
3. 업로드 `1019-upload.json`: 7개 객체, release SHA256 `775fb3fe473087fd1d00ac0379f986c49fe329c42a6b0c6b68b27032951bb136`
4. 적용 `publish_score_tempo_quiesced.py` → `1019-apply.json`: applied, updated [5, 99]. 세션 경로 차단 해제와 `active.conf` 유지 확인
5. 재조회 `1019-after.json`: pending 없음. 이후 `backup.py send db` 완료(run `20261116T070352Z-58615b50b04f`)

receipt의 `task` 표시는 도구 상수라 728로 찍힌다. release 자체의 작업 ID는 1019다.

적용 후 공개 API 확인:

- `/api/songs/{5,99}`: scoreVersion 2, defaultBpm 190·160, `music.bpmUnit` JEONGGAN, `ticksPerBeat` 6
- `/api/songs/{5,99}/model-audio`: scoreVersion 2, 190·160 JEONGGAN, 25,814ms·12,550ms
- `/api/songs/{5,99}/score`: 반주 cycleTicks 60·24(이전과 같음). 변경하지 않은 도라지타령(17)은 그대로다

## 남은 일

- 원본 PDF에 인쇄된 빠르기는 확인하지 않았다(728 감사에서도 "이번 시각 미검토"). 이번 변경은 실제 속도를 바꾸지 않으므로 PDF 확인 결과가 다르면 별도로 교정한다.
- `backend/src/main/resources/catalog/jangdan-playback.json`의 두 곡 행은 판본 1·285/240 기록으로 남아 있다. 두 곡은 악보에 장단 이름이 있어 이 검수 자료를 쓰지 않으므로 동작에는 영향이 없다.
- 이전 판본 음원 행과 객체, 완료된 연습 기록은 728과 같이 보존했다.
