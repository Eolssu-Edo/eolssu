# 소금 JGB·MIDI·M4A 생성

작업: [S15P31E102-108](https://ssafy.atlassian.net/browse/S15P31E102-108), Story30 모범 연주 재생. 2026-10-28 기준. 공통 변환·AAC 출력은 166의 재생 기반을 사용하며 소금 프로필과 검증을 별도 관리한다.

## 해석 기준

[국립국악원 「소금」](https://www.gugak.go.kr/ency/topic/view/109)은 정간보 장식기호·요성·혀치기가 정악대금과 거의 같다고 설명한다. 같은 기호 문법을 사용할 수 있지만, 이것이 모든 곡에 같은 요성의 깊이·속도·시작 위치를 적용한다는 뜻은 아니다. [「요성」](https://www.gugak.go.kr/ency/topic/view/2105)은 장르·선법·연주자에 따른 차이를 설명하며 고정 Hz·cent 값을 정하지 않는다.

- 실음 입력은 그대로 재생한다. 대금 음역으로 기보했다고 명시된 IR에만 `pitch_mode=written`, `notation_octave_shift=1`을 사용한다. JGB 율명에 자동으로 한 옥타브를 더하지 않는다.
- 기존 교재 도판 검토를 따른 기술 음역은 MIDI70–96(B♭4–C7), 정악 관행은 MIDI70–92(B♭4–A♭6)다. 관행 밖에는 경고하고 기술 음역 밖은 실패한다. CAT simple 프리셋의 MIDI70–99 지원 범위와 실연 범위를 혼동하지 않는다.
- IR에서 요성을 명시했을 때의 `mixed`는 기존 재생용 근사 기본값이다. JGB 농음·흘림에는 `playback.expression_policy`의 선택 근거와 수치를 별도로 요구한다. 단소의 음량 요성·흘림 수치를 자동으로 복제하지 않는다.
- 단소에 적용했던 자동 ±25cent 떨림은 소금에 추가하지 않는다. 원본 CAT 샘플에 녹음된 변화는 남는다.
- 장식의 상대음은 명시된 조 또는 근거를 적은 음계를 따른다. 관찰한 일부 음만으로 자동 추정하지 않는다. 미지원 기호·음역 이탈·샘플 소진은 생략하거나 무음으로 채우지 않는다.
- 숨표의 가변 길이는 공통 재생 알고리즘이다. 소금의 공인 호흡 시간이 아니며 음 길이 안에서 숨과 감쇠를 배분한 근사다.

음역의 기존 근거는 국립국악원 『창작을 위한 국악기 이해와 활용2』 소금 편 p61 운지표·p78 정악 상한이다. [공식 PDF](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?boardid=10929&boardseq=41191&dirname=%2Fboard%2F138&filename=kor_sogeum.pdf&sistype=D&type=F)의 웹 뷰어는 이번 조사에서 열리지 않아 음역 도판을 새로 검증했다고 기록하지 않는다. 기존 [소금 원음·라벨 조사](sogeum.md)의 추정 F0를 평균율 조율의 정답으로 바꾸지 않는다.

## 실행

공통 의존성·FFmpeg 준비는 [playback 실행 안내](../../../data/src/playback/README.md)를 따른다. `assets.json`에는 `sogeum` 키로 로컬 CAT 소금 ZIP 경로를 지정한다. 기본 출력은 AAC-LC M4A이며 `--audio-format wav`로 PCM16 WAV를 선택할 수 있다.

```powershell
python -m data.src.playback --score data/src/playback/examples/sogeum-jgb.json --instrument sogeum --assets assets.json --out data/processed/playback/sogeum-example
python -m data.src.playback --score data/processed/playback/166-listen/notion-audio-sogeum-m4a/prepared/001.jgb.json --instrument sogeum --assets assets.json --out data/processed/playback/sogeum-single
python -m unittest discover -s data/src/playback/tests -p 'test_*.py'
```

고정 예제는 청황·청태·쉼·청중 4정간을 직접 작성한 검사 자료다. 예제의 60정간/분은 테스트 입력이며 실제 소금 곡의 기본 빠르기나 다른 악기용 연습값 승인을 뜻하지 않는다. `tempoBpmUserSet: true`로 빠르기 선택을 명시하며, 이 값이 없는 비단소 JGB는 명시적 `--bpm`이 필요하다. 원본의 악기 선언과 충돌하면 실패한다. JGB에 악기가 없으면 입력을 준비할 때 판본을 확인해 `playback.instrument: "sogeum"`과 `playback.instrument_basis`를 기록한다.

두 번째 명령은 로컬에 이미 준비한 「개구리소리」 JSON을 사용하는 예다. 해당 파일·CAT ZIP은 저장소에 포함되지 않으므로 실제 준비 경로를 지정한다. 현재 기능은 입력 하나의 음원·MIDI·변환/추적 JSON 생성까지다. 목록 처리, 빠르기 조사·보충, 원보 교정과 HTML 생성은 포함하지 않는다.

## 기존 실곡 검증 기록

목록 처리 기능을 제외하기 전에 Notion DATA 소금 목록 22파일 중 빠르기와 변환 근거가 있던 13파일을 검증했다. 원본 JGB·연결 PDF의 SHA-256을 당시 기록과 대조했고, 빠르기 미확정 8파일과 상대음계가 미확정인 「오나라」 1파일은 보류했다. 단소에서 승인된 연습용 60정간/분을 소금 전체에 자동 확대하지 않았다. 이 결과는 과거 검증 기록이며 현재 CLI의 목록 처리 기능을 뜻하지 않는다.

「개구리 소리」는 표준악보집 PDF188(인쇄374–375쪽)의 소금 편이다. 이번에 해당 PDF를 직접 렌더링하고 본문을 추출해 소금 명시·굿거리장단·사용 율명 임·남·청황·청태·청고(㴌)를 확인했다. 사용자 빠르기 표의 굿거리 점4분음표66회/분과 한 번에3정간인 표기를 연결해 198정간/분으로 준비한다. 이는 사용자가 선택한 빠르기이며 해당 PDF가 숫자66을 제시한 것은 아니다. 다른12파일의 판본 연결과 빠르기는 기존 검토 기록을 재사용하며 새 전문가 전수 검수로 보고하지 않는다.

기존 로컬 입력은 `data/processed/playback/166-listen/notion-export/`, 이전 목록은 같은 상위 폴더의 `sogeum-m4a-catalog.json`, 보류 사유는 `sogeum-m4a-deferred.json`에 보존했다. 목록 파일은 현재 단일 CLI의 필수 입력이 아니다. 원본 PDF·CAT ZIP·생성 음원은 Git에 포함하지 않는다. 생성 성공은 실제 운지·편곡·연주 해석의 전문가 승인을 의미하지 않는다.

## 검증 결과

단일 변환 범위로 정리한 뒤 공통63개·소금4개, 총 **67개 테스트가 통과**했다. 소금 전용 검사는 기보/실음의 옥타브 중복 방지, 명시된 IR 요성의 mixed 처리, 전통 관행 밖 경고와 기술 음역 밖 실패, JGB의 실음·쉼 시각·원본 ID 보존 및 자동 피치 떨림 미적용을 확인한다. 실제 FFmpeg 인코딩 검사, 악기 선언 충돌 거부, JGB 빠르기 명시 검사도 포함한다.

Python3.12·FFmpeg9.0.2에서 CAT Live Sampler v1.3 소금 simple 프리셋으로 「개구리소리」 준비 JSON을 새 폴더에 단일 CLI로 다시 생성했다. **40음·18.731837초·358,259bytes**, 명령 실행1.209초였다. M4A·MIDI·악기 추적 JSON·score·manifest·원본 JGB 사본·source-map의7파일만 생성되었고 HTML·중간 WAV는0개다. 출력은44,100Hz·mono·AAC-LC(요청192kbps)다.

실음은 MIDI70–79이며 M4A SHA-256 `107648fe8bef580593bf2bbe5996f52cebc7ba4887a51d57fa8f210634899f86`은 기존 생성본과 같다. 원본 준비 JSON 사본, 현재 코드·프로필 해시, MIDI40음·추가 피치 변조0을 확인했다. 결과와 검증 기록은 `data/processed/playback/166-listen/sogeum-single-pipeline-final/`에 보존했다.

이전 목록 검증의13파일·810음·371.675초·6,936,570bytes(18.406초)와 고정 예제3음·4.55초는 각각 `notion-audio-sogeum-m4a/`·`sogeum-fixed-example-m4a/`에 그대로 보존했다. 당시13파일의 원본·선율·황종·원본 ID·음역·해시를 확인했으며, 목록 기능 삭제 후13파일 전체를 다시 생성한 것은 아니다. 공통 인코더는 컨테이너 길이와 원본 PCM 길이·패딩, AAC 디코딩 peak<0.99도 검사한다. 브라우저 실제 재생과 전문가 청감·원보 전수 대조는 이번 검증에 포함하지 않는다.
