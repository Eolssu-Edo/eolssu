import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import numpy as np
from PIL import Image

from playback.recognition import RecognitionError
from playback.staff import recognize_staff
from playback.staff_camera import prepare_retry


CONTEXT = {'pitch_mode': 'sounding', 'notation_octave_shift': 0,
           'style': 'staff', 'tuning_description': 'A440'}


def omr(path, interline=16):
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('sheet#1/sheet#1.xml',
            f'<sheet><scale><interline main="{interline}"/></scale><page><system>'
            '<staff><lines>' + '<line/>'*5 + '</lines></staff></system></page></sheet>')


def shaded(path):
    # Synthetic lighting fixture, never represented as a camera evaluation sample.
    pixels = np.tile(np.linspace(105, 190, 400, dtype=np.uint8), (160, 1))
    pixels[40:43, 30:370] = 15
    Image.fromarray(pixels).save(path)


def music(chord=False):
    note = '<note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration></note>'
    extra = note.replace('<pitch>', '<chord/><pitch>') if chord else ''
    return ('<score-partwise><part id="P1"><measure number="1"><attributes><divisions>1</divisions>'
            '</attributes>' + note + extra + '</measure></part></score-partwise>').encode()


class CameraPreparationTests(unittest.TestCase):
    def test_shading_retry_preserves_source_and_records_invertible_geometry(self):
        with tempfile.TemporaryDirectory() as directory:
            d = Path(directory); source = d/'source.png'; graph = d/'source.omr'; target = d/'retry.png'
            shaded(source); omr(graph)
            before = source.read_bytes()
            report = prepare_retry(source, graph, target)
            self.assertTrue(report['applied'])
            self.assertEqual(source.read_bytes(), before)
            self.assertEqual(report['source_sha256'], hashlib.sha256(before).hexdigest())
            with Image.open(target) as image:
                self.assertEqual(image.size, (660, 360))
                out = np.array(image)
            matrix = np.array(report['source_to_preprocessed'])
            inverse = np.array(report['preprocessed_to_source'])
            np.testing.assert_allclose(inverse @ matrix, np.eye(3))
            self.assertLess(abs(int(out[180, 150])-int(out[180, 500])), 15)
            self.assertEqual(report['interline_basis'], 'Audiveris scale/interline; no note labels')

    def test_binary_input_and_invalid_scale_do_not_get_guessed_transforms(self):
        with tempfile.TemporaryDirectory() as directory:
            d = Path(directory); source = d/'source.png'; graph = d/'source.omr'; target = d/'retry.png'
            Image.new('L', (100, 100), 255).save(source); omr(graph)
            self.assertFalse(prepare_retry(source, graph, target)['applied'])
            self.assertFalse(target.exists())
            shaded(source); omr(graph, 0)
            self.assertFalse(prepare_retry(source, graph, target)['applied'])
            self.assertFalse(target.exists())

    def test_retry_is_once_preserves_raw_evidence_and_never_flattens_real_chords(self):
        for unresolved in (False, True):
            with self.subTest(unresolved=unresolved), tempfile.TemporaryDirectory() as directory:
                d = Path(directory); source = d/'photo.png'; shaded(source)
                exe = d/'Audiveris.exe'; exe.write_bytes(b'fixture')
                calls = []
                def execute(command, cwd, env, log, timeout):
                    calls.append(timeout)
                    output = Path(command[command.index('-output')+1]); output.mkdir()
                    (output/'score.musicxml').write_bytes(music(chord=len(calls)==1 or unresolved))
                    omr(output/'score.omr')
                    log.write_text('external boundary fixture', encoding='utf-8')
                model_frames = []
                def model_crop(root, image, graph, *args, **kwargs):
                    with Image.open(image) as frame:
                        model_frames.append(frame.size)
                    self.assertEqual(Path(image).name, 'preprocessed.png')
                    return root, {'corrections': [], 'warnings': []}
                trace = d/'trace.json'
                with patch('playback.staff._execute', side_effect=execute), \
                     patch('playback.staff_metadata.enrich_musicxml', side_effect=model_crop), \
                     patch('playback.staff_rhythm.enrich_rhythm', side_effect=model_crop):
                    if unresolved:
                        with self.assertRaises(RecognitionError):
                            recognize_staff(source, CONTEXT, executable=exe, trace_path=trace, model='fixture')
                    else:
                        result = recognize_staff(source, CONTEXT, executable=exe, trace_path=trace, model='fixture')
                        self.assertEqual(len(result['score']['notes']), 1)
                        self.assertEqual(result['score']['source']['review_status'], 'machine-transcribed-unverified')
                        self.assertTrue(result['provider']['image_preprocessing']['applied'])
                self.assertEqual(len(calls), 2)
                self.assertEqual(model_frames, [(660, 360), (660, 360)])
                self.assertLess(calls[1], calls[0])
                receipt = json.loads(trace.read_text(encoding='utf-8'))
                artifacts = Path(receipt['artifacts'])
                self.assertTrue((artifacts/'original-attempt'/'score.musicxml').exists())
                self.assertTrue((artifacts/'original-attempt'/'source.png').exists())
                self.assertTrue((artifacts/'preprocessed-source.png').exists())
                self.assertEqual(receipt['image_sha256'], hashlib.sha256(source.read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
