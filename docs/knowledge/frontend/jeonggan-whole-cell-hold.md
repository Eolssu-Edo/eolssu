# 정간 전체를 잇는 이음표의 빈칸 표시

- 작업: S15P31E102-1069
- 확인일: 2026-11-17

## 표기 관행과 데이터

정간보에서 앞 음을 이어 내는 자리는 정간 전체이면 빈칸, 한 정간을 쪼갠 안이면 `ㅡ`(데이터 `-`)로 적는다. 쪼갠 칸 안을 비우면 몇 개로 나눴는지 알 수 없어서다. 데이터·채점은 빈칸 `""`과 `-`를 모두 앞 이벤트의 지속으로 다룬다(`data/src/scores/score_data.py`의 `build_events`). 각 첫머리의 빈칸·`-`가 쉼이 되는 규칙은 이 작업과 무관하게 유지한다.

2026-11-17 운영 공개 API(`/api/songs`, `/api/songs/{id}/score`)로 공개 곡 108개를 집계했다.

| 위치 | 빈칸 | `-` |
| --- | --- | --- |
| 정간 전체 | 800 | 101 (27곡) |
| 쪼갠 칸 안 | 0 | 2,309 |

정간 전체 `-` 101칸은 모두 같은 각 안의 지속이거나 쉼의 연장이다. 각 첫머리 2칸은 쉼이다. 원본 PDF와는 대조하지 않았다.

## 화면 규칙

`JeongganboView`는 기본으로 정간 전체의 `-`를 빈칸으로 그린다(`wholeCellHoldAsBlank`). 다음은 `ㅡ`로 남긴다.

- 쪼갠 정간 안의 `-`
- 그 자리에 시김새·한배 표시가 붙은 `-`. 모범 연주 데이터(`/api/songs/{id}/model-audio/performance`)의 `segments[].sigimsae`가 칸 단위로 붙는다. 공개 곡에서는 뱃노래(장식음) 흘림표 3칸과 한오백년(악기 4) 농음표 2칸이다.
- 편집 화면(`EditScoreBoard`). 편집기는 빈칸과 `-`를 구분해 저장한다.

화면 낭독은 빈칸이어도 연결된 이벤트로 '이어요' 또는 쉼을 읽는다.

## Android VerifyError

`ScoreCellBox`의 소박 그리기 람다 안에 `if` 분기와 지역 변수를 더하자 호스트 테스트는 통과했지만 Android 실행에서 `java.lang.VerifyError: Verifier rejected class ...JeongganboViewKt ... type Conflict unexpected as arg to if-eqz`로 곡 화면을 여는 즉시 앱이 종료됐다. 이 람다는 인자가 40개에 가까운 큰 합성 함수다. 계산을 람다 밖 함수(`forDisplay`) 호출 하나로 옮기자 해결됐다.

이 람다를 고칠 때는 에뮬레이터·실기기에서 정간보 화면을 실제로 열어 본다. `testAndroidHostTest` 통과만으로는 이 오류를 잡지 못한다.

## 검증

- `./gradlew :shared:testAndroidHostTest` 정간보·곡 데이터 묶음 229개 통과(`WholeCellHoldTest` 5개 포함).
- Galaxy S10 에뮬레이터(API 37) debug 빌드: 뱃노래(장식음) 연습 화면에서 음원 전에는 통정간 `-`가 빈칸, 모범 연주 음원을 불러온 뒤에는 `ㅡ)`(흘림표)로 표시됨을 확인했다. 운영 로그인을 쓰지 않으려고 시작 화면만 커밋하지 않는 임시 변경으로 바꿨다.
- 웹(Wasm·JS)·iOS 빌드와 실기기는 확인하지 않았다.
