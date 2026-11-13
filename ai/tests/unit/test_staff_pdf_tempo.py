import io
import zipfile
import xml.etree.ElementTree as ET
from omr_service.staff_pdf_tempo import supplement
from test_staff_musicxml import xml, measure, note


def archive():
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as z:
        z.writestr('sheet#1/sheet#1.xml','''<sheet><system><stack left="10" right="100"><slot x-offset="20" time-offset="0"/></stack>
        <stack left="100" right="200"><slot x-offset="15" time-offset="0"/></stack>
        <part><staff><lines><line><point x="10" y="100"/></line><line><point x="10" y="110"/></line></lines></staff></part>
        </system></sheet>''')
    return stream.getvalue()


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
