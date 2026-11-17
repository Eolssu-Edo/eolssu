"""격자 자리 맞추기(jeongganbo_grid.fit_columns). 그린 격자와 리더가 냈을 법한 상자로 검사한다."""
import copy
from pathlib import Path
import sys

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/scores"))
from jeongganbo_grid import fit_columns

COLS, ROWS, W, H = 3, 6, 96, 104  # 아리랑 150dpi 정간 크기
LEFT, TOP, GAP = 40, 30, 50


def place(col, row):
    """col번째 열(오른쪽부터), row번째 줄 정간의 안쪽 상자."""
    x = LEFT + (COLS - 1 - col) * (W + GAP)
    return x + 2, TOP + row * H + 2, W - 3, H - 3


def page(blank=(), unruled=()):
    """세로선·가로선 격자와 정간마다 글자 덩어리. blank 정간은 비우고 unruled 정간은 세로선도 지운다."""
    g = np.full((TOP * 2 + ROWS * H, LEFT * 2 + COLS * (W + GAP)), 255, np.uint8)
    for col in range(COLS):
        x = LEFT + (COLS - 1 - col) * (W + GAP)
        g[TOP:TOP + ROWS * H + 1, [x, x + 1, x + W, x + W + 1]] = 0
        for row in range(ROWS + 1):
            g[TOP + row * H, x:x + W + 2] = 0
        for row in range(ROWS):
            bx, by, bw, bh = place(col, row)
            if (col, row) in unruled:
                g[by:by + bh, x - 3:x + 3] = g[by:by + bh, x + W - 2:x + W + 4] = 255
            if (col, row) not in blank and (col, row) not in unruled:
                g[by + bh // 3:by + bh * 2 // 3, bx + bw // 3:bx + bw * 2 // 3] = 0
    return g


def cell(col, row, rows=1):
    x, y, w, h = place(col, row)
    return dict(text="임:5", conf=.9, x=x, y=y, w=w, h=h + (rows - 1) * H)


def columns(skip=(), merged=()):
    """merged의 (열, 줄)은 그 줄과 다음 줄을 한 상자로 낸다(가는 가로선을 놓친 리더)."""
    out = []
    for col in range(COLS):
        boxes, row = [], 0
        while row < ROWS:
            if (col, row) in merged:
                boxes.append(cell(col, row, 2))
                row += 2
                continue
            if (col, row) not in skip:
                boxes.append(cell(col, row))
            row += 1
        out.append(boxes)
    return out


def places(cols):
    return [[(c["y"] - TOP) // H for c in col] for col in cols]


def test_clean_grid_is_left_unchanged():
    cols = columns()
    before = copy.deepcopy(cols)
    assert fit_columns(cols, page()) == []
    assert cols == before


def test_row_merged_in_every_column_is_split_from_its_own_height():
    cols = columns(merged={(0, 2), (1, 2), (2, 2)})
    added = fit_columns(cols, page())
    assert len(added) == 6 and all(c["split"] and c["text"] == "" for c in added)
    assert places(cols) == [list(range(ROWS))] * COLS
    first, second = cols[0][2:4]
    assert first["y"] == place(0, 2)[1] and first["y"] + first["h"] == second["y"]
    assert abs(second["y"] - place(0, 3)[1]) <= 3  # 다른 열에도 줄이 없어 상자 높이를 반으로 나눈 위치다
    assert second["y"] + second["h"] == place(0, 3)[1] + place(0, 3)[3]
    assert [c for c in cols[0] if c not in added] == [cell(0, r) for r in (0, 1, 4, 5)]


def test_missing_cell_with_ink_and_interior_blank_are_filled_but_not_trailing_blank():
    cols = columns(skip={(0, 3), (1, 2), (2, 5)})
    added = fit_columns(cols, page(blank={(1, 2), (2, 5)}))
    assert sorted((c["x"], c["y"]) for c in added) == sorted([place(0, 3)[:2], place(1, 2)[:2]])
    assert all(c["filled"] for c in added)
    assert places(cols) == [list(range(ROWS)), list(range(ROWS)), list(range(ROWS - 1))]


def test_blank_gap_without_printed_column_lines_is_not_a_cell():
    cols = columns(skip={(1, 2)})
    assert fit_columns(cols, page(unruled={(1, 2)})) == []
    assert places(cols)[1] == [0, 1, 3, 4, 5]


def test_short_box_still_holds_its_row():
    cols = columns()
    cols[1][3]["y"] += 20
    cols[1][3]["h"] = 60
    assert fit_columns(cols, page()) == []


def test_inside_only_does_not_borrow_rows_of_another_tier():
    cols = columns(skip={(2, 0), (2, 1)})
    assert fit_columns(cols, page(), inside_only=True) == []
    assert places(cols)[2] == [2, 3, 4, 5]


@pytest.mark.parametrize("merged", [{(1, 0)}, {(1, 4)}])
def test_merge_at_column_edge_keeps_following_order(merged):
    cols = columns(merged=merged)
    fit_columns(cols, page())
    assert places(cols) == [list(range(ROWS))] * COLS
