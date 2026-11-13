# 오선보 인식 오류 교정과 로컬 대조 (806)

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
