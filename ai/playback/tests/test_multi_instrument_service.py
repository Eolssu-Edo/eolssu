"""Instrument routing, draft reuse, and failure preservation at the API boundary."""
from copy import deepcopy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from fastapi.testclient import TestClient
import mido
import numpy as np
from PIL import Image
import soundfile as sf

from playback.catalog import INSTRUMENTS
from playback.score import PROFILES
from playback.service import create_app


def four_assets(root):
    mapping = {}
    for i, key in enumerate(INSTRUMENTS):
        audio = io.BytesIO()
        samples = .3 * np.sin(2 * np.pi * (300 + 80*i) * np.arange(88200) / 44100)
        sf.write(audio, samples, 44100, format='WAV', subtype='PCM_16')
        xml = '''<Ableton><MultiSampler><MultiSamplePart><SampleRef><FileRef><Name Value="a.wav"/></FileRef></SampleRef>
        <VelocityRange><Min Value="1"/><Max Value="127"/></VelocityRange>
        <SelectorRange><Min Value="0"/><Max Value="127"/></SelectorRange><TuneScale Value="100"/>
        <KeyRange><Min Value="40"/><Max Value="100"/></KeyRange><RootKey Value="75"/>
        <Detune Value="0"/><Volume Value="1"/><SampleStart Value="0"/><SampleEnd Value="88199"/>
        <SustainLoop><Mode Value="0"/></SustainLoop></MultiSamplePart><VolumeAndPan>
        <Envelope><AttackTime><Manual Value="0"/></AttackTime><ReleaseTime><Manual Value="50"/></ReleaseTime></Envelope>
        <Volume><Manual Value="0"/></Volume></VolumeAndPan></MultiSampler></Ableton>'''
        archive = root/f'{key}.zip'
        with zipfile.ZipFile(archive, 'w') as z:
            z.writestr('fixture/'+PROFILES[key]['preset_name'], xml)
            z.writestr('fixture/a.wav', audio.getvalue())
        mapping[key] = archive.name
    path = root/'assets.json'
    path.write_text(json.dumps(mapping), encoding='utf-8')
    return path


class MultiInstrumentServiceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.assets = four_assets(self.root)
        self.client = TestClient(create_app(self.root/'jobs', self.assets, model='local-test'))
        self.client.headers['X-Playback-Client'] = 'local'

    def tearDown(self):
        self.client.close()
        self.tmp.cleanup()

    def upload(self, instrument='danso'):
        image = io.BytesIO()
        Image.new('RGB', (80, 60), 'white').save(image, format='PNG')
        response = self.client.post('/api/scores', params={'notation':'staff', 'instrument':instrument},
                                    content=image.getvalue())
        self.assertEqual(response.status_code, 201, response.text)
        return response.json()

    def save(self, job, pitch=75):
        score = deepcopy(job['score'])
        score['notes'] = [{'id':'source-a', 'pitch':pitch, 'duration':'1/2'}]
        response = self.client.put(f'/api/scores/{job["id"]}/draft', json={
            'revision':job['revision'], 'score':score, 'instrument':job['instrument']})
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def preview(self, job, instrument, **options):
        return self.client.post(f'/api/scores/{job["id"]}/preview', json={
            'revision':job['revision'], 'instrument':instrument, **options})

    def test_same_draft_four_banks_and_cache_never_rerun_recognition(self):
        job = self.save(self.upload())
        urls, audio, score = [], [], deepcopy(job['score'])
        with patch('playback.recognition.recognize') as recognize:
            for instrument in INSTRUMENTS:
                response = self.preview(job, instrument)
                self.assertEqual(response.status_code, 200, response.text)
                value = response.json(); result = value['result']
                self.assertEqual(value['score'], score)
                self.assertEqual(value['revision'], job['revision'])
                self.assertEqual(result['instrument'], instrument)
                self.assertEqual(result['review_mode'], 'automatic-preview')
                self.assertTrue(result['audio_url'].endswith(f'/{instrument}.wav'))
                urls.append(result['audio_url'])
                raw = self.client.get(result['audio_url']).content
                pcm, rate = sf.read(io.BytesIO(raw))
                self.assertEqual(rate, 44100)
                self.assertGreater(np.max(np.abs(pcm)), .1)
                audio.append(raw)
                midi = mido.MidiFile(file=io.BytesIO(self.client.get(result['midi_url']).content))
                notes = [m.note for t in midi.tracks for m in t if m.type=='note_on' and m.velocity]
                self.assertEqual(notes, [75])
                exported = self.client.get(result['score_url']).json()
                self.assertEqual(exported['notes'], score['notes'])
                self.assertNotIn('reviewed_revision', exported['source'])
                repeated = self.preview(job, instrument).json()['result']
                self.assertEqual(repeated['audio_url'], result['audio_url'])
            recognize.assert_not_called()
        self.assertEqual(len(set(urls)), 4)
        self.assertEqual(len(set(audio)), 4)  # Synthetic banks deliberately have distinct waves.

    def test_lower_daegeum_note_not_rejected_by_danso_validator_or_folded(self):
        job = self.save(self.upload('jeongak-daegeum'), pitch=60)
        response = self.preview(job, 'jeongak-daegeum')
        self.assertEqual(response.status_code, 200, response.text)
        previous = response.json()
        self.assertEqual(previous['result']['timeline'][0]['pitch'], 60)
        failed = self.preview(job, 'sogeum')
        self.assertEqual(failed.status_code, 422)
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current.pop('error_instrument'), 'sogeum')
        self.assertIn('60', current.pop('error'))
        self.assertEqual(current, previous)
        self.assertEqual(self.client.get(previous['result']['audio_url']).status_code, 200)

    def test_missing_bank_is_visible_and_does_not_destroy_previous_audio(self):
        job = self.save(self.upload())
        previous = self.preview(job, 'danso').json()
        mapping = json.loads(self.assets.read_text())
        mapping.pop('sogeum')
        self.assets.write_text(json.dumps(mapping))
        options = self.client.get('/api/config').json()['instruments']
        self.assertEqual(len(options), 4)
        self.assertFalse(next(o for o in options if o['id']=='sogeum')['available'])
        self.assertEqual(self.preview(job, 'sogeum').status_code, 503)
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current.pop('error_instrument'), 'sogeum')
        self.assertTrue(current.pop('error'))
        self.assertEqual(current, previous)
        recovered = self.preview(job, 'danso').json()
        self.assertNotIn('error', recovered)

    def test_corrupt_bank_reports_503_and_other_instrument_still_works(self):
        job = self.save(self.upload())
        previous = self.preview(job, 'danso').json()['result']
        (self.root/'sogeum.zip').write_bytes(b'corrupt')
        failure = self.preview(job, 'sogeum')
        self.assertEqual(failure.status_code, 503, failure.text)
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current['result'], previous)
        self.assertEqual(current['error_instrument'], 'sogeum')
        self.assertEqual(current['score']['notes'][0]['pitch'], 75)
        self.assertEqual(self.preview(job, 'jeongak-daegeum').status_code, 200)

    def test_render_revision_race_and_worker_lock_preserve_newer_draft(self):
        from playback.__main__ import run
        job = self.save(self.upload())
        observed = []
        def racing_run(*args, **kwargs):
            observed.append(self.preview(job, 'sogeum').status_code)
            new_score = deepcopy(job['score'])
            new_score['notes'][0]['pitch'] = 77
            update = self.client.put(f'/api/scores/{job["id"]}/draft', json={
                'revision':job['revision'], 'score':new_score, 'instrument':'danso'})
            self.assertEqual(update.status_code, 200)
            return run(*args, **kwargs)
        with patch('playback.service.run', side_effect=racing_run):
            response = self.preview(job, 'jeongak-daegeum')
        self.assertEqual(response.status_code, 409, response.text)
        self.assertEqual(observed, [409])
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current['revision'], job['revision']+1)
        self.assertEqual(current['score']['notes'][0]['pitch'], 77)
        self.assertNotIn('error', current)
        self.assertIsNone(current['result'])
        self.assertFalse(list((self.root/'jobs'/job['id']/'renders').iterdir()))
        self.assertEqual(self.preview(current, 'danso').status_code, 200)

    def test_truncated_compressed_preset_and_missing_sample_name_are_asset_errors(self):
        job = self.save(self.upload())
        previous = self.preview(job, 'danso').json()['result']
        archive = self.root/'sogeum.zip'
        original = archive.read_bytes()
        preset = 'fixture/'+PROFILES['sogeum']['preset_name']
        with zipfile.ZipFile(io.BytesIO(original)) as z:
            xml = z.read(preset)
            audio = z.read('fixture/a.wav')
        for corrupted in (b'\x1f\x8b\x08\x00', xml.replace(b'<Name Value="a.wav"/>', b'')):
            with self.subTest(preset=corrupted[:16]):
                with zipfile.ZipFile(archive, 'w') as z:
                    z.writestr(preset, corrupted)
                    z.writestr('fixture/a.wav', audio)
                response = self.preview(job, 'sogeum')
                self.assertEqual(response.status_code, 503, response.text)
                current = self.client.get(f'/api/scores/{job["id"]}').json()
                self.assertEqual(current['result'], previous)
                self.assertEqual(current['error_instrument'], 'sogeum')
                self.assertEqual(current['score'], job['score'])
        archive.write_bytes(original)
        self.assertEqual(self.preview(job, 'sogeum').status_code, 200)

    def test_legacy_job_and_cached_danso_artifacts_still_open(self):
        job = self.save(self.upload())
        old = self.preview(job, 'danso').json()
        old.pop('instrument'); old['result'].pop('instrument'); old['result'].pop('instrument_label')
        (self.root/'jobs'/job['id']/'job.json').write_text(json.dumps(old), encoding='utf-8')
        response = self.client.post(f'/api/scores/{job["id"]}/preview', json={'revision':job['revision']})
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()['instrument'], 'danso')
        self.assertEqual(response.json()['result']['audio_url'], old['result']['audio_url'])

    def test_invalid_instrument_stale_revision_and_artifact_allowlist(self):
        job = self.save(self.upload())
        self.assertEqual(self.preview(job, '../danso').status_code, 422)
        stale = self.client.post(f'/api/scores/{job["id"]}/preview', json={
            'revision':0, 'instrument':'sogeum'})
        self.assertEqual(stale.status_code, 409)
        result = self.preview(job, 'danso').json()['result']
        self.assertEqual(self.client.get(result['audio_url'].replace('danso.wav', 'manifest.json')).status_code, 404)
        confirmed = self.client.post(f'/api/scores/{job["id"]}/render', json={
            'revision':job['revision'], 'instrument':'sogeum', 'approved':False})
        self.assertEqual(confirmed.status_code, 422)

    def test_selected_instrument_automatic_preserves_unverified_machine_draft(self):
        job = self.upload('sogeum')
        score = deepcopy(job['score'])
        score['notes'] = [{'id':'source-a', 'pitch':75, 'duration':'1/2'}]
        score['source']['review_status'] = 'machine-transcribed-unverified'
        recognized = {'score':score, 'regions':[], 'warnings':[], 'provider':{'name':'test'}}
        with patch('playback.recognition.recognize', return_value=recognized) as recognize:
            response = self.client.post(f'/api/scores/{job["id"]}/automatic', json={'revision':0})
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(recognize.call_count, 1)
        result = response.json()
        self.assertEqual(result['instrument'], 'sogeum')
        self.assertEqual(result['result']['instrument'], 'sogeum')
        self.assertEqual(result['score']['source']['review_status'], 'machine-transcribed-unverified')


if __name__ == '__main__':
    unittest.main()
