"""Sample-free preflight over the same compiled performance used by rendering."""
import copy
import hashlib
import json
import math
from fractions import Fraction

from .common_score import convert_common_score
from .sampler import FS
from .authoring_policy import SERVICE_POLICY
from .score import MIDI_TEMPO_MAX, PPQN, compile_score, require
from .tempo_repeat import MidiResolutionError


class TempoRejected(ValueError):
    def __init__(self, code, details):
        super().__init__(code)
        self.result = {'errorCode': code, 'details': details}


def prepared_input(request):
    original = request['score']
    canonical = lambda value: json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
    original_hash = request.get('performanceSourceSha256') or hashlib.sha256(json.dumps(original, ensure_ascii=False, allow_nan=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    source = copy.deepcopy(original)
    source['score']['instrument'] = request['instrument'].upper().replace('-', '_')
    encoded = canonical(source)
    digest = hashlib.sha256(encoded.encode()).hexdigest()
    expressions = copy.deepcopy(request.get('expressions'))
    if expressions is not None:
        require(expressions.get('sourceSha256') == original_hash, 'Expression source mismatch')
        expressions['sourceSha256'] = digest
        if plan := expressions.get('performancePlan'):
            require(plan.get('input_sha256') == original_hash, 'Performance plan source mismatch')
            plan['input_sha256'] = digest
            plan['schema'] = f"{request['instrument']}-performance/v1"
    return source, encoded, digest, expressions


def preflight(request):
    source, _, digest, expressions = prepared_input(request)
    unit = source['music']['bpmUnit']
    ratio = Fraction(3, 2) if unit == 'DOTTED_QUARTER' else Fraction(1)
    minimum = math.ceil(60_000_000 / MIDI_TEMPO_MAX / ratio)
    maximum = math.floor(60_000_000 / ratio)
    details = {'bpmUnit': unit, 'minimumBpm': minimum, 'maximumBpm': maximum}
    if not minimum <= request['bpm'] <= maximum:
        raise TempoRejected('TEMPO_UNRENDERABLE', {**details, 'reason': 'MIDI_TEMPO_RANGE'})
    try:
        score, mapping = convert_common_score(source, bpm=request['bpm'], instrument=request['instrument'],
            input_sha256=digest, notation_policy='educational-preview/v1', expressions=expressions)
    except MidiResolutionError as error:
        # Scaling the base and numeric cues together preserves their ratio and
        # cannot repair a collapsed fixed-MIDI-grid note. The plan must change.
        raise TempoRejected('TEMPO_UNRENDERABLE', {'reason': 'MIDI_NOTE_TOO_SHORT', 'bpmUnit': unit,
            'eventIds': error.event_ids[:50], 'minimumNoteTicks': 1, 'ticksPerQuarter': PPQN,
            'adjustmentTarget': 'TEMPO_PLAN'}) from error
    # Include the encoded release and MIDI export tail, exactly as write_midi.
    length = sum((Fraction(n['duration']) for n in score['notes']), Fraction()) * mapping['midi_tempo_us'] / 1_000_000
    sec_tick = mapping['midi_tempo_us'] / 1_000_000 / PPQN
    tail = (round(.05 / sec_tick) + round(.5 / sec_tick)) * sec_tick
    if length + tail > 600:
        raise TempoRejected('EXPORT_TOO_LONG', {**details, 'reason': 'DURATION_LIMIT',
            'minimumBpm': max(minimum, math.ceil(request['bpm'] * float(length) / (600 - tail))),
            'maximumDurationMs': 600000})
    performance = compile_score(score, request['instrument'], max_duration_seconds=600, synthesis_extension=SERVICE_POLICY['synthesis_extension'])
    # A single-frame attack is zero in the sampler. Require two final PCM frames;
    # this is representability, not an assertion that a person can play the note.
    too_short = []
    for event in performance['events']:
        duration = (event['end_tick'] - event['start_tick']) * sec_tick
        maximum = min(maximum, math.floor(request['bpm'] * duration * FS / 2))
        if round(event['end_tick'] * sec_tick * FS) - round(event['start_tick'] * sec_tick * FS) < 2:
            too_short.extend(event['parent_ids'])
    if too_short:
        raise TempoRejected('TEMPO_UNRENDERABLE', {**details, 'reason': 'PCM_NOTE_TOO_SHORT',
            'maximumBpm': maximum, 'minimumNoteFrames': 2, 'sampleRate': FS,
            'eventIds': sorted(set(too_short))[:50]})
    return performance, mapping
