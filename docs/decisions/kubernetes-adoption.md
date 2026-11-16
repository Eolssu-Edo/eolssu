# 운영 배포 구조와 Kubernetes 도입

- 작업: [S15P31E102-990](https://ssafy.atlassian.net/browse/S15P31E102-990) · Story [S15P31E102-40 배포 환경·CI](https://ssafy.atlassian.net/browse/S15P31E102-40) · [계획·검증](../plans/S15P31E102-990.md)
- 상태: **제안** — 2026-11-16 인프라 담당 우동균의 검토 결론이다. 팀 합의 근거가 생기면 `확정`으로 바꾸고 근거를 연결한다.
- 기준: `origin/develop` `f8a17eb0`의 운영 설정과 문서. 이번 갱신에서는 서버에 접속하지 않았다. 서버를 직접 점검한 최초 조사(2026-10-23)는 [이력](#이력)에 요약했다.

## 선택

**운영은 지금처럼 Docker Compose와 Jenkins 배포 도구로 유지하고, Kubernetes(K3s 포함)는 도입하지 않는다(보류).** [재검토 조건](#재검토-조건) 중 하나가 확인되면 다시 비교한다.

## 근거

### 1. K8s로 얻으려던 기능을 Compose 위에 이미 갖췄다

10-23 조사에서 K8s를 고려한 이유는 배포 중단과 서버 장애 대응이었다. 그 뒤 같은 목표를 현행 구조 안에서 구현했다.

| 목표 | 2026-10-23 상태 | 현재 develop | 근거 |
| --- | --- | --- | --- |
| 배포 중 중단 줄이기 | 배포가 기존 컨테이너를 재생성해 중단될 수 있었다 | 백엔드 두 슬롯 블루그린. 새 슬롯이 준비 상태를 통과해야 Nginx 경로를 바꾼다. 격리 시험에서 전환·원복 중 연속 HTTPS 104건 실패 0건 | [블루그린 스펙](../specs/backend-blue-green.md), [604](../plans/S15P31E102-604.md), [947](../plans/S15P31E102-947.md) |
| 준비 상태 검사 | `/api/actuator/health` 404 | `/api/actuator/health/readiness`가 배포·대기계 검사에 쓰인다 | [edo2 대기 환경](../../infra/standby/README.md), [930](../plans/S15P31E102-930.md) |
| 컨테이너 자원 상한 | 모든 컨테이너 상한 0 | AI 6GiB, OMR 2GiB·2 CPU, playback 1GiB·1 CPU | [운영 Compose](../../infra/runtime/compose.yml) |
| 외부 장애 알림 | 없음 | edo2가 외부 HTTPS·복제·백업·디스크 변화를 알리고, edo2가 멈추면 edo1이 알린다. 실제 장애 시험에서 6분 7초에 알림 1건 | [919](../plans/S15P31E102-919.md), [944](../plans/S15P31E102-944.md), [실측 근거](../knowledge/infra/ops-quality-evidence.md) |
| 서버 밖 백업·복구 | 확인 안 됨 | edo1 → edo2 매시 암호화 백업, 매주 격리 복원 | [백업 안내](../../infra/backup/README.md) |
| 서버 한 대 장애 대비 | edo2는 점검 중계만 실행 | edo2에 같은 이미지의 대기 앱 스택과 MySQL GTID 복제본. 승격·원복 런북과 사본 리허설(edo2 쪽 명령 약 35초, 전체 약 2분 30초) | [579](../plans/S15P31E102-579.md), [런북](../../infra/standby/failover-runbook.md), [948](../plans/S15P31E102-948.md) |
| AI 계열 배포 | 운영에 AI 서비스 없음 | AI·OMR·playback 이미지를 Jenkins가 빌드·검사·배포·되돌리기 | [244](../plans/S15P31E102-244.md), [951](../plans/S15P31E102-951.md) |
| 파일 저장소 | 두 서버 공유를 위해 GCS·MinIO를 검토 | 녹음·음원은 외부 Cloudflare R2에 있어 앱 서버에 묶이지 않는다 | [시스템 아키텍처](../knowledge/infra/system-architecture.md) |
| 디스크·릴리스 관리 | — | 보존 정리 자동화, 발표 릴리스·동결 절차 | [921](../plans/S15P31E102-921.md), [릴리스 절차](../../infra/release/README.md) |

K8s가 이 위에 더해 주는 것은 Pod 자동 재시작·선언적 롤링 배포 정도다. 이 둘은 현재 `restart: unless-stopped`·healthcheck·블루그린 배포 도구가 맡고 있다.

### 2. 남은 한계는 K8s로도 풀리지 않는다

- **웹 주소 전환**: edo1 전체 장애 때 같은 주소로 복구하려면 SSAFY DNS 변경이 필요한데 담당 경로의 답을 아직 받지 못했다([948](../plans/S15P31E102-948.md)). 클러스터를 만들어도 공인 진입점은 바뀌지 않는다.
- **두 대로는 제어부 HA가 안 된다**: K3s embedded etcd는 과반을 유지하려면 server 3대가 필요하다. server 1대 + agent 1대는 server 쪽이 죽으면 배포·스케줄링이 멈춘다. 외부 DB 방식은 그 DB가 새 단일 장애 지점이 된다. [K3s embedded etcd HA](https://docs.k3s.io/datastore/ha-embedded), [K3s 외부 DB HA](https://docs.k3s.io/datastore/ha)
- **DB 쓰기 승계는 수동이다**: 비동기 복제는 주 서버 장애 때 미전달 쓰기를 잃을 수 있고, 두 멤버만으로는 안전한 자동 승격이 안 된다. Group Replication도 한 대 장애를 견디려면 3대가 필요하다. 현재 런북의 수동 승격이 K8s에서도 그대로 필요하다. [MySQL 장애 허용](https://dev.mysql.com/doc/refman/8.4/en/group-replication-fault-tolerance.html)

### 3. 지금 바꾸면 비용과 위험이 크다

- 이미지 전달(K3s containerd로 가져오기), 백엔드·웹·AI·OMR·playback 배포 도구, Nginx·TLS, 비밀값 주입, 백업·대기계 절차를 모두 다시 만들고 검증해야 한다.
- main 반영은 2026-11-17 17:00, 발표는 11-18이다. 동결 중 develop 병합은 곧 운영 배포다([릴리스 절차](../../infra/release/README.md)). 운영 기반을 바꿀 시점이 아니다.
- 발표 전 동시 접속 리허설의 위험(메일 발송, 분석 동시 2건 제한)은 코드 구조에서 나와 서버를 늘려도 해결되지 않는다([리허설 결과](../knowledge/infra/demo-load-rehearsal.md)). 7일 운영 지표에서도 DB 대기나 분석 큐 포화는 보이지 않았다([Redis 조사](../knowledge/infra/redis-adoption-review.md)).

## 검토한 대안

| 대안 | 판단 |
| --- | --- |
| **현행 Compose + 블루그린 + edo2 대기계** | 선택. 위 근거 1·3 |
| K3s 한 클러스터(edo2 server + edo1 agent) | server 쪽 장애 때 제어부가 멈추는 비대칭 구조. 진입점·DB 문제는 그대로 |
| K3s embedded etcd server 2대 | 한 대 장애 때 과반 미달. 3대가 필요해 이번 조건(서버 2대·유료 서비스 없음)에 맞지 않음 |
| K3s 외부 DB 방식 server 2대 | 외부 DB를 둘 곳이 edo1·edo2뿐이라 새 단일 장애 지점이 생김 |
| 서버마다 독립 단일 노드 K3s 2개 | 10-23 상세 설계안. 한 서버 장애가 다른 쪽 제어부를 멈추지 않지만, CI가 두 클러스터에 같은 릴리스를 따로 적용해야 하고 운영·백업 대상이 둘로 늘어난다. 이 안이 노린 서버별 순차 무중단 배포·고정 Nginx·MySQL 수동 승격은 현행 구조가 이미 같은 수준으로 제공한다 |

독립 K3s 2개 안의 상세 설계(manifest 초안, 이미지 전달, 수용 검사)는 저장소에 남기지 않았다. 재검토할 때는 이 문서의 조건과 그때의 운영 구성에서 다시 시작한다.

## 재검토 조건

아래 중 하나가 확인되면 K8s를 관리형 K8s·Compose 다중 호스트와 함께 다시 비교한다. 비교에는 운영 담당자·예산·DB 가용성·진입점을 포함한다.

| 상황 | 먼저 확인할 증거 | K8s가 유용해지는 조건 |
| --- | --- | --- |
| 서버가 3대 이상으로 늘어남 | 서버별 역할, 서비스 배치를 손으로 바꾸는 빈도와 실수 | 여러 호스트에 같은 서비스를 반복 배치·이동해야 함 |
| 분석·악보 인식 대기 증가 | 대표 녹음의 처리 시간, 동시 요청 수, `queue_ms`·대기 작업 수 | 앱 안의 동시 실행 제한을 고치고 AI를 분리한 뒤에도 여러 서버의 작업 수를 자주 조절해야 함 |
| 자동 장애 전환 요구 | 팀이 합의한 RTO·RPO, 실제 전환 시간 | 상태 기반 DNS나 외부 부하 분산기, 3대 이상의 DB·제어부를 확보할 수 있음 |
| 팀 목표 변경 | 팀이 K8s 운영 학습·표준화를 목표로 정함 | 운영 담당과 학습 시간을 따로 확보함 |

서버나 AI 서비스 하나를 더하는 것만으로는 도입 기준이 아니다. 고정 배치는 Compose로도 운영할 수 있다.

## 영향 범위

- 운영 Compose, Jenkins 배포 도구, edo2 대기계 구성은 바꾸지 않는다.
- 발표·인수인계에서 "왜 K8s를 쓰지 않았나"에 대한 답으로 쓴다.
- 이 결정과 별개로 남은 운영 과제: 백엔드·MySQL 메모리 상한 미설정, SSAFY DNS 답과 실제 DNS 전환 연습, edo2 사양과 AI·OMR 동시 실행 경합 실측, 팀 합의 RTO·RPO. 930에서 사용자가 정한 평소 목표(30분 안 복구, 최근 1분 이내 손실 허용)는 있으나 팀 합의 근거는 확인하지 못했다([실측 근거 미확인 항목](../knowledge/infra/ops-quality-evidence.md)).

## 이력

- **2026-10-23 최초 조사** (S15P31E102-80 작업 중 작성, 병합되지 않음): edo1을 읽기 전용으로 점검했다. CPU 4개, RAM 약 15.8GiB(가용 약 13GiB), 루트 디스크 사용 12%, 컨테이너 5개(frontend·backend·mysql·grafana·metabase) 모두 메모리 상한 없음, API 준비 상태 엔드포인트 없음, edo2는 점검 중계만 실행했다. 결론은 "Compose 유지, 준비 상태 검사·자원 제한·외부 알림·서버 밖 백업 복구 검증 우선"이었다. 이어서 두 서버 Compose 이중화와 K3s 비교, 독립 K3s 2개 상세 설계안을 작성했다.
- **2026-11-16 재검토** (S15P31E102-990): 위 우선 과제와 Compose 이중화안이 대부분 구현된 것을 최신 develop과 대조해 결론을 유지했다. K3s 상세 설계안은 이 문서의 대안 표로 요약하고 따로 두지 않는다.
