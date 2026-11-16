"""홈 첫 화면 그림 네 장을 앱 자산(WebP)으로 만든다(S15P31E102-1015).

입력은 디자인으로 받은 그림 `designer/*.webp`다. 그림 내용은 고치지 않고 오른쪽·아래의 흰 여백만 잘라낸다.
만든 뒤에는 첫 화면 제목(로고+붓글씨)이 오른쪽 먹 영역 안에 사방 16dp 이상 여유를 두고 들어오는지 창 크기별로 점검한다.
제목의 크기·자리는 앱의 `homeHeroTitle`(feature/home/HomeScroll.kt)을 옮긴 `hero_title`로 계산한다. 앱의 값을 바꾸면 여기도 같이 고친다.

사용(저장소 루트에서):
  python -I frontend/tools/home-art/build_home_art.py            # 앱 자산 폴더에 덮어쓰고 제목 여유를 점검한다
  python -I frontend/tools/home-art/build_home_art.py --check    # 다시 만들어 지금 앱 자산과 같은지와 제목 여유만 확인한다

필요한 것: Python 3.11, numpy, Pillow(WebP 지원).
같은 폴더의 compose_home_art.py와 입력 그림 두 장은 S15P31E102-983에서 그림을 합성할 때 쓴 이전 방식이다.
"""
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / "shared" / "src" / "commonMain" / "composeResources" / "drawable"

# 이름: 받은 그림에서 쓸 영역(가로, 세로 px). 오른쪽·아래의 흰 여백과 흰색이 번진 경계 줄은 뺀다.
CROP = {
    "phone_portrait": (580, 1254),
    "tablet_portrait": (644, 1028),
    "tablet_landscape": (1586, 992),
}

# 제목이 먹 영역 안에 들어와야 하는 창 크기(dp). 그림 종류를 고르는 기준(homeArtKind)과 같은 묶음이다.
SIZES = {
    "phone_portrait": [(360, 780), (360, 800), (390, 844), (412, 915)],
    "tablet_portrait": [(600, 960), (768, 1024), (800, 1280), (834, 1194)],
    "tablet_landscape": [(1024, 768), (1194, 834), (1280, 800), (1366, 1024)],
}

# 제목 상자와 먹 경계 사이에 남길 여유(dp).
MARGIN = 16.0


def button_height(w):
    side = 40 if w >= 600 else 20
    button_w = min(min(w - side * 2, 1120), 360)
    return max(56.5, button_w * 50 / 310)


def hero_title(name, w, h):
    """첫 화면 제목 상자(왼쪽, 위, 오른쪽, 아래, dp). 앱의 homeHeroTitle과 같은 식이다."""
    if name.endswith("portrait"):
        scale = .8 if name == "phone_portrait" else 1.3
        right = w - 20
        bottom = max(h - 88 - button_height(w), 80) - 10
    else:
        scale = w * .23 / 171
        right = w * .95
        bottom = h * .5 + ((59 + 42) * scale + 4) / 2
    return right - 171 * scale, bottom - ((59 + 42) * scale + 4), right, bottom


def navy_mask(a):
    return (a.max(-1) < 75) & (a[..., 2] >= a[..., 0] - 4)


def title_margin_ok(name, img):
    """창 크기마다 제목 상자(+여유)가 먹 안에 들어오는지 출력하고, 모두 들어오면 True."""
    a = np.asarray(img.convert("RGB")).astype(int)
    m = navy_mask(a)
    ih, iw = m.shape
    ok_all = True
    for w, h in SIZES[name]:
        # 앱처럼 그림이 창을 빈틈없이 덮도록 가운데 맞춰 키운다.
        pw = max(w, h / (ih / iw))
        ph = pw * ih / iw
        px, py, k = (w - pw) * .5, (h - ph) * .5, iw / pw
        x0, y0, x1, y1 = hero_title(name, w, h)
        bx0, by0 = int((x0 - MARGIN - px) * k), int((y0 - MARGIN - py) * k)
        bx1, by1 = int(np.ceil((x1 + MARGIN - px) * k)), int(np.ceil((y1 + MARGIN - py) * k))
        inside = bx0 >= 0 and by0 >= 0 and bx1 <= iw and by1 <= ih
        ratio = m[max(by0, 0):by1, max(bx0, 0):bx1].mean()
        ok = inside and ratio >= .995
        ok_all &= ok
        print(f"  {w}x{h}: 먹 비율 {ratio:.3f} {'OK' if ok else '부족'}")
    return ok_all


def build(name):
    w, h = CROP[name]
    return Image.open(HERE / "designer" / f"{name}.webp").convert("RGB").crop((0, 0, w, h))


def main() -> int:
    check = "--check" in sys.argv[1:]
    failed = []
    with tempfile.TemporaryDirectory() as tmp:
        for name in CROP:
            target = ASSETS / f"home_art_{name}.webp"
            out = Path(tmp) / f"{name}.webp" if check else target
            build(name).save(out, "WEBP", quality=90, method=6)
            print(f"{name}: {CROP[name][0]}x{CROP[name][1]}")
            if check:
                same = target.exists() and ImageChops.difference(
                    Image.open(out).convert("RGB"), Image.open(target).convert("RGB")
                ).getbbox() is None
                print("  앱 자산과", "같음" if same else "다름")
                if not same:
                    failed.append(name)
            if not title_margin_ok(name, Image.open(out)):
                failed.append(name)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
