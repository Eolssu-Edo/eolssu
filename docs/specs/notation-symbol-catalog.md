# 정간보 기호 사전 연결

작업: S15P31E102-410. 기존 [CAT-09](catalog-read-apis.md)를 확장한다.

- 기존 FE `Sigimsae.kt`의 47개 그림(요성·농음의 두 그림은 별도 항목), `ScoreNote`의 `ㅡ`·`△`를 등록한다. 이름·설명을 보존하고 그림 기호의 `symbol`은 기존 악보 렌더러의 한글 이름 토큰을 쓴다. 전각 `－`·반각 `-`를 `ㅡ`로 합치지 않는다.
- 빈 사전을 V19로 채우되 이미 같은 symbol이 있으면 기존 행·ID·설명을 덮어쓰지 않는다. API는 실제 DB 행만 ID순으로 반환한다.
- 사용자 승인(2026-11-03): 공개 GET `/api/notation-symbols/{symbolId}/image`에서 번들 SVG를 제공하고 `imageUrl`은 API 원점 기준 상대 경로다. FE는 해당 ID의 정확한 경로만 해석하며 기존 외부 HTTPS 링크 제한을 유지한다.
- 등록 내용이 번들 원문과 일치할 때만 `source`, `descriptionStatus`(DOCUMENTED/EDUCATIONAL/INFERRED), `imageLicense`를 추가한다. 기존·수정된 외부 행에는 null이다. 노라느니는 INFERRED이며 공식 정의 미확인을 화면에 표시한다.
- 이미지에는 기존 MIT 저작권·허가 고지를 포함한다. 문헌의 재확인·전공자 검수를 했다고 주장하지 않는다. 악보 이벤트·음높이·채점은 변경하지 않는다.

완료 검사는 폐기 가능한 MySQL에서 V19·실제 공개 HTTP·SVG/XML 응답과 출처를 확인하고, FE 변환·기존 설명/그림 일치 검사 및 프로젝트 필수 빌드를 수행한다. 운영 DB 적용·Push·MR·배포는 범위 밖이다.

출처와 기존 문헌 검수 한계는 [시김새 조사](../knowledge/domain/sigimsae-symbols.md)를 따른다.
