"""사용자가 고른 폰 세로 홈 캡처(reference_phone.webp)와 같은 구도의 그림을 더 선명하고 동양화답게 다시 만든다.

캡처의 그림 부분은 base_home_illustration.webp(1086x1448)를 1.398배로 키워 (77.0, 236.14)부터 보여 준 것과 같다
(정규화 상관 0.99). 그래서 압축된 캡처 대신 원본에서 같은 영역을 잘라 쓰고, 캡처에만 있는 아래쪽 어두운 그림자와
그 아래 먹 띠는 캡처에서 잰 값으로 다시 칠한다. 상태 표시줄, 아래 흰 그러데이션, 제목·버튼 글자는 넣지 않는다.

사용: python refine_phone.py OUTDIR  (먼저 실행)
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

S, OX, OY = 1.398, 77.0029, 236.14     # 캡처 px = (원본 px - O) * S
CAP_TOP, CAP_BOTTOM = 142, 1920         # 캡처에서 그림이 보이는 세로 범위(상태 표시줄 아래 ~ 흰 그러데이션 끝)
W = 1080
K = W / 920                              # 캡처 px -> 결과 px
H = int(round((CAP_BOTTOM - CAP_TOP) * K))
INK = np.array([24, 30, 45], np.float32)  # 캡처 아래 먹 띠의 색
rng = np.random.default_rng(11)


def lum(a):
    return a[..., 0] * .299 + a[..., 1] * .587 + a[..., 2] * .114


def gblur(a, r):
    """float 배열(H,W) 또는 (H,W,3)을 값 범위 손실 없이 가우스 블러한다."""
    if a.ndim == 3:
        return np.stack([gblur(a[..., c], r) for c in range(a.shape[2])], -1)
    # 같은 폭의 상자 블러 세 번으로 가우스 블러를 근사한다(scipy 없이).
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


# 1. 원본에서 소나무 가지의 검은 점을 껍질 비늘로 바꾼다(지붕·단청은 영역 밖).
base = np.asarray(Image.open(TOOLS + "/base_home_illustration.webp").convert("RGB"))
PINE_ZONE = [(0, 0), (700, 0), (700, 690), (240, 690), (240, 880), (160, 880), (160, 1448), (0, 1448)]
base = clear_bark_dots(base, PINE_ZONE, lime_specks=False)
# 연두 바탕에 떠 있는 검은 점: 잎·가지 덩어리 안쪽에서 짙은 픽셀을 따라 번져 닿지 않는, 연두로 둘러싸인 작은 조각.
_b = base.astype(np.float32)
_L = lum(_b)
_lime = ns["lime_mask"](base)
_dark = _L < 125
_lf = np.asarray(Image.fromarray((_lime * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(6)), np.float32) / 255
_reach = _dark & (_lf < .5)
for _ in range(60):
    _reach = (np.asarray(Image.fromarray((_reach * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))) > 127) & _dark
_dots = _dark & ~_reach & (_lf > .35)
_dots = np.asarray(Image.fromarray((_dots * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))) > 127
_dots &= ~_lime | (_L < 150)
_fill = ns["spread"](base, _lime & ~_dots)
_db = blur(_dots.astype(np.float32), .7)[..., None]
base = np.clip(_b * (1 - _db) + _fill * _db, 0, 255).astype(np.uint8)
print("lime dots", int(_dots.sum()))
BH = base.shape[0]
# 원본 아래로 넘어가는 행은 마지막 행을 늘여 두고(나중에 먹으로 덮는다) 잘라 키운다.
base_im = Image.fromarray(np.concatenate([base, np.repeat(base[-1:], 400, 0)], 0))

# 2. 캡처가 보여 준 영역을 결과 크기로 잘라 키운다.
x0, y0 = OX, OY + CAP_TOP / S
x1, y1 = OX + 920 / S, OY + CAP_BOTTOM / S
img = np.asarray(base_im.resize((W, H), Image.LANCZOS, box=(x0, y0, x1, y1)), np.float32)
ys_out = np.arange(H)
cap_y = ys_out / K + CAP_TOP                   # 결과 행 -> 캡처 행
base_y = OY + cap_y / S                        # 결과 행 -> 원본 행
inside = base_y < BH - 1.5                     # 원본 그림이 있는 행

# 3. 선명하게: 토글 대비(가까운 극값 쪽으로 당김)로 뭉개진 윤곽을 세우고, 두 단계 언샤프로 붓선을 살린다.
def toggle(a, k, mix):
    out = np.empty_like(a)
    for c in range(3):
        ch = Image.fromarray(np.clip(a[..., c], 0, 255).astype(np.uint8))
        mn = np.asarray(ch.filter(ImageFilter.MinFilter(k)), np.float32)
        mx = np.asarray(ch.filter(ImageFilter.MaxFilter(k)), np.float32)
        v = a[..., c]
        out[..., c] = np.where(v - mn < mx - v, mn, mx)
    return a * (1 - mix) + out * mix


# 밝기에만 적용하고, 주변 3x3의 최솟값·최댓값을 넘지 않게 묶어 윤곽에 밝은 테(헤일로)가 생기지 않게 한다.
L0 = lum(img)
L0i = Image.fromarray(np.clip(L0, 0, 255).astype(np.uint8))
lo = np.asarray(L0i.filter(ImageFilter.MinFilter(3)), np.float32)
hi = np.asarray(L0i.filter(ImageFilter.MaxFilter(3)), np.float32)
Lt = toggle(np.repeat(L0[..., None], 3, -1), 3, .35)[..., 0]
Ls = Lt + (Lt - gblur(Lt, 1.0)) * 1.1 + (Lt - gblur(Lt, 2.6)) * .35
Ls = np.clip(Ls, lo - 1, hi + 1)
img = np.clip(img + (Ls - L0)[..., None], 0, 255)
# 색 경계도 조금 세운다(같은 방식으로 묶음).
img = np.clip(toggle(img, 3, .18), 0, 255)

L = lum(img)
# 4. 먹선: 주변보다 어두운 가는 선(솔잎·지붕선·윤곽)을 짙은 먹색으로 눌러 붓선처럼 또렷하게 한다.
D = gblur(L, 2.5) - L
line = np.clip((D - 9) / 26, 0, 1) * np.clip((140 - L) / 70, 0, 1)
img = img * (1 - line[..., None] * .4) + np.array([26, 28, 36], np.float32) * line[..., None] * .4

# 5. 먹 번짐: 짙은 먹 둘레의 종이에 아주 옅은 먹물이 스민 듯한 테를 준다.
L = lum(img)
dark = np.clip((80 - L) / 40, 0, 1)
halo = np.clip(gblur(dark, 2.2) - dark, 0, 1) * (1 + .6 * fbm(H, W, (40, 13), 3))
img = img * (1 - halo[..., None] * .22) + np.array([60, 64, 74], np.float32) * halo[..., None] * .22

# 6. 아래 그림자: 캡처는 연주자 아래로 갈수록 먹색이 덮인다. 행마다 캡처와 원본의 차이로 덮인 비율을 재서 그대로 칠한다.
ref = np.asarray(Image.open(TOOLS + "/reference_phone.webp").convert("RGB"), np.float32)
warp = np.asarray(Image.fromarray(np.asarray(Image.open(TOOLS + "/base_home_illustration.webp").convert("RGB"))).transform(
    (920, 2000), Image.AFFINE, (1 / S, 0, OX, 0, 1 / S, OY), Image.BICUBIC), np.float32)
alpha_rows = np.zeros(2000, np.float32)
for y in range(1000, 1694):
    b, c = warp[y, :560], ref[y, :560]
    den = b - INK
    ok = np.abs(den) > 40
    alpha_rows[y] = np.clip(np.median(((b - c) / np.where(ok, den, 1))[ok]), 0, 1) if ok.sum() > 60 else alpha_rows[y - 1]
alpha_rows = np.convolve(np.pad(alpha_rows, 25, mode="edge"), np.ones(51) / 51, "valid")
alpha_rows[1694:] = 1
alpha_rows = np.maximum.accumulate(alpha_rows * (np.arange(2000) >= 1080))
a_out = np.interp(cap_y, np.arange(2000), alpha_rows).astype(np.float32)

# 7. 먹 영역(오른쪽 대각선 + 아래 띠)을 찾는다. 원본에 없는 아래 행은 먹으로 채운다.
L = lum(img)
navy_raw = (L < 62) & (img[..., 2] >= img[..., 0] - 4)
navy_raw[~inside] = True
nm = Image.fromarray((navy_raw * 255).astype(np.uint8))
for sx, sy in ((W - 3, H - 3), (W - 3, int(H * .55)), (W - 3, int(H * .4)), (W // 2, H - 3)):
    if nm.getpixel((sx, sy)) == 255:
        ImageDraw.floodfill(nm, (sx, sy), 128)
ink_region = np.asarray(nm) == 128
ink_region = np.asarray(Image.fromarray((ink_region * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))) > 127
# 소나무·바위의 짙은 잎이 먹 띠에 붙어 함께 잡히지 않게, 원본 그림 안의 왼쪽 위 영역은 대각선 먹만 남긴다.
xx = np.arange(W)[None, :]
diag_x = np.interp(ys_out, [0, H * .35, H * .62, H], [W * .93, W * .93, W * .7, 0])[:, None]
ink_region &= (xx > diag_x) | (a_out[:, None] > .97)


# 먹 질감: 원본 먹의 결을 고역만 떼어 타일로 깔고, 큰 먹 얼룩과 대각선 방향의 마른 붓결을 더한다.
src_ink = base[900:1440, 820:1086].astype(np.float32)
src_ink = src_ink - gblur(src_ink, 30)
tile = np.asarray(Image.fromarray(np.clip(src_ink + 128, 0, 255).astype(np.uint8)).resize(
    (int(src_ink.shape[1] * S * K), int(src_ink.shape[0] * S * K)), Image.LANCZOS), np.float32) - 128
tile = np.concatenate([tile, tile[:, ::-1]], 1)
tile = np.concatenate([tile, tile[::-1]], 0)
grain = np.tile(tile, (H // tile.shape[0] + 1, W // tile.shape[1] + 1, 1))[:H, :W]
ang = np.degrees(np.arctan2(1, -.62))
streak = np.asarray(Image.fromarray(((value_noise(H * 2, W * 2, 1, 5)[:, :] + .5) * 255).astype(np.uint8)).resize(
    (W * 2 // 9, H * 2)).resize((W * 2, H * 2), Image.BICUBIC).rotate(ang - 90, Image.BICUBIC), np.float32)
streak = (streak[H // 2:H // 2 + H, W // 2:W // 2 + W] / 255 - .5)
streak = gblur(streak, 1.6)
streak /= np.abs(streak).max() * 2
blot = gblur(fbm(H, W, (220, 90, 30), 31), 10)
blot /= np.abs(blot).max()
ink_px = INK[None, None, :] * (1 + .16 * blot[..., None] + .10 * streak[..., None]) + grain * .9
# 대각선 먹: 원래 결을 살리고 새 질감을 반만 더한다. 가장자리 안쪽은 먹이 고여 조금 짙게, 바깥은 종이에 스민 옅은 먹테.
diag = ink_region & inside[:, None] & (a_out[:, None] < .5)
ink_f = blur(diag.astype(np.float32), 1.0)
edge_in = np.clip(ink_f - blur(diag.astype(np.float32), 5), 0, 1)
diag_px = (img * .55 + ink_px * .45) * (1 - .22 * edge_in[..., None])
img = img * (1 - ink_f[..., None]) + diag_px * ink_f[..., None]
spill = np.clip(blur(diag.astype(np.float32), 3.5) - ink_f, 0, 1) * (1 + .8 * fbm(H, W, (30, 9), 41))
img = img * (1 - spill[..., None] * .3) + np.array([70, 74, 86], np.float32) * spill[..., None] * .3
# 아래 그림자와 먹 띠: 캡처에서 잰 행별 비율로 부드럽게 덮는다. 덮는 정도에 먹 얼룩을 조금 섞고, 덮는 색에도 먹 결을 준다.
mott = fbm(H, W, (160, 55, 18), 21)
a_map = np.clip(a_out[:, None] * (1 + .15 * mott * (1 - a_out[:, None])), 0, 1)[..., None]
img = img * (1 - a_map) + ink_px * a_map

# 8. 한지 결: 밝은 종이(연두·한지)에는 섬유와 잔 얼룩을, 먹 위에는 옅은 섬유만 남긴다.
fib = Image.new("L", (W, H), 128)
dr = ImageDraw.Draw(fib)
for _ in range(2600):
    x, y = rng.uniform(0, W), rng.uniform(0, H)
    th = rng.uniform(0, np.pi)
    n = rng.integers(4, 9)
    seg = rng.uniform(6, 16)
    pts = [(x, y)]
    for _ in range(n):
        th += rng.normal(0, .25)
        x += np.cos(th) * seg
        y += np.sin(th) * seg
        pts.append((x, y))
    # 한지 섬유는 바탕보다 조금 밝은 가닥이 대부분이고 어두운 가닥은 드물다.
    v = int(128 + (1 if rng.random() < .75 else -1) * rng.uniform(15, 40))
    dr.line(pts, fill=v, width=1)
fib = gblur(np.asarray(fib, np.float32) - 128, .8) / 40
fine = gblur(rng.normal(0, 1, (H, W)).astype(np.float32), .8)
fine /= np.abs(fine).max()
mot = fbm(H, W, (260, 90, 30), 51)
L = lum(img)
light = np.clip((L - 80) / 90, 0, 1)
tex = 1 + light * (.045 * fib + .015 * fine + .022 * mot) + (1 - light) * (.05 * fib + .012 * fine)
img = img * tex[..., None]

# 9. 색: 형광에 가까운 채도만 살짝 눌러 석채 물감 느낌을 주고, 한지 쪽을 조금 따뜻하게 한다.
L = lum(img)[..., None]
sat = img - L
img = L + sat * .93
img = img * np.array([1.012, 1.0, .985], np.float32)
img = np.clip(img, 0, 255).astype(np.uint8)
Image.fromarray(img).save(OUT + "/home_phone_refined.png")
Image.fromarray(img).save(OUT + "/home_phone_refined.webp", quality=92, method=6)

# 10. 확인용 모의 화면: 앱이 얹는 제목·버튼 글자·화살표를 캡처와 같은 자리에 놓는다.
mock = Image.fromarray(img).convert("RGBA")


def cbox(x0, y0, x1, y1):
    return (x0 * K, (y0 - CAP_TOP) * K, x1 * K, (y1 - CAP_TOP) * K)


def place(path, box, alpha_gain=1.0):
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im)[..., 3]
    ys, xs = np.nonzero(a > 40)
    im = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    if alpha_gain != 1:
        arr = np.asarray(im).copy()
        arr[..., 3] = np.clip(arr[..., 3].astype(np.float32) * alpha_gain, 0, 255).astype(np.uint8)
        im = Image.fromarray(arr)
    bx0, by0, bx1, by1 = box
    im = im.resize((int(round(bx1 - bx0)), int(round(by1 - by0))), Image.LANCZOS)
    mock.alpha_composite(im, (int(round(bx0)), int(round(by0))))


place(RES + "/drawable/home_hero_logo_white.webp", cbox(590, 1293, 872, 1430), 2.2)
place(RES + "/drawable/home_hero_brush_lettering.webp", cbox(369, 1443, 864, 1557))
font_path = RES + "/font/home_ink_griun.ttf"
text = "AI 연주 평가 시작"
tx0, ty0, tx1, ty1 = cbox(293, 1625, 627, 1676)
size = 40
while True:
    f = ImageFont.truetype(font_path, size)
    bb = f.getbbox(text)
    if bb[2] - bb[0] >= tx1 - tx0 or size > 200:
        break
    size += 1
layer = Image.new("RGBA", mock.size, (0, 0, 0, 0))
d = ImageDraw.Draw(layer)
d.text((tx0 - bb[0], ty0 - bb[1] + ((ty1 - ty0) - (bb[3] - bb[1])) / 2), text, font=f, fill=(255, 255, 255, 255))
ax0, ay0, ax1, ay1 = cbox(442, 1793, 478, 1814)
d.line([(ax0, ay0), ((ax0 + ax1) / 2, ay1), (ax1, ay0)], fill=(255, 255, 255, 235), width=max(2, int(3.2 * K)), joint="curve")
mock.alpha_composite(layer)
mock.convert("RGB").save(OUT + "/home_phone_refined_mock.png")

# 11. 비교 그림: 캡처(상태 표시줄 아래) | 개선한 그림 | 글자를 얹은 모의 화면, 그리고 부분 확대.
cap = Image.open(TOOLS + "/reference_phone.webp").convert("RGB").crop((0, CAP_TOP, 920, CAP_BOTTOM)).resize((W, H), Image.LANCZOS)
gap = 24
lab_h = 70
row = Image.new("RGB", (W * 3 + gap * 4, H + lab_h + gap), (245, 242, 235))
dl = ImageDraw.Draw(row)
lf = ImageFont.truetype(RES + "/font/eolssu_sans_kr_semibold.otf", 40)
for i, (im, name) in enumerate(((cap, "캡처 (그대로 키움)"), (Image.fromarray(img), "개선한 그림 (글자 없음)"), (mock.convert("RGB"), "글자를 얹은 모의 화면"))):
    x = gap + i * (W + gap)
    row.paste(im, (x, lab_h))
    dl.text((x, 14), name, font=lf, fill=(40, 40, 40))
row.save(OUT + "/compare_full.png")
row.resize((row.width // 2, row.height // 2), Image.LANCZOS).save(OUT + "/compare_full_small.png")

zooms = [((20, 160, 420, 560), "소나무"), ((380, 640, 780, 1040), "정자 지붕·단청"), ((260, 1150, 660, 1550), "연주자·아래 그림자")]
zs = 560
zim = Image.new("RGB", (gap * 3 + zs * 2, len(zooms) * (zs + lab_h) + gap), (245, 242, 235))
dz = ImageDraw.Draw(zim)
for j, ((a, b, c, e), name) in enumerate(zooms):
    box = (a * K, (b - CAP_TOP) * K, c * K, (e - CAP_TOP) * K)
    yv = gap + j * (zs + lab_h)
    dz.text((gap, yv), name + "  (왼쪽 캡처 · 오른쪽 개선)", font=lf, fill=(40, 40, 40))
    zim.paste(cap.crop(box).resize((zs, zs), Image.LANCZOS), (gap, yv + lab_h - 10))
    zim.paste(Image.fromarray(img).crop(box).resize((zs, zs), Image.LANCZOS), (gap * 2 + zs, yv + lab_h - 10))
zim.save(OUT + "/compare_zoom.png")
print("size", W, H)
