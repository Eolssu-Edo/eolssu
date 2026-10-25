"""Notation segmentation/grammar checks; synthetic shapes are not OCR accuracy."""
import unittest
from unittest.mock import patch
import hashlib
import json
from pathlib import Path
import tempfile
import numpy as np
from PIL import Image, ImageDraw


class SymbolTests(unittest.TestCase):
    def test_compact_dot_has_same_role_and_classification(self):
        from playback.jeonggan_symbols import duration_dot,classify_glyph
        pixels=np.zeros((2,4,3),dtype=np.uint8)
        self.assertTrue(duration_dot(np.ones((2,4),dtype=bool),40))
        value,_=classify_glyph(pixels,[0,0,4,2],typical_height=40,atlas=None,model='local',
                               base_url='http://127.0.0.1:11434',timeout=1)
        self.assertEqual(value['token'],'duration_half')

    def test_grace_legs_distinguish_upper_extension_and_closed_triangle(self):
        from playback.jeonggan_symbols import _grace_legs
        one=Image.new('L',(42,40),255);d=ImageDraw.Draw(one)
        d.line((4,35,20,4,36,35),fill=0,width=3)
        self.assertEqual(_grace_legs(np.asarray(one))['token'],'grace_upper1')
        two=Image.new('L',(42,40),255);d=ImageDraw.Draw(two)
        d.line((4,35,24,2),fill=0,width=3);d.line((18,12,36,35),fill=0,width=3)
        self.assertEqual(_grace_legs(np.asarray(two))['token'],'grace_upper2')
        ImageDraw.Draw(one).line((4,35,36,35),fill=0,width=3)
        self.assertIsNone(_grace_legs(np.asarray(one)))

    def test_disconnected_dot_is_not_a_lambda_upper_stem(self):
        from playback.jeonggan_symbols import _grace_legs
        image=Image.new('L',(25,21),255);d=ImageDraw.Draw(image)
        d.line((2,20,12,8,22,20),fill=0,width=2);d.rectangle((11,0,12,1),fill=0)
        ink=np.asarray(image)<170;ys,xs=np.where(ink)
        crop=np.asarray(image)[ys.min():ys.max()+1,xs.min():xs.max()+1]
        self.assertEqual(_grace_legs(crop)['token'],'unknown')

    def test_small_valley_turn_is_retained_after_touching_split(self):
        from playback.jeonggan_symbols import classify_glyph
        image=Image.new('RGB',(6,3),'white')
        ImageDraw.Draw(image).line((0,0,3,2,5,0),fill='black',width=1)
        value,_=classify_glyph(np.asarray(image),[0,0,6,3],typical_height=21,atlas=None,
                               model='local',base_url='http://127.0.0.1:11434',timeout=1,role='attached')
        self.assertEqual(value['token'],'turn_upper')

    def test_small_touching_wave_is_split_without_splitting_plain_body(self):
        from playback.jeonggan_symbols import split_row
        image=Image.new('RGB',(50,40),'white');draw=ImageDraw.Draw(image)
        draw.rectangle((2,2,25,31),outline='black',width=2)
        self.assertEqual(len(split_row(np.asarray(image),[0,0,50,40],typical_height=30)),1)
        draw.line((25,13,32,18,40,13),fill='black',width=1)
        boxes=split_row(np.asarray(image),[0,0,50,40],typical_height=30)
        self.assertEqual(len(boxes),2)
        self.assertEqual(boxes[0][3],30)
        self.assertLessEqual(boxes[1][3],7)

    def atlas(self,token='rest',kind='rest'):
        from playback.jeonggan_symbols import LegendAtlas
        temp=tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup)
        path=Path(temp.name);image=Image.new('L',(30,30),255);draw=ImageDraw.Draw(image)
        draw.polygon([(15,2),(2,27),(27,27)],outline=0,width=3);image.save(path/'symbol.png')
        manifest={'schema':'jeonggan-legend-atlas/v1','entries':[{'token':token,'kind':kind,'file':'symbol.png'}]}
        (path/'manifest.json').write_text(json.dumps(manifest),encoding='utf-8')
        return path,LegendAtlas(path/'manifest.json'),image

    def test_triangle_is_rest_without_model_and_preserves_box(self):
        from playback.jeonggan_symbols import classify_glyph
        _,atlas,gray=self.atlas()
        pixels=np.asarray(gray.convert('RGB'))
        with patch('playback.jeonggan_symbols._chat') as chat:
            value,evidence=classify_glyph(pixels,[0,0,30,30],typical_height=30,atlas=atlas,
                                         model='local',base_url='http://127.0.0.1:11434',timeout=1,role='main')
        self.assertEqual(value['token'],'rest');chat.assert_not_called()
        self.assertEqual(evidence['bbox'],[0,0,30,30]);self.assertEqual(evidence['holes'],1)
        json.dumps(evidence,allow_nan=False)

    def test_atlas_image_content_changes_digest(self):
        from playback.jeonggan_symbols import LegendAtlas
        path,first,image=self.atlas();ImageDraw.Draw(image).line((0,0,29,29),fill=0,width=2);image.save(path/'symbol.png')
        second=LegendAtlas(path/'manifest.json')
        self.assertNotEqual(first.sha256,second.sha256)

    def test_atlas_rejects_unknown_token_and_escaping_file(self):
        from playback.jeonggan_symbols import LegendAtlas
        from playback.recognition import RecognitionError
        path,_,_=self.atlas()
        for token,file in [('execute','symbol.png'),('rest','../symbol.png')]:
            (path/'manifest.json').write_text(json.dumps({'schema':'jeonggan-legend-atlas/v1',
                'entries':[{'token':token,'kind':'rest','file':file}]}),encoding='utf-8')
            with self.assertRaises(RecognitionError):LegendAtlas(path/'manifest.json')

    def test_unknown_margin_geometry_never_duplicates_clipped_component(self):
        from playback.jeonggan_symbol_page import margin_components
        image=Image.new('RGB',(240,140),'white');ImageDraw.Draw(image).rectangle((80,54,94,88),fill='black')
        layout={'unit_height_pixels':120,'cells':[{'id':'j1','bbox':[100,0,100,120],
            'rows':[{'id':'r1','bbox':[130,25,20,30]},{'id':'r2','bbox':[130,55,20,30]}]}]}
        marks=margin_components(np.asarray(image),layout,40)
        self.assertEqual([(m['row_id'],m['bbox']) for m in marks],[('r2',[80,54,15,35])])

    def test_plain_literal_cannot_be_promoted_without_water_strokes(self):
        from playback.jeonggan_symbols import classify_glyph
        class Atlas:
            def pitch_candidates(self,gray):
                return [{'token':'high:無','score':.25},{'token':'base:無','score':.31}]
        image=Image.new('RGB',(30,30),'white');ImageDraw.Draw(image).rectangle((2,2,27,27),outline='black',width=3)
        with patch('playback.jeonggan_symbols._chat',return_value={'message':{'content':'{"glyph":"無"}'}}):
            value,_=classify_glyph(np.asarray(image),[0,0,30,30],typical_height=30,atlas=Atlas(),
                                   model='local',base_url='http://127.0.0.1:11434',timeout=1)
        self.assertEqual(value['token'],'unknown')

    def test_malformed_provider_message_is_recognition_error(self):
        from playback.jeonggan_symbols import _response
        from playback.recognition import RecognitionError
        for message in (None,[],'bad'):
            with self.assertRaises(RecognitionError):_response({'message':message},['glyph'])

    def test_line_has_no_three_direction_turn(self):
        from playback.jeonggan_symbols import _direction_runs
        self.assertEqual(_direction_runs(np.linspace(0,10,31),2),1)
        self.assertEqual(_direction_runs(np.full(31,4.),1),0)
        self.assertEqual(_direction_runs(np.array([0,2,4,2,0,2,4]),1),3)

    def test_same_margin_component_uses_closest_row(self):
        from playback.jeonggan_symbol_page import margin_components
        image=Image.new('RGB',(240,140),'white');d=ImageDraw.Draw(image)
        d.rectangle((80,54,94,73),fill='black')
        layout={'unit_height_pixels':120,'cells':[{'id':'j1','bbox':[100,0,100,120],
            'rows':[{'id':'r1','bbox':[130,25,20,30]},{'id':'r2','bbox':[130,55,20,30]}]}]}
        marks=margin_components(np.asarray(image),layout,40)
        self.assertEqual(len(marks),1)
        self.assertEqual(marks[0]['row_id'],'r2')
    def test_horizontal_time_symbols_and_attached_mark_stay_separate(self):
        from playback.jeonggan_symbols import split_row
        image=Image.new('RGB',(100,40),'white')
        draw=ImageDraw.Draw(image)
        draw.line((5,20,18,20),fill='black',width=2)
        draw.line((45,8,45,29,61,29),fill='black',width=3)
        draw.line((78,21,83,13,88,21),fill='black',width=2)
        boxes=split_row(np.asarray(image),[0,0,100,40],typical_height=30)
        self.assertEqual(len(boxes),3)
        self.assertLess(boxes[0][0],boxes[1][0])
        self.assertLess(boxes[0][3],boxes[1][3])


if __name__=='__main__': unittest.main()
