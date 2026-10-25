import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from PIL import Image
from playback.recognition import RecognitionError
from playback.staff import parse_musicxml, read_musicxml, recognize_staff


CONTEXT = {'pitch_mode': 'sounding', 'notation_octave_shift': 0, 'style': '정악 단소',
           'tuning_description': 'A440 comparison', 'tuning_a_hz': 440}


def document(body):
    return ('<score-partwise><part id="P1"><measure number="1">' + body +
            '</measure></part></score-partwise>').encode()


class StaffParserTests(unittest.TestCase):
    def test_silent_cue_preserves_time_before_following_note(self):
        result = parse_musicxml(document('''<attributes><divisions>1</divisions></attributes>
          <note><cue/><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note>
          <note><pitch><step>D</step><octave>5</octave></pitch><duration>1</duration></note>'''), CONTEXT)
        self.assertEqual(result['notes'][0], {'id': 'n1', 'duration': '4', 'rest': True})
        self.assertEqual(result['notes'][1]['pitch'], 74)
        self.assertTrue(any('큐' in w for w in result['warnings']))

    def test_audiveris_rounded_sound_tempo_keeps_exact_dotted_metronome(self):
        result = parse_musicxml(document('''<attributes><divisions>1</divisions></attributes>
          <direction><direction-type><metronome><beat-unit>quarter</beat-unit><beat-unit-dot/>
          <per-minute>45</per-minute></metronome></direction-type><sound tempo="68"/></direction>
          <note><pitch><step>A</step><octave>4</octave></pitch><duration>1</duration></note>'''), CONTEXT)
        self.assertEqual(result['quarter_bpm'], 67.5)
        self.assertTrue(any('반올림' in w for w in result['warnings']))

    def test_explicit_transpose_does_not_apply_key_or_octave_display_twice(self):
        result = parse_musicxml(document('''<attributes><divisions>1</divisions><key><fifths>2</fifths></key>
          <transpose><chromatic>-2</chromatic></transpose></attributes>
          <direction><direction-type><octave-shift type="down" size="8"/></direction-type></direction>
          <note><pitch><step>F</step><alter>1</alter><octave>5</octave></pitch><duration>1</duration></note>'''), CONTEXT)
        self.assertEqual(result['notes'][0]['pitch'], 76)
        self.assertEqual(result['metadata']['octave_shifts'][0]['size'], '8')

    def test_duration_fallback_applies_dots_and_tuplet_once(self):
        result = parse_musicxml(document('''<note><pitch><step>C</step><octave>5</octave></pitch>
          <type>quarter</type><dot/><time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification></note>'''), CONTEXT)
        self.assertEqual(result['notes'][0]['duration'], '1')
        self.assertTrue(any('duration' in warning for warning in result['warnings']))

    def test_unsupported_performance_symbols_and_grace_are_visible(self):
        result = parse_musicxml(document('''<attributes><divisions>1</divisions></attributes>
          <note><grace/><pitch><step>B</step><octave>4</octave></pitch></note>
          <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration>
          <notations><ornaments><trill-mark/></ornaments></notations></note>'''), CONTEXT)
        self.assertEqual(len(result['notes']), 1)
        self.assertEqual(result['metadata']['omitted_grace_notes'], 0)
        self.assertEqual(result['notes'][0]['ornament'], {'kind':'grace-before','pitch':71,'duration':'1/16'})
        self.assertTrue(any('trill-mark' in w for w in result['warnings']))

    def test_invalid_polyphonic_repeat_tie_and_entity_inputs_are_rejected(self):
        note = '<note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration></note>'
        base = '<attributes><divisions>1</divisions></attributes>'
        for body in [base + note + '<backup><duration>1</duration></backup>' + note,
                     base + note.replace('<pitch>', '<chord/><pitch>'),
                     base + note + '<barline><repeat direction="backward"/></barline>',
                     base + note + '<attributes><measure-style><measure-repeat>1</measure-repeat></measure-style></attributes>',
                     base + note.replace('</note>', '<tie type="stop"/></note>'),
                     base + note.replace('<step>C</step>', ''),
                     base + note.replace('<duration>1</duration>', '<duration>0</duration>')]:
            with self.subTest(body=body), self.assertRaises(RecognitionError):
                parse_musicxml(document(body), CONTEXT)
        with self.assertRaises(RecognitionError):
            parse_musicxml(b'<!DOCTYPE score-partwise [<!ENTITY x "boom">]>' + document(base + note), CONTEXT)

    def test_slur_stop_and_restart_with_same_number_is_playable(self):
        def note(slur):
            return '<note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><notations>' + slur + '</notations></note>'
        result = parse_musicxml(document('<attributes><divisions>1</divisions></attributes>' +
            note('<slur type="start" number="1"/>') +
            note('<slur type="stop" number="1"/><slur type="start" number="1"/>') +
            note('<slur type="stop" number="1"/>')), CONTEXT)
        self.assertEqual(len({n['slur'] for n in result['notes']}), 1)

    def test_mxl_reads_declared_root_without_extracting_and_rejects_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'score.mxl'
            for name in ('music/score.xml', '../score.xml'):
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr('META-INF/container.xml', '<container><rootfiles><rootfile full-path="' + name +
                                     '" media-type="application/vnd.recordare.musicxml+xml"/></rootfiles></container>')
                    archive.writestr(name, b'<score-partwise/>')
                if name.startswith('..'):
                    with self.assertRaises(RecognitionError):
                        read_musicxml(path)
                else:
                    self.assertEqual(read_musicxml(path), b'<score-partwise/>')
                self.assertFalse((Path(directory) / 'music').exists())

    def test_real_image_adapter_keeps_machine_provenance_and_raw_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            image = directory / '사진.png'
            Image.new('RGB', (20, 20), 'white').save(image)
            exe = directory / 'Audiveris.exe'
            exe.write_bytes(b'test external process boundary')
            (directory / 'tessdata').mkdir()
            def process(command, cwd, env, log, timeout):
                self.assertEqual(command[1:4], ['-batch', '-transcribe', '-export'])
                self.assertEqual(command[-2], '--')
                self.assertTrue(command[-1].isascii())
                self.assertEqual(env['TESSDATA_PREFIX'], 'tessdata')
                output = Path(command[command.index('-output') + 1])
                output.mkdir()
                (output / 'source.musicxml').write_bytes(document('''<attributes><divisions>1</divisions></attributes>
                  <note><pitch><step>A</step><octave>4</octave></pitch><duration>1</duration></note>'''))
                log.write_text('Audiveris fixture process', encoding='utf-8')
            trace = directory / 'trace.json'
            with patch('playback.staff._execute', side_effect=process):
                result = recognize_staff(image, CONTEXT, executable=exe, trace_path=trace)
            self.assertEqual(result['score']['source']['review_status'], 'machine-transcribed-unverified')
            self.assertEqual(result['score']['source']['image_sha256'], hashlib.sha256(image.read_bytes()).hexdigest())
            self.assertEqual(result['score']['notes'][0]['pitch'], 69)
            self.assertEqual(result['regions'], [])
            receipt = json.loads(trace.read_text(encoding='utf-8'))
            self.assertEqual(receipt['status'], 'recognized')
            self.assertTrue((Path(receipt['artifacts']) / 'score.musicxml').is_file())
            self.assertTrue((Path(receipt['artifacts']) / 'audiveris.log').is_file())

    def test_nested_slurs_keep_continuous_legato_and_warn(self):
        def note(slur):
            return '<note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><notations>' + slur + '</notations></note>'
        result = parse_musicxml(document('<attributes><divisions>1</divisions></attributes>' +
            note('<slur type="start" number="1"/>') + note('<slur type="start" number="2"/>') +
            note('<slur type="stop" number="2"/>') + note('<slur type="stop" number="1"/>')), CONTEXT)
        self.assertEqual(len({n['slur'] for n in result['notes']}), 1)
        self.assertTrue(any('겹친' in w for w in result['warnings']))

    def test_performed_pitch_duration_tie_and_slur_are_preserved(self):
        result = parse_musicxml(document('''
          <attributes><divisions>12</divisions><key><fifths>-5</fifths></key>
            <clef><sign>G</sign><line>2</line><clef-octave-change>1</clef-octave-change></clef>
          </attributes>
          <direction><direction-type><metronome><beat-unit>quarter</beat-unit><beat-unit-dot/>
            <per-minute>40</per-minute></metronome></direction-type></direction>
          <note><pitch><step>B</step><alter>-1</alter><octave>5</octave></pitch><duration>18</duration>
            <type>quarter</type><dot/><tie type="start"/><notations><slur type="start" number="1"/></notations></note>
          <note><pitch><step>B</step><alter>-1</alter><octave>5</octave></pitch><duration>4</duration>
            <type>eighth</type><time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>
            <tie type="stop"/><notations><slur type="stop" number="1"/></notations></note>
          <note><rest/><duration>6</duration></note>
        '''), CONTEXT)
        self.assertEqual(result['quarter_bpm'], 60)
        self.assertEqual([n.get('pitch') for n in result['notes']], [82, 82, None])
        self.assertEqual([n['duration'] for n in result['notes']], ['3/2', '1/3', '1/2'])
        self.assertEqual([n.get('tie') for n in result['notes']], ['start', 'stop', None])
        self.assertEqual(result['notes'][0]['slur'], result['notes'][1]['slur'])
        self.assertEqual(result['metadata']['clefs'][0]['octave_change'], 1)


if __name__ == '__main__':
    unittest.main()
