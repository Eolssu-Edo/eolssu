"""Independently compare common-score v1 with a decoded WAV/M4A waveform.

No renderer, compiled score, MIDI or render report supplies expected values.
This bounded check covers untransposed monophonic notes/rests/breaths, not
ornaments, changing tempo, audio quality, or human performance correctness.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess

import numpy as np
import soundfile as sf


YULS = "황 대 태 협 고 중 유 임 이 남 무 응".split()
PITCH_LIMIT_CENTS = 50.0
AAC_EDGE_SECONDS = 0.025  # Slightly over one 1024-sample AAC frame at 44.1kHz.


def require(condition, message):
    if not condition:
        raise ValueError(message)


def expected_intervals(source, bpm):
    require(source.get("schemaVersion") == 1, "Only common-score v1 is supported")
    require(source.get("provenance", {}).get("review") == "reviewed", "A reviewed source is required")
    require(math.isfinite(bpm) and bpm > 0, "A positive explicit source BPM is required")
    music = source["music"]
    require(music["bpmUnit"] in ("QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT"),
            "Unsupported beat unit")
    tpb = music["ticksPerBeat"]
    require(type(tpb) is int and tpb > 0, "ticksPerBeat must be a positive integer")
    hwang = music["hwangHz"]
    require(math.isfinite(hwang) and hwang > 0, "Invalid hwangHz")
    # Account independently for the documented integer MIDI tempo transport.
    quarters = 1.5 if music["bpmUnit"] == "DOTTED_QUARTER" else 1.0
    quarter_seconds = round(60_000_000 / (bpm * quarters)) / 1_000_000
    second_per_tick = quarter_seconds * quarters / tpb
    result, cursor, seen = [], 0, set()
    for event in source["events"]:
        eid = event["id"]
        require(eid not in seen, f"Duplicate event ID: {eid}")
        seen.add(eid)
        require(event["kind"] in ("NOTE", "REST"), f"{eid}: unsupported event")
        require(type(event["startTick"]) is int and event["startTick"] == cursor,
                f"{eid}: overlapping, unordered or implicit-gap events are unsupported")
        duration = event["durationTick"]
        require(type(duration) is int and duration > 0, f"{eid}: invalid duration")
        require(not event["sigimsae"], f"{eid}: ornaments are outside this verification scope")
        start, end = cursor * second_per_tick, (cursor + duration) * second_per_tick
        row = {"id": eid, "kind": event["kind"], "start": start, "end": end,
               "audible_end": end, "breath_seconds": 0.0}
        if event["kind"] == "NOTE":
            require(event["yul"] in YULS and type(event["octave"]) is int, f"{eid}: invalid pitch")
            row["hz"] = hwang * 2 ** ((YULS.index(event["yul"]) + 12 * event["octave"]) / 12)
            require(100 <= row["hz"] <= 3000, f"{eid}: outside the observer's 100..3000Hz range")
            if event["breathAfter"]:
                # Declared policy: 120ms silence + at most 50ms release,
                # together capped at half a note. CC72 has 127 release steps.
                budget = min(0.17, (end - start) / 2)
                release = math.floor(min(0.05, budget / 2) / 0.05 * 127 + 1e-9) / 127 * 0.05
                breath = 0.12 if budget >= 0.17 else budget - release
                row.update(audible_end=end - breath, breath_seconds=breath)
        else:
            require(event["yul"] is None and event["octave"] is None and not event["breathAfter"],
                    f"{eid}: rest carries pitch or breath")
        result.append(row)
        cursor += duration
    require(any(x["kind"] == "NOTE" for x in result), "At least one sounding note is required")
    return result, cursor * 60 / bpm / tpb, cursor * second_per_tick


def fundamental(signal, rate):
    """Normalized autocorrelation; earliest strong periodic peak, without target Hz."""
    x = np.asarray(signal, dtype=np.float64)
    if len(x) < rate * 0.02:
        return None, 0.0
    x = x - np.mean(x)
    if np.mean(x * x) < 1e-10:
        return None, 0.0
    size = 1 << (2 * len(x) - 1).bit_length()
    spectrum = np.fft.rfft(x, size)
    corr = np.fft.irfft(spectrum * spectrum.conjugate(), size)[:len(x)]
    squares = np.concatenate(([0.0], np.cumsum(x * x)))
    lags = np.arange(1, min(len(x) // 2, math.ceil(rate / 100)) + 1)
    norm = squares[len(x) - lags] + squares[-1] - squares[lags]
    values = 2 * corr[lags] / np.maximum(norm, 1e-20)
    first = max(1, math.floor(rate / 3000))
    peaks = [i for i in range(first, len(values) - 1)
             if values[i] > values[i - 1] and values[i] >= values[i + 1]]
    if not peaks:
        return None, 0.0
    best = max(float(values[i]) for i in peaks)
    if best < 0.75:
        return None, best
    i = next(i for i in peaks if values[i] >= max(0.75, best * 0.90))
    a, b, c = values[i - 1:i + 2]
    denominator = a - 2 * b + c
    delta = 0.5 * (a - c) / denominator if denominator else 0.0
    return float(rate / (lags[i] + delta)), float(values[i])


def rms(signal):
    return float(np.sqrt(np.mean(np.asarray(signal, dtype=np.float64) ** 2))) if len(signal) else 0.0


def inspect_waveform(source, bpm, audio, rate, *, lossy=False, container_seconds=None):
    require(rate == 44100 and audio.ndim == 1 and np.isfinite(audio).all(),
            "Expected finite 44.1kHz mono PCM")
    intervals, nominal, transported = expected_intervals(source, bpm)
    failures, notes, silences = [], [], []
    expected_seconds = transported + 0.55
    frames = round(expected_seconds * rate)
    padding = len(audio) - frames
    duration_ok = -2 <= padding <= (1025 if lossy else 2)
    if container_seconds is not None:
        duration_ok = duration_ok and abs(container_seconds - expected_seconds) <= 3 / rate
    if not duration_ok:
        failures.append("file-duration")
    if np.max(np.abs(audio), initial=0) >= 0.99:
        failures.append("clipping")

    def segment(start, end):
        return audio[max(0, round(start * rate)):max(0, round(end * rate))]

    edge = AAC_EDGE_SECONDS if lossy else 0.002
    for event in intervals:
        if event["kind"] == "REST":
            silences.append({"id": event["id"], "kind": "REST", "start": event["start"], "end": event["end"]})
            continue
        length = event["audible_end"] - event["start"]
        require(length >= 0.07, f"{event['id']}: note too short for this pitch observer")
        width = min(0.08, length * 0.28)
        windows = []
        for fraction in (0.30, 0.50, 0.70):
            center = event["start"] + length * fraction
            wave = segment(center - width / 2, center + width / 2)
            hz, periodicity = fundamental(wave, rate)
            windows.append({"time": center, "hz": hz, "periodicity": periodicity,
                            "cents": 1200 * math.log2(hz / event["hz"]) if hz else None})
        errors = [w["cents"] for w in windows if w["cents"] is not None]
        median = float(np.median(errors)) if errors else None
        pitch_ok = len(errors) >= 2 and abs(median) <= PITCH_LIMIT_CENTS
        body = rms(segment(event["start"] + length * .2, event["start"] + length * .8))
        early = rms(segment(event["start"] + min(.025, length * .1), event["start"] + min(.065, length * .35)))
        late = rms(segment(event["audible_end"] - min(.045, length * .25), event["audible_end"] - min(.01, length * .05)))
        active_ok = body > 1e-5 and early > body * .05 and late > body * .01
        # Locate energy support in 2ms bins. This is an envelope observation,
        # not a sample-exact tongue/onset detector, especially after AAC coding.
        wave = segment(event["start"], event["audible_end"])
        hop = round(.002 * rate)
        bins = [rms(wave[i:i + hop]) for i in range(0, len(wave), hop)]
        active = [i for i, energy in enumerate(bins) if energy > body * .02 and energy > 1e-6]
        onset = event["start"] + active[0] * hop / rate if active else None
        offset = min(event["audible_end"], event["start"] + (active[-1] + 1) * hop / rate) if active else None
        timing_limit = .025 + (AAC_EDGE_SECONDS if lossy else 0)
        support_ok = bool(active) and onset - event["start"] <= timing_limit and event["audible_end"] - offset <= timing_limit
        # Every common-score NOTE is a new articulation; the release is inside
        # its source slot. A short energy valley should remain at its onset.
        boundary = rms(segment(event["start"] - .001, event["start"] + .001))
        boundary_ratio = boundary / body if body else None
        boundary_ok = boundary_ratio is not None and boundary_ratio <= (.65 if lossy else .50)
        row = {**event, "median_error_cents": median, "windows": windows,
               "rms": body, "early_rms": early, "late_rms": late,
               "observed_active_start": onset, "observed_active_end": offset,
               "energy_support_passed": support_ok,
               "onset_energy_ratio": boundary_ratio, "pitch_passed": pitch_ok,
               "active_interval_passed": active_ok, "onset_boundary_passed": boundary_ok}
        notes.append(row)
        if not pitch_ok:
            failures.append(event["id"] + ":pitch")
        if not active_ok:
            failures.append(event["id"] + ":active-interval")
        if not support_ok:
            failures.append(event["id"] + ":energy-support")
        if not boundary_ok:
            failures.append(event["id"] + ":onset-boundary")
        if event["breath_seconds"]:
            silences.append({"id": event["id"], "kind": "BREATH", "start": event["audible_end"], "end": event["end"]})
    reference_rms = float(np.median([x["rms"] for x in notes]))
    for quiet in silences:
        start, end = quiet["start"] + edge, quiet["end"] - edge
        require(end > start, f"{quiet['id']}: silence too short for codec edge allowance")
        wave = segment(start, end)
        energy = rms(wave)
        peak = float(np.max(np.abs(wave), initial=0))
        # WAV's expected silence must be exact PCM zero. AAC is lossy; allow
        # 1% RMS (-40 dB amplitude) relative to sounding notes, away from edges.
        silence_ok = energy <= reference_rms * .01 if lossy else peak == 0
        quiet.update(checked_start=start, checked_end=end, rms=energy, peak=peak,
                     relative_rms=energy / reference_rms if reference_rms else None,
                     passed=silence_ok)
        if not silence_ok:
            failures.append(quiet["id"] + ":" + quiet["kind"].lower())
    return {"passed": not failures, "failures": failures,
            "source_nominal_seconds": nominal, "transported_score_seconds": transported,
            "expected_file_seconds": expected_seconds, "decoded_frames": len(audio),
            "decoded_seconds": len(audio) / rate, "container_seconds": container_seconds,
            "padding_frames": padding, "duration_passed": duration_ok,
            "policy": {"pitch_limit_cents": PITCH_LIMIT_CENTS, "codec_edge_seconds": edge,
                       "energy_edge_tolerance_seconds": .025 + (AAC_EDGE_SECONDS if lossy else 0),
                       "silence": "relative RMS <= 1%" if lossy else "exact PCM zero",
                       "pitch_estimator": "target-independent normalized autocorrelation"},
            "notes": notes, "silences": silences,
            "limits": ["Energy/F0 windows do not certify human performance or timbre.",
                       "Adjacent same-pitch notes are checked for an energy valley, not a human tongue gesture.",
                       "No ornaments, tempo changes, transposition, polyphony, or notes shorter than 70ms."]}


def run_command(args):
    result = subprocess.run([str(a) for a in args], capture_output=True, timeout=120,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    require(result.returncode == 0, result.stderr.decode("utf-8", errors="replace")[-1000:])
    return result.stdout


def decode_audio(path, ffmpeg=None, ffprobe=None):
    path = Path(path)
    if path.suffix.lower() == ".wav":
        wave, rate = sf.read(path, dtype="float64", always_2d=True)
        require(wave.shape[1] == 1, "Stereo audio is unsupported")
        return wave[:, 0], rate, False, len(wave) / rate
    require(path.suffix.lower() == ".m4a", "Use WAV or M4A")
    require(ffmpeg and ffprobe, "M4A verification requires explicit --ffmpeg and --ffprobe")
    probe = json.loads(run_command([ffprobe, "-v", "error", "-show_streams", "-of", "json", path]))
    require(len(probe["streams"]) == 1, "Expected one audio stream")
    stream = probe["streams"][0]
    require(stream.get("codec_name") == "aac" and stream.get("profile") == "LC"
            and stream.get("channels") == 1 and int(stream.get("sample_rate", 0)) == 44100,
            "Expected 44.1kHz mono AAC-LC")
    raw = run_command([ffmpeg, "-v", "error", "-nostdin", "-i", path,
                       "-map", "0:a:0", "-f", "f32le", "-c:a", "pcm_f32le", "pipe:1"])
    return np.frombuffer(raw, dtype="<f4"), 44100, True, float(stream["duration"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--score", required=True)
    parser.add_argument("--bpm", required=True, type=float, help="BPM in source music.bpmUnit")
    parser.add_argument("--audio", required=True)
    parser.add_argument("--ffmpeg")
    parser.add_argument("--ffprobe")
    parser.add_argument("--out", help="Optional JSON evidence path; otherwise stdout")
    args = parser.parse_args()
    try:
        payload = Path(args.score).read_bytes()
        source = json.loads(payload.decode("utf-8-sig"))
        wave, rate, lossy, duration = decode_audio(args.audio, args.ffmpeg, args.ffprobe)
        report = inspect_waveform(source, args.bpm, wave, rate, lossy=lossy, container_seconds=duration)
        report.update(source_sha256=hashlib.sha256(payload).hexdigest(),
                      audio_sha256=hashlib.sha256(Path(args.audio).read_bytes()).hexdigest(),
                      observer_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                      source=str(Path(args.score).resolve()), audio=str(Path(args.audio).resolve()), bpm=args.bpm)
    except (ValueError, OSError, KeyError, TypeError, subprocess.TimeoutExpired) as exc:
        report = {"passed": False, "unavailable": str(exc)}
    result = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    if args.out:
        out = Path(args.out)
        require(out.resolve() not in (Path(args.score).resolve(), Path(args.audio).resolve(), Path(__file__).resolve()),
                "Evidence path must not overwrite source, audio or observer")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(result, encoding="utf-8")
    else:
        print(result)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
