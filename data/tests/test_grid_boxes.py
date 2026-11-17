from pathlib import Path
import sys
import unittest

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/scores"))
from grid_boxes import find_blocks, find_grid, gaks


def sheet(parts=1):
    """율명 열(가로 점선 있음)과 그 오른쪽 가사 열(가로선 없음)이 번갈아 있는 세로쓰기 악보 그림.
    성부가 둘이면 가사 열 왼쪽에 율명 열이 둘 붙는다. 단 하나, 각마다 정간 3개."""
    g = np.full((340, 60 + 3 * 80 * parts), 255, np.uint8)
    xs, x = [], 20
    for _ in range(2):  # 각 2개: 왼쪽부터 율명 열 parts개 + 가사 열
        for _ in range(parts):
            xs.append(x)
            g[120, x:x + 60:6], g[120, x + 1:x + 60:6], g[120, x + 2:x + 60:6] = 0, 0, 0  # 점선
            g[220, x:x + 60:6], g[220, x + 1:x + 60:6], g[220, x + 2:x + 60:6] = 0, 0, 0
            x += 60
        x += 20
    for lx in xs + [x0 + 60 for x0 in xs] + [x]:
        g[20:320, lx] = 0
    g[20, 20:x], g[320, 20:x] = 0, 0
    return g


class GridBoxesTests(unittest.TestCase):
    def test_reads_columns_right_to_left_and_rows_top_to_bottom(self):
        (block,) = find_blocks(sheet())
        (boxes,) = block
        self.assertEqual([(round(x), round(y)) for x, y, w, h in boxes],
                         [(100, 20), (100, 120), (100, 220), (20, 20), (20, 120), (20, 220)])

    def test_two_parts_take_the_column_next_to_lyrics_first(self):
        (block,) = find_blocks(sheet(parts=2), parts=2)
        self.assertEqual([round(b[0][0]) for b in block], [220, 160])
        self.assertEqual([len(p) for p in block], [6, 6])

    def test_strict_scan_mode_does_not_treat_a_duet_voice_as_lyrics(self):
        blocks = find_blocks(sheet(parts=2), allow_alternating_lyrics=False)
        self.assertFalse(any(part for block in blocks for part in block))

    def test_two_tiers_with_thin_lines_keep_each_tier_before_the_next(self):
        first = sheet()
        page = np.full((740, first.shape[1]), 255, np.uint8)
        page[:340] = first
        page[400:] = first
        (block,) = find_blocks(page)
        (boxes,) = block
        self.assertEqual([(round(x), round(y)) for x, y, w, h in boxes],
                         [(100, 20), (100, 120), (100, 220), (20, 20), (20, 120), (20, 220),
                          (100, 420), (100, 520), (100, 620), (20, 420), (20, 520), (20, 620)])



def classroom(k=1.0, columns=3, gaks=2, jangdan=False, open_lyric=False, broken=False, thick_lines=True):
    """단소교실 판형을 배율 k로 그린 쪽 (S15P31E102-1077).

    오른쪽부터 제목 열, [가사 열, 율명 열] x columns, 맨 왼쪽 빈 열. 열마다 각 gaks개, 각마다 정간 3개.
    정간 사이는 가는 선, 각 사이는 굵은 선이고 굵은 선은 가사 열·빈 열까지 가로지른다(가사 열에는 가는 선이 없다).
    율명 칸에는 왼쪽 세로선에 붙은 굵은 세로 획과, 띄엄띄엄 가로로 이어진 획(점선처럼 보이는 글자)을 그린다.
    jangdan이면 제목 열 대신 정간 크기 칸이 있는 짧은 장단 열을 맨 오른쪽에 둔다.
    open_lyric이면 장단 열 아래에서 맨 오른쪽 가사 열의 오른쪽 세로선이 끊긴다(실기교재 박연폭포).
    thick_lines가 거짓이면 각 사이도 가는 선이다(굵은 각 구분선이 없는 판형).
    broken이면 율명 열의 세로선 하나가 가운데에서 3px 끊기고 아래 조각이 1px 옆으로 밀린다(스캔).
    """
    r = lambda v: max(1, round(v * k))
    note, lyric, empty, title, pitch, thin, thick, m = r(190), r(100), r(100), r(270), r(207), r(2), r(7), r(60)
    rows = 3 * gaks
    widths = [title] + [lyric, note] * columns + [empty]
    width, height = m * 2 + sum(widths), m * 2 + pitch * rows
    g = np.full((height, width), 255, np.uint8)
    xs = [width - m]
    for w in widths:
        xs.append(xs[-1] - w)
    top, bottom = m, m + pitch * rows
    for x in xs:
        g[top:bottom + thin, x:x + thin] = 0
    g[top:top + thick, xs[-1]:xs[0]] = 0
    g[bottom:bottom + thick, xs[-1]:xs[0]] = 0
    notes = [(xs[i + 1], xs[i]) for i in range(2, 2 + 2 * columns, 2)]  # (왼쪽, 오른쪽) 오른쪽 율명 열부터
    for i in range(1, rows):
        y = top + i * pitch
        if i % 3 == 0 and thick_lines:
            g[y:y + thick, xs[-1]:xs[1]] = 0
        else:
            for x0, x1 in notes:
                g[y:y + thin, x0:x1] = 0
    for x0, x1 in notes:
        for i in range(rows):
            y = top + i * pitch
            g[y + r(30):y + r(30) + r(90), x0 + thin:x0 + thin + r(12)] = 0  # 세로선에 붙은 굵은 획
            for a, b in ((0, .2), (.35, .45), (.55, .6), (.8, 1)):  # 글자 획: 조각 4개, 고르지 않은 간격
                g[y + r(100):y + r(100) + thin, x0 + int((x1 - x0) * a):x0 + int((x1 - x0) * b)] = 0
    if broken:
        y = top + pitch * rows // 2
        g[y:bottom + thin, xs[2]:xs[2] + thin + 1] = 255
        g[y + r(3):bottom + thin, xs[2] - 1:xs[2] - 1 + thin] = 0
    if jangdan:
        g[:, xs[0] - title + thin:] = 255
        x0, x1 = xs[1], xs[1] + note
        g[top:top + pitch * 3 + thin, x1:x1 + thin] = 0
        for i in range(4):
            g[top + i * pitch:top + i * pitch + thin, x0:x1 + thin] = 0
        if open_lyric:
            g[top + pitch * 3 + thick:bottom + thick, x0:x0 + thin] = 255
    return g, notes, top, pitch


class GridAddressTests(unittest.TestCase):
    """쪽 그림에서 율명 칸마다 (단, 열, 줄) 주소와 위쪽 선 굵기를 찾는다 (S15P31E102-1077)."""

    def addresses(self, g):
        (block,) = find_grid(g)
        (cells,) = block
        return cells

    def test_classroom_layout_gives_every_note_cell_an_address_at_any_scale(self):
        for k in (0.5, 1.0, 2.0):
            with self.subTest(k=k):
                g, notes, top, pitch = classroom(k)
                cells = self.addresses(g)
                self.assertEqual([(c['band'], c['col'], c['row']) for c in cells],
                                 [(0, col, row) for col in range(3) for row in range(6)])
                for c in cells:
                    x0, x1 = notes[c['col']]
                    self.assertLessEqual(abs(c['x'] - x0), 3 * k + 2)
                    self.assertLessEqual(abs(c['x'] + c['w'] - x1), 3 * k + 2)
                    self.assertLessEqual(abs(c['y'] - (top + c['row'] * pitch)), 8 * k + 2)

    def test_line_above_each_cell_keeps_its_thickness(self):
        cells = self.addresses(classroom()[0])
        thick = [c['row'] for c in cells if c['col'] == 0 and c['line'] and c['line'] >= 5]
        thin = [c['row'] for c in cells if c['col'] == 0 and c['line'] and c['line'] <= 3]
        self.assertEqual((thick, thin), ([3], [1, 2, 4, 5]))

    def test_vertical_line_with_a_small_scan_break_is_one_line(self):
        cells = self.addresses(classroom(broken=True)[0])
        self.assertEqual([(c['col'], c['row']) for c in cells], [(col, row) for col in range(3) for row in range(6)])

    def test_vertical_line_broken_by_a_long_gap_keeps_the_column_in_its_band(self):
        # 실기교재 바람개비·고사리꺾자: 율명 열 경계선이 칸 절반 높이만큼 끊긴다.
        g, notes, top, pitch = classroom()
        x = notes[0][1]
        y = top + pitch * 3 + pitch // 4
        g[y:y + pitch // 2, x - 2:x + 4] = 255
        self.assertEqual([(c['col'], c['row']) for c in self.addresses(g)],
                         [(col, row) for col in range(3) for row in range(6)])

    def test_column_whose_outer_line_ends_early_stays_in_its_band(self):
        # 실기교재 싸름타령: 마지막 열 바깥 선이 칸 하나 넘게(단 높이의 14%) 일찍 끝난다.
        g, notes, top, pitch = classroom()
        x = notes[2][0]
        g[top + pitch * 6 - pitch * 11 // 10:top + pitch * 6 + 4, x - 2:x + 4] = 255
        self.assertEqual([(c['col'], c['row']) for c in self.addresses(g)],
                         [(col, row) for col in range(3) for row in range(6)])

    def test_stacked_bands_with_a_small_gap_stay_separate_bands(self):
        # 표준악보집: 위아래 단 사이 틈이 단 높이의 4%쯤이고 오른쪽에 두 단을 꿰는 긴 선이 있다.
        page, _, _, pitch = classroom()
        gap = pitch // 4
        g = np.full((page.shape[0] * 2 + gap, page.shape[1] + 40), 255, np.uint8)
        g[:page.shape[0], :page.shape[1]] = page
        g[page.shape[0] + gap:, :page.shape[1]] = page
        g[60:-60, -20:-18] = 0
        cells = self.addresses(g)
        self.assertEqual([(c['band'], c['col'], c['row']) for c in cells],
                         [(band, col, row) for band in range(2) for col in range(3) for row in range(6)])

    def test_missed_line_between_two_cells_splits_the_double_cell(self):
        # 실기교재 강원도아리랑: 빈 정간 두 개 사이 점선을 못 찾아 2배 높이 칸이 된다.
        g, notes, top, pitch = classroom()
        x0, x1 = notes[1]
        y = top + pitch * 5
        g[y:y + 3, x0 + 3:x1 - 2] = 255
        cells = self.addresses(g)
        self.assertEqual([(c['col'], c['row']) for c in cells], [(col, row) for col in range(3) for row in range(6)])
        split = [c for c in cells if c['col'] == 1 and c['row'] in (4, 5)]
        self.assertLessEqual(abs(split[1]['y'] - (top + pitch * 5)), 4)

    def test_short_jangdan_column_without_lyrics_is_not_read(self):
        cells = self.addresses(classroom(jangdan=True)[0])
        self.assertEqual(len(cells), 18)

    def test_lyric_column_open_on_the_right_still_marks_its_note_column(self):
        cells = self.addresses(classroom(jangdan=True, open_lyric=True)[0])
        self.assertEqual(len(cells), 18)

    def test_find_blocks_keeps_plain_boxes(self):
        g = classroom()[0]
        (block,) = find_blocks(g)
        self.assertEqual([tuple(c[k] for k in 'xywh') for c in find_grid(g)[0][0]], [tuple(b) for b in block[0]])



class GakBoundaryTests(unittest.TestCase):
    """칸 위 가로선이 같은 쪽의 가는 선보다 분명히 굵으면 각 경계다 (S15P31E102-1077 D-1·D-3)."""

    def split(self, g, fits=None):
        (block,) = find_grid(g)
        return gaks(block[0], fits)

    def test_thick_lines_split_each_column_into_gaks_at_any_scale(self):
        for k in (0.5, 1.0, 2.0):
            with self.subTest(k=k):
                got = self.split(classroom(k)[0])
                self.assertEqual([[(c['col'], c['row']) for c in gak['cells']] for gak in got],
                                 [[(col, row + 3 * i) for row in range(3)] for col in range(3) for i in range(2)])
                self.assertEqual({(gak['source'], gak['mismatch']) for gak in got}, {('line', False)})

    def test_thick_line_gak_that_disagrees_with_the_jangdan_is_marked(self):
        got = self.split(classroom()[0], fits=lambda n: n == 4)
        self.assertEqual([len(gak['cells']) for gak in got], [3] * 6)
        self.assertTrue(all(gak['mismatch'] for gak in got))

    def test_thick_beat_lines_inside_a_column_that_fits_the_jangdan_are_not_gak_lines(self):
        # 표준악보집 늴리리야: 굿거리 12정간 열 안에 3정간마다 굵은 박 구분선이 있다(2026-11-17 사용자 결정).
        got = self.split(classroom()[0], fits=lambda n: n == 6)
        self.assertEqual([(len(gak['cells']), gak['source'], gak['mismatch']) for gak in got], [(6, 'column', False)] * 3)

    def test_without_thick_lines_a_column_that_fits_the_jangdan_is_one_gak(self):
        got = self.split(classroom(thick_lines=False)[0], fits=lambda n: n == 6)
        self.assertEqual([(len(gak['cells']), gak['source'], gak['mismatch']) for gak in got], [(6, 'column', False)] * 3)

    def test_without_thick_lines_a_long_column_is_cut_by_the_jangdan_size(self):
        got = self.split(classroom(thick_lines=False)[0], fits=lambda n: n == 3)
        self.assertEqual([(len(gak['cells']), gak['source']) for gak in got], [(3, 'jangdan')] * 6)

    def test_without_thick_lines_or_a_fitting_size_the_column_stays_one_marked_gak(self):
        got = self.split(classroom(thick_lines=False)[0], fits=lambda n: n == 4)
        self.assertEqual([(len(gak['cells']), gak['source'], gak['mismatch']) for gak in got], [(6, 'column', True)] * 3)

    def test_a_column_without_thick_lines_is_cut_by_the_jangdan_even_if_another_column_has_them(self):
        lines = {0: [None, 1, 1, 5, 1, 1], 1: [None, 1, 1, 1, 1, 1]}
        cells = [dict(band=0, col=col, row=r, line=lines[col][r]) for col in (0, 1) for r in range(6)]
        got = gaks(cells, fits=lambda n: n == 3)
        self.assertEqual([(gak['cells'][0]['col'], len(gak['cells']), gak['source'], gak['mismatch']) for gak in got],
                         [(0, 3, 'line', False)] * 2 + [(1, 3, 'jangdan', False)] * 2)

    def test_one_and_two_pixel_lines_are_not_thick(self):
        cells = [dict(band=0, col=0, row=r, line=None if r == 0 else (2 if r == 2 else 1)) for r in range(4)]
        self.assertEqual([len(gak['cells']) for gak in gaks(cells)], [4])

    def test_blurred_scan_lines_a_few_pixels_thicker_are_not_thick(self):
        # 실기교재 참새노래처럼 흐린 스캔은 가는 선이 9~15px로 들쭉날쭉하다.
        cells = [dict(band=0, col=0, row=r, line=[None, 11, 11, 15, 11, 10, 11, 11][r]) for r in range(8)]
        self.assertEqual([len(gak['cells']) for gak in gaks(cells)], [8])


if __name__ == "__main__":
    unittest.main()
