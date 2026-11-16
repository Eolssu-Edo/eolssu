"""홈 첫 화면 그림 세 장을 앱 자산(WebP)으로 만든다(S15P31E102-1015, S15P31E102-1034).

입력은 디자인으로 받은 그림 `designer/*.webp`다. 만드는 순서:
1. 오른쪽·아래의 흰 여백을 잘라낸다.
2. 소나무를 왼쪽으로 옮기고 연두 종이를 넓힌다(`edit_tree.py`, 그림마다 값은 TREE).
3. 세로 그림은 첫 화면 제목이 오른쪽 먹 영역 안에 사방 16dp 이상 여유를 두고 들어오도록, 모자라는 행만 먹 경계를 왼쪽으로 넓힌다.
   찢어진 경계 모양과 먹 질감은 그대로 옮긴다.
만든 뒤에는 창 크기별로 제목 여유를 다시 점검한다. 제목 크기·자리와 그림을 올리는 양은 앱의 `homeHeroTitle`·`homeArtLift`
(feature/home/HomeScroll.kt)를 옮긴 식이다. 앱의 값을 바꾸면 여기도 같이 고친다.

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

sys.path.insert(0, str(Path(__file__).resolve().parent))
import edit_tree  # noqa: E402

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / "shared" / "src" / "commonMain" / "composeResources" / "drawable"

# 이름: 받은 그림에서 쓸 영역(가로, 세로 px). 오른쪽·아래의 흰 여백과 흰색이 번진 경계 줄은 뺀다.
CROP = {
    "phone_portrait": (580, 1254),
    "tablet_portrait": (644, 1028),
    "tablet_landscape": (1586, 992),
}

# 소나무 이동·연두 넓히기 값(edit_tree.edit 인자). protect는 건물처럼 연두로 덮거나 옮기면 안 되는 곳이다.
TREE = {
    "phone_portrait": dict(shift_px=55, y0=400, y1=560, widen_px=70, reach=200),
    "tablet_portrait": dict(shift_px=60, y0=380, y1=500, widen_px=70, reach=200,
                            protect=[(240, 348), (644, 348), (644, 1028), (55, 1028), (55, 440)]),
    # 가로 그림은 지붕이 연두 경계 가까이 있어, 지붕 위 줄에서만 넓히고 옮긴다.
    "tablet_landscape": dict(shift_px=60, y0=90, y1=165, widen_px=70, reach=260, stretch=160),
}

# 제목이 먹 영역 안에 들어와야 하는 창 크기(dp). 그림 종류를 고르는 기준(homeArtKind)과 같은 묶음이다.
SIZES = {
    "phone_portrait": [(360, 780), (360, 800), (390, 844), (412, 915), (430, 932)],
    "tablet_portrait": [(600, 960), (768, 1024), (800, 1280), (834, 1194), (1024, 1366)],
    "tablet_landscape": [(1024, 768), (1194, 834), (1280, 800), (1366, 1024)],
}

# 제목 상자와 먹 경계 사이에 남길 여유(dp).
MARGIN = 16.0

# 세로 그림 아래쪽 가로 먹 띠가 시작하는 높이(그림 높이 비율). 앱의 HomeArtKind.bandTop과 같다.
BAND_TOP = {"phone_portrait": 1132 / 1254, "tablet_portrait": 929 / 1028, "tablet_landscape": 897 / 992}

# 세로 첫 화면 제목: 너비 = 창 너비 비율, 아래 = 시작 버튼 위 12 + 올림(dp). 앱의 homeHeroTitle과 같다.
PORTRAIT_TITLE = {"phone_portrait": (.56, 24), "tablet_portrait": (.52, 36)}


def button_height(w):
    side = 40 if w >= 600 else 20
    button_w = min(min(w - side * 2, 1120), 360)
    return max(56.5, button_w * 50 / 310)


def hero_title(name, w, h):
    """첫 화면 제목 상자(왼쪽, 위, 오른쪽, 아래, dp)."""
    button_top = max(h - 88 - button_height(w), 80)
    if name in PORTRAIT_TITLE:
        frac, up = PORTRAIT_TITLE[name]
        scale = w * frac / 171
        right = w - 20
        bottom = button_top - 12 - up
    else:
        scale = w * .23 / 171
        right = w * .95
        bottom = min(h * .5 + ((59 + 42) * scale + 4) / 2, button_top - 12)
    return right - 171 * scale, bottom - ((59 + 42) * scale + 4), right, bottom


def art_lift(name, h, w, ph, py):
    """첫 화면에서 그림을 올리는 양(dp). 앱의 homeArtLift와 같은 식이다. 그림 아래 빈 곳은 먹 띠로 채운다."""
    if name not in BAND_TOP:
        return 0.0
    button_top = max(h - 88 - button_height(w), 80)
    return max(0.0, py + ph * BAND_TOP[name] - (button_top - 16))


def placement(name, iw, ih, w, h):
    """앱처럼 그림이 창을 빈틈없이 덮도록 가운데 맞춰 키우고 세로 그림은 올렸을 때의 (왼쪽, 위, dp→px 배율)."""
    pw = max(w, h / (ih / iw))
    ph = pw * ih / iw
    px, py = (w - pw) * .5, (h - ph) * .5
    return px, py - art_lift(name, h, w, ph, py), iw / pw


def navy_mask(a):
    return (a.max(-1) < 75) & (a[..., 2] >= a[..., 0] - 4)


def navy_edge(a):
    """행마다 오른쪽 끝에서 이어지는 먹 영역의 왼쪽 끝 x. 오른쪽 끝이 먹이 아니면 그림 너비를 돌려준다."""
    m = navy_mask(a).astype(np.float32)
    h, w = m.shape
    k = np.ones(5, np.float32) / 5
    edge = np.full(h, w, np.int64)
    for y in range(h):
        row = np.convolve(m[y], k, "same")
        if row[w - 3] < .5:
            continue
        x = w - 3
        while x > 0 and row[x - 1] >= .5:
            x -= 1
        edge[y] = x
    return edge


def widen_black(name, a):
    """제목 상자(+여유)가 먹 안에 들어오도록 먹 경계를 왼쪽으로 옮긴다. 이동량은 줄마다 곧게 늘어나 대각선 경계가 휘지 않는다."""
    ih, iw = a.shape[:2]
    edge = navy_edge(a)
    need = np.zeros(ih)
    for w, h in SIZES[name]:
        px, py, k = placement(name, iw, ih, w, h)
        x0, y0, _, y1 = hero_title(name, w, h)
        target = (x0 - MARGIN - px) * k
        for y in range(max(int((y0 - MARGIN - py) * k), 0), min(int(np.ceil((y1 + MARGIN - py) * k)), ih)):
            need[y] = max(need[y], min(edge[y], iw) - target)
    # 먹은 대각선 모양을 지켜야 하므로, 이동량을 먹이 시작하는 줄에서 0, 제목 아래 줄까지 곧게 늘어나는 직선으로 둔다.
    # 직선의 기울기는 모든 줄의 필요량을 덮는 가장 작은 값이다. 제목 아래로는 같은 양을 유지한다.
    rows = np.where(need > 0)[0]
    shift = np.zeros(ih)
    if len(rows):
        top = int(np.where(edge < iw)[0].min())
        last = int(rows.max())
        slope = max(need[y] / max(1, y - top) for y in rows)
        y = np.arange(ih)
        shift = np.clip((np.minimum(y, last) - top) * slope, 0, None)
    out = a.copy()
    for y in range(ih):
        d = int(round(shift[y]))
        if d <= 0 or edge[y] >= iw:
            continue
        start = max(0, edge[y] - 3 - d)
        xs = np.arange(start, iw) + d
        out[y, start:] = a[y, np.where(xs < iw, xs, 2 * (iw - 1) - xs)]
    return out, shift.max()


def title_margin_ok(name, img):
    """창 크기마다 제목 상자(+여유)가 먹 안에 들어오는지 출력하고, 모두 들어오면 True. 올린 그림 아래 빈 곳은 먹으로 본다."""
    a = np.asarray(img.convert("RGB")).astype(int)
    m = navy_mask(a)
    ih, iw = m.shape
    ok_all = True
    for w, h in SIZES[name]:
        px, py, k = placement(name, iw, ih, w, h)
        x0, y0, x1, y1 = hero_title(name, w, h)
        bx0, by0 = int((x0 - MARGIN - px) * k), int((y0 - MARGIN - py) * k)
        bx1, by1 = int(np.ceil((x1 + MARGIN - px) * k)), int(np.ceil((y1 + MARGIN - py) * k))
        inside = bx0 >= 0 and by0 >= 0 and bx1 <= iw
        ratio = m[max(by0, 0):min(by1, ih), max(bx0, 0):bx1].mean()
        ok = inside and ratio >= .995
        ok_all &= ok
        print(f"  {w}x{h}: ratio {ratio:.3f} {'OK' if ok else 'FAIL'}")
    return ok_all


def build(name):
    w, h = CROP[name]
    a = np.asarray(Image.open(HERE / "designer" / f"{name}.webp").convert("RGB").crop((0, 0, w, h)))
    a, _ = edit_tree.edit(a, **TREE[name])
    if name in PORTRAIT_TITLE:
        a, moved = widen_black(name, a)
        print(f"{name}: black edge moved up to {moved:.0f}px")
    return Image.fromarray(a)


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
                print("  asset", "SAME" if same else "DIFF")
                if not same:
                    failed.append(name)
            if not title_margin_ok(name, Image.open(out)):
                failed.append(name)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
