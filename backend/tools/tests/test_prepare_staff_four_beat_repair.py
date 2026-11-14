from copy import deepcopy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_staff_four_beat_repair import prepare


def fixture():
    source = {"scoreSha256": "a" * 64, "notes": [{"eventId": "n1", "midi": 72}],
              "score": {"music": {"bpmUnit": "DOTTED_QUARTER", "ticksPerBeat": 18,
                                   "ticksPerJeonggan": 12}}}
    score = {"music": {"bpmUnit": "DOTTED_QUARTER", "ticksPerBeat": 18,
                       "ticksPerJeonggan": 12, "jangdan": ""},
             "provenance": {"converter": "staff-omr/audiveris-5.11.0"},
             "columns": [{"cells": [{"id": f"c0-j{i}", "units": []} for i in range(4)]}],
             "events": [{"id": "n1", "cellId": "c0-j0", "startTick": 0, "durationTick": 12,
                         "kind": "NOTE", "yul": "황", "octave": 1}]}
    authoring = {"source": source, "adaptation": {"instrument": "SOGEUM", "transpose": 6,
                                                 "changes": [{"eventId": "n1", "midi": 78}]}}
    snapshot = {"sheetId": 32, "userId": 6, "source": "STAFF_OMR", "scoreVersion": 1,
                "draftRevision": 6, "draftBaseVersion": 1, "draftStorageSha256": "b" * 64,
                "expressions": None, "authoring": None,
                "recognition": {"authoring": {"source": deepcopy(source)}},
                "draft": {"title": "Edited score", "bpm": 66, "jangdanId": None,
                          "score": score, "expressions": None, "authoring": authoring}}
    target = {"jangdanId": "12", "name": "4박 기본 반주", "bpmUnit": "QUARTER", "defaultBpm": 160,
              "beatCount": 4, "patternCellEighths": 2}
    return snapshot, target


class StaffFourBeatRepairTests(unittest.TestCase):
    def run_prepare(self, snapshot, target):
        return prepare(snapshot, target, user_id=6, sheet_id=32, revision=6)

    def test_repairs_only_the_five_reviewed_fields_and_keeps_edited_notes_and_source(self):
        snapshot, target = fixture()
        before = deepcopy(snapshot)
        result = self.run_prepare(snapshot, target)
        draft = result["draft"]
        self.assertEqual((draft["bpm"], draft["jangdanId"]), (160, "12"))
        self.assertEqual(draft["score"]["music"], {"bpmUnit": "QUARTER", "ticksPerBeat": 12,
                         "ticksPerJeonggan": 12, "jangdan": "4박 기본 반주"})
        self.assertEqual(draft["score"]["events"], before["draft"]["score"]["events"])
        self.assertEqual(draft["score"]["columns"], before["draft"]["score"]["columns"])
        self.assertEqual(draft["authoring"], before["draft"]["authoring"])
        self.assertEqual(snapshot, before)
        self.assertEqual(result["expectedDraftRevision"], 6)
        self.assertEqual(result["expectedScoreVersion"], 1)
        self.assertEqual(result["expectedStorageSha256"], "b" * 64)

    def test_rejects_a_different_account_score_or_revision(self):
        for field, value in [("userId", 7), ("sheetId", 33), ("draftRevision", 7), ("draftBaseVersion", 2)]:
            with self.subTest(field=field):
                snapshot, target = fixture()
                snapshot[field] = value
                with self.assertRaises(ValueError):
                    self.run_prepare(snapshot, target)

    def test_rejects_a_tempo_plan_on_either_saved_score_or_draft(self):
        for place in ["saved", "draft"]:
            with self.subTest(place=place):
                snapshot, target = fixture()
                if place == "saved":
                    snapshot["expressions"] = {"performancePlan": {}}
                else:
                    snapshot["draft"]["expressions"] = {"performancePlan": {}}
                with self.assertRaises(ValueError):
                    self.run_prepare(snapshot, target)

    def test_rejects_user_tempo_edits_unknown_timing_or_incomplete_cycles(self):
        for change in ["bpm", "unit", "tick", "cells", "source", "converter"]:
            with self.subTest(change=change):
                snapshot, target = fixture()
                draft = snapshot["draft"]
                if change == "bpm": draft["bpm"] = 90
                elif change == "unit": draft["score"]["music"]["bpmUnit"] = "QUARTER"
                elif change == "tick": draft["score"]["music"]["ticksPerBeat"] = 24
                elif change == "cells": draft["score"]["columns"][0]["cells"].pop()
                elif change == "source": snapshot["source"] = "JEONGGANBO_OMR"
                else: draft["score"]["provenance"]["converter"] = "app-editor-1"
                with self.assertRaises(ValueError): self.run_prepare(snapshot, target)

    def test_rejects_a_changed_original_or_catalog(self):
        for change in ["source", "bpmUnit", "patternCellEighths", "defaultBpm", "jangdanId"]:
            with self.subTest(change=change):
                snapshot, target = fixture()
                if change == "source": snapshot["draft"]["authoring"]["source"]["notes"][0]["midi"] = 73
                else: target[change] = None
                with self.assertRaises(ValueError): self.run_prepare(snapshot, target)

    def test_rejects_reapplying_to_the_repaired_draft(self):
        snapshot, target = fixture()
        snapshot["draft"] = self.run_prepare(snapshot, target)["draft"]
        with self.assertRaises(ValueError): self.run_prepare(snapshot, target)


if __name__ == "__main__":
    unittest.main()
