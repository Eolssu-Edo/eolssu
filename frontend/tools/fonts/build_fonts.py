"""앱에 넣는 글꼴 파일을 만든다.

Compose Web은 기기 글꼴로 한글·한자를 대신 그리지 않으므로 글꼴 파일을 앱 리소스에 넣어야 한다.
- 본문: Pretendard 원본(Regular·SemiBold·Bold). 한글 11,172자가 모두 있다. 예약 글꼴 이름이 있어 가공하지 않는다.
- 율명·제목: Noto Serif KR(CFF) SemiBold·Bold에서 자주 쓰는 한글 2,350자(KS X 1001)와 율명 한자만 남긴다.
  그래서 세리프는 율명과 앱이 정한 문구에만 쓰고, 사용자가 입력한 글자는 Pretendard로 표시한다.

사용법(원본 내려받기는 frontend/README.md 참고, frontend/에서 실행):
    python -m pip install --user fonttools==4.60.1
    python -P tools/fonts/build_fonts.py <원본 폴더>
"""

import hashlib
import shutil
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

OUT_DIR = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/font"

SOURCES = {
    "Pretendard-Regular.otf": "3ffbacde6ab8411f1d2db54bb9b1f0b3ee2a738932033722cf0388c06aed1c93",
    "Pretendard-SemiBold.otf": "c89bc43027dc7cde5726e96223376f8eec09302b2fc1f8147fd5b57cfc376118",
    "Pretendard-Bold.otf": "2e91915fab54df71cc9598ebf608b2bdb54c6fe3c066ac61dff0bc44fca71cc7",
    "NotoSerifKR-SemiBold.otf": "a63051d299d7219132a98ff3c2b76a62022344e50e9f3b50fdb9b0ee4560f583",
    "NotoSerifKR-Bold.otf": "263408609ef416d597718ea91055379867d3e7086fa32f049996995d4e715e55",
}

PRETENDARD = {
    "Pretendard-Regular.otf": "pretendard_regular.otf",
    "Pretendard-SemiBold.otf": "pretendard_semibold.otf",
    "Pretendard-Bold.otf": "pretendard_bold.otf",
}

SERIF = {
    "NotoSerifKR-SemiBold.otf": "noto_serif_kr_semibold.otf",
    "NotoSerifKR-Bold.otf": "noto_serif_kr_bold.otf",
}

# 세리프로 그리는 한자. 12율명 기본형, 와이어프레임·데이터에서 쓰는 청성, 정간보 설명에 쓰는 글자.
# 새 율명 표기가 데이터에 생기면 여기에 추가하고 다시 만든다. Noto Serif KR에 없는 글자(예: 탁황 僙)는 넣을 수 없다.
SERIF_HANJA = "黃大太夾姑仲蕤林夷南無應" "潢汰淋" "井亻氵"

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
    for src_name, out_name in PRETENDARD.items():
        shutil.copyfile(src / src_name, OUT_DIR / out_name)
    build_serif(src)
    for name in [*PRETENDARD.values(), *SERIF.values()]:
        print(f"{name}\t{(OUT_DIR / name).stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
