import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from load_single_song import verify

CASE = 'sogeum-227-p1'


def release(root):
    score = {'score': {'sourceId': 'new-src'}, 'events': []}
    files = {'source.common.json': json.dumps(score).encode(), 'performance.common.json': b'{}', 'sogeum.m4a': b'audio'}
    entries = []
    for name, data in files.items():
        path = Path(root, 'cases', CASE, name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        entries.append({'path': f'cases/{CASE}/{name}', 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    raw = json.dumps({'schema': 'eolssu-single-song-release/v1', 'sourceId': 'new-src', 'files': entries}).encode()
    Path(root, 'release.json').write_bytes(raw)
    checksum = hashlib.sha256(raw).hexdigest()
    prefix = f'model-audio/drafts/{checksum}/cases/{CASE}/'
    row = {'source': 'new-src', 'score_json': score, 'object_key': prefix + 'sogeum.m4a',
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


if __name__ == '__main__':
    unittest.main()
