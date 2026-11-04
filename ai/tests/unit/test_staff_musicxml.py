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
    # 율명은 하하배(−3)~중청(+2) 옥타브만 있다. C0은 하하배보다 한 옥타브 아래라 하하배로 올린다
    out = convert(xml(measure(note("C0", 16), time=(4, 4))), instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 하하배황 |  |  | "]
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


# --- U4 리뷰 지적 재현 ---

FWD = '<barline location="left"><repeat direction="forward"/></barline>'
BACK = '<barline location="right"><repeat direction="backward"/></barline>'


def end_start(n):
    return f'<barline location="left"><ending number="{n}" type="start"/></barline>'


def end_stop(n, back=False):
    rep = '<repeat direction="backward"/>' if back else ""
    return f'<barline location="right"><ending number="{n}" type="stop"/>{rep}</barline>'


def first_yuls(doc):
    return [l.split(" |")[0].split()[-1] for l in convert(doc, instrument="ANY", hwang_pitch=60).lines]


def whole(p, barline=""):
    return measure(note(p, 16), time=(4, 4), barline=barline)


def test_first_ending_over_two_measures():
    # |: C |1 D | E :|2 F  →  C D E C F
    doc = xml(whole("C4", FWD), whole("D4", end_start(1)), whole("E4", end_stop(1, back=True)),
              whole("F4", end_start(2) + end_stop(2)))
    assert first_yuls(doc) == ["황", "태", "고", "황", "중"]


def test_two_repeat_blocks_with_endings():
    # |:A|1B:|2C |:D|1E:|2F  →  A B A C D E D F
    doc = xml(whole("C4", FWD), whole("D4", end_start(1) + end_stop(1, back=True)), whole("E4", end_start(2) + end_stop(2)),
              whole("F4", FWD), whole("G4", end_start(1) + end_stop(1, back=True)), whole("A4", end_start(2) + end_stop(2)))
    assert first_yuls(doc) == ["황", "태", "황", "고", "중", "임", "중", "남"]


def test_repeats_without_forward_restart_after_previous_repeat():
    # A B:| C D:|  →  A B A B C D C D
    doc = xml(whole("C4"), whole("D4", BACK), whole("E4"), whole("F4", BACK))
    assert first_yuls(doc) == ["황", "태", "황", "태", "고", "중", "고", "중"]


def test_repeat_times_attribute():
    doc = xml(whole("C4", FWD), whole("D4", '<barline location="right"><repeat direction="backward" times="3"/></barline>'))
    assert first_yuls(doc) == ["황", "태"] * 3


def test_tied_chord_is_not_struck_again():
    doc = xml(measure(note("C4", 8, '<tie type="start"/>'), note("E4", 8, "<chord/>", '<tie type="start"/>'),
                      note("C4", 8, '<tie type="stop"/>'), note("E4", 8, "<chord/>", '<tie type="stop"/>'), time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 고 |  |  | "]


def test_chord_on_tied_note_does_not_change_the_tied_note():
    # C를 이어 잡은 채 셋째 박에 G를 함께 치면, 맨 위 선율은 C 2박 뒤 G다(앞 C 전체가 G가 되지 않는다)
    doc = xml(measure(note("C4", 8, '<tie type="start"/>'), note("C4", 4, '<tie type="stop"/>'),
                      note("G4", 4, "<chord/>"), note("C4", 4), time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 |  | :0 임 | :0 황"]


def test_stray_high_note_in_another_voice_does_not_take_over():
    # Audiveris가 높은 음 하나를 voice 2로 낸 경우. 곡 길이 절반 이상을 덮는 성부만 고른다
    main = note("C4", 16, voice=1)
    stray = "<backup><duration>16</duration></backup>" + note("C6", 2, voice=2)
    doc = xml(measure(main, stray, time=(4, 4)), measure(note("D4", 16, voice=1)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 |  |  | ", ":0 태 |  |  | "]


def test_gap_inside_the_voice_is_a_rest():
    doc = xml(measure(note("C4", 4), "<forward><duration>8</duration></forward>", note("D4", 4), time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 | :0 쉼표 |  | :0 태"]


def test_many_notes_are_laid_out_in_linear_time():
    import time

    doc = xml(*[measure(*[note("C4", 1)] * 16, time=(4, 4))] * 250)  # 4000음
    began = time.perf_counter()
    convert(doc, instrument="ANY", hwang_pitch=60)
    assert time.perf_counter() - began < 1  # 실측 0.13초. 음 수의 제곱이면 수 초가 걸린다


def test_time_signature_change_is_flagged():
    doc = xml(measure(note("C4", 12), time=(3, 4)), measure(note("D4", 16), time=(4, 4)))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert {"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": None, "bbox": None,
            "detail": {"measure": 2, "reason": "TIME_SIGNATURE_CHANGED"}} in out.issues


@pytest.mark.parametrize("time, per_gak", [((7, 8), 7), ((5, 8), 5), ((2, 2), 4), ((6, 4), 6)])
def test_simple_meter_beats(time, per_gak):
    out = convert(xml(measure(note("C4", per_gak * (2 if time[1] == 8 else 4)), time=time)), instrument="ANY",
                  hwang_pitch=60)
    assert out.beats_per_gak == per_gak and len(out.lines) == 1


def test_additive_or_missing_beats_do_not_crash():
    doc = xml(measure(note("C4", 10), time=("3+2", 8)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).beats_per_gak == 5


def test_chord_after_zero_length_note_and_chord_on_rest_do_not_crash():
    doc = xml(measure(note("C4", 0), note("E4", 0, "<chord/>"), note(None, 4), note("G4", 4, "<chord/>"), note("C4", 12),
                      time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 쉼표 | :0 황 |  | "]


def test_colliding_onsets_keep_the_later_and_flag():
    # 32분음표 넷: 칸이 1/4박까지라 겹치면 나중 음이 남고 알린다(계약: 나중 음)
    doc = xml(measure(note("C4", 1), note("D4", 1), note("E4", 1), note("F4", 1), note("C4", 12), time=(4, 4))
              .replace("<divisions>4</divisions>", "<divisions>8</divisions>"))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert any(i["detail"].get("reason") == "COLLIDED" for i in out.issues)


def test_out_of_range_is_reported_once_per_note():
    doc = xml(measure(note("C4", 8), note("C4", 8, '<tie type="start"/>'), time=(4, 4)),
              measure(note("C4", 8, '<tie type="stop"/>'), note("C4", 8)))
    out = convert(doc, instrument="DANSO", hwang_pitch=60)
    assert sum(i["kind"] == "OUT_OF_RANGE" for i in out.issues) == 3


def test_three_octaves_below_uses_hahabae():
    out = convert(xml(measure(note("C1", 16), time=(4, 4))), instrument="ANY", hwang_pitch=60)
    assert out.lines == [":0 하하배황 |  |  | "] and out.issues == []


def test_transposing_part_is_read_at_sounding_pitch():
    doc = xml(measure(note("D4", 16), time=(4, 4)).replace(
        "</attributes>", "<transpose><diatonic>-1</diatonic><chromatic>-2</chromatic></transpose></attributes>"))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 |  |  | "]


def test_mxl_rootfile_with_musicxml_extension():
    import io
    import zipfile

    raw = xml(measure(note("C4", 16), time=(4, 4)))
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("META-INF/container.xml", '<container><rootfiles><rootfile full-path="s/score.musicxml"/>'
                                             '</rootfiles></container>')
        z.writestr("other.xml", "<not-a-score/>")
        z.writestr("s/score.musicxml", raw)
    assert convert(buf.getvalue(), instrument="ANY", hwang_pitch=60).lines == [":0 황 |  |  | "]


def test_repeat_after_volta_block_restarts_after_it():
    # |:A|1B:|2C| D E:|  →  A B A C D E D E (둘째 반복은 C 다음에서 시작한다)
    doc = xml(whole("C4", FWD), whole("D4", end_start(1) + end_stop(1, back=True)), whole("E4", end_start(2) + end_stop(2)),
              whole("F4"), whole("G4", BACK))
    assert first_yuls(doc) == ["황", "태", "황", "고", "중", "임", "중", "임"]


@pytest.mark.parametrize("numbers", [("1.", "2."), ("1 2", "3")])
def test_volta_numbers_with_dots_or_spaces(numbers):
    # |: C |1. D :|2. E  → C D C E ("1 2"·"3"이면 C D C D C E)
    first, second = numbers
    doc = xml(whole("C4", FWD), whole("D4", end_start(first) + end_stop(first, back=True)),
              whole("E4", end_start(second) + end_stop(second)))
    expected = ["황", "태", "황", "고"] if first == "1." else ["황", "태", "황", "태", "황", "고"]
    if first == "1 2":
        doc = doc.replace(b'<repeat direction="backward"/>', b'<repeat direction="backward" times="3"/>')
    assert first_yuls(doc) == expected


def test_volta_without_number_is_flagged_and_played_first():
    doc = xml(whole("C4", FWD), whole("D4", end_start("") + end_stop("", back=True)), whole("E4"))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert [l.split(" |")[0].split()[-1] for l in out.lines][:2] == ["황", "태"]
    assert any(i["detail"].get("reason") == "VOLTA_NUMBER" for i in out.issues)


def test_long_gap_inside_the_voice_is_flagged():
    # 마디마다 성부 번호가 바뀌면 이어 잡은 음 자리가 빈 시간이 된다. 쉼표로 두되 1박 넘게 비면 알린다
    doc = xml(measure(note("C4", 16, voice=1), time=(4, 4)), measure(note("C4", 16, voice=2)),
              measure(note("D4", 16, voice=1)))
    out = convert(doc, instrument="ANY", hwang_pitch=60)
    assert {"measure": 3, "reason": "GAP_IN_VOICE"} in [i["detail"] for i in out.issues]


def test_divisions_changed_inside_a_measure():
    # 첫 마디 가운데에서 divisions가 4→2로 바뀐다. 뒤 음 길이를 새 값으로 읽어야 각이 밀리지 않는다
    doc = xml(measure(note("C4", 8), "<attributes><divisions>2</divisions></attributes>", note("D4", 4), time=(4, 4)))
    assert convert(doc, instrument="ANY", hwang_pitch=60).lines == [":0 황 |  | :0 태 | "]


def test_score_without_sounding_notes_says_so():
    for doc in (xml(measure(note(None, 16), time=(4, 4))), xml(measure(note("C4", 0), time=(4, 4)))):
        with pytest.raises(ValueError, match="음표가 없음"):
            convert(doc, instrument="ANY", hwang_pitch=60)
