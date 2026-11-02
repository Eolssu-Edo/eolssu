import wave
import tempfile

import numpy as np
from fastapi.testclient import TestClient

from evaluation.local_score_web import app, _LOCK
from ai_service.pipelines.scoring import NoteResult, ScoreResult
from ai_service.pipelines.scoring import HWANG_HZ, Note


BASE = "http://127.0.0.1:8765"
ORIGIN = {"origin": BASE, "content-type": "application/octet-stream"}
client = TestClient(app, base_url=BASE, raise_server_exceptions=False)


def test_home_serves_selectable_form():
    response = client.get('/')
    assert response.status_code == 200
    assert '아리랑' in response.text
    assert 'type="file"' in response.text


def test_cross_origin_or_non_local_host_cannot_submit():
    url = '/score?score_id=ngc-standard-2022-p173&bpm=96&start=0'
    assert client.post(url, content=b'test', headers={'origin': 'https://example.com', 'content-type': 'application/octet-stream'}).status_code == 403
    assert client.post(url, content=b'test', headers={'content-type': 'application/octet-stream'}).status_code == 403
    assert client.post(url, content=b'test', headers={'host': 'example.com', **ORIGIN}).status_code == 403


def test_invalid_inputs_are_rejected():
    assert client.post('/score?score_id=bad&bpm=96&start=0', content=b'test', headers=ORIGIN).status_code == 400
    assert client.post('/score?score_id=ngc-standard-2022-p173&bpm=0&start=0', content=b'test', headers=ORIGIN).status_code == 400
    assert client.post('/score?score_id=ngc-standard-2022-p173&bpm=96&start=0', content=b'', headers=ORIGIN).status_code == 400


def test_silence_reports_failure_without_a_score(tmp_path):
    path = tmp_path / 'silent.wav'
    with wave.open(str(path), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(b'\0\0' * 16000)
    response = client.post('/score?score_id=ngc-standard-2022-p173&bpm=96&start=0', content=path.read_bytes(), headers=ORIGIN)
    assert response.status_code == 200
    assert response.json()['status'] == 'FAILED'
    assert response.json()['fail_reason'] == 'NO_SOUND'


def test_result_and_temporary_file_cleanup(tmp_path, monkeypatch):
    from evaluation import local_score_web
    monkeypatch.setattr(tempfile, 'tempdir', str(tmp_path))
    def fake_score(path, source, bpm, start):
        assert path.read_bytes() == b'recording'
        assert (source, bpm, start) == ('ngc-standard-2022-p173', 96, 0)
        return ScoreResult('DONE', total=81, pitch=91, timing=93, duration=60,
                           notes=[NoteResult(4, 'CORRECT')])
    monkeypatch.setattr(local_score_web, 'score_recording', fake_score)
    response = client.post('/score?score_id=ngc-standard-2022-p173&bpm=96&start=0',
                           content=b'recording', headers=ORIGIN)
    assert response.status_code == 200
    assert response.json()['total'] == 81
    assert response.json()['notes'][0]['idx'] == 4
    assert list(tmp_path.iterdir()) == []


def test_second_analysis_does_not_run_concurrently():
    assert _LOCK.acquire(blocking=False)
    try:
        response = client.post('/score?score_id=ngc-standard-2022-p173&bpm=96&start=0',
                               content=b'recording', headers=ORIGIN)
        assert response.status_code == 409
    finally:
        _LOCK.release()


def test_compare_defaults_match_current_score_and_invalid_weights_are_rejected(monkeypatch):
    from evaluation import local_score_web
    notes = [Note(i % 2 * 2, 1, idx=i) for i in range(16)]
    f0 = np.concatenate([np.full(50, HWANG_HZ * 2 ** (n.semitone / 12)) for n in notes])
    monkeypatch.setattr(local_score_web, 'prepare_recording', lambda *_: (f0, notes, None))
    params = {'score_id': 'ngc-standard-2022-p173', 'bpm': 120, 'start': 0,
              'pitch_cent': 50, 'duration_ratio': .25, 'onset_spread': 4,
              'pitch_penalty': 0, 'timing_penalty': 0, 'large_error_exponent': 1,
              'weight_pitch': 1, 'weight_timing': 1, 'weight_duration': 1}
    response = client.post('/compare', params=params, content=b'recording', headers=ORIGIN)
    assert response.status_code == 200
    assert response.json()['result']['status'] == 'DONE'
    assert response.json()['result']['total'] == 100
    assert response.json()['onset_tolerance_ms'] == 62.5
    params.update(weight_pitch=0, weight_timing=0, weight_duration=0)
    assert client.post('/compare', params=params, content=b'recording', headers=ORIGIN).status_code == 400
