"""Rebind an audited ornament projection without estimating new audio timing.

Callers supply hashes of the actual input bytes and an independently audited
target event -> original events map. This does not infer musical equivalence
or approve an ornament interpretation. Repeats are deliberately unsupported.
"""
from bisect import bisect_right
from copy import deepcopy
from fractions import Fraction
import math
import re

from ..playback.common_preview import units_of
from ..playback.common_score import validate_common
from ..playback.score import require


def _number(value):
    return type(value) in (int, float) and math.isfinite(value)


def _hash(value):
    return isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value)


def validate_performance(source, performance, *, source_sha256, audio_sha256):
    """Validate the app's companion contract, restricted to one written pass."""
    validate_common(source, allow_sigimsae=True)
    expected = dict(schema='common-score-performance/v1', policy='educational-preview/v1',
                    sourceSha256=source_sha256, audioSha256=audio_sha256,
                    scoreVersion=source['score']['scoreVersion'], sourceId=source['score']['sourceId'],
                    instrument=source['score']['instrument'], bpmUnit=source['music']['bpmUnit'])
    require(_hash(source_sha256) and _hash(audio_sha256), 'invalid source/audio SHA256')
    require(set(performance) == set(expected) | {'bpm', 'durationMs', 'segments'}, 'performance fields')
    for name, value in expected.items():
        require(performance[name] == value, f'performance {name} mismatch')
    for name in ('bpm', 'durationMs'):
        require(_number(performance[name]) and performance[name] > 0, f'invalid {name}')
    units = list(units_of(source))
    segments = performance['segments']
    require(isinstance(segments, list) and len(segments) == len(units), 'one segment per written slot required')
    seen, annotations, end_ms = set(), {e['id']: [] for e in source['events']}, 0.
    fields = {'occurrenceId', 'eventId', 'cellId', 'row', 'col', 'startTick', 'endTick', 'startMs', 'endMs', 'sigimsae'}
    for unit, segment in zip(units, segments):
        require(isinstance(segment, dict) and set(segment) == fields, 'performance segment fields')
        occurrence = segment['occurrenceId']
        require(isinstance(occurrence, str) and occurrence.strip() and occurrence not in seen,
                'duplicate or empty occurrenceId')
        seen.add(occurrence)
        for key in ('eventId', 'cellId', 'row', 'col'):
            require(segment[key] == unit[key], f'written slot {key} mismatch')
        require(type(segment['row']) is int and type(segment['col']) is int, 'slot indices must be integers')
        for key in ('startTick', 'endTick'):
            require(_number(segment[key]) and abs(segment[key] - float(unit[key])) <= 1e-6,
                    f'written slot {key} mismatch')
        start, end = segment['startMs'], segment['endMs']
        require(_number(start) and _number(end) and start >= 0 and end > start
                and abs(start - end_ms) <= 1e-6, 'noncontiguous performance time')
        require(isinstance(segment['sigimsae'], list)
                and all(isinstance(mark, str) and mark for mark in segment['sigimsae']), 'invalid sigimsae')
        annotations[unit['eventId']].extend(segment['sigimsae'])
        end_ms = end
    require(abs(end_ms - performance['durationMs']) <= 1e-6, 'performance duration mismatch')
    for event in source['events']:
        require(annotations[event['id']] == event['sigimsae'], f"{event['id']}: sigimsae mismatch")


def _span(event, source):
    scale = source['music']['ticksPerBeat']
    return (Fraction(event['startTick'], scale),
            Fraction(event['startTick'] + event['durationTick'], scale))


def _pitch(event):
    return event['kind'], event.get('yul'), event.get('octave')


def _validate_reuse(previous, corrected, changed_events):
    for key in ('sourceId', 'instrument', 'part'):
        require(previous['score'][key] == corrected['score'][key], f'score {key} changed')
    require(corrected['score']['scoreVersion'] == previous['score']['scoreVersion'] + 1,
            'corrected scoreVersion must advance by one')
    for key in ('hwangHz', 'bpmUnit'):
        require(previous['music'][key] == corrected['music'][key], f'music {key} changed')
    require(Fraction(previous['music']['ticksPerJeonggan'], previous['music']['ticksPerBeat']) ==
            Fraction(corrected['music']['ticksPerJeonggan'], corrected['music']['ticksPerBeat']),
            'cell beat duration changed')
    old = {e['id']: e for e in previous['events']}
    new = {e['id']: e for e in corrected['events']}
    require(isinstance(changed_events, dict) and set(changed_events) <= set(new), 'unknown target event')
    consumed = []
    for target in corrected['events']:
        ids = changed_events.get(target['id'], [target['id']])
        require(isinstance(ids, list) and ids and all(eid in old for eid in ids), 'unknown original event')
        members = [old[eid] for eid in ids]
        spans = [_span(e, previous) for e in members]
        require(all(a[1] == b[0] for a, b in zip(spans, spans[1:]))
                and (spans[0][0], spans[-1][1]) == _span(target, corrected), 'event span changed')
        require(target['breathAfter'] == members[-1]['breathAfter']
                and not any(e['breathAfter'] for e in members[:-1]), 'breath articulation changed')
        if len(members) == 1:
            require(_pitch(target) == _pitch(members[0]) and target['sigimsae'] == members[0]['sigimsae'],
                    'unapproved pitch or expression change')
        else:
            require(target['kind'] == 'NOTE' and target['sigimsae']
                    and all(e['kind'] == 'NOTE' for e in members)
                    and _pitch(target) in [_pitch(e) for e in members], 'invalid ornament projection')
            require(len({e['cellId'].split('-')[0] for e in members}) == 1, 'ornament crosses column')
        consumed.extend(ids)
    require(consumed == [e['id'] for e in previous['events']], 'original events missing, duplicated or reordered')


def rebind_performance(previous, performance, corrected, *, previous_sha256,
                       corrected_sha256, audio_sha256, changed_events):
    """Use measured old slot boundaries, including MIDI tempo rounding.

    Rational score positions are compared in beats so a changed tick resolution
    does not change playback time. New boundaries inside an old slot interpolate
    that slot's measured time; no time is recalculated from the nominal BPM.
    """
    validate_performance(previous, performance, source_sha256=previous_sha256, audio_sha256=audio_sha256)
    validate_common(corrected, allow_sigimsae=True)
    require(_hash(corrected_sha256), 'invalid corrected SHA256')
    _validate_reuse(previous, corrected, changed_events)
    old_units = list(units_of(previous))
    scale = previous['music']['ticksPerBeat']
    points = [unit['startTick'] / scale for unit in old_units] + [old_units[-1]['endTick'] / scale]
    times = [Fraction(str(s['startMs'])) for s in performance['segments']]
    times.append(Fraction(str(performance['segments'][-1]['endMs'])))

    def at(tick):
        point = tick / corrected['music']['ticksPerBeat']
        index = min(bisect_right(points, point) - 1, len(points) - 2)
        require(0 <= index and points[index] <= point <= points[index + 1], 'target outside audio timeline')
        fraction = (point - points[index]) / (points[index + 1] - points[index])
        return float(times[index] + fraction * (times[index + 1] - times[index]))

    result = deepcopy(performance)
    result.update(sourceSha256=corrected_sha256, scoreVersion=corrected['score']['scoreVersion'], segments=[])
    events, seen = {e['id']: e for e in corrected['events']}, set()
    for number, unit in enumerate(units_of(corrected)):
        eid = unit['eventId']
        segment = {key: unit[key] for key in ('eventId', 'cellId', 'row', 'col')}
        segment.update(occurrenceId=f'unit-{number}', startTick=float(unit['startTick']),
                       endTick=float(unit['endTick']), startMs=at(unit['startTick']), endMs=at(unit['endTick']),
                       sigimsae=list(events[eid]['sigimsae']) if eid not in seen else [])
        result['segments'].append(segment)
        seen.add(eid)
    validate_performance(corrected, result, source_sha256=corrected_sha256, audio_sha256=audio_sha256)
    return result
