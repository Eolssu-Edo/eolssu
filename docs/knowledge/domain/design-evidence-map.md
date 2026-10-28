# 논문·공식 자료와 설계 결정의 근거 표

- 작업: [S15P31E102-205](https://ssafy.atlassian.net/browse/S15P31E102-205). 조사일 2026-10-28.
- 목적: 얼쑤의 채점·악보 데이터·학습 흐름이 어떤 자료를 근거로 정해졌는지 한곳에서 보여준다. 발표·심사에서 "왜 이렇게 했는가"에 답할 때 쓴다.
- 이 문서는 근거를 모은 것이다. 표의 설계 내용 가운데 `제안`은 팀이 확정한 사항이 아니다.

## 핵심 결론

- 채점의 뼈대(음높이 추정 → DTW 정렬 → 음표별 판정)는 MIR 분야의 표준 방법(YIN·pYIN·CREPE, DTW, 연주 분석 분류)을 따른다.
- 국악다운 판단은 국악 연구에서 나왔다. 시김새는 음 높이가 크게 움직이고(피리 추성 약 160센트), 연주자마다 다르다(대금 상령산·가야금 농현). 그래서 시김새 음표는 음정 판정에서 빼고, 조율 기준은 음의 가운데 구간에서 잰다.
- 공개 정간보 OMR 연구를 그대로 쓰지 않고 우리 악보에서 정확도를 다시 쟀다. 떨어진 부분은 직접 보완했다. 외부 연구에 팀의 실측을 더한 사례로 보여주기 가장 좋다.
- 학습 순서는 국립국악원 지도서와 『국악교육연구』 논문을 따른다. 시김새는 마지막 단계다.

## 확인 수준

| 표시 | 뜻 |
| --- | --- |
| **원문** | 이번 작업에서 논문 페이지·저장소·공식 문서를 열어 서지와 해당 내용을 확인함 |
| **초록** | 이번 작업에서 서지와 초록만 확인함. 본문 수치는 보지 않음 |
| **팀 문서** | 기존 팀 조사 문서에서 원문을 확인한 자료. 이번에 다시 열지 않음 |

## 근거 → 설계 결정

### 연주 채점 (AI)

| 자료 | 확인 | 자료가 말하는 것 | 얼쑤의 설계 | 저장소 근거 |
| --- | --- | --- | --- | --- |
| de Cheveigné & Kawahara, "YIN, a fundamental frequency estimator for speech and music", *JASA* 111(4), 1917–1930, 2002. [doi:10.1121/1.1458024](https://doi.org/10.1121/1.1458024) | 초록 | 자기상관에 여러 보정을 더해 옥타브 오류를 줄인 기본주파수 추정. 조정할 변수가 적음 | 산조대금 기본음 분석의 자체 구현(CMND) 기준. pYIN의 바탕 | [sanjo-daegeum.md](../data/sanjo-daegeum.md) |
| Mauch & Dixon, "pYIN: A Fundamental Frequency Estimator Using Probabilistic Threshold Distributions", ICASSP 2014, 659–663. [doi:10.1109/ICASSP.2014.6853678](https://doi.org/10.1109/ICASSP.2014.6853678) | 초록 | YIN의 임계값을 확률 분포로 바꿔 음높이 후보를 여러 개 내고, HMM·Viterbi로 이어진 음높이 궤적을 고름 | 음높이 추정 후보 1(librosa `pyin`, 10ms 간격) | [pitch-estimator-comparison.md](../ai/pitch-estimator-comparison.md) |
| Kim, Salamon, Li & Bello, "CREPE: A Convolutional Representation for Pitch Estimation", ICASSP 2018. [arXiv:1802.06182](https://arxiv.org/abs/1802.06182) | 원문(초록 페이지) | 원시 파형을 받는 CNN. pYIN과 같거나 더 나은 정확도를 보고하고 잡음 강건성을 평가함 | 음높이 추정 후보 2. 합성 입력 비교에서 0dB 잡음에도 pYIN과 달리 소리를 찾음. full은 CPU에서 너무 느려 서버 후보에서 제외, tiny 유지 | [pitch-estimator-comparison.md](../ai/pitch-estimator-comparison.md), [analysis-latency.md](../ai/analysis-latency.md) |
| [torchcrepe](https://github.com/maxrmorrison/torchcrepe) (MIT) | 원문 | CREPE의 PyTorch 구현. `full`·`tiny` 두 크기. 기본 디코딩은 원 논문의 가중 평균이 아니라 Viterbi | 채점 비교에 쓴 실제 구현. 원 논문 결과를 그대로 옮겨 말하지 않음 | [ai/README.md](../../../ai/README.md) |
| Salamon, Gómez, Ellis & Richard, "Melody Extraction from Polyphonic Music Signals: Approaches, Applications and Challenges", *IEEE Signal Processing Magazine* 31(2), 118–134, 2014. 평가 도구 [mir_eval](https://github.com/mir-evaluation/mir_eval) `melody` 모듈 | 원문(mir_eval 소스) | MIR 선율 추출 평가의 표준 지표인 raw pitch accuracy는 정답과 반음 절반(50센트) 이내면 맞은 것으로 봄(`cent_tolerance=50`) | 음정 허용 범위 ±50센트 **제안**. 다만 이 지표는 프레임 단위 추출 평가 기준이고 연주 채점 기준이 아니므로, 실제 녹음으로 다시 정함 | [ai/README.md](../../../ai/README.md) 채점 방식 |
| Sakoe & Chiba, "Dynamic programming algorithm optimization for spoken word recognition", *IEEE Trans. ASSP* 26(1), 43–49, 1978. [doi:10.1109/TASSP.1978.1163055](https://doi.org/10.1109/TASSP.1978.1163055) | 초록 | 동적 계획법으로 두 시간축을 맞추는 DTW. 기울기 제한을 둔 대칭형 정의 | 정답 음표와 녹음을 맞추는 DTW. 녹음이 길 때 쓸 대각선 폭 제한(band)이 이 논문 계열의 정렬 창 제한이다 | [analysis-latency.md](../ai/analysis-latency.md) |
| Müller, *Fundamentals of Music Processing*, Springer, 2015, 3장 Music Synchronization. [FMP 3장](https://www.audiolabs-erlangen.de/resources/MIR/FMP/C3/C3.html) | 원문(목차·교재 페이지) | 같은 곡의 서로 다른 표현(악보·녹음)을 DTW로 시간 정렬하는 표준 방법 | 정간보 → 음표 시퀀스와 녹음 음높이 궤적의 정렬 구조 | [README 연주 평가 흐름](../../../README.md) |
| Lerch, Arthur, Pati & Gururani, "An Interdisciplinary Review of Music Performance Analysis", *TISMIR* 3(1), 221–245, 2020. [doi:10.5334/tismir.53](https://doi.org/10.5334/tismir.53) | 초록 | 연주 분석을 ① 연주 변수 측정 ② 의도·지각과의 관계 ③ 연주 평가로 나눔 | 채점을 "음정·박자·길이 측정 → 음표별 판정"으로 나누고 틀린 위치를 설명하는 구조 | [ai/README.md](../../../ai/README.md) |
| Pati, Gururani & Lerch, "Assessment of Student Music Performances Using Deep Neural Networks", *Applied Sciences* 8(4), 507, 2018. [doi:10.3390/app8040507](https://doi.org/10.3390/app8040507) | 초록 | 학생 연주 녹음을 음높이 궤적·멜 스펙트로그램으로 받아 DNN이 평가 점수를 예측. 수작업 특징 기준선보다 좋음 | 지금은 국악기 평가 점수가 매겨진 데이터가 없어 음표별 규칙 판정으로 시작. 평가 데이터가 쌓이면 학습형 평가로 확장할 근거 | 미착수 |

### 시김새와 조율 (도메인 → AI)

| 자료 | 확인 | 자료가 말하는 것 | 얼쑤의 설계 | 저장소 근거 |
| --- | --- | --- | --- | --- |
| 「피리정악 시김새의 음고변화 연구 – 평조 두거를 중심으로」, 『국악원논문집』 44, 85–116, 국립국악원, 2021. 저자명 미확인 | 초록 | 1박 이상 지속음의 높이로 기준 음고를 정하고 시김새 부분은 뺐음. 추성은 평균 약 160센트 올라감 | 조율 보정을 정렬된 음의 **가운데 구간** 중앙값으로 잼. 시김새 음표는 음정 판정에서 뺌. 추성 폭(약 160센트)은 ±50센트 허용 범위를 크게 넘어, 빼지 않으면 바르게 분 시김새가 오답이 됨 | [ai/README.md](../../../ai/README.md) 채점 방식 |
| 「관악영산회상 상령산 대금 시김새 고찰」, 『국악원논문집』 51, 1–40, 국립국악원, 2025. [doi:10.29028/JNGC.2025.51.9](https://doi.org/10.29028/JNGC.2025.51.9). 저자명 미확인 | 초록 | 『대금보』·『대금정악』·『대금정악보』와 연주자 6명을 비교함. 기본 선율은 같고 장식 시김새 사용은 연주자마다 다름 | 시김새를 한 가지 정답 모양으로 채점하지 않음. 모범 연주 재생의 표현값도 "조절 가능한 초기값"으로 둠 | [score-instrument-playback-pipeline.md](../data/score-instrument-playback-pipeline.md) |
| 유현정, 「가야금 정악 농현의 정형성과 비정형성 – 남창가곡 계면 초수대엽을 중심으로」, 『동양음악』 57, 159–183, 2025. [earticle](https://www.earticle.net/Article/A468552) | 초록 | 농현의 진동 폭·빈도·길이가 연주자마다 다르고, 서양 비브라토와 다른 불규칙한 파형이 있음 | 요성을 고정된 사인파 떨림으로 판정하지 않음. 합성 실험의 요성 입력은 판정 경로 확인용일 뿐 실제 요성의 모형이 아님 | [pitch-estimator-comparison.md](../ai/pitch-estimator-comparison.md) |

### 악보 데이터 (Data)

| 자료 | 확인 | 자료가 말하는 것 | 얼쑤의 설계 | 저장소 근거 |
| --- | --- | --- | --- | --- |
| Kim, Han, Jeong & Valero-Mas, "Optical Music Recognition for Jeongganbo Notation of Korean Court Music", ISMIR 2024 Late-Breaking Demo. [프로그램 페이지](https://ismir2024program.ismir.net/lbd_425.html) | 원문(초록 페이지) | 정간보의 첫 OMR 연구. 합성 데이터로 학습해 정악 악보에서 90% 가까운 성공률을 보고함 | 정간보 PDF → JSON 초안을 공개 OMR로 만듦 | [jeongganbo-omr.md](../data/jeongganbo-omr.md) |
| Kim, Han, Jeong & Valero-Mas, "On the Automatic Recognition of Jeongganbo Music Notation: Dataset and Approach", *ACM JOCCH* 18(3), 1–21, 2025. [doi:10.1145/3715159](https://doi.org/10.1145/3715159) | 원문(Crossref·저장소) | 실제 정간보 데이터셋, 합성 데이터 생성·증강, 정간보 전용 인식 모델 | 실제로 쓴 [MALerLab/jeongganbo-omr](https://github.com/MALerLab/jeongganbo-omr)(MIT)가 구현한 논문. 교실 악보에서는 율명 정확도가 42~82%로 떨어져 사람 검토를 거치고, 음역 제한과 청성·중청 글자 판별로 63%에서 80%까지 올림. 재학습은 하지 않음 | [jeongganbo-omr-accuracy.md](../data/jeongganbo-omr-accuracy.md) |
| 국립국악원 『창작을 위한 국악기 이해와 활용2』 단소 편 | 팀 문서 | 단소 음역도(p26), 꾸밈(p33~34), 숨·요성·혀치기(p35) | 단소 재생 프로필의 실음 범위(MIDI 66~92)와 표현 규칙 | [score-instrument-playback-pipeline.md](../data/score-instrument-playback-pipeline.md) |

### 학습 흐름 (기획·도메인)

| 자료 | 확인 | 자료가 말하는 것 | 얼쑤의 설계 | 저장소 근거 |
| --- | --- | --- | --- | --- |
| 정일영, 「입김의 성질에 따른 단소의 단계적 지도방법」, 『국악교육연구』 8(2), 49–77, 2014. [KCI](https://www.kci.go.kr/kciportal/ci/sereArticleSearch/ciSereArtiView.kci?sereArticleSearchBean.artiId=ART001905872) | 초록 | 저음·고음 구분 대신 입김에 따라 중·저·고음으로 묶고, 곡에 가장 많이 나오는 중음부터 5단계로 가르침 | '기초부터 배우기'에서 쉬운 음부터 익히고 곡에 일찍 들어가는 단계 **제안** | [wind-beginner-curriculum.md](wind-beginner-curriculum.md) |
| 한영숙, 「2007년 개정 교육과정에 따른 초등학교 음악교과서의 단소 지도내용 고찰」, 『국악교육연구』 6(2), 129–156, 2012. [earticle](https://www.earticle.net/Article/A312156) | 초록 | 교과서 단소 지도가 운지·입김 세기에 치우치고 시김새·정간보는 불완전하거나 일관되지 않게 다룸 | 쉬운 정간보(율명 한글 전환·기호 설명)가 입문의 빈칸을 메운다는 근거. 교과서 곡 데이터 구축 | [wind-beginner-curriculum.md](wind-beginner-curriculum.md) |
| 국립국악원 e국악아카데미 단소 지도서·강좌(대금·소금·단소·피리) | 팀 문서 | 악기 알기 → 자세·호흡 → 첫 소리 → 기본음 → 곡 → 시김새 순서. 시김새는 마지막이고 무리하게 지도하지 않아도 된다고 봄 | 초급 곡에서 시김새를 음정 판정에서 빼는 것과 맞음. 단계별 학습 순서 **제안** | [wind-beginner-curriculum.md](wind-beginner-curriculum.md) |

## 팀 자체 실험 (외부 자료와 구분)

외부 논문의 결과가 아니라 팀이 직접 잰 값이다. 재현 방법은 각 문서에 있다.

| 실험 | 결과 | 문서 |
| --- | --- | --- |
| 음높이 추정 3종 비교(합성 입력 30종) | 판정 일치 pYIN 299/300, torchcrepe full·tiny 310/310. 실제 대금 녹음 비교 전 | [pitch-estimator-comparison.md](../ai/pitch-estimator-comparison.md) |
| 녹음 길이별 처리 시간·메모리 | DTW 메모리 때문에 600초 녹음은 현재 방식으로 분석 불가. 120초까지는 60초 제한 안 | [analysis-latency.md](../ai/analysis-latency.md) |
| 정간보 OMR 정확도(단소 12곡) | 교실 악보 율명 42~82%. 율명 인식 개선으로 63% → 80%, 옥타브 오류 44 → 6개 | [jeongganbo-omr-accuracy.md](../data/jeongganbo-omr-accuracy.md) |

## 발표에서 쓰는 법

- 논문 목록이 아니라 "자료 → 결정" 한 줄로 말한다. 예: "피리 시김새 연구에서 추성은 약 160센트 올라간다 → 허용 범위 ±50센트로 채점하면 바르게 분 시김새가 오답이 된다 → 시김새 음표는 음정 판정에서 뺐다."
- 기존 연구의 한계와 팀의 보완을 짝지어 보인다. 예: 공개 정간보 OMR은 정악보 기준 약 90%였지만, 교실 악보에서는 우리가 직접 잰 값이 42~82%였다. 그래서 율명 인식을 고쳐 80%까지 올렸다.
- `초록`으로 표시한 자료의 본문 수치는 인용하지 않는다.

## 미확인 사항

- 『국악원논문집』 두 편(피리 시김새 음고, 상령산 대금 시김새)의 저자명을 확인하지 못했다. 열람한 서지 페이지에 저자가 표시되지 않았다.
- pYIN·YIN·Sakoe & Chiba·Lerch·Pati 논문은 본문을 읽지 않았다. 본문 수치가 필요하면 원문을 확인한다.
- Salamon 외 2014는 mir_eval 소스의 인용과 기본값으로 확인했고 잡지 원문은 열지 않았다.
- CREPE의 잡음 강건성 결과는 초록에 수치가 없다. 팀 실험의 0dB 결과와 같은 조건이라고 말하지 않는다.
- 대금 연주의 음높이를 음향적으로 재서 채점 기준을 제시한 국내 논문은 이번 검색에서 찾지 못했다.
