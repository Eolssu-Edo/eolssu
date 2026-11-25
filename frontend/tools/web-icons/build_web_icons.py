"""웹 탭 아이콘·홈 화면 아이콘을 iOS 앱 아이콘(1024×1024, 흰 바탕)에서 만든다.

- favicon.ico(16·32·48): 작은 크기에서도 붓글씨가 보이게 먹 경계에 맞춰 여백을 줄여 자른다.
- apple-touch-icon.png(180): 원본을 그대로 줄인다. iOS가 모서리를 둥글게 깎는다.
- icon-192.png·icon-512.png: manifest의 purpose "any".
- icon-maskable-512.png: manifest의 purpose "maskable". Android가 원·물방울 모양으로 잘라도
  붓글씨가 남도록, 먹 픽셀이 모두 안전 원(반지름 40%) 안에 들어가게 흰 캔버스에 줄여 넣는다.

사용법(frontend/에서 실행):
    python -m pip install --user pillow==13.0.0
    python -P tools/web-icons/build_web_icons.py
"""

import math
from pathlib import Path

from PIL import Image, ImageChops

FRONTEND = Path(__file__).resolve().parents[2]
SOURCE = FRONTEND / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
OUT_DIR = FRONTEND / "webApp/src/webMain/resources"

BACKGROUND = (255, 255, 255)
INK_THRESHOLD = 24  # 흰 바탕과 채널 차이가 이보다 크면 먹(로고)으로 본다.
FAVICON_MARGIN = 0.04  # 먹 경계 바깥 여백(한 변 대비)
SAFE_RADIUS = 0.40  # maskable 안전 원 반지름(한 변 대비, W3C Web App Manifest)


def ink_mask(image: Image.Image) -> Image.Image:
    diff = ImageChops.difference(image, Image.new("RGB", image.size, BACKGROUND)).convert("L")
    return diff.point(lambda v: 255 if v > INK_THRESHOLD else 0)


def resized(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.LANCZOS)


def favicon_crop(image: Image.Image, mask: Image.Image) -> Image.Image:
    left, top, right, bottom = mask.getbbox()
    side = max(right - left, bottom - top) * (1 + 2 * FAVICON_MARGIN)
    cx, cy = (left + right) / 2, (top + bottom) / 2
    box = [round(cx - side / 2), round(cy - side / 2), round(cx + side / 2), round(cy + side / 2)]
    canvas = Image.new("RGB", (box[2] - box[0], box[3] - box[1]), BACKGROUND)
    canvas.paste(image.crop(box), (0, 0))
    return canvas


def maskable(image: Image.Image, mask: Image.Image, size: int) -> tuple[Image.Image, float]:
    width, height = image.size
    cx, cy = width / 2, height / 2
    pixels = mask.load()
    farthest = max(
        math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        for y in range(height)
        for x in range(width)
        if pixels[x, y]
    )
    scale = min(1.0, SAFE_RADIUS * width / farthest)
    inner = round(size * scale)
    canvas = Image.new("RGB", (size, size), BACKGROUND)
    offset = (size - inner) // 2
    canvas.paste(resized(image, inner), (offset, offset))
    return canvas, scale


def main() -> None:
    image = Image.open(SOURCE).convert("RGB")
    if image.size != (1024, 1024):
        raise SystemExit(f"원본 크기가 1024×1024가 아닙니다: {image.size}")
    mask = ink_mask(image)

    favicon_crop(image, mask).save(OUT_DIR / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
    resized(image, 180).save(OUT_DIR / "apple-touch-icon.png", optimize=True)
    for size in (192, 512):
        resized(image, size).save(OUT_DIR / f"icon-{size}.png", optimize=True)
    icon, scale = maskable(image, mask, 512)
    icon.save(OUT_DIR / "icon-maskable-512.png", optimize=True)

    for name in ("favicon.ico", "apple-touch-icon.png", "icon-192.png", "icon-512.png", "icon-maskable-512.png"):
        print(f"{name}\t{(OUT_DIR / name).stat().st_size:,} bytes")
    print(f"maskable 축소 비율\t{scale:.3f}")


if __name__ == "__main__":
    main()
