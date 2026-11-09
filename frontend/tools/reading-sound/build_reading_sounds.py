"""Build the reading lesson's offline examples.

Without --danso-source, only the four beat examples are generated or checked.
Pass the original Danso_2.wav and FFmpeg with librubberband for the three pitch examples.
Durations and the beat examples' score come from ReadingAudio.kt.
"""

from array import array
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import argparse
import math
import re
import subprocess
import sys
import wave

FRONTEND = Path(__file__).resolve().parents[2]
MODEL = FRONTEND / "shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/ReadingAudio.kt"
OUTPUT = FRONTEND / "shared/src/commonMain/composeResources/files/reading"
RATE = 24_000
# Keep the lesson's E-flat octave comparison, independent of the instrument's playable range.
PITCHES = {"LowHwang": 51, "Hwang": 63, "HighHwang": 75}
NOTES = {"林": 70, "南": 72, "太": 65}
# 국립국악원 국악기 디지털 음원 > 단소 > 평취 중 (KOGL-1)
# https://www.gugak.go.kr/digitaleum/front/monotone/list.do
# data/catalog/danso/sources.json: ngc-danso-2339; 황종: ngc-danso-2339.tone-004
DANSO_SHA256 = "ca706f981e54dbfeb72089b6f72b6494dea1fb39193f9f31c0048cfce3883d9c"
DANSO_HZ = 622.493


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


def danso_tone(source, ffmpeg, seconds, midi):
    pitch_ratio = frequency(midi) / DANSO_HZ
    filters = (
        "atrim=start=19.45:end=21.20,asetpts=PTS-STARTPTS,"
        f"pan=mono|c0=0.5*c0+0.5*c1,aresample={RATE},"
        f"rubberband=pitch={pitch_ratio:.9f}:pitchq=quality"
    )
    raw = subprocess.run(
        [ffmpeg, "-v", "error", "-nostdin", "-i", str(source), "-af", filters, "-f", "f32le", "-"],
        stdout=subprocess.PIPE,
        check=True,
    ).stdout
    samples = array("f")
    samples.frombytes(raw)
    if sys.byteorder != "little":
        samples.byteswap()
    count = round(seconds * RATE)
    if len(samples) < count:
        raise ValueError(f"단소 음원이 너무 짧습니다: {len(samples)} < {count} samples")
    samples = samples[:count]
    gain = 0.55 / max(abs(sample) for sample in samples)
    return [
        sample * gain * min(1, i / (0.03 * RATE), (count - 1 - i) / (0.09 * RATE))
        for i, sample in enumerate(samples)
    ]


def build(danso_source=None, ffmpeg="ffmpeg"):
    if danso_source and sha256(danso_source.read_bytes()).hexdigest() != DANSO_SHA256:
        raise ValueError("국립국악원 단소 「평취 중」 원본(Danso_2.wav)과 SHA-256이 일치하지 않습니다.")
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
        if name in names:
            samples = [0.0] * round(seconds * RATE)
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
        elif danso_source:
            samples = danso_tone(danso_source, ffmpeg, seconds, PITCHES[name])
        else:
            continue
        result[file + ".wav"] = wav(samples)
    assert len(result) == len(names) + (len(PITCHES) if danso_source else 0)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify committed assets without changing files")
    parser.add_argument("--danso-source", type=Path, help="Original 국립국악원 Danso_2.wav")
    parser.add_argument("--ffmpeg", default="ffmpeg", help="FFmpeg executable with librubberband")
    args = parser.parse_args()
    generated = build(args.danso_source, args.ffmpeg)
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
