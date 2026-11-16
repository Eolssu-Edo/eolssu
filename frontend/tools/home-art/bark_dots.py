"""소나무 가지 윤곽에 줄지어 박힌 검은 점을 껍질 비늘로 바꾸는 함수(S15P31E102-1040에서 옮김, S15P31E102-1049).

refine_phone.py·refine_tablet.py가 쓴다. blur·lime_mask는 edit_tree.py의 것을 그대로 쓴다.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from edit_tree import blur, lime_mask


def morph(m, f):
    return np.asarray(Image.fromarray(m.astype(np.uint8) * 255).filter(f)) > 127


def spread(a, known):
    """known 픽셀 색만 번져, 모든 픽셀에 가까운 known 색의 추정값을 준다."""
    acc = a.astype(np.float32) * known[..., None]
    out = np.zeros(a.shape, np.float32)
    done = np.zeros(known.shape, bool)
    for r in (1.5, 3, 6, 12, 24):
        num = np.stack([blur(acc[..., c] / 255, r) for c in range(3)], -1) * 255
        den = blur(known.astype(np.float32), r)[..., None]
        ok = (den[..., 0] > .03) & ~done
        out[ok] = (num / np.maximum(den, 1e-4))[ok]
        done |= ok
    return out


def small_parts(mask, max_area):
    """mask의 이어진 조각 중 넓이가 max_area px 이하인 것만 남긴다."""
    im = Image.fromarray(mask.astype(np.uint8) * 255)
    keep = np.zeros(mask.shape, bool)
    ys, xs = np.nonzero(mask)
    for y, x in zip(ys, xs):
        if im.getpixel((int(x), int(y))) != 255:
            continue
        ImageDraw.floodfill(im, (int(x), int(y)), 128)
        part = np.asarray(im) == 128
        if part.sum() <= max_area:
            keep |= part
        im.paste(64, mask=Image.fromarray(part.astype(np.uint8) * 255))
    return keep


def clear_bark_dots(a, area, lime_specks=True):
    """소나무 가지 윤곽에 줄지어 박힌 검은 점을 껍질 비늘로 바꾸고 연두 바탕의 작은 얼룩을 지운다.

    area: 소나무가 있는 다각형. 정자의 단청·기와처럼 점과 색이 비슷한 곳은 이 밖에 둔다.
    lime_specks: 연두 바탕의 얼룩도 지울지. 디자이너 구름이 연두 안에 작게 남은 그림에서는 구름이 함께 지워지므로 끈다.
    점은 빨강이 파랑 이상인 지름 5px 이상의 어두운 덩어리이고 밝은 껍질 가까이에 있다. 솔잎(청록)과 먹(남색)은 파랑이 높아 남는다.
    점은 속을 짙은 껍질색으로 칠해 테두리만 남은 비늘 무늬로 바꾸므로 가지 윤곽이 끊기지 않는다.
    """
    a = a.astype(np.int32)
    h, w, _ = a.shape
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    zone = Image.new('L', (w, h), 0)
    ImageDraw.Draw(zone).polygon(area, fill=255)
    zone = np.asarray(zone) > 0
    lm = lime_mask(a)
    dark = (a.max(-1) < 90) & (r >= b + 3)
    blobs = morph(morph(dark, ImageFilter.MinFilter(5)), ImageFilter.MaxFilter(5))
    bark = (r > 110) & (r < 215) & (r - b > 35) & (r - g > 4) & (r - g < 38) & ~lm
    bark = morph(morph(bark, ImageFilter.MinFilter(3)), ImageFilter.MaxFilter(3))
    dots = blobs & morph(bark, ImageFilter.MaxFilter(11)) & zone
    # 점은 지우지 않고 속을 짙은 껍질색으로 칠해, 얇은 테두리만 남은 껍질 비늘(거북등)로 바꾼다. 가지 윤곽은 끊기지 않는다.
    blob = morph(dots, ImageFilter.MaxFilter(3)) & (a.max(-1) < 120)
    core = morph(blob, ImageFilter.MinFilter(3))
    scale = spread(a, bark & ~blob) * np.array([.78, .7, .66], np.float32)
    cb = blur(core.astype(np.float32), .8)[..., None] * .9
    out = a.astype(np.float32) * (1 - cb) + scale * cb
    if not lime_specks:
        return np.clip(out, 0, 255).astype(np.uint8)
    # 연두 바탕의 얼룩: 주변(중앙값)보다 눈에 띄게 어둡거나 밝은 연두 픽셀과, 연두로 둘러싸인 작은(400px 이하) 밝은 비연두 조각(옮기고 남은 구름 부스러기)을 고른다.
    # 큰 조각과 가지 가까이(어두운 픽셀 6px 이내)는 건드리지 않는다.
    near_tree = morph(a.max(-1) < 90, ImageFilter.MaxFilter(13))
    med = np.asarray(Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).filter(ImageFilter.MedianFilter(15))).astype(np.float32)
    lum = out @ np.array([.299, .587, .114], np.float32)
    mlum = med @ np.array([.299, .587, .114], np.float32)
    closed = morph(morph(lm, ImageFilter.MaxFilter(21)), ImageFilter.MinFilter(21))
    speck = ((lm & (np.abs(lum - mlum) > 4)) | small_parts(closed & ~lm & zone & (lum > 190), 400)) & zone & ~near_tree
    speck = morph(speck, ImageFilter.MaxFilter(3)) & closed & ~near_tree
    sb = blur(speck.astype(np.float32), .8)[..., None]
    out = out * (1 - sb) + spread(np.clip(out, 0, 255), lm & ~speck) * sb
    return np.clip(out, 0, 255).astype(np.uint8)
