# 기본음·시김새 M4A 카탈로그

- Task: S15P31E102-364, Story S15P31E102-28. 2026-11-02 사용자 요청: Jira 등록, M4A 생성, DB/API 구성.
- 기존 4악기 라벨링 WAV 430개를 재사용한다. 산조대금 121, 정악대금 107, 단소 56, 소금 146개다.

## 동작과 경계

WAV 원본의 해시·샘플레이트·채널·라벨 ID·출처·추정 상태를 유지하고 배포용 AAC-LC M4A를 생성한다. 원본 음높이·구간을 다시 조율하거나 라벨을 확정 정답으로 승격하지 않는다. 각 출력은 전체 디코드와 길이 대조를 통과해야 한다. 출력 폴더는 새 경로만 허용하고 실패 시 완성된 manifest를 남기지 않는다.

DB에는 `reference_audio_assets`를 추가한다. 기존 `pitches`는 악기별 녹음 ID가 아니므로 변경하지 않는다. 기존 단음의 임의 대표 파일 선택을 피하고 `datasetId + labelId`를 자산 식별 기준으로 사용한다. 430개 모두 선택 가능한 새 조회 API를 제공한다. 기존 CAT-06/08, FE 재생 동작과 사용자 녹음 저장 정책은 보존한다.

## 변환·적재 계약

- 출력 manifest schemaVersion=1, `assets` 배열. `assetId`는 UTF-8 `datasetId + "\n" + labelId`의 SHA-256 소문자 64자리다.
- 공통 필드: assetId, datasetId, labelId, category, technique, register, yulmyeong, displayLabel, file, objectKey, contentType, sha256, bytes, durationMs, sampleRateHz, channels, review, eligibleForNoteScoring, metadata.
- datasetId: danso, sogeum, jeongak-daegeum, sanjo-daegeum. 원래 catalog datasetId와 일치해야 한다.
- objectKey: `reference-audio/v1/{datasetId}/{assetId}/{sha256}.m4a`. 사용자 녹음의 `recordings/` 및 그 30일 삭제 규칙을 사용하지 않는다.
- contentType=`audio/mp4`; AAC-LC 목표 192kbps, 원본 샘플레이트·채널 유지. metadata는 원본 label/source와 WAV SHA·크기·프레임 정보를 포함한다.
- 적재는 별도 CLI가 검증한 manifest에서 재실행 가능한 SQL을 만든다. 기본 DRAFT. 동일 ID의 다른 파일/메타데이터를 무조건 덮어쓰지 않는다. 실제 저장소 게시 검증 뒤 PUBLISHED로 전환한다.

## 조회·재생

- `GET /api/reference-audios`: datasetId(필수), category/technique/register/yulmyeong 선택 필터. PUBLISHED만 안정된 순서로 반환.
- `GET /api/reference-audios/{assetId}`: 게시된 자산 상세와 출처·검수 상태.
- `GET /api/reference-audios/{assetId}/play`: 게시된 키에 대해서만 만료형 서명 GET URL로 이동. DB에는 만료 URL을 저장하지 않는다. 응답은 캐시하지 않는다.
- 상세/목록의 soundUrl은 위 재생 경로로 연결한다. 상대 경로임을 계약에 명시한다. 저장소 미설정 시 재생은 공통 형식 503, 없거나 DRAFT인 ID는 404, 잘못된 입력은 400.
- 기존 공개 카탈로그처럼 읽기만 공개한다. 임의 object key나 URL 입력으로 서명하는 API와 공개 쓰기 API는 만들지 않는다.
- 별도 REFERENCE_AUDIO_STORAGE_* 설정으로 읽기용 서명기를 구성한다. 사용자 녹음의 local/s3 선택·비밀값·삭제 동작과 독립적이다. 운영 버킷 공개 정책은 변경하지 않는다.

## 검증과 제외

작은 합성 입력의 실패→성공 검사, 430개 원본 해시·출력 디코드·길이·매핑, clean build, 폐기 가능한 MySQL의 마이그레이션·적재 재실행·필터·공개/초안·오류와 실제 HTTP, 서명 URL의 키·만료·캐시 검사를 수행한다. 운영 R2 업로드·마이그레이션·앱 배포·Push/MR은 이 구현 요청에서 실행하지 않는다. 최종 청감/전문가 라벨 검수는 자동 검증과 구분한다.

## 프론트 연결 추가 요청 (2026-11-02)

사용자가 DB/API/env 설명과 함께 기존 프론트의 소리 연결을 요청했다. 같은 Task의 추가 작업으로 운지법·기초 학습의 음 듣기를 새 카탈로그에 연결한다. 최초 구현의 FE 제외 범위는 이 추가 요청으로 아래 범위만 확장한다.

- GuideInstrument의 단소·소금·정악대금·산조대금을 각각 danso·sogeum·jeongak-daegeum·sanjo-daegeum으로 명시적으로 대응한다. 회원용 DAEGEUM만 보고 두 종류 중 하나를 추정하지 않는다.
- 현재 악기·취법을 고정하고 실제 라벨을 조회한다. 기존 [단소](../knowledge/data/danso.md), [정악대금](../knowledge/data/jeongak-daegeum.md), [산조대금](../knowledge/data/sanjo-daegeum.md), [소금](../knowledge/data/sogeum.md) 라벨링 문서에 근거한 명시적 별칭만 조회에 사용한다(예: 단소 역취 청중→중). 화면의 원래 음 이름은 유지한다. 소금 역취 청임→임과 중청임→중청임은 구분하고 자료 없는 중청남을 남으로 대체하지 않는다. 청/탁/중청 접두사를 일괄 제거하지 않는다. 같은 율명의 여러 주법·구간은 원래 labelId와 함께 모두 표시하고 사용자가 선택한다.
- 음 듣기에서 음원 선택 창을 열고, 해당 악기의 전체 기본음계/시김새 녹음도 탐색할 수 있게 한다. 로딩·빈 목록·실패/재시도·닫기를 제공한다.
- 기존 운지법의 외부 URI 재생 흐름을 유지한다. 상대 soundUrl은 설정된 API 주소로 해석하고 선택 자산의 정확한 /play 경로만 허용한다. 재생 열기 버튼은 외부 재생임을 명시하고 사용자 클릭에서 실행한다. 표시만 바꾸던 기초 학습의 1초 재생 강조는 이 연결에서 사용하지 않는다.
- 시김새 그림의 로컬 합성 MP3는 시간표와 결합돼 있고 원본 녹음과 일대일 대응하지 않아 유지한다. 실제 시김새는 원래 주법 이름으로 목록에서 고른다. 장단 자산 제작/패널 재생 Task340 및 네이티브 공통 재생기 확장은 포함하지 않는다.
- 새 API 조회·상대 URL·4악기/취법/원래 율명·중복·빈 결과·오류·취소 경계를 작은 테스트로 검증한다. Android host 검사 및 Web 빌드, 로컬 브라우저에서 목록/선택/오류 흐름을 확인한다. 실제 R2 게시와 모바일 실기기 청취가 없으면 별도 미검증으로 명시한다.

## 2026-11-02 수정 요청: 업무 분할과 앱 내부 재생

사용자가 외부 재생을 앱 내부 재생으로 바꾸고 파일명/labelId를 숨기도록 명시했다. 이전 외부 URI 방식의 범위를 이 요구로 대체한다. DB/API364, 변환/검증366, 앱재생367, 운영게시368로 분리하며 Story28/Epic16 연결을 유지한다.

367은 기존 공통 AudioPlayer 계약으로 화면 전용 플레이어를 생성한다. Android는 기존 MediaPlayer 구현, Web은 HTMLAudioElement, iOS는 AVAudioPlayer와 앱 HTTP 클라이언트를 사용한다. 재생/정지/완료/오류를 표시하고 새 자산·필터 변경·창 닫기·백그라운드 전환 시 이전 재생을 멈춘다. 식별자는 내부 조회에 보존하며 UI는 주법·취법·율명과 연주 예시 번호를 표시한다. Windows에서 iOS 빌드 및 실기기 음질 검증은 수행할 수 없으므로 결과를 구분한다.

pitches와 reference_audio_assets는 각각 음높이 기준/실제 파일이라는 물리적 분리를 유지한다. 정확히 검수된 관계가 생기면 악기·취법·주법을 포함한 연결 관계를 모델링해야 하며, 현재 inferred 라벨에 FK/대표음을 임의 부여하지 않는다. 이 관계 설계는 이번 UI 수정에서 확정하지 않는다.

R2는 S3 호환 API를 제공한다. 기존241/272/335의 주소·버킷·자격증명 설정이 있으며 별도 AWS S3 서비스는 필요 없다. 학습 음원 설정은 같은 R2 계정/엔드포인트를 재사용할 수 있고 버킷/읽기 권한/영구 보관 prefix는 인프라 적용368에서 확인한다.

## 2026-11-02 후속 확정: 기본음과 기준 음의 명시적 관계

사용자의 관계 보완 요청으로 앞 절의 관계 보류 결정을 다음 범위에서 대체한다. `reference_audio_assets.pitch_id`는 nullable FK로 `pitches.id`를 참조한다. 기준 음 하나에 여러 주법·세기의 기본음 녹음이 연결된다. 이 연결은 라벨이 뜻하는 명목상 음의 식별이며, 실측 주파수 검수나 채점 정답 승격이 아니다. 시김새와 근거가 없는 음원은 null이다.

V13은 유지하고 V14에서 FK와 `pitch_mapping_basis`를 추가한다. 둘은 함께 null이거나 유효한 값이어야 하며 기본음만 연결한다. 명시적 매핑 CLI는 검증된 manifest의 자산·파일 해시와 연결 근거를 확인하고 `(yul, octave)`로 대상 pitch를 찾는다. 없는 대상·충돌은 전체 배치를 거부하고 동일 매핑 재실행과 기존 import는 상태를 보존한다. 기존 라벨 문서와 기준 데이터가 일치하는 단소 기본음 30개만 매핑한다. 나머지 400개는 null이다.

목록과 상세의 자산에 `pitchId: string | null`을 추가한다. 목록은 선택 `pitchId` 필터를 제공하며 1부터 9223372036854775807까지의 선행 0 없는 십진 문자열만 받는다(그 외 공통 400). 미연결은 JSON null, 없는 양수 ID는 빈 목록이다. 근거는 DB/매핑 파일에 보관하고 공개 응답 필드는 늘리지 않는다. 기존 경로·응답 봉투·soundUrl·재생 동작을 보존한다.
