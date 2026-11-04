# 곡별 장단 반주 연결 검수

S15P31E102-340, 2026-11-04. 사용자가 실제 올라간 악보를 검수해 연습 속도에 맞춘 장단 반주를 구현하도록 승인했다. 이번 백엔드 변경은 CAT-04에 검수된 반주 메타데이터를 추가하며 운영 DB·서버는 변경하지 않는다.

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

중모리 곡19는 BPM88·ticksPerBeat4·ticksPerJeonggan4라48tick이 한 주기다. 곡81은 BPM44·ticksPerBeat4·ticksPerJeonggan2라24tick이며 둘 다8181.818ms다. 세마치 곡9와29도 각각 BPM144/ticksPerBeat2와 BPM48/ticksPerBeat6이지만 cycleTicks9로1875ms가 같다. 도드리·타령의12칸과 엇모리10칸, 단모리4큰박도 각 패턴의 칸·그룹과 함께 대조했다. 카탈로그 구음·음가의 출처는 [장단 카탈로그 확장 근거](jangdan-catalog-extension.md)를 재사용했다.

이 검수는 현재 등록된 선율에 일정한 교육용 기본형을 붙이는 연결 확인이다. 원전 연주자의 변주·장식 타격·실제 녹음의 미세한 박 변화·청음 품질까지 검증한 것은 아니다. 향후 못갖춘마디나 장단 전환이 있는 판본은 별도 시작점·구간 검수가 필요하며, 현재 manifest와 다른 악보에는 이 연결을 재사용하지 않는다.

## 판본과 패턴 변경 차단

`ReviewedJangdanPlayback`은 게시 악보의 일반 검증이 끝난 뒤 다음을 확인한다.

1. 명시된 songId의 READY 항목이어야 한다.
2. 외부/내부 scoreVersion·sourceId·기준 BPM·BPM 단위와 공통 악보 의미 해시가 검수값과 같아야 한다.
3. 실제 장단 DB 행의 ID·이름·BPM 단위·pattern이 manifest의 검수 패턴과 같아야 한다.

다르면 `jangdanPlayback=null`만 반환한다. 따라서 곡44의 이전1판이나 새 음표/시간으로 바뀐 미검수판본에는 연결되지 않는다. 기존 악보 오류의409 또는 비게시404는 유지한다. 기존 FK가 있어도 별도 검수 manifest가 없는 곡에는 반주를 제공하지 않는다.

의미 해시는 `typed-tree-sha256-v1`이다. null=`n`, boolean=`t/f`, 숫자=`d`+뒤0/지수 제거한10진수+`;`, 문자열=`s`+UTF-8바이트길이+`:`+문자열, 배열=`[`+요소+`]`, 객체=`{`+UTF-16키순으로 정렬한 문자열키·값+`}`를 UTF-8로 인코딩해 SHA-256한다. 문자열 내용·배열순서·숫자/문자열 타입을 보존하면서 MySQL JSON의 키 순서·공백·동등한 수 표기 차이만 제거한다. 일반 파일 SHA-256이나 CAT-05 sourceCommonSha256과 다른 목적이며 혼용하지 않는다.

## 검증

단위 검사는 굿거리의 새 응답 필드가 없는 기존 구현에서 실패한 뒤 구현 후 통과했다. 변경 판본/BPM/단위/원문/카탈로그의 차단, 세마치·중모리의 다른 음가 스케일,107개 목록의60/45/2 분류, JSON 의미 해시의 동등·상이 입력을 검사한다.

실제 MySQL8.4 폐기 DB에서는8개 원본 판본을 고정 fixture로 넣고 공개 HTTP를 호출했다. MySQL JSON 재직렬화 이후에도 올바른 연결이 제공되고, NULL FK·BPM·악보 원문이 보존되며, BPM/원문/패턴 변경 시 연결만 제거됨을 확인했다. 첫 실행의 테스트 SQL 칼럼 오기(`pattern_json`)를 실제 mapper의 `pattern`으로 고쳐 재실행했다. 제품 스키마를 수정하거나 검사를 약화하지 않았다.

JDK 21에서 `backend/`의 `.\gradlew.bat clean build --console=plain`이 성공했다. 일반 검사 587개가 모두 통과했다. 별도 MySQL 8.4 컨테이너에 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 지정한 `.\gradlew.bat databaseTest --console=plain`도 성공했다. DB 검사 230개 중 229개 통과, 외부 모범 음원 묶음 미설정 검사 1개 skip이며 실패는 없다. 이번 반주 DB 검사 2개는 모두 실행됐다. DB는 운영과 분리했고 검사용 곡을 실행 후 제거했다.

검수 후 공개 API 107개를 다시 읽어 모든 scoreVersion과 의미 해시가 manifest와 일치함을 확인했다. 이 재조회 원문과 결과는 Git 제외 `data/processed/jangdan-link-review-340/`에 남겼다. 자체 diff 점검을 수행했으며 독립 정식 리뷰는 대기다. Android 통합 재생·실기기·연주자 청음은 이 백엔드 검사만으로 통과 처리하지 않는다. FE와 합의한 JSON 구조는 [CAT-04 계약](../../specs/catalog-read-apis.md#검수된-장단-반주-s15p31e102-340)에 있다.
