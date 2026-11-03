"""오선보 MusicXML → JG-like 텍스트(docs/specs/score-recognition.md '오선보 → 정간보 기본값').

결과 줄은 standard_score_text.py와 같은 형식이라 jeongganbo-generator import-ngc-omr.mjs로 .jgb.json이 된다.
한 줄이 한 각, 정간은 '|', 정간 안은 ':위치 율명'(0 통째, 3행 1~9, 2행 10·11 통째와 12~15 좌우)이다.

- 정간 하나 = 1박. 겹박자(6/8·9/8·12/8)는 점4분음표, 그 밖은 4분음표가 1박이다. 한 각 = 박자표의 한 마디 박 수.
  마디선이 빠진 인식 결과도 있어 마디가 아니라 시간 순서대로 각을 자른다. 첫 마디가 짧으면(못갖춘마디) 앞을,
  끝 각은 뒤를 쉼표로 채운다. 각을 넘는 음은 다음 각 처음에 다시 적는다(우물사이는 각을 넘어 잇지 않는다).
- 첫 파트에서 평균 음이 가장 높은 성부를 쓰고, 화음은 맨 위 음을 쓴다. 꾸밈음·가사는 버린다. 반복·volta는 펼친다.
- hwang_pitch(황으로 둘 MIDI 번호)가 없으면 악기 5율(황·태·중·임·무)과 음역에 가장 많이 들어가게 조를 옮긴다.
"""

import io
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from fractions import Fraction

YUL = "황대태협고중유임이남무응"
OCTAVE = {-2: "하배", -1: "배", 0: "", 1: "청", 2: "중청"}
STEP = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
# 악기 기본 5율(황 = 0인 반음 번호의 12 나머지). 단소 황·태·중·임·무, 소금 임·남·황·태·고(소금 서비스 곡도 고·남을 씀)
FIVE_YUL = {"DANSO": {0, 2, 5, 7, 10}, "SOGEUM": {0, 2, 4, 7, 9}}
# 악기 음역(황 = 0인 반음 번호). 단소: data/reference pitches 중~중청중, 소금: 임~중청남(sogeum-service-scores.md)
RANGES = {"DANSO": (5, 29), "SOGEUM": (7, 33)}
DEFAULT_HWANG = 63  # E♭4, 공통 악보의 황
# 정간 안 자리. 행 수별 [행][왼·통째·오른]
POS = {3: [(1, 2, 3), (4, 5, 6), (7, 8, 9)], 2: [(12, 10, 13), (14, 11, 15)]}


@dataclass
class Conversion:
    lines: list[str]
    jangdan: str | None  # 박자표로 정한 장단(9/8 세마치, 12/8 굿거리). 없으면 요청·기본값을 쓴다
    beats_per_gak: int
    issues: list[dict] = field(default_factory=list)
    shift: int = 0  # 자동 조 옮김 반음(hwang_pitch가 있으면 0)


@dataclass
class _Note:
    start: Fraction  # 박
    length: Fraction
    midi: int | None  # None = 쉼표
    measure: int


def convert(data: bytes, *, instrument: str, hwang_pitch: int | None) -> Conversion:
    root = ET.fromstring(_xml_bytes(data))
    part = root.find("part")
    if part is None:
        raise ValueError("MusicXML에 파트가 없음")
    issues = []
    notes, beats_per_gak, compound, beats_in_measure = _read_part(part, issues)
    if not any(n.midi is not None for n in notes):
        raise ValueError("음표가 없음")
    if hwang_pitch is None:
        shift, candidates = _best_shift([n.midi for n in notes if n.midi is not None], instrument)
        issues.append(_issue("TRANSPOSED", {"semitones": shift, "candidates": candidates}))
        hwang = DEFAULT_HWANG - shift
    else:
        shift, hwang = 0, hwang_pitch
    jangdan = {(True, 3): "세마치", (True, 4): "굿거리"}.get((compound, beats_per_gak))
    return Conversion(_layout(notes, beats_per_gak, hwang, instrument, issues), jangdan, beats_per_gak, issues, shift)


def _xml_bytes(data: bytes) -> bytes:
    if data[:2] != b"PK":
        return data
    with zipfile.ZipFile(io.BytesIO(data)) as z:  # .mxl: META-INF 밖의 첫 .xml이 악보다
        name = next(n for n in z.namelist() if n.endswith(".xml") and not n.startswith("META-INF"))
        return z.read(name)


def _issue(kind, detail):
    return {"kind": kind, "eventId": None, "cellId": None, "page": None, "bbox": None, "detail": detail}


def _read_part(part, issues):
    """반복을 펼친 시간 순서의 선율 음표(박 단위)와 각의 박 수·겹박자 여부를 돌려준다."""
    measures = part.findall("measure")
    divisions, beats_time = Fraction(1), None
    for m in measures:  # 첫 박자표가 각 길이를 정한다
        if beats_time is None and m.find("attributes/time") is not None:
            t = m.find("attributes/time")
            beats_time = (int(t.findtext("beats")), int(t.findtext("beat-type")))

    # 성부별 음표를 마디 순서(반복 펼침)로 모은다. 시간은 4분음표 단위
    voices: dict[str, list[_Note]] = {}
    chords_or_voices = False
    cursor_base = Fraction(0)
    for index in _play_order(measures):
        m = measures[index]
        number = index + 1
        div = m.find("attributes/divisions")
        if div is not None:
            divisions = Fraction(int(div.text))
        t, longest, last_start = Fraction(0), Fraction(0), {}
        for el in m:
            if el.tag == "backup":
                t -= Fraction(int(el.findtext("duration"))) / divisions
            elif el.tag == "forward":
                t += Fraction(int(el.findtext("duration"))) / divisions
            elif el.tag == "note":
                if el.find("grace") is not None:
                    issues.append(_issue("ORNAMENT_DROPPED", {"measure": number}))
                    continue
                dur = Fraction(int(el.findtext("duration") or 0)) / divisions
                voice = el.findtext("voice") or "1"
                midi = _midi(el)
                if el.find("chord") is not None:  # 화음은 맨 위 음만
                    chords_or_voices = True
                    prev = voices[voice][-1]
                    if midi is not None and (prev.midi is None or midi > prev.midi):
                        prev.midi = midi
                    continue
                start = cursor_base + t
                tied_on = el.find("tie[@type='stop']") is not None
                seq = voices.setdefault(voice, [])
                if tied_on and seq and seq[-1].midi == midi and seq[-1].start + seq[-1].length == start:
                    seq[-1].length += dur
                elif dur > 0:
                    seq.append(_Note(start, dur, midi, number))
                t += dur
            longest = max(longest, t)
        cursor_base += longest
    if len(voices) > 1:
        chords_or_voices = True
    voice = max(voices, key=lambda v: _mean_pitch(voices[v]))
    if chords_or_voices:
        issues.append(_issue("VOICE_GUESSED", {"voices": len(voices)}))
    if beats_time:
        compound = beats_time[1] == 8 and beats_time[0] % 3 == 0
        beats_per_gak = beats_time[0] // 3 if compound else max(1, round(Fraction(beats_time[0] * 4, beats_time[1])))
    else:  # 박자표를 못 읽었으면 각 경계를 넘는 음이 가장 적은 박 구성을 고른다
        compound, beats_per_gak = _guess_meter(voices[voice])
        issues.append(_issue("JANGDAN_GUESSED", {"timeSignature": None, "compound": compound,
                                                 "beatsPerGak": beats_per_gak}))
    beat = Fraction(3, 2) if compound else Fraction(1)  # 1박의 4분음표 수
    notes = [_Note(n.start / beat, n.length / beat, n.midi, n.measure) for n in voices[voice]]
    # 첫 마디가 짧으면(못갖춘마디) 앞을 쉼표로 채워 첫 각을 맞춘다
    first_len = _first_measure_beats(measures, divisions) / beat
    pad = (beats_per_gak - first_len % beats_per_gak) % beats_per_gak if beats_time else 0
    if pad:
        notes = [_Note(Fraction(0), pad, None, 1)] + [_Note(n.start + pad, n.length, n.midi, n.measure) for n in notes]
    return notes, beats_per_gak, compound, first_len


# 박자표가 없을 때 고르는 순서(동률이면 앞). 민요에 흔한 굿거리(12/8)·4/4·세마치(9/8) 순이다
METER_ORDER = [(True, 4), (False, 4), (True, 3), (False, 3), (True, 2), (False, 2)]


def _guess_meter(notes):
    def crossings(option):
        compound, per_gak = option
        size = per_gak * (Fraction(3, 2) if compound else Fraction(1))
        return sum(1 for n in notes if n.start // size != (n.start + n.length) // size and (n.start + n.length) % size)
    return min(METER_ORDER, key=lambda o: (crossings(o), METER_ORDER.index(o)))


def _first_measure_beats(measures, divisions):
    m = measures[0]
    div = m.find("attributes/divisions")
    d = Fraction(int(div.text)) if div is not None else divisions
    t, longest = Fraction(0), Fraction(0)
    for el in m:
        if el.tag == "backup":
            t -= Fraction(int(el.findtext("duration"))) / d
        elif el.tag == "forward":
            t += Fraction(int(el.findtext("duration"))) / d
        elif el.tag == "note" and el.find("grace") is None and el.find("chord") is None:
            t += Fraction(int(el.findtext("duration") or 0)) / d
        longest = max(longest, t)
    return longest


def _play_order(measures):
    """반복 기호(forward·backward)와 volta(ending number)를 펼친 마디 번호 순서. 한 겹 반복만 다룬다."""
    order, i, start, passes, pass_no = [], 0, 0, {}, 1
    while i < len(measures):
        m = measures[i]
        bars = m.findall("barline")
        if any(b.find("repeat[@direction='forward']") is not None for b in bars):
            start = i
        endings = {int(x) for b in bars for e in b.findall("ending[@type='start']")
                   for x in (e.get("number") or "").replace(" ", "").split(",") if x.isdigit()}
        if endings and pass_no not in endings:
            i += 1
            continue
        order.append(i)
        if any(b.find("repeat[@direction='backward']") is not None for b in bars):
            if passes.get(i, 0) < 1:
                passes[i] = 1
                pass_no, i = 2, start
                continue
            pass_no = 1
        i += 1
    return order


def _midi(note):
    p = note.find("pitch")
    if p is None:
        return None
    return 12 * (int(p.findtext("octave")) + 1) + STEP[p.findtext("step")] + round(float(p.findtext("alter") or 0))


def _mean_pitch(notes):
    pitches = [n.midi for n in notes if n.midi is not None]
    return sum(pitches) / len(pitches) if pitches else float("-inf")


def _best_shift(midis, instrument):
    """황 = E♭4로 본 반음 번호를 k만큼 옮겨 악기 5율·음역에 가장 많이 들어가는 k. 동률이면 작은 |k| 순 후보."""
    low, high = RANGES.get(instrument, (-24, 35))
    five = FIVE_YUL.get(instrument, FIVE_YUL["DANSO"])
    score = {k: sum(1 for m in midis if (m - DEFAULT_HWANG + k) % 12 in five
                    and low <= m - DEFAULT_HWANG + k <= high) for k in range(-24, 25)}
    best = max(score.values())
    candidates = sorted((k for k, v in score.items() if v == best), key=lambda k: (abs(k), k))
    return candidates[0], candidates[:2]


def _layout(notes, beats_per_gak, hwang, instrument, issues):
    end = max(n.start + n.length for n in notes)
    gak_count = -(-end // beats_per_gak)  # 올림
    total = gak_count * beats_per_gak
    if end < total:  # 끝 각을 쉼표로 채운다
        notes = notes + [_Note(end, total - end, None, notes[-1].measure)]
    # 각을 넘는 음은 다음 각 처음에 다시 적는다
    onsets = []
    for n in notes:
        onsets.append((n.start, n))
        gak_start = (n.start // beats_per_gak + 1) * beats_per_gak
        while gak_start < n.start + n.length:
            onsets.append((Fraction(gak_start), n))
            gak_start += beats_per_gak
    onsets.sort(key=lambda o: o[0])
    # 쉼표 뒤의 쉼표는 이어진다
    kept, prev = [], None
    for at, n in onsets:
        if n.midi is None and prev is not None and prev.midi is None and at % beats_per_gak != 0:
            continue
        kept.append((at, n))
        prev = n

    lines = []
    low, high = RANGES.get(instrument, (None, None))
    for g in range(int(gak_count)):
        cells = []
        for j in range(beats_per_gak):
            a = Fraction(g * beats_per_gak + j)
            inside = [(at - a, n) for at, n in kept if a <= at < a + 1]
            cells.append(_cell(inside, hwang, low, high, issues))
        lines.append(" | ".join(cells))
    return lines


def _cell(inside, hwang, low, high, issues):
    """정간 하나(1박) 안의 음 시작들 → ':위치 율명 …'. 칸을 나눌 수 없는 자리는 가까운 칸으로 옮긴다."""
    if not inside:
        return ""
    offsets = [f for f, _ in inside]
    if offsets == [0]:
        rows, grid = 1, Fraction(1)
    else:
        rows, grid = min(((r, Fraction(1, r * s)) for r in (2, 3) for s in (1, 2)),
                         key=lambda rg: (sum(abs(f - round(f / rg[1]) * rg[1]) for f in offsets), rg[1] < 1 / 4, -rg[1]))
    slots = {}
    for f, n in inside:
        k = round(f / grid)
        if k * grid != f:
            issues.append(_issue("LOW_CONFIDENCE", {"measure": n.measure, "reason": "OFF_GRID_RHYTHM"}))
        slots[min(k, round(1 / grid) - 1)] = n  # 같은 칸이면 나중 음
    if rows == 1:
        return f":0 {_yul(slots[0], hwang, low, high, issues)}"
    per_row = round(1 / grid) // rows  # 1(행 통째) 또는 2(좌우)
    toks = []
    for r in range(rows):
        left, whole, right = POS[rows][r]
        here = [slots.get(r * per_row + h) for h in range(per_row)]
        if per_row == 1 or here[1] is None:
            if here[0] is not None:
                toks.append(f":{whole} {_yul(here[0], hwang, low, high, issues)}")
        else:
            first = _yul(here[0], hwang, low, high, issues) if here[0] is not None else "-"
            toks += [f":{left} {first}", f":{right} {_yul(here[1], hwang, low, high, issues)}"]
    return " ".join(toks)


def _yul(note, hwang, low, high, issues):
    if note.midi is None:
        return "쉼표"
    s = note.midi - hwang
    if low is not None and not low <= s <= high:
        issues.append(_issue("OUT_OF_RANGE", {"measure": note.measure, "semitone": s}))
    octave = s // 12
    if octave not in OCTAVE:  # 율명이 있는 옥타브(하배~중청)로 옮긴다
        if low is None:
            issues.append(_issue("OUT_OF_RANGE", {"measure": note.measure, "semitone": s}))
        octave = max(-2, min(2, octave))
    return OCTAVE[octave] + YUL[s % 12]
