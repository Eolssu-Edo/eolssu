"""홈 첫 화면 그림 네 장을 합성해 앱 자산(WebP)으로 저장한다(S15P31E102-983).

사용(저장소 루트에서):
  python -I frontend/tools/home-art/build_home_art.py            # 앱 자산 폴더에 덮어쓴다
  python -I frontend/tools/home-art/build_home_art.py --check    # 다시 만들어 지금 앱 자산과 같은지만 비교한다

필요한 것: Python 3.11, numpy, Pillow(WebP 지원). 네 장을 동시에 만들며 PC에 따라 몇 분 걸린다.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / "shared" / "src" / "commonMain" / "composeResources" / "drawable"
OLD = HERE / "base_home_illustration.webp"
SRC = HERE / "source_pavilion.webp"

# 이름: (W, H, S, TOP, X0, XG, XM). 값의 뜻은 compose_home_art.py의 설명을 따른다.
KINDS = {
    "phone_portrait": (1080, 2340, 1.35, 852, 215, 92, -444),
    "phone_landscape": (2340, 1080, 0.95, 93, -147, 298, 760),
    "tablet_portrait": (1200, 1920, 1.3, 521, 186, 62, -188),
    "tablet_landscape": (1920, 1200, 0.9, 245, -167, 332, 600),
}


def main() -> int:
    check = "--check" in sys.argv[1:]
    with tempfile.TemporaryDirectory() as tmp:
        jobs = {}
        for name, args in KINDS.items():
            png = Path(tmp) / f"{name}.png"
            cmd = [sys.executable, "-I", str(HERE / "compose_home_art.py"), str(OLD), str(SRC), str(png), *map(str, args)]
            jobs[name] = (png, subprocess.Popen(cmd, stdout=subprocess.DEVNULL))
        failed = [name for name, (_, proc) in jobs.items() if proc.wait() != 0]
        if failed:
            print("합성 실패:", ", ".join(failed))
            return 1
        different = []
        for name, (png, _) in jobs.items():
            target = ASSETS / f"home_art_{name}.webp"
            out = Path(tmp) / f"{name}.webp" if check else target
            Image.open(png).convert("RGB").save(out, "WEBP", quality=86, method=6)
            if check:
                same = target.exists() and ImageChops.difference(
                    Image.open(out).convert("RGB"), Image.open(target).convert("RGB")
                ).getbbox() is None
                print(name, "같음" if same else "다름")
                if not same:
                    different.append(name)
            else:
                print("저장:", target)
        return 1 if different else 0


if __name__ == "__main__":
    raise SystemExit(main())
