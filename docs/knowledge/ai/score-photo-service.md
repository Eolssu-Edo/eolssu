# 악보 사진에서 단소 재생까지: 로컬 서비스

작업 [S15P31E102-105](https://ssafy.atlassian.net/browse/S15P31E102-105), Story 30, 기존 MR !41. 2026-10-24 사용자 요청과 ‘기존 모델 연결 없음, 로컬 실행부터 구성’ 답변에 따른 후속 구현이다. 실행은 [playback README](../../../ai/playback/README.md), 이전 해석 근거는 [공통 파이프라인 기록](score-instrument-playback-pipeline.md)을 따른다.

## 현재 동작

사진 업로드 → PNG 정규화/원본 해시 보존 → 오선보 실음 또는 정간보 율명 초안 → 원본 위치와 음표 ID 연결 → 사용자 확인한 revision → 기존 해석 엔진/MIDI → CAT WAV → HTTP 재생·다운로드·음표 표시.

사진을 읽는 로컬 Ollama 연결부는 표기법별 프롬프트, JSON 구조·음표·정규화 bbox 검사, 크기·시간 제한을 가진다. 현재 환경에는 인식 모델이 없다. 실제 실행 검증은 **사진 + 이미 원보 대조한 음표 데이터**로 했으며, 임의 사진 자동 인식 성공으로 보고하지 않는다. 모델 출력은 `machine-transcribed-unverified` 초안이고 확인 요청 때만 `manually-transcribed-unverified`로 저장한다. 전문가 검수 상태로 승격하지 않는다. 모델의 형식 준수는 음악적 정확성의 근거가 아니다.

새 서비스의 추가 흔들림 기본값은 ±25cent, 선택값은 ±100/3cent·없음이다. 이전 `build_vibrato_depth_comparison.py`의 1/4·1/3은 시간 분할이 아니라 반음 대비 피치 폭이었다. 긴 음 0.65초 이상, 4.5Hz, 지연 0.22초, fade-in/out 0.18/0.16초를 재사용한다. 원보의 pitch/mixed 요성은 중복 적용하지 않고 음량 요성의 CC11은 유지한다. 기존 CLI 기본값은 추가 흔들림 없음으로 보존한다.

## 로컬 API

기준 주소 `http://127.0.0.1:8765`. 쓰기에는 `X-Playback-Client: local` 헤더가 필요하며 브라우저는 같은 origin만 허용한다. 이 헤더는 사용자 인증이 아니다. 서버의 `--assets`, `--storage`, `--model`, `--ollama-url`은 실행자가 설정하며 요청 본문에서 파일 경로나 외부 주소를 받지 않는다.

| 요청 | 입력 / 결과 |
| --- | --- |
| `GET /api/config` | 단소·기본 quarter·인식 설정 유무 |
| `POST /api/scores?notation=staff 또는 jeonggan` | 이미지 바이트 직접 전송, multipart 아님. 201: id, image_url, revision=0, score, regions, state=needs_review |
| `GET /api/scores/{id}` | 저장한 작업. 브라우저 주소 `/#id`로 열기 |
| `PUT /api/scores/{id}/draft` | `{revision, score, regions}`. 검증 성공 시 revision 증가, needs_review. regions는 `{note_id,bbox:[x,y,width,height]}`이고 0~1 상대 좌표 |
| `POST /api/scores/{id}/recognize` | `{revision,context}`. 실행자의 로컬 모델로 초안 생성. context에 선택적 quarter_bpm/reading_direction 힌트 가능. 모든 결과는 needs_review |
| `POST /api/scores/{id}/render` | `{revision,approved:true,vibrato:quarter 또는 third 또는 none}`. audio_url, midi_url, score_url, timeline, regions, seconds, credit 반환 |
| `GET /api/scores/{id}/renders/{render_id}/danso.wav` | WAV, 바이트 범위 재생 지원. MIDI/score.json도 허용 목록으로 제공 |

timeline은 `{note_ids,start,end,pitch}` 배열이며 시간 단위는 초다. 원본 ID와 장식 확장 parent ID를 그대로 유지한다. 이 API는 새 로컬 API이며 기존 `SongRow.scoreJson`이나 평가 API 계약을 바꾸지 않는다. `playback`은 현재 AI wheel에 포함되지 않으므로 안내한 소스 checkout의 `ai/`에서 실행한다.

같은 revision·표현의 바로 이전 성공 요청은 같은 결과 URL을 반환한다. 악보 수정 중 생성이 끝나면 최신 revision을 다시 확인하고 오래된 결과를 게시하지 않는다. 동시 인식/생성은 409, 오래된 revision은 409, 입력 오류는 422, 크기 초과는 413, 모델/자산 사용 불가는 503이다. 모델·CAT 실패 시 가짜 음표나 무음 파일로 대체하지 않는다. 새 렌더는 임시 폴더에서 완성한 뒤 이동하며 이전 결과 URL은 유지한다. OS/디스크 고장 전체에 대한 트랜잭션은 보장하지 않는다.

## 검증 기록

Windows / Python 3.10.21 / FastAPI 0.162.0 / Uvicorn 0.54.0 / Starlette 1.7.0 / Pillow 12.3.0. 기존 채점 Python 3.12 환경과 분리했다. 설치는 binary wheel만 허용했고 재생 기본 의존성은 변경하지 않았다. FastAPI/Starlette의 기본 HTTP 동작은 버전 변경 후 API 회귀 검사로 확인했다. 테스트용 httpx 0.28.1은 Starlette의 deprecation 안내가 있으나 실패는 없다.

- `python -m unittest discover -s playback/tests -p "test_*.py" -q`: 43개 통과. 기존 엔진 18 + 표현 5 + 로컬 모델 HTTP 계약 12 + 사진 서비스 8. 실제 로컬 HTTP 대역 검사는 모델 품질 검사가 아니다.
- 오선보와 율명 입력, 확인 누락·stale revision·잘못된 사진·과대 입력·영역 좌표·source/context 객체 오류·실패 시 이전 음원 보존 검사.
- 독립 리뷰에서 source=null의 500 오류, JPEG→PNG 후 인식 크기 초과를 재현하고 각각 422 검증과 인식용 JPEG로 수정했다. 두 회귀 검사는 수정 전 실패/수정 후 성공했다.
- Windows MIME 설정에서 JS가 text/plain으로 제공되어 화면이 실행되지 않는 문제를 실제 브라우저에서 발견했다. JS/CSS MIME을 명시하고 실패/성공 회귀 검사를 추가했다.
- 기존 세령산 첫 장단 사진 + 원보 대조 37개 본음 → 실제 CAT 44음, 44.1kHz·612255프레임·13.88333초. 브라우저 업로드·JSON 불러오기·확인·생성·재생 시작과 현재 음표 행 강조를 확인했다.
- 실제 브라우저에서 1/3 선택·생성, 새로고침 후 같은 선택 복원, 첫 음표 영역 드래그와 1/4 재생성도 확인했다. 데스크톱 1265×712 화면 기준이며 좁은 화면·모바일 실기기·스크린리더 검증은 미완료다.
- 실제 HTTP로 quarter/third 모두 생성. MIDI bend quarter −24.9756~25cent, third ±33.3252cent. PCM 유한·비무음, peak 0.8499756, 두 표현의 파형은 서로 다름. 독립 합성 신호 주파수 검사는 요청 깊이에서 0.2cent 이내였다. CAT 원 녹음의 흔들림까지 이 폭으로 제한한다는 뜻은 아니다.
- 정간보 포함 원본 사진에 같은 수동 입력을 황종63 기준 율명으로 변환해 연결했다. 동일 설정에서 MIDI/WAV가 오선보 입력과 바이트 단위로 동일했다. **정간보 독립 판독 정확도 검증은 아니다.**

로컬 실행 산출물: `C:/Users/kingc/Downloads/2026-10-24-악보-사진-단소-서비스/`. `http-runtime-verification.json`에 실제 job·결과 URL·파일 해시를 보존하고 `danso-quarter.wav`, `danso-third.wav`로 비교한다. 원본 사진·CAT ZIP·생성 음원은 Git에 넣지 않는다.

## 남은 통합·품질 범위

자동 사진 인식 모델 설치·실제 정간보/오선보 평가, 복잡한 기호의 독립 원보 검수, MusicXML 수입, 악기별 편곡과 전문가 청감 검수는 미완료다. Compose 앱/백엔드의 업로드·플레이어와 운영 인증·테넌트 격리·보관/삭제 정책·배포는 별도로 통합해야 한다. 단소 이외 프로필은 기존 106~108 브랜치에서 관리한다. 긴 음의 CAT 샘플 소진은 계속 오류이며 임의 반복하지 않는다. 이 구현만으로 제품 모범 연주 품질이나 운영 준비 완료를 주장하지 않는다.

## 참고한 공식 인터페이스

- [Ollama Vision](https://docs.ollama.com/capabilities/vision), [Structured Outputs](https://docs.ollama.com/capabilities/structured-outputs): base64 이미지와 JSON schema를 포함한 `/api/chat` 연결.
- [FastAPI 테스트](https://fastapi.tiangolo.com/tutorial/testing/), [릴리스 기록](https://fastapi.tiangolo.com/release-notes/): 설치 버전과 HTTP 검사 경계.
- [Pillow Image](https://pillow.readthedocs.io/en/stable/reference/Image.html): 이미지 디코딩과 pixel 제한.
