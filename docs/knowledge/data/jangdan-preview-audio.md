# 등록 장단 10종의 미리듣기 음원

S15P31E102-160 · 2026-11-04

[국립국악원 국악기 디지털 음원 단음 다운로드](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)에서 장구(19847)의 원본 WAV를 받았다. 장구 목록의 공공누리 유형은 1이다. 가공한 음원의 출처는 국립국악원이며, 원본과 파생 음원의 SHA-256 및 타격 배치는 [생성 목록](jangdan-preview-audio-manifest.json)에 있다.

이 미리듣기는 [등록 패턴](../../../data/reference/jangdans.json)을 서비스 기본 BPM에서 한 주기 재생한 것이다. 각 타격의 앞 무음을 잘라 모노로 섞고 MP3 44.1 kHz / 64 kbps로 인코딩했다. 덕기는 3:1, 덕덕은 1:1로 배치한다. 기덕·기닥의 앞꾸밈과 더러러러의 굴림은 녹음 타격을 재배치한 교육용 편집이다. 기닥·다는 약한 채편 샘플을 사용하고 따는 단일 채편 타격으로 표현했다. 타령과 계면가락도드리는 현재 등록 패턴과 BPM이 같아 같은 소리가 나오며 ID별로 각각 제공한다.

## 다시 생성하기

저장소 루트에서 실행한다. `--samples`에는 생성 목록과 SHA-256이 같은 WAV 6개를 둔다. `--output`에는 아직 생성되지 않은 경로를 지정한다.

```powershell
python -m data.src.audio.jangdan_audio --reference data/reference/jangdans.json --samples data/raw/jangdan-160 --output data/processed/jangdan-160
python -m unittest data.tests.test_jangdan_audio
```

FFmpeg가 PATH에 없으면 `--ffmpeg`에 실행 파일을 지정한다. 이 결과는 FFmpeg 7.1의 libmp3lame으로 생성했다.

같은 원본으로 두 번 생성해 결과 SHA-256이 모두 일치했다. MP3 10개를 PCM으로 디코딩해 길이가 계획의 프레임 수와 일치하고, 샘플 피크가 16비트 범위 안에 있음을 확인했다. 장구 연주자 청음 검수는 남아 있다.
