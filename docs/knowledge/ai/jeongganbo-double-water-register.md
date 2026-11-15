# PNG·JPEG 정간보의 중청 음역 복원

2026-11-13, S15P31E102-876(Story S15P31E102-319). 같은 원본 PNG·JPEG를 실제 스캔 OMR API에 넣어 중청 음 5개를 청 음으로 읽는 오류를 수정했다. 두 형식 모두 **19/24 → 24/24**이며 글자층 PDF는 24/24를 유지했다. 모델 가중치·입력·API/DB 형식을 바꾸지 않았다.

## 원인과 변경

수정 전 reader의 원시 출력부터 7·11·15·19·22칸이 청태·청중·청임·청무·청태였다. 고정 MALerLab 모델 어휘에는 중청황만 있고 나머지 중청 토큰이 없다. 후단 공통 악보 변환이 중청을 지운 문제가 아니다. 입력 그림에서는 청의 물수변 왼쪽에 중청의 물수변이 한 겹 더 보인다.

기존 `octave_check.py --out`은 특정 교재의 청태·청황 평균 모양을 만드는 도구다. 다른 글꼴의 중청중·중청임·중청무까지 이 템플릿으로 덮어쓰지 않는다. 같은 파일의 `recover_water_register`에서 다음 기준으로 복원한다.

- 모델이 청 음으로 읽은 각 음의 위치에서 그림을 자른다. 한 칸의 중심 음, 반칸·3분할 위치와 시김새 접미사를 보존한다.
- 격자에 연결된 가장자리 먹을 제외하고, 글자의 왼쪽에 두 물수변의 위·중간 점 쌍과 바깥 물수변의 아래 획이 함께 있는지 확인한다. 크기·간격은 글자 폭·높이에 대한 비율이다. 파일명·칸 번호·악기 음역·주변 선율은 판정에 사용하지 않는다.
- 작은 JPEG 글자에서 회색 번짐이 위쪽 두 점을 잇는 경우에는 어두운 획 중심으로 같은 구조를 한 번 더 확인한다. 두 물수변을 증명하지 못하면 모델 결과를 유지한다. 빈 그림·잘린 획·비음표·이미 중청인 토큰은 올리지 않는다.
- 모델 격자와 `--grid-boxes`의 엄격한 기하 격자 양쪽에서 복원한 토큰으로 `text`와 `jg`를 함께 쓴다. 서비스가 읽는 `text`에도 반영되므로 편집 화면의 실제 음역까지 전달된다.
- 율 이름·토큰 위치·시김새·confidence·원본 bbox·음표 ID·시작·길이는 유지한다. 모델 confidence를 보정 정확도 확률로 다시 해석하거나 높이지 않는다. 새 스캔 provenance는 계속 inferred이며 엔진 버전은 `e95045f-v1.0.0-r4`다.

## 같은 자료 재검증

환경은 기존 검증 이미지 `eolssu-omr:scan458-local`(`68f2de5c9da1`)에 현재 AI/DATA 소스를 읽기 전용으로 마운트했다. CPU 2개·메모리 2GiB·실행 가능한 tmpfs 1GiB, MALerLab `e95045f`·v1.0.0 가중치, 기존 시간 상한을 유지했다. 다운로드 경계만 로컬 원본 바이트로 대신한 실제 `POST /v1/recognitions` 실행이다.

| 입력 | SHA-256 | 수정 전 | 수정 후 |
| --- | --- | --- | --- |
| 율명 음역 대조 PNG | `3d69447d26df244d0f91877a13a420e9cf5e87241a32db345af89110250777cf` | 19/24 | 24/24 |
| 같은 대조표 JPEG | `032b85d47d1228b480031011b69b450b5bc2f7bdbd1e80ee24a6328cad883069` | 19/24 | 24/24 |
| 같은 대조표 PDF | `6c40cb03e2fc636ae17471be1aa769d2089c89f7dfb42dfaa7879e1a3b9a65bf` | 24/24 | 24/24 |
| AuClair 한자 정간보 PNG | `b3508850ee2726de75835cd138e5457e1d95857b3bf5c92cc43d5cbc4c180a9c` | 44음 | 44음, 이벤트·정간 동일 |

PNG·JPEG에서 실제 공통 악보 이벤트의 옥타브가 바뀐 곳은 7·11·15·19·22번째 음뿐이다. 각각 정확히 한 옥타브 올라갔고 나머지 이벤트 필드·ID·시간·시김새는 동일하다. PDF와 AuClair는 음표·정간이 바뀌지 않았다.

추가로 제공된 실제 스캔 PNG 5종을 구 reader와 새 reader로 각각 실행해 음표 이벤트와 정간을 대조했다.

| 입력 묶음 | 수정 전 → 후 음 수 | 이벤트·정간 |
| --- | --- | --- |
| 동동 1장 | 95 → 95 | 동일 |
| 수제천 3장 | 334 → 334 | 동일 |
| 여민락 2장 | 388 → 388 | 동일 |
| 보허자 2장 | 464 → 464 | 동일 |
| 해령 1장 | 116 → 116 | 동일 |

## 회귀와 근거

- 처음 재현한 실제 PNG·JPEG는 각각 19/24로 assertion에 실패했다. 이후 같은 원본·정답·조건에서 24/24로 통과했다.
- 작은 회귀 37개: 24칸의 기본·청·중청, 0.65/1/1.5/2배와 JPEG 압축, 한 칸의 여러 음·시김새·위치, 비음표, 이미 중청, 빈 그림·잘린 획. 수정 전 identity 단계에서 증상에 해당하는 10개가 실패했고 수정 후 37개가 통과했다.
- 실제 reader CLI 2개: 외부 모델 예측만 고정하고 실제 픽셀·격자·CLI 출력으로 두 경로의 `text`·`jg`·confidence를 확인했다. 구 reader를 다시 마운트하면 두 검사 모두 청태/중청태 차이로 실패하고 새 reader는 통과한다.
- 기존 인식·변환·기하 격자와 새 픽셀 회귀를 합친 225개 통과, 추가 CLI 회귀 2개 통과(총 227개). 첫 전체 검사 수집은 검사 이미지에 jsonschema가 없어 실패했으며 프로젝트 지정 `jsonschema==4.26.0`을 임시 검사 경로에 설치한 뒤 수행했다. 제품 의존성이나 기존 검사를 생략하지 않았다.
- 명령은 로컬 검사 이미지 안에서 `PYTHONPATH=ai/src:data/src/scores`로 `pytest`의 `test_omr_text_engine.py`, `test_omr_staff_engine.py`, `test_omr_scan_engine.py`, `test_omr_api.py`, `test_staff_musicxml.py`, `test_staff_source_pitches.py`, `test_staff_audiveris_fixtures.py`, `data/tests/test_grid_boxes.py`, `data/tests/test_scan_register.py`, `data/tests/test_scan_register_cli.py`를 실행한다. 생성기 경로는 이미지의 고정 `JGB_GENERATOR_DIR`를 사용한다.
- 원본·전체 응답·reader 그림·셀 기록·실행 스크립트·로그·전후 대조는 `C:/Users/SSAFY/.codex/worktrees/sheet-flow-qa-876/data/processed/sheet-flow-qa-876/octave-fix/`에 보관한다. `before/`, `after/`, `scans-before/`, `scans-after/`, `comparison.json`, `regression-red.log`, `regression-green.log`, `cli-red-baseline.log`, `cli-regression.log`, `full-regression.log`가 근거다. 작은 회귀 입력만 `data/tests/fixtures/scores/double-water/`에 포함한다.

## 적용 범위와 한계

제공 대조표는 수정 개발과 재검증에 모두 사용한 회귀 자료다. 새로운 글꼴·필기체·저해상도 사진에 대한 독립 정확도 평가로 일반화하지 않는다. 점이 붙거나 손상되어 두 겹을 확인할 수 없는 그림은 올리지 않는다. 기존 스캔 5종의 동일 출력은 회귀 보존이며 전곡 채보 정확도 승인이 아니다.

바흐·보허자의 형식별 음 수 차이와 복잡한 스캔의 LOW_CONFIDENCE·UNREADABLE 검수는 별도 문제다. 이번 변경은 새 인식부터 적용되며 이미 저장된 악보를 일괄 교정하지 않는다. 운영 적용에는 수정 OMR 이미지의 빌드·배포가 필요하고, 이번 실행은 로컬 검증이다.

[876 계획](../../plans/S15P31E102-876.md) · [최초 추가·편집 검증](../frontend/sheet-add-edit-runtime-verification.md)
