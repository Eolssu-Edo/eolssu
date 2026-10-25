from copy import deepcopy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image, ImageDraw
import numpy as np

from playback.recognition import RecognitionError
from playback.staff_metadata import apply_metadata, apply_tempo, enrich_musicxml, _remove_confirmed_header_flats, _octave_mark_evidence


KEY = {"sign": "flat", "count": 5, "uncertain": False}
CLEF = {"numeral": "8", "position": "above", "uncertain": False}


def score():
    return ET.fromstring('''<score-partwise><part id="P1"><measure number="1">
    <attributes><divisions>4</divisions><key><fifths>-2</fifths></key>
    <clef><sign>G</sign><line>2</line></clef></attributes>
    <note><pitch><step>D</step><octave>5</octave></pitch><duration>4</duration></note>
    <note><pitch><step>A</step><alter>0</alter><octave>4</octave></pitch><duration>4</duration><accidental>natural</accidental></note>
    <note><pitch><step>A</step><alter>0</alter><octave>4</octave></pitch><duration>4</duration></note>
    <note><pitch><step>A</step><octave>5</octave></pitch><duration>4</duration></note>
    </measure><measure number="2"><note><pitch><step>A</step><octave>4</octave></pitch><duration>4</duration></note>
    </measure></part></score-partwise>''')


class MetadataTests(unittest.TestCase):
    def test_key_correction_preserves_local_accidentals_and_measure_carry(self):
        original = score()
        before = ET.tostring(original)
        changed, report = apply_metadata(original, KEY, CLEF)
        self.assertEqual(ET.tostring(original), before)
        self.assertEqual(changed.findtext('.//fifths'), '-5')
        self.assertEqual(changed.findtext('.//clef-octave-change'), '1')
        self.assertEqual([p.findtext('octave') for p in changed.findall('.//pitch')], ['6','5','5','6','5'])
        self.assertEqual([n.findtext('pitch/alter','0') for n in changed.findall('.//note')], ['-1','0','0','-1','-1'])
        self.assertEqual(len(changed.findall('.//note')), 5)
        self.assertTrue(report['corrections'])

    def test_existing_clef_shift_is_not_applied_twice(self):
        original = score()
        ET.SubElement(original.find('.//clef'), 'clef-octave-change').text = '1'
        changed, _ = apply_metadata(original, KEY, CLEF)
        self.assertEqual(changed.findtext('.//clef-octave-change'), '1')
        self.assertEqual([p.findtext('octave') for p in changed.findall('.//pitch')], ['5','4','4','5','4'])

    def test_uncertain_metadata_never_changes_music(self):
        original = score()
        key, clef = {**KEY,'uncertain':True}, {**CLEF,'uncertain':True}
        changed, report = apply_metadata(original, key, clef)
        self.assertEqual(ET.tostring(changed), ET.tostring(original))
        self.assertFalse(report['corrections'])
        self.assertTrue(report['warnings'])

    def test_unexplained_alteration_and_midscore_key_change_are_preserved(self):
        original = score()
        ET.SubElement(original.find('.//pitch'), 'alter').text = '1'
        changed, report = apply_metadata(original, KEY, CLEF)
        self.assertEqual(changed.findtext('.//pitch/alter'), '1')
        self.assertTrue(report['warnings'])
        second = original.findall('.//measure')[1]
        attrs = ET.SubElement(second,'attributes')
        ET.SubElement(ET.SubElement(attrs,'key'),'fifths').text = '1'
        changed, report = apply_metadata(original, KEY, CLEF)
        self.assertEqual([e.text for e in changed.findall('.//fifths')], ['-2','1'])

    def test_graph_crops_and_provider_failure_preserve_raw_notes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); image=root/'photo.png'; omr=root/'photo.omr'
            Image.new('RGB',(600,350),'white').save(image)
            graph='''<sheet><picture width="600" height="350"/><scale><interline main="10"/></scale>
            <page><system><part><staff id="1"><lines><line><point x="0" y="150"/></line><line><point x="0" y="190"/></line></lines></staff></part>
            <sig><inters><clef kind="TREBLE" id="1"><bounds x="25" y="120" w="40" h="140"/></clef>
            <head id="2" ctx-grade="0.9"><bounds x="300" y="160" w="12" h="10"/></head></inters></sig>
            </system></page></sheet>'''
            with zipfile.ZipFile(omr,'w') as archive:archive.writestr('sheet#1/sheet#1.xml',graph)
            with patch('playback.staff_metadata._chat', side_effect=RecognitionError('offline')):
                changed, report=enrich_musicxml(score(),image,omr,model='local-test',trace_path=root/'trace.json')
            self.assertEqual(ET.tostring(changed),ET.tostring(score()))
            self.assertTrue(report['warnings'])
            self.assertEqual(report['crops']['key-time-header']['bbox_pixels_xyxy'], [65,130,297,200])
            truncated={'done_reason':'length','message':{'content':json.dumps(KEY)}}
            with patch('playback.staff_metadata._chat', return_value=truncated):
                changed,report=enrich_musicxml(score(),image,omr,model='qwen3-vl:8b-test')
            self.assertEqual(ET.tostring(changed),ET.tostring(score()))
            self.assertIn('Truncated',report['warnings'][0])
            self.assertEqual(report['options']['num_gpu'],20)
            self.assertEqual(report['responses'][0]['options']['num_predict'],128)
            # A photo cut at the clef top can correct its key, but provides no
            # outside-clef evidence for the model's asserted octave numeral.
            tight=graph.replace('y="150"','y="5"').replace('y="190"','y="45"').replace('y="120"','y="0"')
            with zipfile.ZipFile(omr,'w') as archive:archive.writestr('sheet#1/sheet#1.xml',tight)
            replies=[{'done_reason':'stop','message':{'content':json.dumps(value)}} for value in (KEY,CLEF)]
            with patch('playback.staff_metadata._chat',side_effect=replies) as chat:
                changed,report=enrich_musicxml(score(),image,omr,model='local-test')
            self.assertEqual(chat.call_count,2)
            self.assertEqual(changed.findtext('.//fifths'),'-5')
            self.assertIsNone(changed.find('.//clef-octave-change'))
            self.assertFalse(report['octave_geometry']['accepted'])
            self.assertNotIn('metronome',report['crops'])
            self.assertTrue(any('No visible area' in w for w in report['warnings']))

    def test_visual_tempo_keeps_exact_dotted_value_and_existing_marking(self):
        original=score()
        value={'visible':True,'beat_unit':'quarter','dotted':True,'per_minute':45,'uncertain':False}
        changed,report=apply_tempo(original,value)
        self.assertEqual(changed.find('.//sound').get('tempo'),'67.5')
        self.assertEqual(changed.findtext('.//metronome/per-minute'),'45')
        self.assertIsNotNone(changed.find('.//metronome/beat-unit-dot'))
        self.assertIsNone(original.find('.//sound'))
        preserved,report=apply_tempo(changed,{**value,'per_minute':72})
        self.assertEqual(ET.tostring(preserved),ET.tostring(changed))
        self.assertFalse(report['corrections'])

    def test_uncertain_or_invalid_tempo_is_not_inserted(self):
        original=score()
        value={'visible':False,'beat_unit':'unknown','dotted':False,'per_minute':0,'uncertain':True}
        changed,report=apply_tempo(original,value)
        self.assertEqual(ET.tostring(changed),ET.tostring(original))
        self.assertFalse(report['corrections'])
        with self.assertRaises(RecognitionError):
            apply_tempo(original,{**value,'visible':True,'beat_unit':'whole','per_minute':400,'uncertain':False})


class OctavePresenceTests(unittest.TestCase):
    def crop(self, numeral=None):
        image = Image.new('L', (140, 220), 255)
        draw = ImageDraw.Draw(image)
        # A loop belonging to the clef itself must not support an extra numeral.
        draw.ellipse((45, 60, 69, 100), outline=0, width=3)
        draw.line((55, 90, 55, 159), fill=0, width=3)
        if numeral:
            top = 33 if numeral == 'above' else 174
            draw.ellipse((52, top, 63, top+18), outline=0, width=2)
        data = io.BytesIO(); image.save(data, format='PNG')
        return {'image': data.getvalue(), 'bbox_pixels_xyxy': [0, 0, 140, 220],
                'clef_bbox_pixels_xywh': [40, 60, 40, 100], 'interline_pixels': 20}

    def test_clef_loop_alone_cannot_verify_hallucinated_octave(self):
        self.assertFalse(_octave_mark_evidence(self.crop(), CLEF)['accepted'])
        self.assertFalse(_octave_mark_evidence(self.crop('below'), CLEF)['accepted'])
        self.assertTrue(_octave_mark_evidence(self.crop('above'), CLEF)['accepted'])
        self.assertTrue(_octave_mark_evidence(self.crop('below'), {**CLEF, 'position': 'below'})['accepted'])

    def test_enrichment_keeps_all_pitch_octaves_when_model_invents_eight(self):
        original = score()
        ET.SubElement(original.find('part/measure'), 'sound', tempo='60')
        crop = self.crop()
        responses = [{'message': {'content': json.dumps(v)}} for v in
                     ({'sign': 'flat', 'count': 2, 'uncertain': False}, CLEF)]
        with patch('playback.staff_metadata._crops', return_value={'clef-octave': crop, 'key-time-header': crop}), \
             patch('playback.staff_metadata._chat', side_effect=responses), \
             patch('playback.staff_metadata._remove_confirmed_header_flats', side_effect=lambda r,*a: (r, {'removed_notes': []})):
            result, report = enrich_musicxml(original, 'fixture.png', 'fixture.omr', model='fixture')
        self.assertEqual(ET.tostring(result), ET.tostring(original))
        self.assertFalse(report['octave_geometry']['accepted'])
        self.assertEqual(report['metadata']['clef-octave'], CLEF)  # raw claim retained
        self.assertFalse(report['corrections'])


class FlatFilterTests(unittest.TestCase):
    """Geometric guards use a synthetic glyph fixture; real accuracy is measured separately."""

    def fixture(self, folder, *, flat_x=50, confidence='0.5', xml_offset=0):
        glyph = np.zeros((24,6),dtype=bool)
        glyph[:,0:2]=True
        glyph[13:16,2:6]=True
        glyph[16:21,4:6]=True
        glyph[20:23,2:5]=True
        columns=[]
        for column in glyph.T:
            runs=[]; state=True; length=0
            for pixel in column:
                if pixel==state:length+=1
                else:runs.append(length);length=1;state=not state
            runs.append(length)
            columns.append('<runs>'+' '.join(map(str,runs))+'</runs>')
        binary=np.zeros((100,150),dtype=bool)
        binary[35:59,40:46]=glyph
        binary[20:44,flat_x:flat_x+6]=glyph
        binary[45:53,110:120]=True
        stream=io.BytesIO();Image.fromarray(np.where(binary,0,255).astype('uint8')).save(stream,format='PNG')
        graph=f'''<sheet><scale><interline main="10"/></scale><page><system><part><staff><lines>
        <line><point y="40"/></line><line><point y="50"/></line><line><point y="60"/></line><line><point y="70"/></line><line><point y="80"/></line>
        </lines></staff></part><sig><inters>
        <key-alter shape="FLAT" glyph="1"><bounds x="40" y="35" w="6" h="24"/></key-alter>
        <head id="2" ctx-grade="{confidence}"><bounds x="{flat_x}" y="30" w="6" h="10"/></head>
        <head id="3" ctx-grade="0.9"><bounds x="110" y="45" w="10" h="8"/></head>
        </inters></sig></system></page><glyph id="1"><run-table orientation="VERTICAL" width="6" height="24">{''.join(columns)}</run-table></glyph></sheet>'''
        omr=Path(folder)/'fixture.omr'
        with zipfile.ZipFile(omr,'w') as z:
            z.writestr('sheet#1/sheet#1.xml',graph)
            z.writestr('sheet#1/BINARY.png',stream.getvalue())
        xml=ET.fromstring(f'''<score-partwise><part><measure><note default-x="{flat_x}"><pitch><step>C</step><octave>5</octave></pitch></note>
        <note default-x="{110+xml_offset}"><pitch><step>B</step><octave>4</octave></pitch></note></measure></part></score-partwise>''')
        return xml,omr,{'sign':'flat','count':2,'uncertain':False},{'bbox_pixels_xyxy':[25,0,108,90]}

    def test_positive_geometry_removes_only_overlapping_header_note(self):
        with tempfile.TemporaryDirectory() as tmp:
            args=self.fixture(tmp)
            before=ET.tostring(args[0])
            result,report=_remove_confirmed_header_flats(*args)
            self.assertTrue(report['accepted'])
            self.assertEqual(len(report['removed_notes']),1)
            self.assertEqual(result.findtext('.//pitch/step'),'B')
            self.assertEqual(ET.tostring(args[0]),before)

    def test_disagreeing_model_spacing_or_xml_mapping_preserves_notes(self):
        with tempfile.TemporaryDirectory() as tmp:
            for kwargs in ({'flat_x':62},{'xml_offset':4},{'confidence':'0.95'}):
                with self.subTest(kwargs=kwargs):
                    args=self.fixture(tmp,**kwargs)
                    result,report=_remove_confirmed_header_flats(*args)
                    self.assertEqual(ET.tostring(result),ET.tostring(args[0]))
                    self.assertFalse(report['removed_notes'])
            args=list(self.fixture(tmp));args[2]['count']=3
            result,report=_remove_confirmed_header_flats(*args)
            self.assertFalse(report['accepted'])
            self.assertEqual(ET.tostring(result),ET.tostring(args[0]))

    def test_expensive_template_search_is_rejected_before_array_matching(self):
        with tempfile.TemporaryDirectory() as tmp:
            args=list(self.fixture(tmp));omr=args[1]
            with zipfile.ZipFile(omr) as z:graph=ET.fromstring(z.read('sheet#1/sheet#1.xml'))
            runs=graph.find('.//run-table');runs.clear()
            runs.attrib.update(orientation='VERTICAL',width='20',height='50')
            for _ in range(20):ET.SubElement(runs,'runs').text='50'
            stream=io.BytesIO();Image.new('L',(1000,400),255).save(stream,format='PNG')
            with zipfile.ZipFile(omr,'w') as z:
                z.writestr('sheet#1/sheet#1.xml',ET.tostring(graph))
                z.writestr('sheet#1/BINARY.png',stream.getvalue())
            args[3]={'bbox_pixels_xyxy':[0,0,1000,400]}
            with patch('numpy.lib.stride_tricks.sliding_window_view',side_effect=AssertionError('must not allocate')):
                with self.assertRaisesRegex(RecognitionError,'work limit'):
                    _remove_confirmed_header_flats(*args)


if __name__=='__main__':
    unittest.main()
