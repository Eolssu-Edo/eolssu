"""Jigmo 20250912의 실제 율명 세 글자만 남긴다 (fonttools==4.60.1).

frontend/에서 실행: python -P tools/fonts/build_supplementary_yul.py <Jigmo2.ttf>
원본: https://kamichikoichi.github.io/jigmo/Jigmo-20250912.zip (CC0 1.0)
"""

import hashlib
import sys
from pathlib import Path

from fontTools import subset
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont

SOURCE_SHA256 = "5da3582efe77e22073b86b3b86b556d7111148a76b957cfb53318a91da2efff0"
CODEPOINTS = {0x224E1, 0x2042D, 0x23D18}  # 하배이 · 배유 · 중청대
OUTPUT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/font/eolssu_yul_supplement.ttf"


def verify(font: TTFont) -> None:
    cmap = font.getBestCmap()
    if set(cmap) != CODEPOINTS:
        raise ValueError(f"세 율명 외의 문자 또는 누락이 있습니다: {set(cmap)}")
    glyphs = font.getGlyphSet()
    for codepoint in CODEPOINTS:
        name = cmap[codepoint]
        pen = BoundsPen(glyphs)
        glyphs[name].draw(pen)
        if not font.getGlyphID(name) or pen.bounds is None or font["hmtx"][name][0] <= 0:
            raise ValueError(f"U+{codepoint:05X}의 실제 글리프가 없습니다")


def build(source: Path) -> None:
    if hashlib.sha256(source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError("Jigmo2.ttf 원본 해시가 다릅니다")
    font = TTFont(source, recalcTimestamp=False)
    if not CODEPOINTS <= font.getBestCmap().keys():
        raise ValueError("원본에 필요한 글리프가 없습니다")
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=CODEPOINTS)
    subsetter.subset(font)
    names = {
        1: "Eolssu Yul Supplement", 2: "Regular", 3: "EolssuYulSupplement-20250912-1",
        4: "Eolssu Yul Supplement", 6: "EolssuYulSupplement",
        10: "Subset of Jigmo2 20250912; three Korean music ideographs. Subset and renamed 2026-11-12. CC0 1.0.",
        16: "Eolssu Yul Supplement", 17: "Regular", 18: "Eolssu Yul Supplement",
    }
    font["name"].names = [record for record in font["name"].names if record.nameID not in names]
    for name_id, value in names.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
    verify(font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
    with TTFont(OUTPUT) as saved:
        verify(saved)
    print(f"{OUTPUT.name}: {OUTPUT.stat().st_size} bytes, SHA256 {hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(Path(sys.argv[1]))
