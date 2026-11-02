"""Source-coordinate parsing and explicit educational normalization for JGB import."""
from copy import deepcopy
from fractions import Fraction
import re

from data.src.playback.jgb import integer, row_groups
from data.src.playback.preview import canonical
from data.src.playback.expressions import LENGTH_MARKS
from data.src.playback.score import require
from data.src.playback.tempo_repeat import TEMPO_CUES

MARK = re.compile(r"[\{(\[]([^{}()\[\]]+)[\})\]]")
BREAK = re.compile(r"\|\|(?:\r\n|\r|\n)?|\r\n|\n|\r")
REPEATS = {"반복표", "도돌이표"}


def prepare_source(document, instrument, part, policy, has_plan):
    """Keep original coordinates; only opt-in educational exceptions are stripped."""
    require(document.get('v') in (1, 2), 'JGB v1/v2가 필요합니다')
    voices = document.get('parts') if document['v'] == 2 else [document]
    require(isinstance(voices, list) and voices and all(isinstance(v, dict) for v in voices), 'JGB parts가 필요합니다')
    require(part is not None or len(voices) == 1, '여러 성부가 있습니다. --part로 1부터 성부 번호를 선택하세요')
    selected = integer(1 if part is None else part, 'part', 1, len(voices))
    melody = voices[selected-1].get('melody')
    require(isinstance(melody, str) and melody.strip(), '선택한 성부의 melody가 비어 있습니다')
    end = len(melody.rstrip('\r\n'))
    separators = list(BREAK.finditer(melody[:end]))
    starts, ends = [0] + [m.end() for m in separators], [m.start() for m in separators] + [end]
    cells, lines, factors, cues, repeats, traces = [], [], {}, [], set(), []
    changed = False
    for g, (start, stop) in enumerate(zip(starts, ends), 1):
        clean_cells, cell_start = [], start
        for c, cell in enumerate(melody[start:stop].split('|'), 1):
            label = f'성부 {selected} · {g}각 · {c}정간'
            rows, annotations = [], []
            for original_row, match in enumerate(re.finditer(r'\S+', cell), 1):
                text = match[0]
                raw = list(MARK.finditer(text))
                annotation_marks = [m for m in raw if canonical(m[1].partition('@')[0]) in TEMPO_CUES | REPEATS]
                for mark in annotation_marks:
                    name = canonical(mark[1].partition('@')[0])
                    require(name not in REPEATS or policy, f'{label}: 원문 반복표에는 명시적 educational-preview/v1 정책이 필요합니다')
                    require(name not in TEMPO_CUES or policy or has_plan, f'{label}: 빠르기 기호는 --performance-plan을 지정하세요')
                    # Validate adjustment syntax before consuming the annotation.
                    from data.src.playback.common_preview import _symbol_name
                    _symbol_name(mark[1])
                    annotations.append({'text': mark[0], 'symbol': mark[1], 'start': cell_start + match.start() + mark.start(),
                                        'end': cell_start + match.start() + mark.end(), 'row': original_row,
                                        'effectivePlacement': {'cellId': f'c{g-1}-j{c-1}', 'row': 0, 'col': 0},
                                        'basis': '원문 문자 위치는 여기 보존; 공통 표시는 정간 첫 자리로 옮기고 정간 단위 수행 계획에 한 번 적용'})
                    if name in TEMPO_CUES:
                        cues.append({'gak': g, 'cell': c, 'symbol': name})
                    else:
                        repeats.add(g)
                clean = MARK.sub(lambda m: '' if canonical(m[1].partition('@')[0]) in TEMPO_CUES | REPEATS else m[0], text)
                groups, breath = row_groups(clean, f'{label} · {original_row}분박', instrument=instrument, notation_policy=policy)
                if not clean and annotation_marks:
                    continue
                rows.append({'text': text, 'clean': clean, 'start': cell_start + match.start(), 'row': original_row,
                             'groups': groups or [{'kind': 'hold', 'symbols': []}], 'breath': breath,
                             'marks': [m for m in raw if m not in annotation_marks]})
            if not rows:
                rows = [{'text': '', 'clean': '', 'start': cell_start, 'row': 1,
                         'groups': [{'kind': 'hold', 'symbols': []}], 'breath': False, 'marks': []}]
            weight = sum(sum(Fraction(group.get('weight', 1)) for group in row['groups']) / len(row['groups']) for row in rows)
            unbalanced = weight != len(rows)
            require(not unbalanced or policy, f'{label}: 길이 기호의 짝이 정간 전체 시가를 보존하지 않습니다')
            if unbalanced:
                for r, row in enumerate(rows, 1):
                    for u, group in enumerate(row['groups'], 1):
                        if 'weight' in group:
                            factors[(g, c, r, u)] = Fraction(group['weight'])
                    row['clean'] = MARK.sub(lambda m: '' if canonical(m[1].partition('@')[0]) in LENGTH_MARKS else m[0], row['clean'])
                traces.append({'gak': g, 'cell': c, 'operation': 'unpaired-length-performed',
                               'basis': '명시 교육 정책: 원래 균등 자리에서 꾸밈 생성 후 수행 길이 배율 적용'})
            if annotations:
                traces.append({'gak': g, 'cell': c, 'operation': 'performance-annotations', 'annotations': deepcopy(annotations)})
            cells.append({'gak': g, 'cell': c, 'text': cell, 'start': cell_start, 'rows': rows,
                          'annotations': annotations, 'lengthMode': 'performed' if unbalanced else 'written'})
            clean_cells.append(' '.join(row['clean'] for row in rows))
            changed |= bool(annotations) or unbalanced
            cell_start += len(cell) + 1
        lines.append(' | '.join(clean_cells))
    prepared = deepcopy(document) if changed else document
    if changed:
        voice = prepared['parts'][selected-1] if prepared['v'] == 2 else prepared
        voice['melody'] = '\n'.join(lines)
    return prepared, cells, factors, cues, repeats, traces


def educational_plan(cells, cues, repeats, bpm, digest, instrument):
    tempos, current, seen = [], bpm, set()
    counts = {}
    for cell in cells:
        counts[cell['gak']] = cell['cell']
    for cue in cues:
        gak, cell, name = cue['gak'], cue['cell'], cue['symbol']
        require((gak, cell) not in seen, '같은 정간의 빠르기 기호가 중복되었습니다')
        seen.add((gak, cell))
        current = bpm if name == '본래속도로' else current * (.8 if '느리게' in name else 1.2)
        entry = {'gak': gak, 'cell': cell, 'bpm': current}
        if name.startswith('점점'):
            entry.update(end_gak=gak if cell < counts[gak] else gak + 1, end_cell=cell + 1 if cell < counts[gak] else 1)
        tempos.append(entry)
    return {'schema': f'{instrument}-performance/v1', 'input_sha256': digest, 'tempos': tempos,
            'repeats': [{'start_gak': g, 'end_gak': g, 'times': 2} for g in sorted(repeats)]}
