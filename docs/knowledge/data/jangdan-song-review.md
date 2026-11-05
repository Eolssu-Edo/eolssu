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

## 160·340 분리 검증

분리 기준은 develop `a680a0ae`다. 기존 검수본 `fe9163c4204fe4d0e39dd6c061bc0161b96477eb`에서 DATA 전달물만 가져왔으며 이전 구현 브랜치의 이력을 병합하지 않았다. MP3 10개·WAV 11개와 런타임 JSON 2개(곡별 검수·연습 자산 manifest)는 원본 Git blob과 바이트가 같다. 검수 목록은 고유 songId 107개, READY 60개·장단명 미상45개·굴림 미검수2개를 유지한다.

각 생성 manifest의 MP3 10개·WAV 11개 해시가 실제 파일과 일치했다. WAV의 44.1kHz·모노·PCM16·프레임 수도 생성 명세와 같았다. 연습 자산 manifest는 앱 리소스의 [한 파일](../../../frontend/shared/src/commonMain/composeResources/files/jangdan/manifest.json)만 유지한다. 원본·검수 JSON·음원을 압축하거나 다시 생성하지 않았다.

기존 Python 검증 환경으로 저장소 루트에서 `python -m unittest discover -s data/tests -p 'test_*.py' -v`를 실행해 173개가 모두 통과했다(실패·생략0). 분리 확인 결과는 Git 제외 `data/processed/jangdan-split-160/verification.json`, 실행 로그는 `data-split-160-tests.log`에 남겼다. 이 검사는 자료의 동일성과 기존 데이터 도구의 회귀 확인이며 340의 API·앱 재생·실기기 검증을 대신하지 않는다. 전달 형식과 역할은 [공통 스펙](../../specs/jangdan-practice-playback.md)을 따른다.
