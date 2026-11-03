"""MusicXML → JG-like 변환기(omr_service.staff_musicxml)를 작은 고정 MusicXML로 검사한다.

계약: docs/specs/score-recognition.md '오선보 → 정간보 기본값'. JG-like 위치 번호는
data/src/scores/standard_score_text.py·jeongganbo-generator import-ngc-omr.mjs와 같다
(0 통째, 3행 1~9, 2행 10·11 통째와 12·13·14·15 좌우).
"""

import pytest

from omr_service.staff_musicxml import convert

STEP = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note(pitch, dur, *extra, voice=1):
    """pitch 'C4'·'Bb4'·'F#5' 또는 None(쉼표). dur은 divisions(4분음표 = 4) 단위."""
    if pitch is None:
        body = "<rest/>"
    else:
        step, rest = pitch[0], pitch[1:]
        alter = {"#": 1, "b": -1}.get(rest[0], 0) if rest[0] in "#b" else 0
        octave = rest.lstrip("#b")
        body = f"<pitch><step>{step}</step>{f'<alter>{alter}</alter>' if alter else ''}<octave>{octave}</octave></pitch>"
    return f"<note>{''.join(e for e in extra if e.startswith('<grace') or e.startswith('<chord'))}{body}" \
           f"<duration>{dur}</duration><voice>{voice}</voice>{''.join(e for e in extra if not e.startswith(('<grace', '<chord')))}</note>"


def measure(*items, time=None, barline=""):
    attrs = "<attributes><divisions>4</divisions>" + (
        f"<time><beats>{time[0]}</beats><beat-type>{time[1]}</beat-type></time>" if time else "") + "</attributes>"
    return f"<measure>{attrs}{barline}{''.join(items)}</measure>"


def xml(*measures, parts=1):
    body = "".join(f'<part id="P{p}">{"".join(measures)}</part>' for p in range(1, parts + 1))
    return f'<?xml version="1.0"?><score-partwise><part-list></part-list>{body}</score-partwise>'.encode()


def test_quarter_notes_in_four_four_become_one_gak_of_four_jeonggan():
    doc = xml(measure(note("C4", 4), note("D4", 4), note("E4", 4), note("F4", 4), time=(4, 4)))
    out = convert(doc, instrument="ANY", hwang_pitch=60)  # C4 = 황
    assert out.lines == [":0 황 | :0 태 | :0 고 | :0 중"]
    assert out.jangdan is None and out.beats_per_gak == 4 and out.issues == []


@pytest.mark.parametrize("time, jangdan, per_gak", [((12, 8), "굿거리", 4), ((9, 8), "세마치", 3)])
def test_compound_meter_maps_to_jangdan_with_dotted_quarter_jeonggan(time, jangdan, per_gak):
    beats = time[0] // 3
    doc = xml(measure(*[note("C4", 6)] * beats, time=time))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert (out.jangdan, out.beats_per_gak) == (jangdan, per_gak)
    assert out.lines == [" | ".join([":0 황"] * beats)]


def test_eighths_in_compound_meter_use_three_rows():
    doc = xml(measure(note("C4", 2), note("D4", 2), note("E4", 2), note("C4", 6), note("C4", 6), note("C4", 6),
                      time=(12, 8)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines[0].startswith(":2 황 :5 태 :8 고 | :0 황")


def test_halves_and_sixteenths_in_simple_meter_use_two_rows():
    doc = xml(measure(note("C4", 2), note("D4", 2), note("E4", 1), note("F4", 1), note("G4", 2), note("C4", 8),
                      time=(4, 4)))
    line = convert(doc, instrument="ANY", hwang_pitch=60).lines[0]
    assert line.startswith(":10 황 :11 태 | :12 고 :13 중 :11 임 | :0 황 | ")


def test_triplet_in_simple_meter_uses_three_rows():
    trip = "<time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>"
    # divisions 12로 셋잇단 8분음표 하나 = 4
    doc = xml(measure(note("C4", 4, trip), note("D4", 4, trip), note("E4", 4, trip), note("C4", 36),
                      time=(4, 4)).replace("<divisions>4</divisions>", "<divisions>12</divisions>"))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines[0].startswith(":2 황 :5 태 :8 고 | :0 황")


def test_long_and_tied_notes_continue_without_new_onset():
    doc = xml(measure(note("C4", 8), note("D4", 4, '<tie type="start"/>'), note("D4", 4, '<tie type="stop"/>'),
                      time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 |  | :0 태 | "]


def test_rests_start_a_rest_and_repeat_rests_merge():
    doc = xml(measure(note("C4", 4), note(None, 4), note(None, 4), note("D4", 4), time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 | :0 쉼표 |  | :0 태"]


def test_pickup_is_padded_at_start_and_last_gak_with_rest_at_end():
    doc = xml(measure(note("C4", 4), time=(4, 4)), measure(note("D4", 16)), measure(note("E4", 8)))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 쉼표 |  |  | :0 황", ":0 태 |  |  | ", ":0 고 |  | :0 쉼표 | "]


def test_repeat_with_first_and_second_endings_is_expanded():
    fwd = '<barline location="left"><repeat direction="forward"/></barline>'
    end1 = '<barline location="left"><ending number="1" type="start"/></barline>'
    back = '<barline location="right"><ending number="1" type="stop"/><repeat direction="backward"/></barline>'
    end2 = '<barline location="left"><ending number="2" type="start"/></barline>'
    whole = lambda p: note(p, 16)
    doc = xml(measure(whole("C4"), time=(4, 4)), measure(whole("D4"), barline=fwd),
              measure(whole("E4"), barline=end1 + back), measure(whole("F4"), barline=end2))
    lines = convert(doc, instrument="ANY", hwang_pitch=60).lines
    assert [l.split(" |")[0] for l in lines] == [":0 황", ":0 태", ":0 고", ":0 태", ":0 중"]


def test_grace_notes_are_dropped_with_issue():
    doc = xml(measure(note("E4", 0, "<grace/>"), note("C4", 16), time=(4, 4)))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 황 |  |  | "]
    assert [(i["kind"], i["detail"]) for i in out.issues] == [("ORNAMENT_DROPPED", {"measure": 1})]


def test_highest_voice_and_top_of_chord_are_kept():
    upper = note("E4", 8, voice=1) + note("G4", 4, voice=1) + note("C5", 4, "<chord/>", voice=1)
    lower = "<backup><duration>16</duration></backup>" + note("C4", 16, voice=2)
    doc = xml(measure(upper + note("F4", 4, voice=1), lower, time=(4, 4)))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 고 |  | :0 청황 | :0 중"]
    assert [i["kind"] for i in out.issues] == ["VOICE_GUESSED"]


def test_higher_voice_wins_even_when_numbered_second():
    lower = note("C4", 16, voice=1)
    upper = "<backup><duration>16</duration></backup>" + note("G4", 16, voice=2)
    out = convert(xml(measure(lower, upper, time=(4, 4))), instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 임 |  |  | "]


def test_note_over_gak_line_is_written_again_at_next_gak():
    # 우물사이는 각을 넘어 잇지 않아(넘으면 쉼이 된다) 다음 각 처음에 같은 음을 다시 적는다
    doc = xml(measure(note("C4", 8), note("D4", 8, '<tie type="start"/>'), time=(4, 4)),
              measure(note("D4", 8, '<tie type="stop"/>'), note("E4", 8)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 |  | :0 태 | ", ":0 태 |  | :0 고 | "]


def test_key_is_moved_to_fit_the_instrument_scale_when_hwang_is_not_given():
    # A단조 5음(A C D E G)으로 쓴 선율. 단소 5율(중·임·무·황·태)에 맞추려면 반음 몇 칸을 옮겨야 한다
    pitches = ["A4", "C5", "D5", "E5", "G5", "E5", "D5", "C5"]
    doc = xml(measure(*[note(p, 2) for p in pitches], time=(4, 4)))
    out = convert(doc, instrument="DANSO", hwang_pitch=None)
    (issue,) = out.issues
    assert issue["kind"] == "TRANSPOSED"
    yuls = {tok for l in out.lines for tok in l.replace("|", " ").split() if not tok.startswith(":")}
    assert yuls <= {"중", "임", "무", "황", "태", "청중", "청임", "청무", "청황", "청태", "중청중"}
    assert convert(doc, instrument="DANSO", hwang_pitch=63).issues == []


def test_missing_time_signature_picks_meter_with_fewest_notes_over_gak_lines():
    # Audiveris가 박자표를 못 읽은 경우(오나라·어이얼어자리). 점4분음표 셋 길이(9/8 한 마디)의 음이 이어지면 세마치만 각 경계를 넘지 않는다
    out = convert(xml(measure(*[note("C4", 18)] * 4)), instrument="ANY", hwang_pitch=60)
    assert (out.jangdan, out.beats_per_gak) == ("세마치", 3)
    assert out.issues == [{"kind": "JANGDAN_GUESSED", "eventId": None, "cellId": None, "page": None, "bbox": None,
                           "detail": {"timeSignature": None, "compound": True, "beatsPerGak": 3}}]


def test_missing_time_signature_ties_prefer_gutgeori():
    out = convert(xml(measure(*[note("C4", 4)] * 12)), instrument="ANY", hwang_pitch=60)
    assert (out.jangdan, out.beats_per_gak) == ("굿거리", 4)


def test_note_outside_instrument_range_is_flagged():
    # 단소 음역은 중(반음 5)~중청중(29). 황(0)은 음역 아래다
    out = convert(xml(measure(note("C4", 16), time=(4, 4))), instrument="DANSO", hwang_pitch=60)
    assert out.lines == [":0 황 |  |  | "]
    assert [(i["kind"], i["detail"]) for i in out.issues] == [("OUT_OF_RANGE", {"measure": 1, "semitone": 0})]


def test_note_beyond_named_octaves_is_moved_into_them_and_flagged():
    # 율명은 하배(−2)~중청(+2) 옥타브만 있다. C1은 하배보다 한 옥타브 아래라 하배로 올린다
    out = convert(xml(measure(note("C1", 16), time=(4, 4))), instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 하배황 |  |  | "]
    assert [i["kind"] for i in out.issues] == ["OUT_OF_RANGE"]


def test_mxl_zip_is_read():
    import io
    import zipfile

    raw = xml(measure(note("C4", 16), time=(4, 4)))
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("META-INF/container.xml", "<container/>")
        z.writestr("score.xml", raw)
    assert convert(buf.getvalue(), instrument="ANY", hwang_pitch=60).lines == [":0 황 |  |  | "]
