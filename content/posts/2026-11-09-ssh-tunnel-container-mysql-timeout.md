---
title: "컨테이너에서만 타임아웃 난 MySQL SSH 터널 추적기"
date: 2026-11-09
slug: "ssh-tunnel-container-mysql-timeout"
author: "우동균"
categories: ["infra"]
tags: ["Docker", "SSH", "MySQL", "UFW"]
description: "호스트에서는 응답하던 SSH DB 터널이 복제 컨테이너에서만 시간 초과될 때 연결을 구간별로 확인한 기록입니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-ssh-tunnel-container-mysql-timeout.webp"
draft: false
---

MySQL 복제를 준비하면서 원본 DB에 닿는 경로를 SSH 터널로 제한했다. 원본 서버의 전달 포트는 루프백에만 열었다. 대기 서버에서는 SSH 클라이언트가 전용 브리지의 호스트 주소에 포트를 열어 복제 컨테이너의 연결을 받도록 했다.

대기 서버 호스트에서 터널 포트에 접속하자 MySQL이 첫 응답을 보냈다. 같은 포트에 **복제 컨테이너에서** 접속하자 `ERROR 2003`과 함께 연결 시간이 초과됐다.

## 응답이 끊긴 구간 찾기

연결 경로를 세 위치에서 비교했다.

| 어디에서 연결했나 | 관측 결과 | 다음에 확인한 구간 |
| --- | --- | --- |
| 원본 호스트에서 로컬 전달 포트로 | MySQL이 첫 응답을 보냄 | 원본 DB까지의 경로 확인 |
| 대기 호스트에서 브리지 주소의 터널 포트로 | 같은 MySQL 응답을 받음 | SSH 터널과 원본 전달 경로 확인 |
| 대기 서버의 복제 컨테이너에서 같은 주소로 | `ERROR 2003`, 시간 초과 코드 110 | 컨테이너에서 호스트로 들어오는 경로 확인 |

서비스가 실행 중이라는 표시만으로는 터널이 실제로 원본 DB까지 이어지는지 알기 어렵다. 이 경우에는 호스트에서 MySQL이 첫 응답을 보냈으므로, SSH 인증이나 원본 DB보다 **복제 컨테이너에서 대기 호스트로 들어오는 구간**을 먼저 확인할 수 있었다. MySQL의 2003은 연결 오류라는 범위를 알려준다. 같은 코드가 나와도 어느 위치에서 무엇이 응답했는지 비교해야 한다. [MySQL 접속 문제 진단](https://dev.mysql.com/doc/refman/8.4/en/problems-connecting.html)

같은 상황에서는 `ss -ltn`으로 호스트의 수신 주소를 확인한 뒤, 그 주소와 포트로 실제 컨테이너 안에서 접속해 본다. 아래 명령에는 컨테이너 이름과 터널 주소·포트를 넣는다. 미등록 사용자 이름과 `--skip-password`로 운영 자격 증명 없이 인증 단계에 도달하는지 확인할 수 있다. [MySQL 클라이언트 옵션](https://dev.mysql.com/doc/refman/8.4/en/mysql-command-options.html)

```sh
docker exec "$replica" mysql --protocol=TCP --connect-timeout=3 \
  --host="$relay_host" --port="$relay_port" \
  --user=invalid_probe --skip-password --execute='SELECT 1'
```

## 복제 컨테이너에서 들어오는 연결만 허용하기

이 연결의 목적지는 대기 호스트가 열어 둔 터널 포트다. 따라서 호스트의 입력 규칙을 살폈다. `docker network inspect`에서 컨테이너의 네트워크와 주소를, `ip -br addr`에서 호스트의 브리지 주소를 확인할 수 있다. 당시 대기 호스트의 `ufw status`에는 해당 브리지에서 터널 포트로 들어오는 연결을 허용하는 규칙이 없었다.

데이터가 들어가기 전의 복제 환경에서 브리지 이름과 복제 컨테이너 주소를 고정했다. 그다음 그 브리지의 특정 컨테이너 주소에서 터널 주소와 포트로 향하는 연결만 허용했다. 이렇게 하면 브리지와 출발지 주소가 바뀌어 규칙이 엉뚱한 연결에 적용되는 상황을 줄일 수 있다. [Ubuntu 방화벽 문서](https://ubuntu.com/server/docs/how-to/security/firewalls/)

두 조치를 적용한 뒤 같은 컨테이너에서 같은 주소로 접속하자 `1045 Access denied`가 돌아왔다. 미등록 사용자로 보낸 요청이므로, 이 응답으로 MySQL의 인증 단계까지 도달했음을 확인했다. 브리지와 방화벽을 함께 바꿨기 때문에 각 조치의 효과는 따로 측정하지 못했다.

## 실제 복제와 외부 접근을 따로 검사하기

마지막에는 전용 복제 계정으로 수신과 적용이 모두 동작하는지 확인했다. 초기 사본의 데이터 해시를 대조하고, 이후 원본에서 커밋된 트랜잭션도 적용된 것을 확인했다. 두 서버의 공개 주소에서는 전달 포트에 연결할 수 없었다.
