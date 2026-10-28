"""jgb.py conversion: source positions, notation, timing and input selection."""
from copy import deepcopy
from fractions import Fraction
import unittest

from data.src.playback.score import PPQN, compile_score
from data.src.playback.tests._helpers import converted, document, midi


class JgbTests(unittest.TestCase):
    def test_short_subdivided_graces_keep_main_half_and_exact_timing(self):
        cases = (("eighth", "니레", 1, 1), ("dotted", "느로니르", 3, 1),
                 ("eighth", "느니르", 2, 1), ("eighth", "느니르}{나니나", 2, 3))
        for unit, symbol, grace_count, main_count in cases:
            with self.subTest(unit=unit, symbol=symbol):
                source = document(f"청황{{{symbol}}}청태청임 청황청태청임 청황청태청임 | 청태", staffUnit=unit)
                before = deepcopy(source)
                value, mapping = converted(source)
                notes = value["notes"][:grace_count + main_count]
                slot = Fraction(mapping["quarter_ratio"]) / 9
                self.assertEqual(sum(Fraction(n["duration"]) for n in notes), slot)
                self.assertTrue(all((Fraction(n["duration"]) * PPQN).denominator == 1 for n in notes))
                main = [n for n in notes if ".main" in n["id"]]
                grace = [n for n in notes if ".grace" in n["id"] or ".postgrace" in n["id"]]
                self.assertGreaterEqual(sum(Fraction(n["duration"]) for n in main), slot / 2)
                self.assertEqual(len({n["duration"] for n in grace}), 1)
                self.assertAlmostEqual(midi(value)["notes"][-1]["start"], 1, places=5)
                self.assertEqual(mapping["grace_policy"]["maximum_slot_fraction"], "1/2")
                timing = mapping["notes"][notes[0]["id"]][0]["grace_timing"]
                self.assertTrue(timing["reduced"])
                self.assertEqual(Fraction(timing["each_quarters"]), Fraction(grace[0]["duration"]))
                self.assertEqual(source, before)

    def test_reduced_surround_graces_remain_symmetric_after_hold(self):
        value, mapping = converted(document(
            "청황청태청임 청황청태청임 청황청태청임{느니르} | - | 청태",
            staffUnit="eighth", beats=3))
        front, main, post = value["notes"][8:11]
        self.assertEqual(Fraction(front["duration"]), Fraction(1, 72))
        self.assertEqual(front["duration"], post["duration"])
        self.assertEqual(Fraction(main["duration"]), Fraction(1, 36) + Fraction(1, 2))
        self.assertEqual(len(mapping["notes"][main["id"]]), 2)
        self.assertAlmostEqual(midi(value)["notes"][-1]["start"], 2, places=5)
        self.assertEqual(sum(Fraction(n["duration"]) for n in value["notes"]), Fraction(3, 2))

    def test_jeonggan_tempo_is_not_rejected_by_quarter_conversion(self):
        for unit, bpm, expected in (("eighth", 19, 9.5), ("dotted", 270, 405),
                                    ("dotted", 800, 4800)):
            speed = 4 if bpm == 800 else 1
            with self.subTest(unit=unit, bpm=bpm):
                value, mapping = converted(document(staffUnit=unit, tempoBpm=bpm), speed=speed)
                self.assertEqual(value["quarter_bpm"], expected)
                self.assertEqual(mapping["midi_tempo_us"], round(60_000_000 / expected))
                self.assertAlmostEqual(midi(value)["notes"][-1]["start"], 60 / bpm / speed, places=5)
        with self.assertRaisesRegex(ValueError, "MIDI Set Tempo"):
            converted(document(staffUnit="eighth", tempoBpm=1), speed=.25)

    def test_versions_have_the_same_midi_and_keep_source_untouched(self):
        docs = [document(version=v) for v in (1, 2)]
        before = deepcopy(docs)
        a, b = [converted(d)[0] for d in docs]
        self.assertEqual(midi(a), midi(b))
        self.assertEqual(docs, before)
        self.assertEqual(a["source"]["review_status"], "source-review-unverified")
        self.assertEqual([n["pitch"] for n in a["notes"]], [75, 77])

    def test_quarter_unit_and_speed_preserve_explicit_jeonggan_time(self):
        for unit, ratio in (("dotted", Fraction(3, 2)), ("plain", Fraction(1)), ("eighth", Fraction(1, 2))):
            for speed in (.5, 1, 2):
                with self.subTest(unit=unit, speed=speed):
                    score, mapping = converted(document(staffUnit=unit), speed=speed)
                    self.assertEqual(Fraction(mapping["total_quarters"]), 2 * ratio)
                    notes = midi(score)["notes"]
                    self.assertAlmostEqual(notes[1]["start"], 1 / speed, places=5)
                    self.assertAlmostEqual(notes[1]["end"], 2 / speed, places=5)
        old = document(version=1)
        del old["controls"]["staffUnit"]
        score, mapping = converted(old)
        self.assertEqual(mapping["quarter_ratio"], "3/2")
        self.assertTrue(any("구형" in a for a in score["assumptions"]))
        old["controls"]["beats"] = 12
        old["melody"] = " | ".join(["청황"] * 12)
        score, mapping = converted(old)
        self.assertEqual(mapping["quarter_ratio"], "1/2")
        self.assertEqual(score["quarter_bpm"], 30)
        self.assertAlmostEqual(midi(score)["notes"][-1]["end"], 12, places=5)
        old["controls"]["staffUnit"] = "auto"
        self.assertEqual(converted(old)[1]["quarter_ratio"], "1/2")

    def test_hold_stops_at_gak_boundary_and_repeated_pitch_rearticulates(self):
        score, mapping = converted(document("청황 | -\n- | 청황\n청황 | 쉼"))
        notes = midi(score)["notes"]
        self.assertEqual([(n["pitch"], round(n["start"], 5), round(n["end"], 5)) for n in notes],
                         [(75, 0, 2), (75, 3, 4), (75, 4, 5)])
        self.assertEqual([x["cell"] for x in mapping["notes"]["p1.g1.c1.r1.u1"]], [1, 2])
        self.assertEqual(mapping["notes"]["p1.g2.c1.r1.u1"][0]["kind"], "hold")

    def test_rest_then_hold_stays_silent(self):
        score, _ = converted(document("청황 | 쉼 | - | 청태", beats=4))
        notes = midi(score)["notes"]
        self.assertAlmostEqual(notes[1]["start"], 3, places=5)
        self.assertAlmostEqual(notes[0]["end"], 1, places=5)

    def test_subdivisions_and_variable_gak_length(self):
        score, mapping = converted(document("청황청태 임 | 청중\n청태", gakBeats="2:1"))
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]],
                         [Fraction(3, 8), Fraction(3, 8), Fraction(3, 4), Fraction(3, 2), Fraction(3, 2)])
        self.assertEqual(mapping["cells"], 3)
        self.assertAlmostEqual(midi(score)["notes"][-1]["end"], 3, places=5)

    def test_source_offsets_and_ids_survive_crlf_empty_cells_and_double_bars(self):
        melody = " 청황 | \r\n청태 | 임||\r\n청황 | -\r\n"
        score, mapping = converted(document(melody))
        self.assertEqual(mapping["gaks"], 3)
        self.assertEqual(mapping["part_id"], "part-17")
        positions = [p for ps in mapping["notes"].values() for p in ps]
        self.assertEqual({(p["gak"], p["cell"]) for p in positions},
                         {(g, c) for g in range(1, 4) for c in (1, 2)})
        for p in positions:
            self.assertEqual(melody[p["cell_start"]:p["cell_end"]], p["text"])
        for event in compile_score(score, "danso")["events"]:
            self.assertTrue(all(nid in mapping["notes"] for nid in event["parent_ids"]))

    def test_graces_and_relative_sequences_use_scale_and_preserve_time(self):
        score, mapping = converted(document("청황{니레} | {니나}"))
        perf = compile_score(score, "danso")
        self.assertEqual([n["pitch"] for n in score["notes"]], [77, 75, 77, 75])
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]],
                         [Fraction(1, 16), Fraction(23, 16), Fraction(3, 4), Fraction(3, 4)])
        self.assertEqual([n["velocity"] for n in perf["events"]], [80, 90, 90, 90])
        self.assertEqual([n["legato"] for n in perf["events"]], [False, True, False, True])
        self.assertEqual(mapping["total_quarters"], "3")
        plain, _ = converted(document("청황{nire@120,1,-2} | {nina-dur}"))
        self.assertEqual(midi(plain), midi(score))

    def test_attached_sequence_and_multiple_graces_keep_their_slot(self):
        score, _ = converted(document("청황{나니나} | 청황{노니로}"))
        self.assertEqual([n["pitch"] for n in score["notes"]], [75, 77, 75, 75, 77, 75])
        self.assertEqual(sum(Fraction(n["duration"]) for n in score["notes"]), 3)
        self.assertAlmostEqual(midi(score)["notes"][-1]["end"], 2, places=5)

    def test_breath_is_applied_at_end_of_cell_after_hold(self):
        score, mapping = converted(document("청황< - | 청태"))
        self.assertEqual(len(score["notes"]), 2)
        self.assertAlmostEqual(score["notes"][0]["breath_seconds"], .12)
        self.assertTrue(mapping["notes"][score["notes"][0]["id"]][-1]["breath"])
        notes = midi(score)["notes"]
        self.assertAlmostEqual(notes[0]["end"], .83, places=4)
        self.assertAlmostEqual(notes[1]["start"], 1, places=5)
        with self.assertRaisesRegex(ValueError, "숨표 뒤"):
            converted(document("청황< | -"))
        short, _ = converted(document("청황 청태 임< | 청태"), bpm=240)
        self.assertGreater(midi(short)["notes"][2]["end"]-midi(short)["notes"][2]["start"], 0)

    def test_part_selection_does_not_follow_active_editor_or_mix_voices(self):
        doc = document()
        doc["parts"].append({"id": 7, "name": "둘째", "instrument": "danso", "melody": "임 | 중"})
        doc["activePart"] = 1
        with self.assertRaisesRegex(ValueError, "--part"):
            converted(doc)
        score, mapping = converted(doc, part=2)
        self.assertEqual([n["pitch"] for n in score["notes"]], [70, 68])
        self.assertEqual(mapping["part_id"], 7)
        self.assertTrue(all(nid.startswith("p2.") for nid in mapping["notes"]))

    def test_missing_tempo_and_reference_need_explicit_overrides(self):
        doc = document()
        del doc["controls"]["tempoBpm"]
        del doc["controls"]["hwangPitch"]
        doc["controls"]["tempoBpmGak"] = 999
        with self.assertRaisesRegex(ValueError, "BPM"):
            converted(doc)
        score, mapping = converted(doc, bpm=80, hwang_midi=62)
        self.assertEqual(score["quarter_bpm"], 120)
        self.assertEqual(score["notes"][0]["pitch"], 74)
        self.assertEqual(mapping["huangjong_midi"], 62)

    def test_ambiguous_or_unsupported_notation_fails_with_position(self):
        for melody in ("청황{추성표} | 청태", "청황{겹흘림표} | 청태", "청황{반길이표} | 청태",
                       "청황{알수없음} | 청태", "潢 | 청태", "{노} | 청태", "청황{니레 | 청태",
                       "청황청태 | 청태", "청황 청태 임 중 | 청태", "청황 | 청태 | 임"):
            with self.subTest(melody=melody), self.assertRaisesRegex(ValueError, "각"):
                converted(document(melody))
        with self.assertRaisesRegex(ValueError, "--jo"):
            converted(document("청황{니레} | 청태", joPreset="all"))
        score, _ = converted(document("청황{니레} | 청태", joPreset="all"), jo="jung-pyeong")
        self.assertEqual(score["notes"][0]["pitch"], 77)

    def test_invalid_settings_and_unrepresentable_timing_fail(self):
        for controls in ({"tempoBpm": "nan"}, {"hwangPitch": True}, {"beats": "2.5"},
                         {"gakBeats": "2:1"}, {"gakBeats": "1:2,1:2"}, {"staffUnit": "mystery"}):
            with self.subTest(controls=controls), self.assertRaises(ValueError):
                converted(document(**controls))
        with self.assertRaisesRegex(ValueError, "Unrepresentable"):
            from data.src.playback.score import ticks
            ticks("1/7")
        with self.assertRaisesRegex(ValueError, "outside"):
            compile_score(converted(document("배황 | 청태"))[0], "danso")
        with self.assertRaisesRegex(ValueError, "버전"):
            converted(document(version=3))

    def test_source_and_asset_mismatch_do_not_silently_drop_sound(self):
        doc = document()
        doc["parts"][0]["instrument"] = "sogeum"
        with self.assertRaisesRegex(ValueError, "단소"):
            converted(doc)
        doc = document()
        doc["parts"][0]["muted"] = True
        with self.assertRaisesRegex(ValueError, "음소거"):
            converted(doc)
        doc = document(wantJangdan=True, playJanggu=True)
        doc["jangdan"] = "덩 | 궁"
        with self.assertRaisesRegex(ValueError, "장구"):
            converted(doc)
        doc = document(version=1, wantJangdan=True)
        doc["jangdan"] = "덩 | 궁"
        with self.assertRaisesRegex(ValueError, "장구"):
            converted(doc)
        with self.assertRaisesRegex(ValueError, "true/false"):
            converted(document(playJanggu="false"))
        with self.assertRaisesRegex(ValueError, "꺼져"):
            converted(document("청황{니레} | 청태", playSigimsae=False))

    def test_nested_triplets_preserve_exact_cell_and_midi_timing(self):
        score, _ = converted(document('청황청태임 청황청태임 청황청태임 | 청태', staffUnit='eighth'))
        self.assertEqual([Fraction(n['duration']) for n in score['notes'][:9]], [Fraction(1,18)]*9)
        played = midi(score)
        self.assertEqual(len(played['notes']), 10)
        self.assertAlmostEqual(played['notes'][-1]['start'], 1, places=6)

    def test_long_breath_keeps_existing_120ms_gap_and_50ms_release(self):
        score, _ = converted(document('청황< | 청태'))
        n=midi(score)['notes'][0]
        self.assertAlmostEqual(n['end'], .83, places=5)
        self.assertAlmostEqual(n['release_seconds'], .05)

    def test_blank_sustain_keeps_subdivision_and_source_location(self):
        score, mapping = converted(document('청황 {빈지속} | 청태'))
        self.assertEqual([n['pitch'] for n in score['notes']], [75,77])
        self.assertEqual([Fraction(n['duration']) for n in score['notes']], [Fraction(3,2)]*2)
        self.assertEqual(len(mapping['notes'][score['notes'][0]['id']]),2)
        score, _ = converted(document('{빈지속} | 청태'))
        self.assertTrue(score['notes'][0]['rest'])

    def test_vibrato_on_hold_starts_at_mark_without_rearticulating(self):
        score, mapping = converted(document('청임 -{농음표} 청임 | 청태'))
        self.assertEqual([n['pitch'] for n in score['notes']], [82,82,77])
        self.assertEqual(Fraction(score['notes'][0]['duration']), 1)
        self.assertAlmostEqual(score['notes'][0]['yoseong']['delay_seconds'], 1/3)
        played = midi(score)
        self.assertTrue(all(value==1 for t,value in played['cc11'] if t<1/3))
        self.assertTrue(any(value<1 for t,value in played['cc11'] if 1/3<t<2/3))
        self.assertIn('농음표',mapping['notes'][score['notes'][0]['id']][-1]['symbols'])
        with self.assertRaisesRegex(ValueError, '본음'):
            converted(document('-{농음표} | 청태'))

    def test_marked_vibrato_and_fall_change_controls_without_changing_pitches(self):
        plain, _ = converted(document('청황 | 청태'))
        decorated, mapping = converted(document('청황{농음표} | 청태{흘림표}'))
        a,b=midi(plain),midi(decorated)
        self.assertEqual([(n['pitch'],n['start'],n['end']) for n in a['notes']],
                         [(n['pitch'],n['start'],n['end']) for n in b['notes']])
        self.assertNotEqual(a['cc11'], b['cc11'])
        # The falling articulation is directional, not a lower replacement note.
        ending=[cents for t,cents in b['bends'] if 1.95<t<2]
        self.assertTrue(ending and min(ending)<-35)
        self.assertIn('expression_policy',mapping)

    def test_paired_length_marks_redistribute_one_cell_and_keep_annotation(self):
        decorated, mapping=converted(document('청황{덧길이표} 청태{반길이표} | 임 -{원문점}{노}'))
        self.assertEqual([Fraction(n['duration']) for n in decorated['notes'][:2]],
                         [Fraction(9,8),Fraction(3,8)])
        self.assertEqual(sum(Fraction(n['duration']) for n in decorated['notes']),3)
        locations=[p for locations in mapping['notes'].values() for p in locations]
        self.assertTrue(any('원문점' in p['symbols'] for p in locations))
        with self.assertRaisesRegex(ValueError,'길이'):
            converted(document('청황{덧길이표} 청태 | 임'))

    def test_surrounding_graces_and_independent_three_note_figure_keep_time(self):
        score, _=converted(document('청황{느니르} | {니로나}'))
        self.assertEqual([n['pitch'] for n in score['notes']], [73,75,73,77,75,73])
        self.assertEqual([Fraction(n['duration']) for n in score['notes'][:3]],
                         [Fraction(1,16),Fraction(11,8),Fraction(1,16)])
        self.assertEqual(sum(Fraction(n['duration']) for n in score['notes']),3)

    def test_official_twelve_attached_figures(self):
        # National Gugak Center's 12 figures, long middle anchored at MIDI 75.
        cases = {
            "니레": [77, 75], "니라": [80, 75], "노네": [73, 75], "너녜": [70, 75],
            "노니로": [75, 77, 75], "네로네": [75, 73, 75], "나니나": [75, 77, 75],
            "나느나": [75, 73, 75], "느니르": [73, 75, 73], "나니로": [73, 77, 75],
            "로니로": [70, 77, 75], "느로니르": [75, 73, 77, 75],
        }
        for name, pitches in cases.items():
            with self.subTest(name=name):
                score, mapping = converted(document(f"청황{{{name}}} | 청태"))
                self.assertEqual([n["pitch"] for n in score["notes"][:-1]], pitches)
                self.assertEqual(sum(Fraction(n["duration"]) for n in score["notes"][:-1]), Fraction(3, 2))
                self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 1, places=5)
                self.assertEqual(mapping["written_total_quarters"], "3")
        quick, _ = converted(document("청황{노니로} | 청태"))
        equal, _ = converted(document("청황{나니나} | 청태"))
        self.assertEqual([Fraction(n["duration"]) for n in quick["notes"][:2]], [Fraction(1, 16)] * 2)
        self.assertEqual([Fraction(n["duration"]) for n in equal["notes"][:3]], [Fraction(1, 2)] * 3)

    def test_official_twelve_substitute_figures(self):
        cases = {
            "노": [73], "로": [70], "니": [77], "리": [80], "니나": [77, 75],
            "느니": [77, 80], "노라": [73, 70], "니로나": [77, 75, 73],
            "니레나": [80, 77, 75], "느나르나니": [77, 80, 77, 75, 80],
            "니느라니": [77, 75, 77, 75], "느나니나": [73, 75, 77, 75],
        }
        for name, pitches in cases.items():
            with self.subTest(name=name):
                score, mapping = converted(document(f"청황 | {{{name}}}"))
                self.assertEqual([n["pitch"] for n in score["notes"][1:]], pitches)
                self.assertEqual([Fraction(n["duration"]) for n in score["notes"][1:]],
                                 [Fraction(3, 2 * len(pitches))] * len(pitches))
                self.assertIn("nineurani", mapping["symbol_policy"]["upstream_differences"])
                self.assertEqual(len(midi(score)["notes"]), len(pitches) + 1)

    def test_new_aliases_produce_identical_midi(self):
        pairs = [("나니로", "naniro", True), ("나니르", "naniro", True),
                 ("느로니르", "neuronireu", True), ("니루-니", "niruni", True),
                 ("니레나", "nirena", False), ("니느라니", "nineurani", False),
                 ("느나니나", "neunanina", False), ("느니르나니", "neunareunani", False),
                 ("풀어내리는표", "vib-long", True), ("미는표", "push", True),
                 ("끊는표", "staccato", True), ("늘임표", "fermata", True)]
        for a, b, attached in pairs:
            with self.subTest(a=a):
                template = "청황{%s} | 청태" if attached else "청황 | {%s}"
                self.assertEqual(midi(converted(document(template % a))[0]),
                                 midi(converted(document(template % b))[0]))
        a, _ = converted(document("청황{덧길이표} 청태{반길이표} | 청황"))
        b, _ = converted(document("청황{len-double} 청태{len-half} | 청황"))
        self.assertEqual(midi(a), midi(b))

    def test_post_grace_moves_after_sustained_main_and_preserves_reference(self):
        doc = document("청황{느니르} | - | {니}", beats=3)
        original = deepcopy(doc)
        score, mapping = converted(doc)
        self.assertEqual(doc, original)
        self.assertEqual([n["pitch"] for n in score["notes"]], [73, 75, 73, 77])
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]],
                         [Fraction(1, 16), Fraction(23, 8), Fraction(1, 16), Fraction(3, 2)])
        self.assertEqual([p["cell"] for p in mapping["notes"][score["notes"][1]["id"]]], [1, 2])
        self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 2, places=5)
        score, _ = converted(document("청황{느니르} | -{농음표} | 청태", beats=3))
        self.assertTrue(all(v == 1 for t, v in midi(score)["cc11"] if t < 1))
        self.assertTrue(any(v < 1 for t, v in midi(score)["cc11"] if 1 < t < 1.9))
        with self.assertRaisesRegex(ValueError, "숨표"):
            converted(document("청황{느니르}< | -"))

    def test_post_grace_never_crosses_rest_or_gak(self):
        for text in ("청황{느니르} | 쉼", "청황{느니르} | 청태\n- | 청태"):
            score, _ = converted(document(text))
            self.assertEqual(Fraction(score["notes"][2]["duration"]), Fraction(1, 16))

    def test_unwind_is_distinct_pitch_vibrato_plus_fall_and_push_is_upward(self):
        plain, _ = converted(document("청황 | 청태"))
        decorated, mapping = converted(document("청황{풀어내림표} | 청태{미는표}"))
        a, b = midi(plain), midi(decorated)
        self.assertEqual(a["notes"], b["notes"])
        self.assertEqual(decorated["notes"][0]["yoseong"]["mode"], "pitch")
        self.assertTrue(any(v < -35 for t, v in b["bends"] if .95 < t < 1))
        self.assertTrue(any(v > 35 for t, v in b["bends"] if 1.95 < t < 2))
        fall = midi(converted(document("청황{흘림표} | 청태"))[0])
        self.assertNotEqual(b["bends"], fall["bends"])
        self.assertEqual(mapping["expression_policy"]["status"], "audition-policy-not-performance-standard")

    def test_unwind_on_hold_does_not_bend_before_mark(self):
        score, _ = converted(document("청황 | -{풀어내림표} | 청태", beats=3))
        plain, _ = converted(document("청황 | - | 청태", beats=3))
        # The separate automatic tremble is disabled when an explicit pitch
        # yoseong exists; the marked effect itself must not begin before 1s.
        self.assertTrue(all(abs(v) < .001 for t, v in midi(score)["bends"] if t < 1))
        self.assertEqual(midi(score)["notes"], midi(plain)["notes"])

    def test_known_unresolved_figures_are_not_silently_skipped(self):
        for mark in ("격음표", "겹흘림표", "겹미는표", "요성표", "겹요성표", "하성", "느니라", "노라느니", "반복표"):
            with self.subTest(mark=mark), self.assertRaisesRegex(ValueError, "1각 · 1정간.*미확정"):
                converted(document(f"청황{{{mark}}} | 청태"))
        for jo in ("hwang-pyeong", "jung-pyeong", "hwang-gyemyeon", ""):
            score, _ = converted(document("청임{떠이어표} | 청태", joPreset=jo))
            self.assertEqual([n["pitch"] for n in score["notes"][:3]], [82, 85, 82])
        with self.assertRaisesRegex(ValueError, "교육 예시"):
            converted(document("청황{떠이어표} | 청태"))

    def test_ambiguous_combinations_and_disabled_effects_fail(self):
        for text in ("청황{풀어내림표}{농음표} | 청태", "청황{흘림표}{미는표} | 청태",
                     "청황{흘림표} | -{풀어내림표}", "청황{끊는표} | -",
                     "청황{니레}{끊는표} | 청태", "쉼{풀어내림표} | 청태"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                converted(document(text))
        for mark in ("풀어내림표", "미는표", "끊는표", "늘임표"):
            with self.subTest(mark=mark), self.assertRaisesRegex(ValueError, "꺼져"):
                converted(document(f"청황{{{mark}}} | 청태", playSigimsae=False))
        score, _ = converted(document())
        for gate in (0, -1, 2, True, float("nan")):
            score["notes"][0]["gate_fraction"] = gate
            with self.subTest(gate=gate), self.assertRaises(ValueError):
                compile_score(score, "danso")
        score["notes"][0].update(gate_fraction=.5, tie="start")
        with self.assertRaisesRegex(ValueError, "gate and tie"):
            compile_score(score, "danso")

    def test_tempo_requires_explicit_playback_selection_not_a_number_or_jangdan(self):
        for version in (1, 2):
            for flag in (None, False):
                doc = document(version=version)
                doc["controls"].update(tempoBpmUserSet=True, tempoBpmGak="60", jangdan="굿거리")
                doc.pop("tempoBpmUserSet")
                if flag is not None:
                    doc["tempoBpmUserSet"] = flag
                with self.subTest(version=version, flag=flag):
                    with self.assertRaisesRegex(ValueError, "빠르기 선택"):
                        converted(doc, speed=.5)
                    score, mapping = converted(doc, bpm=60)
                    self.assertEqual(mapping["tempo_selection"]["kind"], "cli-override")
                    self.assertEqual(mapping["tempo_selection"]["musicological_basis"], "not-verified")
                    self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 1, places=5)
        for flag in ("true", 1, [], {}):
            doc = document()
            doc["tempoBpmUserSet"] = flag
            with self.subTest(flag=flag), self.assertRaisesRegex(ValueError, "true/false"):
                converted(doc)


if __name__ == "__main__":
    unittest.main()
