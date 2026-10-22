"""Offline measurements for the digital daegeum collection, not score labels."""

import argparse
import csv
import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np

from analyze_basic_tones import read_wave, sounding_regions, yin


def load_audio(path):
    """Keep native rate/channels. MP3 decoding is optional and never resamples."""
    if path.suffix.lower() == ".wav":
        samples, rate, digest = read_wave(path)
        return samples, rate, digest, "RIFF decoder: PCM16/float32"
    if path.suffix.lower() != ".mp3":
        raise ValueError("Only WAV and MP3 are supported")
    import miniaudio
    decoded = miniaudio.mp3_read_file_f32(str(path))
    samples = np.asarray(decoded.samples, dtype=np.float64).reshape(-1, decoded.nchannels)
    return (samples, decoded.sample_rate, hashlib.sha256(path.read_bytes()).hexdigest().upper(),
            f"miniaudio {miniaudio.__version__}: mp3_read_file_f32; no rate conversion")


def modulation(hz, hop):
    """Describe a continuous pitch contour. Rate is a spectral candidate only."""
    hz = np.asarray(hz, dtype=float)
    if len(hz) * hop < 2 or not np.isfinite(hz).all() or np.any(hz <= 0):
        return {"rate_hz": None, "detrended_span_cents": None, "band_power_share": None}
    cents = 1200 * np.log2(hz / np.median(hz))
    t = np.arange(len(cents)) * hop
    residual = cents - np.polyval(np.polyfit(t, cents, 1), t)
    span = float(np.percentile(residual, 90) - np.percentile(residual, 10))
    spectrum = abs(np.fft.rfft(residual * np.hanning(len(residual)))) ** 2
    freqs = np.fft.rfftfreq(len(residual), hop)
    band = (freqs >= 2) & (freqs <= 10)
    total = spectrum[freqs >= 0.5].sum()
    share = float(spectrum[band].sum() / max(total, 1e-20))
    rate = float(freqs[np.flatnonzero(band)[np.argmax(spectrum[band])]])
    return {"rate_hz": round(rate, 3) if span >= 10 and share >= .5 else None,
            "detrended_span_cents": round(span, 2), "band_power_share": round(share, 3)}


def summarize_pitch(values):
    if not len(values):
        return {"median_hz": None, "p10_hz": None, "p90_hz": None, "span_cents": None}
    lo, mid, hi = np.percentile(values, [10, 50, 90])
    return {"median_hz": round(float(mid), 3), "p10_hz": round(float(lo), 3),
            "p90_hz": round(float(hi), 3), "span_cents": round(float(1200*np.log2(hi/lo)), 2)}


def measure(samples, rate):
    if samples.ndim != 2 or not samples.shape[1] or not np.isfinite(samples).all():
        raise ValueError("Expected finite samples shaped (frames, channels)")
    mono = samples.mean(axis=1)
    window, hop = round(rate * 4096 / 48000), round(rate * .02)
    if len(mono) < window:
        raise ValueError("Audio is shorter than one analysis window")
    starts = np.arange(0, len(mono) - window + 1, hop)
    rms = np.array([np.sqrt(np.mean(mono[s:s+window] ** 2)) for s in starts])
    gate = max(1e-4, float(np.percentile(rms, 95))*.025)
    frames, pitch = [], []
    for s, level in zip(starts, rms):
        hz, periodicity = yin(mono[s:s+window], rate) if level >= gate else (None, None)
        accepted = hz is not None and periodicity >= .85
        pitch.append(hz if accepted else np.nan)
        frames.append({"seconds": round(float((s+window/2)/rate), 6),
                       "rms": float(level), "f0_hz": hz, "periodicity": periodicity,
                       "accepted_for_summary": bool(accepted)})
    pitch = np.asarray(pitch)
    regions = []
    for i, (a, b) in enumerate(sounding_regions(rms >= gate, hop/rate), 1):
        begin, end = starts[a]/rate, min(len(mono)/rate, (starts[b-1]+window)/rate)
        trim = min(.2, (end-begin)/5)
        indices = np.array([j for j in range(a, b)
                            if begin+trim <= frames[j]["seconds"] <= end-trim])
        valid = indices[np.isfinite(pitch[indices])] if len(indices) else indices
        values = pitch[valid]
        row = {"region_id": f"region-{i:03d}", "start_seconds": round(float(begin), 4),
               "end_seconds": round(float(end), 4), "valid_pitch_ratio": round(len(valid)/max(1, len(indices)), 4),
               **summarize_pitch(values), "rate_hz": None, "detrended_span_cents": None,
               "band_power_share": None, "rms_end_minus_start_db": None}
        # Never bridge missing pitch frames to invent a modulation frequency.
        if len(valid) and len(valid) == len(indices):
            row.update(modulation(values, hop/rate))
        if len(indices) >= 10:
            n = max(1, len(indices)//5)
            row["rms_end_minus_start_db"] = round(float(20*np.log10(
                max(np.median(rms[indices[-n:]]), 1e-12) / max(np.median(rms[indices[:n]]), 1e-12))), 2)
        regions.append(row)
    # A separate 20ms RMS envelope preserves short articulations better than the F0 window.
    ew, eh = round(rate*.02), round(rate*.01)
    es = np.arange(0, len(mono)-ew+1, eh)
    er = np.array([np.sqrt(np.mean(mono[s:s+ew]**2)) for s in es])
    eg = max(1e-4, float(np.percentile(er, 95))*.025)
    attacks = [{"attack_id": f"attack-{i:04d}", "start_seconds": round(float(es[a]/rate), 4),
                "end_seconds": round(float((es[b-1]+ew)/rate), 4)}
               for i, (a, b) in enumerate(sounding_regions(er >= eg, eh/rate, .05, .03), 1)]
    envelope = [{"seconds": round(float((s+ew/2)/rate), 6), "rms": round(float(r), 8)}
                for s, r in zip(es, er)]
    accepted = pitch[np.isfinite(pitch)]
    avg_channel_power = float(np.mean(samples**2))
    summary = {"duration_seconds": round(len(mono)/rate, 6), "sample_rate_hz": rate,
               "channels": samples.shape[1], "frames": len(frames),
               "active_frame_ratio": round(float(np.mean(rms >= gate)), 4),
               "accepted_pitch_ratio_of_active": round(len(accepted)/max(1, int(sum(rms >= gate))), 4),
               "peak_absolute": round(float(np.max(abs(samples))), 6),
               "samples_over_one_ratio": round(float(np.mean(abs(samples) > 1)), 8),
               "mono_power_to_channel_power": round(float(np.mean(mono**2))/max(avg_channel_power, 1e-20), 6),
               "rms_gate": gate, "envelope_gate": eg, "regions": len(regions),
               "articulation_candidates": len(attacks), "pitch": summarize_pitch(accepted)}
    return frames, regions, attacks, envelope, summary


def reference_matches(regions, references, labels):
    """Relative matching to this instrument's measured basic tones; not labels."""
    rows = []
    for region in regions:
        row = dict(region)
        row.update(reference_tone_id=None, reference_distance_cents=None,
                   inferred_yulmyeong=None, reference_label_status=None,
                   reference_register=None, reference_display_label=None,
                   reference_pitch_name=None, reference_fingering_patterns=None,
                   mapping_status="unmatched", eligible_for_scoring=False)
        if region["median_hz"] and region["span_cents"] <= 100 and region["valid_pitch_ratio"] >= .8:
            distances = [1200*np.log2(region["median_hz"]/r["median_hz"]) for r in references]
            idx = int(np.argmin(abs(np.asarray(distances))))
            if abs(distances[idx]) <= 80:
                ref = references[idx]
                label = labels.get(ref["segment_id"], {})
                row.update(reference_tone_id=ref["segment_id"], reference_distance_cents=round(float(distances[idx]), 2),
                           inferred_yulmyeong=label.get("yulmyeong"), reference_label_status=label.get("status", "unconfirmed"),
                           reference_register=label.get("register"), reference_display_label=label.get("displayLabel"),
                           reference_pitch_name=ref.get("western_note_a440_comparison"),
                           reference_fingering_patterns=" / ".join(r["holes"] for r in label.get("referenceFingeringCandidates", [])) or None,
                           mapping_status="acoustic_reference_candidate")
        rows.append(row)
    return rows


def write_csv(path, rows):
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]) if rows else ["empty"])
        writer.writeheader()
        writer.writerows(rows)


def run(manifest_path, output_dir, only=None):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    summaries, failures = [], []
    started = time.perf_counter()
    for sample in manifest["samples"]:
        if only and sample["id"] != only:
            continue
        tick = time.perf_counter()
        try:
            audio, rate, digest, decoder = load_audio(Path(sample["rawPath"]))
            if digest != sample["sha256"].upper():
                raise ValueError("SHA-256 mismatch")
            frames, regions, attacks, envelope, summary = measure(audio, rate)
            summary.update(id=sample["id"], instrument=sample["instrumentVariant"], title=sample["title"],
                           category=sample["category"], raw_path=sample["rawPath"], sha256=digest, decoder=decoder)
            ref = manifest["references"][sample["instrumentVariant"]]
            refs = json.loads(Path(ref["analysisPath"]).read_text(encoding="utf-8"))["segments"]
            labels = json.loads(Path(ref["mappingPath"]).read_text(encoding="utf-8"))["segments"] if ref.get("mappingPath") else []
            matches = reference_matches(regions, refs, {r["segmentId"]: r for r in labels})
            summary["reference_candidates"] = sum(r["reference_tone_id"] is not None for r in matches)
            summary["modulation_candidates"] = sum(r["rate_hz"] is not None for r in regions)
            summary["elapsed_seconds"] = round(time.perf_counter()-tick, 3)
            summary["status"] = "measured_unreviewed"
            folder = output_dir/sample["id"]
            folder.mkdir(parents=True, exist_ok=True)
            for filename, rows in [("frames.csv", frames), ("regions.csv", matches),
                                   ("articulations.csv", attacks), ("envelope.csv", envelope)]:
                write_csv(folder/filename, rows)
            detail = {"summary": summary, "regions": matches, "articulations": attacks}
            (folder/"analysis.json").write_text(json.dumps(detail, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
            summaries.append(summary)
            print(f"OK {sample['id']} {summary['duration_seconds']}s; regions={len(regions)}", flush=True)
        except (ValueError, OSError, ImportError) as exc:
            failures.append({"id": sample["id"], "error": str(exc)})
            print(f"FAILED {sample['id']}: {exc}", flush=True)
    result = {"analysisVersion": 1, "task": manifest["task"], "sourceManifest": manifest_path.as_posix(),
              "manifestSha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
              "referenceArtifacts": {variant: {name: {"path": path, "sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest()}
                                                for name, path in ref.items() if path}
                                     for variant, ref in manifest["references"].items()},
              "python": platform.python_version(), "numpy": np.__version__,
              "elapsedSeconds": round(time.perf_counter()-started, 3),
              "settings": {"nativeRate": True, "channelMapping": "arithmetic mean", "f0WindowSeconds": 4096/48000,
                           "f0HopSeconds": .02, "fminHz": 100, "fmaxHz": 2500, "periodicityMinimum": .85,
                           "phraseGapSeconds": .2, "phraseMinimumSeconds": .3,
                           "envelopeWindowSeconds": .02, "envelopeHopSeconds": .01,
                           "articulationGapSeconds": .05, "articulationMinimumSeconds": .03,
                           "referenceSpanMaximumCents": 100, "referenceDistanceMaximumCents": 80,
                           "referenceValidRatioMinimum": .8, "modulationBandHz": [2, 10],
                           "modulationMinimumSeconds": 2, "modulationBandPowerShareMinimum": .5,
                           "modulationSpanMinimumCents": 10},
              "limitations": ["Automatic measurements, not expert/score ground truth",
                              "RMS regions can contain multiple notes; no beat or note alignment",
                              "Reference matches are nearest measured basic tones, not independent yulmyeong proof",
                              "Modulation candidates may reflect melodic changes or tracking errors",
                              "Strongest partials, breath and overlapping sounds can confuse monophonic YIN",
                              "No loudness normalization, resampling or pitch correction; parameters are exploratory"],
              "samples": summaries, "failures": failures}
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir/"summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--only", help="Validate one source ID before the collection run")
    args = parser.parse_args()
    result = run(args.manifest, args.output_dir, args.only)
    raise SystemExit(bool(result["failures"]))
