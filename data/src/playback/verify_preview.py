"""Check preview PCM against hand-authored expectations, never renderer output.

Usage: python -m data.src.playback.verify_preview --expect expectations.json
       --audio preview.wav [--out evidence.json]

Expectations use schema preview-waveform-check/v1, duration_seconds and one or
more pitches/silences/boundaries/sweeps/vibratos/levels/attacks arrays. Each check has an
id. Spans are absolute [start,end] seconds chosen from the fixture's declared
policy. Pitches use MIDI at A=440; boundaries use at/from_midi/to_midi; sweeps
use early/late spans and delta_cents [min,max]. Vibratos compare equal-length
span/reference_span windows at hz, amplitude_cents [min,max] and a minimum
added amplitude. Levels compare span/reference_span RMS with ratio [min,max].
Attacks compare a 2ms energy valley at `at` against a reference_span. Named
preview-waveform-cases/v1 fixtures select shared + case checks with --case.
Optional pitchOverrides replace only literal MIDI fields of existing check IDs.

This is a bounded signal observer, not an expert-performance or timbre review.
Keep expectations authored separately from generated IR, MIDI and source maps.
"""
import argparse
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path
from subprocess import TimeoutExpired

import numpy as np

from .verify_common import decode_audio, fundamental, require, rms


KINDS = ("pitches", "silences", "boundaries", "sweeps", "vibratos", "levels", "attacks")
PITCH_CENTS = 50
BOUNDARY_SECONDS = .030


def _number(value, name):
    require(type(value) in (int, float) and math.isfinite(value), f"{name}: finite number required")
    return float(value)


def _range(value, name):
    require(isinstance(value, list) and len(value) == 2, f"{name}: [minimum,maximum] required")
    low, high = [_number(x, name) for x in value]
    require(low <= high, f"{name}: minimum exceeds maximum")
    return low, high


def _hz(midi):
    require(type(midi) is int and 0 <= midi <= 127, "integer MIDI pitch required")
    hz = 440 * 2 ** ((midi - 69) / 12)
    require(100 <= hz <= 3000, "Observer supports 100..3000Hz")
    return hz


def _amplitude(times, cents, hz):
    design = np.column_stack((np.ones(len(times)), times,
                              np.sin(2 * np.pi * hz * times), np.cos(2 * np.pi * hz * times)))
    fitted = np.linalg.lstsq(design, cents, rcond=None)[0]
    return float(np.hypot(fitted[-2], fitted[-1]))


def _spectral_peak(signal, rate):
    """Target-independent dominant spectral component; diagnostic, not F0."""
    if len(signal) < 2:
        return None
    size = 1 << (8 * len(signal) - 1).bit_length()
    spectrum = np.abs(np.fft.rfft((signal - np.mean(signal)) * np.hanning(len(signal)), size))
    frequencies = np.fft.rfftfreq(size, 1 / rate)
    candidates = np.flatnonzero((frequencies >= 100) & (frequencies <= 3000))
    peak = int(candidates[np.argmax(spectrum[candidates])])
    if spectrum[peak] <= 1e-12:
        return None
    a, b, c = np.log(np.maximum(spectrum[peak - 1:peak + 2], 1e-30))
    denominator = a - 2 * b + c
    shift = .5 * (a - c) / denominator if denominator else 0
    return float((peak + shift) * rate / size)


def select_expectation(payload, case=None):
    """A fixture may share literal checks across named, hand-authored cases."""
    if payload.get("schema") == "preview-waveform-cases/v1":
        require(case in payload.get("cases", {}), "Select a named expectation --case")
        shared, selected = payload["shared"], payload["cases"][case]
        require(isinstance(shared, dict) and isinstance(selected, dict), "Shared expectation and case must be objects")
        require(set(selected) <= {*KINDS, "pitchOverrides"}, "Cases may only append checks or override literal pitches")
        require(all(isinstance(value.get(kind, []), list) for value in (shared, selected) for kind in KINDS),
                "Check groups must be arrays")
        result = deepcopy({**shared, **{kind: shared.get(kind, []) + selected.get(kind, []) for kind in KINDS}})
        overrides = selected.get("pitchOverrides", {})
        require(isinstance(overrides, dict), "pitchOverrides must be an object keyed by check ID")
        targets = {}
        for kind in KINDS:
            for check in result[kind]:
                require(isinstance(check, dict) and isinstance(check.get("id"), str) and check["id"], "Check ID required")
                require(check["id"] not in targets, "Unique check id required")
                targets[check["id"]] = (kind, check)
        for identifier, values in overrides.items():
            require(isinstance(identifier, str) and identifier in targets, "Unknown pitch override check ID")
            kind, check = targets[identifier]
            allowed = {"pitches": {"midi"}, "boundaries": {"from_midi", "to_midi"}}.get(kind, set())
            require(isinstance(values, dict) and values and set(values) <= allowed & set(check),
                    "Pitch overrides may only replace MIDI fields of pitches/boundaries")
            require(all(type(value) is int and 0 <= value <= 127 for value in values.values()),
                    "Pitch overrides require integer MIDI values in 0..127")
            check.update(values)
        return result
    require(case is None, "--case requires preview-waveform-cases/v1")
    return payload


def inspect_preview(expected, audio, rate, *, lossy=False, container_seconds=None):
    require(isinstance(expected, dict) and expected.get("schema") == "preview-waveform-check/v1",
            "Expected preview-waveform-check/v1")
    require(not set(expected) - {"schema", "description", "duration_seconds", *KINDS}, "Unknown expectation field")
    require(all(isinstance(expected.get(kind, []), list) for kind in KINDS), "Check groups must be arrays")
    require(any(expected.get(kind) for kind in KINDS), "At least one check is required")
    require(rate == 44100 and audio.ndim == 1 and len(audio) and np.isfinite(audio).all(), "Expected finite mono 44.1kHz PCM")
    duration = _number(expected.get("duration_seconds"), "duration_seconds")
    require(duration > 0, "Positive duration required")
    padding = len(audio) - round(duration * rate)
    failures, checks, seen = [], [], set()
    if not -2 <= padding <= (1025 if lossy else 2):
        failures.append("file-duration")
    if container_seconds is not None and abs(container_seconds - duration) > 3 / rate:
        failures.append("container-duration")
    if float(np.max(np.abs(audio))) >= .99:
        failures.append("clipping")

    def span(value):
        start, end = _range(value, "span")
        require(0 <= start < end <= duration + 2 / rate, "span outside expected audio")
        return start, end

    def wave(value):
        start, end = span(value)
        return audio[round(start * rate):round(end * rate)]

    def track(value, width=.025, hop=.010):
        start, end = span(value)
        require(end - start >= .020 - 1e-9, "Pitch span needs at least 20ms")
        first, last = round(start * rate), round(end * rate)
        width_frames = min(round(width * rate), last - first)
        count = max(1, (last - first - width_frames) // round(hop * rate) + 1)
        centers = np.linspace(first + width_frames / 2, last - width_frames / 2, count)
        times = centers / rate
        values = []
        for center in centers:
            chunk = audio[round(center - width_frames / 2):round(center + width_frames / 2)]
            hz, _ = fundamental(chunk, rate)
            values.append(hz if hz else np.nan)
        return times, np.array(values)

    def record(check, kind, passed, **observed):
        identifier = check.get("id")
        require(isinstance(identifier, str) and identifier and identifier not in seen, "Unique check id required")
        seen.add(identifier)
        checks.append({"id": identifier, "kind": kind, "passed": bool(passed), **observed})
        if not passed:
            failures.append(f"{identifier}:{kind}")

    for check in expected.get("pitches", []):
        target = _hz(check["midi"])
        times, values = track(check["span"])
        valid = values[np.isfinite(values)]
        cents = 1200 * np.log2(valid / target)
        passed = len(valid) >= .9 * len(values) and len(valid) > 0 and np.all(np.abs(cents) <= PITCH_CENTS)
        diagnostics = []
        if not passed:
            start, end = span(check["span"])
            width = min(round(.025 * rate), round(end * rate) - round(start * rate))
            for time in times:
                center = time * rate
                chunk = audio[round(center - width / 2):round(center + width / 2)]
                hz, periodicity = fundamental(chunk, rate)
                diagnostics.append({"time": float(time), "acf_hz": hz, "periodicity": periodicity,
                                    "dominant_spectral_hz": _spectral_peak(chunk, rate)})
        record(check, "pitch", passed, target_hz=target, valid_windows=len(valid), windows=len(values),
               median_cents=float(np.median(cents)) if len(cents) else None,
               maximum_absolute_cents=float(np.max(np.abs(cents))) if len(cents) else None,
               diagnostic_windows=diagnostics)

    sounding = audio[np.abs(audio) > 1e-6]
    reference_level = rms(sounding)
    for check in expected.get("silences", []):
        samples = wave(check["span"])
        peak, level = float(np.max(np.abs(samples), initial=0)), rms(samples)
        passed = level <= reference_level * .01 if lossy else peak == 0
        record(check, "silence", passed, rms=level, peak=peak)

    for check in expected.get("boundaries", []):
        at = _number(check["at"], "boundary time")
        before, after = _hz(check["from_midi"]), _hz(check["to_midi"])
        require(before != after and .09 <= at <= duration - .09, "Boundary needs distinct pitches and 90ms context")
        times, values = track([at - .09, at + .09], hop=.002)
        candidates, prior = [], None
        for time, hz in zip(times, values):
            if not math.isfinite(hz):
                continue
            if abs(1200 * math.log2(hz / before)) <= PITCH_CENTS:
                prior = "before"
            elif abs(1200 * math.log2(hz / after)) <= PITCH_CENTS:
                if prior == "before":
                    candidates.append(float(time))
                prior = "after"
        observed = min(candidates, key=lambda t: abs(t - at)) if candidates else None
        record(check, "boundary", observed is not None and abs(observed - at) <= BOUNDARY_SECONDS,
               expected_seconds=at, observed_seconds=observed,
               error_seconds=observed - at if observed is not None else None)

    for check in expected.get("sweeps", []):
        _, early = track(check["early"])
        _, late = track(check["late"])
        valid = np.isfinite(early).all() and np.isfinite(late).all()
        delta = float(1200 * np.log2(np.median(late) / np.median(early))) if valid else None
        low, high = _range(check["delta_cents"], "delta_cents")
        record(check, "sweep", delta is not None and low <= delta <= high, observed_delta_cents=delta)

    for check in expected.get("vibratos", []):
        start, end = span(check["span"])
        a, b = span(check["reference_span"])
        require(end - start >= .5 and abs((end - start) - (b - a)) < 1e-6,
                "Vibrato/reference spans must have equal lengths >=500ms")
        hz = _number(check["hz"], "vibrato hz")
        require(.5 <= hz <= 10, "Vibrato frequency must be .5..10Hz")
        times, values = track(check["span"])
        _, reference = track(check["reference_span"])
        valid = np.isfinite(values) & np.isfinite(reference)
        enough = int(np.count_nonzero(valid)) >= .9 * len(values)
        amplitude = base = added = dominant = None
        if enough:
            times = times[valid] - start
            cents = 1200 * np.log2(values[valid])
            baseline = 1200 * np.log2(reference[valid])
            difference = cents - baseline
            amplitude, base = _amplitude(times, cents, hz), _amplitude(times, baseline, hz)
            added = _amplitude(times, difference, hz)
            dominant = max(np.linspace(.5, 10, 191), key=lambda f: _amplitude(times, difference, f))
        low, high = _range(check["amplitude_cents"], "amplitude_cents")
        minimum = _number(check["minimum_added_cents"], "minimum_added_cents")
        require(0 < minimum and 0 < low <= high, "Vibrato amplitudes must be positive")
        passed = enough and low <= added <= high and added >= minimum and abs(float(dominant) - hz) <= .3
        record(check, "vibrato", passed, amplitude_cents=amplitude, reference_amplitude_cents=base,
               added_amplitude_cents=added, dominant_added_hz=float(dominant) if dominant is not None else None)

    for check in expected.get("levels", []):
        base, level = rms(wave(check["reference_span"])), rms(wave(check["span"]))
        low, high = _range(check["ratio"], "ratio")
        require(0 < low <= high, "Level ratios must be positive")
        ratio = level / base if base > 1e-6 else None
        record(check, "level", ratio is not None and low <= ratio <= high, ratio=ratio, rms=level, reference_rms=base)

    for check in expected.get("attacks", []):
        at = _number(check["at"], "attack time")
        reference = rms(wave(check["reference_span"]))
        boundary = rms(wave([at - .001, at + .001]))
        ratio = boundary / reference if reference > 1e-6 else None
        record(check, "attack", ratio is not None and ratio <= (.65 if lossy else .50),
               boundary_rms=boundary, reference_rms=reference, ratio=ratio)

    return {"passed": not failures, "failures": failures, "checks": checks,
            "duration_seconds": len(audio) / rate, "padding_frames": padding,
            "policy": {"pitch_limit_cents": PITCH_CENTS, "pitch_window_seconds": .025,
                       "boundary_tolerance_seconds": BOUNDARY_SECONDS,
                       "lossy_silence_rms_ratio": .01},
            "limits": ["Only hand-authored time spans are checked; no renderer artifact supplies expectations.",
                       "Sub-20ms sounds, human technique and timbre quality are outside scope.",
                       "A spectral peak is diagnostic only; it never overrides a failed periodic F0 check.",
                       "Vibrato checks added motion against the fixture's plain note, not acoustic authenticity."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expect", required=True)
    parser.add_argument("--case", help="Named case in a shared hand-authored fixture")
    parser.add_argument("--audio", required=True)
    parser.add_argument("--out")
    parser.add_argument("--ffmpeg")
    parser.add_argument("--ffprobe")
    args = parser.parse_args()
    try:
        payload = Path(args.expect).read_bytes()
        wave, rate, lossy, duration = decode_audio(args.audio, args.ffmpeg, args.ffprobe)
        expected = select_expectation(json.loads(payload.decode("utf-8-sig")), args.case)
        report = inspect_preview(expected, wave, rate,
                                 lossy=lossy, container_seconds=duration)
        report.update(case=args.case, expectation_sha256=hashlib.sha256(payload).hexdigest(),
                      audio_sha256=hashlib.sha256(Path(args.audio).read_bytes()).hexdigest(),
                      observer_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                      f0_observer_sha256=hashlib.sha256(Path(__file__).with_name("verify_common.py").read_bytes()).hexdigest())
    except (ValueError, OSError, KeyError, TypeError, TimeoutExpired) as exc:
        report = {"passed": False, "unavailable": str(exc)}
    result = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    if args.out:
        out = Path(args.out)
        require(out.resolve() not in (Path(args.expect).resolve(), Path(args.audio).resolve(), Path(__file__).resolve(),
                                     Path(__file__).with_name("verify_common.py").resolve()),
                "Evidence must not overwrite inputs or observer")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(result, encoding="utf-8")
    else:
        print(result)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
