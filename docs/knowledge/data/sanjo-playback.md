# 산조대금 JGB·MIDI·CAT 음원 변환의 범위

S15P31E102-107 · 2026-10-28. 기존 산조 프로필을 최신 Data 공통 엔진과 단소 M4A 출력 기반에 연결한다. 악기 종류·황종 기준·빠르기·필요한 상대음 음급과 표현 정책을 명시한 단선율 JGB를 처리한다. 관종·유파별 모범 연주나 실제 운지 가능성을 검증한 결과는 아니다.

## CAT에서 구분되는 것

[공식 라이브러리](https://www.catseoul.com/gugak-library/)는 대금(통합)·산조 대금·정악 대금을 각각 배포한다. 확보한 Live Sampler v1.3 ZIP의 이름만 비교하지 않고, 내부 WAV 67개와 simple·modal 프리셋을 직접 대조했다.

| 확인 대상 | 정악대금 | 산조대금 |
| --- | --- | --- |
| simple 키존 | 32개, MIDI58~89 | 32개, MIDI60~91 |
| modal 키존 | 18개, MIDI58~89 | 18개, MIDI60~91 |
| ZIP 내부 WAV | 67개 | 정악의 67개와 파일명·SHA-256 모두 동일 |
| 공통 MIDI60~89 | sample·root·detune·volume·trim·loop | simple와 modal 각각 정악과 동일 |
| 전역 설정 | attack0.1ms, release50ms, gain−12dB | 정악과 동일 |

샘플 맵과 파일 경로 메타데이터를 제외한 전역 leaf 값도 표시 이름 외에는 같았다. 두 프리셋이 존재하지만 공통 음역의 서로 다른 산조·정악 음색을 확보한 것은 아니다. 이 결론은 조사한 포맷·버전에 한정한다. CAT 키존을 특정 관종의 전통적 실연 음역으로 쓰지 않는다.

- 정악 ZIP SHA-256: `ca1f9388e55611e3766611409ca293bee4352a3960f367bb9c718aee5659f540`
- 산조 ZIP SHA-256: `b27d2247543a019a45f8aba593e3aece425d66a59cbb14d156eca294d36d65b4`
- 산조 simple XML SHA-256: `c226c32df09d470cc74ef5ae9b5b9d7c8f1c354a40982fd400f2180d97756d33`

## 산조에서 필요한 입력과 보류 조건

[국립국악원 단음 자료](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의 산조대금에는 기본음·요성·깊은요성·노니로·니라·스타카토·크레센도·다루치기가 별도 자료로 있다. 이 목록이 각 주법의 고정 cents·Hz·초 수치를 정하지는 않는다. 기존 [산조 원음 조사](sanjo-daegeum.md)의 발음 수·F0도 해당 녹음의 관측값이며 관종 전체의 음역·조율 정답이 아니다.

- 악기는 `sanjo-daegeum`으로 명시한다. `all`·일반 `daegeum`은 산조임을 확인한 근거 없이 선택하지 않는다. 정악/산조가 명시된 곡만 생성한다는 사용자 조건을 유지한다.
- 황종 MIDI와 정간/분·기준 음가를 입력한다. 빠르기는 `tempoBpmUserSet=true` 또는 명시적 `--bpm`이 필요하며 편집기 기본값을 확정값으로 사용하지 않는다. 악기 이름만 보고 전체 음높이를 이동하거나 조율을 선택하지 않는다. 원본에 저장된 수치라도 실제 판본 근거가 확인됐는지는 별도로 기록한다.
- 상대음 시김새에는 `joPreset` 또는 근거가 붙은 `playback.relative_scale`이 필요하다. 등장한 음 몇 개만으로 산조의 음계·조를 자동 판정하지 않는다.
- 농음·흘림은 `playback.expression_policy`로 mode·속도·폭·시점을 명시한다. 정악단소의 amplitude 농음이나 단소용 흘림 수치를 산조 기본값으로 복제하지 않는다. 표기가 없는 음에는 단소 실험의 추가 ±25cent 떨림을 적용하지 않는다.
- 프로필의 `technical_range`·`style_range`와 기본 `yoseong_mode`는 미확정 상태를 유지한다. 실제 렌더링에서는 CAT 키존·샘플 길이를 검사하며 범위 이탈이나 소진을 임의 옥타브 이동·반복으로 메우지 않는다.

## 실행과 확인

[검사용 JGB](../../../data/src/playback/examples/sanjo-authored.jgb.json)는 직접 작성한 4정간이다. 60정간/분, 황종 MIDI63, 상대음 음급, pitch 농음과 흘림 수치는 모두 작성자가 선택한 검사값이다. 실제 산조 악보·관종·유파의 정답을 나타내지 않는다. 검사용 명시 정책을 실제 곡의 검증된 주법으로 복사하지 않는다.

```powershell
python -m data.src.playback --score data/src/playback/examples/sanjo-authored.jgb.json --assets C:/local-gugak/assets.json --instrument sanjo-daegeum --out data/processed/playback/sanjo-authored
```

`assets.json`에 `sanjo-daegeum`의 로컬 v1.3 ZIP 경로가 필요하며 M4A는 FFmpeg·ffprobe 경로도 필요하다. 공통 [실행 안내](../../../data/src/playback/README.md)를 따른다. 입력 하나의 변환에 필요한 원본 JGB·정규화 score·source-map·MIDI·M4A·프리셋 및 샘플 해시를 출력한다. 배치 처리와 HTML 생성은 포함하지 않는다. WAV가 필요하면 별도 출력 폴더와 `--audio-format wav`를 사용한다.

기존 목록의 산조대금 「늴리리야」는 악기 대분류 근거가 있지만 `all` 성부·`joPreset=all`과 상대음 기호가 함께 있다. 상대음 음계·빠르기·표현을 확인하지 않은 채 모범 연주로 생성하지 않는다. 이번 검사용 60정간/분과 사용자에게 승인받은 단소 연습용 속도를 이 곡의 확정 빠르기로 간주하지 않는다.

CAT 원본 ZIP·WAV와 생성 음원은 Git에 넣지 않는다. 출처 표시는 생성 결과에 보존한다. 실제 샘플 생성 성공은 전문가 청감·원보 검수를 뜻하지 않는다.

## 검증 결과 · 2026-10-28

- `python -m unittest discover -s data/src/playback/tests -p 'test_*.py'`: **71개 통과**. 산조 전용 8개와 실제 FFmpeg 통합 검사를 포함한다. 기종 모호·상대음 음급 누락·명시 표현 정책 누락·비단소의 미확정 빠르기를 거부하고, 표시 없는 산조 음에는 단소 추가 떨림이 없음을 확인했다. 제거한 배치 기능의 검사는 현재 검사 수에 포함하지 않는다.
- Python3.12 환경에서 위 4정간 JGB를 단일 CLI로 실제 CAT simple 프리셋과 FFmpeg9.0.2에 연결했다. 최종 프로세스 실행1.086초, MIDI5음, 44,100Hz mono AAC-LC M4A **4.55초·200,655프레임·76,116 bytes**다. 입력 음가 합계는4초이며 공통 출력의 잔향 여백이 포함된다. 요청 비트레이트192kbps, 실제 약130kbps, 디코드 peak0.850016, 끝 패딩0프레임을 확인했다. 중간 WAV·HTML은 생성하지 않았다.
- 원본 JGB 바이트·입력/코드/프로필 해시, 명시 expression/relative-scale 정책의 source-map 보존, CAT 공통 샘플 경고를 확인했다. M4A SHA-256은 `096c510da0be4e300939fab85f951f43a7d6aea53f7e56b51871d053b6fc6ae1`이다.
- 실제 「늴리리야」 입력에 Notion의 산조대금 연결 근거만 추가하면 **빠르기 미확정으로 먼저 차단**된다. 렌더링 없이 진단용 BPM60만 넘겨도 1각12정간의 상대음 시김새에 명시 음계가 없어 차단된다. 이 진단값을 실제 곡의 속도로 승인하거나 음원으로 생성하지 않았다. 원본 SHA-256 `35be3d83d898fc4ba91f8851fcf6c010eecaa97890071c8dba0fb2fa5da986be`를 보존했다. 기호를 제거하거나 임의 음계를 붙여 성공으로 바꾸지 않았다.

최종 로컬 결과는 이 worktree의 새 폴더 `data/processed/playback/107-single-m4a/`에 있으며 `verification.json`에 해시·오디오 검사·실제 곡의 차단 이유를 남겼다. 이전 `107-authored-m4a/`의 음원·페이지는 보존했고 새 음원의 SHA-256이 이전과 같음을 확인했다. 검사용 입력은 통과했지만, 실제 산조 악보의 유파·관종별 주법 수치와 전곡 청감 검증은 미완료다.
