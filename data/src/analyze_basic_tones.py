"""Offline exploratory segmentation and YIN pitch estimates; not reference labels.

Requires Python 3.10+ and NumPy. See data/README.md for invocation.
"""

import argparse
import csv
import hashlib
import json
import platform
import struct
import time
from pathlib import Path

import numpy as np


def read_wave(path):
    raw = path.read_bytes()
    if raw[:4] != b"RIFF" or raw[8:12] != b"WAVE":
        raise ValueError("Expected a RIFF/WAVE file")
    if struct.unpack_from("<I", raw, 4)[0] + 8 != len(raw):
        raise ValueError("RIFF size mismatch")
    pos, fmt, payload = 12, None, None
    while pos + 8 <= len(raw):
        name, size = struct.unpack_from("<4sI", raw, pos)
        chunk = raw[pos + 8:pos + 8 + size]
        if len(chunk) != size:
            raise ValueError("Truncated WAVE chunk")
        if name == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", chunk)
        elif name == b"data":
            payload = chunk
        pos += 8 + size + (size % 2)
    if fmt is None or payload is None:
        raise ValueError("Missing fmt/data chunk")
    code, channels, rate, _, align, bits = fmt
    if code == 3 and bits == 32:
        samples = np.frombuffer(payload, dtype="<f4").astype(np.float64)
    elif code == 1 and bits == 16:
        samples = np.frombuffer(payload, dtype="<i2").astype(np.float64) / 32768
    else:
        raise ValueError(f"Unsupported WAVE encoding: {code}, {bits}")
    if align != channels * bits // 8 or len(payload) % align:
        raise ValueError("Invalid channel alignment")
    samples = samples.reshape(-1, channels)
    if not np.isfinite(samples).all():
        raise ValueError("Nonfinite audio samples")
    return samples, rate, hashlib.sha256(raw).hexdigest().upper()


def analysis_mono(samples, rate):
    if rate != 48000:
        raise ValueError("This experiment expects 48000 Hz input")
    return samples.mean(axis=1), rate


def yin(frame, rate, fmin=100, fmax=2500):
    """First CMND trough below 0.15; reject candidates with CMND > 0.25."""
    x = frame - frame.mean()
    n = len(x)
    lo, hi = max(2, int(rate / fmax)), min(n // 2, int(rate / fmin))
    fft_size = 1 << (2 * n - 1).bit_length()
    spectrum = np.fft.rfft(x, n=fft_size)
    corr = np.fft.irfft(spectrum * spectrum.conj(), n=fft_size)[:hi + 1]
    sums = np.concatenate(([0.0], np.cumsum(x * x)))
    lags = np.arange(1, hi + 1)
    diff = np.maximum(0, sums[n - lags] + sums[n] - sums[lags] - 2 * corr[lags])
    diff /= n - lags
    cmnd = np.ones(hi + 1)
    cmnd[1:] = diff * lags / np.maximum(np.cumsum(diff), 1e-20)
    tau = lo
    while tau < hi:
        if cmnd[tau] < 0.15:
            while tau < hi and cmnd[tau + 1] < cmnd[tau]:
                tau += 1
            break
        tau += 1
    if tau == hi:
        tau = int(np.argmin(cmnd[lo:hi + 1])) + lo
    periodicity = float(1 - cmnd[tau])
    if cmnd[tau] > 0.25 or tau in (lo, hi):
        return None, periodicity
    a, b, c = cmnd[tau - 1:tau + 2]
    denom = a - 2 * b + c
    delta = float(np.clip(0.5 * (a - c) / denom, -0.5, 0.5)) if denom else 0
    return float(rate / (tau + delta)), periodicity


def sounding_regions(active, hop_seconds, gap_seconds=0.20, minimum_seconds=0.30):
    indices = np.flatnonzero(active)
    if not len(indices):
        return []
    cuts = np.flatnonzero(np.diff(indices) * hop_seconds > gap_seconds) + 1
    return [(int(g[0]), int(g[-1]) + 1) for g in np.split(indices, cuts)
            if (g[-1] - g[0] + 1) * hop_seconds >= minimum_seconds]


def analyze(samples, rate):
    mono, sr = analysis_mono(samples, rate)
    window, hop = 4096, 960
    if len(mono) < window:
        raise ValueError("Audio is shorter than one analysis window (4096 samples)")
    starts = np.arange(0, len(mono) - window + 1, hop)
    rms = np.array([np.sqrt(np.mean(mono[s:s + window] ** 2)) for s in starts])
    gate = max(1e-4, float(np.percentile(rms, 95)) * 0.025)
    frames = []
    for s, level in zip(starts, rms):
        hz, periodicity = yin(mono[s:s + window], sr) if level >= gate else (None, None)
        frames.append({"center_seconds": round(float((s + window / 2) / sr), 6),
                       "rms": float(level), "f0_hz": hz, "periodicity": periodicity})
    regions = sounding_regions(rms >= gate, hop / sr)
    segments = []
    for i, (a, b) in enumerate(regions, 1):
        begin = float(starts[a] / sr)
        end = min(len(mono) / sr, float((starts[b - 1] + window) / sr))
        # Exclude attacks/releases; retain pitch deviations instead of correcting them.
        inner = [f for f in frames[a:b] if begin + 0.20 <= f["center_seconds"] <= end - 0.20]
        valid = [f for f in inner if f["f0_hz"] is not None and f["periodicity"] >= 0.85]
        row = {"segment_id": f"tone-{i:02d}", "start_seconds": round(begin, 4),
               "end_seconds": round(end, 4), "duration_seconds": round(end - begin, 4),
               "interior_frames": len(inner), "valid_frames": len(valid),
               "valid_ratio": round(len(valid) / max(len(inner), 1), 4),
               "median_hz": None, "p10_hz": None, "p90_hz": None,
               "spread_p10_p90_cents": None, "median_periodicity": None,
               "western_note_a440_comparison": None, "cents_from_a440_comparison": None,
               "center_spectrum_peak_hz": None,
               "yulmyeong": None, "register": None, "review_status": "unreviewed"}
        if valid:
            hz = np.array([f["f0_hz"] for f in valid])
            p10, median, p90 = np.percentile(hz, [10, 50, 90])
            midi = 69 + 12 * np.log2(median / 440)
            nearest = round(float(midi))
            note = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"][nearest % 12]
            row.update(median_hz=round(float(median), 3), p10_hz=round(float(p10), 3),
                       p90_hz=round(float(p90), 3),
                       spread_p10_p90_cents=round(float(1200 * np.log2(p90 / p10)), 2),
                       median_periodicity=round(float(np.median([f["periodicity"] for f in valid])), 4),
                       western_note_a440_comparison=f"{note}{nearest // 12 - 1}",
                       cents_from_a440_comparison=round(float(100 * (midi - nearest)), 2))
        center_start = max(0, int((begin + end - 1) / 2 * sr))
        center = mono[center_start:center_start + sr]
        if len(center) == sr:
            # Independent method and original rate; strongest spectral peak need
            # not be the fundamental. This checks gross octave discrepancies only.
            power = np.abs(np.fft.rfft((center - center.mean()) * np.hanning(len(center)), n=262144))
            low, high = int(100 * 262144 / sr), int(5000 * 262144 / sr)
            peak = int(np.argmax(power[low:high])) + low
            row["center_spectrum_peak_hz"] = round(peak * sr / 262144, 3)
        segments.append(row)
    settings = {"analysis_rate_hz": sr, "channel_mapping": "arithmetic mean of all input channels",
                "resampling": "none; retain original 48000 Hz for high-register lag resolution",
                "window_samples": window, "hop_samples": hop, "rms_gate": gate,
                "rms_gate_rule": "max(0.0001, p95(frame_rms)*0.025)",
                "fmin_hz": 100, "fmax_hz": 2500, "yin_threshold": 0.15,
                "minimum_periodicity_for_summary": 0.85,
                "merge_gap_seconds": 0.20, "minimum_region_seconds": 0.30,
                "interior_trim_each_end_seconds": 0.20,
                "spectrum_crosscheck": "central 1 second; Hann; 262144-point FFT; strongest peak 100-5000 Hz",
                "western_comparison_only": "12-TET A4=440 Hz; not adopted tuning or yulmyeong"}
    return frames, segments, settings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    started = time.perf_counter()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    if len(manifest["samples"]) != 1:
        raise ValueError("Select a manifest with exactly one sample")
    sample = manifest["samples"][0]
    source = Path(sample["rawPath"])
    samples, rate, digest = read_wave(source)
    if digest != sample["sha256"].upper():
        raise ValueError("Source SHA-256 does not match manifest")
    if rate != sample["sampleRateHz"] or samples.shape[1] != sample["channels"]:
        raise ValueError("Source format does not match manifest")
    frames, segments, settings = analyze(samples, rate)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, rows in [("frames.csv", frames), ("segments.csv", segments)]:
        with (args.output_dir / name).open("w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=list(rows[0]) if rows else ["segment_id"])
            writer.writeheader()
            writer.writerows(rows)
    result = {"analysis_version": 1, "status": "automatic_estimate_unreviewed",
              "source_id": sample["id"], "source_path": source.as_posix(), "sha256": digest,
              "source_url": manifest["source"]["url"],
              "publisher": manifest["source"]["publisher"],
              "license": manifest["source"]["licenseObserved"],
              "attribution_draft": manifest["source"]["attributionDraft"],
              "source_duration_seconds": len(samples) / rate,
              "input_peak_absolute": float(np.max(np.abs(samples))),
              "python": platform.python_version(), "numpy": np.__version__,
              "settings": settings, "frame_count": len(frames), "segments": segments,
              "elapsed_seconds": round(time.perf_counter() - started, 3),
              "limitations": ["RMS boundaries include analysis-window uncertainty and breaths/noise",
                              "Sustained regions may contain multiple notes; no score alignment",
                              "YIN periodicity is a heuristic, not a calibrated correctness probability",
                              "No listening, expert yulmyeong/register/fingering labels or ground-truth F0"]}
    (args.output_dir / "analysis.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
