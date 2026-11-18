# 녹음 길이별 분석 처리 시간과 메모리: 합성 입력

- 작업: [S15P31E102-172](https://ssafy.atlassian.net/browse/S15P31E102-172)
- 상태: 개발 노트북 측정 완료. 운영 서버(edo1) pYIN 측정 완료(2026-10-28, [아래 '운영 서버 측정'](#운영-서버-측정-edo1)). 분석 제한 시간(PRA-03)과 녹음 길이 상한(PRA-02)의 판단 근거로 쓴다.
- 재현: `ai/`에서 `uv sync --group eval` 후 `uv run --group eval python -m evaluation.measure_latency`
- 코드: [측정 실행](../../../ai/evaluation/measure_latency.py), [채점](../../../ai/src/ai_service/pipelines/scoring.py), [합성 입력](../../../ai/evaluation/synthetic.py)

## 결론

- **지금 채점 방식으로는 600초 녹음 × 600초 정답을 분석할 수 없다.** 채점의 DTW가 정답 프레임 수 × 녹음 프레임 수 × 칸당 약 20바이트(tracemalloc 실측)를 쓴다. 이 값으로 120초는 DTW만 약 2.7GiB(기본 메모리를 더해 약 3.0GiB), 300초는 약 17GiB, 600초는 약 67GiB다. PRA-02의 600초 상한을 유지하려면 DTW에 대각선 폭 제한(band)이나 구간 나눔을 넣고 다시 재야 한다.
- **pYIN은 120초까지 60초 제한 안에 든다.** 부하가 큰 노트북에서도 60초 입력은 약 2초, 120초 입력은 5~12초였다. 음높이 추정만 보면 600초도 15초다.
- **torchcrepe tiny는 120초까지 60초 제한 안에 들지만 여유가 작다.** 120초 입력이 20~34초였다. 600초는 음높이 추정만 139초라 제한을 넘는다.
- **torchcrepe full은 CPU 서버에서 쓸 수 없다.** 15초 입력이 이 노트북에서 907초, 이전 Windows 측정에서도 5.5초 입력에 7.9초였다.
- 시간 값은 측정 기기의 부하에 따라 같은 입력도 회차마다 1.1~3.9배 달랐다. 노트북 표를 서버 성능으로 일반화하지 않는다. 아래 운영 서버 측정은 별도 조건이며, end-to-end 60초 보장은 아직 검증하지 않았다.
- 2026-10-29 재검증: 현재 HTTP API의 4GiB 추정 가드가 긴 요청을 DTW 전에 거절한다. 67GiB는 전체 DTW 외삽값이고, 제품600초 상한·기본60초·동시성 예산은 아래 후속 절에서 구분한다.

## 제안

| 항목 | 제안 | 이유 |
| --- | --- | --- |
| 분석 제한 시간 | 60초 유지, 서버에서 다시 잰 뒤 조정 | pYIN·tiny 모두 120초 녹음까지 제한 안이다 |
| 녹음 길이 상한(PRA-02) | 채점 방식을 바꾸기 전에는 서비스 곡의 최대 연습 구간 길이로 낮춘다. 그 길이가 120초를 넘으면 DTW 폭 제한을 먼저 넣는다 | 600초는 채점 메모리가 서버 메모리를 넘는다 |
| DTW 폭 제한 | 120초를 넘는 연습 구간이 필요할 때 별도 AI Task로 진행 | 메모리를 프레임 수 × 폭으로 줄이지만 크게 어긋난 연주의 정렬이 달라질 수 있어 판정 검증이 필요하다 |
| 음높이 추정 방법 | full은 서버 후보에서 뺀다. pYIN과 tiny 선택은 실제 녹음 비교([S15P31E102-62 기록](pitch-estimator-comparison.md)) 뒤에 한다 | 속도 차이만으로 정하지 않는다 |

녹음 길이 상한과 제한 시간을 바꾸는 결정은 PRA-02·PRA-03 스펙을 고칠 때 팀이 정한다.

## 입력과 방법

- 입력: [음높이 추정 비교](pitch-estimator-comparison.md)의 '정상' 합성 음계(16kHz, 120BPM, 한 박 0.5초)를 이어 붙여 15·30·60·120·300·600초로 만들었다. 실제 대금 녹음이 아니다.
- 실행: 길이·방법마다 새 프로세스를 띄워 한 번 분석했다. 서버처럼 모델은 짧은 입력으로 먼저 불러 두고, 그 시간은 '준비'로 따로 적었다.
- 시간: 음높이 추정(f0)과 채점(음량 계산·DTW·판정)을 나눠 쟀다.
- 메모리: 프로세스 최대 메모리(`ru_maxrss`). librosa·numba를 읽은 기본값이 약 300MB, torch를 읽으면 약 2.5GB다. macOS는 메모리 압축·스왑이 있어 `ru_maxrss`가 실제 할당보다 작게 나올 수 있다. 120초 pYIN 표 값(1.9~2.6GB)이 아래 tracemalloc 계산(약 3.0GiB)보다 작은 이유로 보인다. 서버 메모리를 정할 때는 표 값이 아니라 칸당 바이트로 계산한다.
- DTW 칸당 메모리: `score()`를 tracemalloc으로 재면 30초 20.08바이트, 60초 20.03바이트(최대 688MiB)였다. 비용 8 + 누적 비용 8 + 이동 기록 4와 맞는다.
- DTW 예상(표의 열): 실측 20바이트에 여유를 두어 칸당 24바이트로 계산했다. 이 예상이 6GiB를 넘으면 채점하지 않고 음높이 추정만 쟀다.
- 채점까지 하던 길이가 시간 초과·실패하면 채점 탓일 수 있어 더 긴 길이는 음높이 추정만 잰다. 음높이 추정만 재던 길이가 실패하면 그 방법의 더 긴 길이는 돌리지 않는다.
- 이전 길이의 음높이 추정 시간을 비례로 늘린 예상치가 600초를 넘으면 그 길이는 건너뛰었다.

## 결과

### 1회차 (모든 길이)

표의 'DTW 예상' 열과 비고의 GiB는 여유를 둔 칸당 24바이트 기준이다. 결론의 17·67GiB는 실측 칸당 20바이트 기준이라 값이 다르다.

| 녹음 길이(초) | 방법 | 준비(초) | 음높이 추정(초) | 채점(초) | 합계(초) | 최대 메모리(MB) | DTW 예상(MB) | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 15 | pYIN | 2.28 | 0.26 | 0.03 | 0.28 | 318 | 51 |  |
| 30 | pYIN | 1.87 | 0.63 | 0.21 | 0.84 | 593 | 206 |  |
| 60 | pYIN | 1.93 | 1.15 | 0.71 | 1.86 | 1294 | 824 |  |
| 120 | pYIN | 2.08 | 4.03 | 7.93 | 11.96 | 1898 | 3296 |  |
| 300 | pYIN | 2.53 | 11.97 | - | - | 1735 | 20599 | 채점 안 함(DTW 예상 20GiB) |
| 600 | pYIN | 3.11 | 15.14 | - | - | 2367 | 82397 | 채점 안 함(DTW 예상 80GiB) |
| 15 | torchcrepe full | 6.95 | 906.53 | 0.98 | 907.51 | 3862 | 51 |  |
| 30~600 | torchcrepe full | - | - | - | - | - | - | 건너뜀(예상 1813초 이상) |
| 15 | torchcrepe tiny | 5.62 | 2.61 | 0.23 | 2.84 | 2918 | 51 |  |
| 30 | torchcrepe tiny | 6.36 | 13.91 | 0.31 | 14.22 | 2393 | 206 |  |
| 60 | torchcrepe tiny | 4.66 | 11.35 | 0.62 | 11.97 | 2743 | 824 |  |
| 120 | torchcrepe tiny | 4.35 | 31.08 | 2.54 | 33.63 | 3297 | 3296 |  |
| 300 | torchcrepe tiny | 3.25 | 58.14 | - | - | 3189 | 20599 | 채점 안 함(DTW 예상 20GiB) |
| 600 | torchcrepe tiny | 3.37 | 139.33 | - | - | 2834 | 82397 | 채점 안 함(DTW 예상 80GiB) |

### 2회차 (흔들림 확인)

| 녹음 길이(초) | 방법 | 준비(초) | 음높이 추정(초) | 채점(초) | 합계(초) | 최대 메모리(MB) | DTW 예상(MB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 30 | pYIN | 3.00 | 0.50 | 0.10 | 0.60 | 618 | 206 |
| 60 | pYIN | 1.69 | 1.01 | 0.65 | 1.66 | 965 | 824 |
| 120 | pYIN | 1.77 | 1.91 | 3.37 | 5.28 | 2632 | 3296 |
| 30 | torchcrepe tiny | 3.27 | 3.60 | 0.24 | 3.83 | 2792 | 206 |
| 60 | torchcrepe tiny | 2.94 | 7.15 | 0.51 | 7.66 | 2802 | 824 |
| 120 | torchcrepe tiny | 2.99 | 17.71 | 2.76 | 20.47 | 3137 | 3296 |

같은 입력도 회차마다 시간이 달랐다. 1·2회차를 비교하면 tiny 30초 음높이 추정이 3.9배(13.91초와 3.60초), pYIN 120초 합계가 2.3배(11.96초와 5.28초) 차이 나는 등 1.1~3.9배였다. 한 회차 안에서도 1회차 tiny 30초(13.9초)가 60초(11.4초)보다 느렸다. 채점 시간은 60→120초에서 4.1~11.2배 늘었다. 프레임 수의 제곱(4배)보다 크게 늘어난 회차는 부하나 메모리 압박의 영향으로 보이며 원인은 확인하지 않았다.

## 운영 서버 측정 (edo1)

S15P31E102-228에서 운영 AI 컨테이너와 같은 이미지·한도로 pYIN만 다시 쟀다. 120초 녹음까지 합계 17초로 **60초 제한 안에 3배 넘게 여유가 있다.** 최대 메모리도 120초에서 약 3.0GB로 개발 노트북의 tracemalloc 계산(약 3.0GiB)과 맞다.

| 녹음 길이(초) | 방법 | 준비(초) | 음높이 추정(초) | 채점(초) | 합계(초) | 최대 메모리(MB) | DTW 예상(MB) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 15 | pYIN | 16.42 | 1.42 | 1.06 | 2.49 | 418 | 51 |
| 30 | pYIN | 2.80 | 2.99 | 0.37 | 3.36 | 449 | 206 |
| 60 | pYIN | 2.72 | 6.20 | 2.15 | 8.35 | 976 | 824 |
| 120 | pYIN | 3.16 | 9.50 | 7.75 | 17.25 | 3065 | 3296 |

- 실행: `eolssu-ai:runtime`(develop `88a1b05`) 일회용 컨테이너, 네트워크 없음, 메모리 6GiB, 읽기 전용 + `/tmp` tmpfs, `ai/evaluation`만 읽기 전용으로 붙여 `python -m evaluation.measure_latency --methods pYIN --seconds 15 30 60 120`. 15초 '준비' 16초는 첫 실행의 numba 컴파일이다(운영 컨테이너도 재시작 뒤 첫 요청에서 한 번 든다).
- 서버: Intel Xeon Platinum 8259CL 4코어, 메모리 15GiB, Ubuntu 24.04(커널 7.0 AWS). 운영 서비스(backend·MySQL·Jenkins·모니터링 등)가 함께 돌아 측정 중 부하 평균이 5~6이었다.
- Python 3.12.16, numpy 2.5.4, librosa 1.0.0, numba 0.68.0
- 녹음 디코딩·다운로드·백엔드 저장 시간은 포함하지 않았다. torchcrepe는 운영 이미지에 없어 재지 않았다.

## 환경

- 2026-10-27, 기준 커밋 `672ff12`(develop)
- MacBook Apple M4(10코어), 메모리 16GB, macOS 26.2, GPU 미사용
- 측정 중 다른 작업(Gradle 빌드 JVM, 가상 머신, 원격 데스크톱)이 돌아 부하 평균이 약 10이었다. 시간 값은 상한에 가까운 참고치다.
- Python 3.12.13, numpy 2.5.4, librosa 1.0.0, numba 0.68.0, torch 2.14.1(CPU, 스레드 4), torchcrepe 0.0.24

## 한계와 남은 일

- 운영 서버 측정은 edo1의 pYIN만 했다(위 '운영 서버 측정'). 60초 제한은 120초 녹음까지 충분하다. 녹음 길이 상한과 DTW 폭 제한 결정은 남아 있다.
- torchcrepe full이 이 Mac에서 Windows보다 수십 배 느린 원인은 확인하지 않았다. 서버 후보에서 빼는 결론에는 영향이 없다.
- 합성 입력이라 실제 녹음의 잡음·길이 차이에 따른 DTW 경로 변화는 반영하지 않았다.
- 녹음을 디코딩·16kHz로 바꾸는 시간(PRA-02 S15P31E102-183)과 서버 요청·저장 시간은 포함하지 않았다.

## 2026-10-29 판단 근거 재검증 (S15P31E102-282)

이 절은 앞의 2026-10-27/28 실험을 대체하는 운영 성능 측정이 아니다. [후속 작업](../../plans/S15P31E102-282.md)의 코드·가드·작은 로컬 입력 검증이다. 공유 수치와 정책은 변경하지 않았다.

### 버전과 현재 동작

- 기준 소스 `58d9fe9`(develop). edo1 읽기 전용 확인에서 실행 중 AI revision은 `88a1b058ff9bdd60179d8e161e2af3fbbcc99c84`, 이미지 ID는 `sha256:a86ee1f296dc5f3cb078e0d125d78777c74bb4827c4c506b48360374159bb0da`. 이 revision과 기준 사이 `api.py`·`pipelines/scoring.py` diff는 없다.
- CPU 4, 전체 메모리 약15.41GiB, AI 컨테이너 한도 6GiB를 확인했다. 가용 메모리 약10.04GiB는 조회 순간값으로 용량 보장이 아니다. 운영 부하 실험은 하지 않았다.
- score()는 여전히 전체 `expected × detected` 비용 행렬과 DTW 누적/경로 행렬을 만든다. 0.01초 프레임에서 예상값은 `20 × ceil(정답초/0.01) × 녹음프레임` 바이트 수준이다(실제 코드의 정답 프레임은 round). **600초×600초의 약67.06GiB는 실행 실측이 아니라 외삽**이다.
- HTTP API는 `24 × 정답프레임추정 × 녹음프레임추정 > 4GiB`이면 `RecordingTooLong`을 던진다. 양쪽 600초는 80.47GiB 추정이라 **pitch/DTW 전에 거절 → HTTP 500 ANALYSIS_ERROR → BE SERVER_ERROR**다. 따라서 현재 경로에서 67GiB 할당을 실제 관찰했다고 표현하면 안 된다.
- 길이 자체의 고정 120초 상한은 없다. 양쪽 길이가 같을 때 가드 경계는 약133.775초다. 15초 악보×600초 녹음은 가드상 2.01GiB로 통과한다. 통과는 60초 내 처리나 안전한 동시 실행을 보장하지 않는다. 가드는 디코딩 뒤·pYIN 전에 있으며 디코딩 메모리/시간을 보호하지 않는다.
- backend 기본 동시 작업 2개·대기열 50개다. 요청별 4GiB 추정 한도를 통과한 두 요청이 총6GiB 컨테이너에 함께 들어가는 것은 안전성이 보장되지 않는다. AI는 동기 endpoint를 thread pool에서 처리하며 현재 Docker 명령에 요청 동시성 제한은 없다. 실제 OOM 재현은 하지 않았다.
- BE 60초는 PENDING 시작부터 저장 마감이다. 대기열 체류·다운로드·디코딩·추정·DTW·저장이 예산을 공유한다. AI HTTP 대기는 기본65초이며 만료된 결과는 저장하지 않는다. HTTP 연결이 끊겼다고 AI CPU 작업이 반드시 취소되는 구조는 아니다.

### 작은 고정 입력의 실측과 가드 검증

Python 3.12.13, numpy 2.5.4, librosa 1.0.0, macOS 26.2 arm64. 기존 개발 노트북 환경에서 모델·오디오 없이 일정한 311.13Hz f0 배열을 사용했다. 1초 score()로 준비한 뒤 2/4/8초를 각각3회 순서 실행했다. tracemalloc은 score() 구간만 측정한다. 스레드 설정은 OPENBLAS_NUM_THREADS=1, numba 캐시는 /private/tmp. 이는 속도 비교나 운영 SLA가 아니라 칸당 메모리 근사의 재검증이다.

| 입력 | 횟수/결과 | tracemalloc peak 범위(bytes) | 칸당 바이트 | score 시간 중앙값/범위(ms) |
| --- | --- | --- | --- | --- |
| 2초 | 3/모두 DONE | 825501~825533 | 20.64~20.64 | 1.388 / 1.283~3.463 |
| 4초 | 3/모두 DONE | 3257229~3257245 | 20.36~20.36 | 2.995 / 2.991~3.017 |
| 8초 | 3/모두 DONE | 12927997~12939381 | 20.20~20.22 | 8.344 / 8.325~9.872 |

기존 칸당20바이트 근사와 일치한다. 600초까지 실측한 것이 아니고 RSS·컨테이너 peak·pYIN·디코더 메모리도 아니다. API는 실제 `_score_recording`을 사용하되 길이만 제공하는 디코더 대역과 pitch sentinel로 검사했다. 120×120·133×133초는 sentinel까지 도달, 134×134·600×600초는 RecordingTooLong, 15×600초는 sentinel까지 도달했다. 큰 오디오/비용 행렬은 만들지 않았다. 기존 API 가드 회귀 검사도 3개 통과했다.

### 1차 4곡의 길이: 기본 BPM만으로 최댓값을 정하면 안 됨

운영 시드 V6의 score_json을 그대로 파싱했다. 전체 길이는 마지막 NOTE/REST 종료 틱, 채점 정답 길이는 첫 NOTE 시작부터 마지막 NOTE 끝까지를 각각 ticksPerBeat로 나눈다. 소리 뒤 쉼 때문에 두 값이 다르다. 실제 연주 지연/카운트다운은 포함하지 않는다. 서버 세션 생성은 BPM20~240을 허용한다.

| 곡 | 기본 BPM | 악보 전체(초) | 채점 정답(초) | BPM20 전체(초) | BPM20 전체를 녹음할 때 가드 추정(GiB) |
| --- | --- | --- | --- | --- | --- |
| 아리랑 | 96 | 30.000 | 29.375 | 144 | 4.538 |
| 도라지타령 | 96 | 52.500 | 51.875 | 252 | 14.025 |
| 밀양아리랑 | 96 | 30.000 | 30.000 | 144 | 4.635 |
| 늴리리야 | 66 | 21.818 | 21.515 | 72 | 1.143 |

기본 BPM의 전체 길이는 최대52.5초지만, 허용 최저 BPM에서는 최대252초다. BPM20의 앞3곡은 가드4GiB를 넘는다. '현재 곡은 모두120초 이내'나 '600초 녹음만 막으면 됨'은 전체 허용 BPM에 대해 성립하지 않는다. 이 표는 현재 4곡/V6 기준이며 앞으로 추가할 곡의 최대 연습 구간은 Data·기획 확인이 필요하다. 부분 연습은 선택 NOTE 범위의 시작·끝으로 다시 계산해야 한다.

### 60초 유지의 근거와 한계

앞의 edo1 측정120초 합계17.25초는 따뜻한 단일 합성 녹음의 pYIN+채점만이다. 첫15초 준비16.42초에는 numba 컴파일이 포함된다. 다운로드·디코딩·대기열·DB 저장을 합친 end-to-end p95/p99나 두 긴 요청의 동시 처리 증거는 아니다. '3배 여유'를 전체 요청 보장으로 해석하지 않는다. 현재60초 유지 권고는 설정 변경을 정당화할 실제 전체 흐름 증거가 없다는 뜻이며 모든 합법 입력이 성공한다는 뜻이 아니다.

### 선택지와 권고 (팀 미확정)

| 선택지 | 효과/조건 | 검증과 남는 위험 |
| --- | --- | --- |
| 곡·구간·BPM 기준으로 녹음 범위를 한시적으로 줄임 | 수용 범위 합의 뒤 PRA-02·BE·FE를 함께 변경. 기본 길이52.5초만 근거로 상한을 정하지 않음 | 느린 전곡 연습이 제한될 수 있음. 가드와 제품 상한 의미를 일치시키고 동시성 예산도 필요 |
| 메모리 절감 DTW 구현 | 긴/느린 연습이 필요할 때 밴드 전용 저장 또는 분할/다른 정렬 구조를 별도 AI Task로 검토 | **librosa global_constraints/band_rad만 켜는 것으로 부족**. 설치 버전 sequence.py는 C를 복사하고 전체 크기 D와 steps를 만든다. 현재 호출자도 전체 cost를 먼저 만든다. 정상·빠르기 차이·쉼·누락·옥타브·시김새 판정 회귀가 필수 |
| 60초/HTTP 제한만 늘림 | 시간 때문에 실패한 실제 요청이 측정되고 UX/대기열 예산 합의가 있을 때 | 4GiB 사전 거절이나 메모리 사용을 해결하지 않음. 긴 CPU 작업·대기열 체류가 더 길어질 수 있어 현재 단독 대안으로 비권고 |

권고: 현재60초와 제품600초 숫자를 이 작업에서 바꾸지 않는다. 먼저 지원할 최대 곡/구간·최저 BPM, 동시 분석 수와 메모리 예산을 팀이 합의한다. 단기 범위 제한을 선택하면 느린 전곡 사용성을 명시하고 모든 계층에서 같은 제한을 적용한다. 긴 구간이 필수면 메모리 절감 DTW의 별도 설계·품질 검증을 선행한다. 운영 긴 요청 동시성 검토도 필요하지만 이 문서는 workers/한도 설정을 변경하지 않는다.

### 운영 재측정 계획 (이번에는 미실행)

- 담당: AI가 채점 품질/추정·DTW 프로파일, BE가 요청 시작·대기열·DB 시각, Infra가 승인된 제한 환경과 컨테이너 peak/CPU/동시성, Data가 최대 곡·구간과 대표 녹음을 정한다.
- 승인 전 준비: 운영 이미지와 동일한 격리 컨테이너 또는 스테이징, 네트워크/자격 증명 최소화, 입력 파일·해시·모델/이미지 revision·메모리/CPU 한도·백그라운드 부하 고정. 15/30/60/120초 및 지원할 최대 구간을 설계하되 가드 초과는 사전 판정해 큰 할당을 금지한다.
- 저부하 창과 중단 기준을 합의한 뒤 cold/warm·직렬/동시를 분리한다. 최소5회 교차 실행으로 입력 수·실패/거절/timeout·전체 지연 중앙값/범위와 p95(표본 수 부족 표기), 메모리 peak·정확도 변화를 남긴다. 운영 긴 요청2개를 바로 실행하지 않는다.
- 전체 마감은 PENDING 시작부터 평가 저장까지 잰다. 다운로드/디코딩/추정/DTW/저장과 queue 대기를 구분하고 서명 URL·개인 녹음/토큰을 로그에 넣지 않는다. 합성 입력을 실제 대금 녹음 품질 증거로 보고하지 않는다.

### 재현

저장소 루트에서 `uv sync --project ai --offline --locked` 후 아래 스크립트를 `/private/tmp/pra282-check.py`로 저장한다. `PYTHONPATH=ai/src NUMBA_CACHE_DIR=/private/tmp/pra282-numba OPENBLAS_NUM_THREADS=1 ai/.venv/bin/python /private/tmp/pra282-check.py`로 실행했다. stdout JSON이 원시 관찰값이며 다음 코드는 실제 실행한 스크립트다. 별도 모델 추론·인터넷 호출·운영 쓰기 없음.

```python
import json,math,re,time,tracemalloc,platform,sys,statistics
from pathlib import Path
from unittest.mock import patch
import numpy as np,librosa
from ai_service.pipelines.scoring import score,Note,HWANG_HZ,HOP_S
from ai_service import api
root=Path.cwd();rows=[]
# Warm the actual score/DTW on only 100 frames; no pYIN/download/decoder work.
score(np.full(100,HWANG_HZ),[Note(0,1,start_beat=0)],60,HWANG_HZ)
for seconds in (2,4,8):
 for run in range(3):
  f0=np.full(seconds*100,HWANG_HZ)
  tracemalloc.start();t=time.perf_counter()
  result=score(f0,[Note(0,seconds,start_beat=0)],60,HWANG_HZ)
  elapsed=time.perf_counter()-t;_,peak=tracemalloc.get_traced_memory();tracemalloc.stop()
  assert result.status=='DONE'
  rows.append(dict(seconds=seconds,run=run,seconds_elapsed=elapsed,peak_bytes=peak,bytes_per_cell=peak/(seconds*100)**2,status=result.status))
class ShapeAudio:
 def __init__(self,seconds):self.seconds=seconds
 def astype(self,*args):return self
 def __truediv__(self,*args):return self
 def __len__(self):return round(self.seconds*16000)
class ReachedPitch(Exception):pass
def pitch(*a,**k):raise ReachedPitch()
guards=[]
for expected,recorded in [(120,120),(133,133),(134,134),(600,600),(15,600)]:
 request=api.AnalysisIn(sessionId=1,attempt=1,audioUrl='https://invalid.example/test',bpm=60,bpmUnit='QUARTER',notes=[dict(idx=0,semitone=0,startBeat=0,beats=expected,sigimsae=False)])
 with patch.object(api,'decode_recording',return_value=ShapeAudio(recorded)),patch.object(api,'pyin_f0',side_effect=pitch):
  try:api._score_recording(Path('unused'),request)
  except api.RecordingTooLong:outcome='rejected-before-pitch'
  except ReachedPitch:outcome='passed-memory-guard'
 guards.append(dict(expected=expected,recorded=recorded,guard=outcome,estimate_gib=expected*recorded*10000*24/2**30))
seed=(root/'backend/src/main/resources/db/migration/V6__seed_danso_songs.sql').read_text();songs=[]
ref=json.loads((root/'data/reference/songs.json').read_text())['rows']
for song,m in zip(ref,re.finditer(r'\{\s*"schemaVersion"',seed)):
 d,_=json.JSONDecoder().raw_decode(seed[m.start():]);notes=[n for n in d['events'] if n['kind']=='NOTE'];tpb=d['music']['ticksPerBeat']
 span=(notes[-1]['startTick']+notes[-1]['durationTick']-notes[0]['startTick'])/tpb
 length=max(e['startTick']+e['durationTick'] for e in d['events'])/tpb
 songs.append(dict(title=song['title'],bpm=song['default_bpm'],score_beats=length,note_span_beats=span,full_seconds=length*60/song['default_bpm'],note_span_seconds=span*60/song['default_bpm'],full_seconds_at20=length*3,guard_gib_at20=span*3*length*3*10000*24/2**30))
out=dict(python=sys.version,platform=platform.platform(),numpy=np.__version__,librosa=librosa.__version__,score=rows,guard=guards,songs=songs,equal_length_boundary_s=math.sqrt(api.MAX_DTW_BYTES/api.DTW_BYTES_PER_CELL)*HOP_S)
print(json.dumps(out,ensure_ascii=False,indent=2))
```

기존 가드 회귀: `NUMBA_CACHE_DIR=/private/tmp/pra282-numba ai/.venv/bin/python -m pytest ai/tests/unit/test_api.py -k 'dtw or memory' -q` → 3 passed, 25 deselected. 코드 변경 없이 실행했으며 pytest 캐시 쓰기 권한 경고는 기록했다.
