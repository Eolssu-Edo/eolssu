import io
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from types import SimpleNamespace

import pytest

from omr_service.staff_pdf_tempo import read_marks, supplement
from test_staff_musicxml import xml, measure, note


def text_pdf(text):
    """Self-authored Type3 PDF with explicit Unicode mapping; no source/font asset."""
    chars = list(dict.fromkeys(text.replace('\n', '')))
    codes = {char: i + 1 for i, char in enumerate(chars)}
    cmap = ('/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n'
            '/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n'
            '/CMapName /Test-UCS def /CMapType 2 def\n'
            '1 begincodespacerange <00> <FF> endcodespacerange\n'
            f'{len(chars)} beginbfchar\n' + '\n'.join(
                f'<{codes[c]:02X}> <{ord(c):04X}>' for c in chars) +
            '\nendbfchar endcmap CMapName currentdict /CMap defineresource pop end end')
    content = '\n'.join(
        f'BT /F1 10 Tf 1 0 0 1 30 {120 - i * 20} Tm <' +
        ''.join(f'{codes[c]:02X}' for c in line) + '> Tj ET'
        for i, line in enumerate(text.split('\n')))

    def stream(value):
        value = value.encode('ascii')
        return f'<< /Length {len(value)} >>\nstream\n'.encode() + value + b'\nendstream'

    objects = [b'<< /Type /Catalog /Pages 2 0 R >>',
               b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 200] '
               b'/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
               (f'<< /Type /Font /Subtype /Type3 /FontBBox [0 0 600 800] '
                f'/FontMatrix [.001 0 0 .001 0 0] /FirstChar 1 /LastChar {len(chars)} '
                f'/Widths [{"600 " * len(chars)}] /Encoding << /Type /Encoding '
                f'/Differences [1 {"/g " * len(chars)}] >> /CharProcs << /g 6 0 R >> '
                f'/ToUnicode 7 0 R >>').encode(),
               stream(content), stream('600 0 0 0 600 800 d1 0 0 600 800 re f'), stream(cmap)]
    data = b'%PDF-1.4\n'
    offsets = [0]
    for i, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data += f'{i} 0 obj\n'.encode() + obj + b'\nendobj\n'
    xref = len(data)
    data += b'xref\n0 8\n0000000000 65535 f \n'
    data += b''.join(f'{offset:010d} 00000 n \n'.encode() for offset in offsets[1:])
    return data + f'trailer << /Size 8 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF'.encode()


def marks_from_pdf(tmp_path, text, width=600):
    path = tmp_path / 'self-authored.pdf'
    path.write_bytes(text_pdf(text))
    return read_marks(SimpleNamespace(kind='PDF', files=[path], pages=[SimpleNamespace(width=width)]))[0]


@pytest.mark.parametrize('glyph,dot,dots', [('♩', '', 0), ('q', '.', 1),
                                          ('\ueca5', '', 0), ('\ueca6', '', 0),
                                          ('\ueca5', '\uecb7', 1)])
def test_actual_pdf_text_recovers_explicit_quarter_units_and_scaled_location(tmp_path, glyph, dot, dots):
    marks = marks_from_pdf(tmp_path, f'{glyph}{dot} = 100', width=1200)
    assert marks == [{'x': 60, 'bottom': 160, 'bpm': '100', 'dots': dots, 'accel': False}]


@pytest.mark.parametrize('text', ['Moderato 100', 'seq = 100', 'q = 100–120', 'q = 100/2',
    'q = 100,5', 'q = 100foo', 'q =\n100', 'q = 1.2.3', 'q = 100 120',
    '\ueca3 = 100', '\ueca7 = 100', '♪ = 100', '\ue000 = 100'])
def test_actual_pdf_text_does_not_guess_numbers_or_other_note_units(tmp_path, text):
    assert marks_from_pdf(tmp_path, text) == []


def test_actual_pdf_decimal_and_accel_keep_the_original_value(tmp_path):
    marks = marks_from_pdf(tmp_path, '♩ = 100.5 accel.')
    assert marks[0]['bpm'] == '100.5' and marks[0]['accel']


def test_supplied_auclair_pdf_opt_in():
    import os
    path = os.environ.get('AUCLAIR_TEMPO_PDF')
    if not path:
        pytest.skip('Set AUCLAIR_TEMPO_PDF to the local supplied PDF; never commit it.')
    import pypdfium2 as pdfium
    with pdfium.PdfDocument(path) as doc:
        page = doc[0]
        width = page.get_width()
        page.close()
    marks = read_marks(SimpleNamespace(kind='PDF', files=[Path(path)], pages=[SimpleNamespace(width=width)]))
    assert len(marks) == 1 and len(marks[0]) == 1
    assert marks[0][0] == {'x': pytest.approx(148.536, abs=.01),
                          'bottom': pytest.approx(101.729, abs=.01),
                          'bpm': '100', 'dots': 0, 'accel': False}


def archive(systems=None):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as z:
        z.writestr('sheet#1/sheet#1.xml',systems or '''<sheet><system><stack left="10" right="100"><slot x-offset="20" time-offset="0"/></stack>
        <stack left="100" right="200"><slot x-offset="15" time-offset="0"/></stack>
        <part><staff><lines><line><point x="10" y="100"/></line><line><point x="10" y="110"/></line></lines></staff></part>
        </system></sheet>''')
    return stream.getvalue()


# Self-authored geometry reduced from the supplied PDF's key/time-signature header.
HEADER_SYSTEM = '''<system><stack left="220" right="801"><slot x-offset="233" time-offset="0"/>
<slot x-offset="334" time-offset="3/8"/></stack><stack left="801" right="1118"><slot x-offset="44" time-offset="0"/></stack>
<part><staff><lines><line><point x="220" y="915"/></line><line><point x="220" y="934"/></line></lines></staff></part></system>'''
HEADER_MARK = {'x':328.346, 'bottom':849.994, 'bpm':'160', 'dots':0, 'accel':False}


def test_header_tempo_before_the_first_attack_keeps_the_written_notes():
    source = xml(measure(note('C4',16),time=(4,4)),measure(note('D4',16)))
    actual,count = supplement(source,archive('<sheet>'+HEADER_SYSTEM+'</sheet>'),[HEADER_MARK])
    root = ET.fromstring(actual)
    assert count == 1
    assert root.find('part/measure').findtext('direction/direction-type/metronome/per-minute') == '160'
    assert [ET.tostring(n) for n in root.findall('part/measure/note')] == [ET.tostring(n) for n in ET.fromstring(source).findall('part/measure/note')]


def test_header_allowance_keeps_midmeasure_and_ambiguous_marks_unbound():
    source = xml(measure(note('C4',16),time=(4,4)),measure(note('D4',16)))
    geometry = archive('<sheet>'+HEADER_SYSTEM+'</sheet>')
    assert supplement(source,geometry,[{**HEADER_MARK,'x':554}])[1] == 0  # later 3/8 slot
    assert supplement(source,geometry,[{**HEADER_MARK,'x':195}])[1] == 0  # outside the bar
    no_zero = archive('<sheet>'+HEADER_SYSTEM.replace('time-offset="0"','time-offset="1/4"')+'</sheet>')
    assert supplement(source,no_zero,[HEADER_MARK])[1] == 0
    overlapping = HEADER_SYSTEM.replace('y="915"','y="990"').replace('y="934"','y="1009"')
    ambiguous = archive('<sheet>'+HEADER_SYSTEM+overlapping+'</sheet>')
    four = xml(*(measure(note('C4',16),time=(4,4)) for _ in range(4)))
    assert supplement(four,ambiguous,[HEADER_MARK])[1] == 0


def test_header_pdf_mark_does_not_replace_a_conflicting_explicit_number():
    from test_staff_tempo import tempo
    source = xml(measure(tempo('80'),note('C4',16),time=(4,4)),measure(note('D4',16)))
    actual,count = supplement(source,archive('<sheet>'+HEADER_SYSTEM+'</sheet>'),[HEADER_MARK])
    assert count == 0
    assert ET.fromstring(actual).find('part/measure').findtext('direction/direction-type/metronome/per-minute') == '80'


def test_text_layer_recovers_only_unambiguous_measure_start_and_keeps_notes():
    source=xml(measure(note('C4',16),time=(4,4)),measure(note('D4',16)))
    mark={'x':112,'bottom':80,'bpm':'160','dots':0,'accel':False}
    actual,count=supplement(source,archive(),[mark])
    root=ET.fromstring(actual)
    assert count==1
    assert root.findall('part/measure')[1].findtext('direction/direction-type/metronome/per-minute')=='160'
    assert [ET.tostring(n) for n in root.findall('part/measure/note')] == [ET.tostring(n) for n in ET.fromstring(source).findall('part/measure/note')]
    assert supplement(source,archive(),[{**mark,'x':170}])[1]==0
    assert supplement(source,archive(),[{**mark,'bottom':200}])[1]==0
    assert supplement(source,archive(),[{**mark,'x':98}])[1]==1  # engraved note glyph can precede the barline


def test_existing_musicxml_tempo_is_not_duplicated_by_pdf_text():
    source=xml(measure('<direction><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>80</per-minute></metronome></direction-type></direction>',note('C4',16)),measure(note('D4',16)))
    actual,count=supplement(source,archive(),[{'x':30,'bottom':80,'bpm':'80','dots':0,'accel':False}])
    assert count==0 and len(ET.fromstring(actual).findall('part/measure/direction'))==1


@pytest.mark.parametrize('values', [('100', '120'), ('120', '100')])
def test_conflicting_pdf_numbers_at_one_measure_are_not_selected_by_order(values):
    source = xml(measure(note('C4',16),time=(4,4)),measure(note('D4',16)))
    marks = [{'x':30, 'bottom':80, 'bpm':bpm, 'dots':0, 'accel':False} for bpm in values]
    actual, count = supplement(source, archive(), marks)
    assert count == 0
    assert ET.fromstring(actual).findall('.//metronome') == []


def test_duplicate_equivalent_pdf_marks_are_applied_once_and_other_measures_survive():
    source = xml(measure(note('C4',16),time=(4,4)),measure(note('D4',16)))
    mark = {'x':30, 'bottom':80, 'bpm':'100', 'dots':0, 'accel':False}
    actual, count = supplement(source, archive(), [mark, {**mark, 'bpm':'100.0'},
                              {**mark, 'x':112, 'bpm':'80'}])
    from omr_service.staff_musicxml import convert
    assert count == 2
    assert convert(actual, instrument='DANSO', hwang_pitch=60).tempos == [
        {'gak':1, 'cell':1, 'bpm':100}, {'gak':2, 'cell':1, 'bpm':80}]


def test_conflicting_pdf_marks_cannot_relocate_an_existing_musicxml_value():
    from test_staff_tempo import tempo
    source = xml(measure(note('C4',8),tempo('100'),note('D4',8)),measure(note('E4',16)))
    marks = [{'x':30, 'bottom':80, 'bpm':bpm, 'dots':0, 'accel':False} for bpm in ('100','120')]
    actual, count = supplement(source, archive(), marks)
    assert count == 0
    assert ET.tostring(ET.fromstring(actual)) == ET.tostring(ET.fromstring(source))


def test_verified_first_slot_corrects_a_numeric_direction_exported_after_two_beats():
    from test_staff_tempo import tempo
    source=xml(measure(note('C4',8),tempo('80 molto accel. _'),note('D4',8)),measure(note('E4',16)))
    actual,count=supplement(source,archive(),[{'x':30,'bottom':80,'bpm':'80','dots':0,'accel':True}])
    first=ET.fromstring(actual).find('part/measure')
    assert count==1 and first[0].tag=='direction'
    assert len(first.findall('direction'))==1


def test_pdf_accel_text_supplements_an_existing_numeric_mark_without_duplicate_tempo():
    from test_staff_tempo import tempo
    source=xml(measure(tempo('80'),note('C4',16)),measure(tempo('160'),note('D4',16)))
    actual,count=supplement(source,archive(),[{'x':30,'bottom':80,'bpm':'80','dots':0,'accel':True}])
    first=ET.fromstring(actual).find('part/measure')
    from omr_service.staff_musicxml import _tempo_direction
    assert count==1 and _tempo_direction(first.find('direction'))['accel']
    assert len(first.findall('direction'))==1
