# 약관 공개·동의·출시 검증

- Task: S15P31E102-300 / Story: S15P31E102-22.
- 확인일: 2026-10-30. 운영 변경은 [작업 기록](../../plans/S15P31E102-300.md)을 따른다.

## 운영 공개본

> S15P31E102-333: R2 복귀 후 현행 버전은 `2026-11-02`이다. 아래 표의 `2026-10-30` 주소는 운영이 R2(s3)로 바뀐 뒤 이전 버전 이력으로 남는다. 앱 마이페이지와 Play Console 처리방침 URL은 실제 저장소에 맞는 현행 버전을 보여 주는 `.../current/privacy`를 쓴다. [운영 절차](../../../infra/storage/README.md#r2-복귀-s15p31e102-333).

| 용도 | 공개 주소 |
| --- | --- |
| 개인정보 처리방침 / Play Console 개인정보처리방침 URL | https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/privacy |
| 앱 외 계정·데이터 삭제 요청 / Play Console 삭제 URL | https://k15e102.p.ssafy.io/api/terms/documents/account-deletion |
| 이용약관 | https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/service |
| 별도 개인정보 수집·이용 동의 | https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/collection |
| 접근권한 안내 | https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/permissions |
| 연령 확인 | https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/age |

운영 API에는 필수 AGE_OVER_14·SERVICE_TERMS·PRIVACY_COLLECTION과 선택 MICROPHONE_NOTICE가 등록돼 있다. 접근권한 안내 선택과 OS 마이크 허용은 서로 다르며, 둘 다 가입의 필수 조건으로 만들지 않는다. 등록된 버전의 문서·ID·기존 동의 이력을 덮어쓰거나 재생성하지 않는다.

`python infra/terms/publish.py --verify-live`는 문서 6개의 원문 해시와 API의 정확한 한글 제목·필수 여부·버전·URL·중복 ID를 조회만 한다. 빈 목록, HTML로 위장한 SPA, 리다이렉트 및 잘못된 문자셋의 제목을 정상으로 보지 않는다.

## 동의와 오류 처리

- 최초 체크는 해제 상태다. 필수 항목을 모두 선택해야 진행한다. 선택 항목 거절은 그대로 서버에 전송·기록한다.
- 약관 재조회는 이전 체크와 가입 초안의 동의를 비우며, 새 버전에는 다시 동의해야 한다.
- 서버는 요청한 ID·버전과 시행 중인 약관을 대조한다. 필수 누락·거절, 구버전 및 존재하지 않는 ID는 가입을 거절한다. 중복 ID는 잘못된 요청이다.
- 필수 약관 자체가 설정되지 않았다면 서비스 불가로 처리하며, 회원·동의·로그인 토큰을 만들거나 이메일 인증 토큰을 소비하지 않는다. 설정 복구 후 같은 미소비 인증으로 정상 요청을 재시도할 수 있다.
- 약관 조회 실패는 약관 전용 문구와 재시도를 제공한다. 문서 열기 실패와 서버 조회 실패를 구분한다.
- 가입 후 마이페이지에서도 이용약관·개인정보 처리방침·계정 및 데이터 삭제 안내를 외부 브라우저로 연다. 계정 토큰을 문서 URL에 붙이지 않는다.

## 출시 대조 근거

Google Play의 [User Data 정책](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)은 스토어와 앱 안의 개인정보 처리방침 접근, 실제 수집·처리의 정확한 공개를 요구한다. [계정 삭제 안내](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)는 앱 안의 삭제 경로와 앱 밖에서 삭제를 요청할 수 있는 공개 웹 경로를 구분한다. 현재 앱의 마이페이지 탈퇴와 공개 이메일 삭제 요청 안내를 함께 제공한다.

Play Console의 실제 스토어 개발자 표기·Data safety 응답·배포할 번들은 별도로 대조해야 한다. 이 검증은 심사 통과 판정이 아니다. 실제 제공하지 않는 카메라·PDF 변환·녹음 다시 듣기를 완료 기능으로 기재하지 않는다.

## 자동 검사와 기기 확인의 경계

backend `clean build`, 프론트 `:shared:jsTest`, 약관 등록 도구 단위 검사와 공개 API 대조를 사용한다. 별도 MySQL에서 가입 API와 동의 저장을 검사할 때는 운영 DB와 실제 메일을 쓰지 않는다.

Android SDK·실제 기기 검증 환경을 확보하지 못한 이번 실행에서는 Android 설치·클릭·브라우저 복귀를 검증 완료로 기록하지 않는다. 배포할 기본 APK에서 필수만 선택해 가입, 선택 미동의, 본문 보기 및 복귀, 마이크 거부 후 로그인, 마이페이지 문서 링크·탈퇴를 확인한다. Task 309의 약관 우회 QA APK는 이 확인에 사용하지 않는다. release는 우회 플래그가 false인 기존 보호를 유지한다.

2026-10-30 보강 검사 결과: backend `clean build` 405개, 프론트 `:shared:jsTest`의 ChromeHeadless 브라우저 검사 515개, 약관 도구 4개가 실패 없이 통과했다. 별도 MySQL 8.4.13의 `SignUpApiDatabaseTests` 3개도 통과했으며, 출시 조합에서 수집 동의 누락 거절·인증 토큰 미소비·선택 접근권한 거절 후 가입 및 동의 4건 저장을 확인했다. 테스트용 메일 수신기를 사용했고 검사 후 작업 전용 MySQL 컨테이너와 볼륨을 제거했다.
