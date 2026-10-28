"""Instrument identification and explicit musical choices, using tiny authored data."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from data.src.playback.jgb import convert_jgb
from data.src.playback.score import PROFILES, compile_score
from data.src.playback.midi import read_midi, write_midi
from data.src.playback.batch import prepare_jgb, run_batch
from data.src.playback.tests.test_jgb import document
from data.src.playback.tests.test_pipeline import synthetic_sampler

POLICY = {'name':'authored-test', 'basis':'Synthetic regression case, not a performance rule.',
          'status':'audition-policy-not-performance-standard',
          'vibrato':{'mode':'pitch','hz':3,'cents':40,'depth':0,'delay_seconds':.1},
          'fall':{'end_cents':-75,'tail_fraction':.4,'maximum_seconds':.15}}


class InstrumentPolicyTests(unittest.TestCase):
    def setUp(self):
        # Each separate instrument MR can run the common regression suite.
        profiles={key:{**PROFILES['danso'],'label':key,'yoseong_mode':None}
                  for key in ('jeongak-daegeum','sanjo-daegeum','sogeum')}
        self.patch=patch.dict(PROFILES,profiles)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def convert(self, key, melody='청황 | 청태', **metadata):
        doc=document(melody)
        doc['tempoBpmUserSet']=True
        doc['parts'][0]['instrument']=key
        doc['playback']=metadata
        return convert_jgb(doc,source_name='authored.jgb.json',instrument=key)

    def test_instrument_selection_does_not_transpose_or_add_pitch_motion(self):
        for key in ('jeongak-daegeum','sanjo-daegeum','sogeum'):
            score,mapping=self.convert(key)
            self.assertEqual([n['pitch'] for n in score['notes']],[75,77])
            self.assertEqual(mapping['instrument'],key)
            with tempfile.TemporaryDirectory() as tmp:
                path=Path(tmp)/'test.mid'
                write_midi(compile_score(score,key),path)
                self.assertTrue(all(cents==0 for _,cents in read_midi(path)['bends']))

    def test_ambiguous_daegeum_needs_subtype_provenance_and_conflicts_fail(self):
        doc=document();doc['parts'][0]['instrument']='daegeum'
        doc['tempoBpmUserSet']=True
        with self.assertRaisesRegex(ValueError,'기종'):
            convert_jgb(doc,source_name='test',instrument='jeongak-daegeum')
        doc['playback']={'instrument':'jeongak-daegeum','instrument_basis':'Authored source explicitly identifies subtype.'}
        _,mapping=convert_jgb(doc,source_name='test',instrument='jeongak-daegeum')
        self.assertEqual(mapping['instrument_basis'],doc['playback']['instrument_basis'])
        with self.assertRaisesRegex(ValueError,'다릅니다'):
            convert_jgb(doc,source_name='test',instrument='sanjo-daegeum')
        doc['parts'][0]['instrument']='danso'
        with self.assertRaisesRegex(ValueError,'다릅니다'):
            convert_jgb(doc,source_name='test',instrument='jeongak-daegeum')

    def test_marked_expression_requires_complete_explicit_policy(self):
        for key in ('jeongak-daegeum','sanjo-daegeum','sogeum'):
            for mark in ('농음표','흘림표'):
                with self.assertRaisesRegex(ValueError,'expression_policy'):
                    self.convert(key,f'청황{{{mark}}} | 청태')
            score,mapping=self.convert(key,'청황{농음표} | 청태{흘림표}',expression_policy=POLICY)
            perf=compile_score(score,key)
            self.assertEqual(perf['events'][0]['yoseong']['cents'],40)
            self.assertEqual(perf['events'][1]['pitch_curve']['end_cents'],-75)
            self.assertEqual(mapping['expression_policy'],POLICY)
        invalid=deepcopy(POLICY);del invalid['vibrato']['cents']
        with self.assertRaisesRegex(ValueError,'모두'):
            self.convert('sogeum',expression_policy=invalid)
        invalid=deepcopy(POLICY);invalid['fall']['end_cents']=25
        with self.assertRaises(ValueError):self.convert('sogeum',expression_policy=invalid)

    def test_hold_mark_preserves_policy_delay_and_note_identity(self):
        score,_=self.convert('jeongak-daegeum','청황 -{농음표} | 청태',expression_policy=POLICY)
        self.assertEqual(len(score['notes']),2)
        self.assertAlmostEqual(score['notes'][0]['yoseong']['delay_seconds'],.6)

    def test_custom_relative_scale_is_explicit_and_conflict_checked(self):
        doc=document('청태{니레} | 청임',joPreset='all')
        doc['tempoBpmUserSet']=True
        doc['parts'][0]['instrument']='sanjo-daegeum'
        before=deepcopy(doc)
        with self.assertRaisesRegex(ValueError,'명시한 조'):
            convert_jgb(doc,source_name='test',instrument='sanjo-daegeum')
        self.assertEqual(doc,before)
        doc['playback']={'relative_scale':{'pitch_classes':[2,4,7,9,10],'basis':'Authored fixture scale.'}}
        score,mapping=convert_jgb(doc,source_name='test',instrument='sanjo-daegeum')
        self.assertEqual([n['pitch'] for n in score['notes']],[79,77,82])
        self.assertEqual(mapping['relative_scale'],doc['playback']['relative_scale'])
        with self.assertRaisesRegex(ValueError,'중복'):
            convert_jgb(doc,source_name='test',instrument='sanjo-daegeum',jo='jung-pyeong')

    def test_batch_preserves_instrument_provenance_and_routes_output(self):
        doc=document();doc['parts'][0]['instrument']='all'
        tempo={'jeonggan_bpm':60,'beat_unit':'jeonggan','evidence_type':'authored-test'}
        before=deepcopy(doc)
        with self.assertRaisesRegex(ValueError,'instrument_basis'):
            prepare_jgb(doc,tempo,[],instrument='sogeum')
        prepared,_=prepare_jgb(doc,tempo,[],instrument='sogeum',instrument_basis='Source category and edition.')
        self.assertEqual(doc,before)
        self.assertEqual(prepared['parts'][0]['instrument'],'all')
        self.assertEqual(convert_jgb(prepared,source_name='test',instrument='sogeum')[1]['instrument'],'sogeum')
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            (folder/'source.jgb.json').write_text(json.dumps(doc),encoding='utf-8')
            (folder/'assets.json').write_text(json.dumps({'sogeum':'unused.zip'}),encoding='utf-8')
            catalog={'instrument':'sogeum','instrument_basis':'Source category and edition.',
                     'items':[{'title':'Fixture','input':'source.jgb.json','tempo':tempo}]}
            (folder/'catalog.json').write_text(json.dumps(catalog),encoding='utf-8')
            def sampler(*args):
                value=synthetic_sampler();value.instrument='sogeum'
                value.manifest={'fixture':True};value.close=lambda:None
                return value
            with patch('data.src.playback.__main__.CatSampler',side_effect=sampler):
                rows=run_batch(folder/'catalog.json',folder/'assets.json',folder/'out',audio_format='wav')
            self.assertEqual(rows[0]['status'],'generated',rows[0].get('error'))
            self.assertTrue(rows[0]['outputs'][0]['midi'].endswith('/sogeum.mid'))
            self.assertTrue(rows[0]['outputs'][0]['audio_file'].endswith('/sogeum.wav'))

    def test_batch_cannot_replace_explicit_source_subtype(self):
        doc=document();doc['parts'][0]['instrument']='all'
        doc['playback']={'instrument':'sanjo-daegeum','instrument_basis':'Original source subtype.'}
        original=deepcopy(doc)
        with self.assertRaisesRegex(ValueError,'기종'):
            prepare_jgb(doc,{'jeonggan_bpm':60},[],instrument='jeongak-daegeum',instrument_basis='Conflicting catalog.')
        self.assertEqual(doc,original)

    def test_editor_default_tempo_is_not_an_explicit_other_instrument_tempo(self):
        doc=document();doc['parts'][0]['instrument']='sogeum'
        with self.assertRaisesRegex(ValueError,'빠르기'):
            convert_jgb(doc,source_name='test',instrument='sogeum')
        _,mapping=convert_jgb(doc,source_name='test',instrument='sogeum',bpm=80)
        self.assertEqual(mapping['jeonggan_bpm'],80)

    def test_non_danso_ir_batch_preserves_authored_breath(self):
        from data.src.playback.tests.test_pipeline import score
        value=score([{'id':'long-breath','pitch':75,'duration':2,'breath_seconds':.4,'release_seconds':.02}])
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            (folder/'score.json').write_text(json.dumps(value),encoding='utf-8')
            catalog={'instrument':'sogeum','items':[{'title':'IR','input':'score.json',
                     'tempo':{'quarter_bpm':60,'beat_unit':'quarter_note','evidence_type':'authored-test'}}]}
            (folder/'catalog.json').write_text(json.dumps(catalog),encoding='utf-8')
            def capture(source,assets,track,instruments,**options):
                prepared=json.loads(Path(source).read_text(encoding='utf-8'))
                self.assertEqual(prepared['notes'],value['notes'])
                track.mkdir(parents=True)
                return {'results':[{'notes':1,'seconds':2,'audio':{'file':'sogeum.wav'}}]}
            with patch('data.src.playback.batch.run',side_effect=capture):
                records=run_batch(folder/'catalog.json',folder/'assets.json',folder/'out',audio_format='wav')
            self.assertEqual(records[0]['status'],'generated')
