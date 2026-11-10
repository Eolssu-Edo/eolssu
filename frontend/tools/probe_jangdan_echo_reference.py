"""Measure frozen playback-reference subtraction with a simulated microphone path."""

from __future__ import annotations

import argparse
from pathlib import Path
import wave

import numpy as np


RATE = 44_100
TAPS = 256
DELAY = 3_673


def read_pcm(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as source:
        if (source.getframerate(), source.getnchannels(), source.getsampwidth()) != (RATE, 1, 2):
            raise ValueError(f"Expected 44.1 kHz mono PCM16: {path}")
        return np.frombuffer(source.readframes(source.getnframes()), dtype="<i2").astype(float) / 32768


def render(clips: dict[str, np.ndarray], strikes: list[tuple[float, str, float]]) -> np.ndarray:
    pcm = np.zeros(2 * RATE)
    for seconds, clip, gain in strikes:
        samples = clips[clip]
        first = round(seconds * RATE)
        pcm[first:first + samples.size] += samples * gain
    return pcm


def delayed_echo(playback: np.ndarray, impulse: np.ndarray, delay: int) -> np.ndarray:
    return np.pad(np.convolve(playback, impulse), (delay, 0))[:playback.size]


def fit_reference(playback: np.ndarray, capture: np.ndarray) -> tuple[int, np.ndarray]:
    fft_size = 1 << (playback.size + capture.size - 2).bit_length()
    correlation = np.fft.irfft(
        np.fft.rfft(capture, fft_size) * np.fft.rfft(playback, fft_size).conj(), fft_size
    )
    first, end = round(0.06 * RATE), round(0.12 * RATE)
    delay = first + int(np.argmax(np.abs(correlation[first:end]))) - 32
    aligned = capture[delay:]
    windows = np.lib.stride_tricks.sliding_window_view(np.pad(playback, (TAPS - 1, 0)), TAPS)
    design = np.ascontiguousarray(windows[:aligned.size:2, ::-1])
    gram = design.T @ design
    ridge = 1e-4 * np.trace(gram) / TAPS
    return delay, np.linalg.solve(gram + ridge * np.eye(TAPS), design.T @ aligned[::2])


def energy_db(signal: np.ndarray) -> float:
    return 10 * np.log10(max(float(np.mean(signal**2)), 1e-15))


def near_end_note(size: int, onset: int) -> np.ndarray:
    dry = np.zeros(size)
    time = np.arange(round(0.6 * RATE)) / RATE
    phase = 2 * np.pi * 523.25 * time
    envelope = np.minimum(time / 0.025, 1) * np.minimum((0.6 - time) / 0.05, 1)
    dry[onset:onset + time.size] = 0.025 * envelope * (np.sin(phase) + 0.35 * np.sin(2 * phase))
    return dry


def estimate_note(signal: np.ndarray, expected_start: int) -> tuple[float, float]:
    hop = RATE // 200
    bins = signal[:signal.size // hop * hop].reshape(-1, hop)
    onset_ms = int(np.flatnonzero(np.mean(bins**2, axis=1) > 0.008**2)[0]) * hop * 1000 / RATE
    steady = signal[expected_start + RATE // 10:expected_start + RATE // 3]
    nfft = 131_072
    spectrum = np.abs(np.fft.rfft(steady * np.hanning(steady.size), nfft))
    band_start, band_end = round(400 * nfft / RATE), round(650 * nfft / RATE)
    peak = band_start + int(np.argmax(spectrum[band_start:band_end]))
    return onset_ms, peak * RATE / nfft


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture_dir", type=Path, help="Directory with decoded deong, kung and deok WAVs")
    args = parser.parse_args()
    clips = {name: read_pcm(args.fixture_dir / f"{name}.wav") for name in ("deong", "kung", "deok")}
    rng = np.random.default_rng(695)
    impulse = np.zeros(TAPS - 32)
    impulse[[0, 27, 78, 149]] = [0.11, -0.047, 0.024, 0.013]
    calibration = render(clips, [(0.09, "deong", 0.65), (0.51, "kung", 0.51), (0.81, "deok", 0.44)])
    calibration_capture = delayed_echo(calibration, impulse, DELAY) + rng.normal(0, 0.0002, calibration.size)
    delay, coefficients = fit_reference(calibration, calibration_capture)

    playback = render(clips, [(0.34, "kung", 0.65), (0.65, "deok", 0.30), (0.89, "deong", 0.54)])
    note_start = round(0.34 * RATE) + DELAY
    dry = near_end_note(playback.size, note_start)
    echo = delayed_echo(playback, impulse, DELAY)
    noise = rng.normal(0, 0.0002, playback.size)
    estimate = delayed_echo(playback, coefficients, delay)
    cleaned = dry + echo + noise - estimate
    reduction = energy_db(echo + noise) - energy_db(cleaned - dry)
    dry_onset, dry_pitch = estimate_note(dry, note_start)
    clean_onset, clean_pitch = estimate_note(cleaned, note_start)
    shifted = delayed_echo(playback, impulse, DELAY + 32) + noise
    shifted_reduction = energy_db(shifted) - energy_db(shifted - estimate)

    print(f"Simulated frozen filter onset: {delay} frames, true direct path: {DELAY} frames")
    print(f"Simulated held-out bleed reduction (unchanged path): {reduction:.2f} dB")
    print(f"Dry vs recovered spectral peak and RMS onset: {dry_pitch:.2f}/{clean_pitch:.2f} Hz, {dry_onset:.2f}/{clean_onset:.2f} ms")
    print(f"Untracked +32-frame delay, bleed reduction: {shifted_reduction:.2f} dB")
    if reduction < 15 or abs(dry_pitch - clean_pitch) > 1 or abs(dry_onset - clean_onset) > 5:
        raise SystemExit("The unchanged-path simulation did not meet its reduction or note limits")
    if shifted_reduction >= reduction - 10:
        raise SystemExit("The shifted-delay control did not expose timing sensitivity")


if __name__ == "__main__":
    main()
