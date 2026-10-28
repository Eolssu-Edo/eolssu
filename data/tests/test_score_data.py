import copy
from fractions import Fraction as F
import json
from pathlib import Path
import sys
import unittest

import jsonschema

DATA = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DATA / "src/scores"))
from score_data import SCHEMA_PATH, build_events, check, convert, convert_answer, melody_of, read_units

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


META = dict(title="시험곡", instrument="DANSO", source_id="example-p1", jangdan="세마치", bpm_unit="DOTTED_QUARTER",
            review="reviewed", jgb_sha256="0" * 64)


class ConvertTests(unittest.TestCase):
    def setUp(self):
        self.schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))

    def convert(self, bpj=F(1)):
        return convert(load("v2-minimal.jgb.json"), beats_per_jeonggan=bpj, **META)

    def test_result_follows_the_schema(self):
        jsonschema.validate(self.convert(), self.schema)

    def test_times_become_integer_ticks_with_lcm_ticks_per_beat(self):
        result = self.convert()
        self.assertEqual((result["music"]["ticksPerBeat"], result["music"]["ticksPerJeonggan"]), (3, 3))
        self.assertEqual([(e["startTick"], e["durationTick"]) for e in result["events"]],
                         [(0, 5), (5, 1), (6, 3), (9, 6), (15, 3)])
        result = self.convert(F(1, 3))  # 정간 1/3박 → 이음 한 줄 1/9박
        self.assertEqual((result["music"]["ticksPerBeat"], result["music"]["ticksPerJeonggan"]), (9, 3))

    def test_cells_point_at_events_and_events_point_at_cells(self):
        result = self.convert()
        cells = [cell for column in result["columns"] for cell in column["cells"]]
        events = {e["id"]: e for e in result["events"]}
        self.assertEqual([c["id"] for c in cells], ["c0-j0", "c0-j1", "c0-j2", "c1-j0", "c1-j1", "c1-j2"])
        self.assertEqual([u["eventId"] for u in cells[1]["units"]], ["c0-j0-r0-u0", "c0-j0-r0-u0", "c0-j1-r2-u0"])
        self.assertTrue(all(e["cellId"] in {c["id"] for c in cells} for e in events.values()))

    def test_schema_rejects_a_missing_field_or_wrong_kind(self):
        for broken in (lambda r: r["events"][0].pop("durationTick"), lambda r: r["events"][0].update(kind="SOUND"),
                       lambda r: r["events"][2].update(yul="임"), lambda r: r["music"].pop("ticksPerBeat")):
            result = copy.deepcopy(self.convert())
            broken(result)
            with self.assertRaises(jsonschema.ValidationError):
                jsonschema.validate(result, self.schema)


ANSWERS = ["ngc-standard-2022-p171", "ngc-standard-2022-p172", "ngc-standard-2022-p173", "ngc-standard-2022-p174"]
YUL = r"(?:하하배|하배|중청|배|청)?[황대태협고중유임이남무응]"


class AnswerTests(unittest.TestCase):
    """저장소의 채점 정답 4곡을 실제로 변환해 형식·불변식·계보를 확인한다."""

    def test_answers_pass_schema_and_invariants(self):
        schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        for source_id in ANSWERS:
            result = convert_answer(source_id)
            jsonschema.validate(result, schema)
            self.assertEqual(check(result), [], source_id)

    def test_event_counts_match_an_independent_count_of_the_answer(self):
        import re
        for source_id in ANSWERS:
            jgb = next((DATA / "annotations/scores" / source_id).glob("*.jgb.json"))
            melody = melody_of(json.loads(jgb.read_text(encoding="utf-8")))
            kinds = [e["kind"] for e in convert_answer(source_id)["events"]]
            self.assertEqual(kinds.count("NOTE"), len(re.findall(YUL, melody)), source_id)
            self.assertEqual(kinds.count("REST"), len(re.findall("쉼", melody)), source_id)

    def test_conversion_is_deterministic(self):
        for source_id in ANSWERS:
            first, second = (json.dumps(convert_answer(source_id), ensure_ascii=False) for _ in range(2))
            self.assertEqual(first, second, source_id)

    def test_check_reports_each_broken_invariant(self):
        broken = {
            "길이 합": lambda r: r["events"][0].update(durationTick=r["events"][0]["durationTick"] + 1),
            "이어짐": lambda r: r["events"][1].update(startTick=r["events"][1]["startTick"] + 1),
            "eventId": lambda r: r["columns"][0]["cells"][0]["units"][0].update(eventId="c9-j9-r0-u0"),
            "cellId": lambda r: r["events"][0].update(cellId="c9-j9"),
            "율명": lambda r: r["events"][0].update(yul="협"),
        }
        base = convert_answer(ANSWERS[2])
        for name, breaker in broken.items():
            result = copy.deepcopy(base)
            breaker(result)
            problems = check(result)
            self.assertTrue(any(name in p for p in problems), (name, problems))


if __name__ == "__main__":
    unittest.main()
