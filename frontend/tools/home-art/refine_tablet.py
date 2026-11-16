"""채택한 휴대폰 개선 그림(refine_reference.py)과 같은 처리로 태블릿 세로·가로 그림을 만든다.

- 재료는 같다: base_home_illustration.webp(1086x1448)에 소나무 점 제거 → 확대 → 밝기 선명화 → 먹선·먹 번짐 → 먹 질감 → 한지 결 → 채도 조정.
- 태블릿 세로(1600x2560): 휴대폰과 같은 세로 범위(원본 y 337.7~1609.5)를 좌우로 68px씩 넓힌다. 구도·크기 관계는 휴대폰과 같다.
- 태블릿 가로(2560x1600): 원본 y 546~1406, x 0~1376을 보여 준다. 먹을 원본 기준 240px(먹 폭의 약 1/3) 오른쪽으로 옮기고
  드러난 자리는 build_landscape_base()가 새로 그린다. 연주자 발밑(원본 y 1300~1322)부터 아래는 가로 먹 띠다(높이 89% 지점).
- 연주자 아래 그림자는 휴대폰 캡처에서 잰 원본 행별 비율을 그대로 쓴다.

사용: python refine_tablet.py OUTDIR  (refine_phone.py 결과가 OUTDIR에 있어야 비교 그림을 만든다)
"""
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.dont_write_bytecode = True
OUT = sys.argv[1]
TOOLS = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(TOOLS, "../../shared/src/commonMain/composeResources"))
sys.path.insert(0, TOOLS)
import edit_tree  # noqa: E402
import bark_dots  # noqa: E402

ns = {**vars(edit_tree), **vars(bark_dots)}
clear_bark_dots, blur = ns["clear_bark_dots"], ns["blur"]

S, OX, OY = 1.398, 77.0029, 236.14     # 휴대폰 캡처 px = (원본 px - O) * S
K_PHONE = S * 1080 / 920               # 휴대폰 결과의 원본 px 당 결과 px. 질감 크기를 이 값에 맞춰 키운다.
INK = np.array([24, 30, 45], np.float32)


def lum(a):
    return a[..., 0] * .299 + a[..., 1] * .587 + a[..., 2] * .114


def gblur(a, r):
    if a.ndim == 3:
        return np.stack([gblur(a[..., c], r) for c in range(a.shape[2])], -1)
    w = max(1, int(round(np.sqrt(12 * r * r / 3 + 1))))
    out = a.astype(np.float32)
    for _ in range(3):
        for axis in (0, 1):
            p = w // 2
            pad = [(0, 0), (0, 0)]
            pad[axis] = (p + 1, w - p - 1)
            c = np.cumsum(np.pad(out, pad, mode="edge"), axis=axis, dtype=np.float64)
            n = out.shape[axis]
            out = ((np.take(c, np.arange(w, w + n), axis=axis) - np.take(c, np.arange(0, n), axis=axis)) / w).astype(np.float32)
    return out


def value_noise(h, w, cell, seed):
    r = np.random.default_rng(seed)
    cell = max(1, int(round(cell)))
    g = r.random((h // cell + 3, w // cell + 3)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8)).resize(((w // cell + 3) * cell, (h // cell + 3) * cell), Image.BICUBIC)
    return np.asarray(im, np.float32)[:h, :w] / 255 - .5


def fbm(h, w, cells, seed):
    out = np.zeros((h, w), np.float32)
    amp = 1.0
    for i, c in enumerate(cells):
        out += value_noise(h, w, c, seed + i) * amp
        amp *= .55
    return out / np.abs(out).max()


def toggle(a, k, mix):
    out = np.empty_like(a)
    for c in range(3):
        ch = Image.fromarray(np.clip(a[..., c], 0, 255).astype(np.uint8))
        mn = np.asarray(ch.filter(ImageFilter.MinFilter(k)), np.float32)
        mx = np.asarray(ch.filter(ImageFilter.MaxFilter(k)), np.float32)
        v = a[..., c]
        out[..., c] = np.where(v - mn < mx - v, mn, mx)
    return a * (1 - mix) + out * mix


def morph(m, f):
    return np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(f)) > 127


# 1. 원본 정리(휴대폰과 같다): 가지의 검은 점 → 껍질 비늘, 연두 위 고립된 검은 점 제거.
orig = np.asarray(Image.open(TOOLS + "/base_home_illustration.webp").convert("RGB"))
PINE_ZONE = [(0, 0), (700, 0), (700, 690), (240, 690), (240, 880), (160, 880), (160, 1448), (0, 1448)]
base = clear_bark_dots(orig, PINE_ZONE, lime_specks=False)
_b = base.astype(np.float32)
_L = lum(_b)
_lime = ns["lime_mask"](base)
_dark = _L < 125
_lf = np.asarray(Image.fromarray((_lime * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(6)), np.float32) / 255
_reach = _dark & (_lf < .5)
for _ in range(60):
    _reach = morph(_reach, ImageFilter.MaxFilter(3)) & _dark
_dots = _dark & ~_reach & (_lf > .35)
_dots = morph(_dots, ImageFilter.MaxFilter(5))
_dots &= ~_lime | (_L < 150)
_fill = ns["spread"](base, _lime & ~_dots)
_db = blur(_dots.astype(np.float32), .7)[..., None]
base = np.clip(_b * (1 - _db) + _fill * _db, 0, 255).astype(np.uint8)
BH, BW = base.shape[:2]
pad = np.concatenate([base, np.repeat(base[-1:], 900, 0)], 0)
pad = np.concatenate([pad, np.repeat(pad[:, -1:], 900, 1)], 1)
base_im = Image.fromarray(pad)

# 원본 각 행에서 오른쪽 대각선 먹이 시작하는 x(먹 경계). 소나무 잎이 먹으로 잡히지 않게 이 오른쪽만 대각선 먹으로 본다.
_navy = (base.max(-1) < 78) & (base[..., 2].astype(int) >= base[..., 0].astype(int) - 6)
nedge = np.full(BH, float(BW), np.float32)
for y in range(BH):
    row = _navy[y]
    x = BW - 1
    while x > 6 and row[x - 6:x + 1].mean() > .5:
        x -= 1
    nedge[y] = x if x < BW - 2 else BW
# 먹 경계 바로 안쪽의 원본 먹색(가로 확장의 기준색).
_ink_ref = np.median(base[300:1250][np.arange(BW)[None, :] > nedge[300:1250, None] + 20].reshape(-1, 3), 0).astype(np.float32)
print("ink ref", _ink_ref)

# 2. 아래 그림자 비율: 휴대폰 캡처와 원본의 행별 차이(휴대폰과 같은 계산), 원본 행 기준으로 바꿔 둔다.
ref = np.asarray(Image.open(TOOLS + "/reference_phone.webp").convert("RGB"), np.float32)
warp = np.asarray(Image.fromarray(orig).transform((920, 2000), Image.AFFINE, (1 / S, 0, OX, 0, 1 / S, OY), Image.BICUBIC), np.float32)
alpha_rows = np.zeros(2000, np.float32)
for y in range(1000, 1694):
    b, c = warp[y, :560], ref[y, :560]
    den = b - INK
    ok = np.abs(den) > 40
    alpha_rows[y] = np.clip(np.median(((b - c) / np.where(ok, den, 1))[ok]), 0, 1) if ok.sum() > 60 else alpha_rows[y - 1]
alpha_rows = np.convolve(np.pad(alpha_rows, 25, mode="edge"), np.ones(51) / 51, "valid")
alpha_rows[1694:] = 1
alpha_rows = np.maximum.accumulate(alpha_rows * (np.arange(2000) >= 1080))

LETTER = Image.open(RES + "/drawable/home_hero_brush_lettering.webp").convert("RGBA")
LOGO = Image.open(RES + "/drawable/home_hero_logo_white.webp").convert("RGBA")
_la = np.asarray(LOGO).copy()
_la[..., 3] = np.clip(_la[..., 3].astype(np.float32) * 2.2, 0, 255).astype(np.uint8)
LOGO = Image.fromarray(_la)
FONT = RES + "/font/home_ink_griun.ttf"


def render(name, box, W, H, seed, sim=None, bw=None, ned=None, band=None):
    """sim·bw·ned: 원본 대신 쓸 그림(덧댄 PIL 이미지), 그 그림의 실제 폭, 행별 먹 경계. 없으면 원본을 쓴다."""
    sim = base_im if sim is None else sim
    bw = BW if bw is None else bw
    ned = nedge if ned is None else ned
    bx0, by0, bx1, by1 = box
    k = W / (bx1 - bx0)
    f = k / K_PHONE
    rng = np.random.default_rng(seed)
    img = np.asarray(sim.resize((W, H), Image.LANCZOS, box=box), np.float32)
    ys, xs = np.arange(H), np.arange(W)
    base_y = by0 + (ys + .5) / k
    base_x = bx0 + (xs + .5) / k
    inside = (base_y[:, None] < BH - 1.5) & (base_x[None, :] < bw - 1.5)
    a_out = np.interp((base_y - OY) * S, np.arange(2000), alpha_rows, right=1.0).astype(np.float32)
    a_out[base_y >= BH - 1.5] = 1
    if band is not None:
        # 아래 가로 먹 띠: 원본 행 band[0]~band[1]에서 완전히 먹이 되도록 그림자를 마저 채운다(앱이 이 띠를 버튼 뒤에 둔다).
        t = np.clip((base_y - band[0]) / (band[1] - band[0]), 0, 1)
        a_out = np.maximum(a_out, (t * t * (3 - 2 * t)).astype(np.float32))

    # 원본 오른쪽 끝 밖(가로 화면)은 원본 먹색으로 먼저 채워, 이후 처리에서 원본 먹과 같은 먹으로 다뤄지게 한다.
    out_r = (base_x[None, :] >= bw - 1.5) & (base_y[:, None] < BH - 1.5)
    img[out_r] = _ink_ref

    # 3. 선명화(휴대폰과 같은 값).
    L0 = lum(img)
    L0i = Image.fromarray(np.clip(L0, 0, 255).astype(np.uint8))
    lo = np.asarray(L0i.filter(ImageFilter.MinFilter(3)), np.float32)
    hi = np.asarray(L0i.filter(ImageFilter.MaxFilter(3)), np.float32)
    Lt = toggle(np.repeat(L0[..., None], 3, -1), 3, .35)[..., 0]
    Ls = Lt + (Lt - gblur(Lt, 1.0 * f)) * 1.1 + (Lt - gblur(Lt, 2.6 * f)) * .35
    Ls = np.clip(Ls, lo - 1, hi + 1)
    img = np.clip(img + (Ls - L0)[..., None], 0, 255)
    img = np.clip(toggle(img, 3, .18), 0, 255)

    # 4. 먹선, 5. 먹 번짐.
    L = lum(img)
    D = gblur(L, 2.5 * f) - L
    line = np.clip((D - 9) / 26, 0, 1) * np.clip((140 - L) / 70, 0, 1)
    img = img * (1 - line[..., None] * .4) + np.array([26, 28, 36], np.float32) * line[..., None] * .4
    L = lum(img)
    dark = np.clip((80 - L) / 40, 0, 1)
    halo = np.clip(gblur(dark, 2.2 * f) - dark, 0, 1) * (1 + .6 * fbm(H, W, (40 * f, 13 * f), 3))
    img = img * (1 - halo[..., None] * .22) + np.array([60, 64, 74], np.float32) * halo[..., None] * .22

    # 6. 먹 질감.
    src_ink = base[900:1440, 820:1086].astype(np.float32)
    src_ink = src_ink - gblur(src_ink, 30)
    tile = np.asarray(Image.fromarray(np.clip(src_ink + 128, 0, 255).astype(np.uint8)).resize(
        (int(src_ink.shape[1] * k), int(src_ink.shape[0] * k)), Image.LANCZOS), np.float32) - 128
    tile = np.concatenate([tile, tile[:, ::-1]], 1)
    tile = np.concatenate([tile, tile[::-1]], 0)
    grain = np.tile(tile, (H // tile.shape[0] + 2, W // tile.shape[1] + 2, 1))
    # 거울 타일의 대칭 이음매가 넓은 먹에서 드러나지 않게, 어긋나게 민 사본을 겹친다.
    ty, tx = tile.shape[0] * 3 // 7, tile.shape[1] * 2 // 5
    grain = (grain[:H, :W] + grain[ty:ty + H, tx:tx + W]) * .7
    ang = np.degrees(np.arctan2(1, -.62))
    st = Image.fromarray(((value_noise(H * 2, W * 2, 1, 5) + .5) * 255).astype(np.uint8))
    st = st.resize((max(1, int(W * 2 // (9 * f))), int(H * 2 / f))).resize((W * 2, H * 2), Image.BICUBIC).rotate(ang - 90, Image.BICUBIC)
    streak = np.asarray(st, np.float32)[H // 2:H // 2 + H, W // 2:W // 2 + W] / 255 - .5
    streak = gblur(streak, 1.6 * f)
    streak /= np.abs(streak).max() * 2
    blot = gblur(fbm(H, W, (220 * f, 90 * f, 30 * f), 31), 10 * f)
    blot /= np.abs(blot).max()
    ink_px = INK[None, None, :] * (1 + .16 * blot[..., None] + .10 * streak[..., None]) + grain * .9
    ext_px = ink_px * (_ink_ref / INK)[None, None, :]

    # 7. 먹 영역: 원본 먹 경계 오른쪽(대각선)과 원본 밖.
    ne = np.interp(base_y, np.arange(BH), ned, right=0)
    diag_raw = (base_x[None, :] >= ne[:, None] - 1) & inside
    diag_raw = morph(morph(diag_raw, ImageFilter.MaxFilter(3)), ImageFilter.MinFilter(3))
    diag = (diag_raw | out_r) & (a_out[:, None] < .5)
    ink_f = blur(diag.astype(np.float32), 1.0)
    edge_in = np.clip(ink_f - blur(diag.astype(np.float32), 5 * f), 0, 1)
    # 원본 먹은 원래 결을 반 살리고, 원본 밖은 새 먹 질감만 쓴다(경계 40px에서 섞는다).
    seam = np.clip((base_x - (bw - 1.5)) * k / 40 + .5, 0, 1)[None, :, None]
    diag_src = img * .55 + ink_px * .45
    diag_src = diag_src * (1 - seam) + ext_px * seam
    diag_px = diag_src * (1 - .22 * edge_in[..., None])
    img = img * (1 - ink_f[..., None]) + diag_px * ink_f[..., None]
    spill = np.clip(blur(diag.astype(np.float32), 3.5 * f) - ink_f, 0, 1) * (1 + .8 * fbm(H, W, (30 * f, 9 * f), 41))
    img = img * (1 - spill[..., None] * .3) + np.array([70, 74, 86], np.float32) * spill[..., None] * .3
    mott = fbm(H, W, (160 * f, 55 * f, 18 * f), 21)
    a_map = np.clip(a_out[:, None] * (1 + .15 * mott * (1 - a_out[:, None])), 0, 1)[..., None]
    img = img * (1 - a_map) + ink_px * a_map

    # 8. 한지 결(종이에 붙은 무늬라 그림 배율에 맞춰 키운다).
    fib = Image.new("L", (W, H), 128)
    dr = ImageDraw.Draw(fib)
    for _ in range(int(2600 * W * H / (1080 * 2087) / f / f)):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        th = rng.uniform(0, np.pi)
        n = rng.integers(4, 9)
        seg = rng.uniform(6, 16) * f
        pts = [(x, y)]
        for _ in range(n):
            th += rng.normal(0, .25)
            x += np.cos(th) * seg
            y += np.sin(th) * seg
            pts.append((x, y))
        v = int(128 + (1 if rng.random() < .75 else -1) * rng.uniform(15, 40))
        dr.line(pts, fill=v, width=max(1, int(round(f))))
    fib = gblur(np.asarray(fib, np.float32) - 128, .8 * f) / 40
    fine = gblur(rng.normal(0, 1, (H, W)).astype(np.float32), .8 * f)
    fine /= np.abs(fine).max()
    mot = fbm(H, W, (260 * f, 90 * f, 30 * f), 51)
    L = lum(img)
    light = np.clip((L - 80) / 90, 0, 1)
    tex = 1 + light * (.045 * fib + .015 * fine + .022 * mot) + (1 - light) * (.05 * fib + .012 * fine)
    img = img * tex[..., None]

    # 9. 색.
    L = lum(img)[..., None]
    img = L + (img - L) * .93
    img = img * np.array([1.012, 1.0, .985], np.float32)
    img = np.clip(img, 0, 255).astype(np.uint8)
    Image.fromarray(img).save(f"{OUT}/home_{name}_refined.png")
    Image.fromarray(img).save(f"{OUT}/home_{name}_refined.webp", quality=92, method=6)
    return Image.fromarray(img)


def mock(art, landscape, d=2.0, band_frac=None):
    """앱 배치 규칙(HomeScroll.homeHeroTitle)대로 제목을 얹고, 버튼 글자·화살표는 휴대폰 캡처의 아래 여백(dp)을 따른다.

    band_frac: 그림 아래 가로 먹 띠가 시작하는 높이 비율. 주면 앱(homeArtLift)처럼 띠가 버튼 위 16dp에서 시작하도록
    그림을 올리고, 아래 빈 곳은 띠의 아래 3/4를 이어 붙인다(버튼 위 = 화면 아래 152dp, 휴대폰 캡처 기준).
    """
    W, H = art.size
    wd, hd = W / d, H / d
    if band_frac is not None:
        lift = int(round((band_frac * hd - (hd - 152 - 16)) * d))
        if lift > 0:
            a = np.asarray(art)
            strip = a[int(H * (band_frac + (1 - band_frac) * .25)):]
            rep = np.concatenate([strip] * (lift // len(strip) + 2), 0)[:lift]
            art = Image.fromarray(np.concatenate([a[lift:], rep], 0))
    m = art.convert("RGBA")
    text_c, arrow_c = hd - 120, hd - 52        # 휴대폰 캡처에서 잰 화면 아래로부터의 거리(dp)
    if landscape:
        scale = wd * .23 / 171
        right = wd * .95
        title_h = 101 * scale + 4
        bottom = min(hd * .5 + title_h / 2, text_c - 24 - 12)
    else:
        scale = wd * .52 / 171
        right = wd - 20
        bottom = hd - 174                       # 휴대폰(버튼 위 24dp)보다 12dp 높다(태블릿 36dp).
    lw, lh = 171 * scale * d, 42 * scale * d
    gw, gh = 120 * scale * d, 59 * scale * d
    r, b = right * d, bottom * d
    m.alpha_composite(LETTER.resize((int(lw), int(lh)), Image.LANCZOS), (int(r - lw), int(b - lh)))
    m.alpha_composite(LOGO.resize((int(gw), int(gh)), Image.LANCZOS), (int(r - gw), int(b - lh - 4 * d - gh)))
    layer = Image.new("RGBA", m.size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(layer)
    font = ImageFont.truetype(FONT, int(20 * d * 1.18))
    text = "AI 연주 평가 시작"
    bb = font.getbbox(text)
    dr.text((W / 2 - (bb[0] + bb[2]) / 2, text_c * d - (bb[1] + bb[3]) / 2), text, font=font, fill=(255, 255, 255, 255))
    ax, ay, aw, ah = W / 2, arrow_c * d, 8 * d, 4.5 * d
    dr.line([(ax - aw, ay - ah), (ax, ay + ah), (ax + aw, ay - ah)], fill=(255, 255, 255, 235), width=int(1.6 * d), joint="curve")
    m.alpha_composite(layer)
    return m.convert("RGB")


def build_landscape_base(shift=240, width=1400):
    """태블릿 가로용 원본: 먹(찢긴 가장자리 포함)을 shift px 오른쪽으로 옮기고, 드러난 자리를 새로 그린다.

    - 정자: 높이 띠마다 반복 간격(칸 폭)을 재서 먹 경계 앞 그림을 같은 방향 그대로 이어 붙인다(뒤집지 않는다).
    - 산: 이 화면에 보이지 않는 170px 위쪽 산줄기를 가져와 이어 붙인다(같은 봉우리가 되풀이되지 않게).
      정자는 원본처럼 계속 이어지다가 새 먹 경계에서 잘린다.
    - 마당: 무늬가 없어 경계 안쪽을 축으로 반사해 잇는다(연주자는 지운 원본 사용).
    """
    H0 = BH
    src = base.astype(np.float32)
    lime = ns["lime_mask"](base)
    # 먹 경계: 대각선 구간(행 700~1240)에 맞춘 직선으로 아래쪽(마당 그림자와 먹 띠가 섞이는 곳)을 대신한다.
    rows = np.arange(H0)
    ok = (rows >= 700) & (rows <= 1240) & (nedge < BW)
    fit = np.polyfit(rows[ok], nedge[ok], 1)
    ne = nedge.copy()
    low = rows > 1240
    ne[low] = np.polyval(fit, rows[low]) + (nedge[1240] - np.polyval(fit, 1240))
    # 짙은 처마선을 따라 몇 줄만 왼쪽으로 길게 뻗은 가시는 먹이 아니라 그림이므로, 위아래 줄의 중앙값 기준으로 잘라 낸다.
    _m = np.arange(BW)[None, :] >= ne[:, None]
    _m = morph(morph(_m, ImageFilter.MinFilter(7)), ImageFilter.MaxFilter(7))
    _run = np.cumprod(_m[:, ::-1], axis=1).sum(1)
    ne = np.where(nedge < BW, np.maximum(ne, BW - _run), ne).astype(np.float32)
    X = np.arange(width)[None, :]
    F = X >= (ne[:, None] - 7)                     # 먹 + 바로 왼쪽의 찢긴 한지 테
    G = X >= (ne[:, None] + shift)                 # 옮긴 먹(먹만 옮겨 앞쪽 그림 조각이 따라오지 않게 한다)
    R = F & ~G                                      # 새로 그릴 자리
    out = np.empty((H0, width, 3), np.float32)
    out[:, :BW] = src
    out[:, BW:] = _ink_ref
    # 옮긴 먹: 원래 먹(찢긴 테 포함)을 그대로 옮긴다. 원본 밖은 먹색.
    shifted = np.where((X - shift < BW)[..., None], src[np.arange(H0)[:, None], np.clip(X - shift, 0, BW - 1)], _ink_ref)
    out = np.where(G[..., None], shifted, out)

    # 재료 1: 연주자를 지운 원본(뒤집을 정자 재료).
    pav = src.copy()
    hole = np.zeros((H0, BW), bool)
    hole[1188:1316, 262:374] = True                # 연주자(피리 끝·그림자 포함)
    hy, hx = np.nonzero(hole)
    pav[hy, hx] = src[hy, np.minimum(hx + 95, BW - 1)]   # 연주자 자리는 오른쪽 기단 벽으로 채운다
    yy = np.arange(H0)[:, None]
    # 높이(띠)마다 같은 방향으로 이어 그린다: 먹 경계 바로 앞의 정자·산을 그 띠의 반복 간격(칸 폭)만큼씩 옮겨 붙인다.
    # 뒤집지 않으므로 처마 방향·원근이 원본과 같고, 원본처럼 정자가 계속 이어지다가 새 먹 경계에서 잘린다.
    SEAM = 16
    e = (ne - 7 - SEAM).astype(np.float32)          # 이어 그리기 시작점. 원본과 SEAM px 섞는다.

    def period(r0, r1, lo, hi, w=48):
        """띠의 반복 간격: 경계 바로 앞 w px와 가장 닮은, P px 왼쪽 구간을 찾는다."""
        e0 = int(np.min(e[r0:r1]))
        a = pav[r0:r1, e0 - w:e0].ravel()
        best = (-2.0, lo)
        for P in range(lo, hi + 1):
            if e0 - P - w < 0:
                break
            c = np.corrcoef(a, pav[r0:r1, e0 - P - w:e0 - P].ravel())[0, 1]
            if c > best[0]:
                best = (c, P)
        return best[1], best[0]

    def continue_band(r0, r1, P, sw):
        Y = np.arange(r0, r1)[:, None]
        d = X - e[r0:r1, None]
        k = np.floor(d / P) + 1
        s1 = np.clip((X - k * P).astype(int), 0, BW - 1)
        t = d - (k - 1) * P                            # 이번 이음매에서 지난 거리
        s0 = np.clip((X - (k - 1) * P).astype(int), 0, BW - 1)
        w0 = np.clip(1 - t / sw, 0, 1)[..., None]      # 이음매에서는 앞 조각과 섞는다
        return pav[Y, s1] * (1 - w0) + pav[Y, s0] * w0

    fillc = np.empty_like(out)
    fillc[:] = _ink_ref
    # (행 범위, 간격 탐색 범위, 이음매 폭): 산 / 위 지붕 / 위층 공포·창 / 아래 지붕 / 아래층 공포 / 기둥·문 / 난간·기단
    BANDS = [(0, 698, 150, 320, 40), (698, 807, 70, 220, 16), (807, 907, 70, 220, 16), (907, 973, 70, 220, 16),
             (973, 1053, 70, 220, 16), (1053, 1120, 70, 220, 16), (1120, 1253, 70, 260, 16)]
    for r0, r1, lo, hi, sw in BANDS[1:]:
        P, c = period(max(r0, 470), r1, lo, hi)
        print("band", r0, r1, "period", P, "corr", round(float(c), 3))
        fillc[r0:r1] = continue_band(r0, r1, P, sw)
    # 산(정자 지붕 위): 같은 봉우리가 되풀이되지 않게, 이 화면에 보이지 않는 DY px 위쪽 산줄기를 가져와 잇는다.
    DY, W2, SW2 = 170, 260, 40
    r0, r1 = DY, 698
    Y = np.arange(r0, r1)[:, None]
    e2 = e[r0 - DY:r1 - DY, None]
    P2 = W2 - SW2
    tk = np.mod(X - e[r0:r1, None], P2)
    first = (X - e[r0:r1, None]) < P2
    s_new = np.clip((e2 - W2 + tk).astype(int), 0, BW - 1)
    s_prev = np.clip((e2 - SW2 + tk).astype(int), 0, BW - 1)
    prev = np.where(first[..., None], pav[Y, np.clip(X, 0, BW - 1)], pav[Y - DY, s_prev])
    w_prev = np.clip(1 - tk / SW2, 0, 1)[..., None]
    mt = pav[Y - DY, s_new] * (1 - w_prev) + prev * w_prev
    # 가져온 산 아래쪽이 지붕 용마루와 닿는 몇 줄은 원래 줄을 이어 쓴 위 지붕 띠와 섞는다.
    fillc[r0:r1] = mt
    hb = np.clip((np.arange(r0, r1) - 686) / 12, 0, 1)[:, None, None]
    fillc[r0:r1] = fillc[r0:r1] * (1 - hb) + continue_band(r0, r1, 70, 16) * hb
    # 기단 아래 마당은 먹 경계 바로 안쪽을 축으로 반사해 잇는다(연주자를 지운 원본 사용). 마당은 무늬가 없어 대칭이 드러나지 않는다.
    gx = (2 * (ne[:, None] - 7) - X).astype(int)
    gref = pav[yy, np.clip(gx, 0, BW - 1)]
    gr = np.clip((np.arange(H0) - 1250) / 8, 0, 1)[:, None, None]
    fillc = fillc * (1 - gr) + gref * gr
    # 넣을 자리: 정자·산 높이는 이음 시작점부터(이음매는 위에서 섞었다), 마당은 원래 경계에서 부드럽게.
    R2 = (X >= e[:, None]) & ~G
    rs_g = blur(R.astype(np.float32), 3.0)[..., None]
    rs_g = np.where(R[..., None], 1.0, rs_g * (X < ne[:, None])[..., None])
    rs = np.where((np.arange(H0) >= 1250)[:, None, None], rs_g, R2[..., None].astype(np.float32))
    out = out * (1 - rs) + fillc * rs
    out = np.where(G[..., None], shifted, out)
    # 계단 발치에 남은 작은 먹 얼룩(원래 먹 경계의 조각)은 바로 왼쪽 계단 돌로 덮는다.
    out[1244:1262, 494:512] = out[1244:1262, 474:492]
    out = np.clip(out, 0, 255).astype(np.uint8)
    pad2 = np.concatenate([out, np.repeat(out[-1:], 900, 0)], 0)
    pad2 = np.concatenate([pad2, np.repeat(pad2[:, -1:], 900, 1)], 1)
    ned2 = np.where(nedge < BW, ne + shift, BW + shift).astype(np.float32)
    ned2 = np.minimum(ned2, width)
    return Image.fromarray(pad2), width, ned2


ph_h = 1778 / S                                       # 휴대폰이 보여 준 원본 높이
ph_y0 = OY + 142 / S
tp_w = ph_h / 1.6
tp_x0 = OX + (920 / S - tp_w) / 2
portrait = render("tablet_portrait", (tp_x0, ph_y0, tp_x0 + tp_w, ph_y0 + ph_h), 1600, 2560, 12)
land_im, land_w, land_ne = build_landscape_base()
landscape = render("tablet_landscape", (0, 546, 860 * 1.6, 1406), 2560, 1600, 13, land_im, land_w, land_ne, band=(1300, 1322))
mp = mock(portrait, False)
ml = mock(landscape, True, band_frac=(1311 - 546) / 860)
mp.save(OUT + "/home_tablet_portrait_refined_mock.png")
ml.save(OUT + "/home_tablet_landscape_refined_mock.png")

# 비교 그림: 위 줄은 휴대폰(채택본)·태블릿 세로·같은 모의 화면, 아래 줄은 태블릿 가로와 모의 화면.
phone = Image.open(OUT + "/home_phone_refined.png").convert("RGB")
phone_m = Image.open(OUT + "/home_phone_refined_mock.png").convert("RGB")
lf = ImageFont.truetype(RES + "/font/pretendard_semibold.otf", 30)
th = 1000
items_top = [(phone, "휴대폰 세로 (채택본)"), (phone_m, "휴대폰 모의 화면"), (portrait, "태블릿 세로"), (mp, "태블릿 세로 모의 화면")]
tops = [im.resize((int(im.width * th / im.height), th), Image.LANCZOS) for im, _ in items_top]
lw_ = 1000
bots = [im.resize((lw_, int(im.height * lw_ / im.width)), Image.LANCZOS) for im in (landscape, ml)]
gap, lab = 20, 50
cw = max(sum(t.width for t in tops) + gap * 5, lw_ * 2 + gap * 3)
ch = lab + th + gap + lab + bots[0].height + gap
c = Image.new("RGB", (cw, ch), (245, 242, 235))
d = ImageDraw.Draw(c)
x = gap
for t, (_, n) in zip(tops, items_top):
    d.text((x, 8), n, font=lf, fill=(40, 40, 40))
    c.paste(t, (x, lab))
    x += t.width + gap
y = lab + th + gap
for i, (t, n) in enumerate(zip(bots, ("태블릿 가로", "태블릿 가로 모의 화면"))):
    xx = gap + i * (lw_ + gap)
    d.text((xx, y + 8), n, font=lf, fill=(40, 40, 40))
    c.paste(t, (xx, y + lab))
c.save(OUT + "/compare_tablet.png")
print("done", portrait.size, landscape.size)
