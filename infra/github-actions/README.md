# GitHub Actions 운영

[워크플로 목록](../../.github/workflows) · [이전 계획과 검증](../../docs/plans/S15P31E102-1294.md)

## CI와 릴리스

- develop PR과 병합 후 CI 실행의 **CI checks**에서 선택된 테스트 결과를 확인한다. 수동 실행은 전체 검사를 돌린다. 월요일 04시대 KST에는 Python 전 그룹을 실행한다. 기존 격리 Python 검사는 별도 단계에 실패를 표시한다. 기존 Jenkins의 경고 전용 MySQL 검사는 이 구성에서 CI 실패로 표시한다.
- **Release candidate**에 성공한 develop 전체 SHA를 넣으면 기존 후보 검사, 백엔드·미디어 테스트, 릴리스 노트를 실행한다.
- **Runtime image**에 같은 SHA와 서비스를 넣고 승인한다. 성공한 작업의 Summary에서 SHA와 ghcr.io 이미지 digest를 복사한다. 운영 이미지는 GHCR에 보관한다.

## Mac mini에서 운영 이미지 적용

먼저 운영과 분리된 Linux VM에서 재현한다. 기존 배포 도구, 운영 Compose, DB 백업·Flyway 절차, 배포 저널, Nginx 설정을 [운영 절차](../runtime/README.md)에 따라 구성한다. 배포 도구가 쓰는 /opt/app의 origin은 GitHub를 가리켜야 한다. ARM VM에서 OMR은 linux/amd64 에뮬레이션과 실제 악보의 처리 시간을 확인한다. playback 테스트는 합성 자료로 기동을 확인하며, 운영 전 별도 VM에서 실제 CAT·장구 자료로 렌더링을 시험한다.

1. VM 사용자는 승인된 GHCR 이미지에 대한 읽기 권한을 갖는다. 운영 Docker를 실행하는 계정의 자격증명 저장소에 로그인한다.
2. 최초 VM 준비 때에는 **Runtime image**에서 backend-runtime을 빌드하고 승인된 digest를 받아 운영 Compose가 참조하는 eolssu-backend-runtime:21-jre-ffmpeg8.0.1로 태그한다. 이 런타임 이미지 변경은 별도 검증 후 적용한다.
3. deploy-image.sh를 root 소유 0755의 /opt/eolssu/bin/deploy-github-image로 설치한다. 배포할 때는 운영자로 sudo /opt/eolssu/bin/deploy-github-image SERVICE REVISION sha256:DIGEST를 실행한다. SERVICE는 backend, frontend, ai, omr, playback 중 하나다. 이미지 digest·커밋 라벨·아키텍처 확인 뒤 기존 배포 도구가 동작한다.
4. 기존 헬스·복구 검사를 확인한다. 중단됐으면 해당 서비스의 배포 저널을 먼저 조회하고 운영 문서의 복구 절차를 따른다. backend의 DB 변경이 동반된 배포는 기존 백업·스키마 호환 절차를 따른다. GHCR의 적용 digest와 직전 digest를 보관한다.

## 모바일

**Mobile build**를 develop에서 실행한다. 일반 CI의 성공 기록이 있는 전체 SHA와 QA 또는 출시 유형을 고른다. 출시 작업에 버전명과 다음 스토어 빌드 번호를 넣는다. Google Play 업로드와 App Store Connect 업로드는 명시적으로 선택한다. 서명만 검증하려면 signing_check_only를 선택한다. 이 실행은 서명된 파일을 게시하거나 업로드하지 않는다. 이전 업로드 결과가 타임아웃 등으로 불명확하면 콘솔에서 같은 번호를 확인한 뒤 재시도한다.

| GitHub Environment | 필요한 설정 |
| --- | --- |
| android-qa | ANDROID_KEYSTORE_BASE64는 기존 QA폰 앱의 debug.keystore |
| android-release | ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, Play 업로드 시 PLAY_SERVICE_ACCOUNT_JSON |
| ios-qa | ASC_PRIVATE_KEY(p8 원문), ASC_KEY_ID, ASC_ISSUER_ID, IOS_DEVICE_ID 비밀값, IOS_TEAM_ID 변수 |
| ios-release | ASC_PRIVATE_KEY(p8 원문), ASC_KEY_ID, ASC_ISSUER_ID 비밀값, IOS_TEAM_ID 변수 |
| 모바일 환경 공통 | 저장소 변수 MOBILE_ARTIFACT_RECIPIENTS에 운영자의 age 공개 수신자 등록 |

모바일 빌드는 APK·AAB·IPA와 SHA-256 파일을 운영자 공개 키로 암호화한다. Actions에는 암호화한 mobile.tar.age만 올라가며 1일간 보관된다. Actions에서 다운로드한 뒤 아래 예시처럼 복호화하고 SHA-256과 revision.txt를 확인한다. 복호화에 쓰는 개인 키는 운영자에게 보관하고 공개 수신자만 GitHub에 등록한다.

~~~sh
mkdir -p mobile-out
age -d -i "$MOBILE_DECRYPTION_KEY" mobile.tar.age | tar -xf - -C mobile-out
(cd mobile-out && shasum -a 256 -c ./*.sha256)
~~~

QA 배포 담당자는 확인한 파일을 기존 기기와 OTA 게시 도구에 전달한다. APK는 [QA 폰 게시 도구](../qa-phone/publish-apk.py), Ad Hoc IPA는 [IPA 게시 도구](../ios-qa/publish-ipa.py)를 쓴다. 양쪽에 Actions의 Run number와 SHA를 넘긴다. Android는 같은 서명으로 설치본 위에 업데이트하고 iPhone은 등록된 기기에서 설치한다. App Store Connect 업로드 뒤 TestFlight 배포와 심사 제출은 App Store Connect에서 진행한다.

## 권한과 전환

runtime-images와 네 모바일 환경에는 develop만 허용하고 승인자를 지정한다. 보호된 브랜치의 수동 작업만 해당 환경을 사용한다. 일반 CI는 읽기 권한으로 동작한다. 서버 교체는 승인된 digest를 운영자가 VM에서 적용할 때 시작한다.

Jenkins를 끄기 전에 실제 PR·develop CI, 모든 런타임 이미지 발행, 격리 VM 배포와 롤백, QA 기기 설치, Play 내부 트랙·TestFlight 테스트 업로드를 확인한다. 서명이나 업로드 실패가 나면 이전 실행의 산출물과 스토어 상태를 확인한다.
