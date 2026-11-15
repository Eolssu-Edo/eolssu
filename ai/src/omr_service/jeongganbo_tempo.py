"""Bind independently read hanbae signs to the existing expression companion."""
from copy import deepcopy
import hashlib
import json

from hanbae_marks import ALIASES


def attach_marks(score, cues, existing=None):
    if not cues:
        return ({'expressions': existing} if existing else {}), []
    expression = deepcopy(existing) if existing else {'schema': 'common-score-expressions/v1', 'placements': []}
    units = [(cell, unit) for column in score['columns'] for cell in column['cells'] for unit in cell['units']]
    positions = {(cell['id'], unit['row'], unit['col']): [] for cell, unit in units}
    events = {event['id']: event for event in score['events']}
    if existing:
        for entry in expression['placements']:
            positions[(entry['cellId'], entry['row'], entry['col'])] = list(entry['symbols'])
    else:
        seen = set()
        for cell, unit in units:
            if unit['eventId'] not in seen:
                positions[(cell['id'], unit['row'], unit['col'])] = list(events[unit['eventId']]['sigimsae'])
                seen.add(unit['eventId'])
    cells = {(column['index'], cell['index']): cell for column in score['columns'] for cell in column['cells']}
    names = {key: {mark for unit in cell['units']
                   for mark in positions[(cell['id'], unit['row'], unit['col'])] if mark in ALIASES.values()}
             for key, cell in cells.items()}
    for cue in cues:
        key = cue.get('gak'), cue.get('jeonggan')
        if key in names:
            names[key].add(cue['symbol'])
    issues = []
    anchored = False
    seen_cues = set()
    for cue in cues:
        key = cue.get('gak'), cue.get('jeonggan')
        cell = cells.get(key)
        identity = (cell['id'], cue['symbol']) if cell else (cue.get('page'), tuple(cue.get('bbox') or ()), cue['symbol'])
        if identity in seen_cues:
            continue
        seen_cues.add(identity)
        detail = dict(reason='TEMPO_CUE_UNRESOLVED' if cell else 'TEMPO_CUE_POSITION_UNRESOLVED',
                      symbol=cue['symbol'], rawText=cue['rawText'])
        if cell and len(names[key]) > 1:
            detail['reason'] = 'TEMPO_CUE_CONFLICT'
        issues.append(dict(kind='UNREADABLE', eventId=None, cellId=cell['id'] if cell else None,
                           page=cue.get('page'), bbox=cue.get('bbox'), detail=detail))
        if cell is None or detail['reason'] == 'TEMPO_CUE_CONFLICT':
            continue
        unit = cell['units'][0]
        marks = positions[(cell['id'], unit['row'], unit['col'])]
        if cue['symbol'] not in marks:
            marks.append(cue['symbol'])
        anchored = True
    if not anchored:
        return ({'expressions': existing} if existing else {}), issues
    for event in score['events']:
        event['sigimsae'] = []
    placements = []
    for cell, unit in units:
        marks = positions[(cell['id'], unit['row'], unit['col'])]
        events[unit['eventId']]['sigimsae'].extend(marks)
        if marks:
            placements.append(dict(eventId=unit['eventId'], cellId=cell['id'], row=unit['row'], col=unit['col'], symbols=marks))
    expression['placements'] = placements
    instrument = score['score']['instrument'].lower().replace('_', '-')
    digest = hashlib.sha256(json.dumps(score, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    expression['sourceSha256'] = digest
    if expression.get('performancePlan') is None:
        # Keep the existing educational repeat policy when suppressing only the
        # new, unresolved tempo approximation. Explicit caller plans win.
        repeat_columns = {column['index'] + 1 for column in score['columns'] for cell in column['cells']
                          for unit in cell['units']
                          if any(mark.split('@', 1)[0] in ('반복표', '도돌이표')
                                 for mark in positions[(cell['id'], unit['row'], unit['col'])])}
        expression['performancePlan'] = dict(schema=f'{instrument}-performance/v1', input_sha256=digest, tempos=[],
            repeats=[dict(start_gak=gak, end_gak=gak, times=2) for gak in sorted(repeat_columns)])
    expression['performancePlan']['input_sha256'] = digest
    return {'expressions': expression}, issues
