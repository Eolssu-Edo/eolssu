"""Compile canonical common slots directly; no document or notation-string adapter.

Slots retain event identity and rational written duration. Musical symbol rules
and ornament expansion live in expressions; MIDI, synthesis and tempo/repeat
remain downstream consumers of the existing reviewed-score IR.
"""
from copy import deepcopy
from fractions import Fraction

from . import expressions as expr, preview
from .score import midi_tempo, require, ticks


def compile_slots(source, base_score, base_map, slots, config, continuations):
    instrument = source['score']['instrument'].lower().replace('_', '-')
    hwang = base_map['hwang_midi']
    cell_ratio = Fraction(source['music']['ticksPerJeonggan'], source['music']['ticksPerBeat']) * Fraction(base_map['quarter_ratio'])
    cell_bpm = base_map['bpm'] * source['music']['ticksPerBeat'] / source['music']['ticksPerJeonggan']
    quarter_bpm = cell_bpm * float(cell_ratio)
    profile = expr.resolve_expression_profile(config.get('expressionProfile'), instrument, preview.MODE)
    policy = expr.resolve_expression_policy(config.get('expressionPolicy'), instrument, preview.MODE)
    custom_scale = expr.resolve_relative_scale(config.get('relativeScale'))
    scale_name = config.get('jo', '')
    require(isinstance(scale_name, str) and scale_name in ('', 'all', *expr.SCALES), 'Unknown educational preview scale')
    default_scale = custom_scale is None and scale_name in ('', 'all')
    if custom_scale is not None:
        require(scale_name in ('', 'all'), '상대음 음계와 joPreset/--jo가 중복 지정되었습니다')
        scale = tuple(custom_scale['pitch_classes'])
    else:
        scale_name = preview.DEFAULT_SCALE if default_scale else scale_name
        scale = expr.SCALES[scale_name]
    notes, locations, cursor, written = [], {}, Fraction(), Fraction()
    active_event, active_gak, sustain_index, sustain_start = None, None, None, Fraction()
    for slot in slots:
        gak, cell, row, unit = slot['location']
        label = f"{slot['eventId']} · {slot['cellId']} ({row-1},{slot['col']})"
        group = {'kind': slot['kind'], 'symbols': []}
        for raw, name in slot['symbols']:
            expr.add_symbol(group, name, raw.partition('@')[0], instrument, preview.MODE, label)
        width = slot['writtenDuration'] * group.get('weight', 1)
        duration = expr.performed_duration(width, group)
        ticks(duration)
        nid = f"{slot['cellId']}-r{row-1}-u{slot['col']}"
        loc = {'gak': gak, 'cell': cell, 'row': row, 'unit': unit, 'kind': group['kind'],
               'symbols': group['symbols'], 'eventId': slot['eventId'], 'cellId': slot['cellId'],
               'start_quarters': str(cursor), 'duration_quarters': str(duration),
               'written_start_quarters': str(written), 'written_duration_quarters': str(width)}
        if group.get('semitone_offset'):
            loc['written_pitch_shift_semitones'] = group['semitone_offset']
        crosses = gak in continuations and cell == row == unit == 1
        if crosses:
            require(group['kind'] == 'hold' and active_gak == gak - 1 and notes and not notes[-1].get('rest'),
                    f'{label}: 각 경계 지속은 바로 앞 각의 본음과 첫 지속칸이 필요합니다')
            loc['gak_continuation'] = dict(continuations[gak])
        if group['kind'] == 'hold' and notes and not notes[-1].get('rest') and (
                active_event == slot['eventId'] or crosses):
            target = notes[sustain_index]
            require(not target.get('breath_seconds'), f'{label}: 숨표 뒤의 연장은 명시한 새 음이 필요합니다')
            expr.validate_sustain_effects(target, group, label)
            target['duration'] = str(Fraction(target['duration']) + duration)
            delay = float(cursor - sustain_start) * 60 / quarter_bpm
            expr.apply_effects(target, group, label, delay, profile, policy,
                               locations[target['id']][0].get('written_pitch_shift_semitones', 0))
            locations[target['id']].append(loc)
            active_event, active_gak = slot['eventId'], gak
        elif group['kind'] in ('rest', 'hold'):
            require(not expr.has_effects(group, include_fermata=False), f'{label}: 표현을 적용할 앞 본음이 없습니다')
            notes.append({'id': nid, 'duration': str(duration), 'rest': True})
            locations[nid] = [loc]
        else:
            pitch = slot['pitch'] + group.get('semitone_offset', 0)
            expanded, target, offset, _, timing = expr.ornament_notes(
                group, pitch, hwang, scale, duration, nid, label,
                notation_policy=preview.MODE, profile=profile, policy=policy)
            if timing:
                loc['grace_timing'] = timing
            sustain_index, sustain_start = len(notes) + target, cursor + offset
            notes.extend(expanded)
            locations.update({note['id']: [deepcopy(loc)] for note in expanded})
            concrete = slot.get('concreteEventIds')
            require(concrete is None or len(concrete) == len(expanded), 'sourceSlots: 독립 음열의 음 개수가 공통 NOTE와 다릅니다')
            active_event, active_gak = concrete[target] if concrete else slot['eventId'], gak
        require(len(notes) <= 10000, '1..10000 notes required')
        cursor += duration
        written += width
    require(sum(Fraction(n['duration']) for n in notes) == cursor, '공통 음표와 수행 길이 불일치')
    expected_width = sum(len(c['cells']) for c in source['columns']) * cell_ratio
    require(written == expected_width, '공통 표시 자리의 전체 길이 불일치')
    score = deepcopy(base_score)
    score.update(notes=notes, quarter_bpm=quarter_bpm)
    score['assumptions'] = ['공통 이벤트·표시 자리에서 직접 시김새를 전개합니다. 교육용 근사값은 전통 연주의 확정 표준이 아닙니다.']
    mapping = {'schema': 'common-expression-map/v1', 'sourceSha256': base_map['input_sha256'],
               'jeonggan_bpm': cell_bpm, 'quarter_bpm': quarter_bpm, 'midi_tempo_us': midi_tempo(quarter_bpm),
               'gaks': len(source['columns']), 'cells': sum(len(c['cells']) for c in source['columns']),
               'total_quarters': str(cursor), 'written_total_quarters': str(written), 'notes': locations,
               'symbol_policy': expr.symbol_policy(instrument), 'grace_policy': dict(expr.GRACE_POLICY),
               'breath_policy': dict(expr.BREATH_POLICY), 'expression_policy': policy,
               'expression_profile': profile, 'jo_preset': scale_name, 'relative_scale': custom_scale,
               'notation_policy': preview.evidence(instrument, locations, expr.confirmed_symbols(instrument),
                    expr.symbols_for(instrument, preview.MODE), default_scale, policy, profile)}
    if continuations:
        mapping['gak_continuations'] = list(continuations.values())
    return score, mapping
