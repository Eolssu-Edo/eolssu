"""Page geometry/semantics contract checks; fixtures are not recognition accuracy."""
from copy import deepcopy
from fractions import Fraction
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

import numpy as np
from PIL import Image, ImageDraw

from playback.jeonggan import _load_image, recognize_jeonggan
from playback.jeonggan_layout import segment_page
from playback.jeonggan_page import expand_page, parse_row, recognize_page
from playback.recognition import RecognitionError

CONTEXT = {'pitch_mode':'yul','notation_octave_shift':0,'huangjong_midi':63,
           'tuning_a_hz':440,'style':'정악','tuning_description':'test'}


def item(token, ornaments=None):
    return {'token':token,'glyph':token,'ornaments':ornaments or []}


def classification(*tokens):
    return {'items':[item(t) for t in tokens],'uncertain':False}


def reply(value):
    return {'message':{'content':json.dumps(value)},'done_reason':'stop'}


def layout(tokens):
    cells=[]
    for index, token in enumerate(tokens):
        rows=[] if token is None else [{'bbox':[50,20+index*40,15,15], 'relative_y':.5,
                                         'classification':classification(token)}]
        cells.append({'id':f'j{index+1}','bbox':[40,index*40,40,40],'units':1,'rows':rows,'blank':not rows})
    return {'image_size':[200,400],'cells':cells,'ignored_regions':[]}


class PageTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path=Path(self.temp.name)/'page.png'
        self.trace=Path(self.temp.name)/'trace.json'

    def page(self):
        image=Image.new('RGB',(700,900),'white')
        draw=ImageDraw.Draw(image)
        # Surrounding prose and staff-like horizontal strokes are not the grid.
        draw.text((30,30),'Printed page heading and explanation',fill='black')
        for y in range(80,131,10): draw.line((30,y,650,y),fill='black',width=2)
        for x in (160,260,330,430): draw.line((x,230,x,730),fill='black',width=2)
        for y in range(230,731,100): draw.line((160,y,430,y),fill='black',width=2)
        for x in (200,370):
            for y in range(270,671,100): draw.rectangle((x,y,x+22,y+22),fill='black')
        image.save(self.path)
        return np.asarray(image)

    def test_detect_page_grid_preserve_order_and_excluded_spacer(self):
        value=segment_page(self.page())
        self.assertEqual(len(value['cells']),10)
        self.assertEqual(sum(len(c['rows']) for c in value['cells']),10)
        self.assertGreater(value['cells'][0]['bbox'][0],value['cells'][5]['bbox'][0])
        self.assertLess(value['cells'][0]['bbox'][1],value['cells'][1]['bbox'][1])
        self.assertTrue(any('blank/marginal' in c['reason'] for c in value['ignored_regions']))

    def test_multiple_grids_are_rejected(self):
        pixels=self.page()
        image=Image.fromarray(pixels)
        draw=ImageDraw.Draw(image)
        for x in (500,600): draw.line((x,300,x,700),fill='black',width=2)
        for y in range(300,701,100): draw.line((500,y,600,y),fill='black',width=2)
        with self.assertRaisesRegex(RecognitionError,'multiple grids'):
            segment_page(np.asarray(image))

    def test_sparse_musical_column_is_not_discarded_as_margin(self):
        pixels=self.page()
        image=Image.fromarray(pixels)
        draw=ImageDraw.Draw(image)
        for y in range(370,671,100): draw.rectangle((370,y,392,y+22),fill='white')
        result=segment_page(np.asarray(image))
        self.assertEqual(len(result['cells']),10)
        self.assertEqual(sum(c['blank'] for c in result['cells']),4)

    def test_damaged_first_middle_or_last_cell_cannot_be_silently_dropped(self):
        image=Image.new('RGB',(700,900),'white')
        draw=ImageDraw.Draw(image)
        for x in (200,320): draw.line((x,200,x,700),fill='black',width=2)
        for y in range(200,701,100): draw.line((200,y,320,y),fill='black',width=2)
        for y in range(240,641,100): draw.rectangle((245,y,269,y+24),fill='black')
        self.assertEqual(len(segment_page(np.asarray(image))['cells']),5)
        for row in (0,2,4):
            for side in (200,320):
                with self.subTest(row=row,side=side):
                    damaged=image.copy()
                    ImageDraw.Draw(damaged).rectangle((side-2,205+row*100,side+3,295+row*100),fill='white')
                    with self.assertRaisesRegex(RecognitionError,'incomplete cell'):
                        segment_page(np.asarray(damaged))

    def test_blank_sustain_repeat_rest_have_distinct_timing(self):
        value=layout(['base:黃',None,'sustain','repeat','rest'])
        notes,regions,notices=expand_page(value,Fraction(3,2))
        self.assertEqual([n['duration'] for n in notes],['9/2','3/2','3/2'])
        self.assertEqual(notes[0]['pitch'],notes[1]['pitch'])
        self.assertTrue(notes[2]['rest'])
        self.assertEqual(len(regions),3)
        self.assertTrue(any('비어' in n for n in notices))

    def test_relative_symbols_cross_register_and_side_by_side_share_time(self):
        value=layout(['base:黃','h'])
        value['cells'][1]['rows'][0]['classification']=classification('h','upper1')
        notes,_,_=expand_page(value,Fraction(3,2))
        self.assertEqual([n['pitch'] for n in notes],
                         [{'yul':'黃','register':0},{'yul':'無','register':-1},
                          {'yul':'林','register':-1},{'yul':'無','register':-1}])
        self.assertEqual([n['duration'] for n in notes],['3/2','3/8','3/8','3/4'])

    def test_unknown_cannot_create_complete_score_and_trace_is_retained(self):
        pixels=self.page()
        fake=layout(['unknown'])
        with patch('playback.jeonggan_page.segment_page',return_value=fake), \
             patch('playback.jeonggan_page._chat',return_value=reply(classification('unknown'))):
            with self.assertRaisesRegex(RecognitionError,'unknown pitch'):
                recognize_page(pixels,'hash',CONTEXT,CONTEXT,Fraction(3,2),model='test',
                               base_url='http://127.0.0.1:11434',trace_path=self.trace,started=time.monotonic())
        saved=json.loads(self.trace.read_text(encoding='utf-8'))
        self.assertEqual(saved['coverage']['unknown_rows'],1)
        self.assertIn('response',saved['rows'][0])

    def test_exact_crop_cache_reuses_only_identical_bytes(self):
        pixels=self.page()
        with patch('playback.jeonggan_page._chat',return_value=reply(classification('base:黃'))) as chat:
            result=recognize_jeonggan(self.path,CONTEXT,model='test',trace_path=self.trace)
        self.assertEqual(chat.call_count,1)
        self.assertEqual(len(result['score']['notes']),10)
        self.assertEqual(result['provider']['coverage']['cells'],10)
        self.assertEqual(result['score']['source']['review_status'],'machine-transcribed-unverified')
        self.assertTrue(any('BPM60' in a for a in result['score']['assumptions']))

    def test_attached_ornament_is_explicit_warning_not_silent_timing_change(self):
        value=layout(['base:黃'])
        value['cells'][0]['rows'][0]['classification']['items'][0]['ornaments']=['∧']
        notes,_,warnings=expand_page(value,Fraction(3,2))
        self.assertEqual(notes[0]['duration'],'3/2')
        self.assertIn('∧',warnings[0])

    def test_invalid_response_duplicate_keys_truncation_unknown_fields(self):
        good=classification('base:黃')
        invalid=[{'message':{'content':'{"items":[],"items":[],"uncertain":false}'}},
                 {**reply(good),'done_reason':'length'},reply({**good,'extra':1}),
                 reply({'items':[item('invented')],'uncertain':False})]
        for response in invalid:
            with self.subTest(response=response), self.assertRaises(RecognitionError): parse_row(response)

    def test_non_object_model_json_is_a_recognition_error(self):
        for value in (None,1,False,'items',['items','uncertain'],[{}]):
            with self.subTest(value=value), self.assertRaisesRegex(RecognitionError,'must be an object'):
                parse_row(reply(value))

    def test_non_object_model_json_keeps_trace_and_never_returns_draft(self):
        self.page()
        with patch('playback.jeonggan_page._chat',return_value=reply(None)):
            with self.assertRaisesRegex(RecognitionError,'must be an object'):
                recognize_jeonggan(self.path,CONTEXT,model='test',trace_path=self.trace)
        saved=json.loads(self.trace.read_text(encoding='utf-8'))
        self.assertEqual(saved['rows'][0]['response']['message']['content'],'null')
        self.assertIn('error',saved)
        self.assertNotEqual(saved.get('status'),'draft-created')

    def test_blank_or_relative_after_rest_requires_review(self):
        for tokens in ([None],['rest',None],['rest','repeat'],['base:大','h']):
            with self.subTest(tokens=tokens), self.assertRaises(RecognitionError):
                expand_page(layout(tokens),Fraction(1))

    def test_timeout_and_source_pixel_limit_before_calls(self):
        pixels=self.page()
        with patch('playback.jeonggan_page._chat') as chat:
            with self.assertRaisesRegex(RecognitionError,'300-second'):
                recognize_page(pixels,'hash',CONTEXT,CONTEXT,Fraction(1),model='test',
                               base_url='http://127.0.0.1:11434',trace_path=self.trace,started=time.monotonic()-301)
            chat.assert_not_called()
        with patch('playback.jeonggan.MAX_PIXELS',10), self.assertRaisesRegex(RecognitionError,'16 megapixels'):
            _load_image(self.path)

    def test_original_png_larger_than_ten_mib_decodes_within_source_cap(self):
        # Normalization can expand a small JPEG into a large lossless PNG.
        rng=np.random.default_rng(42)
        Image.fromarray(rng.integers(0,256,(1920,1920,3),dtype=np.uint8)).save(self.path)
        self.assertGreater(self.path.stat().st_size,10*1024*1024)
        self.assertLess(self.path.stat().st_size,64*1024*1024)
        pixels,_=_load_image(self.path)
        self.assertEqual(pixels.shape,(1920,1920,3))

    def test_actual_source_dimensions_over_sixteen_mp_rejected(self):
        Image.new('RGB',(4001,4000),'white').save(self.path)
        with self.assertRaises(RecognitionError):
            _load_image(self.path)

    def test_model_cannot_silently_turn_wavy_symbol_into_sustain(self):
        pixels=self.page()
        fake=layout(['base:黃'])
        bad={'items':[{'token':'sustain','glyph':'~','ornaments':[]}],'uncertain':False}
        with patch('playback.jeonggan_page.segment_page',return_value=fake), \
             patch('playback.jeonggan_page._chat',return_value=reply(bad)):
            with self.assertRaisesRegex(RecognitionError,'unknown pitch'):
                recognize_page(pixels,'hash',CONTEXT,CONTEXT,Fraction(1),model='test',
                               base_url='http://127.0.0.1:11434',trace_path=self.trace,started=time.monotonic())
        saved=json.loads(self.trace.read_text(encoding='utf-8'))
        self.assertIn('shape_warning',saved['rows'][0])
        self.assertEqual(json.loads(saved['rows'][0]['response']['message']['content'])['items'][0]['token'],'sustain')


if __name__=='__main__': unittest.main()
