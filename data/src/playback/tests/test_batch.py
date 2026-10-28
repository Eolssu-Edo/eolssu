from copy import deepcopy
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from data.src.playback.tests.test_jgb import document
from data.src.playback.batch import prepare_jgb, run_batch


class PreparationTests(unittest.TestCase):
    def test_bad_input_is_reported_and_later_tracks_are_still_processed(self):
        cases={'truncated':b'{"v":', 'encoding':b'\xff', 'non_object':b'[]', 'missing':None}
        for name,payload in cases.items():
            with self.subTest(name=name),tempfile.TemporaryDirectory() as folder:
                root=Path(folder);out=root/'out';bad=root/'bad.json';good=root/'good.json'
                if payload is not None:bad.write_bytes(payload)
                good.write_text(json.dumps(document('황 | 태')),encoding='utf-8')
                before=good.read_bytes()
                tempo={'jeonggan_bpm':60,'bpm':60,'beat_unit':'jeonggan','evidence_type':'test'}
                items=[{'title':'bad','input':'bad.json','tempo':tempo},
                       {'title':'good','input':'good.json','tempo':tempo}]
                catalog=root/'catalog.json';catalog.write_text(json.dumps({'items':items}),encoding='utf-8')
                def render_stub(source,assets,track,instruments,*,part,audio_format,encoder):
                    track.mkdir(parents=True)
                    return {'results':[{'notes':2,'seconds':2,'audio':{'file':f'danso.{audio_format}'}}]}
                with patch('data.src.playback.batch.run',side_effect=render_stub) as render,redirect_stdout(io.StringIO()):
                    records=run_batch(catalog,root/'assets.json',out,audio_format='wav')
                self.assertEqual([r['status'] for r in records],['blocked','generated'])
                self.assertEqual(records[0]['input'],'bad.json')
                self.assertTrue(records[0]['error'])
                self.assertEqual(len(records[1]['outputs']),1)
                render.assert_called_once()
                saved=json.loads((out/'manifest.json').read_text(encoding='utf-8'))
                self.assertEqual(saved['records'],records)
                self.assertIn('<audio controls',(out/'index.html').read_text(encoding='utf-8'))
                self.assertEqual(good.read_bytes(),before)
                if payload is not None:
                    self.assertEqual(bad.read_bytes(),payload)
                    self.assertEqual((out/'originals'/'001.json').read_bytes(),payload)

    def test_batch_rejects_input_within_output_before_overwriting_it(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);out=root/'out';out.mkdir()
            source=out/'prepared.json';source.write_text('{"original":true}')
            catalog=root/'catalog.json'
            catalog.write_text(json.dumps({'items':[{'input':str(source)}]}))
            with self.assertRaisesRegex(ValueError,'원본'):
                run_batch(catalog,root/'assets.json',out)
            self.assertEqual(source.read_text(),'{"original":true}')
            self.assertFalse((out/'manifest.json').exists())

    def test_preparation_preserves_original_and_records_tempo_and_metadata(self):
        source=document('황 | 태 | 중 | 임 | 무', beats=5,joPreset='all')
        source['parts'][0]['instrument']='all'
        original=deepcopy(source)
        prepared,changes=prepare_jgb(source,{'jeonggan_bpm':84,'bpm':84,'beat_unit':'dotted_quarter','evidence_type':'user_table'},[])
        self.assertEqual(source,original)
        self.assertEqual(prepared['controls']['tempoBpm'],84)
        self.assertEqual(prepared['parts'][0]['instrument'],'danso')
        self.assertEqual(prepared['parts'][0]['melody'],source['parts'][0]['melody'])
        self.assertEqual(prepared['playback']['tempo']['beat_unit'],'dotted_quarter')
        self.assertEqual(prepared['controls']['joPreset'],'all')

    def test_observed_scale_only_with_all_five_explicit_notes(self):
        melody='황{니레} | 태 | 중 | 임 | 무'
        prepared,_=prepare_jgb(document(melody,beats=5,joPreset='all'),{'jeonggan_bpm':84},[])
        self.assertEqual(prepared['controls']['joPreset'],'jung-pyeong')
        self.assertEqual(prepared['playback']['observed_scale'],[0,2,5,7,10])
        small,_=prepare_jgb(document('황{니레} | 태',joPreset='all'),{'jeonggan_bpm':84},[])
        self.assertEqual(small['controls']['joPreset'],'all')

    def test_visual_correction_requires_exact_source_cell_and_is_logged(self):
        source=document('청협{풀어내림표} | 청태')
        correction={'gak':1,'cell':1,'before_cell':'청협{풀어내림표} ',
                    'after_cell':'청태{농음표} ','reason':'원보 직접 대조','source_url':'https://example.invalid/source'}
        prepared,changes=prepare_jgb(source,{'jeonggan_bpm':84},[correction])
        self.assertEqual(prepared['parts'][0]['melody'],'청태{농음표} | 청태')
        self.assertIn(correction,prepared['playback']['source_corrections'])
        correction['before_cell']='다른 판본'
        with self.assertRaisesRegex(ValueError,'원본'):
            prepare_jgb(source,{'jeonggan_bpm':84},[correction])

    def test_verified_extra_gak_removal_requires_exact_before_fields(self):
        source=document('청황 | 청태\n임',gakBeats='2:1')
        corrections=[{'field':['parts',0,'melody'],'before':'청황 | 청태\n임','after':'청황 | 청태'},
                     {'field':['controls','gakBeats'],'before':'2:1','after':''}]
        prepared,_=prepare_jgb(source,{'jeonggan_bpm':60},corrections)
        self.assertEqual(prepared['parts'][0]['melody'],'청황 | 청태')
        self.assertEqual(source['controls']['gakBeats'],'2:1')
        corrections[1]['before']='1:1'
        with self.assertRaisesRegex(ValueError,'원본'):
            prepare_jgb(source,{'jeonggan_bpm':60},corrections)


if __name__=='__main__':unittest.main()
