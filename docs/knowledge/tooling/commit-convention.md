# 커밋 규칙 검토

상태: 제안 — 팀 합의 전

자료 확인일: 2026-10-20

커밋 지침과 예시는 [루트 AGENTS.md](../../../AGENTS.md)에 둔다. 이 문서는 검토 근거와 미확인 사항을 기록한다.

## 형식과 변경 단위

Conventional Commits는 `타입: 설명`을 기본으로 하고 scope·본문은 선택 사항으로 둔다. `feat`·`fix` 외 타입도 허용한다. E102에는 일곱 타입을 브랜치 접두사와 커밋에 공통으로 사용하고, 커밋의 한글 제목·전체 Jira Key를 유지하는 간단한 형식을 제안한다. 필수 scope나 커밋 본문, 자동 버전 발행은 추가하지 않는다. [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)

Pro Git은 논리적으로 구분되는 변경을 커밋으로 묶고, 필요한 경우 같은 파일의 변경도 부분적으로 스테이징하도록 안내한다. E102에서는 Task를 작업 목적의 단위, 커밋을 그 목적 안의 검토 가능한 변경 단위로 사용한다. 기능 구현과 그 기능에 필요한 테스트는 함께 묶을 수 있고, 독립적인 수정은 별도 커밋으로 남길 수 있다. [Pro Git — Commit Guidelines](https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project)

조사·실험도 재사용할 결과가 문서로 남으면 `docs` 변경으로 취급한다. 타입은 작업자의 파트가 아니라 저장소에 남긴 변경의 성격으로 구분한다. 위 구분과 한글 제목·Jira Key 배치는 E102 적용 제안이며 표준이 강제하는 사항은 아니다.

## Task 범위 판단 예시

`연주 녹음 업로드 API 구현`은 요청 ID 반환·잘못된 파일 거부·허용 크기 확인을 완료 조건으로 정할 수 있다. 이를 위한 입력 검사와 회귀 테스트는 같은 Task에 포함한다. 업로드 완료와 별개로 사용할 수 있는 `연습 기록 조회 API 구현`은 별도 Task로 구분한다.

`대금 저음 구간 음높이 추정 방식 비교`는 비교할 후보·입력 데이터·평가 항목·비교 종료 조건을 정할 수 있다. 코드 수정이 없어도 조건·측정 결과·적용 판단이 남으면 완료를 판단할 수 있다. 후보 모두 기준을 충족하지 못했다는 결론도 근거가 있으면 결과에 포함한다.

이 예시는 작업 단위를 설명하기 위한 제안이며, 실제 API 사양이나 채점 기준을 확정하지 않는다.

## Jira 연결과 활동 기록

GitLab의 Jira 연동은 대문자 전체 이슈 Key를 참조할 수 있다. 실제 연결·댓글·상태 전환 동작은 설치된 연동 방식과 설정에 따라 달라진다. 커밋 메시지에 Key를 넣었다는 사실만으로 Jira 연결이나 상태 변경이 완료됐다고 판단하지 않는다. [GitLab Jira issue management](https://docs.gitlab.com/integration/jira/issues/), [Jira issues integration 설정](https://docs.gitlab.com/integration/jira/configure/)

Contributor analytics는 선택한 브랜치의 커밋을 집계한다. 커밋 수를 작업량이나 개인 기여도와 동일하게 취급하지 않고, Task의 결과·MR·문서를 함께 확인하는 방식을 제안한다. 하루 커밋 할당량이나 파일별 분할 기준은 두지 않는다. [GitLab Contributor analytics](https://docs.gitlab.com/user/analytics/contributor_analytics/)

Squash는 여러 커밋을 하나로 합치며 기본 author는 MR 작성자다. 팀이 병합 정책을 정할 때는 검토 편의와 작성자 기록을 함께 고려해야 한다. 이 초안은 squash를 강제하지 않으며 저장소 병합 설정을 변경하지 않는다. 현재 E102의 squash 설정과 SSAFY 평가 가중치는 확인하지 않았다. [GitLab Squash and merge](https://docs.gitlab.com/user/project/merge_requests/squash_and_merge/)
