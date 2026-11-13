---
title: "VectorDrawable 셰이딩과 래스터 외곽선 정규화로 홈 일러스트 통일하기"
date: 2026-11-12
slug: "card-illustration-vector-raster-pipeline"
author: "오은서"
categories: ["frontend"]
tags: ["Compose Multiplatform", "VectorDrawable", "fontTools", "Pillow", "이미지 처리"]
description: "Compose Multiplatform 홈 카드 일러스트를 VectorDrawable로 다시 그리고, 출처가 다른 래스터 그림의 외곽선 두께와 색온도를 거리 기반 마스크와 채널별 보정으로 맞춘 과정을 정리했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-card-illustration-vector-raster-pipeline.webp"
draft: false
---

얼쑤 홈 화면에는 6개 기능 카드가 2열 그리드로 놓이고, 카드마다 일러스트가 하나씩 들어갑니다. 이 일러스트를 두 단계로 교체했습니다.

1. **식별성:** 카드끼리 구별되도록 VectorDrawable로 다시 그렸습니다.
2. **일관성:** 출처가 다른 래스터 그림을 섞어 쓰면서, 외곽선 두께와 색감을 하나의 기준으로 정규화했습니다.

Compose Multiplatform 공유 리소스(`composeResources/drawable`)로 Android·iOS·Web에 같은 파일을 배포하는 환경입니다.

![변경 전 홈 카드와 최종 홈 카드 비교](https://team-edo.github.io/blog-images/2026-11-card-illustration-vector-raster-pipeline-before-after.webp)

왼쪽은 1단계 이전, 오른쪽은 2단계 이후입니다. 1단계의 벡터 결과물은 2단계에서 대체되어 비교에서 뺐습니다.

## 1단계: 실루엣과 주색으로 식별성 확보

기존 6장 중 4장이 모눈 상자 형태여서, 그리드에서 실루엣이 같았습니다. 작은 아이콘은 내부 디테일보다 외곽 실루엣과 지배색으로 먼저 구별되므로, 두 속성을 카드마다 겹치지 않게 다시 배정했습니다.

| 카드 | 실루엣 | 지배색 |
| --- | --- | --- |
| 기초 배우기 | 계단 + 깃발 | 연두 |
| 연습하기 | 캐릭터 | 흰색·먹색 |
| 운지법 보기 | 사선 원통(단소) | 대나무색 |
| 연습 기록 보기 | 프레임 없는 막대·꺾은선 | 보라 |
| 정간보 읽기 | 원형 렌즈 + 손잡이 | 파랑 |
| 악보 추가·편집하기 | 종이 + 붓 | 갈색 |

초안에서 그래프는 주황, 편집 도구는 연필이었는데, 둘 다 단소의 대나무색과 색상(hue)이 가까워 한 묶음으로 읽혔습니다. 다른 카드가 쓰지 않는 보라와 갈색 붓으로 바꿔 색상 충돌을 없앴습니다.

### clip-path 밴드로 그라데이션 없이 곡면 표현

VectorDrawable은 `path`·`group`·`clip-path`만으로 구성하기로 했습니다. 원통의 곡면은 그라데이션 대신 **본체 형상으로 클리핑한 밝은 띠와 어두운 띠**를 겹쳐 표현했습니다.

```xml
<!-- 단소 본체: 기본색 -->
<path android:fillColor="#D9B26A"
      android:pathData="M17,38 H133 Q142,38 142,47 V47 Q142,56 133,56 H17 Q8,56 8,47 V47 Q8,38 17,38Z" />
<group>
    <!-- 본체와 같은 형상으로 클리핑 -->
    <clip-path android:pathData="M17,38 H133 Q142,38 142,47 V47 Q142,56 133,56 H17 Q8,56 8,47 V47 Q8,38 17,38Z" />
    <path android:fillColor="#ECCF92" android:pathData="M8,38 H142 V44 H8Z" /> <!-- 상단 하이라이트 띠 -->
    <path android:fillColor="#BF9550" android:pathData="M8,51 H142 V56 H8Z" /> <!-- 하단 음영 띠 -->
</group>
```

띠 자체는 단순한 직사각형이고, 둥근 끝단은 클리핑이 만들어 줍니다.

- `clip-path`는 같은 `group` 안에서 **뒤에 선언된 path에만** 적용됩니다. 그래서 띠만 별도 group으로 감쌌습니다. 클립 없이 그리면 둥근 끝단 밖으로 사각 모서리가 노출됩니다.
- 그리는 순서가 곧 z-order입니다. 돋보기는 손잡이를 렌즈 내부에서 시작해 먼저 그리고 렌즈와 테로 덮어, 선 굵기 차이로 생기는 이음매를 가렸습니다.

### 텍스트는 글리프 외곽선을 pathData로 변환

VectorDrawable에는 텍스트 요소가 없고, 런타임 폰트에 의존하면 플랫폼마다 글자 모양이 달라집니다. 정간보 카드의 율명 汰는 앱에 번들된 Noto Serif KR SemiBold(OFL)에서 fontTools로 글리프 외곽선을 추출해 `pathData`로 넣었습니다.

```python
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

font = TTFont("noto_serif_kr_semibold.otf")
gs = font.getGlyphSet()
name = font.getBestCmap()[ord("汰")]

svg = SVGPathPen(gs)
gs[name].draw(TransformPen(svg, (s, 0, 0, -s, x, baseline)))
path_data = svg.getCommands()
```

- 폰트 좌표계는 y축이 위쪽이 양수라서 변환 행렬의 y 스케일을 `-s`로 뒤집어 화면 좌표계와 맞췄습니다.
- 서브셋 폰트는 cmap에 해당 코드포인트가 있는지 먼저 확인해야 합니다.
- 시스템 폰트 대신 앱에 이미 포함된 OFL 폰트를 써서, 라이선스와 앱 내 글자 모양을 함께 맞췄습니다.

## 2단계: 래스터 에셋의 외곽선과 색온도 정규화

2단계에서는 기준으로 정한 단소 일러스트의 스타일에 6장을 맞췄습니다.

- **기준 스타일:** 짙은 갈색 외곽선, 하이라이트, 하단으로 갈수록 진해지는 명도, 옅은 바닥 그림자
- **외곽선 두께:** 연습하기 캐릭터의 다리 선(렌더링 기준 약 0.7dp)

질감 있는 그림을 함께 써야 해서 벡터 5장을 투명 배경 WebP(높이 480px)로 전환했습니다. 6장의 출처는 다음과 같습니다.
- **SVG로 직접 작성:** 2장 (징검돌, 막대그래프)
- **AI 생성 이미지를 후처리:** 4장 (단소, 정간보·돋보기, 두루마리·붓, 캐릭터)

AI 생성 이미지는 장마다 외곽선 두께와 색온도가 달라서, 아래 세 단계의 처리를 거쳤습니다.

### 1) SVG 래스터화: 브라우저 canvas를 렌더러로 사용

작업 환경의 Python에는 cairosvg·skia 같은 SVG 렌더러가 없었습니다. 대신 브라우저에서 SVG를 canvas에 그리고, PNG 바이트를 루프백 서버로 보내 저장했습니다.

```js
canvas.toBlob(b =>
  fetch(SAVE_URL + '?name=learn', { method: 'POST', body: b }));
```

- 이후 알파 기준 bounding box 크롭, 리사이즈, WebP 인코딩은 Pillow로 분리했습니다.
- 렌더링과 후처리를 나눠, 한쪽만 고쳐 다시 실행할 수 있게 했습니다.
- `SAVE_URL`은 같은 PC의 루프백 주소에만 바인딩한 임시 저장 서버이고, 작업 후 종료했습니다.
- 브라우저는 로컬에 설치된 폰트로 렌더링합니다. 다른 환경에서 다시 렌더링하면 글자 모양이 달라질 수 있습니다.

### 2) 거리 기반 마스크로 바깥 외곽선만 얇게 깎기

단순히 먹선 픽셀을 지우는 첫 시도에서는 외곽선 전체와 글자 내부까지 비어 버렸습니다. 그래서 먹선 픽셀마다 두 가지 거리를 구했습니다.

- `dt`: 투명 영역(그림 바깥)까지의 거리
- `dl`: 먹선이 아닌 내부 면까지의 거리

거리는 Pillow의 `MaxFilter(3)`을 반복 적용해 체스판(Chebyshev) 거리로 근사했습니다. 아래 조건을 모두 만족하는 픽셀만 제거했습니다.

```python
remove = ink & (dl > keep) & (dt < dl) & (dt + dl <= 34)
```

| 조건 | 의미 |
| --- | --- |
| `dl > keep` | 내부 면에 붙은 `keep`px는 남김 (원본 기준 6px, 해상도가 다른 단소는 5px) |
| `dt < dl` | 바깥쪽에 더 가까운 픽셀만 제거 |
| `dt + dl <= 34` | 국소 선 두께 상한. 붓 쇠테·먹 자국·글자 같은 굵은 먹 면 보호 |

가는 붓 고리처럼 규칙으로 구분하기 어려운 영역은 보호 박스로 제외했습니다. 경계 안티에일리어싱도 `dt`가 작은 바깥 영역에만 적용했습니다. 전체에 적용하면 내부 디테일까지 흐려지기 때문입니다.

### 3) 채널별 비율로 화이트 포인트 맞추기

연습하기 캐릭터는 털이 노란 기가 있는 크림색이라, 프로필 화면의 같은 캐릭터와 색온도가 달랐습니다. 밝기를 일괄로 올리거나 채도를 낮추는 첫 시도에서는 회색으로 탁해졌습니다.

노란 기는 R보다 G·B가 흰색에서 더 멀리 떨어진 상태입니다. 그래서 **흰색까지의 거리 `255 - v`를 채널별로 다른 비율로 줄였습니다.**

| 밝은 면 중앙값 (R, G, B) | 값 |
| --- | --- |
| 기준: 프로필 캐릭터 | (252, 248, 243) |
| 대상: 연습하기 캐릭터 | (251, 238, 226) |
| 채널별 비율 `(255 - ref) / (255 - src)` | (0.75, 0.41, 0.41) |

```python
out = 255 - (255 - rgb) * ratio   # 고명도·저채도 마스크 영역에만 적용
```

중앙값은 고명도·저채도 픽셀만으로 구했습니다. 먹선이나 그림자가 섞이면 비율이 틀어지기 때문입니다. 보정도 같은 마스크 안에서만 적용해 먹선과 유채색 영역은 보존했습니다.

## 적용과 검증

- 모든 에셋을 알파 기준으로 크롭하고 여백 4%만 남겼습니다. 카드의 이미지 슬롯은 96dp에서 112dp로 늘렸습니다.
- `:shared:testAndroidHostTest :shared:compileKotlinJs :androidApp:installDebug`를 통과했습니다.
- 에뮬레이터(Pixel 7, API 35) 홈 화면에서 6장의 외곽선 두께와 색감이 맞는지 확인했습니다.
- 실기기·태블릿·Web에서의 선 두께와 iOS 렌더링은 아직 확인하지 않았습니다.

## 정리

| 문제 | 접근 |
| --- | --- |
| 카드 간 식별성 | 실루엣과 지배색을 카드마다 고유하게 배정 |
| VectorDrawable의 그라데이션·텍스트 제약 | clip-path 밴드 셰이딩, 글리프 외곽선의 pathData 변환 |
| SVG 렌더러 부재 | 브라우저 canvas 래스터화와 Pillow 후처리 분리 |
| 외곽선 두께 불일치 | `dt`/`dl` 거리 비교와 두께 상한으로 바깥 선만 제거 |
| 색온도 불일치 | 고명도 영역 중앙값 기반의 채널별 화이트 포인트 보정 |

식별성은 실루엣과 색상으로, 일관성은 선 두께와 색온도로 맞췄습니다. 두 목표가 서로 다른 속성을 건드리므로 함께 만족시킬 수 있었습니다.
