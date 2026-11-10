"""Compare AEC3 or frozen templates with a locally captured jangdan reference.

Offline experiment only; does not record, upload, or change the Android app.
Requires numpy/scipy, plus pywebrtc-audio==0.2.0 for AEC3. See the investigation
in docs/knowledge/frontend/android-jangdan-recording-echo.md for limitations.
"""

import argparse
import csv
import hashlib
import json
from importlib.metadata import version
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

RATE = 44100


def pcm16(path):
    payload = path.read_bytes()
    if len(payload) % 2:
        raise ValueError(f"Incomplete PCM16 sample: {path}")
    return np.frombuffer(payload, dtype="<i2").astype(np.float32) / 32768


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def clock_model(path, origin):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    valid = [r for r in rows if int(r["status"]) == 0 and RATE < int(r["hw_frame"]) < 18 * RATE]
    if len(valid) < 10:
        raise ValueError(f"Insufficient stable hardware timestamps: {path}")
    frames = np.array([int(r["hw_frame"]) for r in valid], dtype=np.float64)
    nanos = np.array([int(r["hw_nanos"]) - origin for r in valid], dtype=np.float64)
    slope, intercept = np.polyfit(frames, nanos, 1)
    residual = nanos - (frames * slope + intercept)
    if slope <= 0 or abs(1e9 / slope - RATE) > RATE * 0.001:
        raise ValueError(f"Unexpected sample clock or reset: {path}")
    if np.max(abs(residual)) > 2_000_000:
        raise ValueError(f"Hardware clock is not stable within 2 ms: {path}")
    return {
        "ns_per_frame": float(slope), "zero_ns_relative": float(intercept),
        "max_residual_frames": float(np.max(abs(residual)) / slope),
        "rows_used": len(valid),
    }


def align_reference(reference, capture_frames, shift):
    aligned = np.zeros(capture_frames, dtype=np.float32)
    src_start, dst_start = max(0, -shift), max(0, shift)
    count = min(len(reference) - src_start, capture_frames - dst_start)
    if count <= 0:
        raise ValueError("Reference and capture do not overlap")
    aligned[dst_start:dst_start + count] = reference[src_start:src_start + count]
    return aligned


def level_db(samples):
    return float(10 * np.log10(max(np.mean(samples.astype(np.float64) ** 2), 1e-20)))


def compare_windows(capture, output, events, shift):
    rows = []
    for event in events:
        if event["partition"] != "holdout":
            continue
        # Include the early attack; a raw-waveform correlation peak can be late.
        start = int(event["reference_frame"]) + shift - round(0.01 * RATE)
        end = int(event["reference_frame"]) + shift + int(event["asset_frames"]) + round(0.12 * RATE)
        if start < 0 or end > len(capture) or start >= end:
            raise ValueError("Incomplete holdout window")
        before, after = level_db(capture[start:end]), level_db(output[start:end])
        rows.append({"kind": event["kind"], "start_frame": start, "end_frame": end,
                     "before_dbfs": before, "after_dbfs": after,
                     "capture_rms_change_db": before - after})
    if not rows:
        raise ValueError("No holdout strikes")
    values = [row["capture_rms_change_db"] for row in rows]
    return {"median_db": float(np.median(values)), "worst_db": float(min(values)), "strikes": rows}


def frozen_template(capture, events, shift):
    """Fit only the initial calibration, then subtract a fixed per-clip waveform."""
    pre, post, pad = round(.045 * RATE), round(.48 * RATE), round(.005 * RATE)
    calibration = [e for e in events if e["partition"] == "calibration"]
    holdout = [e for e in events if e["partition"] == "holdout"]
    if not calibration or not holdout:
        raise ValueError("Separate calibration and holdout events are required")
    stop = max(e["reference_frame"] + shift + post for e in calibration) + RATE // 10
    if stop >= min(e["reference_frame"] + shift - pre for e in holdout):
        raise ValueError("Calibration overlaps holdout")
    training = capture[:stop].astype(np.float64)
    fit_filter = signal.butter(4, (900, 5500), fs=RATE, btype="bandpass", output="sos")
    output_filter = signal.butter(4, (90, 10000), fs=RATE, btype="bandpass", output="sos")
    fit, clean = signal.sosfiltfilt(fit_filter, training), signal.sosfiltfilt(output_filter, training)

    def match(reference, target):
        corr = signal.correlate(target, reference, mode="valid", method="fft")
        energy = np.r_[0, np.cumsum(target * target)]
        norm = np.sqrt(np.maximum(energy[len(reference):] - energy[:-len(reference)], 1e-15))
        scores = corr / np.maximum(norm * np.linalg.norm(reference), 1e-12)
        best = int(np.argmax(scores))
        return best - pad, float(scores[best])

    templates, quality = {}, {}
    for kind in sorted({e["kind"] for e in holdout}):
        group = [e for e in calibration if e["kind"] == kind]
        if len(group) < 3 or any(e["gain"] <= 0 for e in group):
            raise ValueError(f"At least three positive-gain calibration strikes required: {kind}")
        cuts = [e["reference_frame"] + shift - pre for e in group]
        begin, end = pre + round(.02 * RATE), pre + round(.42 * RATE)
        if min(cuts) < 0 or max(cuts) + pre + post > len(training):
            raise ValueError("Incomplete calibration waveform")
        references = [fit[i + begin:i + end] / e["gain"] for i, e in zip(cuts, group)]
        matches = [[match(ref, fit[i + begin - pad:i + end + pad]) for i in cuts]
                   for ref in references]
        best = int(np.argmax([np.median([score for _, score in row]) for row in matches]))
        phases = np.array([lag for lag, _ in matches[best]])
        phases -= round(float(np.median(phases)))
        template = np.median([clean[i + lag:i + lag + pre + post] / e["gain"]
                              for i, lag, e in zip(cuts, phases, group)], axis=0)
        template[:round(.02 * RATE)] *= np.linspace(0, 1, round(.02 * RATE))
        template[-round(.03 * RATE):] *= np.linspace(1, 0, round(.03 * RATE))
        templates[kind] = template
        quality[kind] = {"calibration_strikes": len(group), "alignment_samples": phases.tolist(),
                         "median_similarity": float(np.median([q for _, q in matches[best]]))}
    output = capture.astype(np.float64).copy()
    for event in holdout:
        start = event["reference_frame"] + shift - pre
        template = templates[event["kind"]]
        if start < 0 or start + len(template) > len(output):
            raise ValueError("Incomplete holdout waveform")
        output[start:start + len(template)] -= event["gain"] * template
    return output, {"training_end_sample": stop, "clips": quality}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_dir", type=Path, help="mic.s16le and capture/playback-timestamps.csv")
    parser.add_argument("reference", type=Path, help="Exact rendered 44100 Hz mono PCM16LE")
    parser.add_argument("timeline", type=Path, help="Reference metadata and calibration/holdout events")
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--method", choices=("aec3", "frozen-template"), default="aec3")
    args = parser.parse_args()
    timeline = json.loads(args.timeline.read_text(encoding="utf-8-sig"))
    if (timeline["rate"], timeline["channels"], timeline["format"]) != (RATE, 1, "PCM16_LE"):
        raise ValueError("Expected 44100 Hz mono PCM16LE")
    if digest(args.reference) != timeline["reference_sha256"].lower():
        raise ValueError("Reference SHA-256 differs from the playback timeline")
    microphone_path = args.capture_dir / "mic.s16le"
    capture, reference = pcm16(microphone_path), pcm16(args.reference)
    if len(reference) != timeline["frames"] or len(capture) < 18 * RATE:
        raise ValueError("Incomplete experiment recording")
    with (args.capture_dir / "capture-timestamps.csv").open(newline="") as stream:
        origin = int(next(csv.DictReader(stream))["hw_nanos"])
    clocks = {name: clock_model(args.capture_dir / f"{name}-timestamps.csv", origin)
              for name in ("capture", "playback")}
    # AudioRecord timestamp frames are not the latest app read's end position.
    # File sample 0 is assumed to be track frame 0, without dropped samples.
    shift = round((clocks["playback"]["zero_ns_relative"] - clocks["capture"]["zero_ns_relative"])
                  / clocks["capture"]["ns_per_frame"])
    reference = align_reference(reference, len(capture), shift)
    template_details = None
    if args.method == "aec3":
        from pywebrtc_audio import AudioProcessor
        if version("pywebrtc-audio") != "0.2.0":
            raise ValueError("This experiment was verified with pywebrtc-audio==0.2.0")
        processor = AudioProcessor(sample_rate=RATE, num_channels=1, echo_cancellation=True,
                                   noise_suppression=False, auto_gain_control=False,
                                   high_pass_filter=False, stream_delay_ms=0)
        output = processor.process(capture, reference)
    else:
        output, template_details = frozen_template(capture, timeline["events"], shift)
    if len(output) != len(capture) or not np.isfinite(output).all():
        raise ValueError("Processing changed sample count or returned nonfinite output")
    report = {
        "method": args.method, "sample_rate": RATE, "template": template_details,
        "pywebrtc_audio": version("pywebrtc-audio") if args.method == "aec3" else None,
        "microphone_sha256": digest(microphone_path), "reference_sha256": digest(args.reference),
        "capture_frames": len(capture), "reference_shift_frames": shift, "clocks": clocks,
        "capture_rail_samples": int(np.count_nonzero((capture <= -1) | (capture >= 32767 / 32768))),
        "aec_forces_highpass": args.method == "aec3",
        "processing_delay_compensated": False,
        "note": "RMS includes ambient sound. No live instrument preservation or scoring claim.",
        "output_peak": float(np.max(abs(output))),
        "holdout": compare_windows(capture, output, timeline["events"], shift),
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    wavfile.write(args.output_dir / f"{args.method}.wav", RATE, output.astype(np.float32))
    (args.output_dir / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"shift_frames": shift, "holdout_strikes": len(report["holdout"]["strikes"]),
                      "median_db": report["holdout"]["median_db"],
                      "worst_db": report["holdout"]["worst_db"]}, indent=2))


if __name__ == "__main__":
    main()
