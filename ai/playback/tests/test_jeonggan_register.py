"""Synthetic stroke evidence checks, independent of any song or expected sequence."""
import unittest
from fractions import Fraction
import json
from pathlib import Path
import tempfile
import time
from unittest.mock import patch
import numpy as np
from PIL import Image, ImageDraw

from playback.jeonggan_register import validate_register


def value(token='high:林',glyph='林'):
    return {'items':[{'token':token,'glyph':glyph,'ornaments':[]}],'uncertain':False}


class RegisterTests(unittest.TestCase):
    def plain(self,broken=False):
        im=Image.new('RGB',(60,60),'white')
        draw=ImageDraw.Draw(im)
        for x in (14,44):
            draw.line((x,2,x,57),fill='black',width=3)
            draw.line((x-12,20,x+12,20),fill='black',width=3)
            draw.line((x,20,x-12,52),fill='black',width=3)
            draw.line((x,20,x+12,52),fill='black',width=3)
        if broken: draw.rectangle((0,27,29,34),fill='white')
        return np.asarray(im)

    def water(self,join_middle=False):
        im=Image.new('RGB',(60,60),'white')
        draw=ImageDraw.Draw(im)
        draw.rectangle((4,3,9,9),fill='black')
        draw.rectangle((1,20,7,26),fill='black')
        draw.rectangle((24,2,55,57),outline='black',width=3)
        draw.line((2,55,26,34),fill='black',width=3)
        if join_middle: draw.line((5,24,5,53),fill='black',width=3)
        return np.asarray(im)

    def test_plain_body_correction_requires_both_transcript_conflict_and_strokes(self):
        original=value()
        checked,evidence=validate_register(self.plain(),[0,0,60,60],original)
        self.assertEqual(checked['items'][0]['token'],'base:林')
        self.assertEqual(evidence['outcome'],'plain-body-supported')
        self.assertEqual(original['items'][0]['token'],'high:林')
        self.assertEqual(evidence['original_glyph'],'林')

    def test_water_radical_with_joined_lower_stroke_keeps_high_register(self):
        for yul in ('林','黃','太'):
            with self.subTest(yul=yul):
                checked,evidence=validate_register(self.water(),[0,0,60,60],value('high:'+yul,yul))
                self.assertEqual(checked['items'][0]['token'],'high:'+yul)
                self.assertEqual(evidence['outcome'],'water-radical-supported')
                self.assertGreaterEqual(evidence['water_upper_dots'],1)
                self.assertGreaterEqual(evidence['water_middle_dots'],1)

    def test_joined_middle_and_lower_water_strokes_remain_positive_evidence(self):
        checked,evidence=validate_register(self.water(join_middle=True),[0,0,60,60],value('high:黃','黃'))
        self.assertEqual(checked['items'][0]['token'],'high:黃')
        self.assertTrue(evidence['water_joined_lower_stroke'])
        self.assertEqual(evidence['outcome'],'water-radical-supported')

    def test_tall_joined_water_component_is_not_a_plain_body(self):
        image=Image.new('RGB',(60,60),'white')
        draw=ImageDraw.Draw(image)
        for box in ((4,2,12,8),(1,20,13,25),(0,42,18,56)):
            draw.rectangle(box,fill='black')
        draw.line((9,5,9,53),fill='black',width=2)
        draw.rectangle((30,2,55,57),outline='black',width=3)
        checked,evidence=validate_register(np.asarray(image),[0,0,60,60],value())
        self.assertEqual(checked['items'][0]['token'],'unknown')
        self.assertEqual(evidence['outcome'],'ambiguous-register')
        self.assertFalse(evidence['plain_left_body'])

    def test_independent_review_one_pixel_bridge_never_lowers_water_glyph(self):
        for bridge in (False,True):
            with self.subTest(bridge=bridge):
                image=Image.new('RGB',(60,60),'white')
                draw=ImageDraw.Draw(image)
                draw.rectangle((4,3,9,9),fill='black')
                draw.rectangle((1,20,7,26),fill='black')
                draw.line((2,55,17,35),fill='black',width=3)
                draw.rectangle((24,2,55,57),outline='black',width=3)
                if bridge: draw.line((5,8,5,53),fill='black',width=1)
                checked,evidence=validate_register(np.asarray(image),[0,0,60,60],value())
                self.assertEqual(checked['items'][0]['token'],'unknown' if bridge else 'high:林')
                self.assertNotEqual(evidence['outcome'],'plain-body-supported')

    def test_antialias_bridge_uses_retained_core_ink_not_default_high(self):
        image=Image.fromarray(self.water(join_middle=True))
        ImageDraw.Draw(image).line((5,8,5,23),fill=(145,145,145),width=1)
        checked,evidence=validate_register(np.asarray(image),[0,0,60,60],value('high:無','無'))
        self.assertEqual(checked['items'][0]['token'],'high:無')
        self.assertEqual(evidence['outcome'],'water-radical-supported')
        self.assertEqual(evidence['threshold'],100)
        self.assertGreaterEqual(evidence['retained_ink_fraction'],.45)

    def test_broken_left_body_without_positive_radical_evidence_is_unknown(self):
        checked,evidence=validate_register(self.plain(broken=True),[0,0,60,60],value())
        self.assertEqual(checked['items'][0]['token'],'unknown')
        self.assertTrue(checked['uncertain'])
        self.assertEqual(evidence['outcome'],'ambiguous-register')

    def test_unrelated_glyph_or_non_conflicting_token_does_not_trigger(self):
        for original in (value('base:林','林'),value('high:林','淋'),value('high:林','漁'),
                         value('high:林','太'),value('repeat','林'),value('doublehigh:林','林')):
            with self.subTest(original=original):
                checked,evidence=validate_register(self.plain(),[0,0,60,60],original)
                self.assertEqual(checked,original)
                self.assertIsNone(evidence)

    def test_multiple_time_items_do_not_guess_which_pixels_belong_to_a_pitch(self):
        original=value()
        original['items'].append({'token':'upper1','glyph':'L','ornaments':[]})
        checked,evidence=validate_register(self.plain(),[0,0,60,60],original)
        self.assertEqual(checked,original)
        self.assertIsNone(evidence)

    def test_tiny_image_never_supplies_correction_evidence(self):
        checked,evidence=validate_register(np.zeros((8,8,3),dtype=np.uint8),[0,0,8,8],value())
        self.assertEqual(checked['items'][0]['token'],'unknown')
        self.assertEqual(evidence['outcome'],'ambiguous-register')

    def test_page_cache_keeps_original_transcript_and_reports_each_correction(self):
        from playback.jeonggan_page import recognize_page
        pixels=np.concatenate([self.plain(),self.plain()],axis=0)
        cells=[{'id':f'j{i+1}','bbox':[0,i*60,60,60],'units':1,'blank':False,
                'rows':[{'bbox':[0,i*60,60,60],'relative_y':.5}]} for i in range(2)]
        layout={'cells':cells,'image_size':[60,120],'ignored_regions':[]}
        context={'pitch_mode':'yul','notation_octave_shift':0,'huangjong_midi':63,
                 'tuning_a_hz':440,'style':'test','tuning_description':'test'}
        response={'message':{'content':json.dumps(value())},'done_reason':'stop'}
        with tempfile.TemporaryDirectory() as directory:
            trace=Path(directory)/'trace.json'
            with patch('playback.jeonggan_page.segment_page',return_value=layout), \
                 patch('playback.jeonggan_page._chat',return_value=response) as chat:
                result=recognize_page(pixels,'hash',context,context,Fraction(1),model='test',
                                      base_url='http://127.0.0.1:11434',trace_path=trace,started=time.monotonic())
            self.assertEqual(chat.call_count,1)
            self.assertEqual(result['score']['source']['register_geometry_corrections'],2)
            self.assertEqual([n['pitch']['register'] for n in result['score']['notes']],[0,0])
            self.assertTrue(any('2개' in a for a in result['score']['assumptions']))
            saved=json.loads(trace.read_text(encoding='utf-8'))
            self.assertEqual(saved['rows'][0]['register_check']['original_token'],'high:林')
            self.assertEqual(saved['rows'][1]['register_check']['original_token'],'high:林')
            self.assertTrue(saved['rows'][1]['exact_crop_cache'])
            self.assertEqual(json.loads(saved['rows'][0]['response']['message']['content'])['items'][0]['token'],'high:林')


if __name__=='__main__': unittest.main()
