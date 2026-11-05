# 곡별 장단 반주 검수 자료 전달

S15P31E102-160, 2026-11-04. 공개 악보 107개의 장단 연결 근거와 보류 사유를 DATA 검수 결과로 전달한다. 현재 업무 경계는 음원과 검수 manifest 준비가 DATA160, 결과를 소비하는 판본 보호·API 제공과 검증이 BE491, 앱 재생 구현과 검증이 FE340이다. 반영 순서는 160 → 491 → 340이며 운영 DB·서버는 변경하지 않는다.

## 결과와 보존 범위

[운영 공개 곡 목록](https://k15e102.p.ssafy.io/api/songs)의107개와 각각 `/api/songs/{id}/score`를 다시 읽었다. 처음 조사 뒤 곡25·44가 악보2판으로 변경됐음을 확인해 현행 판본으로 검수했다. 전체 결과와 조회 시각은 [검수 manifest](../../../backend/src/main/resources/catalog/jangdan-playback.json)의 `scores`와 `queriedAtUtc`에 있다. 제목 중복도 별도 판본으로 유지한다.

- 60개는 교육용 기본형과 악보 시간·배치의 대응을 확인해 `READY`로 기록했다.
- 45개는 원본 장단명 미상으로 `SOURCE_JANGDAN_UNSPECIFIED`, 세령산2개는 굴림 주법 미검수로 `ROLL_ARTICULATION_UNREVIEWED`다.
- `READY`는 반주 연결에 대한 구조 검수이며 원본 악보의 `provenance.review` 또는 음악적 청음 승인을 바꾸지 않는다.
- 곡의 기존 장단 FK·BPM·score JSON·모범 음원은 모두 보존한다. 특히 QUARTER 악보를 DOTTED_QUARTER 장단 FK와 직접 연결하면 기존 음원 단위 검증에 충돌할 수 있어 FK를 갱신하지 않는다.

| 기본형 | 검수 판본 수 | 한 주기의 score tick |
| --- | ---: | --- |
| 굿거리 | 18 | `6 × ticksPerBeat` |
| 자진모리 | 4 | `6 × ticksPerBeat` |
| 중중모리 | 3 | `6 × ticksPerBeat` |
| 세마치 | 20 | `3 × ticksPerJeonggan` |
| 중모리 | 4 | `12 × ticksPerJeonggan` |
| 타령 | 6 | `12 × ticksPerJeonggan` |
| 계면가락도드리 | 3 | `12 × ticksPerJeonggan` |
| 엇모리 | 1 | `10 × ticksPerJeonggan` |
| 단모리 | 1 | `4 × ticksPerJeonggan` |

## 구조 검수의 근거와 한계

원본 악보의 명시 장단명, 판본 식별자와 원문 JGB 해시, 정간 tick 비율, 모든 열의 칸 수와 전체 이벤트의 시간 범위를 함께 확인했다. 60개는 첫 이벤트가 첫 열·첫 정간의 NOTE이고 startTick0이며, 각 열의 길이가 위 기본형의 완전한 주기 또는 정수배다. 교육용 반주는 그 첫 정간에 기본형 첫박을 맞춘다. 장단 이름만으로 runtime에서 매칭하거나 미지정곡에 기본 장단을 대입하지 않는다.

굿거리18개는 12소박/각,4큰박/각,8큰박/각 형태다. 모두4분음표99이고 한 주기6박=3636.364ms이며8큰박형은 한 열에 두 주기를 담는다. 카탈로그12칸을 한 정간과 항상1:1로 대응시키면4큰박형에서3배 오차가 나므로 score tick 주기를 명시한다.

중모리 곡19는 BPM88·ticksPerBeat4·ticksPerJeonggan4라48tick이 한 주기다. 곡81은 BPM44·ticksPerBeat4·ticksPerJeonggan2라24tick이며 둘 다8181.818ms다. 세마치 곡9와29도 각각 BPM144/ticksPerBeat2와 BPM48/ticksPerBeat6이지만 cycleTicks9로1875ms가 같다. 도드리·타령의12칸과 엇모리10칸, 단모리4큰박도 각 패턴의 칸·그룹과 함께 대조했다. 카탈로그 구음·음가의 출처는 [장단 카탈로그 확장 근거](../backend/jangdan-catalog-extension.md)를 재사용했다.

이 검수는 현재 등록된 선율에 일정한 교육용 기본형을 붙이는 연결 확인이다. 원전 연주자의 변주·장식 타격·실제 녹음의 미세한 박 변화·청음 품질까지 검증한 것은 아니다. 향후 못갖춘마디나 장단 전환이 있는 판본은 별도 시작점·구간 검수가 필요하며, 현재 manifest와 다른 악보에는 이 연결을 재사용하지 않는다.

## 단소 미연결 판본 재조사 (2026-11-05)

[S15P31E102-503](https://ssafy.atlassian.net/browse/S15P31E102-503). 공개 목록과 단소 악보 상세 46개를 조회했다. 상세의 `jangdanPlayback`은 33개에 있고 13개에서 비어 있다. 목록의 `jangdan`은 몽금포타령(19)에만 있다. 아래 표는 실제 반주를 기준으로 삼는다.

원본 PDF 사본 10개를 렌더링해 제목 옆 장단 표기, 정간 수, 오선보 박자와 설명을 확인했다. 단소1·2와 연습곡 1·2는 각각 같은 PDF에 있다. 국립국악원 실기교재와 표준악보집에서 가져온 악보는 [원본 목록](../../../data/catalog/collected-scores/sources.json)의 식별자 또는 발췌 번호로 찾을 수 있다. 표준악보집 쪽수는 PDF 뷰어 번호와 인쇄 쪽수를 함께 적었다.

| 곡 ID · 판본 | 원본에서 확인한 내용 | 반주 판단 |
| --- | --- | --- |
| 34 v1 이거리 저거리 각거리 | 실기교재 18868. 장단명 표기 없이 8정간씩 두 각으로 적혀 있다. | **자진모리 우선 후보.** 국립국악원 단소 교재가 3분박으로 가르치고 미래엔 교재가 자진모리를 명시한다. 현재 각마다 두 주기가 들어간다. [국립국악원 단소 교재][danso503-dari] · [미래엔 악곡 해설][danso503-miraen] |
| 7 v1 경복궁타령 | 실기교재 18796. 우측 첫 열에 **자진타령장단**과 구음이 있고, 선율 각은 4정간이다. | **자진타령을 별도로 지원할 때 연결.** 현행 공개 카탈로그 10종에 자진타령이 없다. 교과서에 따라 자진모리 표기도 있으나 이 단소 판본과 국립국악원 실기 해설은 자진타령으로 일치한다. [국립국악원 단소 교재][danso503-gyeong] · [국악사전 자진타령][danso503-jajin] · [교과서 비교 연구][danso503-kci] |
| 28 v1 싸름 | 실기교재 단소 정간보. 장단명 없이 8정간씩 두 각이다. 국립국악원 가창용 악보는 같은 가사와 대응하는 구절을 6/8로 적고, 해설은 3분박 4박자로 설명한다. | **굿거리 청음 후보.** 한국민족문화대백과사전의 굿거리 설명은 현행 정간과 구조상 맞는다. 국악사전에는 중모리·세마치 전승도 있다. 두 이름을 현행 계산에 넣으면 주기가 각각 72·18틱이라 48틱 각을 나누지 못한다. 굿거리 첫박과 장구 가락이 이 판본의 가사와 맞는지는 청음으로 확인할 사항이다. [국립국악원 가창 교재][danso503-ssareum-vocal] · [한국민족문화대백과사전][danso503-ssareum-aks] · [국악사전 싸름][danso503-ssareum-ngc] |
| 10·11 v1 단소 연습곡 1·2 | 교과서 발췌 PDF 14. 기초 율명과 지속·쉼을 연습하는 각 8정간 악보다. 장단명 표기가 없다. | 박자 클릭으로 연습. 전통 장단을 연결할 판본 근거를 찾지 못했다. |
| 21 v1 바람개비 | 실기교재 18730. 장단명 없이 8정간씩 묶었다. | 국립국악원은 **2분박 리듬 연습**으로 설명한다. 공개 데이터의 8틱/4분음표, 12틱/정간을 근거로 3소박 장단을 붙이면 교재 해석과 충돌한다. 장단 연결 보류. [국립국악원 단소 교재][danso503-baram] |
| 25 v2·26 v1 북천이 맑다커늘 단소1·2 | 표준악보집 PDF 182, 인쇄 362–363쪽. 오선보에 **4/4**, 설명에는 임제의 시에 김기수가 곡을 붙인 고가신조로 표기한다. | 4/4 박자 클릭이 악보와 맞는다. 일반 시조창에서 5박과 8박을 교차하는 장단은 이 편곡에 이식할 근거가 없다. [국립국악원 표준악보집][danso503-standard] · [국악사전 시조][danso503-sijo] |
| 27 v1 새야새야 | 표준악보집 PDF 161, 인쇄 320–321쪽. 장단형이 비어 있고 정간보는 각마다 5정간이다. | 5정간 각에 대응하는 별도 클릭 검토. 정간 수만으로 3·2·3·2의 엇모리 강세나 특정 장단을 확인할 수 없다. [국립국악원 표준악보집][danso503-standard] |
| 30·31 v1 어이 얼어자리 단소2·1 | 표준악보집 PDF 183, 인쇄 364–365쪽. 김기수의 고가신조이고 오선보에 **6/4**가 적혀 있다. | 6/4 박자 클릭 후보. 굿거리·자진모리 같은 장단명 표기는 없다. 공개 악보는 정간당 1.5사분음표로 해석해 각 길이가 9사분음표이므로, 원본대로 클릭을 제공하려면 박 단위부터 확인한다. [국립국악원 표준악보집][danso503-standard] |
| 48 v1 참새노래 | 실기교재 18924. 장단명 없이 8정간씩 묶었다. | 국립국악원이 **2분박 리듬 연습**으로 설명한다. 바람개비와 같은 단위 차이가 있어 장단 연결을 보류한다. [국립국악원 단소 교재][danso503-chamsae] |
| 50 v1 정선아리랑 | 실기교재 18882. 장단명 없이 12정간씩 적고, 후렴 뒤에 촘촘한 사설이 온다. | 후렴·사설 구간을 먼저 대조한다. 국립국악원 통속민요 자료는 향토 엮음아라리와 장단이 다르다고 설명한다. 아카이브는 무장단 구간과 세마치 후렴이 있는 연주를 소개하며, 국악사전의 비교 악보에는 6/8 후렴과 박자·빠르기 전환이 있다. 전곡 고정 장단은 보류한다. [국립국악원 단소 교재][danso503-jeongseon-lesson] · [국악사전 정선아리랑][danso503-jeongseon] · [국악아카이브 공연 해설][danso503-jeongseon-video] |

공개 `/api/jangdans/{id}`의 구음 배열과 악보 상세 응답을 현행 [주기 계산 코드](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/song/infrastructure/catalog/ScoreGridJangdanPlayback.kt)의 조건으로 대조했다. 34번 자진모리(ID 4)와 28번 굿거리(ID 2)는 모두 첫 음이 `c0-j0`의 0틱이고, 12소박 패턴이 각마다 정확히 두 번 들어간다. 한 주기는 각각 12틱과 24틱이다. 둘 다 현재 악보의 4분음표 90 BPM에서 주기 4초로, 큰박으로 환산하면 60 BPM이다. 34번은 이 속도에서 자진모리 강세를 듣고 확인할 수 있다. 28번은 자료마다 장단이 달라 굿거리로 고정하기 전에 실제 반주를 맞춰 들어야 한다. 7번 자진타령은 4정간 × 3틱 = 12틱이 원본 3소박 4박과 맞으므로, 새 구음과 재생 지원을 검수할 때 이 주기로 시작할 수 있다.

이 결과는 원본 대조와 구조 검사다. 조사 시점의 13개에는 공개 반주가 없고, 반주 청음과 적용 검증은 남아 있다. 원본 PDF와 렌더 이미지는 로컬에서 읽었다. 공공누리 제4유형으로 표시된 원본은 출처 표시·비상업·무변경 조건에서 재배포 가능하며 가공본 재배포는 현재 불가다. 발행처와 이용 조건이 확인되지 않은 연습곡 발췌본의 원본·가공본 재배포는 현재 불가(허가 미확인)로 본다.

후속 구현은 목록에서 공통 악보의 장단명을 읽고, 7번 자진타령과 34번 자진모리를 원본 식별자가 일치하는 1판에 보완한다. 34번은 별도 반주 필드로 12틱 주기를 제공한다. 7번은 자진타령 패턴 등록 전까지 이름만 표시한다. 싸름은 청음 대기 상태다. 표시·반주 보완값은 조회 시 계산하므로 공통 악보, BPM, 음원 버전과 해시를 유지한다. [응답 계약](../../specs/catalog-read-apis.md#현재-악보에-맞춘-장단-반주-s15p31e102-491)

[danso503-dari]: https://academy.gugak.go.kr/dp/pds/HTML5-2-2-4/HTML5-2-2-4-2/HTML5-2-2-4-2-5/index.htm
[danso503-miraen]: https://ele2.m-teacher.co.kr/files/contents/5c08e43f0527a/contents/m04_01/m04_01_08/m04_01_08_05.html
[danso503-gyeong]: https://academy.gugak.go.kr/dp/pds/HTML5-2-2-3/HTML5-2-2-3-2/index.htm
[danso503-jajin]: https://www.gugak.go.kr/ency/topic/view/1326
[danso503-kci]: https://www.kci.go.kr/kciportal/ci/sereArticleSearch/ciSereArtiView.kci?sereArticleSearchBean.artiId=ART003190406
[danso503-ssareum-vocal]: https://academy.gugak.go.kr/dp/pds/HTML5-2-3-4/HTML5-2-3-4-2/HTML5-2-3-4-2-4/index.htm
[danso503-ssareum-aks]: https://encykorea.aks.ac.kr/Article/E0072817
[danso503-ssareum-ngc]: https://www.gugak.go.kr/ency/topic/view/857
[danso503-baram]: https://academy.gugak.go.kr/dp/pds/HTML5-2-2-4/HTML5-2-2-4-2/HTML5-2-2-4-2-2/index.htm
[danso503-standard]: https://www.gugak.go.kr/userfiles/files/textbook_final.pdf
[danso503-sijo]: https://www.gugak.go.kr/ency/topic/view/306
[danso503-chamsae]: https://academy.gugak.go.kr/dp/pds/HTML5-2-2-4/HTML5-2-2-4-2/HTML5-2-2-4-2-6/index.htm
[danso503-jeongseon-lesson]: https://academy.gugak.go.kr/dp/pds/HTML5-2-2-3/HTML5-2-2-3-6/index.htm
[danso503-jeongseon]: https://www.gugak.go.kr/ency/topic/view/874
[danso503-jeongseon-video]: https://archive.gugak.go.kr/portal/detail/searchVideoDetail?clipid=33202&recording_type_code=V&system_id=AV

## 160·340 분리 검증

분리 기준은 develop `a680a0ae`다. 기존 검수본 `fe9163c4204fe4d0e39dd6c061bc0161b96477eb`에서 DATA 전달물만 가져왔으며 이전 구현 브랜치의 이력을 병합하지 않았다. MP3 10개·WAV 11개와 런타임 JSON 2개(곡별 검수·연습 자산 manifest)는 원본 Git blob과 바이트가 같다. 검수 목록은 고유 songId 107개, READY 60개·장단명 미상45개·굴림 미검수2개를 유지한다.

각 생성 manifest의 MP3 10개·WAV 11개 해시가 실제 파일과 일치했다. WAV의 44.1kHz·모노·PCM16·프레임 수도 생성 명세와 같았다. 연습 자산 manifest는 앱 리소스의 [한 파일](../../../frontend/shared/src/commonMain/composeResources/files/jangdan/manifest.json)만 유지한다. 원본·검수 JSON·음원을 압축하거나 다시 생성하지 않았다.

기존 Python 검증 환경으로 저장소 루트에서 `python -m unittest discover -s data/tests -p 'test_*.py' -v`를 실행해 173개가 모두 통과했다(실패·생략0). 분리 확인 결과는 Git 제외 `data/processed/jangdan-split-160/verification.json`, 실행 로그는 `data-split-160-tests.log`에 남겼다. 이 검사는 자료의 동일성과 기존 데이터 도구의 회귀 확인이며 340의 API·앱 재생·실기기 검증을 대신하지 않는다. 전달 형식과 역할은 [공통 스펙](../../specs/jangdan-practice-playback.md)을 따른다.
