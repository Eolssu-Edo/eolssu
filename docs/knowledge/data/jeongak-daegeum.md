# 정악대금 라벨·연주 자료와 검수 근거

S15P31E102-89 · 2026-10-23. 국립국악원 정악대금 목록 7개를 정리했다. 기본음계 54개·시김새 53개, 총 **107개 발음 구간**과 타령 원음 1개, 비교용 원본 악보집 PDF 2개를 제공한다. 라벨은 모두 `inferred`, `eligibleForNoteScoring=false`이며 독립 연주자 검수는 받지 않았다.

## 파일과 원본

- [원본 목록](../../../data/catalog/jeongak-daegeum/sources.json): ID·SHA-256·파일 크기·형식·길이·접근 경로·이용 조건.
- [연주·악보 목록](../../../data/catalog/jeongak-daegeum/performances.json): 타령 원음과 비교 악보의 실제 PDF 쪽.
- [구간 라벨](../../../data/annotations/audio/jeongak-daegeum/): 각 원본의 `annotationPath`가 가리키는 `labels.json`만 입력으로 사용한다.
- 원본은 `data/raw/instruments/daegeum/jeongak/`, 실제 추출본은 `data/processed/instruments/daegeum/jeongak/prepared-v2/`에 있다. 두 폴더는 Git 제외다.

[공식 단음 다운로드](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의 `getInstrumentList.do`에서 정악대금 `instrCd=19917`, 설명 `황종 = E♭`, `instrNuryTyp=1`을 확인했다. 같은 화면의 `getFileList.do`에 `instrCd=19917`을 전달한 전체 응답은 다음 7개다. 파일명 뒤 숫자를 음 개수로 해석하지 않았다.

| 원본 ID | 공식 분류·제목 | 파일 | 길이(초) | 발음 수 |
| --- | --- | --- | ---: | ---: |
| ngc-jeongak-2557 | 기본음계 / 기본음 | jungak_deageum_scale_sus_25.wav | 213.042500 | 18 |
| ngc-jeongak-2558 | 기본음계 / 기본음+요성 | jungak_deageum_scale_sus_vib_26.wav | 169.480021 | 18 |
| ngc-jeongak-2559 | 기본음계 / 기본음+깊은요성 | jungak_deageum_scale_sus_deep_vib_61.wav | 153.045771 | 18 |
| ngc-jeongak-2560 | 시김새 / 노니로 | jungak_deageum_nina_vib_32.wav | 173.724396 | 17 |
| ngc-jeongak-2561 | 시김새 / 크레센도 | jungak_deageum_scale_chung_59.wav | 143.231000 | 18 |
| ngc-jeongak-2565 | 시김새 / 스타카토 | jungak_deageum_stacatto_48.mp3 | 17.856000 | 18 |
| ngc-jeongak-2627 | 연주 / 중광지곡 중 ‘타령’ | Jeongakdaegeum.wav | 72.011814 | 연주 전체 보존 |

기존 로컬 확보 원본을 바이트 그대로 복사해 해시·헤더·전체 길이를 검사했다. 원래 파일의 최초 취득일은 이번 기록에서 확정하지 않는다. 2026-10-23에는 공식 목록과 미리듣기 7개를 새로 받았다. 기본음은 WAV, 스타카토는 MP3 응답이 로컬 원본 해시와 같았다. 나머지 미리듣기는 별도의 MP3이므로 WAV 해시와 같다고 쓰지 않는다. 용도 정보 없는 다운로드 요청은 HTTP 200의 빈 응답이었으며, 이것을 원본 수신 성공으로 취급하거나 기존 파일을 덮어쓰지 않았다. 목록·응답 해시는 로컬 `research-v1/official-list.json`, `official-instrument.json`, `remote-checks.json`에 보존했다.

## 취법·율명 대응

[국립국악원 국악사전 「취법」](https://www.gugak.go.kr/ency/topic/view/1349)은 정악대금의 저취를 탁임종 B♭3~중려 A♭4, 평취를 임종 B♭4~청중려 A♭5, 역취 시작을 청임종으로 설명한다. 추가 반규 음역은 이 녹음에 자동으로 넣지 않았다. [2021 대금 정악보](https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=17409&boardtypeid=88&menuid=001003002002&pagesize=10)의 PDF 9쪽 안공표에서도 황종 E♭, 저취·평취·역취 구분과 율명 순서를 직접 확인했다.

이 근거에 원음의 오름차순 7음 `임·남·무·황·태·고·중`과 저취→평취의 반복을 대조했다. 기본음의 저취 7음과 평취 7음 사이 옥타브 편차는 −19.00~+9.93cent다. 뒤의 4음은 역취 `임·남·무·황`으로 이어진다. 노니로는 역취 무까지 17음이며 황을 임의로 추가하지 않았다. 같은 악기의 기본음을 기준으로 다른 주법을 대조했을 때 대표 F0 편차는 −32.47~+15.08cent였다.

| 취법 | 임(Hz) | 남(Hz) | 무(Hz) | 황(Hz) | 태(Hz) | 고(Hz) | 중(Hz) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 저취 | 238.618 | 274.189 | 293.795 | 323.727 | 360.387 | 403.230 | 439.715 |
| 평취 | 479.729 | 542.394 | 581.653 | 649.942 | 718.839 | 811.101 | 874.455 |
| 역취 | 961.732 | 1068.582 | 1144.665 | 1289.110 | — | — | — |

표의 Hz는 녹음 관측값이며 표준 조율값이 아니다. 예를 들어 저취 황 323.727Hz를 A4=440Hz의 최근접 12평균율 음명으로 바꾸면 공식 황종 E♭의 이름과 어긋날 수 있다. 이름은 공식 음역·율명 순서·옥타브 반복으로 추론하고 실제 Hz를 별도 보존했다. 실제 운지나 취구의 물리적 조절을 음원만으로 확정하지 않는다.

## 구간과 음높이 측정

로컬 분석 버전은 `tool:prepare_jeongak_assets@1.0`이며 사용한 스크립트의 SHA-256은 `73713d4e61595abc4b443b08960a0d47d623c72fdb08b2fec2a610e959d76dd2`다. Python 3.12.14, NumPy 2.3.5를 사용했다. WAV는 공통 exporter의 PCM16/float32 디코더, MP3는 SoundFile 0.13.1/libsndfile 1.2.2를 사용하며 MP3의 시각 0은 이 디코더가 반환한 첫 PCM 프레임이다. 별도 지연 보정은 하지 않았다.

1. 원래 샘플레이트에서 채널을 평균한다. 라벨 원음 6개는 모두 48kHz이며 리샘플링·음높이 보정을 하지 않는다.
2. 20ms RMS 창, 10ms 간격, `max(0.0001, p95(RMS)×0.025)` 게이트로 발음을 찾고 50ms 이하의 간격만 연결한다. 최종 후보 양끝에 20ms 문맥을 더한다. 85.33ms 창 기반의 이전 후보와 대조한다.
3. 85.33ms 창·20ms 간격의 YIN 계열 CMND 추정에서 100~2500Hz를 찾는다. 임계값 0.15, 주기성 `1−CMND≥0.85`의 프레임을 쓰며 양끝 최대 0.2초를 제외한 중앙값을 대표 F0로 보존한다. 짧은 스타카토는 양끝 각각 길이의 1/5을 제외한다. 주기성은 정답 확률이 아니다.
4. 각 발음 중앙의 최대 1초 Hann FFT 피크를 별도로 대조한다. 스타카토 두 개의 강한 제2배음을 제외하면 FFT 피크와 YIN 중앙값 차이는 최대 17.47cent였다. 그 두 개는 아래처럼 기본주기·제2배음을 재분석했다.

| 교정·확인 대상 | 실제 관찰과 처리 |
| --- | --- |
| 깊은요성 마지막 두 음 | 85ms RMS가 137.92~153.01초를 묶었지만 20ms RMS에서 무 137.96~145.84초, 황 146.00~152.99초로 분리된다. 0.16초 쉼과 서로 다른 F0로 두 발음을 유지했다. |
| 깊은요성 87.90~87.94초 | 40ms 저음량 후보에 유효 F0가 없고 85ms RMS는 게이트 아래였다. 라벨에서 제외했다. 앞 음은 87.73초 종료, 다음 음은 88.33초 시작으로 잡음을 통해 발음을 이어붙이지 않았다. |
| 노니로 중간 세 음 | 85ms RMS의 92.48~121.21초 후보를 무·황·태로 나눴다. 101.83~102.02초, 111.95~112.12초의 쉼과 각각 약 583/648/719Hz를 확인했다. |
| 스타카토 저취 태 | 중앙 0.20초 FFT의 기본음 359.985Hz, 제2배음 719.788Hz. 기본주기 자기상관 0.99471, 반주기 0.57292로 낮은 주기의 F0를 유지했다. |
| 스타카토 평취 임 | 기본음 476.807Hz, 제2배음 954.163Hz. 기본주기 자기상관 0.98160, 반주기 0.32040으로 낮은 주기의 F0를 유지했다. |

약한 구간만으로 하나의 발음을 임의로 나눈 사례나 같은 음의 별도 반복을 삭제한 사례는 없다. 이번 자료에서 같은 주법 안의 동일 취법·율명 반복 발음은 발견되지 않았다. 노니로 원음도 발음별 구간으로 저장하며 원음 전체를 최종 시김새 파일 하나로 복사하지 않는다.

상세 측정·재현 파일은 Git 제외 경로 `data/processed/instruments/daegeum/jeongak/research-v1/`의 `measure_jeongak.py`, `prepare_jeongak_assets.py`, `audit_jeongak.py`, `measurements.json`, `audit.json`, 원본 숫자별 `analysis.json`·`frames.npz`다. 재사용 YIN·검출 코드는 기존 산조 분석 Git `c163f2f`의 `analyze_basic_tones.py`·`analyze_digital_collection.py` 사본이며 산조의 음명·음 개수 매핑은 사용하지 않았다. `measurements.json`은 각 라벨의 실제 F0·p10/p90·FFT·기본음 대비 편차를 담고 있다.

## 타령 악보와 이용 조건

디지털 원음 7개에는 공식 악기 목록의 공공누리 제1유형이 적용된다. 원본·분할본 제공 시 국립국악원·자료명·원본 링크를 표시하고, 가공본에는 구간·채널 평균·PCM16 변환 및 필요한 피크 감쇠를 표시한다. 조건은 [공공누리 이용 안내](https://www.kogl.or.kr/info/introduce.do)와 개별 자료 표시를 따른다.

| 확보 자료 | 확인 범위 | 이용 조건·대응 상태 |
| --- | --- | --- |
| [대금 정악보, 2021 수정본](https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=17409&boardtypeid=88&menuid=001003002002&pagesize=10) | PDF 전체 355쪽 수신. PDF62~63의 영산회상 타령 1~4장을 시각 확인. PDF62에는 앞 곡의 끝이 함께 있고 PDF64는 군악 시작이다. | 공식 상세 페이지 공공누리4. 출처표시·비상업·무변경 조건으로 원본 전체를 복사하며 페이지를 잘라 새 PDF로 만들지 않는다. |
| [한국음악 제5집: 중광지곡, 천년만세](https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=1294&boardtypeid=12&menuid=001003002002&pagesize=10) | PDF 전체 128쪽 수신. PDF81~88(인쇄82~89)의 타령 1~4장 합주 오선보, 대금 성부 확인. PDF89는 군악 시작이다. | 공식 상세 페이지 공공누리4. 원본 전체를 같은 조건으로 복사한다. |

두 악보는 타령의 비교 자료이며 72.01초 녹음과 같은 편곡·판본·장·반복·장식음의 전체 대응을 검증하지 않았다. `scores=[]`, `comparisonScores`에만 넣었고 `samePerformanceEditionVerified=false`, `noteAlignmentVerified=false`를 유지한다. 서비스용 전사·변형·채점 정답으로 승인하지 않았다. [아카이브 T014947](https://archive.gugak.go.kr/portal/detail/searchEtcDetail?clipid=T014947&recording_type_code=T&system_id=AI)의 옛 다운로드 링크는 `boardtypeid=12`로 실패했고, 현재 공식 목록에서 확인한 `boardtypeid=88` 상세 페이지·다운로드 링크로 수정본을 확보했다.

## 실행과 검증

저장소 루트에서 [공통 준비·선택 옵션](../../../data/README.md)을 사용한다. 출력은 아직 없는 폴더여야 한다.

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/jeongak-daegeum --output-dir data/processed/instruments/daegeum/jeongak/prepared-v2
python data/src/audio/export_dataset.py --dataset data/catalog/jeongak-daegeum --technique 스타카토 --register 역취 --yulmyeong 황 --output-dir data/processed/instruments/daegeum/jeongak/selected-staccato-hwang-v2
python data/src/audio/export_dataset.py --dataset data/catalog/jeongak-daegeum --performance ngc-jeongak-2627 --output-dir data/processed/instruments/daegeum/jeongak/selected-taryeong-v2
```

- 실제 전체 추출: 107개 PCM16 WAV, 원본 타령 WAV 1개, 원본 PDF 2개, `catalog.json` 생성.
- 전체 6원음의 유효 피치 프레임 35,875개가 모두 라벨 구간 안에 있었다. 시작·끝의 원음 프레임 범위, 원본별 구간 비중복, ID·출력 경로 충돌을 확인했다. 이는 검출한 피치의 누락 검사이며 음악적 정답 검수와 구분한다.
- 실제 기본음 저취 임 선택 및 스타카토 역취 황 선택, 타령 연주 선택을 확인했다. 선택 결과와 전체 결과의 동일 구간 해시, 연주·악보 원본 복사 해시를 대조했다.
- 최초 라벨 작업에서는 같은 기준 코드의 공통 exporter 39개 회귀 검사를 상위 작업 담당자가 실행해 통과했다. 라벨을 준비하면서 공통 exporter·schema·tests를 수정하지 않았다.

## 출처 목록 축약과 v2 호환 검증

공통 로더가 포함된 develop `a981ef2`를 통합한 `e01f271`에서 `sources.json`만 입력 schema v3로 바꿨다. 음원 7개에서 값이 모두 같은 `kind`, `channels`, `sourceUrl`, `publisher`, `license`, `verifiedOn`, `redistribution`을 `ngc-jeongak-audio` 프로필 한 개로 묶었다. ID·제목·SHA-256·크기·샘플레이트·길이·개별 다운로드 주소·출처 표시·수집 경위·원본 및 라벨 경로는 각 원본에 남겼다. 공공누리4 조건의 PDF 2개는 프로필 없이 기존 개별 객체를 그대로 유지했다. 프로필 규칙과 v2·v3 읽기 방법은 [공통 JSON 안내](../../../data/README.md#json-형식과-악기-추가)를 따른다.

UTF-8·LF 개행으로 맞춰 비교하면 **248줄·13,236바이트 → 175줄·10,783바이트**다. 단순 줄바꿈 차이를 절감량에 넣지 않았다. `load_source_catalog`로 펼친 원본 9개의 전체 객체와 목록의 나머지 내용은 입력 `schemaVersion`을 제외하고 v2와 정확히 같다. 라벨·F0·샘플레이트를 다시 분석하거나 바꾸지 않았다.

검증 자료는 Git 제외 경로 `data/processed/instruments/daegeum/jeongak/source-profiles-v1/`에 있다. 기존 입력의 바이트 사본은 `sources-v2.json`, 전후 크기·해시는 `conversion.json`, 파일별 경로·크기·해시 및 비교 결과는 `verification.json`이다. 같은 통합 버전의 exporter로 v2 입력을 먼저 `before-v2/`에, 변환 후 v3 입력을 `after-v3/`에 전체 저장했다.

- 전체 110파일(발음 107 WAV·타령 원음 1 WAV·원본 PDF 2)의 상대경로와 **371,438,623바이트**가 모두 같다. 파일을 직접 바이트 비교하고 SHA-256도 기록했다.
- 두 출력 `catalog.json`을 재귀 비교한 차이는 `manifestHashes["catalog/jeongak-daegeum/sources.json"]` 한 곳뿐이다. 출력 schema v2, 펼친 원본 9개, 라벨 107개, 연주·비교 악보·디코더·변환 정보는 같다.
- `--technique 기본음 --register 저취 --yulmyeong 임` 선택을 `selected-basic-im-v3/`에 저장했다. `ngc-jeongak-2557.tone-001` 한 개의 라벨 전체와 WAV 바이트가 전체 출력의 같은 항목과 일치한다.
- `python data/src/catalog/verify_sources.py --dataset jeongak-daegeum`으로 원본 크기·SHA-256 **9/9 일치**를 확인했다. `python -m unittest discover -s data/tests -p 'test_*.py' -v`는 **51개 통과**했다. 새 로더로 실제 기존 v2 입력과 변환 후 v3 입력을 모두 실행했으며, 공통 코드와 다른 악기 목록은 추가 수정하지 않았다.

남은 검수는 연주자의 취법·율명 독립 확인과 녹음-악보의 판본·음표 대응이다. 라벨 구조와 측정 근거를 갖춘 추정 데이터로 제공하며 채점 정답으로 승격하지 않았다.
