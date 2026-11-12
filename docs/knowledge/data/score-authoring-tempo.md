# 악보 작성 빠르기와 수행 시간표 (773)

2026-11-11, S15P31E102-773. [상위 스펙](../../specs/score-authoring-consistency.md), [작업 계획](../../plans/S15P31E102-773.md).

2026-11-12 사용자 요청으로 생성기·기술 사전검사는773, API·저장/초안 보존은790, UI·기기 반주는791에 분리했다. 아래 API/FE/통합 검사는 분할 전 구현의 근거이며 새 분할 snapshot의 통과를 대신하지 않는다. 현재773의 검사·실제 생성 결과는 작업 계획에 별도로 기록한다. 정책과 기존 산출물은 바꾸지 않았다.

## 확정 계약

사용자가 기본 BPM을 바꾸면 숫자 구간 BPM 전체도 같은 비율로 바꾼다. 100/80 → 기본120이면120/96이다. 서버 확정 저장이 한 번 적용하고, 저장 응답의 companion을 다음 편집에 사용한다. 자동 저장 초안에는 기준 companion을 그대로 보관하여 반복 자동 저장으로 배율이 누적되지 않는다. 음원만 다른 속도로 요청할 때는 저장한 기준 BPM 대비 비율을 불변 생성 스냅샷에 적용한다.

공통 악보 v1에 임의 필드를 추가하지 않고 기존 `common-score-expressions/v1`을 선택적 `expressions` 형제로 전달한다. SHT-04·11 입력, SHT-08·초안·SHT-04 결과, 내부 estimate/render가 같은 값을 쓴다. V43의 nullable `user_sheets.expressions_json`에 보관한다. 입력 생략/null은 기존 companion 유지이며 제거 요청이 아니다. 기존 악보·공개판본에 누락된 숫자 수행 계획을 추정해 만들지 않는다. 현재 앱은 숫자 구간 자체를 새로 그리는 편집기를 제공하지 않는다.

숫자 계획의 위치가 있는 악보는 칸 추가·삭제·분할·합침을 편집 상태에서 막고 한 줄로 알린다. 서버도 단위·tick, 칸/소박 ID와 배치, 이벤트 ID/시작/길이가 바뀌면 `PLAN_POSITIONS_CHANGED`로 거절하고 되돌리기를 안내한다. 같은 자리의 음고·쉼표 전환·시김새·악기·판본·기본 BPM 변경은 허용한다. 계획을 조용히 버리거나 다른 선율 위치에 재연결하지 않는다.

`sourceSha256`와 `performancePlan.input_sha256`는 현재 악보와 결합한다. 서버가 scoreVersion/provenance를 준비한 뒤 두 해시를 다시 결합한다. MySQL JSON의 키 순서 정규화에도 결합을 유지하도록 서버 companion 해시는 객체 키를 재귀 정렬한 UTF-8 JSON을 기준으로 한다. 내부 HTTP는 같은 source header를 estimate와 render에 보낸다. DATA는 입력 결합을 확인한 후 합성용 악기 사본/실제 입력 파일 해시에 재결합한다. CLI의 원본 바이트 SHA 계약과 기존 companion 없는 HTTP 결과는 유지한다.

## 제한 근거

UI·서버·HTTP의30~160, JGB 어댑터의1~800에는 음악적 근거가 없었다. 작성 UI·DB는 양의 정수 표현 범위를 받고 저장과 생성 전에 실제 수행 계획을 컴파일한다. `music.bpmUnit`을 따른다. 같은160이라도 DOTTED_QUARTER는 QUARTER240과 같다.

가져오기 요청과 변환 큐의 BPM도 같은 양의 INT이며, 최종 생성 가능 여부는 악보가 구성된 뒤 검사한다. 생성된 음원의 기기 저장 메타데이터도 양의 BPM을 보존한다. 제공 곡·장단 카탈로그의 기존30~400 계약은 이번 작성 변경에 끌어들이지 않고, `decodeAuthoring` 경로로 작성 악보의 저장/조회/생성을 분리한다.

최종 생성 조건은 다음과 같다.

- MIDI Set Tempo1~16,777,215µs/quarter. 정수 입력의 표현 경계는 QUARTER4~60,000,000, DOTTED_QUARTER3~40,000,000이다. 이 넓은 숫자는 연주 권장 범위가 아니며 이후 음표·길이 검증도 통과해야 한다.
- 붙임·꾸밈·숨·길이표·구간 변속·반복을 처리한 실제 발음마다44,100Hz에서 최소2프레임.0프레임은 샘플러 경계 실패,1프레임은 attack이0인 문제를 방지한다. 오류는 `TEMPO_UNRENDERABLE`과 단위·사유·음표ID·조정 경계를 반환한다. 이는 소리가 표본으로 표현되는 최소 조건이며 실물 연주 가능성이나 청감 품질 보증이 아니다.
- MIDI 꼬리/릴리스 포함600초. 기존15MiB·1GiB·90초 생성 제한은 [기존 실측](../../specs/sheet-audio.md)에 따른 자원 제한으로 유지한다. 크기·시간은 실제 생성 단계도 검사한다. 길이 초과는 `EXPORT_TOO_LONG`과 필요한 최소 BPM을 반환한다.

확정 저장은 내부 실제 preflight 응답 뒤에만 행을 변경한다. 생성기 불가/시간 초과면503, 표현 불가면400이며 초안은 계속 저장할 수 있다. 실제 생성에서도 같은 preflight를 실행한다. UI는 빠르기 옆에♩/♩.를 표시하고 실패한 때만 빠르게/느리게 조절할 한 줄을 보인다. 수치 계획/반복·길이 변경을 무시한 고정 BPM 길이를 정확한 예상 길이로 표시하지 않는다.

## 장구와 선율의 공통 시계

기보 quarter 위치→길이표 반영 위치→구간 BPM 역수 적분→반복 회차→MIDI tick→초 변환을 `PerformanceClock` 한 경로로 공유한다. 가속/감속 내부 타점도 선율 노트 끝 사이를 선형 보간하지 않는다. 반주는0tick부터 시작하고 첫 REST는 선율 진입만 늦춘다. 772 카탈로그의 `patternCellEighths`가 구음 칸 길이를 정하며 이름으로 길이를 추측하지 않는다. 완전한 장단 주기만 허용한다. 임의 오프셋·못갖춘마디 일반 지원은 추가하지 않았다.

## 회귀와 실제 실행 증거

### 내 악보의 선율 전용 다운로드와 로컬 장구

내 악보는 `SheetPracticeRepository.download`가 `withJangdan=false`로 내려받은 M4A에 기기 장구를 더한다. 서버 합성 타점만 맞추어도 이 경로가 고정 BPM 시계를 쓰면60→30 구간에서0/1/2/4초가0/1/2/3/4/5초로 달라진다. 로컬 장구·박 표시는 수행표의 위치를 사용하고 탐색도 같은 곡선의 역함수를 쓴다. [출력 계약](../../specs/common-score-playback.md#작성-악보의-로컬-반주-시계-773)의 선택적 `tempoRatio`와 반주 tick 쌍으로 점진 변화와 균형 길이표를 처리한다.

일반 길이표/늘임은 `common_preview._stretch_lengths`가 수행 span만 늘리고 기보 좌표는 보존한다. 반면 가져온 `sourceSlots.lengthMode=written`은 `compile_slots`가 기보 span 자체에 균형 길이를 반영한다. 따라서 균등한 표시 tick을 반주 축으로 오인하면 기본60에서도 첫 반박이500ms 대신750ms에 울릴 수 있다. `expand_locations`는 tempo 적용 뒤의 chunk 시간을 원래 slot의 기보 축으로 역투영한다. 원본 표시 tick·이벤트는 바꾸지 않는다.

고정 자료는 `frontend/shared/src/androidHostTest/resources/scores/personal-jangdan/`의6개 JSON이다. 실제 CAT 단소·국립국악원 장구·FFmpeg로 선율 전용/장구 포함 각각 생성한 수행표·원본 JSON/SHA·M4A SHA/길이와 MIDI 채널10 타점을 저장했다. step6550ms, repeat12550ms, ramp3323ms, dotted6550ms, weighted4550ms, weighted-ramp3323ms이다. step의0/1000/2000/4000ms, ramp의0/892.569444/1273.819444/1621.875/1942.013889/2238.472222ms를 앱의 실제 다운로드→기기 메타데이터 재조회→수행표 해석→`JangdanCursor` 경로와 비교한다. weighted 두번째 정간의 반박도 같은 MIDI 타점을 유지한다. 기기 저장/전송만 테스트 대역이며 CAT 출력은 대역이 아니다. 1ms 폴링 및 MIDI 반올림을 포함해1.1ms 허용치를 사용한다.

재생성은 저장소 루트에서 기존 `PLAYBACK_ASSETS_JSON`, `PLAYBACK_JANGGU_SAMPLES`, `FFMPEG_BINARY`, `FFPROBE_BINARY`를 지정하고 `python -m data.src.playback.tests.generate_local_jangdan_fixtures`를 실행한다. 뒤에 case 이름을 주면 해당 자료만 만든다. 원본 음원과 MIDI는 Git 제외 `.scratch/local-jangdan/`에 보존한다. 점4분 단위의1000.0005ms 경계에서 허용 오차 때문에 같은 타점을 두 번 치던 문제도 발견해 조회 하한과 상한의 허용 오차를 일치시켰다. 첫0tick·반복0tick·탐색 뒤 첫 타점을 함께 검사한다. 이 증거는 기기 스피커 청취·연주자 실물 검수가 아니다.

### 앞선 서버·생성기 검증

- 작은4박 회귀에서 기존 장구0/1000/2000/3000ms와 조금느리게 선율0/1000/2250/3250ms의 차이를 먼저 재현했다. 수정 후 타점이 일치하고 끝의 불필요한 추가 타점이 없다. 점진 가속 내부 반박, 반복, 첫 쉼도 검사한다.
- Python 전체257개, 실제 FFmpeg 인코딩/디코딩 포함 통과. BE727개(기존skip1), FE host1421개(기존skip4)와 Android APK 빌드 통과. 실제 MySQL8.4+인증 HTTP 저장→내부 preflight에서120/96→144/115.2→480/384, 생략 보존, DB 키 정규화 뒤 SHA, 너무 짧은 음의 저장 거절/판본 보존을 확인했다.
- 최종 격리 MySQL의 V41→V42 적용 뒤 실제 multipart 가져오기480이 악보/변환 큐에 저장됐다. 여전히 유효한 구간 위치를 남기는 마지막 칸 삭제도 명시 거절하며 판본을 보존한다. 실제 preflight HTTP504 회귀는 저장/초안을 바꾸지 않는다.
- [CAT 공식 배포](https://www.catseoul.com/gugak-library/)의 단소 Live Sampler1.3을 격리 다운로드했다. 설치기를 실행하지 않고 ZIP을 데이터로 읽었다. [FFmpeg 배포](https://www.gyan.dev/ffmpeg/builds/)9.0.2 essentials ZIP은 제공 SHA-256 `60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba`와 일치한다. 전역 설치·PATH 변경 없음.
- 실제 내부 HTTP CAT4음+숫자 구간 변속:200,6,550ms,1.875초 소요. 선율 MIDI onset0/1/2/4초, 수행표0–1/1–2/2–4/4–6초 일치. M4A SHA-256 `e76c207e4eba12d1e22297f147762989444469d51f24b6eb1030360369448ebd`.
- [국립국악원 장구 원본](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의6개 WAV는 [기존 목록](jangdan-preview-audio-manifest.json)의 SHA와 전부 일치한다. 772의 첫 쉼1.5박·ID12 고정 악보에 제2각60→30 숫자 계획을 붙인 실제 CAT+장구 HTTP는200,12,550ms,1.657초. 장구 onset0/1/2/3/4/6/8/10초, 선율1.5/2/3/4/6/8/10초. M4A SHA-256 `db39a0169ef93ba137252069c36b33d7939b875d361e61e7ba620508984812c6`.

실제 파일은 작업 worktree의 Git 제외 `.scratch/real773/`, `.scratch/janggu773/`에 요청·원본·ZIP·MIDI·M4A·수행표로 보존했다. 재현은 `data.src.playback.service.PlaybackService`의 실제 renderer command와 문서화된 CAT/장구/FFmpeg 환경변수를 사용한다. 이 증거는 합성·타이밍 검증이며 실물 악기·전문 연주자·실기기 청취 검수 완료가 아니다. 전체 지원음 교집합과 합친 실제 앱 저장→음원 작업은770/772와 통합 검증한다.


지원음만 쓰는 추가 합성 fixture는 같은 쉼·장단·변속 구조의NOTE를 단소 청황75/청태77로 교대했다. 실제 HTTP200,12,550ms,4.375초 소요, 장구 타점은 그대로이며 선율 음고는75/77 교대이다. M4A SHA-256 `d27c41867fdabd31c6b7efadea07e2e5cd45772cd564c17a9d69fc84c8b0f5eb`, source SHA-256 `118f3aad60d4bae55be0a31c237dd82ee41457aa1fd2c0774ae241cbcac0537d`. 입력·출력은 `.scratch/janggu773-supported/`에 보존했다. 최종770지원음 검사와772식별자/주기를 거친 서버 작업 통합은 루트의 통합 검사로 기록한다.

## MR 전 검토에서 발견한 빠른 구간의 MIDI 해상도 경계

기본60 BPM, 첫 구간2,000,000 BPM의 고정4음에서 구간 변환 후 MIDI 길이가0tick이 되어 일반 INVALID_REQUEST만 반환되는 것을 재현했다. 변환기가 위치를 가진 MidiResolutionError를 내고 공통 악보 어댑터가 원본 eventId로 연결한다. preflight는 TEMPO_UNRENDERABLE / MIDI_NOTE_TOO_SHORT / adjustmentTarget=TEMPO_PLAN을 반환한다. 숫자 계획과 기본 BPM을 같은 비율로 낮추면 MIDI 격자 안의 상대 길이가 같으므로 거짓 기본 BPM 상한을 제시하지 않는다. 앱은 해당 구간을 수정해 다시 가져오도록 한 줄로 안내한다. 기본 BPM만 빠른 기존 PCM_NOTE_TOO_SHORT와 구분한다.

직접 estimate 및 실제 estimate subprocess 경계를 포함한 고정회귀를 red→green으로 확인했다. tempo/repeat/render 30검사(실패0, encoder 조건부1제외) 통과. 정상 타점이나 생성 알고리즘은 변경하지 않았다.
