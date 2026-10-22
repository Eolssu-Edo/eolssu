"""Recheck suspected repeated notes against raw WAV, including excluded gaps."""

import json
from pathlib import Path

import numpy as np

from analyze_basic_tones import read_wave
from prepare_sanjo_assets import ROOT, DOCS, short_pitch_frames, write_audio


CASES = [
    {"id": "deep-vibrato", "sourceId": "ngc-sanjo-2552", "context": [0, 9],
     "regions": [[.6, 3.465291666666667], [3.74, 6.225291666666666]],
     "revisedRegion": [.6, 7.1], "decision": "merge_false_split_and_restore_release"},
    {"id": "noniro", "sourceId": "ngc-sanjo-2553", "context": [107, 124],
     "regions": [[110.22, 114.04529166666667], [117.84, 121.30529166666666]],
     "decision": "retain_two_separate_same_pitch_utterances"},
]


def interval_stats(frames, bounds, trim=0):
    rows = [f for f in frames if bounds[0] + trim <= f["seconds"] <= bounds[1] - trim]
    values = [f["f0_hz"] for f in rows if f["accepted"]]
    return {"boundsSeconds": bounds, "trimSeconds": trim, "frames": len(rows),
            "acceptedFrames": len(values), "acceptedRatio": len(values) / max(1, len(rows)),
            "f0P10MedianP90Hz": np.percentile(values, [10, 50, 90]).tolist() if values else None,
            "rmsP10MedianP90": np.percentile([f["rms"] for f in rows], [10, 50, 90]).tolist(),
            "periodicityMedian": float(np.median([f["periodicity"] for f in rows]))}


def fft_peak(mono, rate, center, duration, low, high):
    data = mono[round((center-duration/2)*rate):round((center+duration/2)*rate)]
    spectrum = np.abs(np.fft.rfft((data-data.mean()) * np.hanning(len(data)), n=262144))
    frequencies = np.fft.rfftfreq(262144, 1/rate)
    band = np.flatnonzero((frequencies >= low) & (frequencies <= high))
    return {"centerSeconds": center, "windowSeconds": duration, "searchHz": [low, high],
            "peakHz": float(frequencies[band[np.argmax(spectrum[band])]])}


def run():
    output = ROOT / "data/processed/instruments/daegeum/sanjo/repeat-audit-v1"
    output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((DOCS / "collection-manifest.json").read_text(encoding="utf-8"))
    report = {"task": "S15P31E102-52", "settings": {"source": "raw WAV, no cached pitch frames",
              "channelMapping": "arithmetic mean", "windowSeconds": .04, "hopSeconds": .01,
              "yinRangeHz": [180, 1800], "minimumPeriodicityExclusive": .90,
              "rmsGate": None, "fft": "Hann window; 262144-point FFT; no pitch correction"}, "cases": []}
    for case in CASES:
        sample = next(s for s in manifest["samples"] if s["id"] == case["sourceId"])
        samples, rate, sha = read_wave(ROOT / sample["rawPath"])
        if sha.lower() != sample["sha256"].lower():
            raise ValueError("Unexpected original source version")
        first, last = [round(v*rate) for v in case["context"]]
        context = samples[first:last]
        frames = short_pitch_frames(context, rate)
        for frame in frames:
            frame["seconds"] = round(frame["seconds"] + first/rate, 6)
        gap = [case["regions"][0][1], case["regions"][1][0]]
        gap_stats = interval_stats(frames, gap)
        region_stats = [interval_stats(frames, r, .2) for r in case["regions"]]
        a, b = [r["f0P10MedianP90Hz"][1] for r in region_stats]
        result = {**case, "sourcePath": sample["rawPath"], "sourceSha256": sha,
                  "sampleRateHz": rate, "regionsMeasured": region_stats, "gapMeasured": gap_stats,
                  "pairDifferenceCents": float(1200*np.log2(b/a))}
        if case["id"] == "deep-vibrato":
            result["revisedMeasured"] = interval_stats(frames, case["revisedRegion"], .2)
            result["restoredReleaseMeasured"] = interval_stats(frames, [6.225291666666666, 7.03])
            result["fftCrosscheck"] = [fft_peak(samples.mean(axis=1), rate, c, .08, 200, 400)
                                      for c in [3.4, 3.5, 3.6, 3.7, 3.8]]
            # This would be a false negative if the old excluded interval were silence.
            assert gap_stats["acceptedRatio"] > .95 and gap_stats["periodicityMedian"] > .95
        else:
            result["fftCrosscheck"] = [fft_peak(samples.mean(axis=1), rate, sum(r)/2, .75, 900, 1300)
                                      for r in case["regions"]]
            sounding_rms = min(r["rmsP10MedianP90"][1] for r in region_stats)
            assert gap_stats["rmsP10MedianP90"][1] < sounding_rms / 100
            assert abs(result["pairDifferenceCents"]) < 5
        result["contextAudio"] = {"file": f"{case['id']}-original-context.wav",
                                  "sourceStartFrame": first, "sourceEndFrameExclusive": last,
                                  **write_audio(output / f"{case['id']}-original-context.wav", context, rate)}
        report["cases"].append(result)
    (output / "audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    run()
