# 운영 AI 장애·복구 후 같은 세션 재시도 검증

- Task: [S15P31E102-178](https://ssafy.atlassian.net/browse/S15P31E102-178), Story 38 관련, 활성 스프린트 59139, MR 병합 전 진행 중 유지.
- 실행: 2026-10-29 13:46~13:49 KST, 공개 HTTPS `https://k15e102.p.ssafy.io`.
- 사용자 승인: 운영 AI 중단·복구의 공유 영향 설명 후 명시적 승인. 사용자가 제공한 개발용 테스트 계정으로 로그인했다. 계정 비밀번호·쿠키·서명 URL은 출력·파일 저장하지 않았다.
- 작업 브랜치: `docs(be)/S15P31E102-178-서버-장애·복구와-재시도-검증-기록`. 기존 178 작업 worktree는 다른 채팅 소유여서 수정하지 않았다.

## 결론

**같은 세션의 실패→재시도→DONE과 평가 저장은 확인했다. 다만 AI healthy 직후 첫 재시도는 다시 SERVER_ERROR였고 추가 재시도가 성공했다.** 이 결과를 즉시 복구 후 첫 재시도가 항상 성공한다고 보고하지 않는다. FE·실기기 검증은 사용자 결정으로 실기기 점검 Task 283에 통합했다(194의 점검 범위도 283으로 이전).

## 버전·입력·안전 장치

- backend 실행 JAR: `689fbf2a703fe6389ba510be5457aaaf6158660d`. 기존 정상·무음 검증 JAR `2bd9d49563ecfc72df2016c3412d48ab71914e79`와 backend/AI 소스 diff는 없다.
- AI 이미지 revision: 기존 `88a1b058ff9bdd60179d8e161e2af3fbbcc99c84`, 같은 컨테이너를 stop/start했다. 설정·이미지·DB 행을 직접 변경하지 않았다.
- 새 QA 세션 8, 곡 1, BPM96/DOTTED_QUARTER, NOTE idx0~1. 아리랑 원본 픽스처의 첫 두 음을 합성한 AAC(m4a) 44.1kHz·모노, 1,450ms·20,118바이트.
- 입력 SHA-256: `e15f564016e7799fa6d6edb45af069e4d18962b31f8697636b8cd8d0772b2d3d`. 실제 마이크·악기 녹음과 채점 품질 검증이 아니다.
- 배포 공용 lock `/run/lock/eolssu-backend-deploy.lock`과 PENDING0건 가드를 확인한 뒤 중단했다. 원격 제어 프로세스는 종료/연결 종료/90초 제한 시 AI를 다시 시작하도록 했고, 정상 경로에서는 즉시 복구·healthy를 확인했다. frontend·mysql·backend 중단 명령은 실행하지 않았다.

## 실제 API·DB·로그 결과

| 단계 | 관찰 | 판정 |
| --- | --- | --- |
| 로그인·세션 생성 | 200 / 201, 새 세션8 | 통과 |
| AI 중단 중 업로드 | 202 뒤 FAILED/SERVER_ERROR, 관찰 2.22초 | 통과 |
| AI 복구 | running/healthy, 시작 시각 13:47:13 KST | 통과(컨테이너 상태) |
| 첫 retry | POST /api/practice-sessions/8/retry → 202/PENDING, 이후 0.13초에 FAILED/SERVER_ERROR | 기대 DONE 미달 |
| backend→AI HTTP 확인 | backend 컨테이너에서 ai 이름 해석 후 빈 POST /v1/analyses → 422 | HTTP 도달 통과, 실제 분석 성공과 구분 |
| 같은 세션 추가 retry | 202/PENDING → 약15.54초 뒤 DONE, evaluationId2 | 통과 |
| DB 교차 확인 | status DONE, fail_reason NULL, analysis_attempt3, 원본 존재·20,118바이트, 평가1개·음표2개 | 통과 |
| 저장 점수 | total/pitch/timing/duration 모두100 | 저장값 확인(합성 두 음 범위) |
| 로그 | AI analysis session=8 attempt=3 result=DONE ms=15417 | API·DB 결과와 일치 |
| 로그인 정리 | 두 테스트 로그인 모두 로그아웃200, 메모리 쿠키 삭제 | 통과 |

첫 backend 로그 수집은 다음을 확인했다. 시도1은 13:47:10의 ResourceAccessException·SERVER_ERROR(elapsed78ms), 시도2는 13:47:19의 ResourceAccessException·SERVER_ERROR(elapsed50ms)였다. AI에는 이 두 시도의 처리 로그가 없었다. 오류 메시지에 서명 URL이 포함될 가능성을 피하려고 예외 종류와 세션·시도·결과만 수집했다. 컨테이너 health 성공과 backend HTTP 경로의 실제 회복을 구분해야 한다. 첫 재시도 실패의 DNS·연결 풀·서비스 기동 중 어느 상세 원인인지는 미확인이다.

추가 재시도 시작은 DB 기준 13:48:58 KST였다. 최종 대조 때 backend는 별도 배포 JAR `58d9fe95b4fa0fc69e5c899403080abee18bdc79`로 13:49:19 KST에 재생성돼 있었다. 이 검증 스크립트는 backend를 재시작하지 않았다. 추가 재시도 DONE 응답 이후의 재생성이므로 성공 원인으로 간주하지 않는다. 새 backend 컨테이너에는 이전 성공 시도의 로그가 없고, **AI 성공 로그·DB·그 전에 받은 공개 API 응답**으로 성공을 교차 확인했다. 최종 상태는 backend·AI 모두 running/healthy다.

## 기존 정상·무음·짧은 입력 증거

13:22:49~13:23:34 KST 실행의 정상·무음·짧은 AAC 결과와 DB·로그를 재확인했다. 로그인·세션 생성·같은 키 재전송·형식 거부 후 상태 유지·업로드·최종 상태·중복 업로드 거부·로그아웃까지 15개 관찰 항목이 통과했다. 이 실행의 backend는 2bd9d49이며 이번 장애 검증의 689fbf2와 backend/AI 코드가 같다.

| 세션 | 입력 | 업로드/최종 상태 | 처리 시간 | DB 평가/음표 |
| --- | --- | --- | --- | --- |
| 4 | 정상 합성음 1,450ms/20,118바이트 | 202 → DONE | 20.44초 | 1/2 |
| 5 | 무음 1,450ms/1,336바이트 | 202 → FAILED/NO_SOUND | 2.76초 | 0/0 |
| 6 | 짧은 합성음 100ms/2,674바이트 | 202 → FAILED/TOO_SHORT | 2.84초 | 0/0 |

세션별 원본 존재·44.1kHz/1채널·분석 시도1을 DB로 확인했고 backend·AI 로그 결과도 일치했다. 해당 관찰 구간의 서명 URL 표시·wiring 경고·저장소 쓰기 경고는 0건이다. 13:12 최초 실행의 R2 403/업로드503은 별도 272 작업의 운영 버킷 설정 정정 후 해소됐다. 전체 과거 로그 비노출이나 실제 악기 채점 품질을 주장하지 않는다.

## 재사용·한계·다음 확인

- 기존 세션4 DONE/평가1/음표2, 세션5 NO_SOUND, 세션6 TOO_SHORT의 API·DB·로그 증거를 재사용했다. 이번에 같은 정상/무음 입력 전체 검사를 다시 실행했다고 주장하지 않는다.
- R2 직접 읽기·비공개·실제900초 만료·recordings/30일 규칙은 [272 검증 기록](../../plans/S15P31E102-272.md)에서 확인했다. 30일 경과 후 실제 객체 소멸까지 입증한 것은 아니다.
- AI healthy 직후 첫 재시도 실패 원인 진단은 2026-10-29 사용자 결정으로 추가 수행하지 않는다. 관측된 실패는 보존하고 즉시 복구 성공으로 바꾸지 않는다. 이번 기록은 제품 코드나 운영 정책을 수정하지 않는다.
- 실제 악기·앱 실패 안내·업로드 단절·앱 재시작 복구 검증은 [S15P31E102-283](https://ssafy.atlassian.net/browse/S15P31E102-283)에 통합했다(2026-10-29 사용자 최종 선택). 194의 연습 시간·성장 화면 점검 범위도 283으로 이전했다. Story 38 관련·Epic 20·담당 우동균·활성 스프린트 59139 배정·진행 중이며 실기기 검증은 미완료다. 194는 이전 정리 완료로 종료하며 실제 검증 통과를 뜻하지 않는다. 녹음 한도 실측은 기존 185와 구분한다.
- 사용자 결정 후 178의 서버 검증 수행 범위는 마쳤다. 검증 기록을 공유하고 develop 병합에 따른 Jira 연동으로 종료한다. 후속 283의 실기기 검증 완료를 뜻하지 않는다.
- 원본·QA 세션·평가는 재현 근거로 유지했고 다른 사용자 데이터는 수정하지 않았다. 재시도에 따른 DB 갱신은 공개 API가 수행했다.

실행 근거: `/private/tmp/pra178-fault-retry-result.json`, `/private/tmp/pra178-fault-retry-db.txt`, `/private/tmp/pra178-fault-retry-logs.json`. 임시 파일은 정리될 수 있어 핵심 입력·결과·한계를 이 문서에 남겼다. 한 번 실행한 도구는 `/private/tmp/pra178_fault_retry.py`·`/private/tmp/pra178_retry_after_recovery.py`다. 값이 포함된 로그인 쿠키·서명 URL·전체 운영 로그는 저장소에 복사하지 않았다.
