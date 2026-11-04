"""Render one catalog cycle with National Gugak Center's recorded janggu strokes."""

import argparse
from array import array
from fractions import Fraction
from hashlib import sha256
import json
from pathlib import Path
import subprocess
import sys
import wave


RATE = 44_100
SOURCE = "https://www.gugak.go.kr/digitaleum/front/monotone/list.do"
SAMPLES = {
    # file, mntnSeq, onset in milliseconds, retained tail in milliseconds
    "덩": ("Janggu_1_2.wav", 2283, 205, 400),
    "기": ("Janggu_2_2.wav", 2286, 204, 125),
    "쿵": ("Janggu_3_2.wav", 2289, 205, 400),
    "덕": ("Janggu_5_2.wav", 2295, 204, 260),
    "다": ("Janggu_5_3.wav", 2296, 213, 200),
    "더": ("Janggu_6_2.wav", 2298, 206, 180),
}
CELLS = {
    "세마치": ("DOTTED_QUARTER", Fraction(1, 3)),
    "굿거리": ("DOTTED_QUARTER", Fraction(1, 3)),
    "중모리": ("QUARTER", Fraction(1)),
    "자진모리": ("DOTTED_QUARTER", Fraction(1, 3)),
    "중중모리": ("DOTTED_QUARTER", Fraction(1, 3)),
    "엇모리": ("QUARTER", Fraction(1, 2)),
    "단모리": ("QUARTER", Fraction(1, 2)),
    "타령": ("QUARTER", Fraction(1, 2)),
    "계면가락도드리": ("QUARTER", Fraction(1, 2)),
    "세령산": ("QUARTER", Fraction(3, 2)),
}
SINGLE = {"떵": "덩", "덩": "덩", "쿵": "쿵", "궁": "쿵", "덕": "덕", "따": "덕", "다": "다"}


def rhythm_plan(row):
    unit, cell_ratio = CELLS[row["name"]]
    beats, groups = row["pattern"]["beats"], row["pattern"]["group"]
    if row["bpm_unit"] != unit or row["default_bpm"] <= 0 or not beats or sum(groups) != len(beats):
        raise ValueError(f"Invalid catalog timing: {row['name']}")
    cell = Fraction(60, row["default_bpm"]) * cell_ratio
    events = []
    for index, syllable in enumerate(beats):
        time = cell * index
        if not syllable:
            continue
        if syllable in SINGLE:
            events.append((time, SINGLE[syllable], 1.0))
        elif syllable in ("기덕", "기닥"):
            grace = min(Fraction(35, 1000), cell / 4)
            events.extend(((time - grace, "기", 0.7), (time, "덕" if syllable == "기덕" else "다", 1.0)))
        elif syllable in ("덕기", "덕덕"):
            offset = Fraction(3, 4) if syllable == "덕기" else Fraction(1, 2)
            events.extend(((time, "덕", 1.0), (time + cell * offset, "기" if syllable == "덕기" else "덕", 0.8)))
        elif syllable == "더러러러":
            span = 2 * cell if row["name"] == "굿거리" else cell
            events.extend((time + span * step / 4, "더", gain) for step, gain in enumerate((1.0, 0.65, 0.7, 0.8)))
        else:
            raise ValueError(f"Unsupported stroke: {syllable}")
    duration = len(beats) * cell
    if not events or any(not 0 <= time < duration for time, _, _ in events):
        raise ValueError(f"Stroke falls outside cycle: {row['name']}")
    return duration, sorted(events)


def read_stroke(path, onset_ms, tail_ms):
    with wave.open(str(path), "rb") as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (2, 2, RATE):
            raise ValueError(f"Expected stereo PCM16 at {RATE} Hz: {path}")
        # Preserve a 2 ms lead so a measured onset is never cut mid-attack.
        source.setpos(round((onset_ms - 2) * RATE / 1000))
        frames = array("h", source.readframes(round(tail_ms * RATE / 1000)))
    if sys.byteorder != "little":
        frames.byteswap()
    mono = [(left + right) / 65536 for left, right in zip(frames[::2], frames[1::2])]
    if not mono or max(map(abs, mono)) < 0.02:
        raise ValueError(f"Missing audible attack: {path}")
    return mono


def render_cycle(duration, events, samples):
    mix = [0.0] * round(duration * RATE)
    fade = RATE // 100
    for time, stroke, gain in events:
        start = round(time * RATE)
        sample = samples[stroke]
        length = min(len(sample), len(mix) - start)
        for index in range(length):
            envelope = min(1, index / (RATE / 1000), (length - 1 - index) / fade)
            mix[start + index] += sample[index] * gain * envelope
    peak = max(map(abs, mix))
    if not peak:
        raise ValueError("Rendered cycle is silent")
    # Leave headroom for peaks introduced by the MP3 encoder.
    pcm = array("h", (round(value * 24_000 / peak) for value in mix))
    if sys.byteorder != "little":
        pcm.byteswap()
    return pcm.tobytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--samples", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()

    rows = json.loads(args.reference.read_text(encoding="utf-8"))["rows"]
    if [row["name"] for row in rows] != list(CELLS):
        raise ValueError("Catalog row order no longer matches the ten V21 IDs")
    samples = {}
    sources = {}
    for stroke, (file, identifier, onset, tail) in SAMPLES.items():
        path = args.samples / file
        samples[stroke] = read_stroke(path, onset, tail)
        sources[stroke] = {"file": file, "mntnSeq": identifier, "sha256": sha256(path.read_bytes()).hexdigest()}

    args.output.mkdir(parents=True, exist_ok=False)
    manifest = {"source": SOURCE, "license": "KOGL-1", "rate": RATE, "sources": sources, "assets": []}
    for identifier, row in enumerate(rows, start=1):
        duration, events = rhythm_plan(row)
        pcm = render_cycle(duration, events, samples)
        asset = args.output / f"{identifier}.mp3"
        subprocess.run(
            [args.ffmpeg, "-v", "error", "-n", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
             "-i", "pipe:0", "-codec:a", "libmp3lame", "-b:a", "64k", str(asset)],
            input=pcm, check=True,
        )
        manifest["assets"].append({
            "jangdanId": identifier, "name": row["name"], "bpm": row["default_bpm"], "bpmUnit": row["bpm_unit"],
            "frames": len(pcm) // 2, "file": asset.name, "sha256": sha256(asset.read_bytes()).hexdigest(),
            "events": [[str(time), stroke, gain] for time, stroke, gain in events],
        })
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
