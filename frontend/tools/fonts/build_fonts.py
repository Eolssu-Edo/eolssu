"""앱에 넣는 글꼴 파일을 만든다.

Compose Web은 기기 글꼴로 한글·한자를 대신 그리지 않으므로 글꼴 파일을 앱 리소스에 넣어야 한다.
- 본문 글꼴(Eolssu Sans KR)은 tools/fonts/build_body_font.py가 따로 만든다.
- 율명·제목: Noto Serif CJK KR(CFF) SemiBold·Bold에서 자주 쓰는 한글 2,350자(KS X 1001)와 율명 한자만 남긴다.
  Noto Serif KR(SubsetOTF)에는 탁성·하배·중청 율명이 없어 같은 디자인의 CJK 원본(OTF/Korean)을 쓴다.
  그래서 세리프는 율명과 앱이 정한 문구에만 쓰고, 사용자가 입력한 글자는 본문 글꼴(Eolssu Sans KR)로 표시한다.

사용법(원본 내려받기는 frontend/README.md 참고, frontend/에서 실행):
    python -m pip install --user fonttools==4.60.1
    python -P tools/fonts/build_fonts.py <원본 폴더>
"""

import hashlib
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

OUT_DIR = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/font"

SOURCES = {
    "NotoSerifCJKkr-SemiBold.otf": "bce8d43887e2999c3af13802311fdc2b68f2c5b79d606ed3dd85293649932dcc",
    "NotoSerifCJKkr-Bold.otf": "10cc03741178ad6d2747df8497d911e34b167d0474a826fb9d866c402cbe3d8f",
}

SERIF = {
    "NotoSerifCJKkr-SemiBold.otf": "noto_serif_kr_semibold.otf",
    "NotoSerifCJKkr-Bold.otf": "noto_serif_kr_bold.otf",
}

# 세리프로 그리는 한자. 율명은 core/model/Score.kt의 SourceHanja(옥타브 -2~2)와 운지표 글자, 나머지는 정간보 설명용이다.
# 보충 평면의 하배이 𢓡·배유 𠐭·중청대 𣴘는 원본에 없어 eolssu_yul_supplement.ttf가 맡는다.
# 앱의 SerifYulHanja(core/model/fingering/YulNotation.kt)와 같게 유지한다. 새 율명 표기가 데이터에 생기면 함께 추가한다.
SERIF_YUL = (
    "黃大太夾姑仲蕤林夷南無應"  # 기본
    "潢汏汰浹㴌㳞㶋淋洟湳潕㶐"  # 청성
    "㶂㳲㴺㵈㴢㶙㵉㴣㵜㶃㶝" "㶄㴩㶌㶍"  # 중청(SourceHanja, 운지표)
    "僙㐲㑀俠㑬㑖㑣侇㑲㒇㒣" "㑥"  # 탁성(배)
    "㣴㣕㣖㣣㣨㣡㣸㣩㣮㣳㣹"  # 하배
)
SERIF_HANJA = SERIF_YUL + "井亻氵彳"

SERIF_RANGES = [
    (0x0020, 0x007E),  # 기본 라틴(영숫자·기호)
    (0x00A0, 0x00FF),  # 라틴-1 보충(· × 등)
    (0x2010, 0x2027),  # 일반 구두점(‘ ’ “ ” … 등)
    (0x2030, 0x203A),  # ′ ″ 등
    (0x2190, 0x2193),  # ← ↑ → ↓
    (0x25A0, 0x25FF),  # 도형(△ 등)
    (0x3000, 0x303F),  # 한중일 기호·구두점
    (0x3131, 0x318E),  # 한글 호환 자모(ㅡ 등)
]


def ks_x_1001_hangul() -> set[int]:
    """EUC-KR에서 2바이트로 표현되는 한글 음절 = KS X 1001 완성형 2,350자."""
    return {c for c in range(0xAC00, 0xD7A4) if len(chr(c).encode("euc_kr")) == 2}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_sources(src: Path) -> None:
    for name, expected in SOURCES.items():
        actual = sha256(src / name)
        if actual != expected:
            sys.exit(f"{name} 해시가 다릅니다: {actual} (기대값 {expected})")


def build_serif(src: Path) -> None:
    unicodes = {c for lo, hi in SERIF_RANGES for c in range(lo, hi + 1)}
    unicodes |= ks_x_1001_hangul()
    unicodes |= {ord(ch) for ch in SERIF_HANJA}
    for src_name, out_name in SERIF.items():
        # 원본 수정 시각을 유지해 다시 만들어도 같은 파일이 나오게 한다.
        font = TTFont(src / src_name, recalcTimestamp=False)
        cmap = font.getBestCmap()
        missing = [ch for ch in SERIF_HANJA if ord(ch) not in cmap]
        if missing:
            sys.exit(f"{src_name}에 없는 한자: {''.join(missing)}")
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=unicodes)
        subsetter.subset(font)
        font.save(OUT_DIR / out_name)


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    check_sources(src)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    build_serif(src)
    for name in SERIF.values():
        print(f"{name}\t{(OUT_DIR / name).stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
