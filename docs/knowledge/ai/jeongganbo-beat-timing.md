# 정간보 변환의 장단 소박과 BPM 박 단위

- 작업: S15P31E102-732
- 확인일: 2026-11-11
- 수정 기반: `e0eb4449affabaf1b347709d08a47d1fea798996`
- [교정 범위와 완료 기준](../../specs/danmori-conversion-timing.md)

## 오류 원인과 영향 경로

`ai/src/omr_service/jeongganbo_text.py`의 기존 `beats_per_jeonggan()`은 장단의 `pattern.beats` 배열 길이를 BPM 박 수로 사용했다. 점4분음표 단위만 3으로 나눴으며 4분음표 단위 장단은 모든 배열 칸을 1박으로 간주했다.

단모리의 배열은 8분음표 소박 8개이므로 4분음표 4박이다. 4정간 각을 8박으로 읽으면 정간당 2박으로 저장된다. 120 BPM에서 4정간이 정상 2초 대신 4초가 되며, 화면과 실제 합성 음원 모두 늘어난다. 원보의 가변 빠르기 미지원과는 별개다.

단위 근거는 [장단 카탈로그](../../../data/reference/jangdans.json), [기존 장구 음원 환산](../../../data/src/audio/jangdan_audio.py)의 `CELLS`, [국립국악원 장단 통일안의 휘모리(단모리) 4/4 설명](https://academy.gugak.go.kr/dp/pds/HTML5-2-4-2/HTML5-2-4-2-4/HTML5-2-4-2-4-1/index.htm)이다. 중모리의 배열 칸은 4분음표, 세령산은 점4분음표, 나머지 현재 카탈로그의 배열 칸은 8분음표라는 차이를 함께 보존해야 한다.

| 경로 | 이번 오류와의 관계 |
| --- | --- |
| 정간보 PDF 자동 변환 | 장단 패턴에서 정간당 박을 정하므로 직접 영향 |
| 오선보 자동 변환 | MusicXML의 박 단위로 길이를 정한다. 기존 장단 일치 검사만 같은 잘못된 함수를 호출 |
| 편집 화면에서 장단 이름 변경 | 기존 tick 비율을 보존하므로 이 동작으로 길이가 새로 두 배가 되지 않음 |
| 공개 카탈로그 | 검수 기록의 명시적 `beatsPerJeonggan` 또는 JGB `staffUnit`을 사용. 장단 이름으로 일괄 교정하면 안 됨 |
| 합성·장구 반주 | 합성은 저장된 tick을 사용. 장구 반주 자체가 곡 길이를 늘리는 것은 아님 |

## 기존 데이터 조사

2026-11-11 09:51~09:57 KST, 활성 백엔드 컨테이너에서 DB명을 확인한 뒤 운영 DB를 `TRANSACTION READ ONLY`로 조회했다. 계정 비밀값은 출력·저장하지 않았다.

- 내 악보 5개: 정간보 OMR 2개, 오선보 OMR 2개, 직접 작성/복사 1개.
- 변환 이력 4개. 현재 악보·초안·음원 요청의 박 단위와 변환 요청의 장단을 함께 대조했다.
- 정간보 OMR 2개는 모두 사용자 제외 요청에 해당하는 블루 발렌타인 판본이다. 두 판본의 `music`·`columns`·`events` 전체 해시가 일치한다. 원본 선율 내용은 이 문서에 복사하지 않는다.
- 120 BPM 판본은 115각 × 4정간 × 2박 × 60/120 = 460초로, 사용자가 보고한 7분 40초와 정확히 일치한다. 160 BPM 판본도 같은 잘못된 시간축을 갖지만 요청에 따라 보존한다.
- 음원 요청 행은 제외 판본의 실패 기록 1개뿐이며 결과 본문·만료값이 없다. 서버 음원 객체 행은 0개, 임시 원본 파일 행도 0개다.
- 삭제되지 않은 카탈로그 114개 중 단모리 곡은 옹헤야(99) 1개다. `jgb-playback-import/v1` 경로이며 이번 OMR 환산 함수로 생성한 곡이 아니다. BPM 240과 정간당 3/2박을 함께 사용하므로 비율만 보고 두 배 오류로 분류하지 않는다.

**블루 발렌타인 제외 후 교정 대상은 0개다.** 운영 UPDATE나 음원 재생성은 수행하지 않는다. 조사 원본과 SHA-256은 로컬 전용 `data/processed/danmori-732/`에 있으며 Git에 넣지 않는다.

09:52와 09:57의 조회 결과에서 내 악보 5개·초안·변환 결과·음원 요청·공개 단모리 악보의 전체 JSON 해시와 판본이 모두 일치했다. 그 사이 활성 백엔드 컨테이너 이름은 바뀌었지만 대상 DB와 조회 데이터는 같았다. 이번 작업에서는 배포를 실행하지 않았다.

## 조사 시 주의점

확정 저장 시 편집기가 `provenance.converter`를 `app-editor-1`로 바꾸므로 변환기 문자열만 검색하면 기존 영향을 놓친다. 지속되는 `user_sheets.source`와 `sheet_conversions.notation`·요청 장단을 확인하고, 현재 악보·초안·음원 요청 스냅샷을 각각 대조해야 한다.

음원은 요청 후 10분 동안 내려받을 수 있는 임시 결과다. 향후 교정 대상이 있으면 악보의 행/JSON 판본을 함께 증가시키고, 진행 중 초안·음원 작업을 보존하면서 구판 결과 재사용을 막아야 한다. 기기에 내려받은 파일은 서버에서 덮어쓸 수 없으며 새 판본으로 재생성·재다운로드해야 한다. 이번에는 해당 작업 자체가 제외 대상이므로 어느 판본도 변경하지 않았다.

## 구현과 검증

변환 엔진은 `2`, converter는 `jeongganbo-text/standard_score_text@2`로 올렸다. 장단 11종의 패턴 칸 음가를 4분음표 단위로 정한 뒤 BPM 박 단위로 나눈다. 기존 세마치·굿거리·중모리 등의 정상 길이는 유지된다. 세령산의 점4분음표 칸만 정간당 3/2박을 정상으로 허용하며, 세마치 2정간 같은 기존 불일치 알림은 유지한다.

검증 기준은 기반 `e0eb4449`와 구현 `62241069`다. 작업자 커밋 `6203c53d`를 내용 변경 없이 통합했으며, 독립 점검에서 발견한 세마치 경고 소실 문제는 세령산 예외로 범위를 좁혀 해결했다.

| 검사 | 결과와 범위 |
| --- | --- |
| 정간보·오선보·OMR API 관련 5개 테스트 파일 | 159 통과, 6 실패. 실패는 기반 코드에서도 재현되는 기존 로그 캡처 문제이며 로그 처리 코드는 변경하지 않음 |
| 음원 변환 관련 5개 테스트 파일 | 47 통과, 건너뜀 0. 실제 FFmpeg/M4A 검사 포함 |
| 같은 PDF의 실제 변환·합성 비교 | 120 BPM, 4정간 2각. 악보 8초 → 4초, 각당 4초 → 2초, M4A 8.550초 → 4.550초 |
| 기존 데이터 보존 | 조회한 내용·판본·해시 일치. 교정 대상 0개, DB 갱신 0개, 음원 재생성 0개 |

PDF 검사는 실제 글자 층 reader와 Node generator, OMR API, 공통 악보 변환, MIDI/WAV/M4A 출력을 거쳤다. 입력 PDF SHA-256은 `b1f8c4817f073dcbbaadd2ffac55145a7bc590aff5f1d4ee70cba61d3071d435`다. 음 시작점은 변경 전 0·1·2·…·7초에서 변경 후 0·0.5·1·…·3.5초가 됐다. 파일에는 기존 합성 잔향/여백 0.55초가 포함된다. 기존 테스트용 440 Hz sine 샘플을 썼으므로 실제 악기 음색을 평가한 검사는 아니다.

로컬 증거는 `%TEMP%/danmori-732-omr-tests.txt`, `danmori-732-playback-tests.txt`, `danmori-732-baseline/`, `danmori-732-audio-before/summary.json`, `danmori-732-audio-after/summary.json`, `danmori-732-audio-probe.py`에 남겼다. 운영 무변경 비교는 `data/processed/danmori-732/comparison.json`이다. 의존성 설정과 재현 테스트 명령은 [AI README](../../../ai/README.md#악보-인식-api)를 따른다.

검증 환경은 Windows/Python 3.12.12, 기존 JGB generator `9d4a24e`, FFmpeg/ffprobe 9.0.2다. `JGB_GENERATOR_DIR` 및 `FFMPEG_BINARY`·`FFPROBE_BINARY`를 실제 실행 파일로 설정한 뒤 다음 범위를 검사했다. 아래 `python`은 작업자의 `ai/.venv/Scripts/python.exe`이며, OMR 실행 시 `PYTHONPATH`에 `ai/src`와 기존 OMR 의존성 경로를 넣었다.

```text
# ai/에서
python -X utf8 -m pytest tests/unit/test_omr_api.py tests/unit/test_omr_text_engine.py tests/unit/test_omr_staff_engine.py tests/unit/test_staff_musicxml.py tests/unit/test_staff_audiveris_fixtures.py -q

# 저장소 루트에서, PYTHONPATH=.
python -X utf8 -m pytest data/src/playback/tests/test_common_score.py data/src/playback/tests/test_common_direct.py data/src/playback/tests/test_common_preview.py data/src/playback/tests/test_midi.py data/src/playback/tests/test_render_request.py -q
```

실제 M4A 재현 스크립트 인자는 `소스루트 새출력폴더 예상음악초 [기존입력PDF]`다. 구현 소스에서 예상 4초로 먼저 실행하고, 기반 소스에서 같은 PDF를 넘겨 예상 8초와 비교했다. 출력 폴더는 기존 증거를 덮어쓰지 않도록 새 경로를 사용한다.

제품 코드는 로컬 커밋 상태이며 Push·MR·제품 배포와 정식 리뷰는 아직 진행하지 않았다. 운영 영향 조사는 완료됐고, 배포 전 운영 변환기는 이번 코드 변경으로 바뀌지 않는다.
