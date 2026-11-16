# 오선보 인식 오류 교정과 빠르기 인식

## 2026-11-14 제공 PDF의 첫160 누락과 운영 버전 차이 (882 재개)

사용자가 제공한 `BlueVal_261114_091621.pdf`는4쪽·74,641bytes, SHA256 `5aa7f7af037acdb7540071b187cdec5923a4d51f4521861828b129ec98321f1d`다. 기존11월13일 성공 PDF와 바이트가 다르므로 새 입력으로 대조했다. 원본·페이지 그림·전체 인식 응답은 커밋하지 않았다. 재개 승인·브랜치·현재 작업 상태는 [Jira882 계획](../../plans/S15P31E102-882.md)의11월14일 절이 기준이다.

글자층의 `q`(OpusTextStd)는 실제4분음표 글리프이며 숫자5개와 `molto accel.`2곳을 읽을 수 있다. 실제 Audiveris/Node 경로의 수정 전 결과는 기본 BPM이 없고26→30,58→62의80→160 가속 및 두 도착160만 있었다. 첫 마디의 조표·박자표 때문에 zero-time 첫 타점x453보다 숫자x328.346이 왼쪽에 놓였고, 둘의124.654px 거리가 기존4오선 간격76px 한계를 넘었다. 같은 마디의 시작 여백을 첫 타점과 같은 위치로 묶지 못한 결함이었다.

동일 마디 시작선부터 첫zero-time 타점 앞의 여백도 유일한 후보일 때만 허용하도록 보완했다. 오선 위의 위치·마디 범위·zero-time·최근접 첫 타점·마디 수 일치·후보 하나·명시 숫자 상충 검사는 유지한다. 마디 중간이나 모호한 표시를 첫 BPM으로 가정하지 않는다. 축소 geometry 회귀는 수정 전1실패/6통과에서 수정 후7통과했고, 관련 숫자·리듬·오선보 회귀128개도 실패·제외 없이 통과했다.

제공 PDF 전체를 격리 FastAPI TestClient 경계에서 실제 Audiveris·Node로 재실행했다. 외부 다운로드만 제공 파일의 로컬 복사로 대체했고 소스와 PDF는 읽기 전용 마운트했다. 수정 후61.898초 HTTP200/DONE, 인식 기본160, 계획5항목(1각160;26각 start80→160/끝30각;30각160;58각 start80→160/끝62각;62각160)을 확인했다. 공통 악보의518이벤트와 JGB는 수정 전후 완전히 같았다. 독립 리뷰는 추가 결함을 찾지 못했다. 전체 채보 정확도·CAT 음질·실기기 청취를 확인한 근거는 아니다.

이 응답을 실제 BE 저장 준비·SHA 재결합과 DATA 사전검사에 연결해518이벤트와492개 source MIDI pitch 보존·5숫자 계획 보존·SHA 결합·입력 불변을 확인했다. 시간표는657구간·181635.521ms이며,26→30과58→62의80→160 가속은 각각8317.760ms로 반영됐다(160고정 대조6000ms). `downstream-verification-summary.json`에 조건·파일 SHA·수행표 비교와 한계를 남겼다. 실제 인식 기본값160을 사용했으며 누락을 가리기 위해 임의 기본값을 넣지 않았다.

운영 읽기 전용 조회에서는 backend JAR이 `d84dd9b9`인 반면 OMR은 `8897d7fb`이며 `staff_pdf_tempo.py`가 없었다. playback은 `d03fd028`이며 `tempo_repeat.py`에 `start_bpm` 처리가 없었다. 두 컨테이너가 healthy여도 숫자 빠르기 지원이 반영된 상태는 아니다. 첫 숫자 보완과 별개로 운영 런타임 반영 누락이 있어, 코드 수정만으로 현재 앱의 새 인식·생성 요청이 교정됐다고 보고하지 않는다.

반영 시에는 새 시작값을 읽는 playback을 먼저 준비하고 숫자 계획을 만드는 OMR을 다음 순서로 검증해야 한다. backend는 최신 버전이며 기존 사용자 악보·초안·생성 음원을 임의 갱신하지 않는다. 후속 사용자 승인으로 MR 게시와 사람의 리뷰·병합 후 운영 두 서비스 반영까지 진행한다. 운영 이미지 교체·서비스 재생성은 아직 수행하지 않았다. 로컬 근거는 `.scratch/bv1114/inspection.json`, `first-geometry.json`, `header-{red,green}.log`, `ai-regression.xml`, `pipeline-{before-,}response.json`, `pipeline-summary.json`에 있다. 앱 업데이트로 최종 stdout 수신은 중단됐지만 완료된 응답·요약 파일과 원본/악보 불변 비교를 재조회했다.

MR !739 게시 후 운영 서버에서 `24273759` 소스로 두 후보 이미지를 별도 빌드했다. 기존 서비스·Compose·자산은 보존하고, 같은 리소스 제한의 내부망 임시 컨테이너에서 실제 HTTP로 검증했다. 최종 scanner 이미지의 PDFium5.15.2·torch2.15.0+cpu와 핵심 소스4파일의 Git SHA 일치를 확인했다. 제공 PDF는99.195초 DONE·기본160·5항목·518이벤트/JGB 일치, 기존 율명 음역 대조 PNG는14.418초에 독립 정답24/24였다.

새 playback의 실제 CAT 생성은20.615초에 성공했다. 추정과 생성 모두182186ms이며 release tail을 뺀 수행 시간표는181635.521ms·657구간으로 일치한다. 생성 MIDI492음의 음높이·시점과 AAC-LC mono44100·음원 SHA·수행표 SHA 결합을 대조했다. 실제 청취·기기 실행·기존 사용자 저장 악보 수정은 하지 않았다. 후보 검사와 실제 운영 반영을 구분하며, 새 이미지를 운영에 연결하기 전 사람 승인·병합을 확인한다. 원본에 빠르기 계획이 저장되지 않은 예전 변환/음원은 서비스 교체만으로 자동 수정되지 않아 재인식·재생성이 필요하다. 후보 근거는 `.scratch/bv1114/candidate-results/`와 `probe-{omr,playback}.log`다.

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

## PDF 음표 글리프의 숫자 빠르기 누락 (935)

2026-11-15, [S15P31E102-935 계획](../../plans/S15P31E102-935.md). QA931은 배포 Android QA372(a2983834)에 AuClair PDF를 실제 업로드해 최초160BPM·24초를 관찰했다. 원본은100Q BPM·64Q=38.4초다. 이후 사용자가100으로 수정한 저장·음원38.95초 성공은 최초 자동 인식 성공이 아니다. 기존 [박자 가져오기 검증](../data/common/rhythm-metronome/meter-import-validation.md)에도 같은 제한이 기록돼 있다.

원본 `02_AuClairDeLaLune_양손피아노.pdf`를 PDFium5.15.2로 읽으면 `Moderato\ueca5 = 100`이다. Type3 폰트의 PDFium 이름은 빈 문자열이지만 PDF FontDescriptor의 FontFamily는 Leipzig, ToUnicode는 byte09→U+ECA5다. [SMuFL 메트로놈 표](https://smufl.formats.music/latest/tables/metronome-marks.html)는 ECA5/ECA6을4분음표, ECB7을점으로 정의한다. 임의 PUA 전체를4분음표로 치환하거나 폰트 이름을 추정하지 않고 이 명시적 표기만 추가했다. PDF의 Unicode 매핑 자체가 잘못된 임의 폰트까지 의미를 검증한 것은 아니다.

- 수정 전 `read_marks`는 `[[]]`, 수정 후 한 표시 `{x:148.53569, bottom:101.72937, bpm:'100', dots:0, accel:false}`를 읽는다(원본 PDF 좌표, 렌더 폭에 맞춰 확대).
- 자체 Type3 PDF를 메모리에서 만들어 실제 PDFium으로 읽는다. SMuFL/일반4분음표·점·소수·가속, 다른 음표 단위·임의PUA·범위·분수·쉼표·단어 안q·별도 줄 숫자·인접 숫자를 검사한다. 소스 악보·폰트 파일은 테스트에 복사하지 않았다.
- 같은 마디에 PDF100/120이 동시에 있으면 기존 코드는 입력 순서의 첫 값을 선택했다. 이제 보충 전 마디별 후보를 모아 상충 숫자는 거부한다. 기존 MusicXML 우선, 같은 값의 중복, 다른 마디 값, 중간/모호한 위치, 음표 불변을 함께 검사한다.
- 원본 AuClair와 자체PDF를 실제 FastAPI 경계로 넣어 `DONE`, `bpm=100`, 첫 정간 tempo100, `TEMPO_PDF_TEXT`, 음표 시간축 불변을 확인했다. 다운로드와 Audiveris subprocess는 통제된 경계이며, Audiveris 출력은 자체 작성1마디 MusicXML/기하다. 실제 PDFium 읽기·렌더·MusicXML→공통악보·JGB 생성·HTTP 응답을 실행했으나 원본16마디 전체 OCR의 성공을 증명하지 않는다.
- BlueVal 원본4쪽은 수정 전후 읽힌 다섯 값160/80/160/80/160, 좌표, 가속 두 곳이 모두 동일하다. 이 비교는 이미 읽던 표시의 보존 근거이며 새 전체 모델 실행이 아니다.

실행환경: Windows Python3.12.5, 기존 `C:/higher/tmp/score-zip-784/venv`, PDFium5.15.2, `JGB_GENERATOR_DIR=C:/higher/projects/jeongganbo-generator`. AI 폴더에서 `PYTHONPATH=src;../data/src`와 README의 원본 opt-in 환경변수로 실행했다. Git 기준은 edf90cc3이며 모델·의존성을 새로 설치하지 않았다. 원본은 Downloads의 지정 폴더에 그대로 있고 비교 JSON만 작업 worktree의 `.scratch/935/source-read-comparison.json`에 보존한다.

집중 회귀31건·HTTP2건 통과. 첫 수정 전 숫자/글리프10건, 충돌 보완 전3건의 실패를 관찰했다. 확장 회귀 도중 C: 여유0 및 WinError112로 임시파일 생성이 실패했으며 이 실행은 제품 통과로 세지 않는다. 최종 회귀·독립 리뷰 결과는935 계획에 기록한다. 배포판의 최초 raw Audiveris MusicXML은 확보하지 못했으므로 최초 OCR누락 전체 원인은 단정하지 않는다. 배포 후 실제 폰에서 서버 수정 효과는 미검증이다.

### 실제 Audiveris 후속 검증 (935)

사용자의 원본 전체 검증 요청으로3371f728을 실제 Audiveris에 연결하자16마디44음64Q는 정확했지만 최종BPM은 누락됐다. 글리프 위치만 쓰면 두 번째 타점에 가까워 마디 첫 빠르기 보충이 거부됐다. 원본에서 바로 붙은 `Moderato`의 왼쪽은 첫 타점 앞이다. 직접 확인한 이 단어에 한해 개행·단어 경계·실제 글자 간격·수직 겹침을 검사해 전체 표기의 왼쪽으로 정렬한다. 다른 단어를 새로 추정하거나 타점 거부 조건을 완화하지 않는다. 가속은 기존 음표 좌표를 따로 유지해 연결한다.

| 원본 전체 실제 경로 | 최종 관찰 |
| --- | --- |
| 원본 | Downloads의 지정 AuClair PDF 그대로, SHA256 `a92a876f6327e0a22941cb6ed03be141c94c400089be649079c13c17a8f5d937` |
| 실제 인식 | PDFium5.15.2,300dpi→Audiveris5.11.0 종료0. 원시 XML16마디, PDF 보충1건 |
| 변환 응답 | HTTP200/DONE,100 BPM(QUARTER), 첫 정간 tempo100, `TEMPO_PDF_TEXT` 검토 알림 |
| 원본 보존 | 출판사 MXL voice5와44음 음고·시작·길이 exact,16각64칸64Q. 최초 실패 응답과 score/sourcePitches 동일 |
| 실제 재생 전처리 | 응답값 그대로 `authoring_tempo.preflight`: 수행·기보·MIDI38.4초,64구간 시간표38400ms. 독립600ms/박 계산 대비 최대 오차7.28e-12ms |
| 최종 응답 SHA256 | `47221c74f2a7e2dcaecee3a00578b836b186541a13b75d9c1a0f7ea160b2d08c` (2차·최종3차 응답 동일) |
| 회귀·리뷰 | 관련213검사 통과, 위치 변경에 따른 accel 회귀1건을 독립 리뷰에서 찾아 수정 후3축 통과 |

로컬 Docker Desktop의 기존 `eolssu-omr:zip784-final` 이미지 ID `sha256:45285fa82953d05d90748f8e04f0922730e1c24b45d0092b982167488534c836`를 사용했다. Python3.12.16, CPU2·메모리2GiB·512MiB exec tmpfs, 네트워크 없음·포트 없음·읽기 전용 루트다. 현재 AI/DATA 소스·원본을 읽기 전용 bind로 연결했고 이미지나 공유 서버를 바꾸지 않았다. 기존 이미지의 eng.traineddata 읽기 권한 문제는 같은 파일을 작업 전용 경로에 추출해 읽기 전용 bind로 연결했다(SHA `daa0c97d651c19fba3b25e81317cd697e9908c8208090c94c3905381c23fc047`, 저장소 Dockerfile의 고정값과 일치). 새 모델·이미지 다운로드는 없다.

실행은 실제 FastAPI `POST /v1/recognitions`이며 다운로드만 로컬 원본으로 공급했다. Audiveris subprocess나 기하·MusicXML·변환기는 대체하지 않았다. 관찰용 wrapper는 실제 subprocess를 호출한 뒤 raw MXL/OMR·stdout/stderr를 복사하고 원래 결과를 그대로 반환한다. 쪽당60초 예산은 유지했고 최종 요청은 약9.96초에 종료했다. Docker의 `--rm` 종료와 작업 컨테이너 부재를 확인했다.

Audiveris stdout에는 beam 높이11 추정 경고와 `MetronomeInter invalid-internals`, 음표 단위가 null인 MusicXML export 예외가 있다. 원시 XML에 tempo가 없는 이유를 이번 로컬 실행에서 직접 확인했으며, PDF 글자층 보충이100을 복원했다. 경고가 없었다거나 배포 당시 raw OCR도 동일했다고 단정하지 않는다. 음표44개는 독립 출판사 정답과 대조해 보존을 확인했다.

로컬 근거 루트는 `C:/higher/qa-evidence/S15P31E102-935/`다. `run_actual_auclair.py`는 관찰 스크립트, `actual-auclair/`는 첫 실패, `actual-auclair-fixed/`는 위치 보완 후 성공, `actual-auclair-final/`은 리뷰 보완 후 최종 결과다. 각 폴더에 `request.json`, `response.json`, `receipt.json`, `audiveris-0/page-0.mxl|.omr`, 보충 전후 XML이 있고 최종 `validation.json`에 실제 preflight·원본 대조 결과가 있다. 이 파일들은 Git 밖에 보존했다. 재현은 같은 소스·원본·이미지를 읽기 전용 연결하고 위 자원 제한으로 `/app/ai/.venv/bin/python /probe.py`를 실행한다. 상세 실행 명령은 계획의 기존 회귀 명령과 로컬 관찰 스크립트를 참고한다.

이번 검증으로 원본 전체 실제 OMR과100BPM·38.4초 전달까지 확인했다. 음원 파일 합성·스피커·실제 기기·운영 서버는 검증하지 않았다. 내보내기 여백까지 포함한 계산상38.95초는 악보 수행38.4초와 구분한다.

## 빠르기 숫자의 잇단음표 오인식 (953)

QA931의 실제 BlueVal 음원 검사에서30·62마디 일부8분음이1/3Q로 줄고 끝에1Q 공백이 생겼다. 총118마디·472Q와 전체 길이가 맞아도 개별 리듬은 틀릴 수 있다. [953 계획](../../plans/S15P31E102-953.md)에서 로컬 원시 인식부터 분리했다. 원본은 Downloads 지정 폴더의 `BlueVal_261114_091621.pdf`, SHA256 `5aa7f7af037acdb7540071b187cdec5923a4d51f4521861828b129ec98321f1d`다.

실제 Audiveris5.11.0의2쪽5번째(전체30)마디에서 `160`의 숫자6을 `TUPLET_SIX`로 오인했다. OMR표식 경계(2100,230,21,33)와 원본 숫자6 경계(2101.177,229.702,19.644,32.740)가 일치하고 여섯 화음에 연결됐다. raw XML은 divisions6·6:4연음·각duration2로 출력했고 베이스는4Q를 유지했다. 선택 선율의3Q와 베이스4Q 차이가 마지막1Q쉼이 됐다. tempo시계나 MusicXML 파서의 비율 중복 적용이 원인은 아니었다.

확인된 PDF 빠르기 숫자와 경계가 유일하게 맞는3·6연음 표식만 작업용 렌더에서 처리한다. 해당 표기의 각 글자 잉크를 개별적으로 가려 숫자만 부분 삭제되거나 글자 사이의 덧줄음이 지워지는 것을 피한다. 원본과 실제 잇단음표는 보존하고 기존 Audiveris 재인식·PDF 빠르기 보충을 사용한다. 모든6:4를 지우거나 음가를 일괄3/2배 하지 않는다.

독립 리뷰에서 마디 중간 빠르기는 기존 첫 박 보충으로 복원되지 않는다는 점과 정수 반올림·1px 여백이 오선을 침범할 수 있다는 점을 재현했다. 마스킹과 보충이 같은 마디 수·첫 박 정렬 판정을 사용하고, 원래 빠르기와 충돌하는 후보는 제외한다. 재인식 후 복원 조건이 깨지면 원래 인식 결과를 보존해 검수로 보낸다. 글자의 실수 경계가 아니라 실제 그리는 정수 경계 전체가 오선 위에 있어야 한다.

| 실제 원본 대조 | 결과 |
| --- | --- |
| 요청/인식 | 최종 `SANJO_DAEGEUM`,4쪽300dpi, HTTP200 `DONE`,160BPM.2쪽 재인식 포함 Audiveris5회 모두종료0, 리뷰 보완 후 총약77.4초 |
| 30마디 | 수정 전1Q쉼+6×1/3Q+1Q쉼 → 수정 후1Q쉼+6×1/2Q |
| 62·63마디 | 로컬 baseline과 수정본 모두8×1/2Q. 배포 앱의62마디 오독은 이번 로컬 실행에서 재현하지 못함 |
| 회귀 대조 |118각·472Q·492음 동일. 전체492음 음고와30마디 밖486음의 시작·길이 동일. 전곡 정답과의 대조는 아님 |
| 표현/식별자 |5빠르기·2가속·반복 계획 동일. tpb12→4와30마디 자리 이동으로4개 음표ID는 바뀌며 최종sourcePitches와 연결됨 |
| 실제 재생 계산 | 기보177초, 수행/MIDI181.635520833초, 시간표181635.520833ms. 독립적분 경계오차 최대0.01293ms |
| AuClair 실제 회귀 |16마디44음64Q,100BPM·38.4초 유지.935 최종응답과 바이트 동일, 출판사 정답44음의 음고·시작·길이 일치 |
| 작은 회귀/끝 붙임줄 |관련9파일234검사 통과. 마지막음467.5Q→472Q·4.5Q와 마지막4칸의 같은 이벤트 참조 보존 |
| 최종 BlueVal 응답 SHA256 | `5877364772d75106ac4e972628c57e0024a904956cf0085748e899e49dc55cfb` |

같은935 로컬 이미지·언어 데이터·Python3.12.16/PDFium5.15.2 환경을 재사용했다. Docker Desktop 로컬 엔진, 네트워크 없음·CPU2/2GiB·512MiB tmpfs·읽기 전용 루트/소스/원본·쪽당60초 제한이며 새 이미지·모델 다운로드는 없다. Audiveris가 쪽별 입력의 일부 시스템에서 박자표의 목표 길이를 찾지 못한 경고를 냈다는 점도 보존한다. 성공 종료를 경고 없음이나 전곡 인식 정확도 보증으로 확대하지 않는다.

근거는 Git 밖 `C:/higher/qa-evidence/S15P31E102-953/`의 `run_actual_blueval.py`, `baseline/`, `fixed/`, `auclair-regression/`, 리뷰 보완 후 `final/`, `final-auclair/`에 있다. 관찰wrapper는 실제 subprocess를 실행한 뒤 raw MXL/OMR·stdout/stderr·채택XML·보충 전후XML을 복사하고 원래 결과를 반환한다. 네트워크 다운로드만 로컬 원본 복사로 대체했다. 최초 baseline probe의 악기 코드 오타는 원시 Audiveris 단계와 무관하지만 산조대금 성공 근거로 사용하지 않으며 최종 요청은 올바른 코드로 다시 실행했다.

최종 실제 실행의 제품 SHA256: `staff_engine.py`=`b9691a7d295548363b3e0f5d413b46fa4bebdefde974b87bfc4c8fa34ff12bcc`, `staff_pdf_tempo.py`=`7f7f46ac2adb87de73691b33dd1d5f985a04ee0ff5441065b44b8d96337f474f`. 제품 커밋 `8e63050e`와 두 실제 실행의 해시가 일치한다. 최종 BlueVal 응답은 리뷰 전 `fixed/`와 바이트 단위 동일하며 독립 수치 재검증은 `final/independent-validation-duration-check.json`에 있다. 수정 음원 합성은 확인한 로컬 자산에 산조대금 CAT v1.3가 없어 미수행이다. 단소 대체나 새 다운로드는 하지 않았으며 시간표 검증을 음원 파형 검증으로 보고하지 않는다. 기기·운영 서버는 건드리지 않았고 기존 저장 악보·음원은 자동 수정하지 않는다.

## 가사 줄의 기호 오인식 (992)

사용자 업로드 사례에서 단소 오선보(가타카나 계이름 가사 포함, 300dpi 래스터 한 장을 넣은 PDF)가 `REPEATED_UNDERFULL_MEASURE` 6건으로 검수에 머물렀다. 원본 16마디는 모두 4/4 4박이다. 같은 곡을 가사 없는 벡터 PDF로 올리면 16마디가 원본과 일치했다. [992 계획](../../plans/S15P31E102-992.md)에서 원인을 나눴다.

| 입력 | 오선 간격 | 4분쉼표/8분쉼표 인식 | 원본에 없는 기호 | 박자와 다른 가운데 마디 |
| --- | --- | --- | --- | --- |
| 가사 래스터 PDF(SHA256 `e20b1ee6…2f75`) | 19px | 2/8 · 3/6 | 29개(악센트9·스타카토11·6잇단2·p3 등) | 6 |
| 가사 없는 벡터 PDF(SHA256 `77eb73c2…f970`) | 27px | 8/8 · 6/6 | 0 | 0 |

- 가사 원인: 영어 OCR만 있어 'ミ'를 `TUPLET_SIX`로, 'ファ'를 악센트로, 탁점을 스타카토로 읽었다. 6잇단이 붙은 3·6마디가 3박이 됐고 4마디 끝 쉼표는 빈 머리 음표가 되어 없는 2분음표가 생겼다.
- 해상도 원인: 쉼표 누락은 가사를 지워도 남았다. 1.5배 LANCZOS 확대는 3·4·5·6마디를 더 나쁘게 만들었다. 래스터 PDF의 작은 오선이 한계이며 단순 확대로 해결되지 않는다.
- 수정 후 같은 서비스 경로(`_read_pages`, PDFium 300dpi): 가사 줄 4개를 가리고 재인식해 3·6마디 4박 복원, 4마디 가짜 음 제거, 12마디 쉼표 복원. 15·16마디 앞 8분쉼표는 이번 재인식에서 빠져 가운데 불일치는 6→5마디다. 쉼표 검출은 경계값 근처라 재인식마다 달라질 수 있다. 쪽 처리 약 17.4초(첫 인식 약 8.5초 + 재인식). 가사 없는 PDF는 가사 줄을 찾지 않아 결과와 시간(약 9.8초)이 그대로다.

환경: 로컬 `eolssu-omr:scan458-local` 이미지의 Audiveris 5.11.0·Python 3.12.16, 서비스와 같은 Audiveris 옵션. 원본 PDF·렌더·OMR/MXL은 Git에 넣지 않았다. 단위 검사는 `ai/tests/unit/test_staff_lyrics.py`가 작은 가짜 `.omr`로 덧줄 음 보존·띄엄띄엄한 기호·오선 사이·다음 단 근접·채택/거절/시간 초과를 고정한다.

## 폰 사진의 오선 간격 오판 (1002)

- 운영 증상(2026-11-16, 운영 DB 읽기 전용 조회는 사용자가 실행): 오선보 사진 변환 3건(sheet 59·60·69)이 `NO_SCORE_FOUND`로 끝났다. backend는 인식 서비스의 `NO_STAFF_FOUND`를 이 코드로 저장한다(`ConversionWorker.kt`). 시간 초과·엔진 오류·형식 오류가 아니다. 원본은 변환 뒤 지워졌고 omr 로그는 컨테이너 재생성으로 남지 않았다.
- 실패 원본: 사용자가 sheet 60 접수(12:10:40) 직전에 찍은 사진(12:10:35, 3264×2448 JPEG, EXIF 회전 6, 모니터 화면의 오선보)을 받았다. 수정 전 서비스 경로에서 운영과 같이 `NO_STAFF_FOUND`(3.7초)로 재현했다.
- 원인 분리(Audiveris 직접 실행, 서비스 옵션 동일): 같은 사진이 컬러로는 원본 크기·75%에서 간격 3픽셀로 재져 `Sheet ignored`, 흑백(Pillow `L`)으로는 원본 크기·75%·1500×2000 모두 간격 24·18·14픽셀로 읽혔다. 배경을 평탄화한 그림, 화면 영역만 자른 컬러 그림, 1500×2000 컬러 그림도 읽혔다. 오선 자체는 배경 162 대비 30~50으로 진하고 실제 간격은 약 25픽셀이었다. 컬러에서 간격을 3으로 재는 Audiveris 내부 이유는 확인하지 않았다.
- 크기 영향: 흑백 원본 크기는 70초가 걸렸다(75% 22초, 1500×2000 13초, 이 PC `--cpus 2`). 서비스 경로에서 흑백 긴 변 상한을 1500·2000·2448로 바꿔 보니 채팅 첨부본 결과와 음높이가 68/68·55/68·68/68이었다. 결과가 크기에 민감하며 1장으로 정한 값이다.
- 별도 원인: 악보가 작게 찍히거나 줄어든 그림은 흑백으로도 간격 8~9픽셀로 재져 버려진다(채팅 첨부본의 60%·50% 축소, 150dpi 렌더). 간격 20픽셀이 되게 확대하면 읽힌다.
- 수정: [스펙 1002절](../../specs/score-recognition-corrections.md#오선-간격-오판-사진-재인식-1002). 경고가 있을 때만 긴 변 1500 흑백 → 그래도 작으면 확대 흑백 순서로 다시 읽는다.

| 입력 | 크기 | 수정 전 | 수정 후 | 대조 기준과 일치 |
| --- | --- | --- | --- | --- |
| **운영 실패 원본 사진** | 2448×3264 | `NO_STAFF_FOUND` | DONE 68음, 흑백 ×0.46, 12.2초 | 채팅 첨부본 대비 음높이 68/68, 길이 68/68 |
| 같은 사진 채팅 첨부본 | 1500×2000 | DONE 68음 | 같음(재인식 없음) | 대조 기준 |
| 첨부본 75% | 1125×1500 | DONE 68음 | 같음(재인식 없음) | — |
| 첨부본 60% | 900×1200 | `NO_STAFF_FOUND` | DONE 68음, 흑백 → 간격 9 → ×2.22 | 음높이 68/68, 길이 65/68 |
| 첨부본 50% | 750×1000 | `NO_STAFF_FOUND` | DONE 68음, 흑백 → 간격 8 → ×2.5 | 음높이 67/68, 길이 63/67 |
| 너영나영 PDF 300dpi | 2150×3036 | DONE 69음 | 같음(재인식 없음) | 대조 기준 |
| 너영나영 150dpi 그림 | 1075×1518 | `NO_STAFF_FOUND` | DONE 70음, 흑백 → 간격 8 → ×2.5 | 음높이 69/69, 길이 66/69 |
| 150dpi 그림을 작은 판형 PDF로 저장(글자 층 없음) | 1075×1518로 렌더 | 재지 않음 | DONE 70음, 같은 순서 | 음높이 68/69, 길이 64/68. PDF 글자 좌표 재읽기가 오류 없이 도는지 확인 |

- 대조 기준은 원래 해상도 Audiveris 결과이고 사람이 검수한 정답이 아니다. 채팅 첨부본 악보를 직접 세면 붙임줄 4곳을 합쳐 68음이라 음 수는 맞지만 음별 정답 대조는 하지 않았다. 기준에도 `REPEATED_UNDERFULL_MEASURE` 3건이 있다. 길이는 짝지은 음끼리 tick 비율이 가장 흔한 값과 같은지로 셌다.
- 축소본·150dpi 그림·작은 판형 PDF는 Pillow LANCZOS로 만든 합성 입력이며 실제 촬영 표본이 아니다. 확대는 사라진 쪽을 되살리지만 원래 해상도만큼 정확하지 않다(길이 3~6음 차이).
- 시간(이 PC, `--cpus 2`, 1쪽): 재인식 건 12.2~35.7초, 영향 없는 건 8.2~20.2초. 운영 서버는 같은 CPU에서 이 PC의 약 3배였으므로(468) 확대까지 가는 건은 쪽당 60초 안에 끝나지 않을 수 있다. 시간이 모자라면 재인식을 건너뛰고 앞 결과(`NO_STAFF`)를 쓴다.

환경: 이 브랜치로 빌드한 `eolssu-omr:s1002`(runtime 단계, Audiveris 5.11.0)에 수정한 `ai/src`를 읽기 전용으로 붙여, 서비스 경로 `/v1/recognitions`에서 내려받기만 로컬 파일 복사로 바꿨다. 사진·렌더 그림은 Git에 넣지 않았다(원본은 `E102-data/14_폰촬영_오선보_표본`). 단위 검사 `ai/tests/unit/test_staff_low_resolution.py`가 실제 Audiveris 경고 문구로 흑백 재인식, 큰 사진의 긴 변 상한, 확대 배율, 최대 화소 제한, 다시 읽어도 실패한 경우, 오선 없는 쪽(재인식·확대 안 함), bbox 원래 좌표 복원을 고정한다.

한계: 운영 실패 3건 중 원본을 받은 것은 1장이다. 원근·기울기·어두운 사진, 종이 인쇄본 사진, 다른 기종은 재지 않았다. 원근·조명 문제는 이 재인식으로 해결되지 않으며 앱의 카메라 문서 스캔(S15P31E102-1003)에서 다시 본다.

## 장단 없는 오선보의 빠르기 60 (1035)

- 증상(2026-11-16, 사용자 앱 업로드): 992에서 다룬 가사 있는 단소 오선보 그림 PDF(♩=156, 4/4)가 빠르기 60으로 들어왔다.
- 원인: 이 PDF는 쉼표 누락으로 가운데 마디가 짧게 읽혀 `REPEATED_UNDERFULL_MEASURE`가 되고, 박자표 4/4를 읽었는데도 자동 장단을 비운다. 숫자 빠르기도 없으면 인식 응답에 `bpm`이 없고, backend는 장단 기본값을 찾지 못해 60을 넣는다(`ConversionWorker`의 `?: 60`).
- 그림 속 ♩=156은 Audiveris가 읽지 못한다. 원본 그림, 가사 없는 입력용 그림에 원본의 ♩=156을 붙인 그림 모두 숫자가 없었다. 같은 입력용 PDF에 "♩ = 156"을 글자층으로 넣으면 `TEMPO_PDF_TEXT`로 156을 읽었다.
- 수정: 숫자도 장단도 없을 때 악보 전체의 박자표가 하나이고 자동 장단 대응이 있으면 그 장단의 기본 빠르기를 `bpm`으로 보낸다. 알림은 `TEMPO_DEFAULT_FROM_TIME_SIGNATURE`. [스펙](../../specs/score-recognition.md).

| 입력(1쪽, 서비스 경로) | 장단 | 수정 전 bpm(앱) | 수정 후 bpm(앱) |
| --- | --- | --- | --- |
| 가사 있는 원본 그림 PDF(♩=156 그림) | 비어 있음 | 없음(60) | 160 + 알림 |
| 가사 없는 입력용 그림 PDF | 4박 기본 반주 | 없음(160) | 없음(160) |
| 입력용 + "♩ = 156" 글자층 | 4박 기본 반주 | 156 | 156 |

환경: develop 기준 `eolssu-omr` runtime 이미지(Audiveris 5.11.0)에 수정한 `ai/src`를 읽기 전용으로 붙여 `/v1/recognitions`를 호출했다. 세 경우 모두 68음·16각이다. PDF는 Git에 넣지 않았다.

한계: 160은 원곡 빠르기(156)를 알아낸 값이 아니라 시작값이다. 박자가 어긋난 인식 결과 자체는 그대로이며 쉼표 누락은 992 한계를 따른다. 앱은 이 알림을 아직 전용 문구 없이 일반 알림으로 보여 준다.

## 아리랑 9/8의 끝 쉼표 누락과 9정간 각 (1043)

- 증상(2026-11-16): 경기 아리랑(세마치 9/8, 8마디, 음 35개) 오선보가 원곡과 크게 다르게 변환됐다.
- 원인 1 — 저해상도 원본: 500×225px 캡처를 그대로 넣으면 Audiveris가 박자표 9/8을 6/8로 읽고 마디마다 뒤 3/8박을 버린다. 박자표 오독은 추정으로 고치지 않는 계약이라 수정하지 않았다. 같은 악보를 다시 그린 벡터 PDF도 Verovio 기본 글꼴(Leipzig)·Leland·Gootville의 9는 6/8로 읽혔고, Bravura로 그리면 9/8과 35음을 모두 맞게 읽었다(Petaluma는 첫 단의 옥타브가 틀렸다).
- 원인 2 — 끝 쉼표 누락: Bravura PDF에서도 Audiveris가 끝 마디의 8분쉼표를 빠뜨려(간격을 넓혀도 같음) 끝 마디가 8/8이 됐다. `_read_part`가 모든 마디 길이의 공통 음가를 정간 박으로 쓰므로 박이 8분으로 바뀌어 세마치 한 각이 9정간(`JANGDAN_MISMATCH`)이 됐다.
- 수정: 끝 마디는 박을 정하는 데 넣지 않고, 박자표보다 한 박 안쪽으로만 모자라면 다음 박 경계까지 쉼표로 채운 뒤 `LOW_CONFIDENCE`(`FINAL_MEASURE_PADDED`)를 남긴다. 883의 "끝 불완전마디 보존"(4/4 끝 마디 8분 하나)은 한 박 이상 모자라므로 그대로다. [리듬 보존 스펙](../../specs/score-rhythm-preservation.md).

| 입력(Bravura 벡터 PDF, 세마치 요청) | 각당 정간 | 알림·검수 | 음 |
| --- | --- | --- | --- |
| 수정 전(develop) | 9 | `JANGDAN_MISMATCH` | 35음 일치, 끝 쉼표 없음 |
| 수정 후 | 3 | `FINAL_MEASURE_PADDED` 1건 | 35음 일치 + 끝 쉼표 |

환경: develop `37b929d44` 기준 `eolssu-omr` 이미지(Audiveris 5.11.0)에 수정한 `ai/src`를 읽기 전용으로 붙여 `/v1/recognitions`를 호출했다. PDF는 Git에 넣지 않았다.

한계: 끝 마디의 점2분~4분 붙임줄을 Audiveris가 놓쳐 두 음(6+2틱)으로 나온다. 율명은 황을 E♭으로 두어 원본 악보의 율명(仲=G)과 다르며, 원본처럼 읽으려면 황 음높이를 D로 지정한다. 원래 캡처처럼 낮은 해상도 그림은 여전히 6/8로 읽힌다.
