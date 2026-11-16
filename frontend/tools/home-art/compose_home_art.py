"""홈 첫 화면 그림 한 장을 합성한다(S15P31E102-983).

기존 홈 그림의 구도(왼쪽 연두 종이 · 가운데 한지 산 · 오른쪽 먹)에 전각 전경 그림을 넣는다.
연두 종이·먹 질감, 먹 가장자리 모양, 경계의 작은 구름은 기존 홈 그림에서, 전각·연주자·소나무·바위·산은 전각 그림에서 가져온다.
난수 씨앗을 고정해 같은 인자로는 같은 그림이 나온다. 네 장을 한 번에 만들려면 build_home_art.py를 쓴다.

사용: compose_home_art.py OLD SRC OUT W H S TOP X0 XG XM
  OLD 기존 홈 그림(1086×1448), SRC 전각 전경 그림(1672×941), OUT 결과 PNG
  W H 결과 크기(px)
  S   전각 그림 배율, TOP 전각 그림 위쪽 위치(px), X0 전각 그림에서 버리는 왼쪽 폭(원본 px, 음수면 그만큼 오른쪽으로 민다)
  XG  화면 중간 높이에서 연두·한지 경계의 가로 위치(px)
  XM  기존 홈 그림의 먹 경계 틀(높이에 맞춰 키운 것)을 놓는 가로 위치(px)
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OLD, SRC, OUT = sys.argv[1:4]
W, H = int(sys.argv[4]), int(sys.argv[5])
S, TOP, X0, XG, XM = float(sys.argv[6]), int(sys.argv[7]), int(sys.argv[8]), int(sys.argv[9]), int(sys.argv[10])
OW, OH = 1086, 1448
m = H / OH

old_im = Image.open(OLD).convert("RGB")
old = np.asarray(old_im).astype(np.float32)
src_im = Image.open(SRC).convert("RGB")
SW, SH = src_im.size
rng = np.random.default_rng(7)


def lum(a):
    return a[..., 0] * .299 + a[..., 1] * .587 + a[..., 2] * .114


def blur(mask, r):
    return np.asarray(Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255


def flood(ok, seeds):
    im = Image.fromarray((ok * 255).astype(np.uint8)).copy()
    im.load()
    for x, y in seeds:
        x, y = int(x), int(y)
        if 0 <= x < ok.shape[1] and 0 <= y < ok.shape[0] and ok[y, x]:
            ImageDraw.floodfill(im, (x, y), 128)
    return np.array(im) == 128


# 1. 기존 그림(원래 크기)에서 연두 경계선과 먹칠 영역을 읽는다.
r, g, b = old[..., 0], old[..., 1], old[..., 2]
old_lime = (g > 150) & (g - b > 90) & (g > r)
old_navy = flood((old.max(-1) < 78) & (b >= r - 6), [(OW - 3, OH - 3), (OW - 3, 700), (OW - 3, 300), (OW - 20, 20)])
raw = np.zeros(OH, np.float32)
for y in range(OH):
    row = old_navy[y]
    x = OW - 1
    while x > 6 and row[x - 6:x + 1].mean() > .5:
        x -= 1
    raw[y] = x
kk = 100
med = np.array([np.median(raw[max(0, y - kk):y + kk + 1]) for y in range(OH)], np.float32)
nedge = np.where(np.arange(OH) < 1285, np.maximum(raw, med - 14), raw)
texture_src = old[900:1448, 800:1086].copy()
_low = np.asarray(Image.fromarray(texture_src.astype(np.uint8)).filter(ImageFilter.GaussianBlur(45))).astype(np.float32)
texture_src = texture_src - _low + _low.reshape(-1, 3).mean(0)
old_navy = np.arange(OW)[None, :] >= nedge[:, None]
old_navy[1250:, :330] = False
lime_ref = old[old_lime].mean(0)
navy_ref = texture_src.reshape(-1, 3).mean(0)
edge0 = np.zeros(OH, np.float32)
for y in range(OH):
    xs = np.where(old_lime[y])[0]
    edge0[y] = xs.max() if len(xs) > 40 else -1
last0 = max(y for y in range(OH) if edge0[y] > 0)
for y in range(OH):
    if edge0[y] < 0:
        edge0[y] = edge0[y - 1] if 0 < y <= last0 else -40
k = 9
sm = np.convolve(np.pad(edge0, k, mode="edge"), np.ones(2 * k + 1) / (2 * k + 1), "valid")
edge0 = np.maximum(edge0, sm - 12)
# 연두 경계는 원래 기울기로 바닥까지 잇는다(가로 화면에서 왼쪽이 넓어도 끊기지 않게).
slope = (edge0[last0] - edge0[last0 - 300]) / 300
edge0[last0:] = edge0[last0] + slope * np.arange(OH - last0)

# 2. 경계 틀을 화면 높이에 맞춰 같은 비율로 키워 XM에 놓는다. 틀 왼쪽 밖은 연두, 오른쪽 밖은 먹.
mw = int(round(OW * m))
old_s = np.asarray(old_im.resize((mw, H), Image.LANCZOS)).astype(np.float32)
navy_s = np.asarray(Image.fromarray((old_navy * 255).astype(np.uint8)).resize((mw, H), Image.BILINEAR)) > 127
ys = np.clip((np.arange(H) / m).astype(int), 0, OH - 1)
nslope = np.polyfit(np.arange(60, 1240), nedge[60:1240], 1)[0]


def noise1(n, scale, seed):
    r = np.random.default_rng(seed)
    kn = r.uniform(-1, 1, int(n / scale) + 4)
    x = np.arange(n) / scale
    i0 = x.astype(int)
    t = x - i0
    t = t * t * (3 - 2 * t)
    return kn[i0] * (1 - t) + kn[i0 + 1] * t


yr = np.arange(H)
edge = (XG + nslope * 1.06 * (yr - H / 2)
        + noise1(H, 150 * m, 1) * 15 * m + noise1(H, 45 * m, 2) * 7 * m + noise1(H, 11 * m, 3) * 2.6 * m)
# 한지 아래 그림자의 두께: 곳에 따라 얇아졌다 두꺼워진다.
shw = np.clip(noise1(H, 110 * m, 4) * .75 + .45 + noise1(H, 30 * m, 5) * .22, .06, 1) * 21 * m
last = int(last0 * m)
xx, yy = np.meshgrid(np.arange(W), np.arange(H))
hs = xx >= edge[:, None]  # 한지 쪽(찢어진 경계는 기존 그림의 모양 그대로)
left = blur((~hs).astype(np.float32), 1.2)
navy = np.zeros((H, W), bool)
navy_px = np.empty((H, W, 3), np.float32)
navy_px[:] = navy_ref
nt = Image.fromarray(np.clip(texture_src, 0, 255).astype(np.uint8))
nt = np.asarray(nt.resize((max(1, int(nt.width * m)), max(1, int(nt.height * m))), Image.LANCZOS)).astype(np.float32)
nt = np.concatenate([nt, nt[::-1]], 0)
nt = np.concatenate([nt, nt[:, ::-1]], 1)
navy_tile = np.tile(nt, (H // nt.shape[0] + 1, (W + 3 * mw) // nt.shape[1] + 1, 1))
navy_px[:] = navy_tile[:H, :W]
pad = navy_tile[:H, :3 * mw]
a0, a1 = max(0, XM), W
if a1 > a0:
    seg = pad[:, a0 - XM:a1 - XM]
    navy_px[:, a0:a0 + seg.shape[1]] = seg
    nm = np.concatenate([navy_s, np.ones((H, 2 * mw), bool)], 1)[:, a0 - XM:a1 - XM]
    navy[:, a0:a0 + nm.shape[1]] = nm
navy = blur(navy.astype(np.float32), 1.2) > .5

_p = np.asarray(src_im.crop((1176, 22, 1672, 318))).astype(np.float32).reshape(-1, 3)
_pl = _p[(_p.max(1) - _p.min(1) < 40) & (_p.mean(1) > 208)]
hanji = np.median(_pl, 0).astype(np.float32)
print('hanji', hanji)
# 3. 연두 종이를 깔고 새 그림을 놓는다.
tex = Image.fromarray(old[0:135, 0:630].astype(np.uint8))
tex = np.asarray(tex.resize((max(1, int(630 * m)), max(1, int(135 * m))), Image.LANCZOS)).astype(np.float32)
tile = np.concatenate([tex, tex[::-1]], 0)
tile = np.concatenate([tile, tile[:, ::-1]], 1)
paper_green = np.tile(tile, (H // tile.shape[0] + 1, W // tile.shape[1] + 1, 1))[:H, :W]
canvas = paper_green.copy()
scaled = np.asarray(src_im.resize((int(SW * S), int(SH * S)), Image.LANCZOS)).astype(np.float32)
x0p = int(X0 * S)
xs0 = max(x0p, 0)


def lpad(a):
    # 그림을 오른쪽으로 밀 때(X0 < 0) 왼쪽 빈 곳은 그림 왼쪽 끝을 거울로 이어 채운다.
    return a if x0p >= 0 else np.concatenate([a[:, :-x0p][:, ::-1], a], 1)


main = lpad(scaled)[max(0, -TOP) + (5 if TOP > 0 else 0):, xs0:xs0 + W]
top = max(0, TOP)
bottom = min(H, top + main.shape[0])
mainw = main.shape[1]
canvas[top:bottom, :mainw] = main[:bottom - top]
if mainw < W:
    canvas[:, mainw:] = navy_ref

# 소나무 영역(새 그림 원본 좌표). 전각 지붕과 겹치지 않는다.
PINE_POLY = [(0, 0), (480, 0), (480, 150), (468, 198), (284, 207), (284, 262), (440, 266), (440, 395), (275, 405),
             (245, 440), (235, 470), (160, 500), (140, 560), (160, 640), (105, 700), (60, 760), (0, 790)]
_pp = Image.new("L", (SW, SH), 0)
ImageDraw.Draw(_pp).polygon(PINE_POLY, fill=255)
_sa = np.asarray(src_im).astype(np.float32)
_sl = lum(_sa)
_sbg = ((_sl > 150) & (_sa.max(-1) - _sa.min(-1) < 62)) | ((_sa[..., 1] - _sa[..., 2] > 60) & (_sa[..., 1] > 110))
pine_src = (np.asarray(_pp) > 0) & ~_sbg
_zone = np.zeros((SH, SW), bool)
_zone[226:304, 276:472] = True
pine_src &= ~(_zone & ((_sl > 85) | (_sa[..., 2] < _sa[..., 1])))
_ev = Image.new('L', (SW, SH), 0)
ImageDraw.Draw(_ev).polygon([(296, 212), (480, 212), (480, 298), (372, 298), (322, 274), (296, 250)], fill=255)
pine_src &= ~(np.asarray(_ev) > 0)
pine_src = blur(pine_src.astype(np.float32), 1.0)
# 그림 안의 원래 소나무는 지우고(바탕으로 돌리고), 아래에서 따로 크게 놓는다.
_off = max(0, -TOP) + (5 if TOP > 0 else 0)
_pm = np.asarray(Image.fromarray((np.clip(blur(pine_src.astype(np.float32), 2.2) * 3, 0, 1) * 255).astype(np.uint8)).resize((int(SW * S), int(SH * S)), Image.BILINEAR)).astype(np.float32) / 255
_pm = lpad(_pm)[_off:, xs0:xs0 + W][:bottom - top]
_fill = paper_green[top:bottom, :_pm.shape[1]] * left[top:bottom, :_pm.shape[1], None] + hanji[None, None, :] * (1 - left[top:bottom, :_pm.shape[1], None])
canvas[top:bottom, :_pm.shape[1]] = canvas[top:bottom, :_pm.shape[1]] * (1 - _pm[..., None]) + _fill * _pm[..., None]
sx = (xx + x0p) / S  # 새 그림 원본 좌표
sy = (yy - TOP) / S
if TOP > 60 * S:
    # 위에서 잘린 소나무 잎은 거울로 조금 이어 붙이고 흐리게 지운다.
    M = 0 if W > H else min(TOP, int(10 * S))
    M = max(M, 1)
    mirror = main[:M][::-1]
    dark = blur((lum(mirror) < 105).astype(np.float32), 2)
    a = (dark * (np.linspace(0, 1, M)[:, None] ** 1.2))[..., None]
    canvas[TOP - M:TOP, :mainw] = canvas[TOP - M:TOP, :mainw] * (1 - a) + mirror * a
    # 가운데 띠: 산을 원근에 맞춰 놓는다.
    def dehaze(a, rows):
        t = a[:rows]
        k = np.clip((t[..., 1] - t[..., 2] - 22) / 25, 0, 1) * (t[..., 1] > t[..., 0] - 4)
        k = blur(k, 2)[..., None]
        a[:rows] = t * (1 - k) + hanji[None, None, :] * k
    hsoft = blur(hs.astype(np.float32), 1.2)
    lowf = np.asarray(Image.fromarray((rng.random((H // 40 + 2, W // 40 + 2)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)).astype(np.float32) / 255
    hb = hanji[None, None, :] * (.985 + .03 * lowf[..., None] + rng.normal(0, .01, (H, W, 1)).astype(np.float32))
    hz = canvas[TOP:TOP + int(150 * S)].copy()
    dehaze(hz, hz.shape[0])
    canvas[TOP:TOP + hz.shape[0]] = np.where(hs[TOP:TOP + hz.shape[0], :, None], hz, canvas[TOP:TOP + hz.shape[0]])
    # 새 그림에서 쓸 산·나무 구역(원본 좌표)과, 위쪽에 하늘이 섞인 줄 수.
    SRCS = {
        "peaks": ((1176, 0, 1672, 190), 46),
        "peaksL": ((1176, 0, 1430, 190), 46),
        "peaksR": ((1400, 0, 1672, 200), 40),
        "right": ((1440, 150, 1672, 335), 0),
        "trees": ((1440, 300, 1672, 470), 0),
        "peaksM": ((1290, 0, 1560, 190), 46),
        "ridge": ((520, 30, 1000, 130), 22),
        "ridgeA": ((520, 30, 780, 130), 22),
        "ridgeB": ((740, 30, 1000, 130), 22),
        "ridge2": ((1000, 20, 1180, 118), 30),
    }
    tiles = {}
    talpha = {}
    for name, (box, sky) in SRCS.items():
        a = np.asarray(src_im.crop(box)).astype(np.float32).copy()
        dehaze(a, a.shape[0])
        la = lum(a)
        pl = (la > 186) & (a.max(-1) - a.min(-1) < 60)
        skym = flood(pl, [(x, 0) for x in range(0, a.shape[1], 6)])
        tiles[name] = a
        talpha[name] = 1 - blur(skym.astype(np.float32), 2.5)
    field = hb.copy()
    y_far = .12 * H
    y_base = TOP + 108 * S  # 가장 가까운 산의 아래 끝(새 그림의 지붕 뒤 능선과 만나는 높이)
    nx = lambda yc: nedge[min(OH - 1, max(0, int(yc / m)))] * m + XM

    def put(name, cx_right, y_bottom, sc, fade, flip, limit=True):
        a = tiles[name]
        room = y_bottom - y_far
        if limit and a.shape[0] * sc > room:
            sc = room / a.shape[0]
            if sc < .3 * S:
                return max(8, int(a.shape[1] * .3 * S))
        w2, h2 = max(8, int(a.shape[1] * sc)), max(8, int(a.shape[0] * sc))
        t = np.asarray(Image.fromarray(a.astype(np.uint8)).resize((w2, h2), Image.LANCZOS)).astype(np.float32)
        if flip:
            t = t[:, ::-1]
        t = t * (1 - fade) + hanji[None, None, :] * fade
        t = t * (1 - .5 * fade * np.array([.05, 0, -.06], np.float32))[None, None, :]
        mk = np.asarray(Image.fromarray((talpha[name] * 255).astype(np.uint8)).resize((w2, h2), Image.BILINEAR)).astype(np.float32) / 255
        if flip:
            mk = mk[:, ::-1]
        ft, fb, fx = (max(2, int(.34 * h2)) if name in ('right', 'trees') else 2), max(2, int(.5 * h2)), max(2, int(.2 * w2))
        mk[:ft] *= np.linspace(0, 1, ft)[:, None]
        mk[-fb:] *= (np.linspace(1, 0, fb) ** .8)[:, None]
        mk[:, :fx] *= np.linspace(0, 1, fx)[None, :]
        mk[:, -fx:] *= np.linspace(1, 0, fx)[None, :]
        px0, py0 = int(cx_right - w2), int(y_bottom - h2)
        x0c, x1c, y0c, y1c = max(0, px0), min(W, px0 + w2), max(0, py0), min(H, py0 + h2)
        if x1c <= x0c or y1c <= y0c:
            return w2
        aa = mk[y0c - py0:y1c - py0, x0c - px0:x1c - px0][..., None]
        field[y0c:y1c, x0c:x1c] = field[y0c:y1c, x0c:x1c] * (1 - aa) + t[y0c - py0:y1c - py0, x0c - px0:x1c - px0] * aa
        return w2

    FAR = ["ridge", "ridgeA", "ridgeB", "ridge2"]
    MID = ["peaksL", "peaksR", "ridgeB", "peaksM", "peaks", "ridge2"]
    NEAR = ["peaks", "peaksR", "peaksM", "peaksL"]
    span = y_base - (y_far + 75 * S)
    nrow = (int(round(max(span, 0) / (62 * S))) + 1) if y_base - y_far > 40 * S else 0
    for r in range(nrow):  # 먼 산부터
        t = r / max(1, nrow - 1) if nrow > 1 else 1.0
        yb = y_far + 75 * S + span * (t ** 1.15) if nrow > 1 else y_base
        sc0 = S * (.5 + .5 * t)
        fade = .6 * (1 - t) ** 1.1
        pool = FAR if t < .34 else (MID if t < .72 else NEAR)
        x = min(W + 80 * S, nx(yb) + 90 * S) - rng.uniform(0, 120) * S
        last = None
        while x > -40 * S:
            name = pool[int(rng.integers(len(pool)))]
            if name == last:
                name = pool[(pool.index(name) + 1) % len(pool)]
            last = name
            w2 = put(name, x, yb + rng.uniform(-16, 16) * S * (.5 + .5 * t), sc0 * rng.uniform(.82, 1.18), fade, bool(rng.integers(2)))
            x -= w2 * rng.uniform(.52, .8)
    # 지붕 아래쪽 빈 곳에 쓸 산: 가까운 크기로 화면 아래까지 깔아 두고, 빈 한지인 곳에만 쓴다.
    low = hb.copy()
    field, top_field = low, field
    yb = y_base + 150 * S
    while yb < bottom + 60 * S:
        x = W + rng.uniform(0, 160) * S
        last = None
        while x > -40 * S:
            name = ["right", "trees", "peaksR", "peaksL", "ridgeB"][int(rng.integers(5))]
            if name == last:
                name = "trees" if name != "trees" else "right"
            last = name
            w2 = put(name, x, yb + rng.uniform(-22, 22) * S, S * rng.uniform(.62, .9), .12, bool(rng.integers(2)), False)
            x -= w2 * rng.uniform(.55, .85)
        yb += rng.uniform(88, 120) * S
    field, low = top_field, field
    # 위쪽: 한지 바탕과 산을 그림 위 빈 곳에 깔고, 그림의 지붕 뒤 능선과 겹쳐 잇는다.
    va = np.clip((TOP + 108 * S - yy) / (88 * S), 0, 1) * hsoft
    canvas = canvas * (1 - va[..., None]) + field * va[..., None]
    # 아래쪽: 소나무를 떼어낸 자리 가운데 한지 쪽에 남은 빈 곳을 산으로 채운다.
    hole = np.zeros((H, W), np.float32)
    hole[top:bottom, :_pm.shape[1]] = _pm
    hole = np.clip(blur(hole, 3 * S) * 1.4, 0, 1) * hsoft * (yy > TOP + 40 * S)
    canvas = canvas * (1 - hole[..., None]) + low * hole[..., None]

# 4. 연두 경계 왼쪽의 한지·하늘 바탕을 연두 종이로 바꾼다(소나무·전각·사람은 그대로).
L = lum(canvas)
sat = canvas.max(-1) - canvas.min(-1)
limeish = (canvas[..., 1] - canvas[..., 2] > 70) & (canvas[..., 1] > 120)
paper = (L > 150) & (sat < 62)
seeds = [((px - X0) * S, TOP + py * S) for px, py in [(400, 5), (600, 8), (300, 30), (60, 300), (30, 200), (150, 420), (700, 20), (900, 14)]]
seeds += [(W * .2, 3), (W * .4, 3), (5, 5)]
bg = flood(paper | limeish, seeds)
bg |= (paper | limeish) & (sx < 262) & (sy < 640)
bg |= paper & (sx < 440) & (sy > 300) & (sy < 403)
bg &= ~((sy > 632) & (np.abs(sx) < 352))  # 바위의 밝은 면은 바탕으로 보지 않는다
bgf = blur(bg.astype(np.float32), 1.0)
ga = (bgf * left)[..., None]
canvas = canvas * (1 - ga) + paper_green * ga
# 경계 오른쪽에 남은 연두 하늘은 한지색으로 돌린다.
limeish = (canvas[..., 1] - canvas[..., 2] > 60) & (canvas[..., 1] > 110) & (canvas[..., 1] > canvas[..., 0])
ha = (blur(limeish.astype(np.float32), 1.2) * (1 - left))[..., None]
canvas = canvas * (1 - ha) + (hanji[None, None, :] * np.clip(lum(canvas) / 165, .97, 1.03)[..., None]) * ha
# 경계 효과: 한지 가장자리 아래의 짙은 올리브색 그림자(두께가 변한다)와 한지 끝의 옅은 음영.
bgc = blur((bg | (yy < TOP)).astype(np.float32), 1.5)
hsf = blur(hs.astype(np.float32), 1.2)
sh = ((xx < edge[:, None]) & (xx >= edge[:, None] - shw[:, None])).astype(np.float32)
sh = np.clip(blur(sh, .9 * m) * 1.15, 0, 1) * (1 - hsf) * bgc
_n = np.asarray(Image.fromarray((rng.random((H // 30 + 2, W // 30 + 2)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)).astype(np.float32) / 255
olive = np.array([86, 101, 27], np.float32)[None, None, :] * (.94 + .12 * _n[..., None])
canvas = canvas * (1 - .93 * sh[..., None]) + olive * (.93 * sh[..., None])
amb = np.clip(blur((xx >= edge[:, None] - shw[:, None] - 10 * m).astype(np.float32), 7 * m) - blur(hs.astype(np.float32), 2 * m), 0, 1) * bgc * (1 - hsf)
canvas *= (1 - .16 * amb)[..., None]
rim = np.clip(hsf - blur((xx >= edge[:, None] + 5 * m).astype(np.float32), 3 * m), 0, 1) * bgc
canvas *= (1 - .07 * rim)[..., None]

# 소나무와 밑동의 바위·꽃을 한 덩어리로 따로 놓는다. 바위는 그림의 땅 높이에 맞추고, 나무 끝은 화면 맨 위 가까이 오게 키운다.
_rk = Image.new("L", (SW, SH), 0)
ImageDraw.Draw(_rk).polygon([(0, 792), (62, 762), (107, 702), (150, 655), (258, 650), (264, 738), (330, 748), (345, SH), (0, SH)], fill=255)
_rb = (np.asarray(_rk) > 0) & ~_sbg
_wall = blur(_rb.astype(np.float32), 3) > .2
_out = flood(~_wall, [(SW - 2, 2), (400, 700), (300, 600), (200, 560), (345, 900)])
_rf = ((np.asarray(_rk) > 0) & ~_out) | ((blur(_rb.astype(np.float32), 7) > .42) & (np.asarray(_rk) > 0))
rock_src = blur((_rb | _rf).astype(np.float32), 1.0)
tree_src = np.clip(pine_src + rock_src, 0, 1)
base_y = TOP + 760 * S
if W > H:
    KP = max(S, base_y / 760)
else:
    KP = max(1.25 * S, (base_y - .02 * H) / 1010)
fl = 400 * S - x0p  # 연주자의 왼쪽 끝(가리지 않는다)
pdx = int(60 * KP - 60 * S + max(x0p, 0)) + int(40 * KP) if W > H else int(110 * KP)
if W <= H:
    _xr = (fl - 26 * S + pdx) / KP
    tree_src[625:] *= np.clip((_xr - np.arange(SW)) / 16, 0, 1)[None, :]
    rock_src[625:] *= np.clip((_xr - np.arange(SW)) / 16, 0, 1)[None, :]
pdy = int(base_y - 760 * KP)
pw_, ph_ = int(480 * KP), int(SH * KP)
prgb = np.asarray(src_im.crop((0, 0, 480, SH)).resize((pw_, ph_), Image.LANCZOS)).astype(np.float32)
pal = np.asarray(Image.fromarray((tree_src[:, :480] * 255).astype(np.uint8)).resize((pw_, ph_), Image.LANCZOS)).astype(np.float32) / 255
_ral = np.asarray(Image.fromarray((rock_src[:, :480] * 255).astype(np.uint8)).resize((pw_, ph_), Image.LANCZOS)).astype(np.float32)[..., None] / 255
_tone = (lum(prgb) / 168)[..., None] * np.array([178, 158, 132], np.float32)[None, None, :]
prgb = prgb * (1 - .5 * _ral) + _tone * (.5 * _ral)
print("tree scale", round(KP, 2), "top", pdy, "shift", pdx)


def put_tree(dy, rows):
    al = pal[:rows]
    x0c, x1c = max(0, -pdx), min(W, pw_ - pdx)
    y0c, y1c = max(0, dy), min(H, rows + dy)
    if x1c <= x0c or y1c <= y0c:
        return
    aa = al[y0c - dy:y1c - dy, x0c + pdx:x1c + pdx][..., None]
    canvas[y0c:y1c, x0c:x1c] = canvas[y0c:y1c, x0c:x1c] * (1 - aa) + prgb[y0c - dy:y1c - dy, x0c + pdx:x1c + pdx] * aa


# 나무 끝이 화면 위에 못 미치면, 윗가지 한 단을 뒤에 겹쳐 올려 맨 위까지 잇는다.
up = min(250 * KP, pdy - .012 * H)
if up > 60 * KP:
    put_tree(int(pdy - up), int(300 * KP))
put_tree(pdy, ph_)

# 연주자는 바위 앞에 다시 그린다.
_fm = np.zeros((SH, SW), np.float32)
_fm[778:936, 390:488] = 1
_fm = blur(_fm * (~_sbg), .8)
_fm = np.asarray(Image.fromarray((_fm * 255).astype(np.uint8)).resize((int(SW * S), int(SH * S)), Image.BILINEAR)).astype(np.float32) / 255
_fm = lpad(_fm)[_off:, xs0:xs0 + W][:bottom - top]
_mn = main[:bottom - top, :_fm.shape[1]]
canvas[top:bottom, :_fm.shape[1]] = canvas[top:bottom, :_fm.shape[1]] * (1 - _fm[..., None]) + _mn * _fm[..., None]

# 구름: 기존 그림의 구름 다섯 모양을 테두리째 잘라, 경계에 걸치거나 경계 가까운 연두 위에 놓는다.
ol_all = lum(old)
lime_soft = np.clip((old[..., 1] - old[..., 2] - 40) / 60, 0, 1)
BOXES = [(600, 214, 724, 272), (588, 300, 704, 354), (532, 362, 664, 412), (496, 520, 606, 570), (350, 660, 476, 712)]
sprites = []
for bx0, by0, bx1, by1 in BOXES:
    bx1 = min(OW, max(bx1, int(edge0[(by0 + by1) // 2]) + 44))
    reg = old[by0:by1, bx0:bx1]
    a = (1 - lime_soft[by0:by1, bx0:bx1]) * np.clip((ol_all[by0:by1, bx0:bx1] - 96) / 26, 0, 1)
    ee = edge0[by0:by1, None]
    cols = np.arange(bx0, bx1)[None, :]
    hh = by1 - by0
    vr = np.clip(np.minimum(np.arange(hh), np.arange(hh)[::-1]) / 10, 0, 1)[:, None]
    a = a * vr * np.clip((cols - bx0) / 10, 0, 1)
    a_edge = np.where(cols < ee, a, np.clip(1 - (cols - ee) / 40, 0, 1) * .85 * vr)
    a_edge = a_edge * np.clip((bx1 - 1 - cols) / 14, 0, 1)
    a_free = np.where(cols < ee - 2, a, 0)
    sprites.append((reg, blur(a_edge, .7), blur(a_free, .7), float(edge0[(by0 + by1) // 2] - bx0)))
print("clouds", len(sprites))
cand = [y for y in range(int(34 * m), H - int(40 * m)) if 12 * m < edge[y] < W - 12 * m
        and bgc[y, int(max(0, edge[y] - 28 * m))] > .9 and bgc[y, int(min(W - 1, edge[y] + 14 * m))] > .9]
if cand:
    n = int(np.clip(len(cand) / (210 * m), 2, 6)) + 4
    order = rng.permutation(len(sprites))
    for i in range(n):
        yc = cand[int(np.clip((i + .5 + rng.uniform(-.3, .3)) / n, 0, .999) * len(cand))]
        reg, a_edge, a_free, anchor = sprites[order[i % len(sprites)]]
        free = i % 3 == 2
        sc = min(m, 1.3) * rng.uniform(1.5, 2.2)
        w2, h2 = max(2, int(reg.shape[1] * sc)), max(2, int(reg.shape[0] * sc))
        rg = np.asarray(Image.fromarray(reg.astype(np.uint8)).resize((w2, h2), Image.LANCZOS)).astype(np.float32)
        al = a_free if free else a_edge
        al = np.asarray(Image.fromarray((np.clip(al, 0, 1) * 255).astype(np.uint8)).resize((w2, h2), Image.LANCZOS)).astype(np.float32) / 255
        off = rng.uniform(40, 150) * m if free else 0
        px0, py0 = int(edge[yc] - anchor * sc - off), int(yc - h2 / 2)
        x0c, x1c, y0c, y1c = max(0, px0), min(W, px0 + w2), max(0, py0), min(H, py0 + h2)
        if x1c <= x0c or y1c <= y0c:
            continue
        aa = (al[y0c - py0:y1c - py0, x0c - px0:x1c - px0] * bgc[y0c:y1c, x0c:x1c])[..., None]
        canvas[y0c:y1c, x0c:x1c] = canvas[y0c:y1c, x0c:x1c] * (1 - aa) + rg[y0c - py0:y1c - py0, x0c - px0:x1c - px0] * aa
# 산 위 하늘의 구름: 두지 않는다(새로 그린 구름은 쓰지 않기로 했다).

# 5. 먹칠: 기존 먹 영역을 덮고, 그림 아래는 먹으로 잇는다.
f = 34 * S
ground = np.clip((yy - (bottom - f)) / f, 0, 1).astype(np.float32)
flat = navy_tile[:H, :W]
canvas = canvas * (1 - ground[..., None]) + flat * ground[..., None]
navy_px = np.where(navy[..., None], navy_px, flat)
navy |= yy > bottom - 4
na = blur(navy.astype(np.float32), 1.0)[..., None]
canvas = canvas * (1 - na) + navy_px * na

Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8)).save(OUT)
print("saved", OUT, W, H)
