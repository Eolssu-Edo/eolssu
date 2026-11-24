"""Export the seven S15P31E102-602 reference diagrams using the app artwork."""

import argparse
from pathlib import Path
import re

from PIL import Image, ImageDraw, ImageFont


FRONTEND = Path(__file__).resolve().parents[1]
RESOURCES = FRONTEND / "shared/src/commonMain/composeResources"
MODEL = FRONTEND / "shared/src/commonMain/kotlin/com/ssafy/eolssu/core/model/fingering/Fingering.kt"
DEFAULT_OUTPUT = FRONTEND.parent / "docs/knowledge/frontend/fingering-images"

INSTRUMENTS = {
    "danso": ("Danso", "단소"),
    "sogeum": ("Sogeum", "소금"),
    "daegeum_sanjo": ("DaegeumSanjo", "산조대금"),
}
CARDS = (
    ("danso", "cheonghyeop", "청협", "11111"),
    ("danso", "cheonggo", "청고", "1111h"),
    ("danso", "cheongnam", "청남", "11h00"),
    ("danso", "jungcheonghyeop", "중청협", "01100"),
    ("danso", "jungcheongjung", "중청중", "11010"),
    ("sogeum", "jungcheongtae", "중청태", "110000"),
    ("daegeum_sanjo", "jungcheongtae", "중청태", "110111"),
)

INK = "#27221E"
MUTED = "#5C564F"


def font(weight, size):
    return ImageFont.truetype(RESOURCES / "font" / f"eolssu_sans_kr_{weight}.otf", size)


def diagram(instrument, model, pattern):
    definition = INSTRUMENTS[instrument][0]
    match = re.search(rf"\b{definition}\(.*?holeRadius = (\d+),\s*holes = points\(([^)]*)\)", model, re.S)
    if match is None:
        raise ValueError(f"Hole coordinates missing from {definition}")
    radius = int(match[1]) - 2
    holes = [(int(x), int(y)) for x, y in re.findall(r"(\d+) to (\d+)", match[2])]
    if len(holes) != len(pattern) or set(pattern) - {"1", "0", "h"}:
        raise ValueError(f"Invalid {instrument} fingering: {pattern}")

    with Image.open(RESOURCES / "drawable" / f"instrument_{instrument}.webp") as source:
        image = source.convert("RGBA")
    draw = ImageDraw.Draw(image)
    for (x, y), state in zip(holes, pattern, strict=True):
        box = (x - radius, y - radius, x + radius, y + radius)
        if state == "1":
            draw.ellipse(box, fill=INK)
        elif state == "h":
            draw.pieslice(box, 90, 270, fill=INK)

    # Keep the app's instrument outlines and replace the printed hole labels.
    # The reference charts count the back hole of the danso as hole 1.
    top, bottom = 156, 411 if instrument == "danso" else 400
    scale = 0.55
    image = image.crop((0, top, image.width, bottom))
    image = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    return image, [round(x * scale) for x, _ in holes]


def render(instrument, note, pattern, model):
    image = Image.new("RGB", (1200, 532), "#F9FAF9")
    draw = ImageDraw.Draw(image)
    name = INSTRUMENTS[instrument][1]
    draw.text((54, 45), f"{name} 운지", font=font("semibold", 25), fill=MUTED)
    draw.text((50, 78), note, font=font("bold", 75), fill=INK)
    draw.rounded_rectangle((1030, 61, 1146, 115), radius=26, fill="#EFF2D6")
    draw.text((1088, 87), "역취", font=font("semibold", 25), fill="#4A5316", anchor="mm")

    draw.rounded_rectangle((36, 178, 1164, 428), radius=22, fill="white", outline="#E8E4DA", width=2)
    figure, holes = diagram(instrument, model, pattern)
    image.paste(figure, (50, 222), figure)
    mouth_x = {"danso": 104, "sogeum": 164, "daegeum_sanjo": 151}[instrument]
    draw.text((mouth_x, 389), "취구", font=font("semibold", 21), fill=MUTED, anchor="mm")
    if instrument == "danso":
        draw.text((50 + holes[0], 212), "1공 · 뒷구멍", font=font("semibold", 21), fill=INK, anchor="mm")
    else:
        draw.text((1053, 389), "칠성공", font=font("semibold", 21), fill=MUTED, anchor="mm")
        if instrument == "daegeum_sanjo":
            draw.text((365, 389), "청공", font=font("semibold", 21), fill=MUTED, anchor="mm")
    for number, x in enumerate(holes, 1):
        if number != 1 or instrument != "danso":
            draw.text((50 + x, 389), f"{number}공", font=font("semibold", 21), fill=INK, anchor="mm")

    for x, state, label in ((62, "1", "막음"), (222, "0", "열림"), (382, "h", "반 막음")):
        box = (x - 10, 470, x + 10, 490)
        if state == "h":
            draw.pieslice(box, 90, 270, fill=INK)
        draw.ellipse(box, fill=INK if state == "1" else None, outline=INK, width=2)
        draw.text((x + 22, 480), label, font=font("regular", 23), fill=MUTED, anchor="lm")
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    output = parser.parse_args().output
    output.mkdir(parents=True, exist_ok=True)
    model = MODEL.read_text(encoding="utf-8")
    for instrument, slug, note, pattern in CARDS:
        render(instrument, note, pattern, model).save(output / f"{instrument}-{slug}.png", optimize=True)


if __name__ == "__main__":
    main()
