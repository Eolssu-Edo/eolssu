"""Build the reading lesson's offline examples.

Pass the original Danso_2.wav and FFmpeg with librubberband to generate or check
the seven AAC-LC M4A files. Durations and the beat examples' score come from ReadingAudio.kt.
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
from tempfile import TemporaryDirectory
import wave

FRONTEND = Path(__file__).resolve().parents[2]
MODEL = FRONTEND / "shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/ReadingAudio.kt"
OUTPUT = FRONTEND / "shared/src/commonMain/composeResources/files/reading"
RATE = 24_000
# Keep the lesson's E-flat octave comparison, independent of the instrument's playable range.
PITCHES = {"LowHwang": 51, "Hwang": 63, "HighHwang": 75}
# 국립국악원 국악기 디지털 음원 > 단소 > 평취 중 (KOGL-1)
# https://www.gugak.go.kr/digitaleum/front/monotone/list.do
# data/catalog/danso/sources.json: ngc-danso-2339
DANSO_SHA256 = "ca706f981e54dbfeb72089b6f72b6494dea1fb39193f9f31c0048cfce3883d9c"
# Each source range is (start seconds, end seconds, measured Hz).
HWANG_SAMPLE = (19.45, 21.20, 622.493)  # tone-004
LENGTH_NOTES = {
    "林": (70, (6.62, 8.90, 465.528)),   # tone-002, 임종
    "南": (72, (12.99, 15.30, 554.360)), # tone-003, 무역을 남려의 높이로 내림
    "太": (65, (25.80, 28.10, 700.560)), # tone-005, 태주
}


def frequency(midi):
    return 440 * 2 ** ((midi - 69) / 12)


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


def encode_m4a(samples, ffmpeg):
    with TemporaryDirectory() as directory:
        output = Path(directory) / "example.m4a"
        subprocess.run(
            [ffmpeg, "-v", "error", "-nostdin", "-i", "pipe:0", "-map_metadata", "-1",
             "-c:a", "aac", "-b:a", "96k", "-fflags", "+bitexact", "-flags:a", "+bitexact",
             "-movflags", "+faststart", str(output)],
            input=wav(samples),
            check=True,
        )
        return output.read_bytes()


def read_danso(source, ffmpeg, midi, sample):
    start, end, recorded_hz = sample
    pitch_ratio = frequency(midi) / recorded_hz
    filters = (
        f"atrim=start={start:.2f}:end={end:.2f},asetpts=PTS-STARTPTS,"
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
    return samples


def shape_tone(samples, seconds, *, peak, attack, release, gain_seconds):
    count = round(seconds * RATE)
    gain_count = round(gain_seconds * RATE)
    if len(samples) < max(count, gain_count):
        raise ValueError(f"단소 음원이 너무 짧습니다: {len(samples)} < {max(count, gain_count)} samples")
    gain = peak / max(abs(sample) for sample in samples[:gain_count])
    return [
        sample * gain * min(1, i / (attack * RATE), (count - 1 - i) / (release * RATE))
        for i, sample in enumerate(samples[:count])
    ]


def build(danso_source, ffmpeg="ffmpeg"):
    if sha256(danso_source.read_bytes()).hexdigest() != DANSO_SHA256:
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
    length_recordings = {
        note: read_danso(danso_source, ffmpeg, midi, sample)
        for note, (midi, sample) in LENGTH_NOTES.items()
    }
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
                        timeline.append([t, length, note])
                    t += length
            assert math.isclose(t, seconds)
            for start, length, note in timeline:
                clip = shape_tone(
                    length_recordings[note], length, peak=0.33, attack=0.008,
                    release=0.04, gain_seconds=seconds,
                )
                first = round(start * RATE)
                for i, sample in enumerate(clip, start=first):
                    samples[i] += sample
            for i in range(2):
                click(samples, i * beat)
        else:
            recording = read_danso(danso_source, ffmpeg, PITCHES[name], HWANG_SAMPLE)
            samples = shape_tone(
                recording, seconds, peak=0.55, attack=0.03,
                release=0.09, gain_seconds=seconds,
            )
        result[file + ".m4a"] = encode_m4a(samples, ffmpeg)
    assert len(result) == len(names) + len(PITCHES)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify committed assets without changing files")
    parser.add_argument("--danso-source", type=Path, required=True, help="Original 국립국악원 Danso_2.wav")
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
    print(f"{'Verified' if args.check else 'Built'} {len(generated)} M4A files, {sum(map(len, generated.values())):,} bytes")
