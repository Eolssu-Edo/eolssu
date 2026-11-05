# edo1 운영 MySQL 웹 조회

- 작업: [S15P31E102-514](https://ssafy.atlassian.net/browse/S15P31E102-514)
- 범위: 2026-11-05 사용자 합의. 운영 DB의 모든 테이블과 컬럼을 조회하며 수정 권한을 부여하지 않는다.

운영 담당자는 SSH 터널로 접속한 phpMyAdmin에서 eolssu의 테이블을 탐색하고 조회 SQL을 실행한다. 인증 관련 테이블도 포함한다. 운영 애플리케이션 계정이나 root로는 이 화면에 로그인할 수 없다.

phpMyAdmin은 별도 Compose로 운영하고 호스트 loopback에서만 요청을 받는다. 런타임 내부망의 MySQL에 TLS로 연결하며, 서버 이름과 CA를 검증한다. 운영 Nginx, MySQL 포트, 런타임 컨테이너, 데이터 볼륨, Grafana 권한을 유지한다.

완료 조건: 브라우저 로그인과 전체 eolssu 조회가 가능하다. MySQL이 추가·수정·삭제, DDL, 다른 업무 스키마 접근을 거부한다. 기존 MySQL 컨테이너 ID와 볼륨이 유지된다. 쓰기 거부 검증은 서비스 행을 건드리지 않는 격리 DB에서 한다.
