"""가사 줄 가리기(S15P31E102-992)를 작은 Audiveris 출력으로 검사한다.

좌표는 줄 간격 10px, 오선 y=100~140, 쪽 높이 400px의 가짜 .omr이다. 실제 원본 PDF 재현은 docs/plans/S15P31E102-992.md에 기록한다.
"""

import io
import subprocess
import zipfile
from pathlib import Path

from PIL import Image

from omr_service import staff_engine
from omr_service.staff_engine import lyric_regions, rhythm_mismatches


def staff(top, left=50, right=450):
    lines = "".join(f'<line><point x="{left}" y="{top + 10 * k}"/><point x="{right}" y="{top + 10 * k}"/></line>'
                    for k in range(5))
    return f'<staff id="{top}" left="{left}" right="{right}"><lines>{lines}</lines></staff>'


def inter(tag, x, y, w, h, shape=""):
    return f'<{tag} shape="{shape}"><bounds x="{x}" y="{y}" w="{w}" h="{h}"/></{tag}>'


def omr(*systems, height=400):
    """systems: (오선 윗줄 목록, inter 문자열 목록)."""
    body = "".join(f'<system>{"".join(staff(t) for t in tops)}<sig><inters>{"".join(items)}</inters></sig></system>'
                   for tops, items in systems)
    doc = (f'<sheet><picture width="500" height="{height}"/><scale><interline min="10" main="10" max="10"/></scale>'
           f'<page>{body}</page></sheet>')
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as z:
        z.writestr("sheet#1/sheet#1.xml", doc)
    return stream.getvalue()


LYRIC_GLYPHS = [inter("articulation", 100, 170, 20, 15, "ACCENT"),  # 'ファ'
                inter("tuplet", 200, 168, 10, 18, "TUPLET_SIX"),  # 'ミ'
                inter("dynamics", 350, 171, 15, 14, "DYNAMICS_P")]
LEDGER_NOTE = [inter("ledger", 290, 158, 30, 2), inter("ledger", 290, 169, 30, 2),  # 오선 아래 두 번째 덧줄 음
               inter("head", 295, 165, 18, 10, "NOTEHEAD_BLACK"), inter("stem", 312, 120, 2, 50)]


def test_lyric_row_is_masked_across_the_staff_but_ledger_note_is_kept():
    # 띠는 글줄±0.5간격, 덧줄·기둥 가로 구간(±0.5간격)은 남긴다.
    assert lyric_regions(omr(([100], LYRIC_GLYPHS + LEDGER_NOTE))) == [(50, 163, 285, 192), (325, 163, 450, 192)]


def test_sparse_marks_and_articulation_of_a_low_note_are_not_lyrics():
    staccato = inter("articulation", 300, 178, 6, 6, "STACCATO")  # 덧줄 음 바로 아래: 음에 딸린 기호
    assert lyric_regions(omr(([100], LYRIC_GLYPHS[:2] + LEDGER_NOTE + [staccato]))) == []


def test_marks_close_to_the_staff_or_between_staves_are_not_lyrics():
    near_staff = [inter("articulation", x, 150, 10, 8) for x in (100, 200, 300)]  # 첫 덧줄 높이
    assert lyric_regions(omr(([100], near_staff))) == []
    # 두 오선이 한 단(피아노 등)이면 오선 사이는 보지 않는다.
    between = [inter("articulation", x, 170, 10, 10) for x in (100, 200, 300)]
    assert lyric_regions(omr(([100, 220], between))) == []


def test_lyrics_close_to_the_next_system_are_not_masked():
    near_next = [inter("articulation", x, 235, 10, 10) for x in (100, 200, 300)]  # 다음 단 윗줄(250)에서 1.5간격 안
    assert lyric_regions(omr(([100], near_next), ([250], []))) == []
    assert lyric_regions(omr(([100], LYRIC_GLYPHS), ([250], [])))  # 단 사이 가운데 가사는 가린다


def measure(beats_quarters, time=False):
    attrs = '<attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>'
    return (f'<measure>{attrs if time else ""}<note><pitch><step>C</step><octave>4</octave></pitch>'
            f'<duration>{beats_quarters}</duration></note></measure>')


def score(*lengths):
    return ('<score-partwise><part>' + "".join(measure(n, k == 0) for k, n in enumerate(lengths))
            + '</part></score-partwise>').encode()


def test_rhythm_mismatches_ignore_pickup_and_last_bar():
    assert rhythm_mismatches([score(3, 4, 4, 2)]) == ((4,), 0)
    assert rhythm_mismatches([score(3, 3, 5, 4, 2)]) == ((5,), 2)


def write_result(folder: Path, data: bytes, archive: bytes | None = None):
    folder.mkdir(parents=True)
    (folder / "page.musicxml").write_bytes(data)
    if archive is not None:
        (folder / "page.omr").write_bytes(archive)


def run_retry(tmp_path, monkeypatch, retried: bytes | None, *, timeout=False):
    out = tmp_path / "page-0"
    write_result(out, score(4, 3, 4, 4), omr(([100], LYRIC_GLYPHS)))  # 가운데 짧은 마디 1개
    page = tmp_path / "page.png"
    Image.new("RGB", (500, 400), "black").save(page)
    seen = {}

    def fake_run(cmd, deadline):
        if timeout:
            raise TimeoutError()
        seen["input"] = Path(cmd[-1])
        if retried is not None:
            write_result(Path(cmd[cmd.index("--") - 1]), retried)
        return subprocess.CompletedProcess(cmd, 0, b"", b"")

    monkeypatch.setattr(staff_engine, "_run", fake_run)
    first = subprocess.CompletedProcess([], 0, b"", b"")
    result = staff_engine._retry_without_lyrics(["audiveris", "-output"], out, first, page, tmp_path / "lyrics-0",
                                                float("inf"), page=0)
    return out, first, page, result, seen


def test_lyric_retry_is_used_when_bars_get_closer_to_the_meter(tmp_path, monkeypatch):
    out, _, _, (new_out, _, new_input, issues), seen = run_retry(tmp_path, monkeypatch, score(4, 4, 4, 4))
    assert new_out == tmp_path / "lyrics-0" and new_input == seen["input"]
    with Image.open(new_input) as masked:
        assert masked.getpixel((150, 175)) == (255, 255, 255)  # 가사 띠만 흰색
        assert masked.getpixel((150, 120)) == (0, 0, 0)  # 오선 영역은 그대로
    assert [i["detail"]["reason"] for i in issues] == ["LYRICS_REMOVED"]
    assert issues[0]["page"] == 0 and issues[0]["bbox"] == [50, 163, 400, 29]


def test_lyric_retry_is_dropped_when_worse_changed_or_timed_out(tmp_path, monkeypatch):
    for case, retried, timeout in (("worse", score(4, 3, 3, 4), False), ("bars", score(4, 4, 4, 4, 4), False),
                                   ("none", None, False), ("timeout", None, True)):
        folder = tmp_path / case
        out, first, page, (new_out, new_run, new_input, issues), _ = run_retry(folder, monkeypatch, retried, timeout=timeout)
        assert (new_out, new_run, new_input, issues) == (out, first, page, []), case
