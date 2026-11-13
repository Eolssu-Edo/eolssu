"""Analyze one complete EchoPairCapture session using only Python's standard library.

Usage (from the repository root)::

    python frontend/tools/analyze_echo_pair.py --pair-dir LOCAL_SESSION --output report.json

Requires metadata.json plus preparation.wav, original.wav and corrected.wav from
the same diagnostic session. WAVs must be the recorder's canonical 16-bit mono
44100 Hz PCM format. Metadata frame spans and streaming PCM SHA-256 bindings must
match. Incomplete, legacy/unbound or mismatched recordings exit nonzero.

The report measures the captured mixed signal and its sample-wise difference.
It does NOT infer isolated jangdan attenuation, instrument preservation, scoring
accuracy or an automatic quality pass. Separate performances are not an A/B pair.
Windows are fixed at 100 ms except the final partial window. Undefined decibels
or ratios involving zero RMS are represented as JSON null, never Infinity/NaN.
Source files are never changed, and --output may not alias a source file.
"""

import argparse
from array import array
from hashlib import sha256
import json
import math
from pathlib import Path
import sys
import wave


_RATE = 44100
_WINDOW_FRAMES = _RATE // 10
_MAX_FRAMES = _RATE * 180
_LONG_MAX = (1 << 63) - 1
_NAMES = ("preparation", "original", "corrected")


def _integer(manifest: dict, name: str, minimum: int = 0) -> int:
    value = manifest.get(name)
    if type(value) is not int or not minimum <= value <= _LONG_MAX:
        raise ValueError(f"metadata {name} must be an integer >= {minimum}")
    return value


def _frame_span(manifest: dict, kind: str, count: int) -> tuple[int | None, int | None]:
    first_name, end_name = kind + "FirstPhysicalFrame", kind + "EndPhysicalFrame"
    if count == 0:
        if first_name not in manifest or end_name not in manifest or manifest[first_name] is not None or manifest[end_name] is not None:
            raise ValueError(f"empty {kind} must have null physical frame bounds")
        return None, None
    first, end = _integer(manifest, first_name), _integer(manifest, end_name)
    if end - first != count:
        raise ValueError(f"{kind} physical frame span does not match its frame count")
    return first, end


def _pcm_binding(manifest: dict, name: str) -> str:
    field = name + "PcmSha256"
    value = manifest.get(field)
    if not isinstance(value, str) or len(value) != 64 or any(char not in "0123456789abcdef" for char in value.lower()):
        raise ValueError(f"missing or invalid {field}; unbound recordings are not a complete comparison")
    return value.lower()


def _file_hash(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_wave(path: Path, frames: int, expected_hash: str, load_samples: bool) -> tuple[array | None, dict]:
    try:
        with wave.open(str(path), "rb") as source:
            if source.getnchannels() != 1 or source.getsampwidth() != 2 or source.getframerate() != _RATE or source.getcomptype() != "NONE":
                raise ValueError(f"{path.name} must be 16-bit mono 44100 Hz PCM")
            if source.getnframes() != frames:
                raise ValueError(f"{path.name} header frame count differs from metadata")
            payload = source.readframes(frames + 1)
            if len(payload) != frames * 2:
                raise ValueError(f"{path.name} has truncated or excess PCM frames")
    except (wave.Error, EOFError) as error:
        raise ValueError(f"{path.name} is not a readable PCM WAV") from error
    # EchoPairCapture always emits one 44-byte RIFF/PCM header, then exact data.
    # Reject trailing/unaccounted bytes instead of silently accepting a partial file.
    if path.stat().st_size != 44 + frames * 2:
        raise ValueError(f"{path.name} size does not match the recorder's canonical frame layout")
    with path.open("rb") as source:
        header = source.read(12)
    if int.from_bytes(header[4:8], "little") != 36 + frames * 2:
        raise ValueError(f"{path.name} RIFF length differs from its frame count")
    digest = sha256(payload).hexdigest()
    if digest != expected_hash:
        raise ValueError(f"{path.name} PCM hash differs from its paired manifest")
    samples = None
    if load_samples:
        samples = array("h")
        if samples.itemsize != 2:
            raise ValueError("this host does not provide 16-bit array('h') samples")
        samples.frombytes(payload)
        if sys.byteorder != "little":
            samples.byteswap()
    return samples, {"name": path.name, "sha256": _file_hash(path), "pcm_sha256": digest, "frames": frames}


def _dbfs(sum_squares: int, frames: int) -> float | None:
    if not sum_squares:
        return None
    return 10 * math.log10(sum_squares / frames / (32768 * 32768))


def _metrics(before: int, after: int, difference: int, changed: int, frames: int) -> dict:
    return {
        "changed_sample_count": changed,
        "original_rms_dbfs": _dbfs(before, frames),
        "corrected_rms_dbfs": _dbfs(after, frames),
        "mixed_signal_rms_change_db": 10 * math.log10(after / before) if before and after else None,
        "difference_signal_rms_dbfs": _dbfs(difference, frames),
    }


def analyze_pair(pair_dir: Path) -> dict:
    pair_dir = Path(pair_dir)
    manifest_path = pair_dir / "metadata.json"
    manifest_bytes = manifest_path.read_bytes()

    def reject_constant(value):
        raise ValueError(f"metadata contains non-finite JSON number {value}")

    manifest = json.loads(manifest_bytes.decode("utf-8-sig"), parse_constant=reject_constant)
    if not isinstance(manifest, dict):
        raise ValueError("metadata must be an object")
    if manifest.get("complete") is not True or manifest.get("cancelled") is not False or manifest.get("failure") is not None:
        raise ValueError("capture is incomplete or cancelled; it cannot be compared")
    if _integer(manifest, "schemaVersion") != 1:
        raise ValueError("unsupported paired metadata schemaVersion")
    session = _integer(manifest, "sessionId")
    if _integer(manifest, "sampleRate") != _RATE or _integer(manifest, "channels") != 1 or manifest.get("encoding") != "PCM_S16LE":
        raise ValueError("metadata must describe 16-bit mono 44100 Hz PCM")
    preparation = _integer(manifest, "preparationFrames")
    original_count = _integer(manifest, "originalFrames", 1)
    corrected_count = _integer(manifest, "correctedFrames", 1)
    if original_count != corrected_count or _integer(manifest, "offeredPerformanceFrames", 1) != original_count:
        raise ValueError("original, corrected and offered performance frame counts differ")
    if _integer(manifest, "offeredPreparationFrames") != preparation:
        raise ValueError("preparation frame counts differ")
    if preparation + original_count > _MAX_FRAMES:
        raise ValueError("frame count exceeds the recorder's 180-second preparation plus performance limit")
    _, preparation_end = _frame_span(manifest, "preparation", preparation)
    performance_first, performance_end = _frame_span(manifest, "performance", original_count)
    if preparation_end is not None and preparation_end != performance_first:
        raise ValueError("preparation and performance physical frame spans are not contiguous")

    files = {}
    _, files["preparation"] = _read_wave(pair_dir / "preparation.wav", preparation, _pcm_binding(manifest, "preparation"), False)
    original, files["original"] = _read_wave(pair_dir / "original.wav", original_count, _pcm_binding(manifest, "original"), True)
    corrected, files["corrected"] = _read_wave(pair_dir / "corrected.wav", corrected_count, _pcm_binding(manifest, "corrected"), True)
    totals = [0, 0, 0, 0]
    windows = []
    for first in range(0, original_count, _WINDOW_FRAMES):
        end = min(first + _WINDOW_FRAMES, original_count)
        before = after = difference = changed = 0
        for index in range(first, end):
            a, b = original[index], corrected[index]
            before += a * a
            after += b * b
            difference += (a - b) ** 2
            changed += a != b
        for index, value in enumerate((before, after, difference, changed)):
            totals[index] += value
        windows.append({"start_frame": first, "end_frame": end,
                        **_metrics(before, after, difference, changed, end - first)})
    return {
        "schema_version": 1,
        "comparison": "same_capture_original_vs_corrected",
        "session_id": session,
        "sample_rate": _RATE,
        "manifest_sha256": sha256(manifest_bytes).hexdigest(),
        "frames": {"preparation": preparation, "original": original_count, "corrected": corrected_count},
        "performance_first_physical_frame": performance_first,
        "performance_end_physical_frame": performance_end,
        "duration_seconds": original_count / _RATE,
        "window_duration_seconds": _WINDOW_FRAMES / _RATE,
        "files": files,
        "metrics": _metrics(*totals, original_count),
        "windows": windows,
        "limitations": [
            "RMS changes describe the mixed captured signal, not isolated jangdan attenuation.",
            "The difference signal is original minus corrected; it is not independently verified echo.",
            "These measurements do not prove instrument preservation, scoring accuracy or a quality pass.",
            "Undefined decibels and ratios involving zero RMS are null.",
        ],
    }


def _check_output(pair_dir: Path, output: Path) -> None:
    for name in ("metadata.json", *(f"{name}.wav" for name in _NAMES)):
        source = pair_dir / name
        if output.resolve() == source.resolve() or (output.exists() and source.exists() and output.samefile(source)):
            raise ValueError("output must not overwrite or alias a source recording or manifest")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate and measure one complete same-input echo comparison.")
    parser.add_argument("--pair-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        _check_output(args.pair_dir, args.output)
        report = analyze_pair(args.pair_dir)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    except (OSError, ValueError, EOFError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    print(f"Wrote paired-signal measurements to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
