# 약관 본문 공개와 버전 등록

Task: S15P31E102-300. [공개·저장 계약](../../docs/specs/terms-release.md), [운영 개인정보 보유기간](../privacy/README.md).

버전 `2026-10-30`의 이용약관·처리방침·필수 수집 동의·접근권한 안내·연령 확인 원문은 `backend/src/main/resources/legal/2026-10-30`에 있다. 공개 경로는 `/api/terms/documents/2026-10-30/{service|privacy|collection|permissions|age}`이며, 앱 외 삭제 요청 안내는 `/api/terms/documents/account-deletion`이다.

## 공개 전 조건

2026-10-30 국내 저장 전환·기존 R2 원본 정리 후 공개 및 약관 4개 등록을 완료했다. 기존 버전과 동의 기록을 재생성하지 않는다. 이 절은 다음 신규 버전을 준비할 때의 절차다. 실제 반영 결과는 [작업 기록](../../docs/plans/S15P31E102-300.md)을 따른다.

현재 운영을 DB 쓰기 없이 점검하려면 저장소 루트에서 `python infra/terms/publish.py --verify-live`를 실행한다. 공개 HTML 6개의 해시와 약관 API의 ID 중복·한글 제목·필수 여부·버전·URL을 확인한다. HTTP 200만으로 성공 판정하지 않는다.

신규 본문은 운영 적용 전 검토를 거친다. 배포만으로 공개하지 않도록 `LEGAL_PUBLICATION_ENABLED=false`가 기본이며, 활성화 값과 LocalRecordingStorage가 모두 있어야 본문·삭제 요청 안내를 반환한다. 조건 미충족은 로그인 페이지나 SPA 대신 503이다. 활성화 후에는 모두 로그인 없이 제공한다.

1. 국내 저장 전환·이관 및 녹음 처리 검증을 마친다. R2 잔존 원본 처리도 확인하며 필요한 원본 삭제는 별도 승인 없이 실행하지 않는다.
2. 개인정보 담당자가 처리 근거·수탁자·연락처·동의 항목·시행일을 최종 검토한다. 소스의 시행일과 실제 적용일이 다르면 새 버전을 작성한다. 기능 범위를 실제 배포 APK와 대조한다.
3. 컨테이너별 로그 드라이버·기존 로그 처리, DB 백업 보유 상한, 감사 기록·문의 처리 운영 절차를 적용·확인한다. rsyslog와 타이머 설치만으로 모든 컨테이너 로그가 전환된 것은 아니다.
4. 백업 복원 후 삭제 내역을 재적용하는 담당자·절차를 확인한다. 문의 메일함의 90일 정리는 서버 타이머와 별개다.
5. 공개 활성화 후 익명 HTTPS 원문·UTF-8·로그인 불필요·모바일 가독성을 확인한다.

## 버전 등록

운영 호스트의 검토한 저장소 체크아웃 루트에서 실행한다. 도구는 같은 체크아웃의 HTML과 실제 HTTPS 응답을 해시로 비교한다. 리다이렉트·SPA 200·변경된 본문이면 등록을 중단한다.

```sh
sudo python3 infra/terms/publish.py
# 위 검사가 통과한 뒤 실제 시행 UTC 시각을 지정하고 승인된 등록을 실행한다.
sudo python3 infra/terms/publish.py --apply --effective-at YYYY-MM-DDTHH:MM:SSZ
```

실제 컬럼 `is_required`·`seq`·`effective_at`으로 한 트랜잭션에 등록한다. 필수는 AGE_OVER_14·SERVICE_TERMS·PRIVACY_COLLECTION, 선택은 MICROPHONE_NOTICE다. 기기 권한을 가입 필수 동의로 만들지 않는다. PRIVACY_COLLECTION은 처리방침 전체와 구분한 필수 회원정보 수집 동의 본문을 가리킨다.

동일 버전이 이미 있으면 기존 동의 이력을 덮어쓰거나 재생성하지 않고 중단한다. 등록 후 결과 조회가 불명확하면 기존 행을 먼저 읽고, 재실행으로 새 버전을 임의 생성하지 않는다. 운영 GET /api/terms와 앱의 필수·선택 체크·가입 흐름을 확인한다.

## 검사

`python -m unittest discover -s infra/terms -p 'test_*.py' -v`로 문서 변조·리다이렉트·SPA 거절 및 필수·선택 구분을 검사한다. 실제 DB 등록은 공개 검증·최종 본문 검토 후 수행한다. 이 검사는 법률 검토나 Play 심사 통과를 보장하지 않는다.

운영 DB 이름은 backend 컨테이너의 실제 DB_URL에서 제한된 식별자로 추출한다. MySQL 컨테이너의 MYSQL_DATABASE는 초기 이름이 남아 있을 수 있어 사용하지 않는다. 한글 본문 제목의 저장·조회에는 --default-character-set=utf8mb4를 명시한다.
