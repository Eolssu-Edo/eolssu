import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from load_single_song import check_upload, verify

CASE = 'sogeum-227-p1'


def release(root):
    score = {'score': {'sourceId': 'new-src', 'title': '도라지타령', 'instrument': 'SOGEUM', 'scoreVersion': 1},
             'music': {'bpmUnit': 'QUARTER'}, 'events': [{'kind': 'NOTE'}, {'kind': 'REST'}]}
    performance = {'bpm': 144.0, 'bpmUnit': 'QUARTER', 'durationMs': 1000.004}
    files = {'source.common.json': json.dumps(score).encode(), 'performance.common.json': json.dumps(performance).encode(), 'sogeum.m4a': b'audio'}
    entries = []
    for name, data in files.items():
        path = Path(root, 'cases', CASE, name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        entries.append({'path': f'cases/{CASE}/{name}', 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    raw = json.dumps({'schema': 'eolssu-single-song-release/v1', 'caseId': CASE, 'sourceId': 'new-src', 'files': entries}).encode()
    Path(root, 'release.json').write_bytes(raw)
    checksum = hashlib.sha256(raw).hexdigest()
    prefix = f'model-audio/drafts/{checksum}/cases/{CASE}/'
    row = {'source': 'new-src', 'score_json': score, 'title': '도라지타령', 'instrument_code': 'SOGEUM', 'score_version': 1,
           'note_count': 1, 'default_bpm': 144, 'bpm_unit': 'QUARTER', 'duration_ms': 1000, 'object_key': prefix + 'sogeum.m4a',
           'performance_object_key': prefix + 'performance.common.json', 'sha256': entries[2]['sha256'],
           'source_common_sha256': entries[0]['sha256'], 'generation_metadata': {'manifestSha256': checksum}}
    return {'manifestSha256': checksum, 'references': {'jangdans': [], 'pitches': [], 'instruments': []}, 'rows': [row]}


class LoadSingleSongTests(unittest.TestCase):
    def test_bound_release_lists_files_then_the_marker(self):
        with tempfile.TemporaryDirectory() as root:
            model = release(root)
            objects = verify(root, model)
            self.assertEqual([item['path'] for item in objects][-1], 'release.json')
            self.assertTrue(all(item['key'].startswith(f"model-audio/drafts/{model['manifestSha256']}/") for item in objects))

    def test_changed_audio_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            model = release(root)
            Path(root, 'cases', CASE, 'sogeum.m4a').write_bytes(b'other')
            with self.assertRaises(ValueError):
                verify(root, model)

    def test_model_row_pointing_elsewhere_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            model = release(root)
            model['rows'][0]['object_key'] = model['rows'][0]['object_key'].replace(CASE, 'sogeum-005-p1')
            with self.assertRaises(ValueError):
                verify(root, model)

    def test_row_field_that_differs_from_the_release_is_rejected(self):
        for field, value in (('title', '도라지타령 8각'), ('note_count', 2), ('default_bpm', 120), ('duration_ms', 999)):
            with self.subTest(field=field), tempfile.TemporaryDirectory() as root:
                model = release(root)
                model['rows'][0][field] = value
                with self.assertRaises(ValueError):
                    verify(root, model)

    def test_extra_release_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            model = release(root)
            manifest = json.loads(Path(root, 'release.json').read_text())
            manifest['files'].append(dict(manifest['files'][0], path='cases/other/source.common.json'))
            raw = json.dumps(manifest).encode()
            Path(root, 'release.json').write_bytes(raw)
            model['manifestSha256'] = model['rows'][0]['generation_metadata']['manifestSha256'] = hashlib.sha256(raw).hexdigest()
            with self.assertRaises(ValueError):
                verify(root, model)

    def receipt(self, objects, **changes):
        report = {'status': 'uploaded', 'phase': 'complete', 'manifestSha256': self.checksum,
                  'objects': [dict(item, status='new') for item in objects]}
        report.update(changes)
        return report

    def test_load_needs_a_complete_upload_of_the_same_objects(self):
        with tempfile.TemporaryDirectory() as root:
            model = release(root)
            self.checksum = model['manifestSha256']
            objects = verify(root, model)
            check_upload(self.receipt(objects), objects, self.checksum)
            for bad in (self.receipt(objects, status='validated-dry-run'), self.receipt(objects, phase='payload-upload'),
                        self.receipt(objects[:-1]), self.receipt(objects, manifestSha256='0' * 64)):
                with self.assertRaises(ValueError):
                    check_upload(bad, objects, self.checksum)
            failed = self.receipt(objects)
            failed['objects'][0]['status'] = 'missing'
            with self.assertRaises(ValueError):
                check_upload(failed, objects, self.checksum)


if __name__ == '__main__':
    unittest.main()
