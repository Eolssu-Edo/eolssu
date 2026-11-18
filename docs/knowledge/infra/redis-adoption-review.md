# 얼쑤의 Redis 적용 필요성 조사

- 작업 [S15P31E102-659](https://ssafy.atlassian.net/browse/S15P31E102-659), Story [S15P31E102-40 배포 환경·CI](https://ssafy.atlassian.net/browse/S15P31E102-40)
- 최초 조사의 코드 기준은 `origin/develop f2bf0a2b`(2026-11-10 09:51 KST)다. 2026-11-15에 `origin/develop 489c90f9`와 다시 대조해 이후 병합된 앱 변경을 반영했다. 운영 서비스 코드에는 여전히 Redis 의존성·설정이 없다.
- 운영 수치는 edo1의 Docker 상태, Prometheus, 분석 로그, MySQL 집계를 읽기 전용으로 한 번 조회한 값이다. 관측 기간은 2026-11-03 10:02:30부터 11-10 10:02:30까지 KST 7일이다. 이 기간은 [S15P31E102-661](https://ssafy.atlassian.net/browse/S15P31E102-661)(11-10)과 [S15P31E102-762](https://ssafy.atlassian.net/browse/S15P31E102-762)·[S15P31E102-763](https://ssafy.atlassian.net/browse/S15P31E102-763)(11-11) 병합 전이어서 변경 전 기준선으로 읽는다. 기간 중 시각별 배포 커밋은 기록하지 않았다.
- 상태는 조사 결과와 제안이다. 운영 재측정은 하지 않았다.

## 결론

**현재는 Redis 도입을 보류한다.** 연주 분석에서 접수한 작업을 재시작 후 이어갈 요구가 생기면 MySQL 작업 큐를 우선한다. 작업 전달용 MySQL 조회·잠금이 병목으로 확인되거나 여러 서비스가 독립적으로 소비하고 재생할 이벤트가 생기면 Redis Streams를 비교한다. 현재 운영 수치에서 DB 병목이나 분석 큐 포화는 확인되지 않았다.

## 운영에서 확인한 것

| 지표 | 관찰 |
| --- | --- |
| 서비스 구성 | 조회 당시 edo1에서 백엔드 그린 슬롯 1개, MySQL, AI, OMR, 음원 생성기가 가동 중이었다. 런타임 프로젝트 컨테이너 목록에는 Redis가 없었다. |
| 백엔드 요청 | Actuator 경로를 제외한 요청 약 19,652건. 공개 악보 조회 HTTP 200 약 3,191건, 서버 응답 p95 약 99ms. 연주 상태 조회 HTTP 200 약 716건, p95 약 62ms. 성장 요약 HTTP 200 약 56건, p95 약 162ms. |
| 녹음 업로드 | HTTP 202 약 66건, 응답 p95 약 2.99초. 이 수치는 업로드 접수 API의 응답 시간이다. |
| MySQL 연결 풀 | HikariCP의 동시 사용 연결 최댓값 2, 연결 대기 최댓값 3. 조회 시점의 최대 풀 크기 10. 가장 대기가 잦았던 관측 시계열에서도 15초 단위 표본 중 대기 연결이 있던 비율은 약 0.006%였다. |
| 연주 분석 | 기간 중 생성되어 분석을 시작한 MySQL 세션 82건. 최종 상태는 성공 70건, 무음 10건, 입력이 짧음 2건이었다. 같은 기간 분석 종료 로그 집계도 일치했다. 백엔드 성공 실행 시간 중앙값 4.14초, 최댓값 20.68초. 포화로 제출을 거절하거나 마감 초과 상태로 전환한 로그는 관찰되지 않았다. |
| 악보 인식·음원 생성 | MySQL 접수 시각 기준 OMR 변환 2건. 한 건은 `NO_SCORE_FOUND`, 한 건은 `SERVER_ERROR`였다. 후자의 상세 원인은 미확인이다. 같은 기간에 접수 시각이 속한 음원 생성 행은 없었다. |

HTTP 건수와 p95는 Prometheus 추정값이다. 건수는 카운터의 7일 증가량이고, p95는 같은 구간의 히스토그램 버킷 증가량에 `histogram_quantile(0.95, ...)`을 적용한 값이라 버킷 경계만큼 오차가 있다. HTTP 응답·DB 세션·분석 로그는 각각 다른 사건을 센다. 완료 로그의 실행 시간은 워커가 시작한 뒤 잰 값이다. 관측 당시 수집 중인 실행기 큐 지표에는 연주 분석 전용 실행기가 따로 표시되지 않아 접수부터 시작까지 대기 시간은 측정할 수 없었다. 이후 S15P31E102-763에서 QA 앱이 `X-Evaluation-Timing-Id` 헤더를 보내면 서버가 `event=evaluation_analysis_timing` 로그에 `queue_ms`(제출→워커 진입)와 `processing_ms`를 남기도록 했다. 헤더가 없는 일반 요청은 여전히 대기 시간을 남기지 않는다. [계측 계획](../../plans/S15P31E102-763.md) 따라서 관측 기간의 저부하 수치로 최대 처리량이나 배포 중 작업 보존을 판정하기 어렵다.

재현은 [운영 모니터링 안내](../../../infra/monitoring/README.md)의 접속 경로를 사용한다. 위 종료 시각에 Prometheus에서 `http_server_requests_seconds_count`와 `http_server_requests_seconds_bucket`의 `increase(...[7d])`를 HTTP 메서드·URI·상태별로 집계하고, 풀은 `hikaricp_connections_active`와 `hikaricp_connections_pending`의 기간 최댓값을 확인한다. 분석 로그는 서버 안에서 `Analysis finished`, `Analysis not started: queue full`, `Analysis timed out`와 AI 종료 로그를 집계했다. MySQL은 읽기 전용 트랜잭션으로 `practice_sessions.created_at`·`analysis_attempt > 0`, `sheet_conversions.accepted_at`, `sheet_audios.accepted_at`을 같은 UTC 구간에서 집계했다. 원문 로그·계정·객체 식별자는 저장하지 않았다.

## 적용 후보

| 영역 | 현재 경로와 병목 가능성 | 판단 |
| --- | --- | --- |
| 연주 분석 접수·복구 | 녹음 업로드 커밋 뒤 메모리 큐로 전달한다. 제출 거절이나 백엔드 종료 뒤 `PENDING`이 남을 수 있다. 배포 스크립트의 이전 슬롯 종료 대기는 OMR과 생성 음원만 조회한다. | **우선 후보.** 재시작 복구는 MySQL 큐로 먼저 검증한다. 여러 워커가 DB 큐를 두고 경합하거나 분석 완료 이벤트를 여러 서비스가 소비해야 할 때 Streams를 비교한다. [분석 실행기](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/analysis/application/AnalysisOnUpload.kt) · [슬롯 종료 로직](../../../infra/jenkins/deploy-backend.py) |
| AI 동시 실행·대기열 제어 | 백엔드 분석 설정 기본값은 워커 2개와 대기 50개다. AI 쪽 DTW 메모리는 정답과 녹음 프레임 수의 곱에 비례하고 요청별 추정 가드가 있다. 별개 백엔드 실행기의 동시 요청은 AI 자원을 공유한다. | 우선 추론 CPU·RSS와 대기 시간을 분리해 측정한다. 대기가 추론 포화에서 오면 워커의 동시 처리 상한과 접수 제어를 조정한다. 여러 호스트의 워커가 공유할 상한이 필요해지면 DB 선점 또는 브로커 소비 동시성을 비교한다. [AI 실측과 가드](../ai/analysis-latency.md) |
| 악보 인식(OMR) | MySQL에 작업과 시도 상태를 저장한다. 블루그린에서 원본이 있는 슬롯만 자기 작업을 읽고 이전 슬롯을 유지한다. 임시 원본 수명과 장애 복구는 큐 행과 별개다. | 큐 이전 우선순위가 낮다. 다른 호스트에서 이어받으려면 원본 접근·보존과 슬롯 소유권부터 설계해야 한다. [변환 작업자](../backend/sheet-conversion-worker.md) · [블루그린 계약](../../specs/backend-blue-green.md) |
| 내 악보 음원 생성·삭제 | MySQL에 작업과 생성 버전을 저장하고 생성 파일은 객체 저장소에 둔다. 현재 슬롯만 새 작업을 읽으며 일부 삭제 구간은 JVM이 소유한다. | 기존 큐를 유지한다. 여러 슬롯이 업무 요청을 상시 처리할 때 삭제·재생성 충돌은 DB에 공유 소유권을 기록하는 방식부터 검토한다. 지연 작업자가 늦게 쓰지 못하도록 기존 생성 버전 조건을 유지한다. [음원 생성과 삭제 복구](../backend/sheet-audio-recovery.md) · [현재 음원 스케줄러](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/config/SheetAudioConfig.kt) |
| 상태 조회·완료 알림 | 앱은 연주 분석 중 처음 8초 동안 1초, 이후 2초 간격으로 HTTP 상태를 읽고 최종 응답에서 멈춘다. OMR·음원 생성도 폴링을 사용한다. | 폴링이 병목으로 확인되면 주기 조정 또는 SSE를 비교한다. 여러 백엔드에 열린 SSE 연결로 신호를 전달할 때 Redis Pub/Sub를 검토할 수 있다. 연결이 끊긴 동안 놓친 알림은 재접속 때 DB 상태를 다시 읽어 보완한다. [앱의 상태 조회](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/result/AnalysisPolling.kt) · [Redis Pub/Sub 전달 방식](https://redis.io/docs/latest/develop/pubsub/#delivery-semantics) |
| 공개 악보·성장 통계·미디어 | 인기 악보는 반복 조회된다. 성장 통계는 사용자별 기록을 읽고, 음원·녹음은 R2 경로를 사용한다. | 악보 p95와 DB 사용량이 목표를 위협하면 원인을 먼저 나눈다. DB·JSON 처리가 반복 병목이면 버전 기반 캐시를 비교한다. 성장 집계는 쿼리와 인덱스부터 보고, 파일 전송은 객체 저장소와 HTTP 경로에서 본다. [카탈로그 API](../../specs/catalog-read-apis.md) · [성장 통계 쿼리](../../../backend/src/main/resources/mybatis/growth/PracticeStatsMapper.xml) · [R2 경로](r2-storage-verification.md) |
| 로그인 제한·인증 | 로그인 실패 집계는 JVM 메모리, 인증 필터의 세션 확인은 10초 Caffeine, 토큰 폐기는 MySQL을 사용한다. | 블루그린 전환과 상시 다중 인스턴스에서 로그인 제한을 이어야 한다는 요구가 확정되면 Redis 후보가 된다. 토큰 폐기·재사용 판정은 회원 상태 변경과 같은 DB 트랜잭션에서 유지한다. [인증 저장 방식](../backend/auth-token-storage.md) |
| 중복 요청·실시간 연습 | 세션 생성과 악보 변환은 DB와 요청 키로 중복을 구분한다. 실시간 음높이 안내는 앱에서 계산한다. | 요청 키와 상태 변경의 원자성은 기존 DB에서 유지한다. 온디바이스 처리 성능은 기기에서 측정한다. [연주 평가 API](../../specs/practice-evaluation-api.md) · [실시간 판정](../ai/realtime-grade-rules-and-kmp.md) |

## 연주 분석 전달 방식 비교

| 방식 | 적합한 상황 | 구현 및 운영 비용 |
| --- | --- | --- |
| 현재 JVM 큐 | 간단한 비동기 실행, 장애 뒤 사용자가 재시도하는 흐름을 수용할 수 있을 때 | 배포·프로세스 종료와 큐 포화 시 작업이 보존되지 않을 수 있다. 현재 시도 번호 검사로 늦은 결과 저장을 막는다. |
| MySQL 작업 큐 | 한 DB에서 분석 접수와 작업 등록을 커밋하고, 재시작 뒤 자동 복구할 때 | `(session_id, attempt)`를 식별자로 삼아 워커가 짧은 트랜잭션으로 선점한다. 상태·임대 만료 시각·소유권을 저장하고 재선점된 뒤에는 이전 소유자의 완료를 거절한다. AI 호출 중 DB 잠금을 놓는다. 적은 워커 수에서 출발하고 폴링 비용을 잰다. [MySQL 8.4 잠금 읽기](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) |
| Redis Streams | 작업자 간 분배·대기 중 이벤트 재전달이 필요하고 MySQL 전달 비용이 실제 병목이거나, 여러 소비자 그룹이 독립적으로 재생해야 할 때 | 작업 등록과 함께 MySQL에 발행 기록을 쓴다. 소비자는 `XREADGROUP`으로 읽고 결과 커밋 뒤 `XACK`하며, 유휴 작업을 `XAUTOCLAIM`으로 회수한다. 큐에서 사라진 미완료 작업을 MySQL 기준으로 다시 발행할 경로와 중복 실행·완료 차단이 필요하다. [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/) · [XAUTOCLAIM](https://redis.io/docs/latest/commands/xautoclaim/) |
| RabbitMQ | 라우팅, 소비자별 수신 한도, 실패 메시지 분리 등 메시지 브로커 기능이 별도로 필요할 때 | 발행 확인과 소비 완료 확인을 지원한다. DB 상태와 발행 간 경계, 작업 멱등성, 브로커 운영·장애 복구를 함께 설계해야 한다. [RabbitMQ 발행·소비 확인](https://www.rabbitmq.com/docs/confirms) |

MySQL 큐와 Streams 모두 요청 접수부터 시작까지의 대기와 실행 마감이 분리되어야 한다. 현재 마감은 분석 시도의 접수부터 적용되므로 지연 후 회수하더라도 기존 마감을 넘겼으면 TIMEOUT을 유지한다. 별도의 대기 SLA나 자동 재시도를 도입하려면 앱에 보여주는 상태와 시간 예산을 먼저 합의한다. 파일은 기존 저장소에서 읽고, 작업에는 식별자와 시도 번호만 전달한다.

Redis를 도입한다면 스트림의 미확인 작업 수·가장 오래된 미확인 시간·재처리 수와 메모리·디스크를 운영 지표로 둔다. 큐는 캐시와 메모리 퇴거 정책을 분리하고 미확인 작업을 지우지 않는 보존 규칙을 둔다. AOF의 초 단위 동기화나 비동기 복제에서도 장애 시 최근 쓰기를 잃을 수 있어 MySQL에서 미완료 작업을 복구할 수 있어야 한다. 같은 서버에 컨테이너를 추가하는 구성에서 호스트 장애 복구는 기존 MySQL과 파일 저장소의 복구 범위에 좌우된다. [Redis 저장 방식](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/) · [Redis 복제](https://redis.io/docs/latest/operate/oss_and_stack/management/replication/) · [키 퇴거 정책](https://redis.io/docs/latest/develop/reference/eviction/)

## 도입 판단을 바꿀 증거

1. **작업 보존.** 격리 환경에서 업로드 확정 직후, AI 실행 중, 결과 커밋 직후에 워커를 각각 중단한다. 자동 회수 대상은 마감 내에 이어지고 평가 저장은 세션당 한 번인지 확인한다. 이전 워커의 늦은 완료와 중복 전달도 함께 검사한다.
2. **병목 위치.** 접수→선점, 선점→AI 응답, AI 응답→DB 커밋을 나눠 기록한다. 대기 p95·최고 대기 작업 나이·포화 거절 수를 AI CPU·RSS, MySQL 잠금·연결 대기와 함께 비교한다. 추론이 포화면 워커 용량을, MySQL 큐 선점이 병목이면 전달 구조를 검토한다.
3. **브로커 채택.** 동일한 대표 녹음·동시성·마감에서 MySQL 큐와 Redis 후보를 비교한다. 작업 유실, 평가 중복, 대기 p95, DB·메모리 부하, 복구 절차를 함께 확인해 운영 부담을 상쇄할 개선이 있는지 판단한다.

이번 조사에서는 운영 부하 시험, 장애 주입, Redis 벤치마크를 수행하지 않았다.

## 사용자 대기 시간으로 재평가

측정 목표는 사용자가 **평가하기**를 누른 시점부터 **총점과 항목별 점수가 처음 보이는 프레임**까지다. 악보·오류 위치까지 준비된 시점은 두 번째 지표로 둔다. 운영 데이터에는 탭·렌더링 시각이 없어 아래에서는 서버가 관찰한 구간을 사용한다.

### 실제 경로

2026-11-15 `489c90f9` 기준 경로다. 아래 운영 표본은 이 변경들보다 앞선 빌드에서 수집했다.

1. 연습 화면은 곡 끝이나 녹음 한도에서 파일을 미리 마무리한다. 평가하기를 누르면 파일과 곡을 분석 화면에 넘긴다. 분석 화면은 새 녹음 제출 전 현재 게시 판본을 서버에서 다시 확인하려고 곡을 한 번 조회한 뒤 연습 세션을 생성하고 녹음을 올린다. 공개 곡의 `getSong()`은 곡 상세를 받은 뒤 악보를 받으므로 HTTP 요청 두 건이 순차로 실행된다. [연습 화면](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/practice/PracticeRoute.kt) · [분석 화면의 조회·접수](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/result/AnalysisSession.kt) · [곡 조회](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/data/song/ApiSongRepository.kt)
2. 백엔드는 녹음을 검사하고 R2에 저장한 뒤 분석을 접수한다. AI가 R2에서 읽어 디코딩·pYIN·DTW를 실행하고, 백엔드가 평가와 음표 결과를 커밋한다. [녹음 접수](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/practice/application/UploadRecording.kt) · [AI 처리](../../../ai/src/ai_service/api.py)
3. 앱은 업로드 응답 후 즉시 상태를 한 번 조회한다. `PENDING`이면 처음 8초 동안 1초, 이후 2초씩 기다려 다시 조회한다(S15P31E102-661). `DONE` 뒤 평가를 조회하면 총점·항목 점수를 먼저 표시한다(S15P31E102-661). 악보·오류 위치는 분석 화면이 넘긴 곡이나 같은 로그인에서 보관한 곡의 ID·판본이 평가와 맞으면 재사용하므로, 공개 곡 정상 경로에서 결과 표시용 곡 GET은 0건이다(S15P31E102-762). 변경 전에는 평가하기 이후 곡 조회가 세 번(순차 GET 여섯 건)이었고 화면은 평가와 곡이 모두 준비되어야 점수를 보였다. [상태 조회 간격](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/result/AnalysisPolling.kt) · [평가 저장소](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/data/practice/ApiEvaluationRepository.kt) · [결과 ViewModel](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/result/ResultViewModel.kt) · [곡 재사용 기록](../../plans/S15P31E102-762.md)

### 운영의 성공 세션 표본

2초 고정 조회와 세 번의 곡 조회를 쓰던 빌드의 값이다. 같은 7일간 처음 분석 시도로 성공한 70건의 MySQL 시각과 AI·백엔드·Nginx 로그를 서버 안에서 세션 기준으로 대조했다. 녹음 길이는 중앙값 29.9초, 최댓값 49.5초였다. 결과 응답이 확인된 64건 중 53건은 업로드 202부터 첫 평가 200까지 상태 조회가 있었고, 인접 응답 간격이 모두 6초 이하였다. 이 연속 조회 표본을 별도로 집계했다. p95는 정렬한 표본의 올림 순위다.

| 관측 구간 | 표본 | 중앙값 | p95 |
| --- | ---: | ---: | ---: |
| 업로드 접수 기록 → 평가 행 삽입 | 70 | 4.10초 | 6.20초 |
| AI HTTP 핸들러 내부, 녹음 다운로드 포함 | 70 | 3.98초 | 5.97초 |
| 같은 시도의 백엔드 실행 시간에서 AI 핸들러 시간을 뺀 값 | 70 | 0.09초 | 0.49초 |
| 평가 행 삽입 → 첫 평가 응답 HTTP 200, 연속 조회 표본 | 53 | 0.75초 | 1.75초 |
| 업로드 응답 HTTP 202 → 첫 평가 응답 HTTP 200, 연속 조회 표본 | 53 | 5초 | 7초 |
| DB 세션 생성 → 첫 평가 응답 HTTP 200, 연속 조회 표본 | 53 | 7.34초 | 11.18초 |

전체 성공 70건 중 15건은 세션 생성 후 접수까지 60초를 넘겼고, 8건은 5분을 넘겼다. 접수 전 다른 업로드 응답은 해당 서버 로그에서 찾지 못했다. 클라이언트가 업로드를 시작한 시각과 시도 이력이 없어 이 간격을 전송 시간으로 볼 수 없다. 또한 평가 행 삽입에서 첫 평가 응답까지 10초 이상 걸린 11건과 평가 조회가 관찰되지 않은 6건은 연속 조회 표본에서 별도로 구분했다. 앱의 재개·재조회·네트워크 영향은 구분할 수 없었다.

DB 평가 시각은 행 삽입 기준이며 실제 트랜잭션 커밋과 작은 차이가 있다. Nginx 접근 로그의 응답 시각은 초 단위다. 평가 200 뒤 곡 로딩·화면 렌더링, 탭 직후 곡 조회·세션 생성까지는 이 표에 포함되지 않는다. 자료 재현은 동일 기간에 생성된 `practice_sessions` 중 `DONE`, `analysis_attempt=1`인 행을 `evaluations`와 연결하고, 시도별 `Analysis finished`와 AI 완료 로그, Nginx의 녹음 202·상태 200·평가 200을 시각순으로 대조한다. 저장한 값은 집계뿐이다.

### 체감 대기를 줄일 순서

1. **앱의 중복 조회 축소와 점수 우선 표시는 반영됐다.** S15P31E102-762가 회원 경계와 `songId`·`scoreVersion` 일치 검사를 유지한 채 결과 화면에서 곡을 재사용하고, S15P31E102-661이 총점·항목 점수를 악보보다 먼저 표시한다. 서버 캐시 대신 앱 재사용으로 순차 요청 자체를 줄였다. 남은 순차 조회는 제출 전 게시 판본 확인용 곡 조회(GET 두 건)다. 수정 빌드의 실기기 첫 점수 표시와 GET 수 재측정은 762 기록에 미완료로 남아 있다.
2. **긴 대기가 생기는 구간과 AI 내부를 나눠 잰다.** 앱에서 탭, 업로드 시작·응답, 첫 점수 프레임을 단조 시계로 기록한다. 서버에서 업로드 수신·검사·R2 저장, 접수→워커 시작, AI 다운로드·디코딩·pYIN·DTW, 결과 커밋을 나눈다. S15P31E102-763이 Android QA 앱의 세션 생성·녹음 전송·첫 상태 조회·첫 완료 확인·총점 첫 그리기와 서버의 `queue_ms`·`processing_ms`를 같은 진단 ID로 잇는 계측을 추가했다. 실기기 수집과 AI 내부 단계 분리는 아직이다. 대표 길이, 느린 네트워크, AI 재시작 직후와 동시 요청을 분리해 p50·p95와 실패율을 본다. 성공 표본에서 AI 핸들러가 서버 처리 대부분을 차지했다. AI 코드나 모델을 바꾸면 지연과 채점 품질을 같은 녹음으로 대조한다.
3. **완료 전달은 짧은 폴링 조정부터 한다.** 변경 전 정상 흐름에서 평가 행 삽입 뒤 첫 응답까지 p95가 약 1.75초였다. S15P31E102-661이 처음 8초만 1초 간격으로 조회한 뒤 2초로 복귀하도록 바꿨다. 이 조정의 완료 감지 지연 감소와 추가 GET 수는 아직 운영·실기기에서 비교하지 않았다. 필요하면 단일 백엔드에서 SSE와 재접속 시 DB 조회를 검토한다. Redis Pub/Sub는 여러 백엔드가 각자의 SSE 연결로 신호를 전달할 때 후보가 된다. [Redis Pub/Sub 전달 방식](https://redis.io/docs/latest/develop/pubsub/#delivery-semantics)

**속도 기준에서도 Redis Streams 도입 근거는 부족하다.** 변경 전 표본은 업로드·AI 처리·앱의 순차 조회를 우선 볼 근거를 줬고, 앱 쪽 순차 조회와 조회 간격은 Redis 없이 조정됐다. 큐 내구성은 배포나 재시작에서 대기가 타임아웃으로 길어지는 경우를 줄이는 별도 개선이다. 워커 시작 대기가 운영에서 커지고 원인이 DB의 작업 전달 경로로 확인될 때 Redis를 비교한다. 실기기 탭→첫 점수 프레임, 느린 네트워크 재현과 변경 전후 비교는 미측정이다.
