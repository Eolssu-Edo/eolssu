"""Hanbae signs retain their location without inventing a numeric target."""
from copy import deepcopy
import hashlib
import importlib.util
import json
import os
import sys
from pathlib import Path

import pytest
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'data/src/scores'))
needs_scan = pytest.mark.skipif(importlib.util.find_spec('cv2') is None, reason='스캔 런타임의 OpenCV 필요')


@pytest.mark.parametrize('raw,expected', [('ㅈ·ㄴ', '조금느리게'), ('ᄌ\nᄉ', '조금속하게'),
    ('ㅈㆍㅈㆍㄴ', '점점느리게'), ('ㅈ‧ㅈ‧ㅅ', '점점속하게'), ('ㅂ ㅅ', '본래속도로')])
def test_hanbae_jamo_aliases(raw, expected):
    from hanbae_marks import canonical
    assert canonical(raw) == expected
    assert canonical('니') is None


def test_vertical_text_sign_maps_to_the_cell_to_its_left():
    from hanbae_marks import text_marks
    glyphs = [dict(c=c, x=113, y=y, size=9) for c, y in [('ㅈ', 130), ('·', 139), ('ㄴ', 148)]]
    cells = [dict(x=50, y=100, w=50, h=60, gak=0), dict(x=50, y=160, w=50, h=60, gak=0)]
    assert text_marks(glyphs, cells) == [dict(symbol='조금느리게', rawText='ㅈ·ㄴ', gak=0,
        jeonggan=0, bbox=[108.5, 125.5, 9.0, 27.0])]


def sign_image(text):
    image = Image.new('L', (200, 240), 'white')
    draw = ImageDraw.Draw(image)
    strokes = {'ㅈ': [[(0, 0), (18, 0)], [(14, 0), (0, 14)], [(9, 7), (18, 14)]],
               'ㅅ': [[(9, 0), (0, 14)], [(9, 0), (18, 14)]],
               'ㄴ': [[(0, 0), (0, 14), (18, 14)]],
               'ㅂ': [[(0, 0), (0, 14), (18, 14), (18, 0)], [(0, 7), (18, 7)]]}
    y = 35
    for letter in text:
        for stroke in strokes[letter]:
            draw.line([(115 + x, y + dy) for x, dy in stroke], fill=0, width=3)
        y += 24
        draw.ellipse((122, y - 3, 125, y), fill=0)
        y += 12
    for dy in (0, 12):
        draw.ellipse((122, y + dy, 125, y + dy + 3), fill=0)
    return image


@pytest.mark.parametrize('letters,expected', [('ㅈㄴ', '조금느리게'), ('ㅈㅅ', '조금속하게'),
    ('ㅈㅈㄴ', '점점느리게'), ('ㅈㅈㅅ', '점점속하게'), ('ㅂㅅ', '본래속도로')])
@needs_scan
def test_printed_jamo_and_continuation_dots(letters, expected):
    from hanbae_marks import image_marks
    marks = image_marks(sign_image(letters), [dict(x=10, y=20, w=100, h=160, gak=0)])
    assert [(m['symbol'], m.get('gak'), m.get('jeonggan')) for m in marks] == [(expected, 0, 0)]


@needs_scan
def test_unanchored_image_sign_is_not_moved_to_another_column():
    from hanbae_marks import image_marks
    marks = image_marks(sign_image('ㅈㄴ'), [dict(x=-60, y=20, w=100, h=160, gak=0)])
    assert marks[0]['symbol'] == '조금느리게'
    assert 'gak' not in marks[0]


def score():
    return dict(score=dict(instrument='SOGEUM'), music=dict(ticksPerBeat=480, ticksPerJeonggan=480),
        columns=[dict(index=0, cells=[dict(id=f'c0-j{i}', index=i,
            units=[dict(row=0, col=0, eventId='n1', symbol='임' if i == 0 else '-')]) for i in range(2)])],
        events=[dict(id='n1', kind='NOTE', cellId='c0-j0', yul='임', octave=0, startTick=0,
                     durationTick=960, sigimsae=['흘림표'], breathAfter=False)])


def test_unresolved_cue_on_a_held_cell_preserves_notes_and_explicitly_disables_guessing():
    from omr_service.jeongganbo_tempo import attach_marks
    source = score()
    before = deepcopy(source)
    cue = dict(symbol='조금느리게', rawText='ㅈㄴ', gak=0, jeonggan=1, page=0, bbox=[100, 20, 10, 25])
    extra, issues = attach_marks(source, [cue])
    assert source['events'][0] | {'sigimsae': before['events'][0]['sigimsae']} == before['events'][0]
    assert source['columns'] == before['columns']
    expression = extra['expressions']
    assert expression['performancePlan']['tempos'] == []
    assert expression['placements'] == [
        dict(eventId='n1', cellId='c0-j0', row=0, col=0, symbols=['흘림표']),
        dict(eventId='n1', cellId='c0-j1', row=0, col=0, symbols=['조금느리게'])]
    digest = hashlib.sha256(json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    assert expression['sourceSha256'] == expression['performancePlan']['input_sha256'] == digest
    assert issues[0]['cellId'] == 'c0-j1'
    assert issues[0]['detail']['reason'] == 'TEMPO_CUE_UNRESOLVED'


def test_no_sign_does_not_add_a_companion_or_change_the_score():
    from omr_service.jeongganbo_tempo import attach_marks
    source = score()
    before = deepcopy(source)
    assert attach_marks(source, []) == ({}, [])
    assert source == before


def test_an_existing_numeric_plan_and_other_mark_positions_survive():
    from omr_service.jeongganbo_tempo import attach_marks
    source = score()
    existing = dict(schema='common-score-expressions/v1', sourceSha256='old', placements=[
        dict(eventId='n1', cellId='c0-j1', row=0, col=0, symbols=['흘림표'])],
        performancePlan=dict(schema='sogeum-performance/v1', input_sha256='old',
                             tempos=[dict(gak=1, cell=1, bpm=90)], repeats=[dict(start_gak=1, end_gak=1, times=2)]))
    before = deepcopy(existing)
    extra, _ = attach_marks(source, [dict(symbol='점점속하게', rawText='ㅈㅈㅅ', gak=0, jeonggan=1)], existing)
    assert extra['expressions']['performancePlan']['tempos'] == before['performancePlan']['tempos']
    assert extra['expressions']['performancePlan']['repeats'] == before['performancePlan']['repeats']
    assert extra['expressions']['placements'][0]['cellId'] == 'c0-j1'
    assert existing == before


def test_an_existing_repeat_is_not_lost_when_only_the_tempo_is_pending():
    from omr_service.jeongganbo_tempo import attach_marks
    source = score()
    source['events'][0]['sigimsae'].append('도돌이표')
    extra, _ = attach_marks(source, [dict(symbol='조금느리게', rawText='ㅈㄴ', gak=0, jeonggan=1)])
    assert extra['expressions']['performancePlan']['repeats'] == [dict(start_gak=1, end_gak=1, times=2)]


@needs_scan
def test_jamo_without_a_dotted_continuation_are_not_claimed_as_an_image_cue():
    from hanbae_marks import image_marks
    image = sign_image('ㅈㄴ')
    ImageDraw.Draw(image).rectangle((0, 106, 199, 239), fill='white')
    assert image_marks(image, [dict(x=10, y=20, w=100, h=160, gak=0)]) == []


@needs_scan
def test_original_tempo_can_end_after_the_letters_in_a_unique_cell_margin():
    from hanbae_marks import image_marks
    image = sign_image('ㅂㅅ')
    ImageDraw.Draw(image).rectangle((0, 90, 199, 239), fill='white')
    cells = [dict(x=10, y=20, w=100, h=160, gak=0)]
    marks = image_marks(image, cells)
    assert [(m['symbol'], m.get('gak'), m.get('jeonggan')) for m in marks] == [('본래속도로', 0, 0)]
    # Without the continuation, do not claim an unanchored pair or one inside a melody cell.
    assert image_marks(image, [dict(x=-60, y=20, w=100, h=160, gak=0)]) == []
    assert image_marks(image, [dict(x=100, y=20, w=100, h=160, gak=0)]) == []
    assert image_marks(image, cells + [cells[0] | {'gak': 1}]) == []


@needs_scan
def test_original_tempo_retains_a_thin_middle_crossbar():
    from hanbae_marks import image_marks
    image = sign_image('ㅂㅅ')
    draw = ImageDraw.Draw(image)
    draw.rectangle((117, 40, 131, 44), fill='white')
    draw.line((115, 42, 133, 42), fill=0, width=1)
    marks = image_marks(image, [dict(x=10, y=20, w=100, h=160, gak=0)])
    assert [(m['symbol'], m.get('gak'), m.get('jeonggan')) for m in marks] == [('본래속도로', 0, 0)]


@pytest.mark.skipif(not os.getenv('JGB_GENERATOR_DIR'), reason='Node 변환기 필요')
@pytest.mark.parametrize('letters,expected', [('ㅈㄴ', '조금느리게'), ('ㅈㅅ', '조금속하게'),
    ('ㅈㅈㄴ', '점점느리게'), ('ㅈㅈㅅ', '점점속하게'), ('ㅂㅅ', '본래속도로')])
def test_real_text_pdf_pipeline_keeps_the_sign_and_does_not_choose_its_target(letters, expected):
    import pymupdf
    from test_omr_text_engine import pdf_bytes, post, SEMACHI
    original = pdf_bytes(SEMACHI)
    with pymupdf.open(stream=original) as doc:
        for i, c in enumerate('·'.join(letters)):
            doc[0].insert_text((505, 51 + i * 7), c, fontsize=6, fontname='korea')
        data = doc.tobytes()
    before = post(original).json()
    result = post(data).json()
    assert result['status'] == 'DONE', result
    assert result['score']['columns'] == before['score']['columns']
    changed = deepcopy(result['score']['events'])
    for event in changed:
        event['sigimsae'] = []
    assert changed == before['score']['events']
    assert result['expressions']['placements'][0]['symbols'] == [expected]
    assert result['expressions']['performancePlan']['tempos'] == []
    assert result['issues'][-1]['detail']['reason'] == 'TEMPO_CUE_UNRESOLVED'
    assert result['issues'][-1]['cellId'] == 'c0-j0'


def test_conflicting_new_signs_do_not_make_the_unresolved_preview_unplayable():
    from omr_service.jeongganbo_tempo import attach_marks
    source = score()
    before = deepcopy(source)
    extra, issues = attach_marks(source, [dict(symbol=name, rawText=raw, gak=0, jeonggan=1)
        for name, raw in [('조금느리게', 'ㅈㄴ'), ('조금속하게', 'ㅈㅅ')]])
    assert extra == {}
    assert source == before
    assert [issue['detail']['reason'] for issue in issues] == ['TEMPO_CUE_CONFLICT'] * 2


def test_two_unanchored_signs_on_different_pages_are_both_kept_for_review():
    from omr_service.jeongganbo_tempo import attach_marks
    _, issues = attach_marks(score(), [dict(symbol='조금느리게', rawText='ㅈㄴ', page=page, bbox=[1, 2, 3, 4])
                                      for page in (0, 1)])
    assert [issue['page'] for issue in issues] == [0, 1]
