# 오선보 인식 오류 교정과 빠르기 인식

## 숫자 빠르기와 가속 가져오기 (882)

2026-11-13, S15P31E102-882, Story319. 기준은880이 병합된 `a89053c4`다. [스펙](../../specs/staff-tempo-import.md). 기존 구간 빠르기를 재사용하며 새로운 악보 형식은 만들지 않는다.

| 영역 | 확인한 지원 범위 |
| --- | --- |
| JGB | 기본 숫자 BPM·빠르기 기호가 있다. `tempo_repeat.py`의 해시 결합 숫자 계획은 step·점진 변화·반복을 지원한다. 원문 기호만의 교육용 기본 근사와 명시 숫자 계획을 구분한다. |
| 공통 악보 v1 | 단위·기보 tick은 원본에 두고 숫자 구간은 형제 `expressions.performancePlan`으로 보존한다. 시간표만으로 원문 숫자를 복원하지 않는다. |
| 앱 편집 | 기본 BPM·단위 표시, companion 왕복·기본 BPM 비례 변경, 숫자 계획이 있는 악보의 위치 편집 제한을 이미 지원한다. 숫자 구간을 새로 만들거나 악보 위에80/160·accel.을 표시하는 UI는 이번에 추가하지 않았다. |
| 모범 재생 | 이미 생성된 음원·수행표의 변속을 재생한다. 숫자 계획이 없는 예전 음원에 새 빠르기를 추정해 입히지 않는다. |
| 내 악보 음원 | `SheetAudios`→`prepared_input`→`PerformanceClock`이 선율·장구·수행표를 같은 시계로 만든다. 새 인식 companion도 이 경로를 사용한다. |

### 끊긴 지점과 수정

AI는 빠르기 표시가 음표로 섞이는 경우의 마스킹만 있었고 metronome/sound 숫자를 읽지 않았다. BE의 `RecognitionResultBuilder`도 요청 BPM 또는 장단 기본값만 사용했고 변환 저장 SQL은 companion을 저장하지 않았다. 숫자·단위·위치를 추출해 기존 companion으로 보내고 서버 제목/식별자/provenance 변경 뒤 두 SHA를 재결합한다. 요청 BPM 우선은 유지하며 요청이 없을 때 인식 BPM이 기본값보다 우선한다.

사용자는80 표시 위치부터 다음160까지 기보 위치에 대해 선형으로 가속하기를 선택했다. 기존 형식은 앞160을80으로 즉시 바꾼 뒤 같은 위치에서 가속할 시작값이 없어, 선택적 `start_bpm` 추가도 별도 승인했다. 기본 BPM 변경은 시작/목표 모두 비례 조정한다. 처음 미해결 음 초안은 확정 score가 없으므로 불변 authoring 원본을 해시 기준으로 읽는다. 음고 수정·자동 저장2회 후80→120 확정 저장에서도 시작120/목표240을 한 번만 적용하는 실제 DB 회귀를 보존했다. 원본 snapshot은 수정하지 않는다.

MusicXML의 metronome은 표시 음가를, `sound.tempo`는4분음표/분을 사용한다. [W3C metronome](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/metronome/), [sound](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/sound/). 정간/분으로 변환하고 못갖춘마디·반복·쪽 결합도 음표와 같은 시간축을 사용한다. 범위·단위 미상·정간 안쪽·숫자 상충·가속 끝 불명은 임의 보간하지 않는다. 정간 안쪽 숫자를 건너뛰어 더 먼 목표로 가속하는 것도 금지한다.

### 대표 PDF 실행

사용자가 제공한4쪽 PDF를 격리된 로컬 OMR HTTP에 넣었다. 원본·추론 산출물은 Git에 넣지 않았다. 실제 Audiveris5.11.0·Python3.12.16·Node18, CPU2개·메모리2GiB, 기존 legacy 영어 tessdata를 사용했다. 소스는 이 worktree에서 읽기 전용 마운트하고 외부 내려받기만 로컬 파일 복사로 대체했다. 모델·JGB 생성기는 실제 실행했다.

Audiveris 원출력은 숫자3곳만 metronome으로 내고 다른160은 누락하거나 `J 160`이라는 일반 글자로 냈다. 두80의 위치도2박 뒤였다. PDFium 글자층의 명시 `q = 숫자`와 Audiveris 마디 첫 타점을 대조해 누락2곳·위치2곳을 보완했다. 위치가 유일하지 않으면 보완하지 않는다.

| 대조한 표기 | 최종 숫자 계획 |
| --- | --- |
| 첫머리 ♩=160 | 1각1정간160, 인식 기본 BPM160 QUARTER |
| 26마디80·molto accel. →30마디160 | 26각1정간 `start_bpm:80`, 목표160, 끝30각1정간 |
| 58마디80·molto accel. →62마디160 | 58각1정간 `start_bpm:80`, 목표160, 끝62각1정간 |

53.38초에 DONE, 숫자5곳·가속2구간이 대조 위치와 일치했다. PDF SHA256은 `07efe4304d2e8e9a81ed9cc33343d8ccf2a499107aa9ec2e81cce926de967c54`다. 같은 입력의 변경 전후 JGB·공통 악보는 전체 동등하다. 이번 실행은118각·526이벤트·NOTE501개이며, 기존 사용자 악보의119각·503음 편곡과 별개다. 전곡 채보 정확도·음질 검수로 확대하지 않는다. 기존 계정·악보32·정간보20·초안 revision7·불변 원본은 조회/수정하지 않았다.

실제 응답을 JVM `RecognitionResultBuilder`에 넣어 숫자5곳과 원음/위치 보존·SHA 재결합을 확인했다. 그 서버 결과를 실제 생성 사전검사에 넣어501음·656수행 구간, 선율 수행 길이181.635520833초를 확인했다. 이는 sample 없는 컴파일/시간표 검사이며 이번 입력의 CAT M4A 생성·스피커 청취를 수행한 근거는 아니다.

### 검사와 재현

- OMR: `python -m pytest ai/tests/unit/test_staff_tempo.py ai/tests/unit/test_staff_pdf_tempo.py ai/tests/unit/test_staff_musicxml.py ai/tests/unit/test_omr_staff_engine.py ai/tests/unit/test_staff_source_pitches.py ai/tests/unit/test_staff_audiveris_fixtures.py ai/tests/unit/test_omr_api.py` —162통과. 실제 JGB 생성기를 연결한 HTTP 포함.
- DATA: `python -m pytest data/src/playback/tests` —264통과·인코더 조건부3제외. `start_bpm` 미지원 실패→통과, 기존step/ramp·반복·장구 타점·역수 적분 회귀 포함.
- BE: `gradlew.bat test` —756개, 실패/오류0·제외1. 폐기 MySQL8.4.13에서 `databaseTest --tests '*ConversionQueueDatabaseTests' --tests '*ConversionResultDatabaseTests'` —12통과. companion 저장 누락과 최초 초안 수정 저장 거절의 실패→통과 확인.
- FE: `gradlew.bat :shared:testAndroidHostTest` —1651개, 실패/오류0·제외5. 시작 BPM을 포함한 companion의 실제 Repository 읽기/쓰기와 기존 반주·편집 회귀다. 실기기·iOS·UI 청취 검증은 수행하지 않았다.

로컬 근거는 `staff-tempo-882/.scratch/882/`의 `runtime/response.json`(변경 전), `runtime-final/response.json`(최종), `run_pdf.py`, `replay_pdf.py`, `RealTempo882Tests.kt`, `prepared-server.json`, `performance-map.json`, `omr-tests.xml`, `playback-tests.xml`에 보존한다. Python 검사는 기존 OMR 이미지에 pytest·mido·jsonschema를 일회용 `/tmp/testdeps`로만 추가했다. DATA 전체 검사 때 필요한 FE 기호 파일도 컨테이너 안에 복사했다. 호스트 설치·개인 설정은 변경하지 않았다. JVM 실행 helper는 검사 때만 test source로 복사하고 이후 제거했다. 원본 PDF·MXL·전체 응답의 재배포 허가는 확인하지 않았으므로 커밋하지 않는다.

2026-11-13 읽기 전용 SSH 조회에서 실행 중 OMR 이미지 revision은 `aa9b92ac4984059d3a3b3c84a42b37089ee82744`, playback은 `d03fd028c6c330a782fca35f7037b9377209ff09`였다. OMR 파일 SHA는880 병합본과 다르고4박 후보 코드도 없었다. 따라서880의 Git 병합과 실제 OMR 배포를 구분한다. 이번 변경은 미게시·미배포이며, 새 시작 BPM을 읽는 생성기와 BE·OMR을 함께 반영해야 한다.

## 앞선 교정과 로컬 대조 (806)

관련 Task는 S15P31E102-806, Story는 S15P31E102-318이다. 2026-11-12 사용자의 “오선보 오류만 먼저 수정” 답변에 따라 스캔 정간보 연결(458)은 제외했다. [교정 기준](../../specs/score-recognition-corrections.md)이 구현 범위다.

## 원인과 변경

- 작은별의 빠르기 표시 음표를 Audiveris가 선율의 A5/B5로 읽었다. 컨테이너에는 OCR 언어 데이터도 없었다. Tesseract legacy 영어 가중치를 체크섬으로 고정하고, OCR로 확인한 숫자 빠르기 지시문 안에 음표 머리가 섞이면 별도 그림에서 그 영역만 제외해 다시 읽는다. 원본은 보존한다.
- 프레르 자크는 높은 지속음 반주 16음을 골랐다. 곡 길이 절반을 덮는 후보에서 높은 성부의 본음 시작 수보다 두 배 이상 활동적인 성부가 있으면 그 성부를 고른다. 붙임줄·화음은 시작 하나다. 비슷한 활동량은 기존 높은 성부 선택을 유지해 기존 4곡 회귀를 보존했다.
- Amazing Grace는 작은 음표·빔 인식이 꺼져 꾸밈음이 본음에 섞였다. 두 옵션을 켜고 본음·꾸밈음을 구분한다. 짧은 음의 빔을 놓쳐 마디가 길어지는 경우에는 1.25배 그림을 한 번 더 읽는다. 본음과 주변 꾸밈음을 유일하게 대응할 수 있는 한 선율에서, 원본 음고와 꾸밈음 구분을 유지하며 초과 마디의 음가만 교정한다. 정상 마디는 보존한다. 단순히 마디 길이를 비례 축소하거나 곡 이름으로 정답을 넣지 않는다.

## 원본 대조 결과

실제 Audiveris 5.11.0을 실행했다. Noctave [작은별](https://noctavepiano.com/en/sheet-music/trad-ah-vous-dirai-je-maman/)·[환희의 송가](https://noctavepiano.com/en/sheet-music/beethoven-hymne-a-la-joie/)·[프레르 자크](https://noctavepiano.com/en/sheet-music/trad-frere-jacques/)의 제공 MusicXML, Mutopia [Amazing Grace](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1832)의 제공 LilyPond 및 MIDI와 별도 비교기로 대조했다. 기대값 생성에 제품 변환 함수를 사용하지 않았다. 단위는 MIDI 반음과 4분음표 길이의 유리수다. Amazing Grace는 꾸밈음의 MIDI 연주 지연을 제외한 기보 본음 47개를 대조했고, 서비스가 붙이는 못갖춘마디 앞 쉼표 3박을 반영했다.

| 입력 | 교정 전 | 최종 PDF | 최종 이미지 |
| --- | --- | --- | --- |
| 작은별 | 음높이 41/42 | 음높이·시작·길이 42/42 | PNG·JPEG 각각 42/42 |
| 환희의 송가 | 62/62 | 음높이·시작·길이 62/62 | PNG 62/62 |
| 프레르 자크 | 반주 16음 선택 | 낮은 선율 64음, 음높이·시작·길이 64/64 | PNG 64/64 |
| Amazing Grace | 본음 47개 대신 63개 | 본음 47개, 음높이·시작·길이 47/47 | PNG 47/47 |

총 9개 입력 모두 원본 본음의 MIDI·시작·길이가 일치했다. 이는 이 네 판본의 결과이며 일반 OMR 정확도 수치가 아니다. 성부·장단 추정, 꾸밈음 생략 안내와 악기별 운지 미등록 표시는 남는다. 특히 프레르 자크의 낮은 원음은 단소 표시 범위를 벗어나 원음 보존과 실제 운지 지원을 구분해야 한다.

바흐 BWV1013 5쪽은 초기 교정 실행에서 DONE 및 편집 저장을 확인했으나 원본의 전곡 음표 대조를 완료하지 않았다. 반복·박자 변경을 포함한 전곡 정확도는 미승인이다. 정간보 스캔 3개는 이전과 같이 미지원이며 이번 수정 대상에서 제외했다.

## 실행 조건과 근거

- 기준: origin/develop `98d4d593`, 별도 `score-recognition-806` worktree. 원본 파일·모델은 Git에 넣지 않았다.
- 엔진: 기존 OMR 런타임 이미지 `70b2e1dbee82…`에 수정한 AI/DATA 소스를 읽기 전용으로 마운트. Audiveris 5.11.0, Python 3.12.16, PDFium 5.14.0, Node 18. CPU 2개·메모리 2GiB, 읽기 전용 파일시스템과 `/tmp` tmpfs에서 실제 API를 실행했다. 쪽당 60초 예산을 보존했다.
- 추가 언어 데이터: [Tesseract tessdata 4.1.0 영어](https://github.com/tesseract-ocr/tessdata/blob/4.1.0/eng.traineddata), SHA-256 `daa0c97d651c19fba3b25e81317cd697e9908c8208090c94c3905381c23fc047`. [Audiveris TesseractOrder](https://github.com/Audiveris/audiveris/blob/5.11.0/app/src/main/java/org/audiveris/omr/text/tesseract/TesseractOrder.java)는 legacy 엔진을 사용하므로 `tessdata_fast`는 사용할 수 없다.
- [작은 음표·빔 옵션의 기본값](https://github.com/Audiveris/audiveris/blob/5.11.0/app/src/main/java/org/audiveris/omr/sheet/ProcessingSwitches.java)은 둘 다 false다. [빠르기 인식 설명](https://audiveris.github.io/audiveris/_pages/guides/specific/metronome/)과 실제 `.omr`의 지시문·머리 상자를 함께 확인했다.
- 실제 API 업로드·worker·SQL·초안 변경·저장·재조회: 로컬 MySQL 8.4.13, Spring 실제 HTTP, 실제 OMR HTTP. 최신 네 곡에서 DONE·기대 음 수·200 저장·재조회 동등성·원본 snapshot 보존·임시 원본 삭제를 검증했다. 운영 DB는 사용하지 않았다.
- 프론트 호스트 테스트: 최신 실제 결과의 무편집 왕복, 음 하나 변경, 나머지 모든 event와 시간·provenance 보존, JSON 재열기를 확인했다. 캐시 결과를 피하려 `--rerun`으로 실행했다.
- OMR 관련 자동 검사 176개 통과. 전체 AI 검사는 최초 실행 423개 통과·37개 실패였다. 이 중 OMR 응답 필드 기대값 누락 1개는 보완해 위 176개에 포함했다. 나머지는 OMR 전용 이미지의 ffmpeg/ffprobe 부재와 Windows 전용 실험 테스트 환경 문제로 전체 AI 성공을 주장하지 않는다.
- 전체 OMR Dockerfile 재빌드·운영 배포·iOS 실기기 검증은 하지 않았다. 이 작업의 실제 런타임 검증은 동일 이미지에 소스·가중치를 마운트한 로컬 경로다.

로컬 근거 루트는 `C:/Users/SSAFY/.codex/worktrees/score-test-assets-801/data/processed/score-test-assets-801/runtime/fix806/`이다. `quality-report.json`, `final/backend/summary.json`, `final/backend/frontend-edit-report.txt`, `backend-final.log`, `frontend-final.log`, `omr-tests.log`와 캡처된 실제 MXL/응답을 보존했다. 원본·임시 진단 코드·모델은 이 제외 경로에만 둔다.

재실행은 AI 폴더의 `uv run pytest tests/unit/test_omr_api.py tests/unit/test_omr_staff_engine.py tests/unit/test_omr_text_engine.py tests/unit/test_staff_musicxml.py tests/unit/test_staff_source_pitches.py tests/unit/test_staff_audiveris_fixtures.py`를 사용한다. 실제 원본 대조와 BE/FE 재현 하네스는 위 근거 루트의 `harness/`에 보관하며 테스트 원본의 출처·이용 조건·해시는 Downloads 테스트자료의 `manifest.json`에 보존했다. 공식 독립 리뷰는 아직 수행하지 않았다.
