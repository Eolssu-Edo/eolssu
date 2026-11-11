"""글자 층 정간보 읽기(standard_score_text.py)를 합성 PDF로 검사한다. pymupdf가 필요하다.

실제 표준악보집 8곡(단소 PDF171~174, 소금 PDF192~196)과 검수 정답의 대조는
docs/knowledge/data/danso-scores-umulsai.md에 있다. 여기서는 판형 규칙만 작은 예로 고정한다.
"""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import pymupdf

SCRIPT = Path(__file__).resolve().parents[1] / "src/scores/standard_score_text.py"
sys.path.insert(0, str(SCRIPT.parent))
import standard_score_text as sst  # noqa: E402

COL_W, JG_H = 36, 45  # 열 너비·정간 높이(pt). 실제 표준악보집과 비슷하다
TWO_TIERS = [[["林", "南", "-"], ["潢", "汰", "-"]], [["仲", "林", "-"], ["南", "潢", "-"]]]


def glyph(c, x, y, i, size=10.5):
    return dict(c=c, x=x, y=y, size=size, i=i)


def score_pdf(path, tiers, gap=13.0, clip_cells=False, no_lines=False, no_text=False, labels=None, width=600,
              side_col=None, right0=500, prose=None):
    """세로쓰기 정간보 한 쪽. tiers[단][열] = 정간마다 율명 한 글자(빈 문자열은 빈 정간). 열은 오른쪽부터.

    clip_cells면 세로선을 쪽 높이 전체로 그리고 정간 칸 잘라내기 영역으로 칸 안만 보이게 한다(소금 표준악보집 방식).
    labels는 맨 위 단의 열마다 머리 표시(예: '단소1'), width가 넓으면 펼침면이고 side_col은 오른쪽 쪽에 둔
    율명 표 한 열(장식음 설명표처럼)이다. right0은 맨 오른쪽 열의 오른쪽 x, prose는 큰 테두리 상자 안의 설명문이다.
    """
    doc = pymupdf.open()
    page = doc.new_page(width=width, height=800)
    ops, top = [], 40.0
    if labels:
        for ci, text in enumerate(labels):
            page.insert_text((right0 - ci * (COL_W + 16) - COL_W + 4, 34), text, fontname="korea", fontsize=8)
    cols = [(t, None) for t in tiers] + ([([side_col], width - 60)] if side_col else [])
    for tier, fixed_right in cols:
        tier_bottom = top + max(len(c) for c in tier) * JG_H
        for ci, col in enumerate(tier):
            n = len(col)  # 곡 끝 열은 다른 열보다 짧을 수 있다
            bottom = top + n * JG_H
            right = fixed_right or right0 - ci * (COL_W + 16)
            left = right - COL_W
            for k, c in enumerate(col):
                if c and not no_text:
                    page.insert_text((left + 12, top + k * JG_H + 26), c, fontname="korea", fontsize=10.5)
            for k in range(n + 1):  # 정간 가로선(점선 조각)
                y = top + k * JG_H
                ops += [f"{x:.1f} {800 - y:.1f} m {x + 3:.1f} {800 - y:.1f} l S" for x in range(int(left), int(right), 6)]
            for x in (left, right):
                if clip_cells:
                    for k in range(n):
                        y0 = top + k * JG_H
                        ops.append(f"q {left:.1f} {800 - y0 - JG_H:.1f} {COL_W:.1f} {JG_H:.1f} re W n "
                                   f"{x:.1f} 790 m {x:.1f} 10 l S Q")
                else:
                    ops.append(f"{x:.1f} {800 - top:.1f} m {x:.1f} {800 - bottom:.1f} l S")
        top = tier_bottom + gap if fixed_right is None else top
    if prose:  # 설명문 상자(교과서 설명 쪽처럼 테두리 안에 율명 한자가 섞인 글)
        page.insert_text((70, 300), prose, fontname="korea", fontsize=10.5)
        ops += [f"60 {800 - 200} m 60 {800 - 500} l S", f"540 {800 - 200} m 540 {800 - 500} l S"]
    page.draw_line((0, 0), (0, 0), width=0)  # 글자가 없어도 내용 스트림이 있게 한다
    if no_lines:
        ops = []
    xref = page.get_contents()[0]
    doc.update_stream(xref, doc.xref_stream(xref) + ("\n0.5 w\n" + "\n".join(ops) + "\n").encode())
    doc.save(path)


def read(tiers, **kw):
    with tempfile.TemporaryDirectory() as d:
        pdf = Path(d) / "s.pdf"
        score_pdf(pdf, tiers, **kw)
        with pymupdf.open(pdf) as doc:
            gaks, _, report = sst.read_page(doc[0])
    return gaks, report


class CellTextTests(unittest.TestCase):
    def test_later_drawn_glyph_hides_the_one_under_it(self):
        # 밀양아리랑 단소: 청무 위에 무를 그려 무만 보인다
        text, _ = sst.cell_text([glyph("潕", 190.1, 285.6, 0), glyph("無", 190.1, 285.6, 1)], 263, 308)
        self.assertEqual(text, ":0 무")

    def test_one_row_split_left_right_becomes_two_equal_rows(self):
        text, split = sst.cell_text([glyph("-", 180, 285, 0), glyph("林", 195, 285, 1)], 263, 308)
        self.assertEqual((text, split), (":10 - :11 임", True))

    def test_single_glyph_in_lower_half_leaves_upper_row_to_continue(self):
        text, _ = sst.cell_text([glyph("林", 190, 300, 0)], 263, 308)
        self.assertEqual(text, ":11 임")


class LayoutTests(unittest.TestCase):
    def test_vector_octave_dots_and_rest_only_gak_are_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            pdf = Path(d) / "vector.pdf"
            score_pdf(pdf, [[["大", "林", "-"], ["", "", ""], ["仲", "林", "-"]]])
            with pymupdf.open(pdf) as doc:
                page = doc[0]
                dae = next(g for g in sst.glyphs(page) if g["c"] == "大")
                for dy in (5, 8):
                    page.draw_circle((dae["x"], dae["y"] - dy), 0.9, color=None, fill=(0, 0, 0))
                for row in range(3):
                    x, y = 500 - (COL_W + 16) - COL_W / 2, 40 + row * JG_H + JG_H / 2
                    page.draw_polyline([(x, y - 4), (x + 5, y + 4), (x - 5, y + 4), (x, y - 4)])
                gaks, _, report = sst.read_page(page)
            self.assertEqual(gaks, [":0 중청대 | :0 임 | :0 -", ":0 쉼표 | :0 쉼표 | :0 쉼표",
                                    ":0 중 | :0 임 | :0 -"])
            self.assertEqual(report["ignored_at"], [])

    def test_single_above_dot_is_not_an_octave_and_open_triangle_is_not_a_rest(self):
        with tempfile.TemporaryDirectory() as d:
            pdf = Path(d) / "marks.pdf"
            score_pdf(pdf, [[["大", "林", "-"], ["仲", "", "-"]]])
            with pymupdf.open(pdf) as doc:
                page = doc[0]
                dae = next(g for g in sst.glyphs(page) if g["c"] == "大")
                page.draw_circle((dae["x"], dae["y"] - 6), 0.9, color=None, fill=(0, 0, 0))
                x, y = 430, 107
                page.draw_polyline([(x - 4, y + 4), (x, y - 4), (x + 4, y + 4)])
                gaks, _, _ = sst.read_page(page)
            self.assertEqual(gaks, [":0 대 | :0 임 | :0 -", ":0 중 |  | :0 -"])

    def test_full_height_columns_read_right_to_left(self):
        gaks, report = read([[["林", "南", "-"], ["潢", "", "汰"]]])
        self.assertEqual(gaks, [":0 임 | :0 남 | :0 -", ":0 청황 |  | :0 청태"])
        self.assertEqual(report["jeonggans"], [3, 3])

    def test_stacked_tiers_read_tier_by_tier(self):
        # 쪽을 가로 두 단으로 나눈 판형: 위 단의 오른쪽 열, 위 단 왼쪽 열, 아래 단 오른쪽 열 … 순서다
        gaks, report = read(TWO_TIERS)
        self.assertEqual(gaks, [":0 임 | :0 남 | :0 -", ":0 청황 | :0 청태 | :0 -",
                                ":0 중 | :0 임 | :0 -", ":0 남 | :0 청황 | :0 -"])
        self.assertEqual(report["columns"], 4)

    def test_lines_are_clipped_to_visible_cells(self):
        # 세로선을 쪽 높이 전체로 그리고 정간 칸만 보이게 잘라 둔 판형도 단을 나눠 읽는다
        self.assertEqual(read(TWO_TIERS, clip_cells=True)[0], read(TWO_TIERS)[0])

    def test_page_with_yul_text_but_no_grid_is_an_error(self):
        # 오선보 쪽의 음계표처럼 율명은 있지만 정간 열이 없으면 읽지 않는다
        with self.assertRaisesRegex(ValueError, "정간 열"):
            read([[["林", "南", "-"]]], no_lines=True)

    def test_duet_reads_first_voice_only(self):
        # 단소 2중주(북천이 맑다커늘 PDF182): 열 위 '단소1'·'단소2' 표시로 성부를 가르고 단소1만 읽는다
        tiers = [[["林", "南", "-"], ["仲", "林", "-"], ["潢", "汰", "-"], ["南", "仲", "-"]]]
        gaks, report = read(tiers, labels=["단소1", "단소2", "단소1", "단소2"])
        self.assertEqual(gaks, [":0 임 | :0 남 | :0 -", ":0 청황 | :0 청태 | :0 -"])
        self.assertEqual(report["voices"], 2)

    def test_duet_column_without_its_own_label_is_an_error(self):
        tiers = [[["林", "南", "-"], ["仲", "林", "-"], ["潢", "汰", "-"]]]
        with self.assertRaisesRegex(ValueError, "성부"):
            read(tiers, labels=["단소1", "단소2"])

    def test_side_page_table_on_a_spread_is_not_read(self):
        # 천년만세 고급(PDF184): 오른쪽 쪽의 장식음 표 칸에도 율명이 있지만 악보가 아니다
        # 표 칸은 정간 수(4)가 악보 열(3)과 달라 빼고, 뺀 열을 report에 남긴다
        gaks, report = read([[["林", "南", "-"], ["潢", "汰", "-"]]], width=1100, side_col=["淋", "仲", "南", "潢"])
        self.assertEqual(gaks, [":0 임 | :0 남 | :0 -", ":0 청황 | :0 청태 | :0 -"])
        self.assertEqual(len(report["dropped"]), 1)

    def test_landscape_page_with_music_across_the_middle_is_read_whole(self):
        # 가로가 긴 쪽이라도 악보가 가운데를 지나 이어지면 모든 열을 읽는다
        cols = [[c, "-", "林"] for c in "林南潢汰仲林南潢汰仲林南"]
        gaks, _ = read([cols], width=1100, right0=1000)
        self.assertEqual(len(gaks), 12)

    def test_short_last_gak_is_kept(self):
        # 곡 끝 각은 정간 수가 달라도 악보다(4정간 각들 뒤 마지막 단의 3정간 각)
        gaks, report = read([[["林", "南", "-", "-"], ["潢", "汰", "-", "-"]], [["仲", "林", "-"]]])
        self.assertEqual(gaks[-1], ":0 중 | :0 임 | :0 -")
        self.assertEqual(report["dropped"], [])

    def test_short_last_column_next_to_the_music_is_kept(self):
        # 소금 취타(교과서 p189): 12정간 열 둘 뒤 왼쪽 옆 열이 곡 끝이라 4정간이다
        cols = [["林", "南", "-", "-"], ["潢", "汰", "-", "-"], ["仲", "-", "△"]]
        gaks, report = read([cols])
        self.assertEqual((len(gaks), report["dropped"]), (3, []))

    def test_prose_page_is_not_a_score(self):
        # 교과서 설명 쪽: 테두리 안 글에 율명 한자가 섞여 있어도 악보가 아니다
        with self.assertRaisesRegex(ValueError, "악보"):
            read([], prose="소금으로 林 南 潢 汰를 불고, 仲은 반음 아래로 짚는다. 이 곡은 굿거리 장단이다.")

    def test_voice_labels_in_other_spellings(self):
        tiers = [[["林", "南", "-"], ["仲", "林", "-"], ["潢", "汰", "-"], ["南", "仲", "-"]]]
        for labels in (["단소Ⅰ", "단소Ⅱ", "단소Ⅰ", "단소Ⅱ"], ["1단소", "2단소", "1단소", "2단소"]):
            gaks, report = read(tiers, labels=labels)
            self.assertEqual((len(gaks), report["voices"]), (2, 2), labels)

    def test_unread_glyph_reports_the_jeonggan_it_sits_in(self):
        # 확인 필요 칸을 결과 악보의 칸(각·정간)으로 잇는다(S15P31E102-469)
        _, report = read([[["林", "南", "-"], ["潢", "ㄱ", "汰"]]])
        (g,) = report["ignored_at"]
        self.assertEqual((g["gak"], g["jeonggan"]), (1, 1))

    def test_trio_reports_three_voices(self):
        tiers = [[["林", "南", "-"], ["仲", "林", "-"], ["潢", "汰", "-"], ["林", "-", "-"], ["汰", "南", "-"],
                  ["潢", "-", "-"]]]
        gaks, report = read(tiers, labels=["단소1", "단소2", "단소3"] * 2)
        self.assertEqual((gaks, report["voices"]), ([":0 임 | :0 남 | :0 -", ":0 임 | :0 - | :0 -"], 3))

    def test_tier_with_only_rests_and_ties_is_read(self):
        # 곡 끝 단에 '- - △'만 남은 열(북천이 맑다커늘 끝)도 위 단과 같은 x의 음 열이면 한 각이다
        gaks, _ = read([[["林", "南", "-"], ["潢", "汰", "-"]], [["-", "-", "△"], ["汰", "-", "林"]]])
        self.assertEqual(gaks[2:], [":0 - | :0 - | :0 쉼표", ":0 청태 | :0 - | :0 임"])

    def test_page_with_yul_text_but_no_grid_exits_with_grid_code(self):
        with tempfile.TemporaryDirectory() as d:
            pdf = Path(d) / "s.pdf"
            score_pdf(pdf, [[["林", "南", "-"]]], no_lines=True)
            run = subprocess.run([sys.executable, str(SCRIPT), str(pdf), str(Path(d) / "out")], capture_output=True)
        self.assertEqual(run.returncode, sst.NO_GRID_EXIT)

    def test_page_without_yul_text_exits_with_omr_code(self):
        with tempfile.TemporaryDirectory() as d:
            pdf = Path(d) / "blank.pdf"
            doc = pymupdf.open()
            doc.new_page()
            doc.save(pdf)
            run = subprocess.run([sys.executable, str(SCRIPT), str(pdf), str(Path(d) / "out")], capture_output=True)
        self.assertEqual(run.returncode, sst.NO_YUL_EXIT)


if __name__ == "__main__":
    unittest.main()
