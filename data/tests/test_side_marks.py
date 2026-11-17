"""side_marks: 율명 오른쪽의 작은 시김새 획 보정. 원본 악보 그림 대신 직접 그린 정간을 쓴다."""
from pathlib import Path
import sys
import unittest

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/scores"))
from side_marks import classify, mark_cell, side_marks, unify_short_waves

W, H = 100, 150  # 150dpi 실기교재 정간 크기 정도


def cell(rows=3):
    g = np.full((H, W), 255, np.uint8)
    cv2.rectangle(g, (0, 0), (W - 1, H - 1), 0, 2)
    return g


def glyph(g, row, rows=3, x=34, size=28):
    """한자 한 글자 크기의 연결된 획 덩어리(너비 = 높이)."""
    top = int(H / rows * row + (H / rows - size) / 2)
    cv2.rectangle(g, (x, top), (x + size, top + size), 0, 3)
    cv2.line(g, (x, top + size // 2), (x + size, top + size // 2), 0, 3)
    cv2.line(g, (x + size // 2, top), (x + size // 2, top + size), 0, 3)
    return top


def wave(g, x, top, length, amplitude=2, period=8):
    ys = np.arange(top, top + length)
    pts = np.stack([x + amplitude * np.sin((ys - top) * 2 * np.pi / period), ys], 1).astype(np.int32)
    cv2.polylines(g, [pts], False, 0, 2)


def paren(g, x, top, height=24):
    cv2.ellipse(g, (x, top + height // 2), (6, height // 2), 0, -80, 80, 0, 2)


def caret(g, x, top, size=12):
    cv2.polylines(g, [np.array([[x, top + size * 2 // 3], [x + size // 2, top], [x + size, top + size * 2 // 3]], np.int32)],
                  False, 0, 2)


def n_mark(g, x, top, size=16):
    cv2.polylines(g, [np.array([[x, top], [x, top + size], [x + size, top], [x + size, top + size]], np.int32)],
                  False, 0, 2)


class SideMarksTest(unittest.TestCase):
    def test_short_wave_renames_confused_model_name_to_unwind(self):
        g = cell()
        top = glyph(g, 0)
        glyph(g, 2)
        wave(g, 76, top - 4, 38)
        text, found = side_marks("청태_나니나:2 -:5 청임:8", g)
        self.assertEqual(text, "청태_풀어내림표:2 -:5 청임:8")
        self.assertEqual([(m["name"], m["action"], m["model"]) for m in found], [("풀어내림표", "renamed", ["나니나"])])

    def test_short_wave_is_added_when_model_missed_it(self):
        g = cell()
        top = glyph(g, 0)
        wave(g, 76, top - 4, 38)
        self.assertEqual(side_marks("청태:2 -:5 -:8", g)[0], "청태_풀어내림표:2 -:5 -:8")

    def test_long_wave_puts_vibrato_on_every_spanned_note_but_not_on_ties(self):
        g = cell()
        for r in (0, 2):
            glyph(g, r)
        wave(g, 76, 12, 126)
        self.assertEqual(side_marks("청임:2 -:5 청임_나니나:8", g)[0], "청임_농음표:2 -:5 청임_농음표:8")

    def test_existing_wave_name_is_kept(self):
        g = cell()
        top = glyph(g, 0)
        wave(g, 76, top - 4, 38)
        text, found = side_marks("청태_농음표:2 -:5 -:8", g)
        self.assertEqual(text, "청태_농음표:2 -:5 -:8")
        self.assertEqual([(m["name"], m["action"], m["short"]) for m in found], [("농음표", "kept", True)])

    def test_short_wave_name_follows_the_model_on_the_same_page(self):
        def page(model_names):
            cells = []
            for name in model_names:
                g = cell()
                top = glyph(g, 0)
                wave(g, 76, top - 4, 38)
                cells.append(mark_cell({"text": f"청태_{name}:2 -:5 -:8" if name else "청태:2 -:5 -:8"}, g))
            return cells
        cells = page(["농음표", "농음표", "나니나", ""])
        self.assertEqual(unify_short_waves(cells), 2)
        self.assertEqual([c["text"] for c in cells][2:], ["청태_농음표:2 -:5 -:8"] * 2)
        self.assertEqual(cells[2]["sideMarks"][0]["name"], "농음표")
        cells = page(["농음표", "풀어내림표", "나니나"])
        self.assertEqual(unify_short_waves(cells), 0)  # 같은 수면 실기교재 표기(풀어내림표)를 둔다
        self.assertEqual(cells[2]["text"], "청태_풀어내림표:2 -:5 -:8")
        cells = page(["나니나", ""])
        self.assertEqual(unify_short_waves(cells), 0)
        self.assertEqual([c["text"] for c in cells], ["청태_풀어내림표:2 -:5 -:8"] * 2)

    def test_small_marks_are_added_only_when_model_gave_no_ornament(self):
        for draw, name in ((paren, "흘림표"), (caret, "니레"), (n_mark, "니나*")):
            with self.subTest(name=name):
                g = cell()
                top = glyph(g, 1)
                draw(g, 70, top + 2)
                self.assertEqual(side_marks("청무:2 청임:5 -:8", g)[0], f"청무:2 청임_{name}:5 -:8")
                self.assertEqual(side_marks("청무:2 청임_노니로:5 -:8", g)[0], "청무:2 청임_노니로:5 -:8")

    def test_glyph_strokes_and_ties_are_not_marks(self):
        g = cell()
        for r in range(3):
            glyph(g, r)
        # 글자 오른쪽에 붙은 획(예: 中·黃의 오른쪽 부분)은 글자 덩어리다
        cv2.ellipse(g, (64, 75), (5, 12), 0, -80, 80, 0, 2)
        self.assertEqual(side_marks("청태:2 청태:5 청태:8", g), ("청태:2 청태:5 청태:8", []))
        g = cell()
        paren(g, 70, 60)
        self.assertEqual(side_marks("-:2 -:5 -:8", g)[0], "-:2 -:5 -:8")

    def test_half_cells_are_left_to_the_model(self):
        g = cell()
        glyph(g, 1, x=14)
        glyph(g, 1, x=50)
        paren(g, 84, 56)
        self.assertEqual(side_marks("청태:2 청황:4 청태:6 청태:8", g)[0], "청태:2 청황:4 청태:6 청태:8")

    def test_unusual_text_is_returned_as_is(self):
        g = cell()
        for text in ("", "니나*:5", "청태:2 청태:10", "청태"):
            self.assertEqual(side_marks(text, g)[0], text)

    def test_mark_cell_keeps_model_text_and_found_marks(self):
        g = cell()
        top = glyph(g, 1)
        caret(g, 70, top + 2)
        c = mark_cell({"text": "청무:2 청임:5 -:8"}, g)
        self.assertEqual(c["modelText"], "청무:2 청임:5 -:8")
        self.assertEqual(c["text"], "청무:2 청임_니레:5 -:8")
        self.assertEqual([m["name"] for m in c["sideMarks"]], ["니레"])
        plain = mark_cell({"text": "청무:5"}, cell(1))
        self.assertEqual(plain, {"text": "청무:5"})

    def test_classify_shapes(self):
        def shape(draw):
            canvas = np.full((60, 30), 255, np.uint8)
            draw(canvas)
            ys, xs = np.nonzero(canvas < 160)
            return classify((canvas < 160)[ys.min():ys.max() + 1, xs.min():xs.max() + 1], H)
        self.assertEqual(shape(lambda c: wave(c, 15, 5, 50)), "wave")
        self.assertIsNone(shape(lambda c: cv2.line(c, (15, 5), (15, 55), 0, 2)))  # 곧은 세로획


if __name__ == "__main__":
    unittest.main()
