# R2 원본 저장소 실행 검증

- 검증일: 2026-10-29 KST. [Task 272](https://ssafy.atlassian.net/browse/S15P31E102-272), [계획·후속 조치](../../plans/S15P31E102-272.md).
- 기준: [PRA-02 원본 저장소·보관](../../specs/recording-file.md#원본-저장소), [실행 안내](../../../backend/README.md#녹음-업로드와-원본-저장소pra-02).
- 코드와 운영 JAR: `2bd9d49563ecfc72df2016c3412d48ab71914e79`, edo1 `/opt/eolssu/releases/<SHA>/app.jar`, `eolssu-runtime-backend-1`. Java 21.0.12.1.

## 결론

**최초 검증에서 버킷명 오류·30일 삭제 규칙 누락을 발견했고, 사용자 승인 후 운영에 수정했다.** 현재 운영 설정으로 배포 JAR의 쓰기·서명 읽기·짧은 URL 만료·삭제 후 접근 거부를 확인했다. 공개 접근 차단도 확인했다. 실제 900초 만료와 테스트 객체 정리까지 통과했다. 30일 자동 삭제 실행은 장기 관찰 전이라 미검증이다.

다음 표는 **수정 전** 관찰이다. 수정 후 결과는 아래 절을 따른다.

| 대상 | 확인 결과 |
| --- | --- |
| 운영 `RECORDING_STORAGE_BUCKET` | `eolssu-backend-recordings` |
| Cloudflare 실제 버킷 | `eolssu-recordings` (APAC, Standard) |
| R2 토큰 목록 | 이름 `eolssu-backend-recordings`, 적용 버킷 `eolssu-recordings`, Object Read & Write, Active |
| 버킷명 오류의 근거 | 서버가 토큰 이름을 버킷명으로 사용한다. 해당 설정으로 PUT·DELETE 403, 실제 버킷명만 대체하면 PUT·GET 성공 |
| 서버 설정 주입 | R2 변수 5개 존재, 앞뒤 공백·미치환 변수 없음, 컨테이너 값과 서버 `.env` 일치 |
| 공개 접근 | Public Access Disabled, Public Development URL disabled, 사용자 도메인 없음 |
| 수명 주기 | `Default Multipart Abort Rule`만 Enabled, 전체 접두사에 미완료 multipart를 7일 후 중단. `recordings/` 객체를 30일 후 삭제하는 규칙 없음 |
| 삭제 방지 잠금 | Bucket Lock Rules 없음 |

토큰 목록에서 비밀값·토큰 ID는 열거나 복사하지 않았다. 저장소 키는 검증 프로세스 안에서 인증에만 사용하며 출력·문서·명령 인자에 넣지 않았다.

## 수정 전 실제 실행 결과

2026-10-29 13:12~13:13, **운영 설정 그대로** 배포 JAR의 `S3RecordingStorage.create` → `put` 호출은 `RecordingStorageException` → `AccessDeniedException`, HTTP 403 `AccessDenied`였다. 동일 키의 DELETE도 403이었다. 버킷 수명 주기 조회는 객체용 권한으로 403이므로 대시보드에서 확인했다. 이 403만으로 수명 주기 규칙 유무를 판단하지 않았다.

이후 사용자가 승인한 실제 버킷의 합성 객체를 사용했다. 검증 프로세스의 버킷명만 `eolssu-recordings`로 대체했고 서버 파일·운영 컨테이너·정책·DB는 바꾸지 않았다. 요청 재시도는 1회로 제한했다.

- 객체: `recordings/verification/S15P31E102-272/945b721f-3ff3-48d7-87b9-5e73c35b48dd.m4a`.
- 입력: `S15P31E102-272 synthetic storage verification`과 줄바꿈으로 된 46바이트 합성 파일. 미디어 디코딩 검사가 아닌 저장소 검사이므로 실제 녹음은 쓰지 않았다.
- 서명: 배포 JAR의 `RECORDING_READ_URL_TTL` 상수(900초)와 `readUrl` 사용. 13:14:49 서명, 13:29:49 만료.
- 클라이언트: Python `urllib.request`, 추가 인증 헤더 없음. SDK·키는 서버 프로세스 안에서만 사용.

| 검사 | 판정 | 실행 결과 |
| --- | --- | --- |
| 배포 저장소 구현의 PUT | 통과(버킷명 대체 조건) | 예외 없이 성공 |
| 15분 GET 서명으로 읽기 | 통과(동일 조건) | 13:14:50 HTTP 200, 본문 바이트 일치, Content-Type audio/mp4 |
| 무서명 S3 API GET | 통과 | HTTP 400 InvalidArgument, 객체 본문 미반환. 403이라고 기록하지 않음 |
| GET 서명을 PUT에 재사용 | 통과 | HTTP 403 SignatureDoesNotMatch |
| 서명 URL의 객체 키 변경 | 통과 | HTTP 403 SignatureDoesNotMatch |
| 짧은 만료의 실제 거부 | 통과 | TTL 3초, 5초 뒤 HTTP 403 ExpiredRequest |
| 짧은 만료의 양성 대조 | 통과 | 같은 객체의 900초 URL은 계속 200·본문 일치 |
| 실제 900초 경과 후 거부 | 통과 | 13:29:52 HTTP 403 ExpiredRequest. 같은 객체의 새 서명 URL은 200·본문 일치 |
| 삭제·삭제 후 접근·정리 | 통과 | 13:29:55 JAR delete 성공, GET 404 NoSuchKey·HEAD 404, finally DELETE 성공 |
| 업로드 객체의 만료 헤더 | 실패(규칙 누락과 일치) | GET 응답에 x-amz-expiration 없음. 규칙 부재의 직접 근거는 대시보드 |
| 30일 자동 삭제 실행 | 미검증 | 규칙도 없으며 이번 단기 검사로 30일 후 실행을 증명하지 않음 |
| backend·AI 최근 로그 노출 | 관찰 범위 내 일치 없음 | 13:12경 최근 15분 로그의 X-Amz-Signature·X-Amz-Credential·audioUrl 일치 각각 0. 전체 기간 보장은 아님 |

## 승인 후 운영 수정·재검증

- 13:19경 서버 `.env`의 `RECORDING_STORAGE_BUCKET` 한 줄을 `eolssu-recordings`로 정정했다. 백업은 `/opt/eolssu/.env.before-r2-272-20261029T041912Z`, root 전용 0600이다. 다른 비밀값은 보존했다.
- 배포 lock `/run/lock/eolssu-backend-deploy.lock`을 획득한 뒤 `docker compose --env-file /opt/eolssu/.env -f /opt/eolssu/config/compose.yml config --quiet`와 `up -d --no-deps --force-recreate --pull never backend`를 실행했다.
- 컨테이너 ID 대조로 backend만 재생성되고 frontend·mysql·ai는 재생성되지 않았음을 확인했다. 운영 JAR·이미지·DB·볼륨 변경 없음. 재생성된 backend의 버킷명 정정 확인, health healthy, 공개 HTTPS readiness 200·terms 200.
- Cloudflare Settings에서 `recordings-expire-after-30-days`, 접두사 `recordings/`, Delete objects after 30 day(s), Enabled를 추가했다. 저장 후 목록에 해당 행과 기존 7일 multipart 중단 규칙이 각각 Enabled임을 확인했다. 공개 개발 URL disabled·사용자 도메인 없음 유지.
- 새 객체 `recordings/verification/S15P31E102-272/c299418f-ec26-47cf-a807-8412b7efa4c9.m4a`는 수정된 운영 컨테이너 설정을 대체 없이 사용했다. 배포 JAR PUT·900초 서명 GET 200·46바이트 본문 일치.
- 이 객체는 `x-amz-expiration: expiry-date="Sat, 28 Nov 2026 04:19:48 GMT", rule-id="recordings-expire-after-30-days"`를 반환했다. 업로드 후 30일 만료 메타데이터 적용은 통과, 실제 자동 삭제 실행은 미검증이다.
- 무서명 GET 400 InvalidArgument, GET 서명으로 PUT·키 변조 403 SignatureDoesNotMatch. TTL 3초 URL은 5초 뒤 403 ExpiredRequest, 같은 객체의 900초 URL은 200.
- 13:19:57 배포 JAR delete 성공. 삭제 전에 성공했던 새 URL로 GET 404 NoSuchKey, 인증 HEAD 404. finally의 멱등 DELETE도 성공해 해당 객체 정리 확인.
- 13:14:49에 시작한 기존 URL은 13:29:52(900초 경과 후)에 403 ExpiredRequest. 같은 객체를 새로 서명하면 200·본문 일치여서 만료 거부임을 확인했다. 13:29:55 JAR delete 성공, 새 URL GET 404 NoSuchKey·HEAD 404, finally DELETE 성공. 생성에 성공한 합성 객체 2개 모두 정리했다.

## 재현 경계와 안전한 확인 방법

1. 배포 스냅샷은 컨테이너의 `/app/app.jar` bind mount 원본으로 확인한다. 실제 환경변수는 출력하지 않고 필수값 존재·공백·서버 파일 일치 여부만 기록한다.
2. 운영 JAR의 `BOOT-INF/classes`와 `BOOT-INF/lib/*.jar`를 임시 디렉터리에 풀고 별도 Java 프로세스에서 다음 공개 인터페이스를 호출했다. 이 검증 프로세스 자체는 서비스를 재기동하거나 새 API·테스트용 Bean을 등록하지 않았다. 승인된 환경 정정 과정에서는 backend만 별도로 재생성했다.

```java
var storage = S3RecordingStorage.Companion.create(properties, 1, Duration.ofSeconds(40));
storage.put(ownedKey, syntheticFile);
var url = storage.readUrl(ownedKey, RecordingUploadKt.getRECORDING_READ_URL_TTL());
// URL은 메모리에만 보관하고 외부 인증 헤더 없이 GET한다.
// X-Amz-Date + X-Amz-Expires 이후 거부와 새 URL의 성공을 각각 확인한다.
storage.delete(ownedKey);
storage.close();
```

3. 검사 URL·SDK 오류 전체 메시지는 출력하지 않는다. 상태·S3 오류 코드·본문 일치 여부만 남긴다. 환경변수는 메모리에서 자식 프로세스로 전달하며 명령 인자로 넣지 않는다. 임시 클래스·파일은 종료 시 제거한다.
4. Cloudflare R2 > `eolssu-recordings` > Settings에서 Custom Domains, Public Development URL, Object Lifecycle Rules, Bucket Lock Rules를 읽는다. R2 > Manage API Tokens 목록에서 이름과 적용 버킷을 혼동하지 않는다.
5. 실제 만료는 서버·클라이언트 시계를 바꾸지 않고 기다려 확인한다. 삭제 전 새 서명 URL로 객체 존재를 입증하고, 삭제 뒤 같은 URL의 NoSuchKey·인증 HEAD 404를 확인한다. 자신이 만든 정확한 키만 정리한다.

기본 소스의 단위 테스트는 로컬 HTTP 대역을 사용하므로 실제 R2 연결을 증명하지 않는다. 이번 작업은 운영 JAR와 실제 R2의 저장소 인터페이스를 검사하며 앱 HTTP 업로드·DB 기록·AI 소비의 통합 성공을 주장하지 않는다. 앱 코드·이미지 변경이 없어 전체 앱 빌드·전체 Compose 기동은 수행하지 않았다. 승인된 설정 반영에 필요한 backend만 재생성했다.

## 프로젝트 적용과 미확인 사항

- 버킷명 정정·환경 재주입과 저장소 인터페이스 재검증은 완료했다. 실제 앱 업로드 API·DB·AI 소비의 통합 흐름은 별도 통합 검증 범위다. readiness만 성공해도 저장소 사용 가능 판정이 되지 않는다.
- 기존 multipart 중단 규칙을 보존하고 `recordings/` 접두사·Enabled·생성 후 30일 Expiration 규칙을 추가했다. 이는 저장 등급 전환이나 multipart 중단과 다른 동작이다.
- `UploadRecording.deleteOwnObject`는 삭제 실패 시 수명 주기 규칙에 의존한다. 수정 전에는 규칙 누락으로 고아 객체의 30일 정리가 보장되지 않았다. 수정 뒤에는 접두사가 일치하는 객체에 30일 만료가 적용된다.
- 탈퇴 배치 `WithdrawalPurgeConfig`는 같은 `storage::delete`를 사용한다. 해당 키가 있는 회원의 삭제 호출이 실패하면 `WithdrawalPurgeService`가 DB 삭제를 미룬다. 실제 회원 탈퇴·DB 삭제는 이 검사에서 실행하지 않았다.
- r2.dev와 사용자 도메인은 독립된 공개 경로이므로 모두 확인한다. S3 API의 무서명 접근 거부만으로 다른 공개 경로 차단을 증명하지 않는다.
- 추가한 정책과 새 합성 객체의 만료 메타데이터는 확인했다. 실제 30일 자동 삭제 실행은 이후에 별도로 관찰한다. 객체 생성(업로드) 시각 기준이며 DB created_at 기준이 아니다.

## 버킷 CORS 정책 (S15P31E102-421)

- 작업: [S15P31E102-421](https://ssafy.atlassian.net/browse/S15P31E102-421), 2026-11-03 KST.
- 증상: Swagger에서 `GET /api/songs/{songId}/model-audio/play`가 `Failed to fetch`였다. 같은 출처 API는 302로 R2 서명 URL에 이동하고, Swagger의 `fetch`가 리다이렉트를 따라간다. R2 응답에 `Access-Control-Allow-Origin`이 없어 브라우저가 응답을 막았다. Spring CORS 설정과 무관하다.
- 앱 영향: 웹 앱은 `<audio>`의 `src`로만 재생하고 `crossOrigin`·`fetch`·`decodeAudioData`를 쓰지 않으므로 CORS 없이 재생된다. 정책은 Swagger 점검과 이후 파형·분석 같은 JS 읽기를 위한 것이다.

### 선택

302 계약은 유지하고, 서명 URL을 JSON으로 반환하는 API 변경은 하지 않는다. 재생마다 서버가 게시 상태와 현재 악보를 확인하고, 클라이언트는 만료를 다루지 않는다. Swagger 편의만으로 공유 계약을 바꿀 근거가 부족하다.

버킷에 다음 정책을 둔다.

```json
[
  {
    "AllowedOrigins": ["https://k15e102.p.ssafy.io", "http://localhost:8080"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["Content-Length", "Content-Type", "Content-Range", "Accept-Ranges"],
    "MaxAgeSeconds": 3600
  }
]
```

- `http://localhost:8080`은 [웹 개발 서버](../../../frontend/README.md) 기본 주소다. 개발·스테이징 주소가 늘면 목록에 추가하고 `*`는 쓰지 않는다.
- 탐색(seek)의 Range 요청을 위해 `Content-Range`·`Accept-Ranges`를 노출한다. 쓰기 메서드는 허용하지 않는다.
- R2 CORS는 접두사별로 나눌 수 없어 `recordings/`·`reference-audio/`·`model-audio/` 전체에 적용된다. CORS는 접근 권한이 아니라 허용 출처의 JS가 응답을 읽게 할 뿐이며, 보호는 서명과 900초 TTL이 맡는다. 서명 URL은 CORS와 무관하게 curl로도 받을 수 있으므로 위험 증가는 작다고 판단했다.
- 서버 R2 키는 객체 권한만 있어 버킷 설정을 바꿀 수 없다. 대시보드 R2 > `eolssu-recordings` > Settings > CORS Policy에서 적용·확인한다.

### 적용 전 관찰

운영 `/api/songs/5/model-audio/play`의 302 `Location`(버킷 `eolssu-recordings`, `model-audio/` 객체)에 대해 curl로 확인했다. 두 출처 모두 preflight(`OPTIONS`, `Access-Control-Request-Method: GET`, `Access-Control-Request-Headers: range`)는 403이었다. `Origin`과 `Range: bytes=0-1`을 붙인 GET은 206·`Content-Range: bytes 0-1/554262`였고 `Access-Control-*` 헤더가 없었다.

### 적용과 검증

2026-11-03 15:5x KST(15:55 직전), 대시보드 CORS Policy(기존 정책 없음)에 위 JSON을 저장했다. 저장 후 화면에서 허용 출처 2개, GET·HEAD, 허용 헤더 `*`를 확인했다. 15:55 KST 새 302 `Location`으로 다시 확인한 결과는 다음과 같다.

| 검사 | 결과 |
| --- | --- |
| 운영·`localhost:8080` 출처 preflight(GET, `range`) | 각각 204, 요청 출처 그대로의 `Access-Control-Allow-Origin`, `Allow-Methods: GET, HEAD`, `Max-Age: 3600` |
| 두 출처의 Range GET | 206, 출처별 `Access-Control-Allow-Origin`, `Expose-Headers: Content-Length,Content-Type,Content-Range,Accept-Ranges` |
| 비허용 출처 `https://evil.example` | preflight 403, GET 206이지만 `Access-Control-*` 없음(브라우저가 응답 읽기를 막음) |
| 운영 출처의 PUT preflight | 403 |
| 운영 Swagger 페이지 안의 `fetch('/api/songs/5/model-audio/play', {Range})` | 리다이렉트 후 R2 호스트에서 206·`audio/mp4`·`bytes 0-1/554262`, CORS 오류 없음 |

서명 URL은 기록하지 않았다. Swagger UI의 "Try it out" 버튼 조작 자체는 하지 않았고 같은 출처의 `fetch`로 대신 확인했다.

같은 Settings 화면의 Object Lifecycle Rules에는 `Default Multipart Abort Rule`만 있었다. 위에서 추가한 `recordings-expire-after-30-days` 규칙은 팀이 의도적으로 없앤 것이며(2026-11-03 사용자 확인) 복구하지 않는다. 이번 작업에서 수명 주기 설정은 바꾸지 않았다.

### 남은 15분 만료 위험

`<audio>`가 리다이렉트된 서명 URL로 이후 Range 요청을 보내면, 15분 넘게 연 화면에서 탐색할 때 403이 날 수 있다. 302·JSON 방식 모두의 문제이며 CORS와 별개다. 이번에는 확인하지 않았다.

## 출처

- [Cloudflare: Presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/): 서명은 객체·메서드·기간에 한정되고 S3 API 도메인에서 사용한다.
- [Cloudflare: Public buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/): r2.dev와 사용자 도메인 공개 경로는 각각 설정한다.
- [Cloudflare: Configure CORS](https://developers.cloudflare.com/r2/buckets/cors/): 버킷 단위 CORS 규칙 설정 방법.
- [Cloudflare: Object lifecycles](https://developers.cloudflare.com/r2/buckets/object-lifecycles/): 접두사별 보관 규칙, 기본 multipart 중단 규칙, 만료 시각 뒤 통상 24시간 이내 제거. 실제 제거 시점은 30일 정각으로 보장하지 않는다.
- 프로젝트의 직접 관찰: 운영 JAR·컨테이너 설정, Cloudflare 버킷 Settings·토큰 목록, 위 합성 객체 실행 결과. 공식 문서의 일반 설명과 운영 검증 결과를 구분했다.
