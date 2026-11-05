# Jenkins 실패 알림: 미전송 원인과 짧은 오류 근거

S15P31E102-509, 2026-11-05 운영 Jenkins 조회 기준. [운영·복구](../../../infra/jenkins/notifications/README.md) · [요구사항](../../specs/jenkins-failure-notifications.md)

운영 잡 설정에서 Mattermost 실패 전송 구현이 없음을 확인했다. 일부 `post`는 JUnit·artifact 보관용으로, 알림 전송을 의미하지 않았다. backend/frontend/android/iOS/graphify는 inline Pipeline, blog는 SCM Pipeline이라 저장소 Jenkinsfile 하나의 변경으로 전체 실패 경로를 처리할 수 없었다. 따라서 Jenkins 완료 결과를 읽는 외부 observer를 선택했다. 기존 API 인증·CI/CD webhook을 재사용하고 공개 포트·플러그인·인증 토큰은 추가하지 않았다.

## 실제 실패를 대조한 결과

| 빌드 | 실패 단계·근거 | 메시지의 조치 |
| --- | --- | --- |
| backend #256 | Check database migrations, 배포 단계 검사 실패 | deployment.jsonl과 Flyway 버전·checksum·호환성 비교; 임의 repair 금지 |
| frontend #185 | JavaScript browser tests, 841개 중 1개 실패 | Test Result의 실패명·기대값/실제값 확인 후 같은 테스트 재실행 |
| ios-qa #24 | Build signed IPA on Mac, provision profile·개발 Team·앱 ID 관련 오류 | Mac의 Bundle ID·Team·인증서·프로파일 일치 후 서명 빌드 재실행 |

전체 Console Output에는 앞 단계의 **성공한 테스트가 의도적으로 출력한 오류**도 섞인다. 실제 frontend #185의 앞 단계에 마이그레이션 모의 오류가 있어 전체 로그만 분류하면 원인을 잘못 설명했다. 실패 단계의 로그를 우선 사용하고, 노드 로그 API가 실패한 경우 Console의 해당 단계 범위만 발췌해 오분류를 막았다. skipped 단계에도 실패가 전파되므로 가장 먼저 실패한 단계를 고른다. 단계 메타데이터가 없으면 전체 로그를 사용한다는 한계가 있다.

로그는 최대 3줄·합계 300자로 제한한다. 일반적인 exit code·BUILD FAILED보다 파일/행·테스트명·구체 오류를 우선한다. 원인을 확인할 수 없으면 미확정이라고 쓰고, 원시 로그 전체 대신 기존 SSH 터널의 Jenkins 링크를 제공한다. 변경 작성자는 원인 제공자를 뜻하지 않는다.

## 검증 근거와 남은 범위

- 신규 격리 테스트 11개와 기존 Jenkins 배포 테스트 50개 통과. 길이 제한 제거·동시 완료 추적 제거를 임시 복제본에 적용하면 관련 회귀 테스트가 실제 실패함을 확인했다.
- 실제 API로 backend/frontend/iOS 과거 실패를 미리보기하고, iOS #24 한 건을 `과거 실패 알림 연결 검증`으로 표시해 기존 CI/CD webhook에 전송했다. HTTP 200과 영속 `sent` 상태를 확인했다. 브라우저는 로그인이 필요해 채널 수신 화면 확인은 하지 못했다.
- edo1의 DynamicUser·LoadCredential·타이머 실행 성공과 서버/저장소 프로그램 SHA 일치를 확인했다. Jenkins·앱 재시작, 운영 빌드 강제 실패, DB 변경은 하지 않았다.
- 전송 응답이 모호하면 중복 방지를 우선해 `uncertain`으로 보존한다. 미수신 복구는 채널 검색 후 수동 판단한다. 관찰기는 최근 100개 빌드를 조회하며 장기 중단·병렬 중첩 단계 로그에는 운영 확인이 필요하다.

공식 자료는 [Jenkins Remote Access API](https://www.jenkins.io/doc/book/using/remote-access-api/)와 [Pipeline REST API](https://plugins.jenkins.io/pipeline-rest-api/)다. API의 제공 기능은 공식 자료를 참고했고, 운영 API 응답·실패 분류·배포 결과는 위 실제 빌드로 검증했다.
