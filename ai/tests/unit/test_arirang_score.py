"""공통 악보 데이터의 아리랑 샘플을 채점 입력으로 바꿔 끝까지 채점한다.

악보를 음표로 푸는 일은 백엔드 몫이라 여기서는 테스트 안에서만 PRA-03 음표 번호 규칙대로 흉내 낸다.
"""

import json
from pathlib import Path

from ai_service.pipelines.scoring import HWANG_HZ, Note, frame_rms, pyin_f0, score
from evaluation.synthetic import BPM, SR, synth

ROOT = Path(__file__).resolve().parents[3]


def request_notes():
    """NOTE만 0부터 센다. 반음은 pitches.semitone, 박은 틱 ÷ 박당 틱."""
    doc = json.loads((ROOT / "data/tests/fixtures/scores/ngc-standard-2022-p173.score.json").read_text(encoding="utf-8"))
    rows = json.loads((ROOT / "data/reference/pitches.json").read_text(encoding="utf-8"))["rows"]
    semitone = {(r["yul"], r["octave"]): r["semitone"] for r in rows}
    tpb = doc["music"]["ticksPerBeat"]
    events = [e for e in doc["events"] if e["kind"] == "NOTE"]
    return [
        Note(semitone[e["yul"], e["octave"]], e["durationTick"] / tpb, bool(e["sigimsae"]), i, e["startTick"] / tpb)
        for i, e in enumerate(events)
    ]


NOTES = request_notes()


def play(notes, **errors):
    """같은 음이 이어지는 곳이 있어 음마다 40ms씩 끊어 분다. errors의 키는 notes 안의 위치다."""
    audio = synth(notes, gap=0.04, **errors)
    result = score(pyin_f0(audio, SR), notes, BPM, HWANG_HZ, rms=frame_rms(audio, SR))
    assert [n.idx for n in result.notes] == [n.idx for n in notes]
    return result, {n.idx: n.verdict for n in result.notes if n.verdict != "CORRECT"}


def test_sample_becomes_61_notes_with_rests_kept():
    assert len(NOTES) == 61
    assert (NOTES[18].start_beat, NOTES[32].start_beat) == (12, 24)  # 쉼 뒤 음은 앞 음 끝보다 늦게 시작한다
    assert NOTES[17].start_beat + NOTES[17].beats < NOTES[18].start_beat


def test_whole_song_played_correctly_has_no_errors():
    result, wrong = play(NOTES)
    assert result.status == "DONE"
    assert wrong == {}


def test_errors_are_reported_on_their_notes():
    # 8번 음을 반음 높게, 쉼 뒤 18번 음을 0.1초 늦게, 36번 음은 불지 않는다.
    _, wrong = play(NOTES, shift={8: 1}, delay={18: 0.1}, drop=(36,))
    assert wrong == {8: "PITCH", 18: "TIMING", 36: "MISSED"}


def test_practice_section_keeps_song_note_numbers():
    section = NOTES[18:32]  # 곡 중간 18~31번 음. 녹음은 18번 음에서 시작한다
    _, wrong = play(section, delay={2: 0.1})
    assert wrong == {20: "TIMING"}
