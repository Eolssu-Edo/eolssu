# 제공 악보 ZIP의 추가·편집 회귀 검증 (784)

2026-11-12, Task S15P31E102-784, Story S15P31E102-317. 사용자가 제공한 두 ZIP을 최신 develop에서 검증하고 실패를 수정해 MR을 준비했다. 첨부 설명의 과거 결과·권장 순서는 근거 자료로만 읽었고, 거기에 적힌 미지원 상태를 현재 결과로 재사용하지 않았다.

## 입력과 환경

| 입력 | SHA-256 |
| --- | --- |
| 악보_추가편집_테스트자료.zip | `29decc1dc913dc21ded33092aaebc445557bb012d9c2215ab8e40599c83e5584` |
| 악보_추가편집_추가자료_2차.zip | `9cd58d20f8569b098b9b9524fbe200fe81a3cbe3d74a0beb7ab4a1f0c03ac027` |

- PDF 14개, PNG 22개, JPEG 4개와 곡별 여러 쪽 PNG 묶음 4개, 총 44개 변환 시나리오다. MusicXML·JGB·정답 JSON은 비교에 사용한다.
- 시작 기준 `a61b62a8`, 통합 기준 `origin/develop 26e93bca`. 도중 반영된 구간 평가 변경은 충돌 없이 통합했다. 인식 코드는 그 변경과 겹치지 않는다.
- Audiveris 5.11.0, 생성기 `9d4a24e`, MALerLab `e95045f`·가중치 v1.0.0. 실제 모델을 CPU 2개·메모리 2GiB·읽기 전용 파일시스템·`/tmp:rw,exec,size=1g`에서 실행했다. 원격 다운로드 경계만 로컬 원본 바이트로 대체한 실제 인식 API다.
- Docker 첫 빌드는 인증서 패키지 `.1`이 내려가 실패했다. Ubuntu 패키지 조회에서 확인한 `ca-certificates=20260816ubuntu0.24.04.2`로 고정했고, 실제 설치 버전을 재조회했다. GTK·Node·모델·가중치 고정은 유지한다.
- 입력을 수정하거나 저장소에 재배포하지 않았다. 특히 국립국악원 발췌·렌더본은 원본 묶음의 이용 조건에 따라 로컬에서만 사용했다.

## 원인과 교정

1. 보허자 두 쪽·해령의 2600px PNG는 모델의 칸 너비 85~110px 조건을 벗어났다. 기존 1배·1/2배 시도로는 `NO_GRID_FOUND`였고, 모델 기준 높이 3091px로 정규화하면 인식된다. 단순 2배 확대는 실패해 채택하지 않았다.
2. AuClair 오선보는 Audiveris의 최소 오선 길이 30 때문에 짧은 마지막 두 마디가 빠졌다. 최소 20으로 16마디를 읽는다. 상성부 23회·하성부 44회라 기존 시작 수 두 배 기준으로는 높은 반주를 골랐다. 더 활동적인 성부의 본음 길이 중앙값이 절반 이하인 경우도 고르되, 길이가 비슷하면 높은 성부를 유지한다. `VOICE_GUESSED`는 유지한다.
3. AuClair 정간보 PNG는 크기만 바꾸면 44음이 나와도 56칸으로 합쳐지고 두 단의 순서가 섞였다. 마지막 2/3배 시도에서 기존 `grid_boxes.find_blocks`의 엄격한 경로로 64칸·16각을 읽는다. 가로선이 있는 두 열을 율명·가사로 추측하는 기능은 꺼서 다성부를 조용히 버리지 않는다. 여러 악보가 나란히 있는 모호한 입력은 이 경로에서 선택하지 않는다.

원본 bbox, 쪽별 시간 예산, 최대 화소 제한과 기존 성공 경로를 보존한다. 요청·응답의 형식과 실패 코드는 바꾸지 않는다. DB 마이그레이션은 없다.

## 정확도와 기능 검증

| 대상 | 관찰 |
| --- | --- |
| AuClair 오선보 PDF·PNG | 수정 전 20음 → 44음. 제공 정답의 원본 MIDI·시작·길이 44/44 일치 |
| AuClair 한자 정간보 PDF | 율명·옥타브와 시작·길이 44/44 일치 |
| AuClair 한자 정간보 PNG | 엄격한 격자 경로에서 64칸·16각, 율명·옥타브와 시작·길이 44/44 일치 |
| 율명 음역 대조 PDF | 24/24 율명·옥타브 일치 |
| 율명 음역 대조 PNG·JPEG | 각각 19/24 일치. 중청 계열 5개는 미일치하며 수동 검수가 필요하다 |
| 보허자·해령 PNG | 수정 전 실패 → 편집 가능한 초안 변환. 전곡 음표 정답은 미제공이므로 정확도 통과를 뜻하지 않는다 |
| 바흐 BWV1013 5쪽 | 병렬 빌드 중 쪽별 제한 `TIMEOUT` 1건. 단독 재검증은 142.42초에 4044음으로 DONE이며 교정 전과 같은 음 수다. 전곡 정확도는 승인하지 않는다 |

정간보의 MIDI 비교는 첨부 정답과 같은 황종 C4를 대입한 **율명·옥타브 대조**다. 앱의 기본 황음은 E♭4이며 정간보 PDF의 설명문에 적힌 조율을 자동으로 해석하지 않는다. 오선보는 원본 실음 MIDI를 대조했다. 변환 `DONE`, 편집·저장 성공, 음표 정확도를 구분한다. 모델 어휘가 일부 중청 율명을 지원하지 않는 한계는 기존 DATA 리더에도 명시되어 있으며, 이 작업에서 가중치를 학습하거나 원본을 추정해 덮어쓰지 않았다.

Android API37/x86_64·16KiB, Samsung 공식 S10 화면 크기 1440×3040 기준의 작업 전용 AVD를 사용했다([화면 크기 출처](https://developer.samsung.com/galaxy-emulator-skin/galaxy-s.html)). Samsung One UI나 실기기 실행을 뜻하지 않는다.

- 실제 Android 문서 선택기로 PDF 선택 → 로컬 Spring/MySQL/실제 OMR → 편집 진입 → 한 음 수정·자동 초안 저장 → 음역 조정·확인 → 확정 저장·재조회까지 실행했다. 24음, 판본 2, `EVALUABLE`, 초안 없음과 목록 `연습 가능`을 확인했다.
- 최신 develop 통합 후 APK를 재설치하고, 실제 해령 PNG 선택 → 업로드 → 실제 모델 변환 → 116음 편집 화면을 확인했다. 원래 실패하던 이미지의 bbox는 원본 좌표로 되돌아간다. 중청 글자와 복잡한 기호는 검수 대상으로 남는다.
- 저장 사전검사는 운영의 sample-free `PlaybackService`·`render_request --estimate`를 로컬 하네스로 실행했다. CAT·장구 자산 준비를 생략한 길이 검사이며 실제 음원 합성·다운로드 검증으로 보고하지 않는다. 운영 사용자·DB·배포는 건드리지 않았다.

## 실행과 근거

- 전체 44건은 43건의 첫 성공과 바흐 단독 재검증을 합쳐 변환 완료를 확인했다. R3의 엄격한 격자는 실제 AuClair 입력을 다시 실행해 44/44 정답 일치를 확인했다. 초기 실패·시간 초과 기록은 지우거나 통과로 바꾸지 않았다.
- AI·격자 회귀 최종 188건 통과. 기존 작은별·환희의 송가·프레르 자크·Amazing Grace의 PDF·이미지 9개는 교정 전후 event·원본 pitch 출력이 모두 동일하다. 이것은 전후 회귀 대조이며 별도의 전곡 수동 검수 결과는 아니다.
- 작은 회귀: 기존 성부 선택·붙임줄·화음, 대표 음 길이가 비슷한 성부, 크기 재시도·원본 bbox, 얇은 선의 두 단 읽기, 엄격한 격자의 다성부 거절을 검사한다. 새 실패를 먼저 관찰하고 수정 후 통과했다.
- AI: `PYTHONPATH=ai/src;data/src`, `JGB_GENERATOR_DIR`에 고정 생성기를 지정하고 `pytest ai/tests/unit/test_omr_text_engine.py ai/tests/unit/test_omr_staff_engine.py ai/tests/unit/test_omr_scan_engine.py ai/tests/unit/test_omr_api.py ai/tests/unit/test_staff_musicxml.py ai/tests/unit/test_staff_source_pitches.py ai/tests/unit/test_staff_audiveris_fixtures.py data/tests/test_grid_boxes.py -q`.
- Android: `frontend/`에서 `gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug -Peolssu.debugApiBaseUrl=http://127.0.0.1:18085 --no-daemon --max-workers=2`. 통합 후 1544건 중 기존 제외 4건, 실패·오류 0건이다.
- Backend: 폐기 MySQL DB의 `TEST_DB_*`를 지정해 `gradlew.bat databaseTest --tests '*SheetImportApiDatabaseTests' --tests '*UserSheetApiDatabaseTests' --no-daemon --max-workers=2`. 실제 HTTP·업로드·저장·권한·버전 검증 30건 통과다.
- 이미지: 저장소 루트의 `docker build -f ai/omr.Dockerfile -t eolssu-omr:zip784-final .`. 고정 패키지를 `docker run --rm --entrypoint dpkg-query eolssu-omr:zip784-final -W libgtk-3-0t64 nodejs ca-certificates`로 확인한다.
- 로컬 근거 루트: `C:/higher/tmp/score-zip-784/`. `recognize_archives.py`는 실제 API를 로컬 파일 경계로 재현하고 SHA-256·시간·응답을 저장한다. `matrix-exec/`는 교정 전 44건(39 DONE·5 NO_GRID), `final-matrix/`는 첫 교정 후 전체 결과, `strict-grid/`는 얇은 선·두 단의 최종 결과, `bwv-isolated/`는 시간 제한을 유지한 바흐 재검증이다. `check_matrix_answers.py`와 정답 대조 JSON, `regression-red.log`, `grid-fallback-red.log`, `strict-grid-red.log`, `final-tests.log`, `r3-build.log`, `staff-nine-regression.json`, DB·Android 로그와 AVD XML·캡처도 같은 위치에 있다. 원본과 대용량 응답은 Git에 넣지 않는다.

코드 검토에서 추가 격자의 다성부 손실 위험을 발견해 엄격 모드 회귀로 수정했다. 실기기·iOS·운영 배포·CAT 음원 품질과 복합 악보의 전곡 정확도는 미검증이다. 롤백은 이 Task의 코드 커밋을 되돌리고 이전 검증 OMR 이미지를 사용하는 방식이다. 이미 저장된 악보는 자동으로 바뀌지 않는다.
