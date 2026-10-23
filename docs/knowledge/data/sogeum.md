# 소금 라벨·연주 자료

작업: [S15P31E102-87](https://ssafy.atlassian.net/browse/S15P31E102-87). 원문·원음 확인일 2026-10-23. 국립국악원 단음 목록의 소금 전체 15개를 확보하고, 기본음계·시김새 14파일을 146개 구간으로 나눴다. 모든 율명은 `inferred`, `eligibleForNoteScoring=false`다. 특히 기본음의 여섯째 율명은 대안이 남아 있어 채점 정답으로 사용하지 않는다.

## 파일과 수량

- [sources.json](../../../data/catalog/sogeum/sources.json): 공식 ID, 원본 경로·SHA-256·출처·이용 조건과 라벨 경로.
- [원음별 labels.json](../../../data/annotations/audio/sogeum/): 구간·취법·율명·대표 F0, 경계 수정과 음렬 판단 근거.
- [performances.json](../../../data/catalog/sogeum/performances.json): 도드리 원음. 대응 판본을 확인한 악보와 비교용 악보는 현재 없다.

| 공식 ID | 공식 주법명 | 구간 수 |
| --- | --- | ---: |
| 2654 / 2655 / 2656 | 기본음 강 / 중 / 약 | 16 / 16 / 16 |
| 2657 / 2658 / 2659 | 요성 강 / 중 / 약 | 14 / 14 / 15 |
| 2660 | 깊은요성 | 2 |
| 2661 / 2662 / 2663 | 스타카토 강 / 중 / 약 | 13 / 14 / 14 |
| 2664 | 내리떠는농음 | 2 |
| 2665 | 나니르 | 2 |
| 2666 | 노니로 | 6 |
| 2667 | 노로니르 | 2 |
| 2668 | 도드리 | 연주 원음 1개 |

기본음계 93개·시김새 53개다. 원본 15개는 총 **148,203,342 bytes·834.091973초**, 모두 44,100Hz·2채널·PCM16 WAV다. 사이트 미리듣기 파일명에 MP3가 섞여 있지만 이번 목록은 실제 다운로드한 WAV 바이트와 디코딩 결과를 기록한다.

## 취법·율명 연결

[국립국악원 소금 교재](https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=138&menuid=001003003006002&boardtext1=%EC%86%8C%EA%B8%88)의 PDF 5쪽(인쇄 61쪽) 운지·음역표와 [국악사전 소금](https://www.gugak.go.kr/ency/topic/view/109), [취법](https://www.gugak.go.kr/ency/topic/view/1349)을 대조했다. 표의 향악 율명 배열과 평취·역취 구분을 사용한다. 교재 뒤쪽의 저취·평취·역취 용례와 사전 본문의 일부 옥타브 숫자는 일관되지 않아, 단독 문구로 취법을 세 등분하지 않았다.

평취는 임종 B♭4부터 청중려 A♭5 부근, 역취는 청임종 B♭5 이상의 범위다. 라벨에는 기존 악기와 조회할 수 있게 기본 율명 `임·남·무·황·태·협·고·중`을 쓴다. 다만 역취 안에서 한 옥타브 더 높은 마지막 임은 **`중청임`**으로 구분한다. `황`은 평취에서 청황종 부근, 역취에서 중청황종 부근을 뜻하며, 실제 높이는 `labelF0Hz`로 보존한다. 이것은 주파수 보정이나 운지 확인이 아니다.

기본음 강(2654)의 순서와 대표 F0는 다음과 같다. 다른 강세에도 같은 수의 발음이 있지만 주파수·경계는 각각 측정했다.

| 순서 | 취법·율명 가설 | 구간(초) | F0(Hz) |
| ---: | --- | --- | ---: |
| 1 | 평취 임 | 0.12–5.72 | 484.590 |
| 2 | 평취 남 | 6.95–12.89 | 539.459 |
| 3 | 평취 무 | 13.98–20.11 | 581.484 |
| 4 | 평취 황 | 22.00–27.43 | 644.320 |
| 5 | 평취 태 | 29.14–34.66 | 726.044 |
| 6 | 평취 협 — 고 반복 대안 있음 | 36.05–40.89 | 800.153 |
| 7 | 평취 고 | 42.04–47.05 | 823.386 |
| 8 | 평취 중 | 47.93–53.19 | 864.202 |
| 9 | 역취 임 | 54.04–59.09 | 976.054 |
| 10 | 역취 남 | 59.93–65.15 | 1085.549 |
| 11 | 역취 무 | 66.00–71.28 | 1141.983 |
| 12 | 역취 황 | 71.96–76.95 | 1286.449 |
| 13 | 역취 태 | 77.94–82.72 | 1438.998 |
| 14 | 역취 고 | 83.91–88.13 | 1627.533 |
| 15 | 역취 중 | 88.85–93.59 | 1775.711 |
| 16 | 역취 중청임 | 94.88–98.78 | 1942.115 |

단순 최근접 12평균율 분류를 쓰지 않았다. 예를 들어 823Hz를 무조건 중으로 옮기면 뒤의 864Hz, 요성의 고–중 짝, 상옥타브 고–중 배열과 어긋난다. 기본음 16개를 소금의 모든 운지·음역 개수로 해석하지 않는다.

**남은 음악적 쟁점은 기본음 세 파일의 6번이다.** 교재의 태–협–고–중 배열에 따라 협으로 추정했지만 강·중의 6–7번 간격은 약 49.55cent, 약에서는 약 66.78cent에 불과하다. 낮게 낸 고 또는 다른 운지의 고를 다시 분 가능성이 있다. FFT 피크도 800.093·823.813·863.683Hz로 YIN과 일치하므로 추정기의 단순 옥타브 오류는 아니다. `evidence`에 고 대안과 수치를 남겼다. 녹음의 율명표·운지 영상·전문 연주자 검수 중 하나가 있어야 확정할 수 있다.

## 분할과 예외 점검

원래 샘플레이트에서 채널을 평균하고 YIN CMND로 분석했다. 40ms 창·10ms 간격, 350–6500Hz, CMND 문턱 0.15, 대표값은 주기성 0.85 초과 프레임을 사용했다. RMS 문턱은 `max(0.0001, p95 × 0.025)`, 0.15초 이하 끊김을 연결하고 0.08초 미만 후보는 제외했다. 분석창은 경계 위치에 최대 약 40ms 영향을 줄 수 있으며, 저장 소수점 수가 그만큼의 음악적 정확도를 뜻하지 않는다. 원본 프레임은 시작 포함·끝 제외, `round(초 × 샘플레이트)`다.

- **요성 약 2659의 1·6번 끝:** 낮아진 RMS 아래에서도 같은 음의 주기음이 이어졌다. 최초 끝 6.39·44.40초를 6.60·44.64초까지 넓혀 무음 직전 감쇠 꼬리를 포함했다. 40ms 창을 이동하며 확인한 6.52초 F0 482.8Hz/주기성 0.96, 44.54초 825.77Hz/0.904가 근거다. 별도 발음으로 추가하지 않았다.
- **깊은요성·내리떠는농음 각 2개, 노니로 첫 2개:** 사이 무음과 새로운 어택을 가진 실제 반복이다. 동일 율명이더라도 별도 구간으로 유지했다.
- **나니르·노니로·노로니르:** 장식 동작 전체를 한 발음 구간에 담고 뒤의 안정음으로 라벨을 붙였다. 장식의 고음 또는 전체 중앙값을 본음으로 삼지 않는다. 내리떠는농음은 농음 전 안정창을 쓴다. 선택한 창과 측정 프레임 수는 `sequenceDecision`에 보존한다. 12개 안정창은 취법·율명·F0·절대초 창을 가진 `orderedAnchors` 한 원소와 `labelAnchorIndex=0`으로 표현한다. 선택창의 의미와 기존 예외 판단은 그대로 유지했다.
- **주법별 빠진 음:** 요성 강에는 역취 남, 요성 중에는 역취 태가 없고, 스타카토 세 파일에는 역취 태가 없다. 스타카토 강은 마지막 중청임도 없다. 전체 F0 궤적에서 각 후보(1085·1431·1934Hz)의 ±4%·주기성 0.9 초과 프레임은 모두 0개였다. 유음 내부에 붙은 다른 음도 p10–p90와 궤적으로 점검했다. 다른 파일에서 음을 복사해 보충하지 않았다.
- **낮은 음량의 누락 검사:** 모든 구간 밖을 기존 문턱의 1/10로 다시 검사했다. 앞의 두 감쇠 꼬리 보완 후 14파일 모두에서 경계 40ms 이격 밖에 80ms 이상 이어지는 미포함 주기음은 없었다. 이는 설정 범위의 음향 검사 결과이며 청음·운지 검수 완료를 뜻하지 않는다.

기본음 강·중은 앞 101.8초의 상관계수가 **0.99999999495**, 중/강 회귀 이득이 **0.749892558**, 잔차 RMS가 **0.000010964**였다. 거의 같은 테이크를 이득·끝 패딩만 바꾼 자료로 볼 근거가 있으므로 독립 녹음으로 세지 않는다. 공식 원본 ID·강세별 자료는 그대로 보존하되 같은 악기·같은 음렬의 강세 변형을 학습/평가로 나누지 않는다. 요성·스타카토도 강세별 파일에 유사한 테이크가 있어 전체 소금 묶음을 같은 원천군으로 취급한다.

## 도드리와 이용 조건

2668은 61.946667초 원음이며 변환 없이 복사한다. 공식 목록에는 ‘도드리’만 있고 세부 곡명·연주자·판본·편집 범위가 없다. 소금 교재와 국립국악원 악보 목록을 확인했으나 이 녹음에 대응하는 소금 오선보·정간보를 확보하지 못했다. [대금 정악보](https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=17409&boardtypeid=88&menuid=001003002002&pagesize=10)의 밑도드리는 다른 악기 자료여서 현재 묶음에 넣지 않았다. `scores=[]`, `comparisonScores=[]`, `noteAlignmentVerified=false`를 유지한다.

[디지털 음원 목록](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의 관악기 → 소금(`instrCd=19991`) 전체 15개와 공공누리 제1유형 표시를 확인했다. 출처를 표시하고 가공 내용을 알리는 조건으로 원음·잘라 낸 음원을 사용할 수 있다. 다운로드 목적은 비상업 연구용·소속 SSAFY로 기록했다. 교재 PDF와 사전 도판은 음역 확인에만 사용했고 배포용 sources에는 넣지 않았다. 사전 도판의 공공누리 제4유형을 원음의 제1유형과 혼동하지 않는다.

## 재현과 검증

원음은 `data/raw/instruments/sogeum/`, 상세 분석은 `data/processed/instruments/sogeum/research-v1/`에 있으며 모두 Git 제외다. 분석 코드의 YIN 함수는 [c163f2f의 analyze_basic_tones.py](https://lab.ssafy.com/s15-final/S15P31E102/-/blob/c163f2f4b7d09201f173e55dfb374a33499397be/data/src/analyze_basic_tones.py)에서 가져왔고 소금의 주파수 범위·원래 샘플레이트·구간 조건을 별도 적용했다. 산조 율명 배열은 재사용하지 않았다.

| 로컬 분석 파일 | SHA-256 |
| --- | --- |
| `label_sogeum.py` | `9987bcef6e5c6e7e0ed8e2ae1e24153803021c9b41303deb88666425cd1015f6` |
| `analyze_sogeum.py` | `94724492230731410857837aaa482f5eaa17aaf89d521f4e7c4a8188b06244dd` |
| `analyze_basic_tones_c163f2f.py` | `899f8b3831dab54bf6a8c62303a1b29940c77106f659db5c37c6281aa92620b4` |

라벨의 method는 실제 작성 스크립트 해시를 줄인 `tool:label_sogeum@sha256-9987bcef6e5c6e7e`다. 명시적 배열 선택과 예외 판단을 코드에 함께 남겼다. 현재 export는 이 로컬 연구 코드에 의존하지 않으며, 추적되는 JSON과 원음으로 동일 구간을 다시 만든다. 분석 자체를 다시 실행하려면 해시가 같은 연구 코드도 별도로 보존해야 한다.

Python 3.12.14·NumPy 2.3.5·SoundFile 0.13.1에서 확인했다. 최초 2654를 먼저 다운로드한 뒤 나머지 수집·15파일 메타데이터 확인은 61.990초, 14파일 분석 7.282초, 최초 경계 감사 0.626초였다. 최종 메타데이터로 `prepared-v3/` 전체 내보내기 1.948초, 선택 5건과 전수 대조를 포함한 검증은 2.801초였다. 이번 측정은 새 출력 폴더에 모두 다시 생성한 시간이다.

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/sogeum --output-dir data/processed/instruments/sogeum/new-full
python data/src/audio/export_dataset.py --dataset data/catalog/sogeum --technique '기본음 강' --register 평취 --yulmyeong 황 --output-dir data/processed/instruments/sogeum/new-hwang
python data/src/audio/export_dataset.py --dataset data/catalog/sogeum --label ngc-sogeum-2666.group-002 --output-dir data/processed/instruments/sogeum/new-repeat
python data/src/audio/export_dataset.py --dataset data/catalog/sogeum --performance ngc-sogeum-2668 --output-dir data/processed/instruments/sogeum/new-dodeuri
```

전체 146개와 조건 선택 황·두 번째 노니로 임·보완한 요성 꼬리·도드리·평취 임 노니로 2개 선택까지 5건을 확인했다. 원본 15개 SHA/형식/길이, 출력 146개 SHA/프레임 수, 구간 범위·중복·겹침·경로 충돌, 선택/전체 값·파일 바이트, 도드리 원본 바이트 모두 일치했다. Windows 디렉터리 rename 접근 거부가 선택 황·도드리에서 각 1회 있었으며, 완성 출력이 없는 것을 확인한 뒤 해당 단계만 다시 실행해 성공했다. 이번 최종 재생성은 접근 거부 없이 성공했다. 소금 브랜치의 공통 exporter 변경은 없다. `orderedAnchors` 검증은 단소 작업의 exporter SHA-256 `28d5bec57bc65cde0b37aec46ad8d94e56c3ed60e86a318092893d6969efd0cf`를 외부 경로에서 읽어 수행했으며, 해당 공통 코드 변경은 단소 MR에서 관리한다. 이전 결과와 146행의 ID·구간·취법·율명·F0, 출력 경로·WAV 바이트가 같고 12개 안정창 표현과 방법 해시만 바뀌었음을 대조했다.

측정·감사 기록은 같은 연구 폴더의 `analysis-<ID>.json`, `frames-<ID>.npz`, `label-summary.json`, `missing-tone-audit.json`, `boundary-audit-final.json`, `validation.json`, `validation-ordered-anchors.json`이다. 최종 검증 코드는 `verify_ordered_anchors.py`, 변경 전 비교본은 `before-ordered-anchors.json`에 보존했다. 상위 작업의 기존 공통 테스트 39개와 단소 작업의 선택창 검증 추가 후 43개 통과 보고를 확인했고, 이 작업은 실제 소금 입력 검증을 추가했다. 독립 연주자에 의한 율명·취법 검수와 도드리 대응 악보 확보는 남아 있다.

## 출처 공통 항목 축약

단소 MR !30에서 병합된 공통 출처 로더를 `a981ef24`에서 가져와 통합 커밋 `487379dd`로 반영했다. 이어 소금 sources 입력만 v3로 바꿨다. 원음 15개의 값이 모두 같은 `channels`, `kind`, `sourceUrl`, `publisher`, `license`, `verifiedOn`, `redistribution`을 `ngc-sogeum-audio` 프로필에 한 번 기록한다. 원본별 `downloadUrl`·`attribution`과 ID·제목·경로·해시·크기·샘플레이트·길이는 개별 항목에 그대로 둔다. 새로운 출처 값이나 측정값을 추가하지 않았다.

파일을 UTF-8·LF로 통일해 도구로 세면 축약 전 **397줄·16,986 bytes**, 후 **220줄·11,117 bytes**다. 공통 로더가 펼친 15개 원본 객체와 최상위 메타데이터는 입력 `schemaVersion`을 제외하면 이전 v2와 정확히 같다. annotations 14파일과 performances는 바이트 단위로 그대로다.

통합 후 공통 회귀 테스트 51개가 통과했다(5.596초). 같은 로더·exporter로 기존 v2 전체와 v3 전체를 실제 CLI로 다시 생성했다. 기본음계·시김새 146개와 도드리 원음 1개, 총 **147개 파일의 경로와 바이트가 모두 같다**. 결과 catalog도 `manifestHashes["catalog/sogeum/sources.json"]`만 달라졌고 나머지 모든 값은 같았다. 기본음 강·평취 황(`ngc-sogeum-2654.group-004`) 선택 1개도 전체 출력의 라벨·파일 바이트와 같았다. 원본 검증 CLI는 크기·SHA-256 **15/15 일치**로 끝났다.

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/sogeum --output-dir data/processed/instruments/sogeum/source-profiles-after-v3
python data/src/audio/export_dataset.py --dataset data/catalog/sogeum --output-dir data/processed/instruments/sogeum/source-profiles-selected-v3 --technique '기본음 강' --register 평취 --yulmyeong 황
python data/src/catalog/verify_sources.py --dataset sogeum
```

검증 당시 CLI 실행의 도구 경과 시간은 기존 v2 전체 2.310초, v3 전체 2.195초, 선택 0.506초, 원본 대조 0.354초였다. 기존 v2 전체의 첫 두 시도는 Windows 폴더 rename 접근 거부로 실패했고 완성 출력이 없음을 확인했다. 같은 경로에서 CLI를 직접 실행한 세 번째 시도와 이후 작업은 성공했다. 잠금 원인은 확인하지 않았으며 코드·원본을 바꾸지 않았다. 성공 출력의 가장 긴 절대 파일 경로는 140자다.

산조대금·AI-Hub 71470·AI-Hub 71955·수집 악보의 기존 v2 목록 4개도 같은 로더로 읽은 객체가 원문 JSON과 같았다. 이 목록들은 변환하지 않았다. 입력 버전별 형식과 우선 적용 규칙은 [공통 README](../../../data/README.md#json-형식과-악기-추가)를 따른다.

변경 전 원문과 검증 결과는 Git 제외 경로 `data/processed/instruments/sogeum/research-v1/source-profiles-v1/`의 `sources-before-v2.json`, `unchanged-input-hashes.json`, `validation.json`, CLI 실행 기록에 보존했다. `compact_sources.py`가 변환·동등성 검증 코드다. 실제 전후 출력은 `data/processed/instruments/sogeum/source-profiles-before-v2/`·`source-profiles-after-v3/`에 있다. 기존 율명 가설과 검수 범위는 이번 출처 축약으로 달라지지 않는다.
