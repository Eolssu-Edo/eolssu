# edo1 운영 MySQL 웹 조회

운영 담당자 전용 phpMyAdmin입니다. eolssu의 모든 테이블과 컬럼을 보고 조회 SQL을 실행할 수 있습니다. 쓰기와 스키마 변경은 MySQL에서 막습니다. 작업: [S15P31E102-514](https://ssafy.atlassian.net/browse/S15P31E102-514).

~~~sh
ssh -N -L 127.0.0.1:18081:127.0.0.1:8081 edo1
~~~

edo1 SSH 별칭에 기존 포워딩도 설정되어 있고 이미 떠 있다면, 18081이 비어 있는지 확인한 뒤 위 명령에 -o ExitOnForwardFailure=no를 추가합니다.

터널을 연 뒤 [http://127.0.0.1:18081/](http://127.0.0.1:18081/)에서 eolssu_web_read로 로그인합니다. 비밀번호는 권한이 있는 운영자가 edo1의 root 전용 파일 /opt/eolssu/mysql-admin/web-read.password에서 확인합니다. 인증 정보가 표시되는 화면이나 내보낸 파일은 공유하지 않습니다.

비밀번호 확인은 로컬 터미널에서 실행합니다.

~~~sh
ssh -o ClearAllForwardings=yes edo1 'sudo cat /opt/eolssu/mysql-admin/web-read.password'
~~~

phpMyAdmin의 SQL 탭에서 가동 시간(초), 연결 수, 실행 중인 연결 수를 조회할 수 있습니다.

~~~sql
SHOW GLOBAL STATUS
WHERE Variable_name IN ('Uptime', 'Threads_connected', 'Threads_running');
~~~

## 설치·확인

1. edo1에서 8081이 비어 있고, docker network inspect eolssu-runtime_default의 실제 IPv4 서브넷 및 기존 MySQL 컨테이너·볼륨을 확인합니다. Compose와 PHP 설정을 /opt/eolssu/mysql-admin/에 배치합니다.
2. 운영 MySQL의 로컬 root 세션에서 같은 이름의 전용 사용자가 없는지 확인합니다. 아래 주소는 확인한 운영 런타임의 서브넷입니다. MySQL이 생성한 비밀번호는 위 root 전용 파일(0600)에만 보관합니다.

   ~~~sql
   CREATE USER 'eolssu_web_read'@'172.18.0.0/255.255.0.0'
     IDENTIFIED BY RANDOM PASSWORD REQUIRE SSL WITH MAX_USER_CONNECTIONS 3;
   GRANT SELECT ON eolssu.* TO 'eolssu_web_read'@'172.18.0.0/255.255.0.0';
   SHOW GRANTS FOR 'eolssu_web_read'@'172.18.0.0/255.255.0.0';
   ~~~

3. 운영 MySQL의 공개 CA 인증서인 /var/lib/mysql/ca.pem을 컨테이너에서 꺼내 UI 구성 폴더의 mysql-ca.pem으로 둡니다. CA의 개인 키는 복사하지 않습니다. server-cert.pem의 이름(자동 생성 인증서는 CN)과 런타임 네트워크 안의 실제 MySQL IP를 .env의 MYSQL_ADMIN_TLS_HOST와 MYSQL_ADMIN_DB_IP에 각각 넣습니다. phpMyAdmin은 이 CA와 서버 이름을 검증합니다. MySQL 재생성으로 IP가 바뀌면 .env를, 인증서 교체 시에는 인증서와 이름을 갱신하고 이 UI만 다시 시작합니다.
4. edo1에서 아래 명령으로 별도 서비스를 시작합니다.

   ~~~sh
   sudo docker compose --project-directory /opt/eolssu/mysql-admin -f /opt/eolssu/mysql-admin/compose.yml config --quiet
   sudo docker compose --project-directory /opt/eolssu/mysql-admin -f /opt/eolssu/mysql-admin/compose.yml up -d
   ~~~

5. ss로 8081이 127.0.0.1에만 열리고 3306이 호스트에 열리지 않는지 확인합니다. 웹에서 SHOW TABLES, SHOW GRANTS, SHOW STATUS LIKE 'Ssl_cipher'를 확인합니다. eolssu에 SELECT만 있고 TLS cipher가 비어 있지 않아야 합니다. 기존 런타임 컨테이너 ID와 MySQL 볼륨도 확인합니다. 운영 데이터에 쓰기 SQL을 실행해 검증하지 않습니다.

## 중단·복구

sudo docker compose --project-directory /opt/eolssu/mysql-admin -f /opt/eolssu/mysql-admin/compose.yml down으로 UI만 내립니다. 접근을 영구히 없앨 때는 별도로 위 호스트 조건의 전용 사용자만 DROP USER하고 비밀번호 파일을 제거합니다.

edo2 복제본에는 이 계정이 없습니다. 그래서 `DROP USER`나 권한 변경(`GRANT`·`REVOKE`)을 그냥 실행하면 복제본에서 실패해 복제가 멈춥니다. 같은 세션에서 `SET SESSION sql_log_bin = 0;`을 먼저 실행하고, 끝나면 `SET SESSION sql_log_bin = 1;`로 되돌립니다. 열 단위로 줄일 때의 순서는 [모니터링 문서](../monitoring/README.md#권한을-바꿀-때-edo2-복제-주의)를 따릅니다.

위의 비밀번호 확인 명령은 비밀번호를 터미널에 그대로 출력합니다. 화면 공유나 녹화 중에는 실행하지 말고, 확인한 뒤에는 터미널을 닫습니다.
