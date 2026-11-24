# 웹 장단 재생(Web Audio) 지원 범위와 브라우저 확인 (S15P31E102-1260)

웹(Kotlin/Wasm·JS)의 '장단 연습' 모드와 장단 설정 패널의 '장단 듣기'를 Web Audio 예약 재생으로 지원하면서 확인한 브라우저 동작·제약·측정 결과다. 구현과 결정은 [작업 기록](../../plans/web-parity/S15P31E102-1260-handoff.md)에 있다.

## 구조

경로는 `frontend/shared/src/webMain/kotlin/com/ssafy/eolssu/platform/audio/` 아래다.

| 파일 | 역할 |
| --- | --- |
| `WebJangdanSampleCache.kt` | 앱 전체 `single`. manifest의 SHA-256으로 SRI(`integrity`) fetch → 크기 확인 → 44.1kHz `OfflineAudioContext`로 디코딩한 `AudioBuffer`를 메모리에 둔다 |
| `WebAudioOutput.kt` | 앱 전체 `single`. `AudioContext` 하나와 사용자 제스처 리스너를 가진다. 플레이어가 붙어 있는 동안만 깨어 있고, 마지막 플레이어가 떨어지면 `suspend()`한다 |
| `WebJangdanSamplePlayer.kt` | 화면별 `factory`. 타격마다 `AudioBufferSourceNode → GainNode → destination`을 만들어 `start(when)`으로 예약한다 |
| `WebAudio.kt` | 위 세 파일이 쓰는 Web Audio·fetch external 선언과 `js()` 도우미 |

## 브라우저 동작에서 정한 것

- **서명 주소와 SRI:** `GET /api/jangdan-samples/{sample}/{sha256}`는 비공개 R2 서명 주소로 302 이동한다. `fetch(url, {integrity: 'sha256-<base64>'})`는 이동 뒤 받은 바이트로 검사한다. R2 응답에 `Access-Control-Allow-Credentials`가 없으므로 `credentials` 기본값(`same-origin`)을 바꾸지 않는다.
- **R2 CORS 허용 출처:** `https://k15e102.p.ssafy.io`와 `http://localhost:8080`뿐이다(2026-11-23 curl로 확인). 로컬 확인은 8080에서만 된다.
- **디코딩 길이:** 44.1kHz `OfflineAudioContext.decodeAudioData`의 길이가 manifest `frames`와 같았다(Chromium 156, 2026-11-23). 그래도 재생은 manifest 길이까지로 자른다.
- **자동 재생 해제:** 연습 화면이 열려 있는 동안 `window` 캡처 단계의 누름·키 입력마다 문맥을 만들거나 `resume()`한다. Safari는 제스처 안에서 시작한 문맥에만 소리를 허락하고 그 허락이 문맥에 남으므로, 탭을 가리거나 다른 화면에 다녀올 때 문맥을 닫지 않고 멈췄다가 다시 쓴다.
- **다시 쓰기:** `PracticeViewModel.onScreenHidden()`(탭 숨김·화면 이탈)과 재생 실패 때마다 `release()`가 불리고, 같은 플레이어로 다시 튼다. `release()` 뒤의 `prepare()`는 멈춘 문맥이 다시 돌 때까지 최대 300ms 기다려 첫 타격을 버리지 않는다.
- **예약 시각:** `getOutputTimestamp()`를 `performance.now()`로 보간한 '들리는 지금'에 출력 지연+20ms와 타격까지의 시간을 더한다. 멈췄다 깨어난 직후처럼 보간 값이 `currentTime`보다 50ms 넘게 앞서면 낡은 값으로 보고 `currentTime - (baseLatency + outputLatency)`를 쓴다.
- **굿거리 굴림:** Web Audio에는 음높이를 지키는 속도 변경이 없다. 계획이 고른 가장 가까운 BPM 원본을 원래 속도로 틀고, 박보다 길면 박 길이에서 자르며 끝 5ms를 줄인다. 박보다 짧으면 늘이지 않는다. 59~119 BPM 안에서 원본과 박 길이 차이는 최대 약 16%다. 이 범위 밖에서는 굴림이 일찍 끝나거나 더 많이 잘린다.

## 브라우저 확인 결과

2026-11-24, macOS, Claude 앱 인앱 브라우저(Chromium), 개발 배포본, 개구리소리(굿거리, 99 BPM 4분음표 기준, 8분음표 303.03ms). `AudioBufferSourceNode.prototype.start`를 감싸 예약 시각(`when`)을 기록했다. 소리를 녹음해 잰 값이 아니라 오디오 시계 기준 예약 값이다.

| 항목 | 결과 |
| --- | --- |
| 장단 듣기 | 문맥 `running`, 지원 안 함 안내 없음. 8분음표 간격 303.14·302.89·303.03·303.12ms, 굴림 `gutgeori_roll_99` 26,727 frame 그대로. 첫 타격 예약 앞 17ms, 이후 약 137ms(미리 예약 120ms+지연) |
| 정지·일시정지 | 정지 뒤 새 타격 0 |
| 화면 이탈·재진입 | 떠나면 공유 문맥 `suspended`, 돌아와 장단 연습을 틀자 같은 문맥이 다시 `running`, 첫 타격 버림 없음. 샘플은 앱 캐시로 다시 받지 않음(11개 중 쓴 7개 각 1회) |
| 탭 숨김(인앱 창은 `visibilitychange`를 내지 않아 `document.hidden`을 덮어써 시뮬레이션) | 타격 즉시 멈춤, '다른 소리나 기기 연결 변경으로 장단을 멈췄어요.' 안내. 다시 보이게 한 뒤 재생 정상 |
| 속도 109·59 BPM | 8분음표 275.36·275.13ms, 508.43·508.55ms. 굴림 109·59 원본을 자르지 않고 그대로(곡 속도 단계가 굴림 원본 BPM과 같음). 자르기(`targetFrames` < 원본)는 내 악보의 중간 BPM에서만 생기며 웹 단위 테스트로만 확인 |
| 모범 연주 반주 | `<audio>` 위치로 환산하면 타격이 맞는 8분음표 칸에 오되 약 36~43ms 늦게(첫 타격 27ms) 들리도록 예약됐다. 그중 약 33ms는 계약대로 더하는 출력 지연+여유 20ms다(Android도 출력 지연을 더함) |
| 시작 첫 타격 | 59 BPM으로 바꾼 직후 첫 '덩'이 31ms 늦게 예약됐다(99 BPM에서는 약 1ms). 공통 타이밍 코드의 시작 지연이며 웹 플레이어와 무관 |

macOS Safari 26.2는 확인하지 못했다(사용자 청취 답변과 계측이 맞지 않아 미확인으로 남김). Firefox·iOS Safari도 미확인이다.

## 남은 제약

- **모범 연주 반주의 시계:** 모범 연주(S15P31E102-1259)는 `<audio>`의 `currentTime`을, 장단은 Web Audio 시계를 쓴다. 두 시계의 출력 지연 보정이 같다는 보장이 없다.
- **코덱:** AAC(M4A) 디코딩은 OS 코덱에 기대는 브라우저가 있다. 디코딩이 실패하면 `prepare`가 false를 돌려주고 장단이 '실패'로 끝난다.
- **AAC priming:** 브라우저가 앞 여백을 덜 자르면 타격이 그만큼 늦게 들린다. 앞쪽 지연은 보정하지 않는다.
- **메인 스레드:** 웹은 단일 스레드다. 120ms 앞 예약으로 흡수하지만 메인 스레드가 그보다 오래 막히면 늦은 타격을 `JangdanCursor`가 버린다(몰아서 울리지 않음).
- **재다운로드:** 서명 주소가 매번 달라 HTTP 캐시가 듣지 않는다. 메모리 캐시라 새로고침마다 11개 약 76KB를 다시 받는다.
- **녹음 중 장단:** 웹 녹음(S15P31E102-1262)이 생기면 스피커 장단이 녹음에 섞이는 문제(S15P31E102-878)를 웹에서도 정해야 한다. 지금은 녹음이 없어 `NoJangdanVolumeLimit`을 쓴다.
