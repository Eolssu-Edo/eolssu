# CAT03~10 앱 통합과 검증

2026-10-29, [S15P31E102-261](https://ssafy.atlassian.net/browse/S15P31E102-261). 담당 박찬형, Story109 관련 연결, Epic1, sprint59139, 진행 중. 사용자가 API의 앱 연결 및 필요한 MR 생성을 요청했다. 커밋·Push·MR까지 허용되며 사람의 병합·서버 배포·Play 제출은 별도다.

서버는 [BE288 MR !166](https://lab.ssafy.com/s15-final/S15P31E102/-/merge_requests/166) `eb32519c2b69cf97636242e32e0ad05b118b9a47`의 CAT03~10 계약을 따른다. 앱은 기존 256/285와 병합된 245/250/191의 실제 세션·업로드·분석·연습 시간 전송을 재사용한다. 완료 Task를 재개하지 않았다. 원래 Task261의 전체 Play 출시 완료 조건은 아직 충족하지 않아 앱 MR은 Draft로 검토한다.

## 기능 추적표

| 기능/화면 | 확정 근거 | 코드·계약 | Jira·MR | 구현 상태 | 통합 상태 | 검증 결과 | 미결정·의존성 | 영향·우선순위 | 조치·담당 | 증거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 활성 악기 | DATA·실제 DB | InstrumentCatalog / CAT03 | 261·288 !166 | 구현 완료 | 앱 MR 검토, 서버 MR 미병합 | 통과: 실제 HTTP ID/code | 서버 배포 | P1 선택/가입 | 288 담당·배포 담당 | CatalogLocalServerTest |
| 곡 목록·상세·연습 악보 | 공통 score schema1 | ApiSongRepository / CAT01·02·04 | 256/285 병합,261·288 | 구현 완료 | 후속 앱 미병합 | 통과: 4곡 원본 ID/tick/NOTE idx·버전, Android 표시 | CAT04 배포 선행 | P1 핵심 연습 진입 | 261·288 | ApiSongScoreContractTest, debug 캡처 |
| 모범 연주 | CAT05 metadata·현재 악보 버전 | ModelAudioSession / AndroidAudioPlayer | 261·288 | Android 구현 완료 | 앱·서버 미병합 | 통과: strict DTO, 실제 HTTPS M4A native probe, 404 안내 | 공개 음원 0개·권리 미확인 | P1 콘텐츠 없음 | DATA/권리/등록 담당 미정 | ModelAudioRepositoryTest·ModelAudioSessionTest·probe JSON |
| 율명·운지 | 실제 활성 악기·pitchId | ReferenceCatalogRepository / CAT06·07 | 261·288 | 구현 완료 | 앱·서버 미병합 | 통과: 음계11개·빈 운지·오류/재시도 | 운지 콘텐츠 없음, 대금 유형 임의 선택 안 함 | P1 운영 데이터 | DATA 담당 미정 | GuideCatalogViewModelTest·실제 HTTP |
| 장단·악보 기호 | DATA 장단/기호 계약 | CAT08·09·10 / ReadingRoute | 261·288 | 구현 완료 | 앱·서버 미병합 | 통과: 장단3개·상세·빈 기호 | 등록 자료·권리 | P1 | DATA 담당 미정 | ReferenceCatalogRepositoryTest·실제 HTTP |
| 평가 조회·복구 | PRA03·평가 조회 스펙 | ApiEvaluationRepository / ResultViewModel | 245/250 병합,261 보완 | 구현 완료 | 보완 미병합 | 통과: 버전·noteIdx·MISSED·오류 재시도·캐시 제거 | 실제 계정→분석 E2E 미검증 | P0 핵심 흐름 | FE/QA·서버 담당 | ApiEvaluationRepositoryTest·CatalogLookupTest |
| 로그아웃·계정 전환 | 기존 SignedInMember·쿠키 계약 | 큐 완료 후 이동, 대기 녹음 owner | 191/245 병합,261 보완 | 구현 완료 | 보완 미병합 | 통과: 소유자·재전송·401 갱신 회귀 | 이전 owner 없는 파일 보존/재전송 불가 | P0 타 회원 데이터 보호 | 261 | 후속 회귀 검사 |
| 기록 저장·조회 | 실제 이력 API 미존재 | PracticeRecordRepository | 261 | 미구현(실제 서버 이력) | 샘플 출시 DI 제거 | 저장 불가 안내 통과, 실제 이력 미검증/차단 | 이력 계약·구현 담당 확인 필요 | P1 확정 기능 | 팀 담당 미정 | backend controller·AppModule 조사 |
| 가입→로그인→녹음→분석 | 팀 필수 출시 흐름 | 운영 HTTPS | 261 | 부분 구현 | 다수 병합, 이번 보완 미병합 | 미검증 | 운영 terms items=[]·심사 계정·콘텐츠 | P0 출시 차단 | 서버/정책/QA 담당 | production-public-get.json |

## 변경 이유와 복구

CAT04의 원본 악보와 projection을 모두 검사한다. CAT02와 곡·버전·BPM 단위·음표 수가 다르거나 eventId/noteIdx/startTick/durationTick/칸 위치가 어긋나면 오류로 표시한다. 샘플 악보나 enum 순서의 악기 ID로 대체하지 않는다. 이전 285의 fixture 변환기는 commonTest로 격리했다.

모범 연주는 실제 MediaPlayer 위치에서 악보 tick을 계산한다. startOffset과 음원 꼬리 시간을 구분하며 시간만 흐르는 가짜 재생을 제거했다. Android worker looper에서 준비·재생하고 포커스/헤드폰 변경/화면 이탈에 멈춘다. 로딩, 미등록, 미지원, 재시도 상태가 있다. iOS/Web의 실제 플레이어는 미구현이며 지원 불가로 표시한다. Android 구현을 commonMain에 넣지 않았다.

평가 응답의 scoreVersion을 보존해 UI의 두 번째 곡 조회에서도 비교한다. 다른 버전의 오류 좌표를 새 악보에 표시하지 않는다. `MISSED`를 음 길이 점수와 혼동하지 않고 별도 음 미검출 오류로 표시한다. 알 수 없는 판정·음표 번호를 정상으로 무시하지 않는다. 매 조회마다 서버 소유권을 확인하며 이전 회원의 결과 캐시를 재사용하지 않는다.

## 검사 조건과 증거

로컬 증거 루트: `C:/Users/kingc/.codex/visualizations/2026/10/29/01a185a6-eb94-7bb0-a047-67ddd8275410/android-api-integration-261/`. 큰 로그·패키지·스크린샷·검증용 키는 Git에 넣지 않는다. 별도 서버 증거는 형제 `catalog-api-288-mr/`, 오디오 검사는 `model-audio-native-probe/`에 있다.

| 기준 ID·출처 | 성격 | 적용·시나리오 | 환경·입력 | 기대 결과 | 실제 결과·증거 |
| --- | --- | --- | --- | --- | --- |
| TEAM-CAT03~10 | 팀 확정 API 계약 | 앱 Repository→실제 Spring HTTP | MySQL8.4 전용DB, server eb32519, 127.0.0.1:18088 | 4곡·원본 ID/tick/버전, 음계11·장단3·빈 선택 콘텐츠 | 통과, CatalogLocalServerTest 포함 |
| TEAM-REGRESSION | 팀 검사 | host tests·JS/Wasm 컴파일 | JDK21, Gradle9.5.1, Kotlin2.4.20, Compose1.12.1 | 성공·skip 여부 명시 | 473개/71suite 통과, 실패·오류·skip0; JS/Wasm 컴파일 성공 |
| Audio_Focus_Request/Change | Android 품질 권고 | 실제 native 재생·정지·포커스 | API37 x86_64, getconf16384, p172 생성 M4A | 위치 증가·pause 고정·focus loss 중단·release | 통과: audio-probe.json. 동일 UID 두 번째 OS focus client이며 타앱/통화 검증 아님 |
| TEAM-PUBLIC-UI | 팀 통합 검사 | 홈→4곡→CAT04악보→CAT05 미등록 | API37 16KB 전용 AVD, debug loopback | 실제 공개 조회·정직한 빈 상태 | 통과, screenshots/debug-*; 로그인/동의/분석 성공 아님 |
| TEAM-ARTIFACT | 팀 완료조건 | 동일 버전 APK/AAB·서명·AAB 파생 설치 | versionCode4, 로컬 검증키 | 서명·bundletool·ELF/ZIP·설치 | 최종 빌드 대기 |
| Touch_Target_Size / Visual_Contrast / T-Config_State / T-Multi-Window_Resources | Android 품질 권고·팀 필수 검사 | 큰 글꼴·태블릿·창 변경·오디오 경합 | 이전 v3 범위와 이번 v4 delta 구분 | 잘림·상태 유실·마이크 경합 없음 | 이번 delta 전체 미검증. 이전117보드/111태블릿 대응표를 전체 통과로 확대하지 않음 |
| DEVICE01~18 | 프로젝트 실기기 품질 조건 | 실제 마이크·음질/지연·타앱/통화·장시간 | 실제 Android 폰·정상 서버·개발계정 | 기존 인계표 기대 결과 | 미검증: 이번 PC에 기기 없음, 담당 미정 |

debug 공개 조회는 전용 AVD에 `first_run_completed=true`만 주입했다. 로그인·동의·세션 검증을 우회해 성공했다고 보고하지 않는다. 로컬 서버에는 실제 계정·녹음·분석 세션을 만들지 않았다. release는 production HTTPS 고정이며 이 fixture나 로컬 CA를 포함하지 않는다. native probe는 별도 debug package로 설치 후 삭제했고 OS CA를 바꾸지 않았다.

## 정책·제출 한계

공식 play-policy-insights의 기존 정식 init→worker→aggregate→critic→report 결과는 `Needs Review`이며 새 통과로 바꾸지 않는다. 이번 delta는 공개 카탈로그 GET, HTTPS 음원, 기존 마이크/오디오·회원 데이터 보호이며 신규 SDK·OS 권한·운영 보관 정책은 추가하지 않는다. 재생 호스트의 IP/요청 로그 처리는 실제 운영 CDN·보관 설정을 확인해 Data safety 초안과 대조해야 한다. 공개 음원 URL·해시의 형식 검사는 실제 저작권 승인이나 서버 해시 검증을 대신하지 않는다.

운영 비인증 GET(2026-10-29T07:29Z): songs200/4개, instruments404, terms200/빈 목록. 서버 MR 병합·배포와 승인 콘텐츠 등록이 필요하다. 공식 업로드 키, Play app signing 인증서, Console 최고 versionCode/계정 조건, 공개 개인정보 URL·웹 삭제 경로·대상 연령·권리·심사 계정은 미확인이다. 기존 스토어 문구/Data safety/영어 접근 설명 초안과 실기기 인계표는 version3 자료를 유지하며 실제 앱 전체 흐름 검증 후 확정해야 한다.

개발 환경·로컬 산출물 검사와 Play 제출 가능 여부는 다르다. A 개발 환경은 최종 검사로 판정하고, B 로컬 서명 산출물 검증은 공식 upload key 미확인과 구별한다. C 제출 준비는 P0/P1·필수 미검증 때문에 미완료, D Google 심사 승인은 제출하지 않아 미검증이다. 실기기 부재만 남은 상태로 보고하지 않는다.
