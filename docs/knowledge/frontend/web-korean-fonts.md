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

- Noto Serif KR에는 탁황 `僙`(U+50D9), `潕`(U+6F55)가 없다. 백엔드 율명 기준 데이터(`pitches`)의 테스트 예시(`backend/src/test/resources/db/mapper-fixtures.sql`)도 한 옥타브 아래 황을 `僙`으로 저장한다. S15P31E102-923부터 Noto Serif CJK KR에서 서브셋을 만들어 이런 청성·탁성도 한 글자 한자로 그린다(아래 절).
- 세리프에 넣은 한자는 `frontend/tools/fonts/build_fonts.py`의 `SERIF_HANJA`가 기준이다. 데이터에 새 율명 표기가 생기면 이 목록을 갱신해야 화면에 보인다.
- Pretendard는 예약 글꼴 이름(Reserved Font Name)이 있는 OFL이라 줄이거나 고치면 이름을 바꿔야 한다. 그래서 원본을 그대로 쓴다. Noto CJK 라이선스에는 예약 이름이 없다.

## 남은 질문

- 웹 첫 화면에서 글꼴을 내려받는 동안의 표시(빈 상자가 잠깐 보이는지)와 실제 로딩 시간은 측정하지 않았다.
- iOS 표시는 Mac에서 확인하지 않았다.

## 보충 평면 율명 세 글자 (S15P31E102-813)

2026-11-12 사용자 요청으로 하배이·배유·중청대의 한글 대체 표시를 제거한다. 원인은 `Score.SourceHanja`의 세 빈 항목이며 기존 렌더러도 이 경우 한글을 쓰도록 되어 있었다. 국립국악원 [청성 표](https://www.gugak.go.kr/ency/topic/view/1585)는 중청대를 `𣴘`로 표기한다. [IDS 원문](https://www.babelstone.co.uk/CJK/IDS.TXT)은 하배이 `𢓡`(U+224E1, 彳+夷), 배유 `𠐭`(U+2042D, 亻+㽔), 중청대 `𣴘`(U+23D18, 氵+汏)의 구성을 확인하는 근거다. 글자의 존재와 앱에서 연주·저장할 수 있는 음역은 별도다.

기존 본문·Noto 폰트를 바꾸지 않고 [Jigmo 공식 배포](https://kamichikoichi.github.io/jigmo/) 20250912의 `Jigmo2.ttf`에서 세 실제 글리프만 추출해 `eolssu_yul_supplement.ttf`를 추가했다. 명조체 보조 폰트로, 세 음을 악보와 자판에서 그릴 때만 선택한다. 앱의 음높이·옥타브·정간 이벤트 ID·JSON 표기는 바꾸지 않는다. 다운로드 원본을 시스템에 설치하지 않으며 전체 폰트를 앱에 넣지 않는다.

| 대상 | SHA-256 |
| --- | --- |
| `Jigmo-20250912.zip` (35,559,738바이트) | `5744c7386d129475d87607ca66d043c8793c65448adeaedc921b6931890e5d0b` |
| 원본 `Jigmo2.ttf` | `5da3582efe77e22073b86b3b86b556d7111148a76b957cfb53318a91da2efff0` |
| 보조 폰트 (3,808바이트) | `6da41a041bccdd222953a29e94a1365d6dc08f522214e566e9f9399fa9cfa13c` |

`frontend/tools/fonts/build_supplementary_yul.py`를 fonttools 4.60.1로 두 번 실행해 같은 출력 해시를 확인했다. cmap에 위 세 코드포인트만 있고 세 글리프의 윤곽과 양의 폭이 있음을 원본·출력에서 확인한다. 수정 폰트 이름은 `Eolssu Yul Supplement`이며 원본 CC0 라이선스 전문을 제작 도구와 앱 리소스에 함께 보존한다. 모델 회귀 검사는 한글 대체 상태에서 실패했고 수정 후 통과했다.

실제 공유 `SheetEditRoute`·`ScoreNoteGlyph`와 작은 고정 악보를 사용한 Wasm 브라우저(360×820)에서 세 음의 악보 표시 및 배탁·탁·중청 자판을 확인했다. 모두 한글 대체·빈 상자 없이 실제 한자이며 접근성 이름은 하배이칙·배유빈·중청대려로 유지됐다. 검증용 진입점은 확인 후 복원했다. 전체 Android host 검사 1,498개(실패0, 기존 건너뜀4)와 `:androidApp:assembleDebug`가 통과했다. Android 실기기와 iOS는 미검증이다.

## 탁성·하배·중청 율명을 세리프 원본 글리프로 (S15P31E102-923)

- 문제: 대금 악보의 배·하배 율명(`僙`·`㑣` 등)은 세리프 서브셋에 없어 변(亻)을 62%, 율명을 80% 크기로 조합하거나(정간보) 기기 산세리프 글꼴로 대체돼(연습 단계 화면) 단소·소금보다 획이 가늘게 보였다.
- 확인(2026-11-15, fonttools): 정간보 원본 표기 60자(`Score.kt`의 `SourceHanja`, 옥타브 -2~2) 중 Noto Serif KR SubsetOTF·Windows Noto Serif KR 가변·바탕에는 40자가 없다. Noto Serif CJK KR(`Serif/OTF/Korean/NotoSerifCJKkr-SemiBold.otf`, 24,696,816바이트)에는 보충 평면 세 글자(`𢓡`·`𠐭`·`𣴘`)를 뺀 57자가 모두 있다. 라이선스 원문은 기존 `NotoSerifKR-OFL.txt`와 같다(OFL 1.1).
- 같은 디자인이라 원본만 바꿔 다시 만들었다. 기존 2,786자의 윤곽·폭·hhea/OS2 세로 지표는 그대로이고 율명 47자만 늘었다(SemiBold 901,492 → 935,308바이트, Bold 956,536 → 990,988바이트).
- 렌더러는 글꼴에 글자가 있으면 그대로 그린다. 변 + 기본 율명 조합은 `yulGlyphParts` 한 규칙으로 정하고, 옥타브 ±3처럼 원본 표기가 없을 때만 쓴다. 조합할 때는 작은 변을 율명보다 한 단계 굵게(SemiBold → Bold) 그려 획 굵기를 맞춘다.
- 연습 빠른 설정의 음 상태 문구는 본문 글꼴(IBM Plex Sans KR, 한자 없음)이라 율명 한자 없이 이름만 쓴다.

## 본문 글꼴 CFF 변환 (S15P31E102-1267)

- 문제: 웹은 첫 방문에 본문 글꼴 IBM Plex Sans KR 세 굵기(TTF, 합계 8,527,428바이트)를 받는다. 쓰지 않는 Pretendard 세 굵기·Song Myung(합계 6,770,016바이트)도 배포 폴더·APK·IPA에 들어 있었다. 웹은 쓰는 글꼴만 내려받으므로 미사용 글꼴 삭제는 첫 로딩을 줄이지 않는다.
- 결정(2026-11-23 사용자): 한글 11,172자를 모두 남기고 CFF 변환만 한다. KS X 1001 2,350자 서브셋(굵기당 약 0.48MB)은 사용자가 입력한 드문 음절이 빈 상자가 되므로 하지 않는다. OFL 예약 이름 `Plex` 때문에 글꼴 이름을 `Eolssu Sans KR`, 파일 이름을 `eolssu_sans_kr_*.otf`로 바꿨다. 미사용 글꼴과 그 라이선스 원문(`pretendard_ofl.txt`·`song_myung_ofl.txt`·`Pretendard-OFL.txt`)은 지웠다(S15P31E102-768의 Song Myung 보존 결정을 이 티켓 범위로 바꿈).
- 방법: `frontend/tools/fonts/build_body_font.py`가 이차 곡선 여러 개를 삼차 곡선 하나로 합친다(`Qu2CuPen`, 허용 오차 1/1000em). TrueType 힌팅 표(`cvt`·`fpgm`·`prep`·`gasp`·`VDMX`)는 CFF에 쓸 수 없어 뺐다. 원본에서 이 표들은 합계 약 2.6KB로 작다. 크기 대부분은 윤곽(`glyf` 2.59~2.67MB)이다.

| 파일 | 원본 TTF | CFF(OTF) | 줄어든 비율 |
| --- | --- | --- | --- |
| Regular | 2,797,100 | 2,027,132 | 27.5% |
| SemiBold | 2,854,744 | 2,022,768 | 29.1% |
| Bold | 2,875,584 | 2,027,436 | 29.5% |
| 합계 | 8,527,428 | 6,077,336 | 28.7% |

- 같은 원본에서 다른 방법도 쟀다(Regular, 2026-11-23 Windows, fonttools 4.68.0). 이차 곡선을 그대로 삼차로 바꾼 CFF는 2,750,564바이트로 거의 줄지 않았다. 2,350자 서브셋 TTF는 848,700, 서브셋 + CFF는 476,368바이트였다. CFF 서브루틴 압축(`cffsubr`)은 측정하지 않았다.
- 검증(2026-11-24 macOS, fonttools 4.60.1·Pillow 13.0.0)
  - 원본 세 파일의 SHA-256이 Google Fonts `ofl/ibmplexsanskr` 원본, 기존 앱 파일과 같다.
  - 같은 명령을 두 번 실행해 세 출력의 SHA-256이 같았다.
  - 굵기마다 글리프 12,240개, cmap, 글자 폭(`hmtx` 전진 폭), `hhea`·`OS/2` 세로 지표와 굵기 값이 원본과 같다. 한글 음절 11,172자가 모두 있다. `name`의 1·3·4·6·16·17번에 `Plex`가 없다.
  - 윤곽이 있는 12,228개 글리프의 경계 상자 차이는 최대 1 단위(1/1000em), 면적 차이는 최대 1.2%(원 문자 `ⓘ`처럼 얇은 고리)였다.
  - FreeType(Pillow)으로 같은 문장을 그리면 테두리에 1px 차이가 보인다. TrueType과 CFF의 힌팅·격자 맞춤이 달라서이고 윤곽 차이는 위 수치 범위다.
- 배포 폴더(2026-11-24 `:webApp:wasmJsBrowserDistribution`): 전체 38,638,268바이트이고, 이 중 Wasm 두 개가 20,052,193바이트(gzip 5단계 6,539,998), `composeResources`가 16,243,805바이트, 글꼴이 8,017,180바이트다. 바꾸기 전 develop은 바뀐 파일 크기로 계산해 약 47.6MB다(Wasm 크기 차이는 빼고 계산).
- 전송 크기: 운영 nginx와 같은 gzip 5단계로 압축하면 본문 글꼴 세 굵기는 3,118,430 → 2,396,528바이트(-23%)다. 웹은 첫 화면에서 본문 세 굵기와 세리프 두 굵기를 모두 받는다(Chrome·Safari 요청 기록).
- 화면 확인: Chrome 156(앱 내장 브라우저)에서 로그인·홈 화면의 한글이 빈 상자 없이 그려졌다. macOS Safari 26.2에서도 같은 배포본이 오류 없이 홈까지 열리고 새 글꼴 다섯 파일을 모두 받았다. Android·iOS 앱 화면은 확인하지 않았다.
