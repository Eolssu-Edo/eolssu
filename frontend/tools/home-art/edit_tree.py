"""받은 그림에서 소나무를 왼쪽으로 옮기고 연두 종이를 넓힌다(S15P31E102-1034).

- 소나무 층: 연두 종이·한지가 아닌 어두운 잎과 갈색 줄기 중 줄기에 이어진 덩어리.
- 위쪽(잎이 무성한 높이)일수록 많이, 지붕·바위 높이에서는 0으로 줄여 옮긴다. 줄기 아래와 지붕 위 가지는 그대로라 끊기지 않는다.
- 소나무가 비킨 자리: 연두 경계 안쪽은 연두 종이 질감, 바깥은 주변 색을 번져 채운다.
- 연두 경계(찢어진 가장자리와 그림자)는 행마다 오른쪽으로 옮겨 연두를 넓힌다.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter


def blur(m, r):
    return np.asarray(Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255


def lime_mask(a):
    return (a[..., 1] - a[..., 2] > 75) & (a[..., 1] > 120) & (a[..., 0] > 120)


def smooth_curve(v, k):
    p = np.pad(v.astype(np.float64), k, mode="edge")
    return np.convolve(p, np.ones(2 * k + 1) / (2 * k + 1), "valid")


def ramp(h, y0, y1):
    """y0 위로 1, y1 아래로 0인 부드러운 계단."""
    y = np.arange(h, dtype=np.float64)
    t = np.clip((y1 - y) / max(1.0, y1 - y0), 0, 1)
    return t * t * (3 - 2 * t)


def diffuse_fill(a, hole, iters=60):
    """hole 픽셀을 주변 색으로 번져 채운다(numpy만 쓰는 간단한 채우기)."""
    out = a.astype(np.float32).copy()
    known = (~hole).astype(np.float32)
    out[hole] = 0
    acc = out * known[..., None]
    w = known.copy()
    img = Image.fromarray
    for r in (2, 4, 8, 16, 32):
        num = np.stack([blur(acc[..., c] / 255, r) for c in range(3)], -1) * 255
        den = blur(w, r)[..., None]
        est = num / np.maximum(den, 1e-4)
        fill = hole & (den[..., 0] > .02)
        out[fill] = est[fill]
    return out


def noise1(n, scale, seed):
    rng = np.random.default_rng(seed)
    kn = rng.uniform(-1, 1, int(n / scale) + 4)
    x = np.arange(n) / scale
    i0 = x.astype(int)
    t = x - i0
    t = t * t * (3 - 2 * t)
    return kn[i0] * (1 - t) + kn[i0 + 1] * t


def edit(a, shift_px, y0, y1, widen_px, reach, stretch=110, shadow=12, seed=3, protect=None):
    """a: HxWx3 uint8. y0 위로는 연두 경계를 widen_px만큼 오른쪽으로 옮기고 소나무를 shift_px만큼 왼쪽으로 옮긴다. y0~y1에서 0으로 줄어든다.

    소나무는 새 연두 경계 T보다 stretch px 안쪽까지만 다 옮기고 T에서 0이 되게 가로로 늘여, 가지가 끊기지 않고
    옮겨서 드러나는 자리는 모두 연두 안에 들어간다. 경계는 찢어진 굴곡과 연두 쪽 그림자를 새로 그린다.
    """
    a = a.astype(np.int32)
    h, w, _ = a.shape
    lm = lime_mask(a)
    r = ramp(h, y0, y1)
    ymax = int(np.where(r > 1e-3)[0].max()) + 1
    raw = np.zeros(h)
    for y in range(ymax):
        xs = np.where(lm[y])[0]
        raw[y] = xs.max() if len(xs) > 10 else 0
    # 소나무가 경계를 가린 행은 경계가 작게 잡히므로 위아래 최댓값으로 감싼 뒤 부드럽게 한다.
    k = 25
    pad = np.pad(raw[:ymax], k, mode="edge")
    env = np.array([pad[i:i + 2 * k + 1].max() for i in range(ymax)])
    edge = np.zeros(h)
    edge[:ymax] = smooth_curve(env, 12)
    T = edge + widen_px * r + noise1(h, 40, seed) * 4 + noise1(h, 9, seed + 1) * 1.5
    xx = np.arange(w)[None, :]
    lum = a @ np.array([.299, .587, .114])
    dark = a.max(-1) < 105
    # 갈색 줄기는 빨강이 초록보다 높다. 연두 경계의 올리브색 그림자(초록 >= 빨강)는 소나무로 보지 않는다.
    brown = ((a[..., 0] - a[..., 2]) > 48) & (a[..., 0] > a[..., 1] + 8) & (lum > 60) & (lum < 205) & ~lm
    prot = np.zeros((h, w), bool)
    if protect:
        pm = Image.new('L', (w, h), 0)
        ImageDraw.Draw(pm).polygon(protect, fill=255)
        prot = np.asarray(pm) > 0
    cand = (dark | brown) & (xx < edge[:, None] + reach) & ~prot
    cand[ymax:] = False
    im = Image.fromarray((cand * 255).astype(np.uint8)).copy()
    im.load()
    for y in range(0, ymax, 5):
        for x in range(0, 30, 3):
            if cand[y, x] and im.getpixel((x, y)) == 255:
                ImageDraw.floodfill(im, (x, y), 128)
    pine = np.array(im) == 128
    pine = blur(pine.astype(np.float32), 1.2) > .35
    pine &= blur(pine.astype(np.float32), 4) > .12  # 떨어진 잎 조각(점)은 뺀다
    pa = blur(pine.astype(np.float32), .8)
    tex = lime_texture(a, lm)
    # 남길 구름: 원래 연두 안쪽(경계에서 24px 이상 안)의 밝은 픽셀 중 큰 덩어리.
    bright = (~lm) & (lum > 205) & (xx < raw[:, None] - 24)
    bright[ymax:] = False
    cloud_keep = blur(blur(bright.astype(np.float32), 2) > .5, 3) > .5
    # 큰 구름 덩어리만 남긴다(넓이 기준).
    ck = blur(cloud_keep.astype(np.float32), 10) > .35
    cloud_keep &= ck
    cloud_keep &= bright | (blur(bright.astype(np.float32), 1.5) > .3)
    res = a.astype(np.float32).copy()
    ys = np.arange(w, dtype=np.float64)
    for y in range(ymax):
        t = T[y]
        # 바탕: 새 경계 안쪽은 연두 종이. 원래 연두 안쪽의 구름(밝은 비연두)은 남긴다.
        inside = ys < t
        cloud = cloud_keep[y]
        # 보호 영역에서는 하늘(한지) 픽셀만 연두로 덮어, 연두가 지붕 윤곽을 따라 멈추게 한다.
        sky = (lum[y] > 200) & (a[y, :, 0] > a[y, :, 2] + 40)
        sel = inside & ~cloud & ~(prot[y] & ~sky)
        res[y, sel] = tex[y, sel]
        # 연두 쪽 그림자: 경계에서 shadow px 안쪽으로 옅어진다.
        sx = np.arange(max(0, int(t) - shadow), min(w, int(t) + 1))
        f = np.clip((t - sx) / shadow, 0, 1)
        dk = (1 - .38 * (1 - f) ** 1.6)[:, None]
        keep = ~(prot[y, sx] & ~sky[sx])
        sx = sx[keep]
        dk = dk[keep]
        f = f[keep]
        res[y, sx] = res[y, sx] * dk * np.array([.92, .96, .8])[None, :] ** (1 - f)[:, None]
        # 소나무: 늘여 옮긴 층을 얹는다.
        d = shift_px * r[y]
        g = np.clip((t - ys) / stretch, 0, 1)
        xmap = ys - d * g
        src = np.interp(ys, xmap, ys)
        si = np.clip(np.round(src).astype(int), 0, w - 1)
        al = pa[y, si]
        m = (al > .01) & (ys < t + 2)
        res[y, m] = res[y, m] * (1 - al[m, None]) + a[y, si[m]] * al[m, None]
    # 아래 바뀌지 않는 행과 부드럽게 잇는다.
    bl = np.clip(r * 4, 0, 1)[:, None, None]
    out = a * (1 - bl) + res * bl
    return np.clip(out, 0, 255).astype(np.uint8), pine


def lime_texture(a, lm):
    """연두 종이만 있는 가장 큰 칸을 찾아 타일로 깐다."""
    h, w, _ = a.shape
    best = None
    for size in (96, 72, 56, 40, 28):
        for y in range(0, h - size, 8):
            for x in range(0, w - size, 8):
                if lm[y:y + size, x:x + size].mean() > .995:
                    best = a[y:y + size, x:x + size]
                    break
            if best is not None:
                break
        if best is not None:
            break
    # 칸 안의 작은 얼룩(잎 끝·티끌)이 타일 간격으로 되풀이되지 않게, 평균에서 크게 벗어난 픽셀은 평균 쪽으로 누른다.
    best = best.astype(np.float32)
    mean = best.reshape(-1, 3).mean(0)
    dev = np.abs(best - mean).max(-1, keepdims=True)
    best = np.where(dev > 18, mean + (best - mean) * 18 / np.maximum(dev, 1e-3), best)
    t = np.concatenate([best, best[:, ::-1]], 1)
    t = np.concatenate([t, t[::-1]], 0)
    return np.tile(t, (h // t.shape[0] + 1, w // t.shape[1] + 1, 1))[:h, :w].astype(np.float32)
