"""Photo -> review -> real MIDI/WAV API, using a tiny CAT-shaped fixture."""
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from copy import deepcopy
from unittest.mock import patch

import numpy as np
from PIL import Image
import soundfile as sf
from fastapi.testclient import TestClient

from playback.service import create_app


def asset(root):
    audio = io.BytesIO()
    sf.write(audio, .2*np.sin(2*np.pi*440*np.arange(44100*4)/44100), 44100, format="WAV")
    # The production CAT parser reads exactly these preset fields.
    from playback.score import PROFILES
    xml = '''<Ableton><MultiSampler><MultiSamplePart>
    <VelocityRange><Min Value="1"/><Max Value="127"/></VelocityRange>
    <SelectorRange><Min Value="0"/><Max Value="127"/></SelectorRange>
    <TuneScale Value="100"/><SampleRef><FileRef><Name Value="a.wav"/></FileRef></SampleRef>
    <KeyRange><Min Value="66"/><Max Value="92"/></KeyRange><RootKey Value="69"/>
    <Detune Value="0"/><Volume Value="1"/><SampleStart Value="0"/><SampleEnd Value="176399"/>
    <SustainLoop><Mode Value="0"/></SustainLoop></MultiSamplePart><VolumeAndPan>
    <Envelope><AttackTime><Manual Value="0"/></AttackTime><ReleaseTime><Manual Value="50"/></ReleaseTime></Envelope>
    <Volume><Manual Value="0"/></Volume></VolumeAndPan></MultiSampler></Ableton>'''
    archive = root/"test.zip"
    with zipfile.ZipFile(archive, "w") as z:
        z.writestr("fixture/"+PROFILES["danso"]["preset_name"], xml)
        z.writestr("fixture/a.wav", audio.getvalue())
    manifest = root/"assets.json"
    manifest.write_text(json.dumps({"danso":str(archive)}), encoding="utf-8")
    return manifest


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.client = TestClient(create_app(self.root/"jobs", asset(self.root)))
        self.client.headers["X-Playback-Client"] = "local"

    def tearDown(self):
        self.client.close()
        self.tmp.cleanup()

    def upload(self, notation="staff"):
        image = io.BytesIO()
        Image.new("RGB", (320,100), "white").save(image, format="PNG")
        response = self.client.post("/api/scores", params={"notation":notation}, content=image.getvalue(), headers={"Content-Type":"image/png"})
        self.assertEqual(response.status_code,201, response.text)
        return response.json()

    def save(self, job):
        draft = job["score"]
        pitch = 69 if job["notation"] == "staff" else {"yul":"蕤","register":0}
        if job["notation"] == "jeonggan":
            draft["context"]["huangjong_midi"] = 63
        draft["quarter_bpm"] = 60
        draft["notes"] = [{"id":"a","pitch":pitch,"duration":"1"}]
        result = self.client.put(f'/api/scores/{job["id"]}/draft', json={"revision":job["revision"],"score":draft,"regions":[{"note_id":"a","bbox":[.1,.1,.2,.2]}]})
        self.assertEqual(result.status_code,200,result.text)
        return result.json()

    def test_both_notations_review_render_and_audio_download(self):
        for notation in ("staff","jeonggan"):
            job = self.save(self.upload(notation))
            url = f'/api/scores/{job["id"]}/render'
            denied = self.client.post(url,json={"revision":job["revision"],"approved":False})
            self.assertEqual(denied.status_code,422)
            result = self.client.post(url,json={"revision":job["revision"],"approved":True})
            self.assertEqual(result.status_code,200,result.text)
            value = result.json()
            self.assertEqual(value["vibrato"],"quarter")
            self.assertEqual(value["timeline"][0]["note_ids"],["a"])
            wav = self.client.get(value["audio_url"])
            samples, rate = sf.read(io.BytesIO(wav.content))
            self.assertEqual(rate,44100)
            self.assertGreater(float(np.max(np.abs(samples))),.1)
            self.assertEqual(self.client.get(value["midi_url"]).content[:4],b"MThd")
            repeat = self.client.post(url,json={"revision":job["revision"],"approved":True})
            self.assertEqual(repeat.json()["audio_url"],value["audio_url"])

    def test_rejects_stale_review_invalid_upload_and_cross_origin(self):
        self.assertEqual(self.client.post("/api/scores?notation=staff",content=b"not image").status_code,422)
        job = self.save(self.upload())
        response = self.client.post(f'/api/scores/{job["id"]}/render',json={"revision":0,"approved":True})
        self.assertEqual(response.status_code,409)
        response = self.client.post("/api/scores?notation=staff",content=b"x",headers={"Origin":"https://attacker.example"})
        self.assertEqual(response.status_code,403)

    def test_no_model_returns_unavailable_and_preserves_draft(self):
        job = self.save(self.upload())
        response = self.client.post(f'/api/scores/{job["id"]}/recognize',json={"revision":job["revision"],"context":job["score"]["context"]})
        self.assertEqual(response.status_code,503)
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current["revision"],job["revision"])
        self.assertEqual(current["score"],job["score"])

    def test_browser_assets_have_executable_mime_types(self):
        self.assertTrue(self.client.get('/static/app.js').headers['content-type'].startswith('text/javascript'))
        self.assertTrue(self.client.get('/static/style.css').headers['content-type'].startswith('text/css'))

    def test_invalid_nested_objects_preserve_saved_revision(self):
        from copy import deepcopy
        job = self.save(self.upload())
        for field in ('source','context'):
            for invalid in (None,[],"bad"):
                score = deepcopy(job['score']);score[field] = invalid
                response = self.client.put(f'/api/scores/{job["id"]}/draft',json={'revision':job['revision'],'score':score})
                self.assertEqual(response.status_code,422)
                self.assertEqual(self.client.get(f'/api/scores/{job["id"]}').json()['revision'],job['revision'])

    def test_large_decoded_photo_has_bounded_recognition_input(self):
        rng = np.random.default_rng(19)
        photo = io.BytesIO()
        Image.fromarray(rng.integers(0,256,(2200,2200,3),dtype=np.uint8)).save(photo,format='JPEG',quality=75)
        response = self.client.post('/api/scores?notation=staff',content=photo.getvalue())
        self.assertEqual(response.status_code,201)
        folder = self.root/'jobs'/response.json()['id']
        self.assertLess((folder/'recognition.jpg').stat().st_size,10*1024*1024)
        with Image.open(folder/'recognition.jpg') as small, Image.open(folder/'source.png') as original:
            self.assertAlmostEqual(small.width/small.height,original.width/original.height)

    def test_failed_render_keeps_previous_audio_and_cleans_staging(self):
        job = self.save(self.upload())
        url = f'/api/scores/{job["id"]}/render'
        previous = self.client.post(url,json={'revision':job['revision'],'approved':True}).json()
        job['score']['notes'][0]['duration'] = '8'
        job = self.client.put(f'/api/scores/{job["id"]}/draft',json={
            'revision':job['revision'],'score':job['score'],'regions':job['regions']}).json()
        failed = self.client.post(url,json={'revision':job['revision'],'approved':True})
        self.assertEqual(failed.status_code,422)
        self.assertIn('exhausted',failed.json()['detail'])
        self.assertEqual(self.client.get(previous['audio_url']).status_code,200)
        self.assertFalse(list((self.root/'jobs'/job['id']/'renders').glob('pending-*')))

    def test_oversized_upload_and_invalid_regions_are_rejected(self):
        response = self.client.post('/api/scores?notation=staff',content=b'x'*(8*1024*1024+1))
        self.assertEqual(response.status_code,413)
        job = self.save(self.upload())
        response = self.client.put(f'/api/scores/{job["id"]}/draft',json={
            'revision':job['revision'],'score':job['score'],'regions':[{'note_id':'a','bbox':[.9,.9,.2,.2]}]})
        self.assertEqual(response.status_code,422)
        self.assertEqual(self.client.get(f'/api/scores/{job["id"]}').json()['revision'],job['revision'])

    def machine_result(self, job):
        score = deepcopy(job['score'])
        score['source'].update(review_status='machine-transcribed-unverified',image_sha256='model-jpeg-hash')
        score['notes'] = [{'id':'model-1','pitch':69,'duration':'1'}]
        return {'score':score,'regions':[{'note_id':'model-1','bbox':[.1,.1,.1,.1]}],
                'warnings':['unverified model result'],'provider':{'name':'test'}}

    def configured_client(self):
        self.client.close()
        self.client = TestClient(create_app(self.root/'jobs',self.root/'assets.json',model='local-test'))
        self.client.headers['X-Playback-Client'] = 'local'

    def test_photo_automatic_preview_preserves_provenance_and_confirmation_has_separate_cache(self):
        self.configured_client()
        job = self.upload()
        with patch('playback.recognition.recognize',return_value=self.machine_result(job)) as recognize:
            response = self.client.post(f'/api/scores/{job["id"]}/automatic',json={'revision':0})
        self.assertEqual(response.status_code,200,response.text)
        preview = response.json()
        self.assertEqual(preview['state'],'preview_ready')
        self.assertNotIn('quarter_bpm',recognize.call_args.args[2])
        self.assertEqual(recognize.call_args.args[2]['ornament_pitch_classes'],[1,3,5,8,10])
        self.assertEqual(preview['recognition_context']['ornament_pitch_classes'],[1,3,5,8,10])
        source = self.client.get(preview['result']['score_url']).json()['source']
        self.assertEqual(source['review_status'],'machine-transcribed-unverified')
        self.assertEqual(source['review_mode'],'automatic-preview')
        self.assertNotIn('reviewed_revision',source)
        self.assertEqual(source['image_sha256'],'model-jpeg-hash')
        self.assertEqual(source['original_image_sha256'],job['image_sha256'])
        checked = self.client.post(f'/api/scores/{job["id"]}/render',json={'revision':1,'approved':True}).json()
        self.assertNotEqual(checked['audio_url'],preview['result']['audio_url'])
        self.assertEqual(checked['review_mode'],'user-confirmed')
        self.assertEqual(self.client.get(checked['score_url']).json()['source']['reviewed_revision'],1)

    def test_automatic_render_failure_preserves_machine_draft_and_error(self):
        self.configured_client()
        job = self.upload()
        candidate = self.machine_result(job)
        candidate['score']['notes'][0]['pitch'] = 20
        with patch('playback.recognition.recognize',return_value=candidate):
            response = self.client.post(f'/api/scores/{job["id"]}/automatic',json={'revision':0})
        self.assertEqual(response.status_code,422)
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current['revision'],1)
        self.assertEqual(current['score']['notes'][0]['pitch'],20)
        self.assertEqual(current['state'],'needs_review')
        self.assertIsNone(current['result'])
        self.assertTrue(current['error'])

    def test_revision_change_during_inference_is_not_overwritten(self):
        self.configured_client()
        job = self.upload()
        def recognize(*args,**kwargs):
            self.save(deepcopy(job))
            return self.machine_result(job)
        with patch('playback.recognition.recognize',side_effect=recognize):
            response = self.client.post(f'/api/scores/{job["id"]}/automatic',json={'revision':0})
        self.assertEqual(response.status_code,409)
        current = self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current['score']['notes'][0]['id'],'a')

    def test_failed_page_recognition_preserves_reason_and_previous_draft(self):
        from playback.recognition import RecognitionError
        self.configured_client()
        job=self.save(self.upload())
        with patch('playback.recognition.recognize',side_effect=RecognitionError('Unknown or uncertain glyph')):
            response=self.client.post(f'/api/scores/{job["id"]}/automatic',json={'revision':job['revision']})
        self.assertEqual(response.status_code,503)
        current=self.client.get(f'/api/scores/{job["id"]}').json()
        self.assertEqual(current['score'],job['score'])
        self.assertEqual(current['revision'],job['revision'])
        self.assertEqual(current['error'],response.json()['detail'])

    def test_jeonggan_default_is_an_explicit_preview_assumption(self):
        self.configured_client()
        job = self.upload('jeonggan')
        candidate = self.machine_result(job)
        candidate['score']['context']['huangjong_midi'] = 63
        candidate['score']['notes'][0]['pitch'] = {'yul':'蕤','register':0}
        with patch('playback.recognition.recognize',return_value=candidate) as recognize:
            response = self.client.post(f'/api/scores/{job["id"]}/automatic',json={'revision':0,'vibrato':'third'})
        self.assertEqual(response.status_code,200,response.text)
        self.assertEqual(recognize.call_args.args[2]['huangjong_midi'],63)
        self.assertEqual(recognize.call_args.args[0].name,'source.png')
        current = response.json()
        self.assertEqual(current['result']['vibrato'],'third')
        self.assertIn('사진에서 확인한 조율값이 아닙니다',' '.join(current['score']['assumptions']))

    def test_page_longer_than_two_minutes_is_accepted_but_ten_minute_bound_remains(self):
        job = self.upload()
        score = deepcopy(job['score'])
        score['notes'] = [{'id':f'n{i}','pitch':69,'duration':'1'} for i in range(130)]
        response=self.client.put(f'/api/scores/{job["id"]}/draft',json={
            'revision':0,'score':score,'regions':[]})
        self.assertEqual(response.status_code,200,response.text)
        score['notes'] = [{'id':f'n{i}','pitch':69,'duration':'1'} for i in range(601)]
        rejected=self.client.put(f'/api/scores/{job["id"]}/draft',json={
            'revision':1,'score':score,'regions':[]})
        self.assertEqual(rejected.status_code,422)
        self.assertEqual(self.client.get(f'/api/scores/{job["id"]}').json()['revision'],1)


if __name__ == "__main__":
    unittest.main()
