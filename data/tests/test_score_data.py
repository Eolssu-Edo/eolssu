from fractions import Fraction as F
import json
from pathlib import Path
import sys
import unittest

DATA = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DATA / "src/scores"))
from score_data import build_events, melody_of, read_units

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


class BuildEventsTests(unittest.TestCase):
    def test_holds_and_blank_cells_extend_the_previous_event(self):
        events, unit_events = build_events(read_units(MELODY, beats_per_jeonggan=F(1)))
        got = [(e.id, e.kind, e.yul, e.octave, e.start, e.duration, e.cell_id, e.breath_after) for e in events]
        self.assertEqual(got, [
            ("c0-j0-r0-u0", "NOTE", "중", 0, F(0), F(5, 3), "c0-j0", False),   # 중 + 다음 정간의 이음 두 줄
            ("c0-j1-r2-u0", "NOTE", "임", 0, F(5, 3), F(1, 3), "c0-j1", False),
            ("c0-j2-r0-u0", "REST", None, None, F(2), F(1), "c0-j2", False),
            ("c1-j0-r0-u0", "NOTE", "황", 1, F(3), F(2), "c1-j0", True),        # 청황 + 빈 정간, 숨표
            ("c1-j2-r0-u0", "NOTE", "무", 0, F(5), F(1), "c1-j2", False),
        ])
        # 정간 글자마다 자기가 속한 이벤트를 가리킨다(이음·빈 정간은 앞 이벤트)
        ids = [e.id for e in events]
        self.assertEqual(unit_events, [ids[0], ids[0], ids[0], ids[1], ids[2], ids[3], ids[3], ids[4]])

    def test_octave_prefix_becomes_number(self):
        events, _ = build_events(read_units("배임 | 중청태 | 하배황", beats_per_jeonggan=F(1)))
        self.assertEqual([(e.yul, e.octave) for e in events], [("임", -1), ("태", 2), ("황", -2)])

    def test_sigimsae_and_breath_on_a_hold_belong_to_the_held_event(self):
        events, _ = build_events(read_units("임{니나} | -<", beats_per_jeonggan=F(1)))
        self.assertEqual(len(events), 1)
        self.assertEqual((events[0].sigimsae, events[0].breath_after, events[0].duration), (("니나",), True, F(2)))

    def test_hold_without_a_previous_sound_stops(self):
        with self.assertRaisesRegex(ValueError, "c0-j0"):
            build_events(read_units("- | 임", beats_per_jeonggan=F(1)))


if __name__ == "__main__":
    unittest.main()
