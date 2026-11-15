"""Self-authored PDF/OMR cases for tempo digits mistaken for tuplet signs."""
import io
import os
import subprocess
import zipfile
from fractions import Fraction
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

from omr_service import api, staff_engine, staff_pdf_tempo
from test_staff_pdf_tempo import text_pdf


def omr_tuplet(x=250, y=300, width=25, height=34, shape='TUPLET_SIX', top=400):
    sheet = f'''<sheet><system>
      <stack left="100" right="1800"><slot x-offset="300" time-offset="0"/></stack>
      <part><staff id="1"><lines><line><point x="100" y="{top}"/></line>
      <line><point x="100" y="{top+40}"/></line></lines></staff></part>
      <sig><inters><tuplet shape="{shape}" staff="1" id="99">
      <bounds x="{x}" y="{y}" w="{width}" h="{height}"/></tuplet></inters></sig>
      </system></sheet>'''
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as z:
        z.writestr('sheet#1/sheet#1.xml', sheet)
    return stream.getvalue()


def rhythm_xml(*, false_tuplet, eight_notes=False):
    duration = 2 if false_tuplet else 3
    modification = ('<time-modification><actual-notes>6</actual-notes>'
                    '<normal-notes>4</normal-notes></time-modification>') if false_tuplet else ''
    eighth = '<note><pitch><step>C</step><octave>5</octave></pitch><duration>3</duration><voice>1</voice><type>eighth</type><staff>1</staff></note>'
    return ('''<score-partwise><part id="P1"><measure number="1"><attributes>
        <divisions>6</divisions><time><beats>4</beats><beat-type>4</beat-type></time>
        </attributes>'''
        + ('' if eight_notes else '<note><rest/><duration>6</duration><voice>1</voice><type>quarter</type><staff>1</staff></note>')
        + ''.join(f'''<note><pitch><step>A</step><octave>4</octave></pitch>
          <duration>{duration}</duration><voice>1</voice><type>eighth</type>{modification}<staff>1</staff></note>'''
                  for _ in range(6))
        + (eighth * 2 if eight_notes else '')
        + f'''<backup><duration>{6+duration*6}</duration></backup><note><pitch><step>A</step><octave>2</octave>
          </pitch><duration>24</duration><voice>5</voice><type>whole</type><staff>2</staff></note>
          </measure></part></score-partwise>''').encode()


def bounded_marks(tmp_path, text='q = 160'):
    source = tmp_path / 'marks.pdf'
    source.write_bytes(text_pdf(text))
    return staff_pdf_tempo.read_marks(SimpleNamespace(kind='PDF', files=[source],
        pages=[SimpleNamespace(width=2500)]), with_bounds=True)[0]


@pytest.mark.parametrize('text,shape', [('q = 160', 'TUPLET_SIX'), ('q = 130', 'TUPLET_THREE')])
def test_only_matching_numeric_glyph_is_evidence_to_mask_the_complete_tempo(tmp_path, text, shape):
    marks = bounded_marks(tmp_path, text)
    groups = staff_pdf_tempo.tuplet_regions(omr_tuplet(shape=shape), marks)
    assert len(groups) == 1 and len(groups[0]) == 5  # q, =, and all3 digits; no spaces.
    assert any(x <= 260 <= right and y <= 315 <= bottom for x, y, right, bottom in groups[0])
    assert any(x <= 285 <= right and y <= 315 <= bottom for x, y, right, bottom in groups[0])


@pytest.mark.parametrize('overrides', [
    {'x': 285},  # A genuine tuplet next to the tempo, not the digit6 itself.
    {'y': 350}, {'width': 28}, {'shape': 'TUPLET_THREE'},
    {'shape': 'OTHER'}, {'top': 330},  # The mark overlaps the actual staff.
])
def test_other_tuplets_and_ambiguous_geometry_are_not_masked(tmp_path, overrides):
    assert staff_pdf_tempo.tuplet_regions(omr_tuplet(**overrides), bounded_marks(tmp_path)) == []


def test_missing_or_duplicate_pdf_evidence_cannot_remove_a_tuplet(tmp_path):
    marks = bounded_marks(tmp_path)
    archive = omr_tuplet()
    assert staff_pdf_tempo.tuplet_regions(archive, []) == []
    assert staff_pdf_tempo.tuplet_regions(archive, [{k:v for k,v in marks[0].items() if k != 'glyphs'}]) == []
    assert staff_pdf_tempo.tuplet_regions(archive, marks + marks) == []
    assert staff_pdf_tempo.read_marks(SimpleNamespace(kind='IMAGE'), with_bounds=True) == []


def test_ink_between_separated_tempo_characters_is_preserved(tmp_path):
    marks = bounded_marks(tmp_path, 'q      =    160')
    six = next(g['bounds'] for g in marks[0]['glyphs'] if g['text'] == '6')
    group = staff_pdf_tempo.tuplet_regions(omr_tuplet(x=round(six[0]), y=round(six[1]),
        width=round(six[2]-six[0]), height=round(six[3]-six[1])), marks)[0]
    image = Image.new('RGB', (800, 500), 'black')
    draw = ImageDraw.Draw(image)
    for box in group:
        draw.rectangle(box, fill='white')
    assert image.getpixel((200, 315)) == (0, 0, 0)  # Ink between unit andequals.
    assert image.getpixel((135, 315)) == (255, 255, 255)


@pytest.mark.skipif(not os.environ.get('JGB_GENERATOR_DIR'), reason='JGB_GENERATOR_DIR is required')
@pytest.mark.parametrize('tuplet_x,needs_mask,eight_notes', [(250, True, False), (375, False, False), (250, True, True)])
def test_only_pdf_tempo_digit_tuplet_is_re_read_before_conversion(tmp_path, monkeypatch, tuplet_x, needs_mask, eight_notes):
    source = text_pdf('q = 160')
    runs = []

    def run(command, deadline):
        image_path = Path(command[-1])
        corrected = image_path.name == 'cleaned-0.png'
        with Image.open(image_path) as image:
            # The numeric6 ink is black on the original render and white after masking.
            assert image.convert('RGB').getpixel((260, 315)) == ((255, 255, 255) if corrected else (0, 0, 0))
        runs.append(corrected)
        out = Path(command[command.index('-output') + 1])
        out.mkdir()
        out.joinpath('score.musicxml').write_bytes(rhythm_xml(false_tuplet=not corrected, eight_notes=eight_notes))
        out.joinpath('score.omr').write_bytes(omr_tuplet(x=tuplet_x))
        return subprocess.CompletedProcess(command, 0, b'', b'')

    def fetch(url, dest, max_bytes, deadline):
        dest.write_bytes(source)

    monkeypatch.setattr(staff_engine, '_run', run)
    monkeypatch.setattr(staff_engine, 'AUDIVERIS_LOGS', tmp_path)
    api.app.dependency_overrides[api.get_fetcher] = lambda: fetch
    try:
        response = TestClient(api.app).post('/v1/recognitions', json={
            'conversionId': 'self-authored-tempo-tuplet', 'notation': 'STAFF', 'instrument': 'SANJO_DAEGEUM',
            'files': [{'url': 'https://example.test/source.pdf', 'contentType': 'application/pdf'}]})
    finally:
        api.app.dependency_overrides.clear()
    result = response.json()
    assert response.status_code == 200 and result['status'] == 'DONE'
    score = result['score']
    tpb = score['music']['ticksPerBeat']
    expected = ([] if eight_notes else [('REST', Fraction(1))])
    expected += [('NOTE', Fraction(1, 2) if needs_mask else Fraction(1, 3))] * 6
    if eight_notes:
        expected += [('NOTE', Fraction(1, 2))] * 2
    if not needs_mask:
        expected.append(('REST', Fraction(1)))
    assert [(e['kind'], Fraction(e['durationTick'], tpb)) for e in score['events']] == expected
    assert runs == ([False, True] if needs_mask else [False])
    assert result['bpm'] == 160
    assert len(result['sourcePitches']) == (8 if eight_notes else 6)
