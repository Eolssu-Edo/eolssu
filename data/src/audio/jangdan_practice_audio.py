"""Prepare recorded janggu hits and five pitch-preserving gudgeori rolls."""

import argparse
from array import array
from fractions import Fraction
from hashlib import sha256
import json
from pathlib import Path
import subprocess
import sys
import wave

from .jangdan_audio import RATE, SAMPLES, SOURCE, read_stroke


GUTGEORI_BPMS = (59, 79, 99, 109, 119)
ROLL_FILE = 'Janggu_4_2.wav'
ROLL_SHA256 = 'ec1d6d318834871d61d003b2590c2b056318008784ad5be108195e95659edd30'
ROLL_START_MS = 204
ROLL_END_MS = 2650
HIT_NAMES = {'덩': 'deong', '기': 'gi', '쿵': 'kung', '덕': 'deok', '다': 'da', '더': 'deo'}


def gutgeori_roll_frames(bpm):
    if bpm not in GUTGEORI_BPMS:
        raise ValueError('Expected one of the five rounded QUARTER BPM values')
    return round(Fraction(60 * RATE, bpm))


def atempo_factors(ratio):
    ratio = Fraction(ratio)
    if ratio <= 0:
        raise ValueError('A positive tempo ratio is required')
    factors = []
    while ratio > 2:
        factors.append(Fraction(2))
        ratio /= 2
    while ratio < Fraction(1, 2):
        factors.append(Fraction(1, 2))
        ratio *= 2
    return factors + [ratio]


def write_hit(path, values):
    # Small edge fades prevent a cut waveform click; the original pitch is retained.
    fade_in, fade_out = RATE // 1000, RATE // 100
    pcm = array('h', (
        round(value * 32767 * min(1, i / fade_in, (len(values) - 1 - i) / fade_out))
        for i, value in enumerate(values)
    ))
    if sys.byteorder != 'little':
        pcm.byteswap()
    with wave.open(str(path), 'wb') as out:
        out.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        out.writeframes(pcm.tobytes())


def inspect_asset(path):
    with wave.open(str(path), 'rb') as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (1, 2, RATE):
            raise ValueError(f'Unexpected PCM format: {path}')
        frames = source.getnframes()
        pcm = array('h', source.readframes(frames))
    if sys.byteorder != 'little':
        pcm.byteswap()
    peak = max(map(abs, pcm), default=0)
    if not 100 < peak < 32767:
        raise ValueError(f'Silent or clipped output: {path}')
    # Check that the scheduled beginning is audible instead of retaining source padding.
    if max(map(abs, pcm[:RATE // 20]), default=0) < 100:
        raise ValueError(f'Leading silence hides the attack: {path}')
    return {'file': path.name, 'sha256': sha256(path.read_bytes()).hexdigest(),
            'frames': frames, 'durationMs': frames * 1000 / RATE, 'peakPcm16': peak}


def encode_m4a(path, info, ffmpeg):
    encoded = path.with_suffix('.m4a')
    subprocess.run([ffmpeg, '-v', 'error', '-nostdin', '-n', '-i', str(path),
                    '-c:a', 'aac', '-b:a', '96k', '-movie_timescale', str(RATE),
                    '-movflags', '+faststart', str(encoded)], check=True)
    payload = encoded.read_bytes()
    info.update({'file': encoded.name, 'pcmWavSha256': info['sha256'],
                 'sha256': sha256(payload).hexdigest(), 'bytes': len(payload)})
    path.unlink()
    return info


def prepare(samples, output, ffmpeg, baseline_manifest):
    baseline = json.loads(baseline_manifest.read_text(encoding='utf-8'))
    for stroke, (filename, _, _, _) in SAMPLES.items():
        digest = sha256((samples / filename).read_bytes()).hexdigest()
        if digest != baseline['sources'][stroke]['sha256']:
            raise ValueError(f'Original sample hash mismatch: {filename}')
    roll = samples / ROLL_FILE
    if sha256(roll.read_bytes()).hexdigest() != ROLL_SHA256:
        raise ValueError('Expected the verified National Gugak Center deroreoreo/medium recording')
    output.mkdir(parents=True, exist_ok=False)
    manifest = {
        'source': SOURCE, 'license': 'KOGL-1', 'sampleRate': RATE,
        'channels': 1, 'pcmBits': 16, 'codec': 'AAC-LC', 'targetBitrate': 96000,
        'rollSource': {'file': ROLL_FILE, 'mntnSeq': 2292, 'division': '더러러러',
                       'strength': '중', 'sha256': ROLL_SHA256,
                       'cropStartMs': ROLL_START_MS, 'cropEndMs': ROLL_END_MS},
        'processing': 'FFmpeg atempo chain with each factor <= 2; no pitch shifting; exact frame trim and edge fades',
        'musicalListeningReview': 'pending',
        'ffmpegVersion': subprocess.check_output([ffmpeg, '-version'], text=True).splitlines()[0],
        'assets': [],
    }
    for stroke, (filename, identifier, onset, tail) in SAMPLES.items():
        path = output / (HIT_NAMES[stroke] + '.wav')
        write_hit(path, read_stroke(samples / filename, onset, tail))
        info = inspect_asset(path)
        info.update({'kind': 'hit', 'stroke': stroke, 'sourceFile': filename, 'mntnSeq': identifier,
                     'sourceSha256': baseline['sources'][stroke]['sha256'],
                     'cropStartMs': onset - 2, 'cropDurationMs': tail})
        if stroke == '기':
            info['sourceDivision'] = '기덕'
            info['edit'] = 'Only the first grace attack is extracted from the recorded gideok'
        manifest['assets'].append(encode_m4a(path, info, ffmpeg))
    for bpm in GUTGEORI_BPMS:
        frames = gutgeori_roll_frames(bpm)
        ratio = Fraction(ROLL_END_MS - ROLL_START_MS, 1000) / Fraction(frames, RATE)
        factors = atempo_factors(ratio)
        filters = [f'atrim=start={ROLL_START_MS / 1000}:end={ROLL_END_MS / 1000}',
                   'asetpts=PTS-STARTPTS', 'pan=mono|c0=0.5*c0+0.5*c1']
        filters += [f'atempo={float(factor):.12f}' for factor in factors]
        path = output / f'gutgeori_roll_{bpm}.wav'
        raw = subprocess.check_output([ffmpeg, '-v', 'error', '-nostdin', '-i', str(roll),
                                       '-af', ','.join(filters), '-ar', str(RATE), '-ac', '1',
                                       '-f', 's16le', '-c:a', 'pcm_s16le', 'pipe:1'])
        compressed = array('h', raw)
        if sys.byteorder != 'little':
            compressed.byteswap()
        uncorrected_frames = len(compressed)
        trimmed_peak = max(map(abs, compressed[frames:]), default=0)
        # Keep the correction visible; do not silently pass by padding an empty conversion.
        if not compressed or abs(uncorrected_frames - frames) > RATE // 10 or trimmed_peak > 1000:
            raise ValueError(f'Unexpected atempo length or audible discarded tail at BPM {bpm}')
        values = [sample / 32768 for sample in compressed[:frames]]
        values += [0.0] * (frames - len(values))
        write_hit(path, values)
        info = inspect_asset(path)
        if info['frames'] != frames:
            raise ValueError('A prepared roll has an incorrect musical duration')
        info.update({'kind': 'roll', 'jangdan': '굿거리', 'songBpm': bpm, 'bpmUnit': 'QUARTER',
                     'musicalDurationFrames': frames, 'atempoFactors': [str(f) for f in factors],
                     'uncorrectedFrames': uncorrected_frames,
                     'paddedFrames': max(0, frames - uncorrected_frames),
                     'trimmedFrames': max(0, uncorrected_frames - frames),
                     'discardedTailPeakPcm16': trimmed_peak})
        manifest['assets'].append(encode_m4a(path, info, ffmpeg))
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--samples', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--baseline-manifest', type=Path,
                        default=Path('docs/knowledge/data/jangdan-preview-audio-manifest.json'))
    args = parser.parse_args()
    prepare(args.samples, args.output, args.ffmpeg, args.baseline_manifest)


if __name__ == '__main__':
    main()
