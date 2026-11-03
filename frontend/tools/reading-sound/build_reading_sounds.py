"""Build the reading lesson's offline, educational tones with Python's standard library.

Run from any directory: python frontend/tools/reading-sound/build_reading_sounds.py [--check]
Durations, cell splits, holds and rests come from ReadingAudio.kt, not a second score.
These are comparison tones, not recordings or an instrument's tuning reference.
"""

from array import array
from io import BytesIO
from pathlib import Path
import argparse
import math
import re
import sys
import wave

FRONTEND = Path(__file__).resolve().parents[2]
MODEL = FRONTEND / "shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/ReadingAudio.kt"
OUTPUT = FRONTEND / "shared/src/commonMain/composeResources/files/reading"
RATE = 24_000
# A neutral E-flat Huang example: same timbre, exact 1:2:4 octave ratio.
PITCHES = {"LowHwang": 51, "Hwang": 63, "HighHwang": 75}
NOTES = {"林": 70, "南": 72, "太": 65}


def frequency(midi):
    return 440 * 2 ** ((midi - 69) / 12)


def tone(samples, start, duration, midi):
    first, count = round(start * RATE), round(duration * RATE)
    hz = frequency(midi)
    for i in range(count):
        t = i / RATE
        envelope = min(1, t / 0.008, (duration - t) / 0.025)
        phase = 2 * math.pi * hz * t
        value = math.sin(phase) + 0.12 * math.sin(2 * phase) + 0.035 * math.sin(3 * phase)
        samples[first + i] += 0.25 * envelope * value


def click(samples, start):
    first = round(start * RATE)
    for i in range(round(0.045 * RATE)):
        t = i / RATE
        envelope = min(1, t / 0.001) * math.exp(-t * 105)
        samples[first + i] += 0.32 * envelope * (math.sin(2 * math.pi * 1450 * t) + 0.3 * math.sin(2 * math.pi * 2100 * t))


def wav(samples):
    assert max(abs(x) for x in samples) < 1, "Audio must not clip"
    pcm = array("h", (round(x * 32767) for x in samples))
    if sys.byteorder != "little":
        pcm.byteswap()
    output = BytesIO()
    with wave.open(output, "wb") as media:
        media.setnchannels(1)
        media.setsampwidth(2)
        media.setframerate(RATE)
        media.writeframes(pcm.tobytes())
    return output.getvalue()


def build():
    model = MODEL.read_text(encoding="utf-8")
    beat_ms = int(re.search(r"ReadingBeatMs = ([\d_]+)L", model)[1].replace("_", ""))
    beat = beat_ms / 1000
    table = model.split("internal val ReadingLengthCells = listOf(", 1)[1].split("\n)", 1)[0]
    rows = re.findall(r"^\s*listOf\((.+)\),$", table, re.MULTILINE)
    cells = [[re.findall(r'"([^"]+)"', cell) for cell in re.findall(r"listOf\(([^()]*)\)", row)] for row in rows]
    names = re.findall(r"ReadingAudioClip\.(\w+)", model.split("internal val ReadingLengthClips = ", 1)[1].split("\n", 1)[0])
    assert len(cells) == len(names) == 4
    clips = re.findall(r'^\s+(\w+)\("([^"]+)", ([^)]+)\),$', model, re.MULTILINE)
    result = {}
    for name, file, duration in clips:
        seconds = beat * 2 if duration == "ReadingBeatMs * 2" else int(duration.replace("_", "")) / 1000
        samples = [0.0] * round(seconds * RATE)
        if name in names:
            timeline = []
            t = 0
            for cell in cells[names.index(name)]:
                length = beat / len(cell)
                for note in cell:
                    if note == "ㅡ":
                        timeline[-1][1] += length
                    elif note != "△":
                        timeline.append([t, length, NOTES[note]])
                    t += length
            assert math.isclose(t, seconds)
            for start, length, midi in timeline:
                tone(samples, start, length, midi)
            for i in range(2):
                click(samples, i * beat)
        else:
            tone(samples, 0, seconds, PITCHES[name])
        result[file + ".wav"] = wav(samples)
    assert len(result) == 7
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify committed assets without changing files")
    args = parser.parse_args()
    generated = build()
    if not args.check:
        OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, data in generated.items():
        path = OUTPUT / name
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                raise SystemExit(f"Out of date: {path}")
        else:
            path.write_bytes(data)
    print(f"{'Verified' if args.check else 'Built'} {len(generated)} WAV files, {sum(map(len, generated.values())):,} bytes")
