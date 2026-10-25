import unittest
import xml.etree.ElementTree as ET
import tempfile
from pathlib import Path
from unittest.mock import patch
import zipfile
import numpy as np
from PIL import Image, ImageDraw

from playback.staff import parse_musicxml
from playback.staff_rhythm import apply_tuplet, _grace_geometry, _grace_accidental, _small_flagged_grace, enrich_rhythm
from playback.recognition import RecognitionError


CONTEXT = {'pitch_mode': 'sounding', 'notation_octave_shift': 0, 'tuning_a_hz': 440,
           'style': 'test', 'tuning_description': 'A440 comparison'}


def document(body):
    return ('<score-partwise><part><measure><attributes><divisions>8</divisions>'
            '<key><fifths>-5</fifths></key><clef><sign>G</sign><line>2</line></clef>'
            '</attributes>'+body+'</measure></part></score-partwise>').encode()


def glyph_from_mask(mask):
    height,width=mask.shape
    glyph=ET.Element('glyph',left='10',top='90')
    table=ET.SubElement(glyph,'run-table',orientation='VERTICAL',width=str(width),height=str(height))
    for column in mask.T:
        runs=[];state=True;length=0
        for pixel in column:
            if pixel==state:length+=1
            else:runs.append(length);length=1;state=not state
        runs.append(length);ET.SubElement(table,'runs').text=' '.join(map(str,runs))
    return glyph


class OrnamentPlaybackTests(unittest.TestCase):
    def test_upper_mordent_uses_explicit_pitch_classes_and_fixed_preview_policy(self):
        xml = document('<note><pitch><step>B</step><alter>-1</alter><octave>4</octave></pitch>'
                       '<duration>8</duration><notations><ornaments><inverted-mordent/>'
                       '</ornaments></notations></note>')
        parsed = parse_musicxml(xml, {**CONTEXT, 'ornament_pitch_classes': [1,3,5,8,10]})
        self.assertEqual(parsed['notes'][0]['ornament'], {'kind':'upper-return','pitch':73,'duration':'1/16'})
        self.assertEqual(parsed['notes'][0]['duration'], '1')
        standard = parse_musicxml(xml, CONTEXT)
        self.assertEqual(standard['notes'][0]['ornament']['pitch'],72)

    def test_single_grace_preserves_head_pitch_and_main_duration(self):
        xml = document('<note><grace/><pitch><step>F</step><octave>4</octave></pitch><type>eighth</type></note>'
                       '<note><pitch><step>E</step><alter>-1</alter><octave>4</octave></pitch><duration>6</duration></note>')
        parsed = parse_musicxml(xml, CONTEXT)
        self.assertEqual(len(parsed['notes']),1)
        self.assertEqual(parsed['notes'][0]['ornament'], {'kind':'grace-before','pitch':65,'duration':'1/16'})
        self.assertEqual(parsed['notes'][0]['duration'],'3/4')
        self.assertEqual(parsed['metadata']['omitted_grace_notes'],0)


class TupletTests(unittest.TestCase):
    def test_explicit_triplet_changes_only_selected_durations_and_is_not_applied_twice(self):
        root=ET.fromstring(document('<note><pitch><step>C</step><octave>5</octave></pitch><duration>6</duration></note>'+''.join(
            '<note><pitch><step>D</step><octave>5</octave></pitch><duration>2</duration><type>16th</type></note>' for _ in range(3))))
        changed=apply_tuplet(root,[1,2,3],3)
        parsed=parse_musicxml(ET.tostring(changed),CONTEXT)
        self.assertEqual([n['duration'] for n in parsed['notes']],['3/4','1/6','1/6','1/6'])
        self.assertEqual(root.findtext('.//divisions'),'8')
        with self.assertRaises(RecognitionError):apply_tuplet(changed,[1,2,3],3)

    def test_wrong_group_count_does_not_rewrite_xml(self):
        root=ET.fromstring(document('<note><pitch><step>C</step><octave>5</octave></pitch><duration>2</duration></note>'*2))
        with self.assertRaises(RecognitionError):apply_tuplet(root,[0,1],3)


class GeometryGuardsTests(unittest.TestCase):
    def test_grace_accidental_carry_and_unbound_sign_guard(self):
        root=ET.fromstring(document('<note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch><duration>8</duration><accidental>sharp</accidental></note>'
            '<note><pitch><step>E</step><octave>4</octave></pitch><duration>8</duration></note>'))
        glyph={'id':'g','box':[100,80,120,130]}
        geometry={'step':'F','octave':4,'alter':0,'head_y':120}
        graph=ET.fromstring('<sheet/>')
        result=_grace_accidental(root,1,glyph,geometry,graph,[],10)
        self.assertEqual(result['alter'],1)
        self.assertEqual(geometry['alter'],0)
        unknown={'id':'unknown','box':[85,105,95,130],'w':10,'h':25}
        with self.assertRaisesRegex(RecognitionError,'accidental'):
            _grace_accidental(root,1,glyph,geometry,graph,[unknown],10)

    def test_multiple_systems_preserve_score_without_model_calls(self):
        with tempfile.TemporaryDirectory() as tmp:
            omr=Path(tmp)/'test.omr'
            with zipfile.ZipFile(omr,'w') as z:z.writestr('sheet#1/sheet#1.xml','<sheet><staff/><staff/></sheet>')
            root=ET.fromstring(document('<note><pitch><step>C</step><octave>5</octave></pitch><duration>8</duration></note>'))
            with patch('playback.staff_rhythm._chat',side_effect=AssertionError('must not infer')):
                result,report=enrich_rhythm(root,Path(tmp)/'unused.png',omr,CONTEXT,model='local')
            self.assertEqual(ET.tostring(result),ET.tostring(root))
            self.assertFalse(report['corrections'])
            self.assertTrue(any('one staff/system' in w for w in report['warnings']))

    def test_grace_notehead_position_determines_pitch_without_melody_reference(self):
        image=Image.new('1',(30,50));draw=ImageDraw.Draw(image)
        draw.rectangle((15,0,17,45),fill=1)
        draw.ellipse((0,39,15,49),fill=1)
        draw.line((17,2,26,13,26,23),fill=1,width=2)
        mask=np.array(image,dtype=bool)
        glyph=ET.Element('glyph',left='10',top='90')
        table=ET.SubElement(glyph,'run-table',orientation='VERTICAL',width='30',height='50')
        for column in mask.T:
            runs=[];state=True;length=0
            for pixel in column:
                if pixel==state:length+=1
                else:runs.append(length);length=1;state=not state
            runs.append(length);ET.SubElement(table,'runs').text=' '.join(map(str,runs))
        graph=ET.fromstring('<sheet><staff><lines>'+''.join('<line><point y="'+str(y)+'"/></line>' for y in [100,110,120,130,140])+'</lines></staff></sheet>')
        root=ET.fromstring(document(''))
        ET.SubElement(root.find('.//clef'),'clef-octave-change').text='1'
        result=_grace_geometry(glyph,graph,root,10)
        self.assertEqual((result['step'],result['alter'],result['octave']),('F',0,5))
        self.assertEqual(result['stem_direction'],'up')

    def test_character_stroke_and_flag_only_are_not_grace_notes(self):
        graph=ET.fromstring('<sheet><staff><lines>'+''.join('<line><point y="'+str(y)+'"/></line>' for y in [100,110,120,130,140])+'</lines></staff></sheet>')
        root=ET.fromstring(document(''))
        heads=[ET.fromstring('<head><bounds x="0" y="0" w="28" h="24"/></head>')]
        for shape in ('character','stroke','flag'):
            image=Image.new('1',(30,50));draw=ImageDraw.Draw(image)
            draw.rectangle((15,0,17,45),fill=1)
            if shape=='character':
                for y in (0,22,42):draw.rectangle((2,y,17,y+4),fill=1)
            elif shape=='flag':draw.line((17,2,26,13,26,23),fill=1,width=2)
            with self.subTest(shape=shape):
                try:result=_grace_geometry(glyph_from_mask(np.array(image,dtype=bool)),graph,root,10)
                except RecognitionError:continue
                self.assertFalse(_small_flagged_grace(result,heads))

    def test_multiple_graces_remain_visible_as_unsupported(self):
        grace='<note><grace/><pitch><step>F</step><octave>4</octave></pitch></note>'
        parsed=parse_musicxml(document(grace*2+'<note><pitch><step>E</step><octave>4</octave></pitch><duration>8</duration></note>'),CONTEXT)
        self.assertNotIn('ornament',parsed['notes'][0])
        self.assertEqual(parsed['metadata']['omitted_grace_notes'],2)


if __name__ == '__main__':
    unittest.main()
