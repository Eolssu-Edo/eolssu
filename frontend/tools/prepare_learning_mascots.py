"""Transcode the three supplied GIFs to portable Compose frame sheets (Pillow)."""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageSequence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("gifs", nargs=3, type=Path, metavar="GIF")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    resources = root / "shared/src/commonMain/composeResources"
    records = {}
    for name, path in zip(("study", "point", "rhythm"), args.gifs):
        with Image.open(path) as source:
            frames = [frame.convert("RGB").resize((192, 192), Image.Resampling.LANCZOS)
                      for frame in ImageSequence.Iterator(source)]
            source.seek(0)
            durations = [frame.info.get("duration", 100) for frame in ImageSequence.Iterator(source)]
        if len(frames) != 30 or durations != [70, 60, 70] * 10:
            raise ValueError(f"{path.name}: update the Compose player for this frame count/timing")
        # Keep all original frames and durations; no re-timing or generated artwork.
        sheet = Image.new("RGB", (192 * 6, 192 * ((len(frames) + 5) // 6)), "white")
        for index, frame in enumerate(frames):
            sheet.paste(frame, ((index % 6) * 192, (index // 6) * 192))
        sheet.save(resources / "drawable" / f"learning_mascot_{name}.png", optimize=True)
        records[name] = {"source": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                         "frameSize": 192, "columns": 6, "durationsMs": durations}
    destination = resources / "files" / "learning_mascots.json"
    destination.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name, record in records.items():
        print(name, record["durationsMs"])


if __name__ == "__main__":
    main()
