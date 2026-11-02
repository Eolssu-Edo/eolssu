"""Explicit source-backed holds may cross only their declared gak boundary."""
from copy import deepcopy
from fractions import Fraction
import unittest

from data.src.playback.tests._helpers import converted, document, midi


class GakContinuationTests(unittest.TestCase):
    def source(self, melody="청황 남 | - | -\n | | \n- | 청황 | 쉼"):
        source = document(melody, beats=3, staffUnit="plain")
        source["playback"] = {"gak_continuations": [
            {"part": 1, "gak": 2, "basis": "앞 남을 다음 각 세 빈 정간까지 5.5정간 이어 불기"}]}
        return source

    def test_declared_boundary_keeps_one_5_5_second_note_and_original_locations(self):
        source = self.source()
        before = deepcopy(source)
        score, mapping = converted(source)
        self.assertEqual(source, before)
        notes = midi(score)["notes"]
        self.assertEqual([(n["pitch"], round(n["start"], 5), round(n["end"], 5)) for n in notes],
                         [(75, 0, .5), (72, .5, 6), (75, 7, 8)])
        held = mapping["notes"]["p1.g1.c1.r2.u1"]
        self.assertEqual([(p["gak"], p["cell"]) for p in held],
                         [(1, 1), (1, 2), (1, 3), (2, 1), (2, 2), (2, 3)])
        self.assertEqual(held[3]["gak_continuation"], source["playback"]["gak_continuations"][0])
        self.assertEqual(mapping["gak_continuations"], source["playback"]["gak_continuations"])
        self.assertEqual(sum(Fraction(n["duration"]) for n in score["notes"]), 9)

    def test_absent_declaration_preserves_default_rest_at_new_gak(self):
        source = self.source()
        del source["playback"]
        score, mapping = converted(source)
        self.assertEqual([(n["pitch"], round(n["start"], 5), round(n["end"], 5)) for n in midi(score)["notes"]],
                         [(75, 0, .5), (72, .5, 3), (75, 7, 8)])
        self.assertNotIn("gak_continuations", mapping)

    def test_invalid_and_duplicate_declarations_are_not_silently_ignored(self):
        valid = {"part": 1, "gak": 2, "basis": "검사"}
        invalid = [None, {}, [None], [{"part": 1, "gak": 2}],
                   [dict(valid, extra=True)], [dict(valid, basis=" ")],
                   [dict(valid, part=True)], [dict(valid, part=2)],
                   [dict(valid, gak=True)], [dict(valid, gak=1)],
                   [dict(valid, gak=4)], [dict(valid, gak=2.5)], [valid, valid]]
        for entries in invalid:
            source = self.source()
            source["playback"]["gak_continuations"] = entries
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                converted(source)

    def test_declaration_needs_an_adjacent_sounding_note_and_first_hold(self):
        for melody in ("청황 | 남 | 쉼\n- | 청태 | 청황",
                       "청황 | 남 | -<\n- | 청태 | 청황",
                       "청황 | 남 | -\n청황 | - | 청태",
                       "청황 | 남 | -\n쉼 | - | 청태"):
            with self.subTest(melody=melody), self.assertRaises(ValueError):
                converted(self.source(melody))

    def test_unselected_part_bounds_are_checked_against_that_parts_melody(self):
        source = self.source()
        source["parts"].append({"id": "part-2", "instrument": "danso", "melody": "청황 | 남 | -\n | | 청태"})
        entry = {"part": 2, "gak": 2, "basis": "두 번째 성부만 이어 불기"}
        source["playback"]["gak_continuations"] = [entry]
        score, mapping = converted(source, part=1)
        self.assertAlmostEqual(midi(score)["notes"][1]["end"], 3)
        self.assertNotIn("gak_continuations", mapping)
        score, mapping = converted(source, part=2)
        self.assertEqual([(n["pitch"], round(n["start"], 5), round(n["end"], 5)) for n in midi(score)["notes"]],
                         [(75, 0, 1), (72, 1, 5), (77, 5, 6)])
        self.assertEqual(mapping["gak_continuations"], [entry])
        source["playback"]["gak_continuations"][0]["gak"] = 3
        with self.assertRaisesRegex(ValueError, "각"):
            converted(source, part=1)

    def test_continued_note_can_end_with_breath_after_a_tempo_change(self):
        source = self.source("청황 | 남 | -\n{조금속하게} - | - | -<")
        plan = {"schema": "danso-performance/v1", "input_sha256": "a" * 64,
                "tempos": [{"gak": 2, "cell": 1, "bpm": 120}]}
        score, mapping = converted(source, performance_plan=plan, input_sha256="a" * 64)
        notes = midi(score)["notes"]
        self.assertEqual(len(notes), 2)
        self.assertAlmostEqual(notes[1]["start"], 1)
        self.assertAlmostEqual(notes[1]["end"], 4.33, places=3)
        held = [loc for locs in mapping["notes"].values() for loc in locs if "gak_continuation" in loc]
        self.assertEqual([(loc["gak"], loc["cell"], loc["row"]) for loc in held], [(2, 1, 2)])


if __name__ == "__main__":
    unittest.main()
