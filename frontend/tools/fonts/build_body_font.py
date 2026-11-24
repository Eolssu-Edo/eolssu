"""본문 글꼴(IBM Plex Sans KR)을 크기를 줄인 CFF(OTF)로 바꾼다.

웹은 첫 화면에서 본문 글꼴 세 굵기를 모두 내려받는다. 원본 TTF(이차 곡선)는 굵기마다 약 2.8MB라서
윤곽을 삼차 곡선(CFF)으로 바꿔 줄인다. 글자는 하나도 빼지 않는다(한글 11,172자·자모·라틴·기호 그대로).
- 이차 곡선 여러 개를 삼차 곡선 하나로 합칠 때 허용 오차는 1/1000em(MAX_ERR)이다. 화면에서 구분되지 않는다.
- TrueType 힌팅(cvt·fpgm·prep·gasp·VDMX)은 CFF에 쓸 수 없어 뺀다. 원본 힌팅도 거의 없다.
- 원본은 예약 글꼴 이름 "Plex"가 있는 OFL 1.1이라, 고친 글꼴의 이름을 "Eolssu Sans KR"로 바꾼다.
  저작권·라이선스 이름 항목은 그대로 두고 라이선스 원문은 앱의 files/licenses/ibm_plex_sans_kr_ofl.txt에 있다.

사용법(원본 내려받기는 frontend/README.md 참고, frontend/에서 실행):
    python -m pip install --user fonttools==4.60.1
    python -P tools/fonts/build_body_font.py <원본 폴더>
"""

import hashlib
import sys
from pathlib import Path

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.qu2cuPen import Qu2CuPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.ttLib import TTFont

OUT_DIR = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/font"

# Google Fonts ofl/ibmplexsanskr 원본(Version 1.001). S15P31E102-1267 전까지 앱에 그대로 넣던 파일과 같다.
SOURCES = {
    "IBMPlexSansKR-Regular.ttf": ("53750379270312368cf7641901f43a98dd892e3d9d5798cf25cdc245c85c71c0", "eolssu_sans_kr_regular.otf"),
    "IBMPlexSansKR-SemiBold.ttf": ("823c1956adf56c27c06b062725dfce23f266e4fd1366290f2957c8407482a817", "eolssu_sans_kr_semibold.otf"),
    "IBMPlexSansKR-Bold.ttf": ("9d82a8be5330f6d7b53121262867b402baca672eb69b852928f06d185d357f7d", "eolssu_sans_kr_bold.otf"),
}

MAX_ERR = 1.0  # 글꼴 단위(1000/em)
TRUETYPE_ONLY = ["glyf", "loca", "cvt ", "fpgm", "prep", "gasp", "VDMX", "hdmx", "LTSH", "DSIG"]
OLD_FAMILY, NEW_FAMILY = "IBM Plex Sans KR", "Eolssu Sans KR"
OLD_PS, NEW_PS = "IBMPlexSansKR", "EolssuSansKR"
RENAMED_IDS = {1, 3, 4, 6, 16, 17, 18, 21, 22}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rename(font: TTFont) -> None:
    name = font["name"]
    for record in name.names:
        if record.nameID in RENAMED_IDS:
            text = record.toUnicode().replace(OLD_FAMILY, NEW_FAMILY).replace(OLD_PS, NEW_PS)
            name.setName(text, record.nameID, record.platformID, record.platEncID, record.langID)
    # 상표 안내(7)는 원래 글꼴 이름을 가리키므로 고친 글꼴에서는 뺀다.
    name.removeNames(nameID=7)
    leftover = [r.nameID for r in name.names if "Plex" in r.toUnicode() and r.nameID in RENAMED_IDS]
    if leftover:
        sys.exit(f"예약 이름이 남은 name ID: {leftover}")


def to_cff(src: Path, out: Path) -> None:
    # 원본 수정 시각을 유지해 다시 만들어도 같은 파일이 나오게 한다.
    font = TTFont(src, recalcTimestamp=False)
    glyphs = font.getGlyphSet()
    order = font.getGlyphOrder()
    metrics = font["hmtx"].metrics
    charstrings = {}
    for glyph in order:
        pen = T2CharStringPen(metrics[glyph][0], glyphs)
        glyphs[glyph].draw(Qu2CuPen(pen, max_err=MAX_ERR, all_cubic=True))
        charstrings[glyph] = pen.getCharString()

    for tag in TRUETYPE_ONLY:
        if tag in font:
            del font[tag]
    rename(font)
    font.sfntVersion = "OTTO"
    builder = FontBuilder(font=font)
    builder.isTTF = False
    builder.setupCFF(
        font["name"].getDebugName(6),
        {"FullName": font["name"].getDebugName(4), "FamilyName": NEW_FAMILY},
        charstrings,
        {},
    )
    # 왼쪽 여백(lsb)은 CFF 윤곽의 실제 xMin으로 다시 맞춘다.
    advance_lsb = {}
    for glyph in order:
        bounds = charstrings[glyph].calcBounds(None)
        advance_lsb[glyph] = (metrics[glyph][0], round(bounds[0]) if bounds else 0)
    builder.setupHorizontalMetrics(advance_lsb)
    font["maxp"].tableVersion = 0x00005000
    font["post"].formatType = 3.0
    font.save(out)


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    for src_name, (expected, _) in SOURCES.items():
        actual = sha256(src / src_name)
        if actual != expected:
            sys.exit(f"{src_name} 해시가 다릅니다: {actual} (기대값 {expected})")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for src_name, (_, out_name) in SOURCES.items():
        to_cff(src / src_name, OUT_DIR / out_name)
        before = (src / src_name).stat().st_size
        after = (OUT_DIR / out_name).stat().st_size
        print(f"{out_name}\t{before:,} -> {after:,} bytes")


if __name__ == "__main__":
    main()
