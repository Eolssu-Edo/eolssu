# Compose Web의 한글·율명 글꼴

- 작업: [S15P31E102-92](https://ssafy.atlassian.net/browse/S15P31E102-92)
- 환경: Compose Multiplatform 1.12.1, Kotlin 2.4.20, Windows, 앱 내장 브라우저(Chromium) / Android 에뮬레이터 Medium Phone API 37(Android 17)

## 핵심 결론

- 글꼴 파일을 넣지 않으면 Compose Web(Wasm)은 한글과 율명 한자를 빈 상자로 그린다. 2026-10-23 마법사 예제 버튼에 `얼쑤 아리랑 黃太仲林`을 넣어 `wasmJsBrowserDevelopmentRun`으로 직접 확인했다. Android·iOS와 달리 기기 글꼴로 대신 그리지 않는다.
- 그래서 앱 리소스(`composeResources/font/`)에 한글·한자 글꼴을 넣어야 한다. 넣은 뒤 같은 확인에서 Web과 Android 모두 한글(드문 음절 `똠`·`뷁` 포함)·율명 한자·세리프 제목이 표시됐다.
- Pretendard에는 한자가 없다. 율명은 한자가 있는 세리프(Noto Serif KR)로 그려야 한다.

## 글꼴 크기 측정

`fonttools` 4.60.1로 직접 만들어 측정했다(바이트, 2026-10-23).

| 파일 | 크기 |
| --- | --- |
| Noto Serif KR 가변(TTF) 원본 | 23,795,420 |
| 가변에서 SemiBold를 뽑아 한글 11,172자만 남긴 TTF | 약 8.4MB (힌팅 제거해도 같음) |
| Noto Serif KR SemiBold(CFF) 원본 | 7,610,748 |
| CFF SemiBold, 한글 11,172자 + 율명 한자 | 3,330,584 |
| CFF SemiBold, 한글 2,350자(KS X 1001) + 율명 한자 | 901,348 (최종 사용) |
| CFF Bold, 한글 2,350자 + 율명 한자 | 956,396 (최종 사용) |
| Pretendard Regular·SemiBold·Bold 원본(OTF) | 각 약 1.57MB (최종 사용, 합계 4,734,716) |

- 같은 글자 범위라도 TTF(이차 곡선)보다 CFF(OTF)가 훨씬 작다. 줄일 원본은 CFF를 쓴다.
- 최종 글꼴 합계는 약 6.6MB다. 세리프 글자 범위는 2,350자로 하고, 사용자가 입력한 글자는 한글 전체가 있는 Pretendard로 표시하기로 했다(2026-10-23 권영민 결정).

## 앱·데이터에서 주의할 점

- Noto Serif KR에는 탁황 `僙`(U+50D9), `潕`(U+6F55)가 없다. 백엔드 율명 기준 데이터(`pitches`)의 테스트 예시(`backend/src/test/resources/db/mapper-fixtures.sql`)가 한 옥타브 아래 황을 `僙`으로 저장하므로, 청성·탁성 표기를 화면에 어떻게 그릴지(한 글자 한자, `亻`+기본형 조합, 한글 표기)는 정간보 렌더러 작업에서 정해야 한다.
- 세리프에 넣은 한자는 `frontend/tools/fonts/build_fonts.py`의 `SERIF_HANJA`가 기준이다. 데이터에 새 율명 표기가 생기면 이 목록을 갱신해야 화면에 보인다.
- Pretendard는 예약 글꼴 이름(Reserved Font Name)이 있는 OFL이라 줄이거나 고치면 이름을 바꿔야 한다. 그래서 원본을 그대로 쓴다. Noto CJK 라이선스에는 예약 이름이 없다.

## 남은 질문

- 웹 첫 화면에서 글꼴을 내려받는 동안의 표시(빈 상자가 잠깐 보이는지)와 실제 로딩 시간은 측정하지 않았다.
- iOS 표시는 Mac에서 확인하지 않았다.
