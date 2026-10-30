# 국내 비공개 녹음 저장 전환

Task: S15P31E102-300. [구현 계약](../../docs/specs/terms-release.md).

`RECORDING_STORAGE_TYPE=local`일 때 backend는 `/var/lib/eolssu/recordings`에 원본을 저장한다. 호스트는 `/opt/eolssu/recordings`이며 웹 루트에 연결하지 않는다. backend UID/GID 10001에만 디렉터리 접근·파일 읽기/쓰기를 허용한다. 로컬 서명 키는 JWT 키와 별도로 32바이트 이상 무작위 값을 서버 `.env`에만 설정한다.

AI용 읽기 기준 URL은 Compose 내부 `http://backend:8080/api/recordings/read`다. 외부 사용자에게 이 내부 주소를 반환하지 않는다. 원본 다시 듣기를 앱에 연결할 때는 로그인한 본인 세션 확인 후 외부 HTTPS 서명 URL을 발급하는 별도 흐름이 필요하다. 이번 변경만으로 앱의 원본 다시 듣기 UI가 완성됐다고 보지 않는다.

## 사전 대조 및 준비 복사

이관 도구는 운영 서버에 설치된 boto3를 사용한다. 기본은 목록 조회이며 파일·원본·DB를 변경하지 않는다.

```sh
sudo python3 migrate_r2.py --env-file /opt/eolssu/.env --destination /opt/eolssu/recordings
```

`--stage --manifest <새 root 전용 JSON 경로>`는 운영 업로드를 유지한 채 준비 사본을 복사한다. R2 원본은 지우지 않는다. 새 사본은 크기·SHA-256을 대조하고 목록에 기록된 원래 업로드 시각을 mtime으로 보존한다. R2 HTTP 날짜는 초 단위, 목록은 소수 초를 포함할 수 있어 동일 초를 비교하고 ETag IfMatch로 원본 변경을 거절한다. 기존 국내 파일이 다르면 덮어쓰지 않는다. 예상 형식 밖 키·심볼릭 링크·크기 불일치는 중단한다.

2026-10-30 서울 리전(ap-northeast-2)을 인스턴스 메타데이터로 확인했다. 준비 복사는 12개, 1,602,140바이트를 검증했다. 사본 경로 `/opt/eolssu/recordings`, 검증 manifest `/opt/eolssu/recordings-stage-300-20261030.json`(root 전용). 원본·DB·운영 설정 전환은 수행하지 않았다.

## 최종 전환

1. 사용 중인 배포 lock과 백업 절차를 지키고 녹음 업로드를 잠시 중지한다. 분석 중 작업이 끝났는지 확인한다.
2. R2 목록 및 DB의 recording_url을 다시 대조한다. `--apply --uploads-paused --manifest <다른 새 경로>`로 준비 복사 이후 차분까지 검증한다. 이 옵션은 실제 업로드 중지를 대신하지 않는다.
3. 지정 녹음 디렉터리의 경로와 파일 목록을 확인한 뒤 UID/GID 10001, 디렉터리 0700·원본 0600을 적용한다. manifest는 별도 root 전용으로 유지한다.
4. 검증된 backend JAR와 Compose·Nginx를 배포하고 `RECORDING_STORAGE_TYPE=local`, 로컬 서명 키 및 디스크 마운트를 반영한다. R2 설정은 검증 전 복구를 위해 보존한다.
5. 실제 녹음 업로드·AI 다운로드·채점, 서명 만료·위조 거절, 삭제 후 404와 탈퇴 배치를 확인한다. 새 파일이 국내 경로에 생성되는지 확인한다.
6. 국내 사본 검증·운영 전환 후 R2 잔존 원본 삭제 계획을 별도로 승인받아 처리한다. 이 도구에는 원본 삭제 기능이 없다.

30일이 된 원본은 즉시 읽기를 거절하고 매시 15분에 디스크에서 정리한다. 정리 중 장애가 없으면 물리 삭제까지 최대 약 1시간 지연된다. 실패·프로세스 중단의 고아 업로드 임시파일도 시간별로 정리한다. 정상 업로드 임시파일은 성공·실패 시 즉시 제거한다.

## Naver 인증 메일

메일 전환은 다른 담당자가 완료했다는 사용자 안내에 따라 이번 작업에서 제외했다. 운영 계정·비밀번호·호스트·포트를 변경하지 않는다. 기존 Naver 설정을 사용하고 실제 운영 API 확인은 해당 담당자의 변경과 대조한다.

Gmail의 기존 STARTTLS 지원은 유지한다. 이 작업에서 추가했던 465 지원과 전환 도구는 복구 가능한 로컬 보류 자료로 보존하고 활성 소스·배포 범위에서 제외했다. Google 소셜 로그인은 SMTP와 별개다.

## 검증

- `python -m unittest discover -s infra/storage -p 'test_*.py' -v`
- backend에서 `gradlew.bat clean build`. 로컬 저장·HTTP Range·변조·만료·삭제 및 공개 문서 인증 경계를 포함한다.
- `docker compose --env-file infra/runtime/.env.example -f infra/runtime/compose.yml config --quiet`는 비밀값 없는 합성 MYSQL_PASSWORD·MYSQL_ROOT_PASSWORD를 환경으로 지정해 문법을 검사한다.
