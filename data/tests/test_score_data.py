from fractions import Fraction as F
import json
from pathlib import Path
import sys
import unittest

DATA = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DATA / "src/scores"))
from score_data import melody_of, read_units

FIXTURES = DATA / "tests/fixtures/jgb"
# 두 고정 파일의 선율: 1각 = 중 | - - 임 | 쉼, 2각 = 청황< | (빈 정간) | 무
MELODY = "중 | - - 임 | 쉼\n청황< |  | 무"


def load(name):
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


class MelodyTests(unittest.TestCase):
    def test_v1_root_and_v2_parts_give_the_same_melody(self):
        self.assertEqual(melody_of(load("v1-minimal.jgb.json")), MELODY)
        self.assertEqual(melody_of(load("v2-minimal.jgb.json")), MELODY)

    def test_several_parts_need_an_explicit_part(self):
        doc = load("v2-minimal.jgb.json")
        doc["parts"].append(dict(doc["parts"][0], id=2, melody="임"))
        with self.assertRaises(ValueError):
            melody_of(doc)
        self.assertEqual(melody_of(doc, part=1), "임")


class ReadUnitsTests(unittest.TestCase):
    def test_rows_split_a_cell_evenly_and_times_are_exact_fractions(self):
        units = read_units(MELODY, beats_per_jeonggan=F(1))
        got = [(u.cell_id, u.row, u.symbol, u.start, u.duration) for u in units]
        self.assertEqual(got, [
            ("c0-j0", 0, "중", F(0), F(1)),
            ("c0-j1", 0, "-", F(1), F(1, 3)),
            ("c0-j1", 1, "-", F(4, 3), F(1, 3)),
            ("c0-j1", 2, "임", F(5, 3), F(1, 3)),
            ("c0-j2", 0, "쉼", F(2), F(1)),
            ("c1-j0", 0, "청황", F(3), F(1)),
            ("c1-j1", 0, "", F(4), F(1)),  # 빈 정간: 줄 하나짜리 빈 글자
            ("c1-j2", 0, "무", F(5), F(1)),
        ])
        self.assertEqual([u.breath for u in units].count(True), 1)
        self.assertTrue(units[5].breath)

    def test_beats_per_jeonggan_scales_every_time(self):
        units = read_units("임 | 청황 무", beats_per_jeonggan=F(1, 3))
        self.assertEqual([(u.start, u.duration) for u in units],
                         [(F(0), F(1, 3)), (F(1, 3), F(1, 6)), (F(1, 2), F(1, 6))])

    def test_joined_units_split_their_row(self):
        units = read_units("청임청중 무", beats_per_jeonggan=F(1))
        self.assertEqual([(u.row, u.col, u.symbol, u.duration) for u in units],
                         [(0, 0, "청임", F(1, 4)), (0, 1, "청중", F(1, 4)), (1, 0, "무", F(1, 2))])

    def test_sigimsae_and_rest_alias_are_read(self):
        a, b = read_units("임{니나} | 쉼표", beats_per_jeonggan=F(1))
        self.assertEqual((a.symbol, a.sigimsae), ("임", ("니나",)))
        self.assertEqual(b.symbol, "쉼")

    def test_unreadable_text_stops_with_its_cell(self):
        with self.assertRaisesRegex(ValueError, "c0-j1"):
            read_units("임 | 도레미", beats_per_jeonggan=F(1))


if __name__ == "__main__":
    unittest.main()
