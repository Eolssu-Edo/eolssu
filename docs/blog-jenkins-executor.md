# 블로그 Jenkins 전용 실행 슬롯

- 작업 [S15P31E102-668](https://ssafy.atlassian.net/browse/S15P31E102-668) · Story S15P31E102-40
- 기준 `origin/blog`의 `fc670446`
- 대상 `edo1`의 `eolssu-blog` 운영 잡

## 동작과 완료 조건

`eolssu-blog`는 전용 라벨 `edo1-blog`를 가진 에이전트에서 한 번에 한 건씩 실행한다. 에이전트는 한 슬롯을 가지며 라벨이 없는 잡을 받지 않는다. 기존 `edo1-host`와 작업 공간은 분리하고, 같은 서버의 Jenkins 계정과 기존 Git·Docker 도구를 쓴다. 전용 에이전트가 오프라인이면 블로그 잡은 복구를 기다린다.

기존 SCM 폴링, GitLab checkout, Hugo 빌드, 변경된 결과물의 GitHub Pages 게시를 유지한다. 컨트롤러, 기존 에이전트, 편집 서버와 공개 사이트는 가동 중에 전환한다. 성공 기준은 Jenkins의 실제 노드와 빌드 결과, 공개 블로그 HTTP 응답으로 확인한다.

## 적용 순서

1. Jenkins 선언형 문법과 systemd unit을 검사한다. 에이전트 이름, 라벨, 작업 공간에 충돌이 없는지 확인한다.
2. 기존 관리자 CLI의 `groovy =`에 [전용 노드 등록 파일](../scripts/jenkins/register-blog-agent.groovy)을 입력한다. 등록 파일은 일치하는 노드를 재사용하고 기존 설정이 다르면 중단한다.
3. `/var/lib/jenkins-blog-agent`를 `jenkins:jenkins` 소유, 모드 `0750`으로 준비한다. 서버에서 Jenkins CLI로 `Jenkins.get().getComputer('edo1-blog').getJnlpMac()`을 호출해 얻은 inbound secret을 값이 노출되지 않게 `secret` 파일에 모드 `0600`으로 저장한다. 운영 관리 인증 파일과 Remoting JAR는 기존 컨트롤러 설치 경로를 사용한다.
4. [전용 서비스 파일](../scripts/jenkins/eolssu-jenkins-blog-agent.service)을 `/etc/systemd/system/`에 설치하고 `systemctl daemon-reload`, `systemctl enable --now eolssu-jenkins-blog-agent`를 실행한다. `edo1-blog`가 온라인이고 공용 에이전트도 온라인인지 확인한다.
5. 최근 블로그 빌드를 새 Jenkinsfile로 Replay해 전용 노드의 GitLab, Docker, GitHub 인증과 빌드 경로를 확인한다. `blog`에 병합한 뒤에는 기존 SCM 잡이 Jenkinsfile을 다시 읽고 전용 노드에서 성공하는지 확인한다.

## 복구

전용 에이전트가 끊기면 새 서비스 상태와 로그를 확인해 해당 서비스만 복구한다. 공용 슬롯으로 복귀해야 하면 Jenkinsfile의 에이전트 지정을 되돌리는 변경을 `blog`에 반영한다. 이전 Jenkinsfile을 읽은 빌드가 에이전트 할당만 기다리면 게시 단계에 진입하지 않았는지 확인한 뒤 그 빌드를 중단하고 다시 실행한다. 이미 게시를 진행 중인 빌드는 종료를 기다린 뒤 전용 서비스를 중지한다. 서비스 중지 뒤에도 설정과 작업 공간을 보존한다. 마지막으로 게시된 Pages 사이트는 빌드 대기 중에도 요청을 처리한다.

## 적용 전 검증

- Jenkins 선언형 문법과 `systemd-analyze verify`를 통과했다. Python 블로그 회귀 33건과 편집 서버 회귀 28건, Hugo 0.145.0의 오프라인 빌드도 통과했다.
- 운영 Jenkins [eolssu-blog #100](http://127.0.0.1:18080/job/eolssu-blog/100/) Replay는 `edo1-blog`에서 소스 `fc670446`을 빌드했다. 슬롯 할당까지 84ms, 실행 결과는 12.97초에 SUCCESS였다. 산출물 차이가 없어 Pages 커밋은 생성하지 않았다. 공개 홈과 기존 글은 HTTP 200이었다.
