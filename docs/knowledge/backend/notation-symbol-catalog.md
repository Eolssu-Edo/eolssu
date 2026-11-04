# 기존 FE 정간보 기호와 CAT-09 연결

작업: S15P31E102-410. 자료 기준: 2026-11-03, develop `05d1bb76`의 FE 기호 자료. 최신 develop `a7a48b73` 통합 후에도 기호 원문은 동일하다. [계약](../../specs/notation-symbol-catalog.md).

## 자료와 등록 단위

`frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/Sigimsae.kt`의 Basic 11항목은 요성·농음의 두 그림을 포함해12개, More는35개다. 그림별47행과 `ScoreNote.Hold/Rest`의 `ㅡ`·`△`를 합쳐49행이다. 요성표와 농음표는 서로 다른 그림이며 FE의 공통 이름·설명(요성·농음)을 보존했다. SVG로만 표현하는 기호의 symbol에는 `core/ui/score/ScoreSigimsae.kt`에서 이미 쓰는 한글 토큰을 사용한다. 새 유니코드 기호를 추측하지 않는다.

쉼표·연장 문구는 `values/strings.xml`의 `guide_reading_length_cap_hold/rest`에서 가져왔다. 시김새 설명은 기존 FE 문구를 그대로 사용한다. `backend/tools/sync_notation_symbols.py --check`는 설명·그림·마이그레이션 스냅샷을 원본과 대조하며 VectorDrawable의 translate/scale, path, evenOdd를 보존한다. 기호 이름·설명은 악보 이벤트나 채점 주법의 정의로 사용하지 않는다.

## 출처·이용 조건

그림47개는 기존 FE의 우물사이 MIT 고지를 SVG metadata와 서버 번들 LICENSE.txt에 포함했다. 기존 [문헌 대조 기록](../domain/sigimsae-symbols.md)의 근거를 이어서 사용했으며 이번 작업에서 외부 원문을 재검수하지 않았다. '더 많은 시김새'35개 중 노라느니 하나는 INFERRED, 나머지34개는 DOCUMENTED다. Basic과 쉼표·연장은 교육용 표현(EDUCATIONAL)이다. DOCUMENTED도 전공자의 최종 승인을 뜻하지 않는다.

동일한 symbol을 가진 기존 DB 행은 V19에서 덮어쓰지 않는다. 전체 값이 번들 원문과 일치하는 행만 출처·검수 상태·번들 이미지를 연결한다. 외부 이미지 URL이 있거나 설명·이름이 달라진 행은 기존 값을 그대로 반환하며 번들 출처와 이미지 API를 붙이지 않는다. 전각/반각 기호는 binary 비교로 구분한다.

## 검증

폐기 가능한 MySQL 8.4 컨테이너 `eolssu-symbol-410-db`, 루프백13350을 사용한다. develop 통합 후에는 `eolssu_410_latest` 새 DB를 사용하며 이미 적용된 시험용 V17 이력을 고치지 않았다. 최초 V17로 준비한 기호 시드는 먼저 병합된 악기 V17·운지 V18과 충돌해 V19로 이동했다. 콘텐츠·API 의미 변경은 없다. 운영·공유 개발 DB를 사용하지 않았다. JDK21, 프로젝트 Gradle Wrapper로 검사한다.

- 변경 전: 실제 DB/HTTP에서 등록 개수49 기대에0이 반환되어 실패. FE에서는 같은 항목의 상대 이미지 경로를 기존 검사에서 거부하여 실패했다.
- 핵심 검사: V19 적용 뒤 실제 GET 사전49개·SVG49개·MIT 고지·미등록/잘못된 ID·만료 쿠키 공개 접근 확인. FE 저장소의 기존 HTTPS 링크와 정확한 ID 상대 경로 처리 검사 통과.
- 최신 develop `a7a48b73` 통합 후 `backend/gradlew.bat clean build databaseTest --max-workers=1` 통과. 기본 테스트538개(실패·오류·skip0), DB214개(실패·오류0, 기존 외부 코퍼스 검사1개 skip). V1~V19의 실제 적용·재실행 무변경과 운지/곡 필터 회귀를 포함한다. 결과는 `backend/build/reports/tests/test/`와 `databaseTest/`, 로컬 로그 `backend/build-410-integrated.log`에 있다.
- 실행 JAR(`eolssu-0.0.1-SNAPSHOT.jar`)에서도 사전49개·SVG49개와47개 그림의 MIT 고지가 모두 포함됐음을 별도로 확인했다.
- 통합 후 FE 검사: Android680개(실패·오류0, 기존 선택 실행 HTTP 검사3개 skip), JS·Wasm 각각662개(실패·오류·skip0) 통과. 기존 HTTPS 링크, 정확한 ID의 상대 이미지 경로만 API 원점에 연결, 출처·추정 상태 보존과 잘못된 상대 경로 거절을 포함한다.
- `:webApp:wasmJsBrowserDistribution`도 통과했다. FE의 검증 명령은 `gradlew.bat :shared:testAndroidHostTest :shared:jsTest :shared:wasmJsTest :webApp:wasmJsBrowserDistribution --max-workers=1 --no-daemon -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx8192m -Dfile.encoding=UTF-8"`이며 Chrome을 `CHROME_BIN`으로 지정했다. 최초 3GB Kotlin daemon의 OOM 후 실행 옵션만 변경했으며 저장소의 메모리·최적화 설정과 테스트 기준은 그대로다. 최종 실행은13분5초, 로컬 로그는 `frontend/build-410-web-integrated.log`, 배포 파일은 `frontend/webApp/build/dist/wasmJs/productionExecutable/`에 있다. 운영에는 배포하지 않았다.
- 전체 DB 검사의 여러 Spring 컨텍스트가 연결151개 한도에 도달해 최초 시도가 실패했다. 이 폐기용 MySQL의 `max_connections`만512로 조정한 뒤 성공했다. 운영 설정·제품 코드·테스트 기준은 변경하지 않았다. 동시 빌드 부하에서 기존 메트릭 검사 한 건도 시간 초과됐으나, 단독 실행의 전체 검사에서는 성공했다.
- Windows에서 iOS 빌드·실기기 화면은 검증하지 않았다. 자체 점검은 독립 검토와 구분한다.

## MR 준비 중 develop 동기화

사용자의 후속 MR 요청으로 `develop=c7ef0bd5`를 통합했다. 텍스트 충돌 없이 곡 목록의 악보 본문 제외, HOME 전환, Android 외부 브라우저, Nginx JSON gzip 변경을 보존했다. 기호 콘텐츠와 API 의미는 그대로이며 V19를 유지한다.

- BE `build databaseTest --max-workers=1`: 기본538개(실패·오류·skip0), DB214개(실패·오류0, 기존 코퍼스1 skip) 통과. 로컬 로그 `backend/build-410-mr.log`.
- FE Android684개(기존 선택 실행3 skip), JS·Wasm 각각665개(실패·오류·skip0) 통과. 앞서 기록한 동일8GB 실행 옵션을 사용했다.
- `RUN_NGINX_TESTS=1 python -B -m unittest discover -s infra/scripts/tests -p test_nginx_actuator.py -v`: 실제 격리 Nginx6개 통과. JSON gzip·미디어 비압축·공개 API 경로를 확인했고 임시 컨테이너와 fixture를 정리했다.
- 동일 FE 명령의 `:webApp:wasmJsBrowserDistribution`까지 통과(14분11초)했다. 로컬 로그는 `frontend/build-410-web-mr.log`이며 기호 원본 대조49개도 다시 통과했다. 독립 검토·iOS·실기기 확인과 운영 적용은 이 MR 준비의 완료 주장에 포함하지 않는다.
