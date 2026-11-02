"""Opt-in educational expressions over an unchanged service v1 score.

The original score is the storage/scoring contract. This adapter supplies
placement and performed time separately; none of the defaults claim to
reconstruct ornament positions or a historically authoritative performance.
"""
from copy import deepcopy
from fractions import Fraction
import re

from .expressions import EFFECT_MARKS, PROFILE_MARKS, LENGTH_MARKS, adaptive_breath
from .preview import EXTRA_MARKS, canonical
from .common_compile import compile_slots
from .common_score import ANCHOR_OFFSETS, YUL_NAMES
from .score import reject_unknown, require
from .tempo_repeat import TEMPO_CUES, apply_tempo_repeat
from .import_slots import source_slots, expand_locations

POLICY = "educational-preview/v1"
REPEAT_MARKS = {"반복표", "도돌이표"}


def _symbol_name(raw):
    require(isinstance(raw, str), '기호 이름은 문자열이어야 합니다')
    name, at, adjustment = raw.partition('@')
    require(re.fullmatch(r'[^{}\[\]()\s@|;,]+', name), '잘못된 기호 이름')
    if at:
        require(adjustment and all(re.fullmatch(r'[+-]?(?:\d+(?:\.\d*)?|\.\d+)', part)
                                   for part in adjustment.split(',')), '잘못된 기호 배치값')
    return canonical(name)


def _sustain_effect(raw):
    name = _symbol_name(raw)
    return (name in EFFECT_MARKS and EFFECT_MARKS[name] != 'staccato' or
            name in PROFILE_MARKS and PROFILE_MARKS[name] != 'accent' or
            name in EXTRA_MARKS and EXTRA_MARKS[name] != 'geok' or name == '원문점')


def units_of(source):
    """Keep rational unit positions even when merged events use integer ticks."""
    offset = 0
    width = source['music']['ticksPerJeonggan']
    for column in source['columns']:
        for cell in column['cells']:
            rows = {}
            for unit in cell['units']:
                rows.setdefault(unit['row'], []).append(unit)
            for row, units in rows.items():
                duration = Fraction(width, len(rows) * len(units))
                for unit in units:
                    start = offset + Fraction(width * row, len(rows)) + duration * unit['col']
                    yield dict(unit, cellId=cell['id'], startTick=start, endTick=start + duration,
                               gak=column['index'] + 1, cell=cell['index'] + 1)
            offset += width


def _key(unit):
    return unit['cellId'], unit['row'], unit['col']


def _placements(source, config, input_hash):
    units = list(units_of(source))
    by_key = {_key(unit): unit for unit in units}
    marks = {key: [] for key in by_key}
    if config is None:
        seen = set()
        events = {event['id']: event for event in source['events']}
        for unit in units:
            eid = unit['eventId']
            if eid not in seen:
                event = events[eid]
                marks[_key(unit)] = list(event['sigimsae'])
                seen.add(eid)
        return units, marks, 'event-origin-inferred'
    require(isinstance(config, dict), '표현 파일은 JSON 객체여야 합니다')
    reject_unknown(config, ['schema', 'sourceSha256', 'placements', 'jo', 'relativeScale',
                            'expressionPolicy', 'expressionProfile', 'performancePlan',
                            'gakContinuations', 'slurGroups', 'sourceSlots'], 'expression companion')
    require(config.get('schema') == 'common-score-expressions/v1', '표현 파일 schema 불일치')
    require(config.get('sourceSha256') == input_hash, '표현 파일 sourceSha256 불일치')
    entries = config.get('placements')
    require(isinstance(entries, list) and len(entries) <= 10000, 'placements는 최대 10000개 배열이어야 합니다')
    seen, actual = set(), {event['id']: [] for event in source['events']}
    for entry in entries:
        require(isinstance(entry, dict), 'placement는 객체여야 합니다')
        reject_unknown(entry, ['eventId', 'cellId', 'row', 'col', 'symbols'], 'placement')
        require(type(entry.get('row')) is int and type(entry.get('col')) is int, 'placement row/col은 정수여야 합니다')
        key = (entry.get('cellId'), entry['row'], entry['col'])
        require(key in by_key and key not in seen, f'placement 위치 누락·중복: {key}')
        unit = by_key[key]
        require(entry.get('eventId') == unit['eventId'], f'{key}: placement eventId 불일치')
        symbols = entry.get('symbols')
        require(isinstance(symbols, list) and symbols and all(isinstance(x, str) and x for x in symbols),
                f'{key}: 비어 있지 않은 symbols 배열이 필요합니다')
        marks[key] = list(symbols)
        seen.add(key)
    for unit in units:
        actual[unit['eventId']].extend(marks[_key(unit)])
    for event in source['events']:
        require(actual[event['id']] == event['sigimsae'],
                f"{event['id']}: placement 기호가 원본 sigimsae와 다릅니다")
    return units, marks, 'explicit-unit-placement'


def _slur_groups(source, config):
    """Only explicit, contiguous concrete notes may restore an imported figure."""
    groups = config.get('slurGroups', [])
    require(isinstance(groups, list) and len(groups) <= 10000, 'slurGroups는 최대10000개 배열이어야 합니다')
    events = {event['id']: (i, event) for i, event in enumerate(source['events'])}
    assigned = {}
    for number, group in enumerate(groups):
        label = f'slurGroups[{number}]'
        require(isinstance(group, dict), f'{label}: 객체가 필요합니다')
        reject_unknown(group, ['eventIds', 'basis'], label)
        basis, ids = group.get('basis'), group.get('eventIds')
        require(isinstance(basis, str) and basis.strip(), f'{label}: 원문 근거가 필요합니다')
        require(isinstance(ids, list) and 2 <= len(ids) <= len(events) and
                all(isinstance(eid, str) and eid in events for eid in ids),
                f'{label}: 실제 eventId2개 이상의 배열이 필요합니다')
        require(len(set(ids)) == len(ids) and not (assigned.keys() & set(ids)), f'{label}: 중복·겹친 그룹')
        indices, members = zip(*(events[eid] for eid in ids))
        require(list(indices) == list(range(indices[0], indices[0] + len(ids))), f'{label}: 연속 이벤트가 필요합니다')
        require(len({e['cellId'] for e in members}) == 1 and
                all(e['kind'] == 'NOTE' and all(_sustain_effect(m) for m in e['sigimsae']) for e in members),
                f'{label}: 같은 정간의 구체 NOTE와 지속 효과만 연결할 수 있습니다')
        require(not any(e['breathAfter'] for e in members[:-1]), f'{label}: 중간 숨표를 넘을 수 없습니다')
        assigned.update({eid: f'common-slur-{number}' for eid in ids})
    return assigned


def _spans(mapping):
    spans = {}
    for locations in mapping['notes'].values():
        for loc in locations:
            start = Fraction(loc['start_quarters'])
            end = start + Fraction(loc['duration_quarters'])
            key = (loc['gak'], loc['cell'], loc['row'], loc['unit'])
            value = (end, key, loc)
            require(start not in spans or spans[start][:2] == value[:2], '표현 위치 시간 충돌')
            spans[start] = value
    return [(start, *value) for start, value in sorted(spans.items())]


def _stretch_lengths(score, mapping, factors):
    """Length marks affect performed spans; original display ticks stay fixed."""
    if not factors:
        return
    spans, cursor = [], Fraction()
    for start, end, key, _ in _spans(mapping):
        factor = factors.get(key, Fraction(1))
        spans.append((start, end, cursor, factor))
        cursor += (end - start) * factor

    def warp(value):
        value = Fraction(value)
        for start, end, target, factor in spans:
            if start <= value <= end:
                return target + (value - start) * factor
        raise ValueError('길이표 시간 변환 범위 오류')

    old_cursor = Fraction()
    for note in score['notes']:
        end = old_cursor + Fraction(note['duration'])
        note['duration'] = str(warp(end) - warp(old_cursor))
        for field in ('yoseong', 'pitch_curve'):
            effect = note.get(field)
            if effect and 'delay_seconds' in effect:
                old_delay = Fraction(str(effect['delay_seconds'])) * Fraction(str(score['quarter_bpm'])) / 60
                effect['delay_seconds'] = float(warp(old_cursor + old_delay) - warp(old_cursor)) * 60 / score['quarter_bpm']
        old_cursor = end
    for locations in mapping['notes'].values():
        for loc in locations:
            start = Fraction(loc['start_quarters'])
            end = start + Fraction(loc['duration_quarters'])
            loc['start_quarters'], loc['duration_quarters'] = str(warp(start)), str(warp(end) - warp(start))
    mapping['total_quarters'] = str(cursor)


def convert_preview(source, base_score, base_map, config):
    input_hash = base_map['input_sha256']
    require(isinstance(input_hash, str) and re.fullmatch('[0-9a-f]{64}', input_hash), '원본 SHA256이 필요합니다')
    units, marks, placement_policy = _placements(source, config, input_hash)
    config = config or {}
    imported_slots = source_slots(source, config, units, marks, _symbol_name)
    rendered_slots = {}
    slur_groups = _slur_groups(source, config)
    instrument = source['score']['instrument'].lower().replace('_', '-')
    events = {event['id']: event for event in source['events']}
    by_location = {(u['gak'], u['cell'], u['row'] + 1, u['col'] + 1): u for u in units}
    cell_bpm = base_map['bpm'] * source['music']['ticksPerBeat'] / source['music']['ticksPerJeonggan']
    continuations = {}
    if 'gakContinuations' in config:
        entries = config['gakContinuations']
        require(isinstance(entries, list), 'gakContinuations는 배열이어야 합니다')
        for entry in entries:
            require(isinstance(entry, dict), 'gakContinuations 항목은 객체여야 합니다')
            reject_unknown(entry, ['gak', 'basis'], 'gakContinuations')
            gak = entry.get('gak')
            require(type(gak) is int and 2 <= gak <= len(source['columns']),
                    'gakContinuations.gak은 2 이상인 실제 각 번호여야 합니다')
            require(gak not in continuations, '중복 각 경계 지속 위치입니다')
            require(isinstance(entry.get('basis'), str) and entry['basis'].strip(), '각 경계 지속 근거가 필요합니다')
            # Breath is normally applied after the preview has been compiled.
            # Check it here as well, before allowing the performed sustain to cross.
            previous = source['columns'][gak - 2]['cells'][-1]['units'][-1]
            require(not events[previous['eventId']]['breathAfter'],
                    f'gakContinuations 각 {gak}: 숨표 뒤에서는 이어 불 수 없습니다')
            continuations[gak] = {'part': 1, 'gak': gak, 'basis': entry['basis'].strip()}
    slots, factors, repeat_columns, tempo_entries, approximations = [], {}, set(), {}, []
    for column in source['columns']:
        for cell in column['cells']:
            for unit in cell['units']:
                key = (cell['id'], unit['row'], unit['col'])
                if imported_slots is not None and key not in imported_slots:
                    continue
                slot = imported_slots.get(key) if imported_slots is not None else None
                col = slot['renderCol'] if slot else unit['col']
                location = (column['index'] + 1, cell['index'] + 1, unit['row'] + 1, col + 1)
                if slot:
                    rendered_slots[location] = slot
                event = events[unit['eventId']]
                remaining = []
                for mark in slot['symbols'] if slot else marks[key]:
                    name = _symbol_name(mark)
                    if name in LENGTH_MARKS and not (slot and slot['lengthMode'] == 'written'):
                        require(location not in factors, f'{key}: 길이표 중복')
                        factors[location] = Fraction(LENGTH_MARKS[name])
                        approximations.append({'symbol': mark, 'location': list(key), 'factor': str(factors[location]),
                                               'basis': '교육용 해당 표시 자리의 수행 길이 배율; 기보 틱 보존'})
                    elif name in REPEAT_MARKS:
                        repeat_columns.add(column['index'] + 1)
                    elif name in TEMPO_CUES:
                        where = (column['index'] + 1, cell['index'] + 1)
                        require(where not in tempo_entries, f'{key}: 빠르기 기호 중복')
                        tempo_entries[where] = name
                        approximations.append({'symbol': mark, 'written_position': list(key),
                            'effective_position': [cell['id'], 0, 0],
                            'basis': '빠르기 기호는 기존 정간 단위 계획에 맞춰 해당 정간 시작부터 적용; 원래 기호 위치는 보존'})
                    else:
                        remaining.append(mark)
                kind = ('note' if slot['kind'] == 'relative' else slot['kind']) if slot else (
                    'hold' if unit['symbol'] in ('', '-') else 'rest' if event['kind'] == 'REST' else 'note')
                pitch = None
                if kind == 'note':
                    pitch = base_map['hwang_midi'] + (ANCHOR_OFFSETS[slot['anchorSymbol']] if slot else
                                                     YUL_NAMES.index(event['yul']) + 12 * event['octave'])
                sounding_marks = [m for m in remaining if not (slot and _symbol_name(m) in LENGTH_MARKS)]
                require(event['kind'] == 'NOTE' or not any(not _sustain_effect(m) for m in sounding_marks),
                        f'{key}: 쉼·각 경계 지속 자리의 새 음열/강세에는 원본 본음이 필요합니다')
                # Pure curves on a held note use the existing delayed-effect path.
                # A new pitch figure on a hold must have an explicit pitch anchor.
                if kind == 'hold' and event['kind'] == 'NOTE' and any(not _sustain_effect(m) for m in sounding_marks):
                    kind = 'note'
                    pitch = base_map['hwang_midi'] + YUL_NAMES.index(event['yul']) + 12 * event['octave']
                    approximations.append({'symbols': list(remaining), 'location': list(key),
                                           'basis': '교육용 이음 자리의 음열을 원본 본음에서 새로 발음'})
                members = slot['units'] if slot else [by_location[(column['index'] + 1, cell['index'] + 1,
                                                                  unit['row'] + 1, unit['col'] + 1)]]
                width = sum(u['endTick'] - u['startTick'] for u in members) / source['music']['ticksPerBeat']
                slots.append({'eventId': unit['eventId'], 'cellId': cell['id'], 'col': unit['col'],
                              'location': location, 'kind': kind, 'pitch': pitch,
                              'concreteEventIds': list(dict.fromkeys(u['eventId'] for u in members))
                                                  if slot and slot['kind'] == 'relative' else None,
                              'writtenDuration': width * Fraction(base_map['quarter_ratio']),
                              'symbols': [(raw, _symbol_name(raw)) for raw in remaining]})
    score, mapping = compile_slots(source, base_score, base_map, slots, config, continuations)
    _stretch_lengths(score, mapping, factors)
    plan = config.get('performancePlan')
    if plan is None:
        tempos, current = [], cell_bpm
        for (gak, cell), name in sorted(tempo_entries.items()):
            current = cell_bpm if name == '본래속도로' else current * (.8 if '느리게' in name else 1.2)
            entry = {'gak': gak, 'cell': cell, 'bpm': current}
            if name.startswith('점점'):
                last = len(source['columns'][gak - 1]['cells'])
                entry.update(end_gak=gak if cell < last else gak + 1, end_cell=cell + 1 if cell < last else 1)
            tempos.append(entry)
        plan = {'schema': f'{instrument}-performance/v1', 'input_sha256': input_hash, 'tempos': tempos,
                'repeats': [{'start_gak': c, 'end_gak': c, 'times': 2} for c in sorted(repeat_columns)]}
        if tempo_entries or repeat_columns:
            approximations.append({'basis': '교육용 기본 계획: 반복 기호가 있는 각 총2회; 느림0.8/빠름1.2; 점진 변화는 다음 정간까지', 'plan': deepcopy(plan)})
    score, mapping = apply_tempo_repeat(score, mapping, plan, input_hash, instrument=instrument)
    if imported_slots is not None:
        expand_locations(score, mapping, rendered_slots, source, base_map)
    ordinals = {event['id']: event['noteIdx'] for event in base_map['events']}
    for locations in mapping['notes'].values():
        for loc in locations:
            unit = by_location[(loc['gak'], loc['cell'], loc['row'], loc['unit'])]
            loc['common'] = {key: unit[key] for key in ('eventId', 'cellId', 'row', 'col')}
            loc['common'].update(noteIdx=ordinals[unit['eventId']],
                                 startTick=str(unit['startTick']), endTick=str(unit['endTick']))
    for note in score['notes']:
        origin = mapping['notes'][note['id']][0]
        group = slur_groups.get(origin['common']['eventId'])
        if group:
            note['slur'] = f"{group}.pass{origin.get('repeat_pass', 1)}"
    # Exact hwangHz and source provenance are inherited from the normal adapter.
    score['context'] = deepcopy(base_score['context'])
    score['source'] = deepcopy(base_score['source'])
    score['source']['notation_policy'] = POLICY
    score['assumptions'].extend(base_score['assumptions'])
    score['assumptions'].append('교육·미리듣기용 해석. 원본 v1 저장·NOTE idx·채점 타임라인은 변경하지 않음.')
    # Breath belongs to the final performed sound of each original event/pass,
    # including an ornament figure whose last sound differs from its main pitch.
    last_sound = {}
    for note in score['notes']:
        if note.get('rest'):
            continue
        loc = mapping['notes'][note['id']][-1]
        unit = by_location[(loc['gak'], loc['cell'], loc['row'], loc['unit'])]
        last_sound[(unit['eventId'], loc.get('repeat_pass', 1))] = note
    for (eid, _), note in last_sound.items():
        if events[eid]['breathAfter']:
            note.update(adaptive_breath(note['duration'], score['quarter_bpm']))
    for index, note in enumerate(score['notes']):
        if note.get('rest'):
            continue
        following = score['notes'][index + 1] if index + 1 < len(score['notes']) else {}
        linked = (note.get('slur') and following.get('slur') == note['slur'] and
                  not following.get('rest') and not note.get('breath_seconds'))
        # A figure's internal note-off must reach the next onset for legato.
        # Explicit gates still take precedence; only the default moves to its end.
        if not linked:
            note.setdefault('gate_fraction', 1)
    segments = []
    seconds_per_quarter = mapping['midi_tempo_us'] / 1e6
    for index, (start, end, location, _) in enumerate(_spans(mapping)):
        unit = by_location[location]
        segments.append({'occurrenceId': f'unit-{index}', 'eventId': unit['eventId'], 'cellId': unit['cellId'],
                         'row': unit['row'], 'col': unit['col'],
                         'startTick': float(unit['startTick']), 'endTick': float(unit['endTick']),
                         'startMs': float(start) * seconds_per_quarter * 1000,
                         'endMs': float(end) * seconds_per_quarter * 1000,
                         'sigimsae': list(marks[_key(unit)])})
    require({_key(unit) for unit in units} == {(s['cellId'], s['row'], s['col']) for s in segments},
            '수행 표시 시간표에서 원본 표시 자리가 사라졌습니다')
    require(segments and segments[0]['startMs'] == 0, '수행 표시 시간표 시작 오류')
    require(all(abs(a['endMs'] - b['startMs']) < 1e-6 for a, b in zip(segments, segments[1:])), '수행 표시 시간표 불연속')
    total_ms = segments[-1]['endMs']
    timeline = {'schema': 'common-score-performance/v1', 'policy': POLICY, 'sourceSha256': input_hash,
                'scoreVersion': source['score']['scoreVersion'], 'sourceId': source['score']['sourceId'],
                'instrument': source['score']['instrument'], 'bpm': base_map['bpm'], 'bpmUnit': source['music']['bpmUnit'],
                'durationMs': total_ms, 'segments': segments}
    result = deepcopy(base_map)
    result.update(notation_policy=mapping['notation_policy'], placement_policy=placement_policy,
                  placement_assumptions=approximations, performanceTimeline=timeline,
                  expression_source_map=mapping, performed_score_seconds=total_ms / 1000,
                  written_score_seconds=base_map['score_seconds'])
    if slur_groups:
        result['slur_groups'] = deepcopy(config['slurGroups'])
    return score, result
