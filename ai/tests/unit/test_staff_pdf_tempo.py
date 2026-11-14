import io
import zipfile
import xml.etree.ElementTree as ET
from omr_service.staff_pdf_tempo import supplement
from test_staff_musicxml import xml, measure, note


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
