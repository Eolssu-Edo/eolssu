"""Validated source-slot grouping for inferred JGB imports, never reviewed scores."""
from copy import deepcopy
from fractions import Fraction

from .expressions import symbols_for, LENGTH_MARKS
from .jgb import NOTE, YUL
from .score import reject_unknown, require


def source_slots(source, config, units, marks, symbol_name):
    entries = config.get('sourceSlots')
    if entries is None:
        return None
    require(source['provenance']['review'] == 'inferred' and
            source['provenance'].get('converter') == 'jgb-playback-import/v1',
            'sourceSlots는 inferred JGB 재생 가져오기에만 사용할 수 있습니다')
    require(not config.get('slurGroups'), 'sourceSlots와 별도 slurGroups를 겹칠 수 없습니다')
    require(isinstance(entries, list) and 0 < len(entries) <= 10000, 'sourceSlots: 1..10000 슬롯이 필요합니다')
    indexed = {(u['cellId'], u['row'], u['col']): u for u in units}
    visited, visited_set, result, row_counts, row_widths, cell_rows = [], set(), {}, {}, {}, {}
    events = {e['id']: e for e in source['events']}
    last_unit = {u['eventId']: k for k, u in indexed.items()}
    instrument = source['score']['instrument'].lower().replace('_', '-')
    for i, entry in enumerate(entries):
        label = f'sourceSlots[{i}]'
        require(isinstance(entry, dict), f'{label}: 객체가 필요합니다')
        reject_unknown(entry, ['cellId', 'row', 'cols', 'kind', 'anchorSymbol', 'symbols', 'lengthMode', 'basis'], label)
        cid, row, cols = entry.get('cellId'), entry.get('row'), entry.get('cols')
        require(isinstance(cid, str) and type(row) is int and isinstance(cols, list) and
                0 < len(cols) <= 64 and all(type(c) is int for c in cols), f'{label}: 잘못된 표시 위치')
        require(cols == list(range(cols[0], cols[0] + len(cols))), f'{label}: 연속 col이 필요합니다')
        keys = [(cid, row, c) for c in cols]
        require(all(k in indexed and k not in visited_set for k in keys), f'{label}: 존재하지 않거나 겹친 위치')
        members = [indexed[k] for k in keys]
        require(not any(events[u['eventId']]['breathAfter'] and last_unit[u['eventId']] == k
                        for k, u in zip(keys[:-1], members[:-1])), f'{label}: 슬롯 내부 숨표를 넘을 수 없습니다')
        require(isinstance(entry.get('basis'), str) and entry['basis'].strip(), f'{label}: 원문 근거가 필요합니다')
        kind, symbol, raw = entry.get('kind'), entry.get('anchorSymbol'), entry.get('symbols')
        require(kind in ('note', 'relative', 'hold', 'rest'), f'{label}: 잘못된 kind')
        require(isinstance(symbol, str) and (NOTE.fullmatch(symbol) if kind in ('note', 'relative') else
                symbol == '-' if kind == 'hold' else symbol == '쉼'), f'{label}: 숨은 음표/숨표 없는 anchorSymbol이 필요합니다')
        require(isinstance(raw, list) and all(isinstance(v, str) for v in raw), f'{label}: symbols 배열이 필요합니다')
        names = [symbol_name(v) for v in raw]
        actual_marks = [m for k in keys for m in marks[k]]
        if kind == 'relative':
            table = symbols_for(instrument, 'educational-preview/v1')
            require(names and names[0] in table and table[names[0]][0] == 'independent', f'{label}: 독립 음열이 필요합니다')
            require(raw[1:] == actual_marks, f'{label}: 원문 효과와 placements 불일치')
            require(all(u['symbol'] not in ('쉼', '') for u in members), f'{label}: 독립 음열에 쉼을 넣을 수 없습니다')
        else:
            require(raw == actual_marks, f'{label}: 원문 기호와 placements 불일치')
            expected = members[0]['symbol'] or '-'
            require(symbol == expected and all(u['symbol'] in ('', '-') for u in members[1:]),
                    f'{label}: 원문 anchor와 공통 표시 율명이 다릅니다')
        require(entry.get('lengthMode') in ('written', 'performed'), f'{label}: 명시 lengthMode가 필요합니다')
        row_key = (cid, row)
        row_widths.setdefault(row_key, len(cols))
        require(row_widths[row_key] == len(cols), f'{label}: 원문 같은 row의 슬롯 너비는 같아야 합니다')
        weights = [Fraction(LENGTH_MARKS[n]) for n in names if n in LENGTH_MARKS]
        require(len(weights) <= 1, f'{label}: 길이표 중복')
        cell_rows.setdefault(cid, {}).setdefault(row, []).append((entry, weights[0] if weights else Fraction(1)))
        ordinal = row_counts.get(row_key, 0)
        row_counts[row_key] = ordinal + 1
        item = dict(entry, units=members, renderCol=ordinal)
        result[keys[0]] = item
        visited.extend(keys)
        visited_set.update(keys)
    require(visited == list(indexed), 'sourceSlots는 원본 표시 자리를 순서대로 빠짐없이 덮어야 합니다')
    for rows in cell_rows.values():
        balanced = sum(sum(w for _, w in entries) / len(entries) for entries in rows.values()) == len(rows)
        expected = 'written' if balanced else 'performed'
        require(all(e['lengthMode'] == expected for entries in rows.values() for e, _ in entries),
                'sourceSlots: 균형 길이표는 written, 짝 없는 교육 길이표는 performed로 보존해야 합니다')
    return result


def expand_locations(score, mapping, groups, source, base_map):
    """Map original slot spans to display units, respecting concrete-note durations."""
    by_event = {e['id']: e for e in source['events']}
    intervals, cursor = {}, Fraction()
    for note in score['notes']:
        start, end = cursor, cursor + Fraction(note['duration'])
        origin = mapping['notes'][note['id']][0]
        key = (origin['gak'], origin['cell'], origin['row'], origin['unit'], origin.get('repeat_pass', 1))
        intervals.setdefault(key, []).append((note, start, end))
        cursor = end
    cache = {}
    for note in score['notes']:
        rewritten = []
        for loc in mapping['notes'][note['id']]:
            key = (loc['gak'], loc['cell'], loc['row'], loc['unit'])
            group = groups[key]
            start, width = Fraction(loc['start_quarters']), Fraction(loc['duration_quarters'])
            cache_key = (key, loc.get('repeat_pass', 1), start, width)
            if cache_key not in cache:
                members = group['units']
                if group['kind'] == 'relative':
                    ids = list(dict.fromkeys(u['eventId'] for u in members))
                    sounds = intervals.get((*key, loc.get('repeat_pass', 1)), [])
                    require(len(ids) == len(sounds), 'sourceSlots: 독립 음열의 음 개수가 공통 NOTE와 다릅니다')
                    chunks = []
                    for eid, (sound, a, b) in zip(ids, sounds):
                        event = by_event[eid]
                        require(event['kind'] == 'NOTE', 'sourceSlots: 독립 음열에는 구체 NOTE가 필요합니다')
                        pitch = base_map['hwang_midi'] + YUL.index(event['yul']) + 12 * event['octave']
                        require(sound.get('pitch') == pitch,
                                'sourceSlots: 독립 음열의 실제 음높이가 공통 NOTE와 다릅니다')
                        chunks.append(([u for u in members if u['eventId'] == eid], max(a, start), min(b, start + width), sound['id']))
                else:
                    chunks = [(members, start, start + width, None)]
                expanded = []
                for chunk, a, b, owner in chunks:
                    require(b > a, 'sourceSlots: 음표와 표시 자리의 시간 교차가 없습니다')
                    for j, unit in enumerate(chunk):
                        item = deepcopy(loc)
                        item['source_slot'] = {k: loc[k] for k in ('gak', 'cell', 'row', 'unit')}
                        item.update(row=unit['row'] + 1, unit=unit['col'] + 1,
                                    start_quarters=str(a + (b-a)*j/len(chunk)), duration_quarters=str((b-a)/len(chunk)))
                        expanded.append((owner, item))
                require(Fraction(expanded[0][1]['start_quarters']) == start and
                        sum(Fraction(v['duration_quarters']) for _, v in expanded) == width,
                        'sourceSlots: 표시 시간표에 빈틈이 있습니다')
                cache[cache_key] = expanded
            for owner, item in cache[cache_key]:
                if owner is None or owner == note['id']:
                    rewritten.append(deepcopy(item))
        require(rewritten, 'sourceSlots: 음표의 표시 대응이 사라졌습니다')
        mapping['notes'][note['id']] = rewritten
