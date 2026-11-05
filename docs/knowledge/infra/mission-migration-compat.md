# V25와 구 백엔드의 배포 호환성

- 작업: S15P31E102-499 · [스펙](../../specs/mission-migration-compat.md) · [계획](../../plans/S15P31E102-499.md)
- 근거: [공유 대화](https://chatgpt.com/share/6aebe39d-7dec-83ec-99e8-553060f3814e), `infra/jenkins/deploy-backend.py`의 `migration_plan`/`deploy`, 원본 V25, 실제 실행 JAR.
- 재현 환경: 2026-11-05, macOS, OpenJDK 21.0.11, Gradle Wrapper 9.8.2, Python 3.9, base 6aca5df7.

## 원인과 선택

배포 도구는 DB를 바꾼 뒤 실행 JAR을 교체한다. V25는 구 운영 Mapper가 읽는 `mission_id`를 삭제한다. 삭제 승인은 이 순서를 해결하지 못한다. 현재 코드의 Mapper와 Row는 이미 참조를 제거했으므로 실행 JAR에서만 V25를 보류한 호환 릴리스를 먼저 배포한다.

공유 대화의 `processResources.exclude` 대신 `bootJar.exclude`를 쓴다. 전자는 기존 `DatabaseIntegrationTests`가 요구하는 최종 V25 이력까지 테스트 classpath에서 숨기므로 부적합하다. 실행 JAR과 테스트 리소스의 차이는 의도적이며 `bootRun`은 여전히 V25를 적용한다.

임시 V25 보류 중 V26이 적용되면 나중에 V25가 적용 이력보다 오래됐다는 검사에 걸린다. 따라서 V26 이상이 소스에 있으면 호환 bootJar 빌드를 거부한다. 원본 SQL·Flyway 이력·배포 순서·위험 SQL 승인 검사는 바꾸지 않는다. 기존 위험 SQL 목록 테스트가 V14만 기대해 실패하므로 원본 V25도 승인 대상임을 반영했다.

## 관찰한 결과

| 검사 | 결과 |
| --- | --- |
| 수정 전 clean bootJar | 실제 JAR 검사에서 V1~V24 예상과 달리 V25도 포함되어 실패 |
| 수정 후 실제 JAR | V1~V24만 포함, 각 SQL 원본 바이트 일치, Mapper의 mission_id와 Row 클래스의 missionId 없음 |
| 테스트 리소스 | build/resources/main의 V25가 원본과 바이트 일치 |
| 임시 V26__compat_probe.sql | bootJar가 지정 오류로 실패. finally에서 임시 파일 제거 후 clean build |
| 백엔드 build | 594개 중 593 통과·1 skipped, failures/errors 0 |
| Python 배포 회귀 | 50개 통과. 위험 SQL 승인·실패 시 교체 차단·배포 순서 검사 포함 |
| 실행 JAR 무DB 기동 | 임의 로컬 포트의 /api/actuator/health/liveness → 200, status UP. 소유 프로세스 종료 확인 |
| 운영 읽기 전용 inspect | e335ac20891301867129e3a8e1662b833e7e2e83 JAR, running/healthy. 배포·설정 변경 없음 |

초기 백엔드 build의 6개 실패는 macOS `/var` 임시 경로가 `/private/var` 심볼릭 링크인 데서 발생했다. 실패 원인은 `Recording root must not contain symlinks`였다. 보안 검사를 바꾸지 않고 테스트 JVM의 임시 경로를 실제 `/private/tmp`로 지정하자 전체 build가 통과했다. Python HTTP 검사는 sandbox의 로컬 포트 제한 밖에서 실행했다.

원본 V25 SHA-256은 `0e5c476bb63613bb9eb95f3edfccf83620d9a7cadb3f97728c9052799929a9a7`이며 공유 대화와 같다. 이 일치는 운영 데이터 검토나 승인 등록을 뜻하지 않는다.

## 재현

저장소 루트에서 실행한다. DB·외부 서비스는 시작하지 않는다.

```sh
mkdir -p /private/tmp/jira-499-java-tmp
JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp/jira-499-java-tmp ./backend/gradlew -p backend --no-daemon clean build
python3 -B -m unittest discover -s infra/jenkins/tests -v
```

실제 JAR의 SQL과 리소스 보존은 아래처럼 검사한다.

```sh
python3 - <<'PYCODE'
from pathlib import Path
import zipfile
root = Path('backend')
jar = next(p for p in (root / 'build/libs').glob('*.jar') if not p.name.endswith('-plain.jar'))
prefix = 'BOOT-INF/classes/db/migration/'
with zipfile.ZipFile(jar) as z:
    names = [n for n in z.namelist() if n.startswith(prefix) and n.endswith('.sql')]
    assert sorted(int(Path(n).name.split('__')[0][1:]) for n in names) == list(range(1, 25))
    for n in names:
        assert z.read(n) == (root / 'src/main/resources/db/migration' / Path(n).name).read_bytes()
    assert b'mission_id' not in z.read('BOOT-INF/classes/mybatis/practice/PracticeSessionMapper.xml')
    assert b'missionId' not in z.read('BOOT-INF/classes/com/ssafy/eolssu/backend/feature/practice/infrastructure/persistence/PracticeSessionRow.class')
v25 = 'db/migration/V25__drop_practice_session_mission.sql'
assert (root / 'build/resources/main' / v25).read_bytes() == (root / 'src/main/resources' / v25).read_bytes()
print('호환 JAR과 원본 리소스 확인 완료')
PYCODE
```

임시 V26으로 bootJar 실패를 확인할 때는 기존 파일을 덮어쓰지 않고 반드시 정리한 뒤 clean build한다. 이번 실행 로그는 로컬 `/tmp/jira-499-*.log`, JUnit 결과는 `backend/build/test-results/test/`다. 이 임시 로그는 Git 산출물이 아니다.

## 남은 운영 검증

[실행 절차](../../../infra/runtime/README.md#v25-삭제-전-호환-백엔드-배포)의 A 배포 → 실제 JAR·세션 기능 확인 → B 복원·V25 검토/승인·배포 순서가 남았다. 현재 운영 DB 이력·컬럼 값·의존성·백업은 직접 조회하지 않았다. databaseTest·Docker 이미지 빌드·Jenkins 실제 배포도 이번 로컬 검증에는 포함하지 않았다. 소스와 테스트의 DB 의미는 바꾸지 않았다. V25 이후 정상 재배포할 복구 후보에는 새 코드와 원본 V25가 모두 필요하다.


## 2026-11-05 후속 릴리스 B 검증

- A 운영 반영: MR !332, a4abb13b, Jenkins #257 SUCCESS. 운영 JAR V1~V24·미션 참조 없음·readiness UP·DB V24·실패 0·mission_id 유지·MySQL ID/볼륨 보존을 직접 확인했다.
- 사용자 지정 개발용 계정(ID 1)의 실제 HTTPS 검증: 세션144 생성201, 조회200, 같은 키 재전송201/동일ID, 다른 BPM409, 로그아웃200. 비밀번호·쿠키는 메모리에서만 사용했다. 배포 후 기존144 재조회/재전송과 신규세션을 다시 검증한다.
- 삭제 전 데이터: 기존143건의 mission_id는 모두NULL. 인덱스·외래키·뷰·트리거·루틴·CHECK·예약 이벤트 의존성0, 코드 소비자 참조 없음. V25 원본은 수정하지 않는다.
- B JAR 검증: 임시 bootJar 블록 제거 후 V1~V25가 포함되고 각 V25 바이트·SHA-256이 원본과 일치한다. Mapper·Row는 계속 미션 컬럼을 읽지 않는다. 기대한 두 릴리스 분리에서 추가 의미 변경은 없다.
- 폐기 MySQL 8.4: DatabaseIntegrationTests25개와 PracticeSessionApiDatabaseTests5개 통과. 전용 temp-mysql 도구가 종료·파일 해제를 확인한 뒤 데이터 폴더를 정리했다. backend build와 배포 회귀50개도 통과했다.
- 별도 읽기 전용 검토: spec/quality 차단 결함 없음. 발견한 EOF 빈 줄은 정리했다. 승인 파일은 검증한 B JAR의 원본 V25 해시로만 등록하며, 배포 도구의 최종 후보 해시 비교를 유지한다.
- 운영 컨테이너는 syslog 드라이버의 로컬 캐시를 끄므로 docker logs가 동작하지 않는다. 실제 로그는 /var/log/eolssu/ordinary/eolssu-backend.YYYY-MM-DD.log에서 신규 기동 이후 범위를 확인한다. A 기동 완료·관련 SQL/시작 오류0건을 확인했다.
- 현재 단계: B 승인·Push/MR·배포 준비. 최종 이력·세션 재검증·postmigration 백업은 실행 후 결과를 구분해 보고한다.
