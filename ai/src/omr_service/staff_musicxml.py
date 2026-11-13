"""오선보 MusicXML → JG-like 텍스트(docs/specs/score-recognition.md '오선보 → 정간보 기본값').

결과 줄은 standard_score_text.py와 같은 형식이라 jeongganbo-generator import-ngc-omr.mjs로 .jgb.json이 된다.
한 줄이 한 각, 정간은 '|', 정간 안은 ':위치 율명'(0 통째, 3행 1~9, 2행 10·11 통째와 12~15 좌우)이다.

- 정간 하나 = 1박. 겹박자(6/8·9/8·12/8)는 점4분음표, 마디가 4분음표로 나뉘면 4분음표, 그 밖(5/8·7/8)은 8분음표다.
  명시한 박자표가 있으면 마디별 실제 길이와 경계를 보존한다. 변박은 공통 음가의 정간과 각별 너비로 나타낸다.
  첫 못갖춘마디 앞은 쉼표로 채운다. 끝의 불완전마디는 실제 길이대로 두고, 각을 넘는 음은 명시적 이음으로 잇는다.
- 첫 파트에서 곡 길이 절반 이상을 덮는 성부 중 높은 성부를 쓰되, 본음 시작이 두 배 이상 많은 하성부가 있으면 고른다.
  성부 안의 빈 시간은 쉼표다. 꾸밈음·가사는 버린다. 반복·volta는 펼친다.
- 원음 MIDI를 보존한다. 악기에 맞추는 이조·옥타브 이동은 앱에서 지원 목록과 전후 결과를 확인한 뒤 적용한다.

입력은 우리 Audiveris 출력이다. 사용자가 올린 MusicXML(S15P31E102-349)에 쓰려면 defusedxml·압축 크기 상한이 더 필요하다.
"""

import io
import re
import zipfile
import xml.etree.ElementTree as ET
from bisect import bisect_left, bisect_right
from collections import defaultdict
from dataclasses import dataclass, field, replace
from fractions import Fraction
from itertools import accumulate
from math import gcd, lcm
from statistics import median

from omr_service.jeongganbo_text import score_data  # data/src/scores의 율명·옥타브 표를 함께 쓴다

YUL = re.search(r"\[(.+)\]", score_data.YUL).group(1)  # 황대태협고중유임이남무응
OCTAVE = {number: prefix for prefix, number in score_data.OCTAVE.items()}  # -3 하하배 ~ 2 중청
STEP = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
# 악기 음역(황 = 0인 반음 번호). 단소: data/reference pitches 중~중청중, 소금: 임~중청남(sogeum-service-scores.md)
DEFAULT_HWANG = 63  # E♭4, 공통 악보의 황
# 정간 안 자리. 행 수별 [행][왼·통째·오른]. standard_score_text.py POS와 같다(그 파일은 pymupdf를 불러 들이지 않는다)
POS = {3: [(1, 2, 3), (4, 5, 6), (7, 8, 9)], 2: [(12, 10, 13), (14, 11, 15)]}
# 박자표가 없을 때 고르는 순서(동률이면 앞). 민요에 흔한 굿거리(12/8)·4/4·세마치(9/8) 순이다
METER_ORDER = [(True, 4), (False, 4), (True, 3), (False, 3), (True, 2), (False, 2)]
MIN_COVER = Fraction(1, 2)  # 성부 후보: 곡 길이의 절반 이상을 덮는다(Audiveris가 잘못 낸 한두 음 성부를 거른다)


@dataclass
class Conversion:
    lines: list[str]
    jangdan: str | None  # 기존 자동 연결 대상만 제안한다. 없으면 명시 요청 또는 무반주다.
    beats_per_gak: int
    issues: list[dict] = field(default_factory=list)
    shift: int = 0  # 자동 조 옮김 반음(hwang_pitch가 있으면 0)
    beat: Fraction = Fraction(1)  # 정간 하나(1박)의 4분음표 수. 점4분 3/2, 4분 1, 8분 1/2
    meter_guessed: bool = False  # 박자표를 못 읽어 리듬으로 박 구성을 골랐다
    source_pitches: list[dict] = field(default_factory=list)
    quarter_bpm: Fraction | None = None
    tempos: list[dict] = field(default_factory=list)  # existing performancePlan, BPM in jeonggan/minute
    gak_beats: list[int] = field(default_factory=list)  # 각별 정간 수. 모든 정간은 beat만큼의 같은 음가다.
    source_meters: list[str | None] = field(default_factory=list)  # 원본 마디 순서, 상속된 박자표. 추정값을 섞지 않는다.

    @property
    def bpm_unit(self):
        return 'DOTTED_QUARTER' if self.jangdan in ('세마치', '굿거리') or self.beat == Fraction(3, 2) else 'QUARTER'


@dataclass
class _Note:
    start: Fraction
    length: Fraction
    midi: int | None  # None = 쉼표
    measure: int
    flags: list = field(default_factory=list)  # 이 음의 시작 칸에 남길 issue (kind, detail): 버린 꾸밈음·성부 안 빈 시간
    continuation: bool = False  # 각 경계의 명시적 지속. 새 onset과 원음 ID를 만들지 않는다.


def convert(data: bytes, *, instrument: str, hwang_pitch: int | None) -> Conversion:
    root = ET.fromstring(_xml_bytes(data))
    part = root.find("part")
    if part is None:
        raise ValueError("MusicXML에 파트가 없음")
    issues = []
    notes, beats_per_gak, compound, beat, meter_guessed, raw_tempos, pad, widths, source_meter, source_meters = _read_part(part, issues)
    if not any(n.midi is not None for n in notes):
        raise ValueError("음표가 없음")
    shift, hwang = 0, DEFAULT_HWANG if hwang_pitch is None else hwang_pitch
    jangdan = {(True, Fraction(3, 2), 3): "세마치", (True, Fraction(3, 2), 4): "굿거리"}.get(source_meter)
    if source_meter == (False, Fraction(1), 4):
        jangdan = "4박 기본 반주"
    lines = _layout(notes, beats_per_gak, hwang, instrument, issues, widths=widths)
    widths = widths or [beats_per_gak] * len(lines)
    originals = [item['detail'] for item in issues if item['kind'] == '_SOURCE_PITCH']
    issues = [item for item in issues if item['kind'] != '_SOURCE_PITCH']
    quarter_bpm, tempos = _tempo_plan(raw_tempos, beat, widths, pad, issues)
    return Conversion(lines, jangdan, beats_per_gak, issues, shift, beat, meter_guessed, originals,
                      quarter_bpm, tempos, widths, source_meters)


def _xml_bytes(data: bytes) -> bytes:
    if data[:2] != b"PK":
        return data
    with zipfile.ZipFile(io.BytesIO(data)) as z:  # .mxl: META-INF/container.xml의 rootfile이 악보다
        names = z.namelist()
        if "META-INF/container.xml" in names:
            rootfile = ET.fromstring(z.read("META-INF/container.xml")).find(".//rootfile")
            if rootfile is not None and rootfile.get("full-path") in names:
                return z.read(rootfile.get("full-path"))
        return z.read(next(n for n in names if n.endswith((".xml", ".musicxml")) and not n.startswith("META-INF")))


def _issue(kind, detail, cell_id=None):
    """cell_id는 결과 악보의 칸 c{각}-j{정간}(score_data). 악보 전체에 대한 것은 None이다."""
    return {"kind": kind, "eventId": None, "cellId": cell_id, "page": None, "bbox": None, "detail": detail}


def _meter(time):
    """<time> → (겹박자 여부, 1박의 4분음표 수, 각의 박 수). 읽을 수 없으면 None."""
    try:
        beats = sum(int(b) for b in time.findtext("beats").split("+"))
        beat_type = int(time.findtext("beat-type"))
    except (AttributeError, TypeError, ValueError):  # senza-misura 등
        return None
    if beat_type == 8 and beats % 3 == 0:
        return True, Fraction(3, 2), beats // 3
    quarters = Fraction(beats * 4, beat_type)
    if quarters.denominator == 1:
        return False, Fraction(1), int(quarters)
    return False, Fraction(1, 2), int(quarters * 2)  # 5/8·7/8: 8분음표 박


def _read_part(part, issues):
    """반복을 펼친 시간 순서의 선율 음표(박 단위)와 각의 박 수·겹박자 여부를 돌려준다."""
    measures = part.findall("measure")
    divisions, transpose, meter, measure_meters = [], [], None, []
    current_meter = None
    source_meters, current_text = [], None
    div, shift = Fraction(1), 0
    for i, m in enumerate(measures):  # 마디 시작 시점의 divisions·이조 값(반복으로 돌아가도 같은 값)
        divisions.append(div)
        transpose.append(shift)
        for attrs in m.findall("attributes"):  # 마디 가운데의 attributes도 다음 마디로 이어진다
            div, shift = _attributes(attrs, div, shift)
            time = attrs.find("time")
            if time is not None:
                read = _meter(time)
                current_text = f"{time.findtext('beats')}/{time.findtext('beat-type')}" if read else None
                current_meter = read
                if meter is None:
                    meter = read or False
                elif read and read != meter:
                    issues.append(_issue("LOW_CONFIDENCE", {"measure": i + 1, "reason": "TIME_SIGNATURE_CHANGED"}))
        measure_meters.append(current_meter)
        source_meters.append(current_text)

    written_lengths = [_measure_length(m, d) for m, d in zip(measures, divisions)]
    underfull = {i for i in range(1, len(measures) - 1) if measure_meters[i]
                 and 0 < written_lengths[i] < measure_meters[i][1] * measure_meters[i][2]}
    if len(underfull) < 2:
        underfull.clear()  # 못갖춘마디·마지막 불완전마디 한 번으로 박자 오독을 단정하지 않는다.

    voices = defaultdict(list)  # 성부 → [시작, 길이, [(MIDI, 이어받음)], 마디, flags] 묶음(화음은 한 묶음)
    graces = defaultdict(list)  # 성부 → 다음 음에 붙일 버린 꾸밈음
    cursor, last, tempos, lengths = Fraction(0), None, [], []
    for index in _play_order(measures, issues):
        m, number, d, shift = measures[index], index + 1, divisions[index], transpose[index]
        local_meter = measure_meters[index]
        overfull = local_meter and _measure_length(m, d) > local_meter[1] * local_meter[2]
        rhythm_flags = [("LOW_CONFIDENCE", {"measure": number, "reason": "OVERFULL_MEASURE"})] if overfull else []
        if index in underfull:
            rhythm_flags.append(('LOW_CONFIDENCE', {'measure': number, 'reason': 'REPEATED_UNDERFULL_MEASURE',
                'expectedQuarters': str(local_meter[1] * local_meter[2]),
                'actualQuarters': str(written_lengths[index])}))
        t, longest = Fraction(0), Fraction(0)
        for el in m:
            if el.tag == "attributes":
                d, shift = _attributes(el, d, shift)
            elif el.tag in ("backup", "forward"):
                t += (-1 if el.tag == "backup" else 1) * Fraction(int(el.findtext("duration"))) / d
            elif el.tag in ("direction", "sound"):
                cue = _tempo_direction(el)
                if cue is not None:
                    offset = Fraction(el.findtext('offset') or 0) / d
                    tempos.append({**cue, 'position': cursor + t + offset, 'measure': number})
            elif el.tag == "note":
                voice = el.findtext("voice") or "1"
                if el.find("grace") is not None:  # 꾸밈음은 뒤따르는 음의 칸에 알린다
                    graces[voice].append(("ORNAMENT_DROPPED", {"measure": number}))
                    continue
                pitch = _midi(el, shift)
                tie_stop = el.find("tie[@type='stop']") is not None
                if el.find("chord") is not None:  # 앞 음과 함께 치는 음
                    if last is not None:
                        last[2].append((pitch, tie_stop))
                    continue
                dur = Fraction(int(el.findtext("duration") or 0)) / d
                if dur <= 0:
                    last = None  # 길이 없는 음에 붙은 화음은 버린다
                    continue
                last = [cursor + t, dur, [(pitch, tie_stop)], number, graces.pop(voice, []) + rhythm_flags]
                voices[voice].append(last)
                t += dur
            longest = max(longest, t)
        lengths.append(longest)
        cursor += longest
    issues += [_issue(kind, detail) for flags in graces.values() for kind, detail in flags]  # 뒤따르는 음이 없는 꾸밈음

    if not voices:
        raise ValueError("음표가 없음")
    voice = _pick_voice(voices, cursor)
    if len(voices) > 1 or any(len(g[2]) > 1 for g in voices[voice]):
        issues.append(_issue("VOICE_GUESSED", {"voices": len(voices)}))
    melody = _melody(voices[voice])
    if melody[-1].start + melody[-1].length < cursor:
        end = melody[-1].start + melody[-1].length
        melody.append(_Note(end, cursor - end, None, melody[-1].measure,
                            [("LOW_CONFIDENCE", {"measure": melody[-1].measure, "reason": "GAP_IN_VOICE"})]))
    source_meter = None
    widths = []
    pad_quarters = Fraction(0)
    if meter:
        compound, beat, beats_per_gak = meter
        # 실제 마디 길이와 박 단위의 공통 음가를 사용해, 변박·불완전마디도
        # 정수 개 셀로 나타낸다. 각의 길이를 맞추려고 끝 쉼표를 만들지 않는다.
        first_length = lengths[0]
        expected = meter[1] * meter[2]
        if 0 < first_length < expected:
            pad_quarters = expected - first_length
        lengths[0] += pad_quarters
        values = [m[1] for m in measure_meters if m] + [n for n in lengths if n]
        denominator = lcm(*(v.denominator for v in values))
        numerator = 0
        for value in values:
            numerator = gcd(numerator, int(value * denominator))
        beat = Fraction(numerator, denominator)
        widths = [int(length / beat) for length in lengths if length]
        beats_per_gak = widths[0]
        if all(m == meter for m in measure_meters) and not underfull and not any(
                length > m[1] * m[2] for length, m in zip(written_lengths, measure_meters) if m):
            source_meter = meter
    else:  # 박자표를 못 읽었으면 각 경계를 넘는 음이 가장 적은 박 구성을 고른다
        compound, beats_per_gak = _guess_meter(melody)
        beat = Fraction(3, 2) if compound else Fraction(1)
        source_meter = (compound, beat, beats_per_gak)
    notes = [_Note(n.start / beat, n.length / beat, n.midi, n.measure, n.flags) for n in melody]
    pad = pad_quarters / beat
    if pad:
        notes = [_Note(Fraction(0), pad, None, 1)] + [_Note(n.start + pad, n.length, n.midi, n.measure, n.flags)
                                                      for n in notes]
    return notes, beats_per_gak, compound, beat, not meter, tempos, pad, widths, source_meter, source_meters


def _tempo_direction(el):
    """MusicXML metronome units differ from sound's quarter-notes/minute."""
    bpm = None
    units = {'whole': 4, 'half': 2, 'quarter': 1, 'eighth': Fraction(1, 2), '16th': Fraction(1, 4)}
    text = ' '.join(el.itertext())
    accel = bool(re.search(r'\baccel(?:erando)?\b', text, re.I))
    for mark in el.findall('direction-type/metronome'):
        value = (mark.findtext('per-minute') or '').strip()
        # Audiveris sometimes includes the following instruction in per-minute.
        match = re.fullmatch(r'(\d+(?:\.\d+)?)\s*(?:(?:molto\s+)?accel(?:erando)?\.?\s*[_-]*)?', value, re.I)
        unit = units.get(mark.findtext('beat-unit'))
        if match and unit and len(mark.findall('beat-unit')) == 1:
            dots = len(mark.findall('beat-unit-dot'))
            bpm = Fraction(match[1]) * unit * (2 - Fraction(1, 2 ** dots))
    sound = el if el.tag == 'sound' else el.find('sound')
    if bpm is None and sound is not None:
        try:
            bpm = Fraction(sound.get('tempo', ''))
        except (ValueError, ZeroDivisionError):
            pass
    if bpm is not None and bpm <= 0:
        bpm = None
    return {'bpm': bpm, 'accel': accel} if bpm is not None or accel else None


def _tempo_plan(raw, beat, widths, pad, issues):
    """Map through the same repeat/pickup clock; never snap a cue to another cell."""
    positioned = {}
    unbound = []
    initial = None
    raw = [dict(cue) for cue in raw]
    for i, cue in enumerate(raw):
        if cue['bpm'] is None and cue['accel']:
            previous = next((r for r in raw if r['bpm'] is not None and r['measure'] == cue['measure']
                             and r['position'] == cue['position']), None) or next(
                (r for r in reversed(raw[:i]) if r['bpm'] is not None
                 and r['measure'] == cue['measure'] and r['position'] <= cue['position']), None)
            if previous is not None:
                previous['accel'] = True
                cue['accel'] = False
    for cue in raw:
        if cue['bpm'] is None and not cue['accel']:
            continue
        written = cue['position'] / beat
        position = written + pad if written else Fraction(0)
        if position.denominator != 1 or not 0 <= position < sum(widths):
            issues.append(_issue('LOW_CONFIDENCE', {'reason': 'TEMPO_OFF_GRID', 'measure': cue['measure']}))
            if cue['bpm'] is not None:
                unbound.append(position)
            continue
        if cue['bpm'] is None:
            issues.append(_issue('LOW_CONFIDENCE', {'reason': 'TEMPO_RAMP_UNRESOLVED', 'measure': cue['measure']}))
            continue
        if position in positioned and positioned[position]['bpm'] != cue['bpm']:
            issues.append(_issue('LOW_CONFIDENCE', {'reason': 'TEMPO_CONFLICT', 'measure': cue['measure']}))
            positioned[position]['conflict'] = True
        else:
            old = positioned.get(position, {})
            positioned[position] = {**cue, 'accel': cue['accel'] or old.get('accel', False),
                                    'conflict': old.get('conflict', False)}
    tempos = []
    ordered = sorted(positioned.items())
    for i, (position, cue) in enumerate(ordered):
        if cue['conflict']:
            continue
        gak, cell = _column_at(int(position), widths)
        entry = {'gak': gak + 1, 'cell': cell + 1, 'bpm': float(cue['bpm'] / beat)}
        tempos.append(entry)
        if position == 0:
            initial = cue['bpm']
        if cue['accel']:
            target = ordered[i + 1] if i + 1 < len(ordered) else None
            if (target and not target[1]['conflict'] and target[1]['bpm'] > cue['bpm']
                    and not any(position < p <= target[0] for p in unbound)):
                end_gak, end_cell = _column_at(int(target[0]), widths)
                entry.update(start_bpm=entry['bpm'], bpm=float(target[1]['bpm'] / beat),
                             end_gak=end_gak + 1, end_cell=end_cell + 1)
            else:
                issues.append(_issue('LOW_CONFIDENCE', {'reason': 'TEMPO_RAMP_UNRESOLVED', 'measure': cue['measure']}))
    return initial, tempos


def _attributes(attrs, divisions, transpose):
    """<attributes> 하나를 읽은 뒤의 divisions·이조 반음."""
    if attrs.find("divisions") is not None:
        divisions = Fraction(int(attrs.findtext("divisions")))
    t = attrs.find("transpose")
    if t is not None:
        transpose = int(t.findtext("chromatic") or 0) + 12 * int(t.findtext("octave-change") or 0)
    return divisions, transpose


def _pick_voice(voices, total):
    """지속음 반주보다 움직이는 선율을 우선한다. 붙임줄·화음은 시작 한 번으로 센다."""
    def cover(v):
        return sum(g[1] for g in voices[v] if any(p is not None for p, _ in g[2]))

    def mean(v):
        tops = [max(p for p, _ in g[2] if p is not None) for g in voices[v] if any(p is not None for p, _ in g[2])]
        return sum(tops) / len(tops) if tops else float("-inf")

    def attacks(v):
        return sum(n.midi is not None for n in _melody(voices[v]))

    candidates = [v for v in voices if total and cover(v) >= total * MIN_COVER]
    if not candidates:
        return max(voices, key=cover)
    upper = max(candidates, key=mean)
    active = max(candidates, key=lambda v: (attacks(v), mean(v)))
    # 음 수가 비슷한 두 선율은 높은 쪽을 유지한다. 지속음 반주처럼 활동량이
    # 절반 이하인 성부만 제외해 빠른 반주를 무조건 선율로 고르는 일을 막는다.
    if attacks(active) >= 2 * attacks(upper):
        return active
    # 몇 개의 2박 선율음 때문에 시작 수 비율이 2에 조금 못 미쳐도,
    # 대표 음 길이가 절반인 선율은 긴 음 위주의 반주보다 우선한다.
    def typical_length(v):
        return median(n.length for n in _melody(voices[v]) if n.midi is not None)
    if attacks(active) > attacks(upper) and typical_length(active) * 2 <= typical_length(upper):
        return active
    return upper


def _melody(groups):
    """한 성부의 묶음 → 시간 순서의 음표. 화음은 맨 위 음, 붙임줄은 합치고, 빈 시간은 쉼표로 채운다.

    마디마다 성부 번호가 바뀌는 인식 결과는 이어 잡은 음 자리가 빈 시간이 된다. 4분음표 하나보다 길게 비면 알린다."""
    out = []
    for start, length, pitches, measure, flags in sorted(groups, key=lambda g: g[0]):
        sounding = [(p, tie) for p, tie in pitches if p is not None]
        # 쉼표에 붙은 화음 표시는 잘못 읽은 것이라 쉼으로 둔다
        midi, tie_stop = max(sounding) if sounding and pitches[0][0] is not None else (None, False)
        if out and out[-1].start + out[-1].length > start:  # 겹치면 앞 음을 자른다
            out[-1].length = start - out[-1].start
            if out[-1].length <= 0:  # 같은 때 시작한 음이 앞 음을 대신한다. 붙은 알림은 넘겨받는다
                flags = out.pop().flags + list(flags)
        end = out[-1].start + out[-1].length if out else Fraction(0)
        if start > end:
            if out and out[-1].midi is None:
                out[-1].length = start - out[-1].start
            else:
                out.append(_Note(end, start - end, None, measure))
            if start - end > 1:  # 빈 시간이 시작하는 쉼표 칸에 알린다
                out[-1].flags.append(("LOW_CONFIDENCE", {"measure": measure, "reason": "GAP_IN_VOICE"}))
        prev = out[-1] if out else None
        if tie_stop and prev is not None and prev.midi == midi and prev.start + prev.length == start:
            prev.length += length
            prev.flags += flags
        elif midi is None and prev is not None and prev.midi is None:
            prev.length += length
            prev.flags += flags
        else:
            out.append(_Note(start, length, midi, measure, list(flags)))
    return out


def _guess_meter(notes):
    def crossings(option):
        compound, per_gak = option
        size = per_gak * (Fraction(3, 2) if compound else Fraction(1))
        return sum(1 for n in notes if n.start // size != (n.start + n.length) // size and (n.start + n.length) % size)
    return min(METER_ORDER, key=lambda o: (crossings(o), METER_ORDER.index(o)))


def _measure_length(measure, divisions):
    t, longest = Fraction(0), Fraction(0)
    for el in measure:
        if el.tag == "attributes":
            divisions, _ = _attributes(el, divisions, 0)
        elif el.tag in ("backup", "forward"):
            t += (-1 if el.tag == "backup" else 1) * Fraction(int(el.findtext("duration"))) / divisions
        elif el.tag == "note" and el.find("grace") is None and el.find("chord") is None:
            t += Fraction(int(el.findtext("duration") or 0)) / divisions
        longest = max(longest, t)
    return longest


def _play_order(measures, issues):
    """반복(forward·backward, times)과 volta(ending number)를 펼친 마디 순서. 안쪽에 겹친 반복은 다루지 않는다."""
    order, i, start, pass_no, ending, back_to, taken = [], 0, 0, 1, None, None, {}
    while i < len(measures):
        bars = measures[i].findall("barline")
        if any(b.find("repeat[@direction='forward']") is not None for b in bars) and i != back_to:
            start, pass_no = i, 1
        back_to = None
        for e in (e for b in bars for e in b.findall("ending")):
            if e.get("type") == "start":  # '1'·'1, 2'·'1.'·'1 2' 모두 숫자만 읽는다
                ending = {int(x) for x in re.findall(r"\d+", e.get("number") or "")}
                if not ending:
                    ending = {1}
                    issues.append(_issue("LOW_CONFIDENCE", {"measure": i + 1, "reason": "VOLTA_NUMBER"}))
        play = ending is None or pass_no in ending
        if play:
            order.append(i)
        stop = any(e.get("type") in ("stop", "discontinue") for b in bars for e in b.findall("ending"))
        back = next((b.find("repeat[@direction='backward']") for b in bars
                     if b.find("repeat[@direction='backward']") is not None), None)
        if play and back is not None:
            times = int(back.get("times") or 2)
            if taken.get(i, 0) < times - 1:
                taken[i] = taken.get(i, 0) + 1
                pass_no += 1
                if stop:
                    ending = None
                i = back_to = start
                continue
            taken[i], pass_no, start = 0, 1, i + 1  # 반복이 끝났다. 다음 반복은 여기서 다시 시작한다
        if stop:
            if ending is not None and pass_no > 1 and play:  # 마지막 volta가 끝나면 반복도 끝났다
                pass_no, start = 1, i + 1
            ending = None
        i += 1
    return order


def _midi(note, transpose=0):
    p = note.find("pitch")
    if p is None:
        return None
    written = 12 * (int(p.findtext("octave")) + 1) + STEP[p.findtext("step")] + round(float(p.findtext("alter") or 0))
    return written + transpose  # 이조 파트는 실음으로


def _column_at(position, widths, *, starts=None):
    if starts is None:
        starts = [0, *accumulate(widths)]
    column = bisect_right(starts, position) - 1
    return column, position - starts[column]


def _layout(notes, beats_per_gak, hwang, instrument, issues, *, widths=None):
    end = max(n.start + n.length for n in notes)
    widths = widths or [beats_per_gak] * int(-(-end // beats_per_gak))
    total = sum(widths)
    starts = [0, *accumulate(widths)]
    if end < total:  # 끝 각을 쉼표로 채운다
        notes = notes + [_Note(end, total - end, None, notes[-1].measure)]
    # 각 경계의 지속을 명시해 같은 eventId로 이어 붙인다.
    cells = defaultdict(list)
    for n in notes:
        cells[int(n.start)].append((n.start - int(n.start), n))
        for gak_start in starts[bisect_right(starts, n.start):bisect_left(starts, n.start + n.length)]:
            cells[gak_start].append((Fraction(0), replace(n, continuation=n.midi is not None)))
    for n in notes:  # 음에 붙은 issue는 그 음이 시작하는 칸에(정간 하나 = 1박)
        g, j = _column_at(int(n.start), widths, starts=starts)
        issues += [_issue(kind, detail, f"c{g}-j{j}") for kind, detail in n.flags]
    flagged = set()  # OUT_OF_RANGE는 음마다 한 번
    lines = []
    start = 0
    for g, width in enumerate(widths):
        row = [_cell(sorted(cells.get(start + j, []), key=lambda c: c[0]), hwang, issues, flagged,
                     f"c{g}-j{j}") for j in range(width)]
        lines.append(" | ".join(row))
        start += width
    return lines


def _cell(inside, hwang, issues, flagged, cell_id):
    """정간 하나(1박) 안의 음 시작들 → ':위치 율명 …'. 칸을 나눌 수 없는 자리는 가까운 칸으로 옮긴다."""
    if not inside:
        return ""
    offsets = [f for f, _ in inside]
    if offsets == [0]:
        rows, grid = 1, Fraction(1)
    else:
        rows, grid = min(((r, Fraction(1, r * s)) for r in (2, 3) for s in (1, 2)),
                         key=lambda rg: (sum(abs(f - _nearest(f, rg[1]) * rg[1]) for f in offsets), rg[1] < 1 / 4, -rg[1]))
    slots = {}
    for f, n in inside:
        k = min(_nearest(f, grid), round(1 / grid) - 1)
        if k * grid != f:
            issues.append(_issue("LOW_CONFIDENCE", {"measure": n.measure, "reason": "OFF_GRID_RHYTHM"}, cell_id))
        if k in slots and slots[k] is not n:
            issues.append(_issue("LOW_CONFIDENCE", {"measure": n.measure, "reason": "COLLIDED"}, cell_id))
        slots[k] = n  # 같은 칸이면 나중 음
    if rows == 1:
        return f":0 {_yul(slots[0], hwang, issues, flagged, cell_id)}"
    per_row = round(1 / grid) // rows  # 1(행 통째) 또는 2(좌우)
    toks = []
    for r in range(rows):
        left, whole, right = POS[rows][r]
        here = [slots.get(r * per_row + h) for h in range(per_row)]
        if per_row == 1 or here[1] is None:
            if here[0] is not None:
                toks.append(f":{whole} {_yul(here[0], hwang, issues, flagged, cell_id, r, 0)}")
        else:
            first = _yul(here[0], hwang, issues, flagged, cell_id, r, 0) if here[0] is not None else "-"
            toks += [f":{left} {first}", f":{right} {_yul(here[1], hwang, issues, flagged, cell_id, r, 1)}"]
    return " ".join(toks)


def _nearest(f, grid):
    return int(f / grid + Fraction(1, 2))  # 반올림(0.5는 위로). round()는 짝수 쪽으로 가서 칸이 겹친다


def _yul(note, hwang, issues, flagged, cell_id, row=0, col=0):
    if note.continuation:
        return "-"
    if note.midi is None:
        return "쉼표"
    s = note.midi - hwang
    issues.append(_issue('_SOURCE_PITCH', {'eventId': f'{cell_id}-r{row}-u{col}', 'cellId': cell_id,
                                         'midi': note.midi, 'semitone': s}, cell_id))
    octave = s // 12
    outside = octave not in OCTAVE
    if outside and id(note) not in flagged:
        flagged.add(id(note))
        issues.append(_issue("UNREGISTERED_PITCH", {"measure": note.measure, "semitone": s, "reason": "NOTATION_UNSUPPORTED"}, cell_id))
    # JGB is only a structural intermediate. staff_engine restores exact pitch below;
    # unrepresentable notes become unresolved draft slots, never this placeholder pitch.
    octave = max(-2, min(2, octave))
    return OCTAVE[octave] + YUL[s % 12]
