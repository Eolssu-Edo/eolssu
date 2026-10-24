"""Photo -> review -> real MIDI/WAV API, using a tiny CAT-shaped fixture."""
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

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


if __name__ == "__main__":
    unittest.main()
