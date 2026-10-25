"""Local Audiveris OMR and a bounded, monophonic MusicXML reader.

MusicXML pitch and duration are performance data. Clefs, octave-shift lines,
key signatures, dots and tuplets must not be applied a second time. Only an
explicit instrument transpose changes the encoded pitch. See MusicXML 4.0:
https://www.w3.org/2021/06/musicxml40/tutorial/midi-compatible-part/
https://www.w3.org/2021/06/musicxml40/tutorial/notation-basics/
"""
from copy import deepcopy
from fractions import Fraction
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile

from .recognition import RecognitionError, _context
from .score import PPQN

MAX_XML = 8 * 1024 * 1024
MAX_ARCHIVE = 32 * 1024 * 1024
TYPE_QUARTERS = {'long': 16, 'breve': 8, 'whole': 4, 'half': 2, 'quarter': 1,
                 'eighth': Fraction(1, 2), '16th': Fraction(1, 4), '32nd': Fraction(1, 8),
                 '64th': Fraction(1, 16), '128th': Fraction(1, 32), '256th': Fraction(1, 64)}
WARNING = 'Audiveris 자동 인식 미검수 초안입니다. 조표·8va·임시표·잇단음표·장식음 누락이나 오독을 원보와 대조하세요.'


def require(condition, message):
    if not condition:
        raise RecognitionError(message)


def number(value, label):
    require(isinstance(value, (str, int, float)) and len(str(value)) <= 32, f'Invalid {label}')
    # MusicXML numeric types are decimal/integer. Exponents are not valid here
    # and can otherwise ask Fraction to allocate an enormous power of ten.
    require('e' not in str(value).lower(), f'Invalid {label}')
    try:
        return Fraction(str(value))
    except (ValueError, ZeroDivisionError) as exc:
        raise RecognitionError(f'Invalid {label}') from exc


def xml_root(raw):
    require(0 < len(raw) <= MAX_XML, 'MusicXML is empty or too large')
    # External DOCTYPEs emitted by Audiveris are allowed; ElementTree does not
    # fetch them. Internal entity declarations are never accepted or expanded.
    try:
        text = raw.decode('utf-8-sig')
    except UnicodeError as exc:
        raise RecognitionError('MusicXML must use UTF-8') from exc
    require(not re.search(r'<!ENTITY|<!DOCTYPE[^>]*\[', text, re.I), 'MusicXML entities are unsupported')
    try:
        root = ET.fromstring(raw)
    except (ET.ParseError, ValueError) as exc:
        raise RecognitionError('Invalid MusicXML') from exc
    for element in root.iter():
        element.tag = element.tag.rsplit('}', 1)[-1]
    return root


def read_musicxml(path):
    """Read the declared MXL root without extracting archive entries."""
    path = Path(path)
    require(path.stat().st_size <= MAX_ARCHIVE, 'OMR output is too large')
    if path.suffix.lower() != '.mxl':
        return path.read_bytes()
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            require(len(infos) <= 100 and len({i.filename for i in infos}) == len(infos), 'Invalid MXL entries')
            require(sum(i.file_size for i in infos) <= MAX_ARCHIVE, 'MXL expands beyond the limit')
            def read(name):
                require('\\' not in name and not PurePosixPath(name).is_absolute()
                        and '..' not in PurePosixPath(name).parts, 'Unsafe MXL root path')
                info = archive.getinfo(name)
                require(info.file_size <= MAX_XML, 'MXL entry is too large')
                with archive.open(info) as stream:
                    return stream.read(MAX_XML + 1)
            container = xml_root(read('META-INF/container.xml'))
            roots = container.findall('./rootfiles/rootfile')
            selected = [r for r in roots if r.get('media-type') in (None, 'application/vnd.recordare.musicxml+xml')]
            require(len(selected) == 1 and selected[0].get('full-path'), 'MXL must contain one score root')
            return read(selected[0].get('full-path'))
    except (zipfile.BadZipFile, KeyError, RuntimeError, OSError) as exc:
        raise RecognitionError('Cannot read the MXL score') from exc


def _pitch_midi(pitch, transpose=0):
    require(pitch is not None, 'Missing note pitch')
    step = pitch.findtext('step')
    require(isinstance(step, str) and len(step) == 1 and step in 'CDEFGAB', 'Invalid pitch step')
    octave = number(pitch.findtext('octave'), 'octave')
    alter = number(pitch.findtext('alter', '0'), 'alter')
    midi = 12 * (octave + 1) + {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}[step] + alter + transpose
    require(midi.denominator == 1 and 0 <= midi <= 127, 'Microtonal or out-of-range pitch is unsupported')
    return int(midi)


def _upper_neighbor(pitch, midi, fifths, context, transpose):
    if context.get('ornament_pitch_classes'):
        return next(p for p in range(midi+1, midi+13) if p % 12 in context['ornament_pitch_classes'])
    step = pitch.findtext('step')
    index = 'CDEFGAB'.index(step) + 1
    upper = ET.Element('pitch')
    ET.SubElement(upper, 'step').text = 'CDEFGAB'[index % 7]
    ET.SubElement(upper, 'octave').text = str(int(pitch.findtext('octave')) + (index // 7))
    order = 'BEADGCF' if fifths < 0 else 'FCGDAEB'
    ET.SubElement(upper, 'alter').text = str((-1 if fifths < 0 else 1) if 'CDEFGAB'[index % 7] in order[:abs(fifths)] else 0)
    return _pitch_midi(upper, transpose)


def parse_musicxml(raw, context):
    """Return parsed musical events and OMR metadata, without claiming accuracy."""
    _context('staff', context)
    root = xml_root(raw)
    require(root.tag == 'score-partwise', 'Only partwise MusicXML is supported')
    parts = root.findall('part')
    require(len(parts) == 1, '여러 파트 악보입니다. 단선율 한 파트로 사진을 나눠 주세요.')
    require(not root.findall('.//repeat') and not root.findall('.//ending') and not root.findall('.//measure-repeat')
            and not root.findall('.//segno') and not root.findall('.//coda'), '반복·도돌이표는 아직 지원하지 않습니다.')
    require(not any(set(s.attrib) & {'dacapo', 'dalsegno', 'tocoda', 'fine'} for s in root.findall('.//sound')),
            '반복 재생 지시는 아직 지원하지 않습니다.')
    notes, warnings, tempos = [], [], []
    metadata = {'software': [e.text for e in root.findall('./identification/encoding/software') if e.text],
                'keys': [], 'clefs': [], 'octave_shifts': [], 'omitted_grace_notes': 0}
    divisions = None
    transpose = 0
    voice = None
    active_slurs = set()
    slur_serial = 0
    tie_pitch = None
    pending_grace = []
    key_fifths = 0
    total = Fraction()
    for measure in parts[0].findall('measure'):
        for element in measure:
            tag = element.tag
            if tag == 'attributes':
                value = element.findtext('divisions')
                if value is not None:
                    divisions = number(value, 'divisions')
                    require(divisions > 0, 'MusicXML divisions must be positive')
                require(element.findtext('staves', '1') == '1', '복수 보표는 지원하지 않습니다.')
                for key in element.findall('key'):
                    if key.findtext('fifths') is not None:
                        value = number(key.findtext('fifths'), 'key fifths')
                        require(value.denominator == 1 and -7 <= value <= 7, 'Unsupported key fifths')
                        key_fifths = int(value)
                    metadata['keys'].append({'measure': measure.get('number'), 'fifths': key.findtext('fifths')})
                for clef in element.findall('clef'):
                    shift = number(clef.findtext('clef-octave-change', '0'), 'clef octave')
                    require(shift.denominator == 1 and -2 <= shift <= 2, 'Invalid clef octave')
                    metadata['clefs'].append({'measure': measure.get('number'), 'sign': clef.findtext('sign'),
                                              'line': clef.findtext('line'), 'octave_change': int(shift)})
                transposes = element.findall('transpose')
                require(len(transposes) <= 1, 'Multiple instrument transpositions are unsupported')
                if transposes:
                    trans = transposes[0]
                    require(trans.find('double') is None, 'Octave doubling is unsupported')
                    transpose = number(trans.findtext('chromatic', '0'), 'chromatic transpose') + 12 * number(trans.findtext('octave-change', '0'), 'octave transpose')
                    require(transpose.denominator == 1 and -24 <= transpose <= 24, 'Unsupported instrument transposition')
                continue
            if tag in ('backup', 'forward'):
                raise RecognitionError('여러 성부 또는 음표 사이 빈 구간이 감지됐습니다. 단선율 사진을 사용하세요.')
            if tag in ('direction', 'sound'):
                sounds = [element] if tag == 'sound' else element.findall('sound')
                sound_tempos = [number(s.get('tempo'), 'tempo') for s in sounds if s.get('tempo')]
                written_tempos = []
                for met in element.findall('./direction-type/metronome'):
                    unit = met.findtext('beat-unit')
                    require(unit in TYPE_QUARTERS and met.findtext('per-minute'), 'Unsupported metronome marking')
                    dots = len(met.findall('beat-unit-dot'))
                    require(dots <= 3, 'Too many metronome dots')
                    written_tempos.append(number(met.findtext('per-minute'), 'metronome') * TYPE_QUARTERS[unit]
                                          * sum((Fraction(1, 2**d) for d in range(dots + 1)), Fraction()))
                if len(written_tempos) == len(sound_tempos) == 1 and abs(written_tempos[0] - sound_tempos[0]) <= Fraction(1, 2):
                    tempos.extend(written_tempos)
                    if written_tempos != sound_tempos:
                        warnings.append('Audiveris의 반올림된 sound tempo 대신 인식된 점음표 빠르기의 정확한 값을 사용했습니다.')
                else:
                    tempos.extend(sound_tempos or written_tempos)
                    if sound_tempos and written_tempos and set(sound_tempos) != set(written_tempos):
                        warnings.append('기보 빠르기와 소리용 빠르기가 달라 MusicXML sound tempo를 사용했습니다.')
                for shift in element.findall('./direction-type/octave-shift'):
                    metadata['octave_shifts'].append(dict(shift.attrib))
                continue
            if tag != 'note':
                continue
            require(element.find('chord') is None and element.find('unpitched') is None,
                    '화음·무정음은 단소 단선율로 자동 변환할 수 없습니다.')
            require(element.findtext('staff', '1') == '1', '복수 보표는 지원하지 않습니다.')
            current_voice = element.findtext('voice', '1')
            if voice is None:
                voice = current_voice
            require(voice == current_voice, '여러 성부가 감지됐습니다. 단선율 한 성부를 사용하세요.')
            if element.find('grace') is not None:
                pending_grace.append(_pitch_midi(element.find('pitch'), transpose))
                continue
            value = element.findtext('duration')
            if value is not None:
                require(divisions is not None, 'Missing divisions for note duration')
                duration = number(value, 'duration') / divisions
            else:
                kind = element.findtext('type')
                require(kind in TYPE_QUARTERS, 'Missing note duration and type')
                dots = len(element.findall('dot'))
                require(dots <= 3, 'Too many note dots')
                duration = TYPE_QUARTERS[kind] * sum((Fraction(1, 2**d) for d in range(dots + 1)), Fraction())
                modification = element.find('time-modification')
                if modification is not None:
                    actual = number(modification.findtext('actual-notes'), 'actual notes')
                    normal = number(modification.findtext('normal-notes'), 'normal notes')
                    require(actual > 0 and normal > 0, 'Invalid tuplet ratio')
                    duration *= normal / actual
                warnings.append('MusicXML duration이 없는 음표는 기보 음가·점·잇단 비율로 계산했습니다.')
            require(duration > 0 and (duration * PPQN).denominator == 1, 'Unsupported note duration')
            note = {'id': f'n{len(notes) + 1}', 'duration': str(duration)}
            if element.find('rest') is not None or element.find('cue') is not None:
                require(tie_pitch is None, 'Rest interrupts a tie')
                note['rest'] = True
                if element.find('cue') is not None:
                    warnings.append('큐 음표는 소리 내지 않고 해당 길이의 쉼표로 보존했습니다.')
            else:
                pitch = element.find('pitch')
                note['pitch'] = _pitch_midi(pitch, transpose)
                ties = {t.get('type') for t in element.findall('tie')}
                if not ties and element.findall('./notations/tied'):
                    ties = {t.get('type') for t in element.findall('./notations/tied')}
                    warnings.append('소리용 tie가 없어 기보용 tied를 이음줄로 사용했습니다.')
                require(ties <= {'start', 'stop'}, 'Unsupported tie')
                if tie_pitch is not None:
                    require('stop' in ties and tie_pitch == note['pitch'], 'Broken or mismatched tie')
                else:
                    require('stop' not in ties, 'Unmatched tie stop')
                if ties:
                    note['tie'] = 'continue' if ties == {'start', 'stop'} else next(iter(ties))
                tie_pitch = note['pitch'] if 'start' in ties else None
                slurs = element.findall('./notations/slur')
                starts = [s for s in slurs if s.get('type') == 'start']
                stops = [s for s in slurs if s.get('type') == 'stop']
                stop_ids = {s.get('number', '1') for s in stops}
                restarted = set()
                if starts:
                    if active_slurs or len(starts) > 1:
                        warnings.append('겹친 이음줄은 연결된 구간 하나의 레가토로 적용했습니다.')
                    if not active_slurs:
                        slur_serial += 1
                    for start in starts:
                        identifier = start.get('number', '1')
                        if identifier in active_slurs and identifier in stop_ids:
                            restarted.add(identifier)
                        else:
                            require(identifier not in active_slurs, 'Duplicate slur start')
                        active_slurs.add(identifier)
                if active_slurs:
                    note['slur'] = f'slur-{slur_serial}'
                for stop in stops:
                    identifier = stop.get('number', '1')
                    require(identifier in active_slurs, 'Unmatched slur stop')
                    if identifier not in restarted:
                        active_slurs.remove(identifier)
                ornaments = element.findall('./notations/ornaments/*')
                if len(ornaments) == 1 and ornaments[0].tag == 'inverted-mordent' and duration > Fraction(1, 8) and not ties:
                    neighbor = _upper_neighbor(pitch, note['pitch'], key_fifths, context, transpose)
                    require(0 <= neighbor <= 127, 'Ornament pitch outside MIDI')
                    note['ornament'] = {'kind':'upper-return', 'pitch':neighbor, 'duration':'1/16'}
                    warnings.append('짧은 위꾸밈음은 본음에서 1/16박씩 빌리는 미리듣기 가정을 적용했습니다.')
                elif ornaments:
                    warnings.append('해석하지 못하거나 너무 짧거나 붙임줄로 연결된 장식음은 생략했습니다: '+', '.join(e.tag for e in ornaments))
                unsupported = [n.tag for n in element.findall('./notations/*') if n.tag not in ('tied', 'slur', 'tuplet', 'ornaments')]
                if unsupported:
                    warnings.append('자동 재생에 반영하지 못한 연주 기호: ' + ', '.join(sorted(set(unsupported))))
            if pending_grace:
                if len(pending_grace) == 1 and not note.get('rest') and 'ornament' not in note and not note.get('tie') and duration > Fraction(1, 16):
                    note['ornament'] = {'kind':'grace-before', 'pitch':pending_grace[0], 'duration':'1/16'}
                    warnings.append('앞꾸밈음은 뒤 본음에서 1/16박을 빌리는 미리듣기 가정을 적용했습니다.')
                else:
                    metadata['omitted_grace_notes'] += len(pending_grace)
                    warnings.append('복수·겹침 또는 적용할 본음이 없는 앞꾸밈음은 생략했습니다.')
                pending_grace = []
            notes.append(note)
            require(len(notes) <= 1000, '인식 음표가 1000개를 넘습니다. 사진을 나눠 주세요.')
            total += duration
    require(notes, '오선보에서 재생 가능한 음표를 찾지 못했습니다.')
    if pending_grace:
        metadata['omitted_grace_notes'] += len(pending_grace)
        warnings.append('뒤 본음이 없는 앞꾸밈음은 생략했습니다.')
    if context.get('ornament_pitch_classes'):
        metadata['ornament_pitch_classes'] = list(context['ornament_pitch_classes'])
        warnings.append('위꾸밈음 음높이는 명시된 음계 음급 '+str(context['ornament_pitch_classes'])+'을 적용했습니다.')
    require(tie_pitch is None, 'Unclosed tie')
    if active_slurs:
        warnings.append('사진 끝에서 이음줄이 닫히지 않았습니다. 인식된 구간에만 적용했습니다.')
    if 'quarter_bpm' in context:
        bpm = number(context['quarter_bpm'], 'quarter_bpm')
        if any(t != bpm for t in tempos):
            warnings.append('인식된 빠르기 대신 사용자가 지정한 빠르기로 재생합니다.')
    elif tempos:
        require(len(set(tempos)) == 1, '빠르기 변화는 지원하지 않습니다. 구간별로 사진을 나눠 주세요.')
        bpm = tempos[0]
    else:
        bpm = Fraction(60)
        warnings.append('빠르기 표기를 인식하지 못해 임시로 4분음표=60을 사용합니다.')
    require(10 <= bpm <= 400 and total * 60 / bpm <= 600, 'Invalid tempo or score longer than 10 minutes')
    title = root.findtext('work/work-title') or root.findtext('movement-title') or '자동 인식 오선보'
    return {'title': title[:200], 'notes': notes, 'quarter_bpm': float(bpm),
            'warnings': list(dict.fromkeys(warnings)), 'metadata': metadata}


def _execute(command, cwd, env, log_path, timeout):
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    with log_path.open('wb') as log:
        try:
            process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                       stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
        except OSError as exc:
            raise RecognitionError('Audiveris 실행 파일을 시작할 수 없습니다.') from exc
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired as exc:
            # The Windows launcher can own a JVM child: terminate only this run's
            # process tree so a timed-out request does not leave a CPU job behind.
            if os.name == 'nt':
                try:
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   creationflags=flags, timeout=10, check=False)
                except (OSError, subprocess.TimeoutExpired):
                    pass
            if process.poll() is None:
                process.kill()
            process.wait(timeout=10)
            raise RecognitionError('Audiveris 인식 시간이 초과됐습니다. 더 짧은 악보 사진을 사용하세요.') from exc
        require(code == 0, f'Audiveris 인식에 실패했습니다 (exit {code}). 인식 로그를 확인하세요.')


def _exported_xml(output):
    outputs = sorted(output.rglob('*.mxl')) or sorted(output.rglob('*.musicxml'))
    require(len(outputs) == 1, 'Audiveris가 단일 악보 MusicXML을 내보내지 못했습니다.')
    return read_musicxml(outputs[0])


def _keep_attempt(output, log, destination, *, xml=None, source=None):
    destination.mkdir(parents=True, exist_ok=True)
    if xml is not None:
        (destination / 'score.musicxml').write_bytes(xml)
    if source is not None:
        shutil.copyfile(source, destination / 'source.png')
    if log.exists():
        with log.open('rb') as stream:
            stream.seek(max(0, log.stat().st_size - 1024 * 1024))
            (destination / 'audiveris.log').write_bytes(stream.read(1024 * 1024))
    for suffix in ('*.mxl', '*.omr'):
        for index, item in enumerate(sorted(output.rglob(suffix))[:10]):
            if item.stat().st_size <= MAX_ARCHIVE:
                shutil.copyfile(item, destination / f'output-{index}{item.suffix}')


def recognize_staff(image_path, context, *, executable, trace_path=None, timeout=120,
                    model=None, base_url='http://127.0.0.1:11434'):
    """Read a photo with installed local Audiveris; never install or call a cloud API.

    A configured trace also keeps raw MXL/OMR and a bounded log alongside it.
    These files are unverified model outputs, useful for reproducing omissions.
    """
    from PIL import Image, ImageOps, UnidentifiedImageError

    score_context = _context('staff', context)
    require(type(timeout) in (int, float) and 0 < timeout <= 300, 'Audiveris timeout must be 0..300 seconds')
    exe = Path(executable).resolve()
    require(exe.is_file(), '로컬 Audiveris 실행 파일을 설정하세요.')
    path = Path(image_path)
    require(path.is_file() and 0 < path.stat().st_size <= 64 * 1024 * 1024, 'Invalid score image size')
    raw = path.read_bytes()
    image_hash = hashlib.sha256(raw).hexdigest()
    started = time.monotonic()
    trace = {'provider': 'audiveris', 'image_sha256': image_hash, 'context': deepcopy(context),
             'executable': str(exe), 'timeout_seconds': timeout, 'recognition_quality': 'unverified'}
    preprocessing = {'applied': False}
    artifacts = None
    if trace_path is not None:
        trace_path = Path(trace_path)
        trace_path.parent.mkdir(parents=True, exist_ok=True)
        artifacts = Path(tempfile.mkdtemp(prefix=trace_path.stem + '-artifacts-', dir=trace_path.parent))
        trace['artifacts'] = str(artifacts)
    try:
        with tempfile.TemporaryDirectory(prefix='score-omr-') as temporary:
            stage = Path(temporary)
            require(str(stage).isascii(), 'Audiveris 임시 경로는 영문 경로여야 합니다. TEMP를 영문 경로로 지정하세요.')
            source = stage / 'source.png'
            try:
                with Image.open(path) as image:
                    require(image.format in ('PNG', 'JPEG', 'WEBP') and image.width * image.height <= 16_000_000
                            and not getattr(image, 'is_animated', False), 'Unsupported score image')
                    trace['input_image'] = {'format': image.format, 'encoded_size': list(image.size),
                                            'exif_orientation': image.getexif().get(274, 1)}
                    normalized = ImageOps.exif_transpose(image).convert('RGB')
                    trace['normalized_source_size'] = list(normalized.size)
                    normalized.save(source)
            except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as exc:
                raise RecognitionError('Invalid score image') from exc
            output = stage / 'out'
            log = stage / 'audiveris.log'
            command = [str(exe), '-batch', '-transcribe', '-export', '-output', str(output), '--', str(source)]
            trace['command'] = command
            trace['omr_input_sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
            env = os.environ.copy()
            if (exe.parent / 'tessdata').is_dir():
                # Tesseract on Windows failed with an absolute Unicode path in
                # the real experiment. Relative to the configured runtime works.
                env['TESSDATA_PREFIX'] = 'tessdata'
            try:
                remaining = timeout - (time.monotonic() - started)
                require(remaining > 0, 'Audiveris 준비 시간이 인식 상한을 넘었습니다.')
                _execute(command, exe.parent, env, log, remaining)
                xml = _exported_xml(output)
                original_root = xml_root(xml)
                overlaps = {tag: len(original_root.findall('.//' + tag)) for tag in ('chord', 'backup', 'forward')}
                # Do not flatten overlaps or remove guessed notes. A shaded,
                # single-staff image gets one pixel-only retry, before VLM crops.
                omrs = list(output.rglob('*.omr'))
                if any(overlaps.values()) and len(omrs) == 1:
                    from .staff_camera import prepare_retry
                    retry_source = stage / 'preprocessed.png'
                    preprocessing = prepare_retry(source, omrs[0], retry_source)
                    preprocessing['trigger'] = overlaps
                    trace['image_preprocessing'] = preprocessing
                    if preprocessing['applied']:
                        trace['original_attempt'] = {'command': command,
                            'omr_input_sha256': trace['omr_input_sha256'],
                            'musicxml_sha256': hashlib.sha256(xml).hexdigest(),
                            'structural_overlap': overlaps}
                        if artifacts:
                            _keep_attempt(output, log, artifacts / 'original-attempt', xml=xml, source=source)
                            shutil.copyfile(retry_source, artifacts / 'preprocessed-source.png')
                        source = retry_source
                        output = stage / 'retry-out'
                        log = stage / 'retry-audiveris.log'
                        command = [str(exe), '-batch', '-transcribe', '-export', '-output', str(output), '--', str(source)]
                        trace['command'] = command
                        trace['omr_input_sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
                        remaining = timeout - (time.monotonic() - started)
                        require(remaining > 0, 'Audiveris 보정 재시도의 전체 인식 시간이 초과됐습니다.')
                        _execute(command, exe.parent, env, log, remaining)
                        xml = _exported_xml(output)
                trace['musicxml_sha256'] = hashlib.sha256(xml).hexdigest()
                raw_xml = xml
                if artifacts:
                    (artifacts / 'score.musicxml').write_bytes(raw_xml)
                enrichment = {'corrections': [], 'warnings': []}
                if model:
                    from .staff_metadata import enrich_musicxml
                    omrs = list(output.rglob('*.omr'))
                    if len(omrs) == 1:
                        evidence_path = (artifacts or stage) / 'metadata-trace.json'
                        corrected, enrichment = enrich_musicxml(xml_root(xml), source, omrs[0],
                            model=model, base_url=base_url, trace_path=evidence_path)
                        from .staff_rhythm import enrich_rhythm
                        excluded = [n['head_id'] for n in enrichment.get('flat_filter', {}).get('removed_notes', [])]
                        corrected, rhythm = enrich_rhythm(corrected, source, omrs[0], context,
                            model=model, base_url=base_url, trace_path=(artifacts or stage) / 'rhythm-trace.json',
                            excluded_head_ids=excluded)
                        enrichment['rhythm'] = rhythm
                        enrichment['corrections'].extend(rhythm['corrections'])
                        enrichment['warnings'].extend(rhythm['warnings'])
                        xml = ET.tostring(corrected, encoding='utf-8', xml_declaration=True)
                    else:
                        enrichment['warnings'].append('OMR 구조 파일이 없어 조표·옥타브 추가 확인을 생략했습니다.')
                    trace['metadata_enrichment'] = enrichment
                    trace['corrected_musicxml_sha256'] = hashlib.sha256(xml).hexdigest()
                parsed = parse_musicxml(xml, context)
                parsed['warnings'].extend(enrichment.get('warnings', []))
                if preprocessing['applied']:
                    parsed['warnings'].append('원본 OMR의 성부 겹침 오류 후 밝기·선간격을 보정하여 한 번 재인식했습니다. 보정으로 누락·오독이 생길 수 있어 원본 대조가 필요합니다.')
                trace['metadata'] = parsed['metadata']
                trace['status'] = 'recognized'
                if artifacts:
                    (artifacts / 'score.musicxml').write_bytes(raw_xml)
                    if xml != raw_xml:
                        (artifacts / 'corrected.musicxml').write_bytes(xml)
            finally:
                if artifacts:
                    _keep_attempt(output, log, artifacts)
        warnings = [WARNING, '음표의 사진 좌표는 아직 제공하지 않습니다.', *parsed['warnings']]
        return {'score': {'schema': 'gugak-reviewed-score/v1', 'title': parsed['title'],
                         'source': {'edition': f'Uploaded staff image {image_hash[:12]}',
                                    'review_status': 'machine-transcribed-unverified', 'image_sha256': image_hash,
                                    'scope': 'Audiveris image extraction; unverified preview',
                                    'model': 'Audiveris', 'software': parsed['metadata']['software'],
                                    'image_preprocessing': preprocessing,
                                    'corrections': enrichment.get('corrections', [])},
                         'context': score_context, 'quarter_bpm': parsed['quarter_bpm'],
                         'notes': parsed['notes'], 'assumptions': warnings},
                'regions': [], 'warnings': warnings,
                'provider': {'name': 'audiveris', 'notation': 'staff', 'recognition_quality': 'unverified',
                             'elapsed_seconds': round(time.monotonic() - started, 3),
                             'metadata': parsed['metadata'], 'musicxml_sha256': trace['musicxml_sha256'],
                             'image_preprocessing': preprocessing,
                             'metadata_enrichment': enrichment}}
    except (RecognitionError, OSError, ValueError) as exc:
        trace.update(status='failed', error=str(exc))
        if isinstance(exc, RecognitionError):
            raise
        raise RecognitionError('Audiveris 결과를 읽거나 저장하지 못했습니다.') from exc
    finally:
        if trace_path is not None:
            trace['elapsed_seconds'] = round(time.monotonic() - started, 3)
            trace_path.write_text(json.dumps(trace, ensure_ascii=False, indent=2), encoding='utf-8')
