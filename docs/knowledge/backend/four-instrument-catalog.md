# 4악기 카탈로그·회원 선택 정렬 검증

2026-11-03, S15P31E102-407 / Story24. [계약](../../specs/four-instrument-catalog.md). 기준 develop `3d1521df`에서 구현했다. 사용자 후속 선택으로 회원가입·마이페이지의 일반 대금도 정악·산조를 나누었다. 최초 구현은 로컬까지였으며 이후 MR 준비 요청과 별도 승인으로 [운영 Flyway V17](../infra/four-instrument-ops-migration.md)을 적용했다. 앱 배포·MR 병합은 실행하지 않았다.

## 결과와 보존

- V17은 FE 운지표에 필요한 명목상 율명36개를 구성한다. 기존22개는 ID·한자·반음을 유지하고14개만 보충한다. 별도 적재로 이미 있는 같은 (yul,octave,semitone)은 그대로 재사용한다. 다른 음의 충돌은 영구 쓰기 전에 거부한다.
- 단소 음역 하한은 협(3)까지 넓힌다. 소금은 [7,33], 두 대금은 [-5,29]를 최소 탐색 범위로 하며 기존 더 넓은 범위는 축소하지 않는다. 새 DB의 조회 수는 단소26·소금24·정악34·산조34다. 모든 반음을 서비스 운지나 채점 정답으로 승인한 의미는 아니다.
- 기존 3악기 행은 ID·이름·허용 오차를 보존하며 활성화한다. 없는 행만 등록한다. 다른 code의 비활성 상태는 유지한다.
- FE 회원 모델은 명시적 serverCode로 실제 ID를 왕복한다. 기존 로컬 Daegeum을 종류 판정에 쓰지 않는다. 운지 한자 이체자는 기본 율명·옥타브로 대응하므로 DB의 기존 한자를 수정할 필요가 없다.
- 가입·마이페이지 선택은 긴 악기명도 읽을 수 있도록 2열이며, 정악·산조의 기존 학습 진도 저장 키는 유지하고 각 종류의 단계만 집계한다.
- 기존 적재 도구는 이미 공개되고 범위가 확장된 악기를 재사용하되 곡·음원 상태와 파일·해시 충돌 검사는 유지한다.

## 실행 근거

- 변경 전 실제 MySQL의 네 악기 공개 검사가 실패했다(기존 단소만 존재). FE에서 대금2종 조회·이체자 검사2개와 회원 선호 종류 왕복 검사1개의 실패를 각각 확인한 뒤 수정했다.
- MySQL8.4.13 전용 Docker 컨테이너 `eolssu-catalog-407-db`, loopback13347. 테스트 전용 공개 비밀번호만 사용했으며 운영 접속 정보는 사용하지 않았다.
- V16 DB에 기존 비활성 소금2·정악3·산조4를 넣고 V17을 적용한 실제 HTTP 검사8개가 통과했다. 이후 ID2/3/4 보존·활성 및 각 음역, 총36음의 DB readback을 확인했다.
- 별도 빈 DB `catalog407_fresh`에서 전체 마이그레이션·HTTP·DB 회귀를 실행했다. backend `clean build`의 단위534개 통과, 최종 `databaseTest`는208개 중207개 통과·외부107곡 corpus 조건1개 생략이다. 로컬 JUnit 보고서에 보존한다. Flyway 재실행0개·validate와 기존 곡4개·원래 pitch1~11 보존을 검사한다.
- 전체 DB 첫 실행은 V17에 따른 기존 기대값2개와 테스트 MySQL의 기본 연결 한도151로 실패했다. 기존 기대값을 실제 새 계약으로 바꾸고 해당2개를 재검증했다. MySQL Max_used_connections=152를 관측한 뒤 폐기용 DB의 max_connections만512로 조정해 전체 DB 검사를 통과했다. 앱의 pool 설정·검사 임계값은 변경하지 않았다.
- FE `:shared:testAndroidHostTest` 전체623개 중620개 통과·기존 선택 검사3개 생략. `:webApp:compileKotlinJs :webApp:compileKotlinWasmJs` 통과. 가입·내 정보 실제 코드/ID 구분, 잘못된 ID·지연 응답·로그아웃 무효화 및 독립 학습 진도 검사를 포함한다.
- Python `python -m unittest discover -s infra/catalog -p 'test_*.py'`:78개 중66개 통과·외부 reference-audio MySQL 조건12개 생략. 적재 호환 신규 회귀는 실패→성공을 확인했다.
- MR 준비 시 최신 develop `aee5a4a7` 통합 후 FE host 검사를 다시 실행해648개 중645개 통과·기존 선택 검사3개 생략을 확인했다. JS·Wasm 컴파일과 Android `assembleDebug`도 통과했다. BE source·build 파일은 최초 검증본과 동일하므로 위 BE·DB 검증 결과를 유지한다. 통합 FE 검증은 JDK21에서 `--no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process'`로 실행했다.

명령은 backend/README와 frontend/README의 Wrapper를 사용한다. JDK21, Android SDK, 폐기 MySQL의 TEST_DB_URL/USERNAME/PASSWORD를 설정한다. 결과는 각 `build/reports/tests`·`build/test-results`에 있다.

## 한계와 후속

MR 준비 시 `aee5a4a7` 통합 작업트리 기준으로 BE 마이그레이션/적재와 FE 식별자/회원 선택을 두 별도 검토자가 읽기 전용으로 검토했고 확정된 결함은 없었다. 검토자가 요청한 운영 collation 일치·사전 스냅샷/백업은 실제 적용 절차에서 확인했다. 검토자는 테스트·운영 변경을 직접 실행하지 않았다.

실제 기기에서의 가입·마이페이지 화면 조작과 iOS 빌드·실기기는 미검증이다. 연결된 개인 기기의 계정·앱을 이 작업에서 변경하지 않았다. JS/Wasm은 컴파일 검사이며 브라우저 전체 사용자 흐름 검사로 보고하지 않는다.

공개 운지 콘텐츠·기호·장단과 악기별 곡 필터는409·410·411·408의 후속 업무다. 이번 변경만으로 그 데이터가 채워졌다고 보고하지 않는다.
