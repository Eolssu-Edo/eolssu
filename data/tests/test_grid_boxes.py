from pathlib import Path
import sys
import unittest

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/scores"))
from grid_boxes import find_blocks


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


if __name__ == "__main__":
    unittest.main()
