# 공통 악보 v1 재생 연결

- 작업: [S15P31E102-338](https://ssafy.atlassian.net/browse/S15P31E102-338), Story 30 모범 연주 재생.
- 요청: 기존 공통 악보와 재생기를 연결하고 한 곡의 화면 음·실제 생성 음원의 음높이, 길이, 쉼을 대조한다.
- 입력 계약: [score_data.schema.json](../../data/src/scores/score_data.schema.json). 새 서비스 스키마나 API는 만들지 않는다.

## 동작

기존 `python -m data.src.playback`이 서비스 JSON을 판별해 기존 reviewed-score IR → MIDI → CAT PCM → M4A/WAV 경로를 사용한다. `--common-bpm`은 `music.bpmUnit`의 분당 박 수이며 반드시 명시한다. QUARTER는 1, DOTTED_QUARTER는 3/2 quarter로 길이와 BPM을 함께 변환한다. JEONGGAN/JANGDAN_BEAT는 서양 음가를 추측하지 않고 MIDI 전송용 1 quarter를 1 입력 박에 대응시킨다. 어느 경우도 `tick/ticksPerBeat*60/bpm` 초를 보존한다.

원본 바이트, schemaVersion, scoreVersion, provenance, eventId/cellId 및 표시 자리와 초 대응표를 결과 묶음에 보관한다. 인접한 동음은 새로 발음하고 이미 합쳐진 지속 이벤트는 다시 나누지 않는다. `music.hwangHz`를 실제 음높이 기준으로 사용하며 악기 변경·옥타브 접기는 하지 않는다. 별도로 요청한 기존 transpose 옵션은 결과에 기록한다.

NOTE의 release를 원본 시간 안에 넣어 REST를 침범하지 않는다. 숨표는 기존 adaptive_breath 정책(목표 무음120ms, release와 합쳐 최대 음 길이 절반)을 사용하고 다음 음 시작을 유지한다. 음원의 후행 여백은 기존 MIDI 정책의 약550ms이며 악보 길이와 구분한다. 원본 reviewed는 악보 검수 상태이고 합성 음원의 전문가 검수 완료를 뜻하지 않는다.

스키마 위반, 표시와 음높이·시간의 불일치, 중복 ID, 미지원 악기/음역, rejected 원본, MIDI에서 정확히 표현할 수 없는 분할, 비어 있지 않은 sigimsae는 입력 위치를 포함한 오류로 중단한다. v1의 합쳐진 sigimsae만으로는 원래 장식의 배치·조를 복원할 수 없다. 기존 JGB/IR 입력의 동작은 유지한다.

## 완료 조건과 경계

- 작은 고정 입력으로 지속/반복음/앞·중간·끝쉼/숨표/조율/명시 BPM/미지원 오류 및 출력 묶음 보존을 검사한다.
- 검수된 p173 아리랑의 실제 FE mapper 출력(61 NOTE, 4 REST, 144ticks)을 원본 및 재생 대응표와 교차 검증한다. 기본 BPM96 근거는 `data/reference/jangdans.json`이다.
- 실제 CAT 샘플로 WAV와 AAC-LC M4A를 생성하고 실제 PCM 음높이·쉼·발음 구간·전체 시간을 측정한다. 합성 테스트는 실제 CAT 검사를 대신하지 않는다.
- 가능한 Android 검증 환경에서 실제 정간보 컴포넌트와 오디오 플레이어를 실행한다. 에뮬레이터 증거와 물리 기기 청취는 구분한다. 미실시 청감을 실시했다고 기록하지 않는다.
- 새 악보 렌더러(336), 인식(313), 서비스 업로드 연동(347), 곡 전수 검수는 제외한다.
