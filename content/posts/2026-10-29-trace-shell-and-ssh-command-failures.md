---
title: "원격 명령은 어디서 막혔을까"
date: 2026-10-29T00:00:00+09:00
slug: "trace-shell-and-ssh-command-failures"
author: "우동균"
description: "zsh 변수 확장과 SSH 자동 포워딩을 거치며 명령이 예상보다 일찍 실패한 세 기록을 묶었습니다."
categories: ["infra"]
tags: ["zsh","SSH","디버깅"]
thumbnail: "https://team-edo.github.io/blog-images/2026-10-trace-shell-and-ssh-command-failures.webp"
draft: false
---

운영 기록을 읽으려던 명령이 실패하면 먼저 원격 서버 상태를 의심하기 쉽습니다. 세 번의 실패를 다시 보니 Git에 인수를 넘기기 전, SSH에 인수를 넘기는 순간, SSH가 원격 명령을 시작하기 전이라는 서로 다른 위치에서 막혔습니다.

## zsh가 만든 인수 확인 {#zsh가-만든-인수를-먼저-본다}

첫 사례에서는 변수에 든 Git ref 뒤에 콜론과 경로를 바로 붙였습니다. zsh에서 뒤 문자열이 `:a`로 시작하면 변수 확장의 경로 수정자로 읽힐 수 있습니다. `git show "${ref}:archive/report.txt"`처럼 중괄호로 변수 이름을 닫으면 콜론은 Git이 받는 문자열에 그대로 남습니다. [zsh 확장 문서](https://zsh.sourceforge.io/Doc/Release/Expansion.html)

두 번째 사례에서는 SSH 옵션 여러 개를 문자열 하나에 보관했습니다. 기본 zsh에서는 일반 스칼라 변수를 확장한다고 공백마다 인수가 나뉘지 않습니다. 각각의 인수가 하나의 배열 원소가 되게 만들고 `"${ssh_args[@]}"`로 확장하면 경계가 보입니다.

```zsh
ssh_args=(-o BatchMode=yes -o ConnectTimeout=5)
ssh "${ssh_args[@]}" host 'uptime'
```

## SSH 포워딩 실패 추적 {#접속-준비에-실패하면-원격-출력이-나오지-않는다}

세 번째 사례에서는 SSH 별칭이 설정 파일의 로컬 포트 포워딩을 자동으로 열었습니다. 포트가 이미 쓰이는 상태에서 `ExitOnForwardFailure`가 적용돼 접속이 끝났고, 원격 명령은 실행되지 않았습니다. 포워딩을 쓸 이유가 없던 조회는 `ssh -o ClearAllForwardings=yes host 'uptime'`처럼 접속할 때만 설정을 비우는 방식으로 분리했습니다. 포워딩이 필요한 호출에는 기존 별칭 설정을 사용합니다. [OpenSSH 설정 문서](https://man.openbsd.org/ssh_config#ClearAllForwardings)

`LocalForward` 설정의 앞쪽 주소와 포트는 SSH 클라이언트가 실행되는 쪽의 입구입니다. 뒤쪽 주소와 포트는 원격 SSH 서버가 새로 접속할 목적지입니다. 양쪽에 같은 루프백 주소를 써도 각 연결이 시작되는 쪽의 로컬 서비스를 가리킵니다. 이번 충돌은 앞쪽 수신 포트를 열 때 일어났으므로 목적지 서비스가 열려 있어도 원격 명령까지 진행할 수 없었습니다.

## 접속 없이 재현한 출력

로컬에서는 가상의 ref와 호스트 이름을 쓰고, SSH는 접속 없이 적용 설정을 출력하는 `ssh -G`로 확인했습니다. 실제 서버의 주소나 설정은 사용하지 않았습니다.

| 확인한 단계 | 격리 재현에서 확인한 출력 |
| --- | --- |
| zsh의 ref 확장 | `"$ref:archive/report.txt"`는 현재 폴더로 시작하고 콜론이 없는 경로가 됐습니다. `"${ref}:archive/report.txt"`의 출력은 `main:archive/report.txt`였습니다. |
| SSH의 옵션 해석 | 문자열은 인수 1개로 넘어가 `keyword batchmode extra arguments at end of line`과 종료 코드 255를 반환했습니다. 배열은 인수 4개로 넘어가 `batchmode yes`와 `connecttimeout 5`를 출력했습니다. |
| 포워딩 설정 적용 | 같은 가상 설정에서 `ClearAllForwardings=yes`를 주자 `ssh -G` 출력에서 `localforward` 줄이 사라졌습니다. |

앞의 두 행은 인수 문제를 재현한 값입니다. 마지막 행에서 확인한 것은 포워딩 설정 제거까지이며, 당시 포트 충돌과 원격 명령의 미실행은 원래 기록에 근거합니다. 실제 접속이 시작된 뒤 어느 네트워크 구간에서 막혔는지는 [컨테이너에서만 타임아웃 난 MySQL SSH 터널 추적기](https://team-edo.github.io/posts/2026/11/ssh-tunnel-container-mysql-timeout/)에서 다룹니다.
