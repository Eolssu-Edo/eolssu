# 공통 악보 음원의 독립 파형 검증

- 연결 Task: `S15P31E102-338`
- 기준: develop `0fc4c4a`에 추가한 [공통 악보 재생 스펙](../../specs/common-score-playback.md)의 단음·고정 BPM·원조율 재생
- 검증 대상: 검수된 아리랑 공통 악보를 BPM 96으로 변환해 CAT 단소 샘플러로 만든 WAV와 AAC-LC M4A
- 결과: 두 실제 음원에서 아래 검사 통과. 사람의 청음·악기 음색 품질·앱 재생 동작은 이 검사의 결론에 포함하지 않는다.

## 기대값과 관측값을 분리한 방법

[verify_common.py](../../../data/src/playback/verify_common.py)는 원본 `score.json`과 사용자가 지정한 BPM만 기대값으로 읽는다. 재생 어댑터, 컴파일 결과, MIDI, 소스 맵, 렌더 보고서를 가져오지 않는다. PCM의 주파수 추정 함수도 목표 주파수를 입력받지 않는다.

- `hwangHz × 2^((율의 반음 번호 + 12 × octave) / 12)`로 음높이를 구한다. 율 순서는 황·대·태·협·고·중·유·임·이·남·무·응이다.
- 원본 틱과 `ticksPerBeat`, 명시 BPM으로 시각을 구한다. `DOTTED_QUARTER`만 MIDI 4분음표 환산값 1.5를 사용하며, 다른 단위는 1을 사용한다. MIDI 정수 마이크로초 템포의 반올림도 별도로 계산한다.
- 숨표는 120ms 무음과 최대 50ms release를 합쳐 음 길이 절반으로 제한하는 기존 정책에서 구한다. 짧은 숨표의 CC72 release 양자화는 127단계로 계산한다. MIDI 틱 반올림 차이는 무음 경계 2ms 여유 안에서 취급한다.
- 음표마다 발음 구간 30%·50%·70% 지점에서 최대 80ms 창 세 개의 정규화 자기상관 F0를 측정한다. 유효 창 두 개 이상과 중앙 음정 오차 ±50cent를 요구한다. ±50cent는 반음 간 중간 경계이며 채점 알고리즘의 통과 기준을 정하는 값은 아니다.
- 발음 구간 앞·뒤 에너지와 2ms 구간별 에너지 시작·끝을 확인한다. WAV는 예상 경계에서 25ms 이내, M4A는 AAC 여유를 더한 50ms 이내여야 한다. 모든 NOTE 시작의 2ms 에너지 골도 확인해 이어진 같은 음의 재발음 누락을 탐지한다.
- 긴 음의 중간 단절은 별도로 검사한다. 발음 양끝 25ms를 제외한 구간 전체에 20ms RMS 창을 10ms 간격으로 놓으며, 마지막 창도 끝에 맞춰 넣는다. 모든 창은 발음 RMS의 2%와 절대 RMS 0.000001을 모두 넘어야 한다. 20ms는 관측 하한 100Hz의 두 주기이므로 사인파의 정상적인 순간 영점을 무음으로 오판하지 않는다. 이 시간 해상도보다 짧은 끊김까지 보증하지는 않는다.
- 원본 REST 및 숨표의 양 끝을 제외한 내부 구간을 검사한다. WAV는 경계 2ms를 제외하고 PCM이 정확히 0이어야 한다. M4A는 44.1kHz AAC 한 프레임(1024샘플)보다 조금 큰 25ms를 제외하고 무음 RMS가 발음 RMS 중앙값의 1% 이하여야 한다.
- 악보가 끝난 뒤 종료 여유 0.55초와 디코드 패딩도 같은 무음 기준으로 검사한다. 시작 쪽 경계 여유만 적용하고 파일 마지막까지 확인한다. 음표 release는 원본 NOTE 슬롯 안에서 끝나므로 이 구간에 새로운 발음이 있어서는 안 된다.
- 전체 길이는 악보 재생 시간과 기존 MIDI 종료 여유 0.55초를 합친 값과 비교한다. WAV 프레임 수는 ±2샘플, M4A 디코드 프레임은 -2~1025샘플을 허용한다. 컨테이너가 보고하는 길이는 ±3샘플이다. AAC 패딩은 디코더별로 남을 수 있으므로 별도 기록한다.

시김새, 템포 변화, 이조, 다성부, 관측 음역 100~3000Hz 밖의 음, 발음 구간 70ms 미만은 검증 불가로 실패한다. 지원하지 않는 조건을 통과로 처리하지 않는다. 이 도구는 공통 JSON 전체 스키마 검사를 대체하지 않는다.

## 재현

저장소 루트에서 재생 도구의 환경과 실제 생성 음원을 준비한다. 생성 절차는 [재생 도구 README](../../../data/src/playback/README.md)를 따른다. 외부 서비스·브라우저·마이크는 사용하지 않으며, 실행한 FFmpeg·FFprobe 프로세스는 명령 종료와 함께 끝난다. 원본과 음원을 수정하지 않는다. `data/processed/`의 JSON 보고서와 음원은 Git에 넣지 않는다.

```powershell
.\data\.venv\Scripts\python -B -m unittest data.src.playback.tests.test_verify_common -v

.\data\.venv\Scripts\python -B -m data.src.playback.verify_common --score data/tests/fixtures/scores/ngc-standard-2022-p173.score.json --bpm 96 --audio data/processed/playback-338/arirang-wav/danso.wav --out data/processed/playback-338/verify-wav.json

$ffmpegBin = 'C:/Users/kingc/AppData/Local/Eolssu/Tools/ffmpeg-release-20261029/unpacked/ffmpeg-9.0.2-essentials_build/bin'
.\data\.venv\Scripts\python -B -m data.src.playback.verify_common --score data/tests/fixtures/scores/ngc-standard-2022-p173.score.json --bpm 96 --audio data/processed/playback-338/arirang-m4a/danso.m4a --ffmpeg "$ffmpegBin/ffmpeg.exe" --ffprobe "$ffmpegBin/ffprobe.exe" --out data/processed/playback-338/verify-m4a.json
```

`$ffmpegBin`은 해당 컴퓨터의 실행 파일 위치로 바꾼다. M4A는 명시한 FFprobe로 44.1kHz·mono·AAC-LC인지 확인한 뒤, FFmpeg로 리샘플링 없이 float PCM을 추출한다. CLI 종료 코드 0과 JSON의 `passed: true`가 모두 있어야 통과다. 보고서에는 원본·음원·관측기 코드의 SHA-256, BPM, 음표별 기대·관측값, 모든 실패 항목을 남긴다.

## 실제 실행 결과

2026-11-01 실행 환경은 Python 3.10, NumPy 2.2.6, SoundFile 0.14, FFmpeg/FFprobe 9.0.2이다. 검증기 자체 대조 테스트 9개가 통과했다. 대조 파형은 렌더러 없이 직접 만든 사인파이며, 한 반음 오류·쉼에 소리 삽입·파일 절단·발음 지연·같은 음 재발음 누락·미지원 시김새를 각각 검출한다.

독립 리뷰에서 초기 검증기 `371944e`가 종료 여유 구간의 880Hz 발음과 1초 NOTE 내부의 총 480ms 단절을 놓치는 반례를 확인했다. 두 반례를 WAV·AAC 정책 각각에서 실패하는 테스트로 먼저 고정한 뒤, tail 무음 및 연속 RMS 검사를 추가했다. 수정 후 대조 테스트와 아래 실제 두 음원을 모두 다시 실행해 통과했다. 기존 음정·무음 기준을 완화하지 않았다.

원본 `ngc-standard-2022-p173.score.json`에는 NOTE 61개, REST 4개, 숨표 4개가 있다. 144틱 ÷ 3틱/박 ÷ 96박/분 × 60 = 명목 30초이며, MIDI 템포 반올림 후 30.000024초, 종료 여유를 포함해 30.550024초가 기대값이다.

| 관측 | WAV | M4A |
| --- | --- | --- |
| 결과 / 실패 | 통과 / 없음 | 통과 / 없음 |
| 디코드 프레임 / 시간 | 1,347,256 / 30.550022676초 | 1,347,256 / 30.550022676초 |
| 예상 대비 패딩 | 0샘플 | 0샘플 |
| 음표별 중앙 음정 오차의 최대 절댓값 | 17.3334cent | 17.3378cent |
| 쉼·숨표 내부 RMS / 발음 RMS 최댓값 | 0 | 0.000005124 |
| NOTE 시작 에너지 / 발음 RMS 최댓값 | 0.02587 | 0.02570 |
| 발음 에너지 시작의 최대 지연 | 9.98ms | 9.98ms |
| 발음 에너지 끝의 최대 선행 | 1.61ms | 1.61ms |
| 내부 연속성 검사 창 / 단절 검출 | 2,461 / 0 | 2,461 / 0 |
| 내부 창 RMS / 발음 RMS 최솟값 | 0.14864 | 0.14816 |
| 종료 여유 구간 RMS | 0 | 0 |

원본 SHA-256: `51cd874652d705511cfaa0f2c03ba6d3632ae10b4a483036d6a438a8eac73f1b`.

음원 SHA-256:

- WAV: `86fb6234e7210cbe955daaf3bbf6b2254a83d138e6a3aa6e4d17272664c1ad86`
- M4A: `b61b800934077e1e505c04d67eccc6ab8439df7a554362cb4b1a5ab9b9619f50`

에너지 골은 재발음 경계를 관측하는 제한된 신호이며 사람의 혀 동작이나 음색의 자연스러움을 증명하지 않는다. CAT 자산·렌더러·템포/숨표 정책·인코더·원본 fixture가 바뀌면 새 음원으로 재실행한다. 새 입력이 현재 관측 범위를 벗어나면 기준을 완화해 통과시키지 말고 별도 검증 방법과 범위를 정한다.

## 2026-11-02 아리랑 자료 재대조와 대상 정정

사용자의 멜로디·음높이·리듬 지적을 위 아리랑 M4A에 대한 것으로 잘못 이해해 `4d15a52` 기준 아래 조사를 수행했다. 이후 사용자가 지정한 실제 문제 파일은 `preview/danso-m4a/danso.m4a`(SHA `9fb7f0de…`)였다. 이는 별도의 인공 시김새 검증곡이다. **아래 아리랑 결과는 사용자 지적 음원을 검증한 결과가 아니며, 그 파일의 원인 설명에 사용하지 않는다.** [정확한 대상과 검증 한계](common-score-expression-preview.md#2026-11-02-청취-지적-대상-확인)를 별도 기록했다. 원본과 생성 음원을 보존했으며 제품 동작을 바꾸거나 수정본을 만들지 않았다.

### 원보와 이전 생성본 대조

- [국립국악원 교과서 표준악보집](https://www.gugak.go.kr/userfiles/files/textbook_final.pdf)의 PDF 173쪽(인쇄 344–345쪽)을 직접 읽어 별도 전사한 48정간·65이벤트와 공통 fixture를 대조했다. 율명·옥타브·시작 박·길이 차이는 없었다. PDF 157쪽의 단소 기보 설명과 해당 곡의 세마치 범례는 1정간=1박 해석을 뒷받침한다.
- 원보에는 수치 BPM이 없다. 이번 96 BPM은 `data/reference/jangdans.json`의 세마치 서비스 기본값이다. 특정 실연과 같은 속도를 검증한 결과가 아니다.
- 10월 29일 앱 검수용 JGB 생성본과 현재 공통 경로의 65이벤트를 MIDI 음높이·누적 시작초·명목 길이초로 비교한 결과 차이가 없었다. 이전 파일도 전문가가 승인한 기준 연주라는 증거는 없다.
- 이전 JGB의 quarter BPM 48/24 quarter와 현재 공통 경로의 quarter BPM 144/72 quarter는 모두 30초다. BPM 숫자만의 차이를 3배 속도 오류로 해석하면 안 된다.
- 이전 M4A SHA-256은 `4942c7c9c1e58fe58821c39667f8afa9fb724e4b1d4d7393d7cd9aa3de01ab9e`다. 이번 전달 파일은 위 `b61b8009…`이며, 시김새 확장 검증곡과 다른 파일이다. 아리랑 원본의 시김새 배열은 비어 있다.

### 실제 파일에서 확인한 범위

- WAV/AAC를 각각 디코딩한 결과 길이 1,347,256프레임@44.1kHz, 최고 상관 시차 0프레임, 상관계수 0.999996305, SNR 51.24dB다. AAC 인코딩 때문에 속도나 음높이가 달라졌다는 증거는 없다.
- 독립 `mido` 파싱에서 61개 NOTE의 음높이·순서가 원본과 일치했다. 시작 시각의 명목값 오차 최대 22.5µs는 정수 MIDI 템포 반올림 범위였다.
- 실제 WAV/AAC 각각 948개의 40ms 창을 20ms 간격으로 측정했다. 창 시작점을 음 시작+60ms부터 note-off−50ms 미만에 배치했다. 따라서 창 끝은 note-off 약10ms 전까지 도달할 수 있다. 유효하지 않은 창은 없었고 목표 대비 최대 오차는 각각 24.281/24.292cent였다. 기존 자동 비브라토 ±25cent가 이 범위에 포함된다.
- 사용된 6개 CAT 샘플의 RootKey·해시·44.1kHz 샘플레이트를 확인했다. 모두 샘플 기본 재생 비율 1, 샘플 detune 0, 시작 cursor 0, sustain loop 없음이다. 별도 조율 보정·비브라토 pitch bend는 적용된다. 샘플을 잘못 대응하거나 한 옥타브 이조했다는 증거는 없다.

### 확인된 표현 차이와 미확인 사항

이전 생성본은 일반 음의 명목 끝 뒤로 release 50ms가 이어졌고, 이번 공통 경로는 그 감쇠를 명목 음 길이 안에 포함한다. 따라서 일반 음 57개의 note-off가 약 50ms 앞당겨졌고 시작 시점은 유지된다. 음 연결 느낌은 달라질 수 있으나, 이것이 사용자 지적의 원인이라고 확인된 것은 아니다.

긴 단소 음에는 원보의 시김새 표기와 별개로 자동 비브라토가 들어간다. ±50cent 통과 기준은 이를 허용하므로 기보된 표현만 충실하게 재생했다는 증명도 아니다. 샘플마다 어택과 배음 세기가 다르며, 위 F0 조사는 첫 60ms의 과도음이나 자연스러운 장단·호흡·음색을 평가하지 않는다. 사용자 플레이어의 실제 속도 설정도 관측하지 않았다.

사용자는 10월 29일 아리랑 파일은 괜찮다고 확인했고, 이어 실제 문제 파일의 경로를 명시했다. 따라서 아리랑 경로의 추가 원인 조사를 중단했다. 비교용 파일은 이름만 구분해 복사하고 원본과 SHA-256 일치를 확인했다. 이 조사에서 음열·조율·BPM을 바꾸지 않았다.

원보 수동 대조 증거는 로컬 `common-score-preview-audit` worktree의 `data/processed/arirang-source-audit/`에 있다. `manual_original_comparison.py`와 `manual-original-comparison.json`을 보존했으며 원문 PDF·이미지·음원은 Git에 포함하지 않는다.

실제 PCM/MIDI/샘플 검사 스크립트와 결과는 로컬 `common-score-preview-verify` worktree의 `data/processed/arirang-diagnosis/diagnose_arirang.py`, `arirang-2026-11-02.json`에 보존했다. 스크립트 인자는 `--root <현재 worktree> --old-bundle <10월29일 p173 출력 폴더> --ffmpeg <실행 파일> --output <결과 JSON>`이다. 결과 JSON의 SHA-256은 `93e17ce7905fd7a450a42b25b17e758d0b7439a2f3dbb79e434f0b0f846d7648`이다.

## 2026-11-02 MR !203의 Notion 기존곡 전수 생성

[MR !203](https://lab.ssafy.com/s15-final/S15P31E102/-/merge_requests/203)의 `e4f61db2664e49a240ab907c3fcad2d4fc0bca35`를 별도 worktree에서 실행했다. 기존 재생용 입력 107개·108성부는 모두 MIDI·M4A를 만들었으나, **전곡의 음악적 내용이 보존됐다는 판정은 실패**다. 공통 변환 13성부 거부, 두 곡의 음표 수 변화, 정악대금 오나라의 지속음 누락을 확인했다. 제품 코드는 수정하지 않았다.

### 입력과 실행 조건

[Notion DATA](https://app.notion.com/p/DATA-3f1a0252aa45809b937ce004308badae)의 단소·소금·대금 하위 페이지에서 최신 재생용 첨부 이름·버전·곡 목록을 확인했다. Notion MCP는 404를 반환해 브라우저로 읽었고, 해당 이름·버전·파일 수와 일치하는 기존 로컬 ZIP을 사용했다. 원격 첨부를 다시 내려받아 바이트 단위로 일치시키지는 않았다. 로컬 ZIP 및 추출 입력의 SHA-256은 `inventory.json`에 보존했다.

| 입력 | 악보 파일 | 성부 | 기존 경로 M4A | JGB→공통 경로 M4A / 변환 거부 |
| --- | ---: | ---: | ---: | ---: |
| 단소 재생용 수정본 20261028 | 47 | 47 | 47 | 39 / 7, 청성곡 1 제외 |
| 소금 전체 수정본 20261030 | 22 | 23 | 23 | 20 / 3 |
| 정악대금 재생보정 20261029 | 28 | 28 | 28 | 25 / 3 |
| 산조대금 재생보정 20261029 | 10 | 10 | 10 | 10 / 0 |
| 합계 | 107 | 108 | 108 | 94 / 13, 기존 IR 1 제외 |

소금 북천이맑다커늘은 두 성부를 각각 생성했다. 산조 한오백년은 v2를 사용했다. 단소 청성곡은 기존 오선보 2줄 발췌 `score.json`으로 생성했으며 서비스 공통 v1 입력이 아니다. 대금 청성곡은 Notion에 PDF만 있어 이번 JSON 생성 목록에 포함되지 않았다.

환경은 Windows, Python 3.12.12, NumPy 2.5.4, SoundFile 0.14.0, mido 1.3.3, jsonschema 4.26.0, FFmpeg/ffprobe 9.0.2다. MR의 requirements를 별도 `data/.venv`에 설치하고 기존 로컬 CAT v1.3 ZIP을 사용했다. 외부 서비스 게시·DB 변경·앱 기동은 하지 않았다.

### 관측 결과와 실패

- **기존 경로 생성 passed:** `data.src.playback.__main__.run`으로 원본 JGB/기존 IR에서 108개 M4A와 MIDI를 생성했다. AAC-LC·44.1kHz·mono, 전체 디코드, 길이·유한값·피크 검사를 통과했다. 독립 mido 판독의 6,446음과 출력 보고서의 음높이·시작·음 개수가 일치하고 원본/출력 해시가 맞았다. 이는 출력 간 일관성 검사이며 원보 정확성의 독립 증명은 아니다. 총 음원 길이는 4,057.419683초다.
- **공통 경로 생성 일부 failed:** JGB 107성부 중 94성부는 서비스 v1로 변환한 뒤 실제 신규 어댑터를 통해 M4A를 생성했다. 13성부는 JGB→공통 단계에서 거부됐다. 이 변환기 파일은 MR의 base `7bdecc1`과 동일하므로 13건을 이번 MR이 새로 만든 회귀로 분류하지 않는다.
- **공통 표현 비교 failed:** 원본 `staffUnit`의 4분음표 비율·빠르기를 보존하고 연속 쉼의 합침을 허용했을 때, 실행 가능한 94성부 중 92성부의 명목 음높이·시작·길이·쉼이 기존 경로와 일치했다. 단소 **늴리리야 기존 판본은 93→91음**, **천안삼거리는 77→73음**으로 달라졌다. 율명 뒤의 독립 율명대용 기호를 JGB 파서는 별도 슬롯으로, 공통 변환은 앞 음의 시김새로 처리하는 차이가 있다. 앞 음을 시김새 음열로 바꾸는 새 미리듣기 경로와 결합하므로 원곡 보존 통과로 처리하지 않는다.
- **오나라 지속음 failed:** 정악대금 입력의 `playback.gak_continuations` 및 Notion 설명은 남을 45.5~51초, 5.5정간 지속하도록 명시한다. 기존·공통 경로 모두 48~51초를 쉼으로 만들었다. 두 실제 AAC에서 48.1~50.9초의 123,480샘플이 모두 정확히 0이었다. 직전 46~47.5초 RMS는 약 0.165, 다음 음 51.1~51.4초 RMS는 약 0.369여서 관측기가 정상 발음과 무음을 구별함을 확인했다. 기존 경로도 실패하므로 신규 공통 어댑터만의 회귀가 아니다.
- **참조표 경고:** 단소 진도아리랑의 청남(`남·1`)은 공통 변환 불변식 검사에서 율명 표 미등록 경고가 있다. 생성기는 재생했으나 참조 데이터 일관성까지 통과한 것은 아니다.
- **회귀 passed:** playback unittest 201개, data unittest 90개, 합계 291개 통과. skip·실패·오류 0이다. 전수 입력의 위 실패를 단위 테스트 통과로 대체하지 않는다.

공통 경로 검증 입력은 사람이 검수 완료한 것으로 표시하지 않고 `review=inferred`를 사용했다. 원본의 명시 황종을 Hz로 옮기고, 시김새 위치·조·표현 정책을 별도 companion으로 보존했다. 초기 `JEONGGAN=1quarter` 투영에서 생긴 9개 꾸밈 시작 차이는 검증용 메타데이터 변환의 부작용이었다. 원본 staffUnit 비율을 유지하는 QUARTER 투영으로 제거했고, 출력이 달라지는 11곡을 추가 생성해 통과했다. 나머지 83곡은 컴파일된 음열이 같고 시각이 1ms 허용오차 내에서 동등한 기존 생성 증거를 재사용했다(전체 길이 차이 최대 54µs). 공통 경로의 note-off 약 50ms 차이는 release를 음 길이 안에 넣는 명시 정책이며 별도 보존 여부 결함으로 세지 않았다.

| 공통 변환 거부 악기 | 곡 / 판본 | 이유 |
| --- | --- | --- |
| 단소 | 몽금포타령, 뱃노래, 베틀가, 영산회상 중 타령, 현악영산회상 타령 고급, 천년만세 계면가락도드리 고급, 정선아리랑 | 율명 없는 독립 시김새·빈지속 기호 7건 |
| 소금 | 취타 초장 초급·중급·고급 | `gakBeats='3:4'` 3건 |
| 정악대금 | 세령산 1장 장식음, 타령 1장 장식음 | 율명 없는 독립 시김새 2건 |
| 정악대금 | 인연 | `gakBeats='1:1'` 1건 |

### 증거와 재실행

검증 worktree `mr203-verify/S15P31E102`의 `data/processed/mr203-verification/`에 `results.md`(곡별 M4A/MIDI 링크), `inventory.json`, `legacy-preflight.json`, `legacy-results.json`, `verify_legacy.py`를 보존했다. 같은 상위 worktree 폴더의 `mr203-common-evidence/`에는 공통 입력·실제 음원과 `preflight.json`, `render.json`, `quarter-preflight.json`, `quarter-render.json`, `onara-waveform-evidence.json`이 있다. 회귀 로그는 `mr203-regression-evidence/`에 있다. 원본·음원·로컬 증거는 커밋하지 않는다.

```powershell
# mr203-verify/S15P31E102 루트. 입력·CAT·FFmpeg 경로는 helper의 로컬 상수를 확인한다.
# 전체를 다시 생성한다. 중단한 실행을 이어갈 때만 verify_legacy.py에 --resume을 붙인다.
data/.venv/Scripts/python.exe -X utf8 -B data/processed/mr203-verification/verify_legacy.py
data/.venv/Scripts/python.exe -X utf8 -B ../../mr203-common-evidence/check_common.py preflight
data/.venv/Scripts/python.exe -X utf8 -B ../../mr203-common-evidence/check_common.py render
data/.venv/Scripts/python.exe -X utf8 -B ../../mr203-common-evidence/check_quarter_projection.py preflight
data/.venv/Scripts/python.exe -X utf8 -B ../../mr203-common-evidence/check_quarter_projection.py render
data/.venv/Scripts/python.exe -X utf8 -B ../../mr203-common-evidence/measure_onara.py
```

이번 결과는 파일 생성·명목 이벤트 비교·오나라 특정 구간 파형 관측이다. 전곡 PDF 독립 대조, 전곡 음정 계측, 사용자 전곡 청취, 전문가 연주 검수, 운영 앱 재생 검증은 미실시다. 다음 수정에서는 독립 기호와 가변 각 변환, 두 곡의 기호 슬롯 의미, 오나라의 각 경계 지속을 각각 회귀 입력으로 고정해야 한다.
## 2026-11-02 기존 변환과 비교한 후속 수정

같은 Jira338에서 사용자 요청에 따라 위 전수 검사의 원인을 고쳤다. 입력 ZIP·성부·원문 바이트는 유지하고, 수정 전 증거를 덮어쓰지 않았다. 새 증거는 로컬 `C:/Users/SSAFY/.codex/worktrees/mr203-common-evidence/fixed/`에 있다.

원인은 세 가지다. 채점 정답용 `score_data.convert()`는 독립 기호·빈지속·`gakBeats`를 지원하지 않아13성부를 거부했다. `청임{니나}` 같은 표기는 기존 재생기에서는 본음과 독립 음열이 각각 시간을 차지하지만 채점용 변환기는 본음의 시김새로 합쳐 늴리리야·천안삼거리의 음 수가 줄었다. 오나라의 `playback.gak_continuations` 지원은 별도 수정본에만 있고 MR203에는 없어 명시된 지속을 읽지 않았다.

[재생용 importer](../../../data/src/scores/playback_import.py)는 기존 JGB parser의 슬롯·실음 해석을 재사용한다. 독립 음열138슬롯만 구체 율명으로 펼치고 긴 같은 row 슬롯은 이음으로 채운다. 소비한 기호·문자 범위·생성 표시 자리/이벤트는 별도 대응표에 남기며 원문 기호를 두 번 적용하지 않는다. 빈지속·각별 정간 수·황종·원본 quarter 비율과 BPM도 보존한다. 기존 검수 fixture·채점용 변환기·서비스 v1은 변경하지 않는다.

import는 `inferred` 결과와 해시 결합 표현 파일을 만든다. 내보내기 전에 기존 JGB와 공통 preview의 NOTE/REST·실음·명목 시작·길이를1μs 허용오차로 비교하고, 표현할 수 없는 혼합은 위치와 함께 거부한다. 이번107 JGB성부 모두 통과했다. 명시 각 경계 지속은 JGB와 공통 표현 파일 양쪽에서 지원하며, 공통의 원본 각 시작 REST/NOTE idx는 수행과 분리해 유지한다.

수정된 기존 경로108성부를 실제 재생성했다. MIDI·AAC-LC M4A·독립 mido·원문/음원 해시 검사108/108 통과, 총6,446음이다. 파일 길이 합은 각 파일의 후행 약550ms를 포함한4,057.41968254초이고, MIDI 악보 길이 합은3,998.019769초다. 오나라 이외107성부는 기존 MIDI와 M4A의 SHA256까지 동일했다. 오나라만 원문 SHA·84음·96.55초 파일 길이를 유지하면서 의도된 지속 구간이 바뀌었다. `legacy-summary.json`, `legacy-results.json`에 전체 결과가 있다.

공통 경로도 공개 importer CLI→공개 playback CLI로107/107성부의 M4A를 실제 생성했다(301.95초, 실패0). 변환 거부13건은 모두 해소됐고, 늴리리야 기존판93음·천안삼거리77음으로 복원됐다. 단소 청성곡2줄은 JGB가 없는 IR1성부이므로 기존108성부 검사에 포함하며 공통107성부에는 포함하지 않는다. 원문→생성 표시 자리→수행 시간표의 전체 대응과 실제 MIDI attack을 확인했다. 요성·음높이 곡선·강약 등 표현 대조에서는 산조3곡의 delay_seconds 12값만 약10^-15초 차이였고1ns 허용오차의 실질 차이는 없었다. MIDI release/legato 차이는 기존 공통 정책으로 별도 기록한다. 곡별 두 경로 음원·음 수·비교 결과는 새 증거 폴더의 `results.md`, 전체 기계 판정은 `final-summary.json`에 있다.

오나라의 남은 명목45.5~51초까지5.5정간 이어진다. 실제 AAC48.1~50.9초의123,480샘플은 기존 전부0에서 수정 후 RMS0.1463509·무음 샘플0으로 바뀌었다. 기존 JGB 출력의 F0 11창 오차는 최대12.86cent다. 공통 출력도 같은 구간 RMS0.1463509·무음 샘플0이며 MIDI noteoff50.95초+release50ms로 기존 공통 release 정책을 지킨다. 두 최종 전수 생성 음원의 SHA와 각11개 F0 창(모두±50cent 이내)은 새 증거 폴더의 `onara-runtime.json`에 있다. 실제 음원은 `results.md`의 바로듣기 링크를 사용한다.

회귀는 통합본에서 playback209개·data104개 통과, 실패·생략0이다. 로그는 새 증거 폴더의 `playback-tests.log`·`data-tests.log`다. 독립 검토자는 지속음20개 focused와4개 교차 경로 반례, importer14개 focused 및107성부의 원문 Unicode 범위·생성 자리 전수 대응·해시를 직접 확인했다. 차단 결함 없음이며 `reviewer-import-preflight.json`에 별도 근거가 있다.

공통 재생의 release를 음 길이 안에 포함하는 정책 때문에 기존 JGB와 공통 음원 바이트 동일성이나 청감 동일성을 주장하지 않는다. 새 공통 투영의 최대 분할은3행·4열이며 FE mapper의 분할 상한에는 걸리지 않지만, 4성부10개 row의4분할 화면 가독성은 이번에 실화면으로 검증하지 않았다. 원보/전문가 연주 품질 검수·운영 업로드·실기기 청취도 별도다. 단소 진도아리랑의 `남·1` 기준표 경고는 원문이나 채점 기준을 임의로 바꾸지 않고 결과에 남긴다.
