# 다양한 박자 악보의 실제 인식과 장단 연결 검증

2026-11-13 · [S15P31E102-883](https://ssafy.atlassian.net/browse/S15P31E102-883) · Story318 · 담당 박찬형. 자료·검증 결과이며 제품 변경의 확정 기획이 아니다. [기계 판독 결과·출처·해시](meter-import-results.json), [기존 4박 수정](../../../../specs/staff-four-beat-import.md).

## 후속 구현과 재검증

사용자의 ‘제안한 대로 해’와 ‘악보마다 저장해 다른 기기에서도 유지’ 답변에 따라 [리듬 보존·독립 반주 계약](../../../../specs/score-rhythm-preservation.md)을 구현했다. 아래 본문은 **수정 전 기준 조사**로 보존하며, 현재 결과는 이 절과 [후속 구조화 결과](meter-import-followup-results.json)를 따른다. 새 전통 장단을 등록하는 대신 기존 무반주 계약과 악보별 기본 박 반주를 사용한다.

기반은 `origin/develop 8897d7fb`이며, 미병합 빠르기 작업882의 기존 `72768032`를 로컬 통합했다. 기존882 작업트리는 변경하지 않았다. 변경은 별도 `rhythm-preserve-883` 작업트리의 `fix(ai)/S15P31E102-883-악보-리듬-보존과-반주-독립-저장-지원` 브랜치에 있다. 운영·기존 개인 악보·원본·카탈로그는 변경하지 않았다. 사용자 중단 요청에 따라 인수인계를 포함한 Draft MR로 넘긴다. [남은 작업](../../../../plans/S15P31E102-883.md)을 병합 전에 확인해야 하며 배포는 하지 않았다.

| 수정·검증 | 관찰 결과 |
| --- | --- |
| 각간 붙임줄 | 고정4/4 두 마디가8Q NOTE 하나로 유지되고 다음 각 이음이 같은 eventId를 참조한다. 붙임줄 없는 같은 음은 별도 onset이다 |
| 변박 | 고정3/4→2/4→3/4는3·2·3칸·총8Q·추가 쉼0. Q/DQ 혼합은 공통 최소 셀 음가로 보존한다. 출판사 Ballade 첫9마디 XML도4Q×7+6Q×2=40Q 보존 |
| 미지원 자동 장단 | 2/4·3/4·6/8·5/8·7/8은 빈 장단명/null ID로 저장된다. 정상4/4·9/8·12/8과 명시 선택은 유지한다 |
| 기본 박 반주 | 2박Q·3박Q·2박DQ, 5/8 두 강세 묶음, 7/8 세 강세 묶음과 첫 강박 정간을 authoring 메타데이터에 저장한다. 원본 틱·이벤트·공통 스키마와 DB 스키마는 유지한다 |
| 실제 원보 선율 | Au clair44음·Greensleeves72음·Kesh170음·Butterfly146음, 총432음의 음고·시작·길이가 독립 XML/LY 리더와 전부 일치 |
| 실제 불규칙 박자 | Al lez는 여전히6/8, Mileva는2/4로 OMR 오독한다. 낮은 박자표 grade와 원본 page/bbox, 반복 불완전마디 사유를 검수에 표시하며 자동 장단을 억제한다. 음가만으로5/8·7/8을 확정 복원하지 않는다 |
| 실제 변박 오독 | Ballade OMR의 빈8마디·4/4만 남은 박자표는 복원하지 못한다. 34Q의 인식된 길이를 보존하고 빈 마디/초과 마디 검수를 남긴다. 출판사 XML40Q 성공과 구분한다 |
| 아리랑 글자층 | 장구 예시 열을 제외하여17각→원본16각, 첫 임 onset0·61음·총144tick으로 교정. 가짜 시작 쉼3DQ가 사라짐 |
| 같은 아리랑 PNG | 실제 모델이4→1(알려진 높이 오류)→4→0의 네 번 시도 후28.281초에16각·61음 생성. 확인 필요17개, 글자층 대비4곳 음 시작/길이 차이를 남김. 두 경로의 음 수 일치만으로 정확도 승인하지 않음 |
| 고정·실제 시간표/저장 | 자작 XML11개+실제 OMR13개 모두 DATA 선율 preflight·시간표 환산, BE 메모리 저장 왕복 통과. 검수 이슈 해결이나 원본 정확도 승인을 대신하지 않음 |
| 실제 HTTP·SQL | 격리 MySQL8.4.13/Flyway43, 인증 GET/초안PUT/확정PUT/음원POST8개 검사 통과. 7박자 사례·반주 저장/해제·빠르기 해시·잘못된 요청400. OMR 응답·렌더러 estimate·객체 저장소는 대역 |

필수 회귀는 OMR222개, 글자층25개, BE clean build761개와 기존 악보 후속 수정 뒤762개(기존1개 미실행), 관련 DB79개(실제 CAT 생성2개 미실행), FE host1678개(5개 미실행), Web Wasm 컴파일 통과다. FE·BE 편집 왕복도 같은24사례가 통과했다. 전체 AI 채점 검사 시도에서는 ffmpeg/ffprobe 미설치와 Linux에서 실행한 Windows 전용 검사41개가 환경 실패했으며 OMR 회귀와 분리한다.

전체 증거는 통합 작업트리 `.scratch/{actual-staff,actual-extra,actual-scan,contracts-preflight,source-full-comparison.json,omr-final.xml}` 및 `data/processed/meter-validation/`에 있다. 테스트 명령은 [DATA README](../../../../../data/README.md#박자별-원본과-리듬-보존-검증-883), [스펙](../../../../specs/score-rhythm-preservation.md#검증과-범위)을 따른다. 고정 XML은 자작 CC0이며 외부 저작권 원본·MusicXML/전체 인식 산출물을 Git에 넣지 않는다.

실기기 SM_S926N/Android17/API37에서16회 타격·2+3강세·첫강박tick4 뒤 시작·백그라운드 STOP→RELEASE를 확인했다. 별도 앱은 제거했다. 실제 UI의 프리셋 선택→적용→저장→재열기 여정은 미완료이며 음향지연은 측정하지 않았다.

## 수정 전 기준 조사

**기존 카탈로그만으로 모든 박자를 처리할 수 없다.** 4/4·9/8·12/8은 주기가 맞는 후보가 있지만, 2/4·3/4·6/8·5/8·7/8의 현재 가져오기 결과는 선택 가능한 후보가 없다. 장단 추가에 앞서 붙임줄·변박·박자표 오인식·정간보의 장단 예시 혼입을 고쳐야 한다. 서양 곡의 9/8을 읽었다는 사실은 그 곡이 세마치라는 음악적 판정이 아니다.

## 판본과 실행 경계

- 검증 소스: `develop a89053c4a1d96bc9910a1f91dea8453024243481`. [MR726](https://lab.ssafy.com/s15-final/S15P31E102/-/merge_requests/726)은 11월13일16:26 KST 병합됐다. 880 수정이 포함돼 있다.
- 운영 OMR 이미지 라벨: `aa9b92ac4984059d3a3b3c84a42b37089ee82744`, 이미지 `70b2e1dbee82`. 같은 실제 Au clair MusicXML을 이 이미지에서 변환하면 굿거리·DOTTED_QUARTER·박/정간3/2 tick이다. 880 포함 소스에서는 4박 기본 반주·QUARTER·1/1 tick이다. 운영 서비스를 교체하거나 배포하지 않았다.
- 실제 인식: 위 기존 이미지의 Audiveris5.11.0·생성기에 검증 소스를 읽기 전용으로 마운트한 **격리 컨테이너**. CPU2·메모리2GiB·실행 가능한 tmpfs768MiB·네트워크 없음. 300dpi PDFium PNG 입력이며 다운로드/업로드 HTTP를 통과한 앱 여정은 아니다. 엔진의 실제 `recognize(Job)`와 응답 빌더를 실행했다.
- 정간보 PNG: 이미 준비된 로컬 `eolssu-omr:scan458-local` 이미지 `68f2de5c9da1`에 같은 소스를 마운트했다. CPU2·2GiB·tmpfs1GiB, MALerLab e95045f/v1.0.0. 운영 기본 이미지의 스캔 설정 누락과 별도로 실제 리더까지 실행했다.
- 고정 XML11개는 **OMR을 거치지 않은 변환 검사**다. 실제 PDF 성공 건수에 포함하지 않는다. 새 숫자 빠르기 인식은882의 범위다.
- FE23사례는 실제 Android host 편집/후보 함수를, BE23사례는 실제 저장 서비스·검증기·시간표 디코더를 실행했다. BE Repository와 음높이 카탈로그는 시험 대역이다. MySQL 왕복·실제 UI·기기 반주 지연을 검증한 결과는 아니다.

## 확보한 원본과 이용 조건

원본은 `data/raw/meter-validation/`, 렌더·전체 MusicXML/OMR/응답·실패 로그는 `data/processed/meter-validation/`에 있다. 두 경로는 Git 제외다. 공개 자료는 이번 실행에서 직접 GET했다. Blue Valentine과 정간보 아리랑은 기존 사용자 로컬 파일을 보존해 복사했다. 전송·변환 전후 SHA-256은 결과 JSON에 기록한다.

| ID / 실제 자료 | 원본 범위·예상 박자 | 출처·조건 | 원문 / 가공본 재배포 |
| --- | --- | --- | --- |
| bach-bourree / Bach BWV1013 Bourrée Angloise | PDF5쪽 중5쪽, 2/4 | [Mutopia1528](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1528), Jacob Shapiro 판각 Public Domain | 가능 / 가능 |
| greensleeves / Greensleeves | PDF1쪽, **3/4** | [Noctave](https://noctavepiano.com/en/sheet-music/trad-greensleeves/), 편곡·판각 포함 CC0 | 가능 / 가능 |
| au-clair / Au clair de la lune | PDF1쪽, 4/4 | [Noctave](https://noctavepiano.com/en/sheet-music/trad-au-clair-de-la-lune/), CC0 | 가능 / 가능 |
| kesh / The Kesh | PDF1쪽, 6/8 | [Breizh Partitions794](https://www.celticscores.com/sheet-music/794_The_Kesh), PDF·LilyPond CC BY-SA3.0 | 가능 / 가능: 출처·변경 표시·동일조건 |
| butterfly / The Butterfly | PDF1쪽, 9/8 | [Breizh Partitions795](https://www.celticscores.com/sheet-music/795_The_Butterfly), PDF·LilyPond CC BY-SA3.0 | 가능 / 가능: 같은 조건 |
| circassian / Cercle Circassien | GIF577×434, 12/8 | [Breizh Partitions86](https://www.celticscores.com/sheet-music/86_Circassian_circle), 게시물·그림 CC BY. 버전 미기재 | 현재 불가 / 현재 불가: 배포 적용 버전 확인 보류. 허용된 로컬 재사용 시험만 수행 |
| al-lez-vamm / Al lez Vamm | PDF1쪽, 5/8·못갖춘마디 | [Michel Allain 편곡819](https://www.partitions.bzh/partition/819_Al_lez_Vamm), 게시물 CC BY-SA·변경/재사용 허용. PDF 버전 미기재 | 현재 불가 / 현재 불가: 공유 데이터셋 편입 전 적용 버전 확인 |
| mileva / Mileva Rachenitza | PDF3쪽 중1쪽, 7/8 | [Patrick Duplenne 편곡1730](https://www.celticscores.com/sheet-music/1730_Mileva_Rachenitza), 게시물 CC BY-SA·변경/재사용 허용. PDF 버전 미기재 | 현재 불가 / 현재 불가: 같은 보류 |
| chopin-12-8 / Nocturne Op.9 No.2 쉬운 판 | PDF1쪽, 12/8·못갖춘마디 | [Noctave](https://noctavepiano.com/en/sheet-music/chopin-nocturne-op9-2-facile/), 새 편곡 CC0 | 가능 / 가능 |
| amazing-pickup / Amazing Grace | PDF1쪽, **9/8**·점4분 못갖춘마디 | [Mutopia1832](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1832), Breizh Partitions CC BY-SA3.0 | 가능 / 가능: 출처·변경 표시·동일조건 |
| ballade-variable / Chopin Ballade No.1 Op.23 | PDF15쪽 중1쪽 상단1550px, 첫9마디의4/4→6/4 | [Noctave](https://noctavepiano.com/en/sheet-music/chopin-ballade-1/), CC BY4.0. Noctave·Psamtik/PDMX·The Fryderyk Chopin Institute/chopinscores.org에 대한 게시물 지정 출처와 변경 표시 | 가능 / 가능: 지정 출처·라이선스·변경 표시. 원본 전곡 판본의 상위 출처 재검증 TODO도 보존 |
| blue-valentine / 사용자 easy PDF | 전체 원본 보존·첫 쪽만 시험, 4/4 | 사용자 제공. 기존880 사례의 원본 | 현재 불가 / 현재 불가: 재배포 허가 미확인 |
| jeonggan-arirang / 소금 아리랑 | 표준악보집 PDF194·인쇄386–387, 3소박3박·16각×3정간 | [국립국악원 게시물](https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=44199&boardtypeid=102&menuid=001003002011&pagesize=10), KOGL4 | 원문 가능: 출처·비상업·무변경 / 가공본 현재 불가: 변경금지·별도 허락 미확보 |

아리랑 발췌는801에서 확보한 공식 전체 PDF SHA `fe0c268d864568b794a462e5b1875c1c9d8742bff671260fa486e525ec760558`의194쪽과72dpi 전체 페이지 픽셀이 같다. 발췌·PNG를 제품용 재배포 허가로 취급하지 않는다. 최초 수집 메모의 Amazing Grace3/4 가정은 PDF·공개 LilyPond의9/8 확인으로 정정했다. Greensleeves도 이 판본은6/8이 아니다. flutetunes 후보는 변경 제한 때문에 변환 표본에서 제외했다.

## 실제 인식과 연결 결과

표의 `박/정간 tick`은 `ticksPerBeat/ticksPerJeonggan`이다. Q는4분음표, DQ는점4분음표, E는8분음표다. `각×칸`은 출력 격자이며 원본 마디 보존을 뜻하지 않는다. 후보는 FE의 실제 `canSelectJangdan`과 BE의 주기 검사에서 확인한 ID다.

| 자료 | 원본 → Audiveris/리더 구조 | 공통 단위·박/정간 tick·출력 격자 | 음·시작·길이 검수 | 후보 / 판정 |
| --- | --- | --- | --- | --- |
| Bach | 2/4 → 2/4. 전체 쪽60초 TIMEOUT, 상단1100px 발췌37.7초 완료 | DQ·6/4·42×2, 정간1Q | 240음 생성. 완전 정답과 음별 대조는 미완료 | 없음. 단순2박에 굿거리 기본값·주기 불일치 |
| Greensleeves | 3/4 → 3/4, 38.8초 | DQ·3/2·33×3, 정간1Q | **선율72음 전부** 출판사 XML voice5와 음고·시작·길이 일치. 앞2Q 쉼은 가져오기 규칙 | 없음. 리듬은 보존됐지만 연결/확정 저장 차단 |
| Au clair | 4/4 → 4/4, 30.6초 | Q·1/1·16×4 | **44음 전부** 출판사 XML voice5와 일치 | 7·12. 기본 제안12는880 수정. 단모리7은 시간 호환일 뿐 편곡 적합 판정 아님 |
| The Kesh | 6/8 → 6/8, 32.2초 | DQ·3/3·32×2, 정간1DQ | **170음 전부**, 공개 LilyPond의 두 반복을 펼친 음고·시작·길이 일치 | 없음. 굿거리4DQ 주기에2DQ 각을 넣을 수 없음 |
| The Butterfly | 9/8 → 9/8, 31.2초 | DQ·3/3·24×3 | **146음 전부**, 공개 LilyPond 반복 포함 일치 | 1. 세마치3DQ 주기 호환. Irish slip jig를 세마치로 확정하지 않음 |
| Cercle Circassien | 원본12/8 GIF → NO_STAFF_FOUND4.3초. 4배 확대도60초 TIMEOUT | 공통 악보 없음 | 미검증. 확대를 새로운 원본/성공으로 세지 않음 | 판정 불가. 원본 해상도 제약 |
| Al lez Vamm | **5/8 → 6/8** 두 성부 모두 오독, 40.5초 | DQ·3/3·11×2 | 원보 픽업+첫 완전마디5음의 위치 대조 실패. 원보 앞쉼2Q 대신2.5Q | 없음. 우선 박자표 오인식 수정 필요. 새 장단으로 해결되지 않음 |
| Mileva | **7/8 → 2/4**, 40.3초 | DQ·3/2·19×2 | 첫7/8 마디4음만 일치. 이후 길이/격자 손실·LOW_CONFIDENCE16. 전쪽 성공 아님 | 없음. OMR 오인식과7/8 지원을 분리 |
| Chopin 발췌 | 전체60초 TIMEOUT, 상단1000px22.5초 완료,12/8 유지 | DQ·12/12·3×4 | 첫2마디 출판사 XML7음 대비12음. 원보에 없는 시작음·길이 변경, LOW_CONFIDENCE10 | 2·4·5·11. 후보가 있어도 선율 정확도 실패 |
| Amazing Grace | 9/8 → 9/8, 36.8초 | DQ·6/6·17×3 | 원본 픽업1DQ에 앞2DQ 쉼. 꾸밈음63개 제외·검수4곳. 전곡 정확도 미승인 | 1. 못갖춘마디 정렬과 전통 명칭 적합성은 별개 |
| Blue Valentine 첫 쪽 | 4/4 → 4/4, 38.0초 | Q·4/4·25×4 | 105음·쉼5개. 원본 전체503음 대조 결과와 혼동하지 않음 | 7·12. 880 적용본에서 단위/후보 연결 확인 |
| 아리랑 글자층 PDF | 본선율16각 + 옆 장단 예시1열 → **17각**. 1.7초 | DQ·3/3·17×3 | 61음·쉼5개. 시작3DQ가 가짜 쉼으로 추가됨 | 1. 주기는 맞지만 원문 선율의 시작이1.875초 늦음(96DQ BPM) |
| 같은 아리랑 PNG | 실제 스캔 리더12.0초, 격자 높이 AssertionError | 공통 악보 없음 | 첫 크기 NO_GRID, 0.5배에서 비정상 종료. 후속 격자 경로 미실행 | 실패. 글자층 PDF 성공으로 스캔 성공을 대신하지 않음 |
| Ballade 변박 발췌 | 원본/출판사XML은8마디에서4/4→6/4. 실제/확대 Audiveris 모두 **4/4만 출력**, 총20.2초 | Q·12/12·9×4 | 원보9마디40Q에 대해 출력36Q. 34음·쉼6개, OVERFULL/GAP3경고. 전곡 음고 정답 대조 아님 | 7·12가 나와 확정 저장까지 허용됨. **후보/저장 통과가 변박 보존을 보장하지 않는 실제 사례** |

실제 원본13종에서 완료 응답을 얻은12종도 모두 음악적 성공으로 세지 않는다. 전곡 음별 정답 대조는4곡432음(44+72+170+146)만 통과했다. 다른 곡은 표에 적은 부분 검수만 수행했다. 변박은 실제 Ballade PDF와 아래 고정 XML 양쪽에서 실패를 확인했다.

Ballade 출판사 XML의 첫9마디를 OMR 없이 직접 변환하면 TIME_SIGNATURE_CHANGED(8마디)를 알리고40Q를10각×4칸으로 바꾼다. 실제 PDF의 Audiveris 단계에서는6/4 자체가 사라져36Q·9각×4칸이 된다. 따라서 **OMR의 박자표 누락**과 **변환기의 첫 박자 격자 고정**은 별도 실패다. `variable-layer-comparison.json`에 두 경로를 분리했다.

## 카탈로그 단위·주기와 후보 제외 이유

2026-11-13 공개 `GET /api/jangdans` 및12개 상세 응답의 이름·패턴·기본 BPM·단위·`patternCellEighths`가 `data/reference/jangdans.json`12행과 모두 같다. 한 장단 Q길이 = `beatCount × patternCellEighths / 2`다. 한 각 tick은 해당 장단 주기의 양의 정수배여야 하며 BPM 단위도 같아야 한다.

| ID·명칭 | 칸수 × 칸 음가(E개수) | BPM 단위·기본값 | 1주기(Q) |
| --- | --- | --- | ---: |
| 1 세마치 | 9×1 | DQ96 | 4.5 |
| 2 굿거리 | 12×1 | DQ66 | 6 |
| 3 중모리 | 12×2 | Q88 | 12 |
| 4 자진모리 | 12×1 | DQ96 | 6 |
| 5 중중모리 | 12×1 | DQ80 | 6 |
| 6 엇모리 | 10×1 | Q90 | 5 |
| 7 단모리 | 8×1 | Q120 | 4 |
| 8 타령 | 12×1 | Q48 | 6 |
| 9 계면가락도드리 | 12×1 | Q48 | 6 |
| 10 세령산 | 10×3 | Q63 | 15 |
| 11 자진타령 | 12×1 | DQ60 | 6 |
| 12 4박 기본 반주 | 4×2 | Q160 | 4 |

- 4/4 Q 각은7·12만 허용. DQ 항목1·2·4·5·11은 단위가 다르며 나머지 Q 항목의 주기5·6·12·15Q는4Q 각을 나누지 못한다.
- 9/8 DQ 각은1만 허용. 나머지 DQ 항목의6Q 주기는4.5Q 각에 들어가지 않는다. Q 항목은 단위가 다르다.
- 12/8 DQ 각은2·4·5·11 허용. 세마치4.5Q 주기는6Q 각을 나누지 못하고 Q 항목은 단위가 다르다.
- 현재2/4·3/4·6/8·5/8·7/8 결과에는 굿거리 DQ가 붙는다. Q 카탈로그는 단위 검사에서, DQ 카탈로그는 각 길이/주기 검사에서 제외된다.5/8·7/8 고정 XML의 정간은0.5Q지만 BPM 단위는 기본값 DQ라서 박/정간3/1 tick이다.
- Q 단위로만 바꾸어도2/4의2Q,3/4의3Q,5/8의2.5Q,7/8의3.5Q와 같은 단일 마디를 수용할 기존 주기는 없다. 두 마디를 한 각으로 합치면2/4→4Q,3/4→6Q,5/8→5Q의 시간 호환은 만들 수 있으나 원보의 마디 경계를 바꾸고 악센트를 확인해야 한다. 자동 전통 장단 확정의 근거가 아니다.

[국립국악원 세마치 교육자료](https://academy.gugak.go.kr/dp/pds/HTML5-1-7-3/HTML5-1-7-3-3/index.htm)는 점4분3박과 구음을, [굿거리 자료](https://academy.gugak.go.kr/dp/pds/HTML5-1-7-3/HTML5-1-7-3-2/index.htm)는6소박 두 묶음과 구음을 제시한다. [엇모리 자료](https://academy.gugak.go.kr/dp/pds/HTML5-4-40/HTML5-4-40-4/index.htm)는3+2+3+2의10소박 주기다. 따라서5/8 한 마디의5소박을 기존 엇모리 한 장단과 동일시하면 안 된다. 이 근거는 음악적 차이를 설명하며 새로운 전통 장단명을 승인하지 않는다.

## 빠르기와 저장·재생 시간축

기본 BPM은 백엔드가 선택한 장단의 카탈로그 기본값이다. 다음 원본 수치는 사람이 원본/PDF/출판사 XML·LilyPond에서 확인한 값이며, 이번 인식기가 자동으로 읽었다는 뜻이 아니다.

| 사례 | 원본 숫자·단위 | 저장 시작 기본값 | 시간축 해석 |
| --- | --- | --- | --- |
| Au clair | Q100 | Q160 | 선율64Q는 원본38.4초, 기본값24초 |
| Greensleeves | Q112 | DQ66(=Q99) | 본음97Q와 앞쉼2Q를 포함해 기본60초. 원본 선율과 BPM 차이 분리 |
| Kesh | 비표시 LilyPond DQ140 | DQ66 | 96Q=64DQ: 27.429초 →58.182초. 숫자는 악보에 보이는 지시가 아님 |
| Butterfly | 비표시 LilyPond DQ140 | DQ96 |108Q=72DQ:30.857초 →45초 |
| Chopin | **E112**(PDF·XML) | DQ66(=E198) | 같은112라도 Q/DQ로 취급하면 틀린다. 이번 발췌는 선율 오인식도 있어 원곡 시간 복원 성공으로 보지 않음 |
| Blue Valentine | 시작 Q160 | Q160 | 숫자가 같아도 현재160의 출처는 카탈로그12. 원본 템포 인식/가속 복원 증거 아님 |
| 국악원 아리랑 | 수치 없음 | DQ96 |51DQ=31.875초. 가짜 첫 각3DQ를 빼면48DQ=30초 |

고정 XML11개와 완료 응답12개에 대해 실제 재생 어댑터의 eventId·시작/길이 초를 `tick / ticksPerBeat × 60/BPM`과 대조했다.23개 모두 저장된 **현재 값**과 일치한다. 잘못 인식된 현재 값도 일관되게 재생될 수 있으므로 원본 충실도와 구분한다.

FE23사례는 원래 columns/events/music 보존, 직렬화→재읽기, 후보·선택 가능 목록 보존을 통과했다. BE23사례는 초안 저장·Repository 재조회에서 같은 구조를 보존했다. 호환 후보가 있는11사례는 확정 저장·판본 증가 후 실제 디코더의 이벤트들을 대조했고, 후보가 없는12사례는 `InvalidRequest`로 차단됐다. 시험 대역 저장소여서 SQL의 트랜잭션·영속성·실제 네트워크 저장을 증명하지 않는다.

## 고정 XML 회귀와 결함의 최소 재현

`data/tests/fixtures/meter-validation/`의 자체 작성 CC0 XML11개를 실제 변환기에 넣었다. 일반8박자와3/4 못갖춘마디의 음고·시작·길이9개는 통과하고 아래2개는 실패한다. CLI 종료 코드1을 유지했다. 별도 FE/BE23사례 통과는 이2개 결함이 해결됐다는 뜻이 아니다.

| 우선순위·원인 | 최소 재현·관측 | 필요한 수정 범위·완료 조건 |
| --- | --- | --- |
| P1 붙임줄 재발음 | `tied-4-4.musicxml`: C4 4Q tie-start + C4 4Q tie-stop. 기대 C4 0–8Q 한 음, 실제0–4·4–8 두 NOTE, 경고 없음 | AI `staff_musicxml._layout`가 각 경계에서 본음을 다시 적고 staff 경로 `lines_to_score`가 각간 지속을 켜지 않는다. 명시적 지속·eventId 연결을 보존하고 총8Q·한 onset·새 각 표시를 회귀로 확인 |
| P1 변박 격자 손실 | `mixed-3-4-to-2-4.musicxml`:3/4→2/4→3/4, 실제 각3·3·3. 마지막 E4가5–6·6–8Q로 분리되고8Q 음악 뒤1Q 쉼 추가 | 첫 meter만 전체 layout 기준으로 사용한다. 마디별 길이/경계를 보존하거나 지원 전 명시적 검수 차단. 기대3·2·3Q, 추가 쉼0, 재발음0. TIME_SIGNATURE_CHANGED 안내만으로 보존 성공이라 하지 않음 |
| P1 박자표 오독 | Al lez Vamm5/8→6/8, Mileva7/8→2/4. 원본 이미지·raw .mxl을 보존 | Audiveris 출력과 표시 박자표·마디 음가 교차 검수. 확신 없는 결과에 전통명 자동 확정 금지. 같은 이미지에서5/8·7/8 또는 명시 검수 상태, 원래 onset/길이 보존 확인 |
| P1 리듬과 필수 장단 결합 | 2/4·3/4·6/8 고정/실제 악보 모두 후보0, 초안은 저장되지만 확정 저장 거부 | AI 기본값·FE 선택·BE 확인·재생 계약을 함께 설계. 무반주/박수·음가 직접 지정 또는 검수된 기본 반주로 음 길이를 유지한 저장·조회·재생을 허용 |
| P1 정간보 장단 예시 혼입 | 아리랑 PDF의 옆 동그라미/이음 예시가 `c0` REST9tick으로 추가됨.61음의 첫 NOTE는tick9 | DATA `standard_score_text`가 이웃 이음 열을 허용하면서 비선율 장단 예시도 포함. 인식 영역/열 역할을 구분하되 실제 쉼뿐인 각은 보존. 이 원본16각48정간·첫 임tick0, 다른 실제 시작쉼 회귀 함께 통과 |
| P2 스캔의 복구 가능 격자 오류 | 같은 PNG: 첫크기 리더 code4→0.5배 code1/높이 AssertionError로 종료.60초 중12초에 실패 | DATA reader·AI scan 경계에서 알려진 격자 실패를 분류하고 지원 대체 크기/기하 격자 시도. 일반 엔진 오류/OOM을 덮지 않음. 같은 PNG의 성공 또는 명시NO_GRID·후속 시도 근거 필요 |
| P2 12/8 선율 오인식·밀집도 | Chopin 첫2마디7음→12음. 시작 숫자 빠르기 주변의 가짜 음과 OFF_GRID 계열10경고 | 원본·rawXML·출판사 XML을882의 숫자 영역 분석과 대조. 이 요청에서는 AI 제품 파일을 고치지 않음. 새 음 유입0·원본 음가/붙임줄 보존으로 검증 |

## 장단/반주 추가와 제품 제안

1. **P1 원본 리듬 독립 저장**을 우선 제안한다. 한 각의 박수·박 단위·못갖춘 위치를 전통명과 분리하고, 무반주 또는 박 클릭을 허용한다. 완료 조건은 위5종에서 후보 없음 때문에 정확한 리듬의 확정 저장이 막히지 않고, FE/BE/재생이 같은 시간축을 쓰는 것이다. 공유 계약 변경이므로 팀 합의 후 구현한다.
2. **P2 기본 반주**는2Q·3Q·2DQ 주기,5/8·7/8은 사용자가 고른2+3/3+2·2+2+3 등의 악센트형을 제안한다. 전통 장단 이름을 새로 붙이지 않는다. 기존 `4박 기본 반주`와 같은 제품 분류이며 구음/샘플·음가·기본 BPM·음악 검수 후 등록한다. 완료 조건은 각 주기 길이와 명칭·강세 검수, 원본 이벤트 보존, 후보/저장/재생 회귀다.
3. 4/4·9/8·12/8은 **주기만 보면 기존 항목이 충분**하다.9/8·12/8 서양 곡에 사용할 중립3DQ·4DQ 클릭/반주가 필요한지는 음악적 UX 선택이다. 실제 후보가 있다는 이유로 세마치·굿거리를 확정하지 않는다.
4.5/8 두 마디=엇모리10소박 등 **여러 마디 묶기**는 대안이나 단일 마디 지원의 대체로 자동 적용하지 않는다. 원보 마디 표시와 장단 위상·강세가 맞는지 확인한다. 변박은 고정 단일 장단 추가만으로 해결되지 않는다.

이번 시험으로 새 전통 장단의 명칭·정답 패턴 필요성이 증명되지는 않았다. 운영 카탈로그·사용자 악보·기존880/882 제품 구현은 변경하지 않았다.

## 재현과 근거 위치

[DATA README](../../../../../data/README.md#박자별-악보-검증-883)의 명령으로 원본 획득·고정 XML·독립 원보 대조·FE/BE를 재실행한다. 원본·큰 산출물은 Git에 없으므로 원문 라이선스 조건에 따라 다시 준비한다.

- `acquisition.json`, `catalog-list.json`, `catalog-1.json`~`catalog-12.json`, `catalog-comparison.json`: 원본 해시·권리 근거 HTML과 카탈로그 일치.
- `actual/<ID>/engine.json`, `response.json`, `audiveris-0/*.mxl|*.omr`: 실제 엔진 결과. Bach/Chopin은 `bach-short`, `chopin-short`. 원본 전체 실패·재시도는 `retries/`, `chopin-full-failure/`에 따로 둔다.
- `source-full-comparison.json`:4곡432음 전부 대조. `source-prefix-comparison.json`: 불규칙·Chopin 부분 검수. 후자는 완전 정답이 아니다.
- `contracts/audit.json`:11개 고정 XML +12개 실제 완료 결과의 단위·전체12후보 제외 이유·재생 초. `frontend-runtime.json`, `backend-runtime.json`:23사례 실제 제품 함수 실행.
- `scan-local-diagnostic/jeonggan-scan/jeonggan-reader-*/stderr.txt`: 실제 스캔 실패. 기본 서버 이미지의 설정 누락은 `final-probe/`이며 로컬 모델 실패와 구분한다.
- `setup-noexec-failure/`: 최초 tmpfs noexec 설정 때문에 네이티브 라이브러리 로딩이 실패한 준비 오류. 실행 가능한 tmpfs로 고친 뒤 실제 인식을 수행했으며 OMR 품질 실패에 합산하지 않는다.
- FE·BE의 `build/test-results/`와 `frontend-run.log`, `backend-run.log`: 실행 결과. BE 기존 저장/주기/공통 악보 회귀도 별도 실행했다.880의96·6·189개 과거 결과를 이번 수치로 재인용하지 않는다.

남은 검증: 실제 변박 PDF의 음별 전체 정답 검수, 나머지 곡 전체 정답 검수, MySQL·앱 UI·기기 시간축, 제안 기능의 팀 합의와 구현 후 재검증. 현재 결과는 성공·실패·미검증을 나눈 소규모 재현 기준이다.
