"""Renderer subprocess for the internal service. All asset paths are server-owned."""
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import sys
from fractions import Fraction

import mido
import numpy as np
import soundfile as sf

from .__main__ import run
from .audio import resolve_encoder, write_audio
from .sampler import FS, CREDIT
from .score import require
from .score import compile_score, midi_tempo, PPQN
from .common_score import convert_common_score
from .midi import export_seconds
from .tempo_repeat import performed_points
from .authoring_tempo import prepared_input, preflight, TempoRejected
from data.src.audio.jangdan_audio import rhythm_plan

from .authoring_policy import SERVICE_POLICY

# Stable channel-10 note IDs distinguish the six recorded janggu strokes.
# They identify export events; a generic GM soundfont is not a janggu library.
DRUM_NOTES = {'덩': 35, '기': 37, '쿵': 36, '덕': 38, '다': 39, '더': 40}


def prepare_score(source, instrument):
    value = copy.deepcopy(source)
    value['score']['instrument'] = instrument.upper().replace('-', '_')
    return value


def jangdan_events(request, seconds, *, timing=None):
    music, jangdan = request['score']['music'], request['jangdan']
    # The supported accompaniment grid starts at zero and requires full cycles.
    # Convert score beat units explicitly; never use catalog default BPM.
    units={'QUARTER':Fraction(1),'DOTTED_QUARTER':Fraction(3,2)}
    require(music['bpmUnit'] in units and jangdan['bpmUnit'] in units, 'Unsupported beat unit')
    catalog_bpm=Fraction(request['bpm'])*units[music['bpmUnit']]/units[jangdan['bpmUnit']]
    if timing is not None:
        # Pattern positions are written quarters; melody's clock owns all tempo,
        # length marks and repeats. An initial rest never moves the rhythm origin.
        eighths = jangdan['patternCellEighths']
        require(type(eighths) is int and eighths > 0, 'Invalid pattern cell duration')
        quarter_bpm = Fraction(request['bpm']) * units[music['bpmUnit']]
        cycle, hits = rhythm_plan({'name': jangdan['name'], 'default_bpm': quarter_bpm,
            'bpm_unit': 'QUARTER', 'pattern': jangdan['pattern'], 'pattern_cell_eighths': eighths})
        expected_cycle = len(jangdan['pattern']['beats']) * Fraction(eighths, 2)
        factor = quarter_bpm / 60
        total = Fraction(sum(e['durationTick'] for e in request['score']['events']), music['ticksPerBeat']) * units[music['bpmUnit']]
        require(total % expected_cycle == 0, 'Incomplete accompaniment cycle')
        points = [(repeat * expected_cycle + at * factor, (stroke, gain))
                  for repeat in range(int(total / expected_cycle)) for at, stroke, gain in hits]
        return [(at, stroke, gain) for at, (stroke, gain) in performed_points(timing, points)]
    duration, events = rhythm_plan({'name': jangdan['name'], 'default_bpm': catalog_bpm,
        'bpm_unit': jangdan['bpmUnit'], 'pattern': jangdan['pattern'],
        **({'pattern_cell_eighths': jangdan['patternCellEighths']} if 'patternCellEighths' in jangdan else {})})
    cycle_seconds=float(duration)
    output = []
    for repeat in range(max(0, math.ceil(seconds / cycle_seconds))):
        for at, stroke, gain in events:
            onset = float(repeat*duration+at)
            if onset < seconds:
                output.append((onset, stroke, gain))
    return output


def add_jangdan(audio, midi_path, request, samples_directory, score_seconds, *, timing=None):
    midi = mido.MidiFile(midi_path)
    tempo = next(message.tempo for track in midi.tracks for message in track if message.type == 'set_tempo')
    # Use the score duration, excluding sample release, for repeated strokes.
    seconds = score_seconds
    events = jangdan_events(request, seconds, timing=timing)
    samples = load_janggu(samples_directory)
    messages = []
    for at, stroke, gain in events:
        offset = round(at * FS)
        sample = samples[stroke]
        length = min(len(sample), len(audio) - offset)
        if length > 0:
            audio[offset:offset + length] += sample[:length] * gain * .5
        tick = round(mido.second2tick(at, midi.ticks_per_beat, tempo))
        stop = round(mido.second2tick(min(seconds, at + .08), midi.ticks_per_beat, tempo))
        messages.extend([(tick, True, DRUM_NOTES[stroke], round(100 * gain)),
                         (max(tick + 1, stop), False, DRUM_NOTES[stroke], 0)])
    track = mido.MidiTrack()
    track.append(mido.MetaMessage('track_name', name='Janggu strokes', time=0))
    previous = 0
    for tick, on, note, velocity in sorted(messages):
        track.append(mido.Message('note_on' if on else 'note_off', channel=9, note=note,
                                  velocity=velocity, time=tick - previous))
        previous = tick
    midi.type = 1
    midi.tracks.append(track)
    midi.save(midi_path)


def load_janggu(directory):
    samples = {}
    for stroke, filename in {'덩':'deong','기':'gi','쿵':'kung','덕':'deok','다':'da','더':'deo'}.items():
        path = Path(directory) / f'{filename}.wav'
        require(path.stat().st_size <= 100000, 'Janggu hit exceeds limit')
        values, rate = sf.read(path, dtype='float32')
        require(rate == FS and values.ndim == 1 and 0 < len(values) <= FS
                and np.isfinite(values).all() and np.max(np.abs(values)) > 0, 'Invalid prepared janggu hit')
        samples[stroke] = values
    return samples


def render(directory):
    root = Path(directory)
    request = json.loads((root / 'request.json').read_text(encoding='utf-8'))
    preflight(request)
    source, encoded, _, expressions = prepared_input(request)
    path = root / 'source.json'
    path.write_text(encoded, encoding='utf-8')
    expression_path = None
    if expressions is not None:
        expression_path = root / 'expressions.json'
        expression_path.write_text(json.dumps(expressions, ensure_ascii=False), encoding='utf-8')
    instrument = request['instrument']
    out = root / 'rendered'
    manifest=run(path, os.environ['PLAYBACK_ASSETS_JSON'], out, [instrument], common_bpm=request['bpm'],
        audio_format='wav', notation_policy='educational-preview/v1', common_expressions=expression_path, synthesis_extension=SERVICE_POLICY['synthesis_extension'])
    audio, rate = sf.read(out / f'{instrument}.wav', dtype='float32')
    require(rate == FS and audio.ndim == 1 and 0 < len(audio) <= 600 * FS, 'Export duration exceeds limit')
    shutil.copyfile(out / f'{instrument}.mid', root / 'score.mid')
    if request['withJangdan']:
        add_jangdan(audio, root / 'score.mid', request, os.environ['PLAYBACK_JANGGU_SAMPLES'],
                    manifest['common_score']['midi_score_seconds'], timing=manifest['common_score']['timingMap'])
    peak = float(np.max(np.abs(audio)))
    require(math.isfinite(peak) and peak > 0, 'Silent/nonfinite render')
    credit = CREDIT + ' 원음 유지 합성이며 실제 악기 연주 가능 음역을 뜻하지 않습니다.' + (' 장구 음원 출처: 국립국악원(공공누리 제1유형).' if request['withJangdan'] else '')
    final_audio = root / 'audio.m4a'
    write_audio(audio * min(1, .85 / peak), final_audio, resolve_encoder(), credit=credit)
    if source_hash := request.get('performanceSourceSha256'):
        performance = json.loads((out / 'performance.common.json').read_text(encoding='utf-8'))
        with final_audio.open('rb') as encoded:
            performance['audioSha256'] = hashlib.file_digest(encoded, 'sha256').hexdigest()
        # The rendering instrument changes timbre. The written identity comes from the committed input.
        performance['sourceSha256'] = source_hash
        performance['instrument'] = request['score']['score']['instrument']
        (root / 'performance.common.json').write_text(
            json.dumps(performance, ensure_ascii=False, allow_nan=False, separators=(',', ':')), encoding='utf-8')
    (root / 'result.json').write_text(json.dumps({'durationMs': math.ceil(len(audio) * 1000 / FS)}), encoding='utf-8')


def estimate(directory):
    root=Path(directory)
    request=json.loads((root/'request.json').read_text(encoding='utf-8'))
    try:
        perf, mapping = preflight(request)
        if request['withJangdan']:
            jangdan_events(request, mapping['performanceTimeline']['durationMs'] / 1000, timing=mapping['timingMap'])
        result = {'durationMs': math.ceil(export_seconds(perf) * 1000)}
    except TempoRejected as error:
        result = error.result
    except (ValueError,KeyError,TypeError):
        result={'errorCode':'INVALID_REQUEST'}
    (root/'result.json').write_text(json.dumps(result),encoding='utf-8')


if __name__ == '__main__':
    try:
        estimate(sys.argv[1]) if '--estimate' in sys.argv[2:] else render(sys.argv[1])
    except TempoRejected as error:
        (Path(sys.argv[1]) / 'result.json').write_text(json.dumps(error.result), encoding='utf-8')
