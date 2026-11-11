# 악보 장단의 선택·저장·재생 일치

2026-11-11, S15P31E102-772. 기준 develop 929641c7. [계획](../../plans/S15P31E102-772.md), [저장 계약](../../specs/user-sheets.md), [카탈로그](../../specs/catalog-read-apis.md).

## 원인과 선택

ID12는 기존 FE 명칭 목록과 OMR `PATTERN_CELL_QUARTERS`에 없어 선택 불가·KeyError를 재현했다. 내 악보 연습은 QUARTER 장단의 각 패턴 칸을 4분음표로 계산해 단모리 주기가 두 배, 세령산 주기가 짧아졌다. FE는 마지막 각이 짧아도 허용했고, 저장 서버는 ID 이름·BPM 단위만 비교했다. 경고 없이 가져온 악보는 장단 검토를 건너뛰었다.

기존 DATA 참조12행에 `pattern_cell_eighths`를 추가했다. 음악적 값은 기존 출처의 8분·4분·점4분음표 설명을 정수로 옮긴 것이다. BE가 원본 JSON을 번들하고 DB 이름·단위·패턴 일치를 확인해 API에 `patternCellEighths`로 제공한다. DB 마이그레이션이나 화면별 음가 목록이 필요 없다. DATA CLI·OMR도 같은 참조를 읽는다. ID7과12는 같은4박 길이여도 다른 구음이다.

새 확정 저장은 첫 쉼을 금지하지 않고0tick 원점·모든 각의 완전한 주기를 검사한다. 마지막 각만 예외로 두지 않는다. 이름만 저장된 악보는 장단을 다시 선택하거나 해제해야 한다. 임시 초안과 기존 판본을 지우지 않는다. 적용·해제는 음표와 tick을 유지하며, 상세 시간 안내는 설정 창에서 펼쳐 본다.

## 재현과 실행 근거

- FE ID12 4정간/QUARTER fixture를 먼저 실행해 기존4검사 중1실패를 확인한 뒤 공통 필드를 연결했다. `SheetJangdanCompatibilityTest`는 단모리·세령산·ID12·단위·누락 음가·완전한 마지막 각을 구분한다. ViewModel 회귀는 명시적 변경/해제·undo/reload의 음표 보존을 검사한다.
- OMR 기존 `beats_per_jeonggan('4박 기본 반주',4)`는 KeyError. 수정 후1박이며 의존성 없는 `test_jangdan_timing.py`2검사 통과. 기존 PDF 엔진 전체 테스트는 현재 Python 환경의 pymupdf 부재로 수집되지 않아 전체 OMR 검증으로 보고하지 않는다.
- DATA `test_jangdan_audio.py`2검사와 `test_reference_data.py`9검사 통과. 이전 참조 검사는10종만 가정해11·12종에서 실패했고 이번 범위의12종과 필드를 반영했다. 합성 파형·실물 연주 검수와 구분한다.
- FE `:shared:testAndroidHostTest :androidApp:assembleDebug` 전체1421검사와 Android debug 빌드 통과. 마지막 안내 코드 변경 후 장단10검사·빌드도 통과했다. Windows에서 iOS simulator는 실행할 수 없으며 adb 연결 기기는 없다.
- BE `clean build`727검사(기존 스냅샷 선택 검사1건 제외) 통과. DB4클래스79검사도 통과했으며 실제 CAT URL 미설정2건은 제외됐다. `databaseTest`는 폐기 가능한 MySQL8.4의 별도 eolssu_test772에 실행했다. 실제 HTTP 저장 fixture `backend/src/test/resources/jangdan-playback/authoring-772.json`은8박, 장단0박·선율1.5박 시작이다. ID12 저장200, 이름만 있는 요청400, 마지막3박 각400, 장단 해제 저장200, 저장 전후 events/columns 동일을 검증했다. 카탈로그 실제 HTTP 목록/상세가 원본 칸 길이를 반환하는지도 검사한다.
- 소스 Docker 빌드는 저장소 루트 context와 `backend/Dockerfile.dockerignore` allowlist로 변경했다. `docker build --file backend/Dockerfile --target build --tag eolssu-backend-build:772 .` 통과, 실행 JAR 내부 `BOOT-INF/classes/catalog/jangdans.json` 확인. Jenkins는 이미 저장소 전체 checkout 뒤 Gradle JAR를 이미지에 넣으므로 동일 원본을 포함한다. 배포/운영 DB 변경은 수행하지 않았다.

## 경계

수행 빠르기 변화·실제 CAT 생성의 통합 검증은773과 함께 확인한다. 이 작업의 타점 계획/HTTP 테스트는 실물 악기 연주 검수나 기기 청취 완료가 아니다. 기존 게시 곡용 검수 기반 재생 resolver와 이미 게시된 악보·음원 판본은 수정하지 않는다.
