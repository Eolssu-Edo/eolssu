import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import publish_model_audio as publication


def fixture():
    source = "mr203-fixed-danso-001-p1"
    row = dict(source=source, title="연습곡", description=None, instrument_code="DANSO", jangdan_name=None,
               difficulty=2, default_bpm=90, score_json={"score": {"sourceId": source}}, score_version=1, note_count=1,
               object_key="model-audio/drafts/" + "a" * 64 + "/cases/danso-001-p1/danso.m4a",
               performance_object_key="model-audio/drafts/" + "a" * 64 + "/cases/danso-001-p1/performance.common.json",
               source_common_sha256="c" * 64, generation_metadata={"caseId": "danso-001-p1", "rights": {"serviceDatasetApproved": False}},
               bpm_unit="QUARTER", duration_ms=1234, start_offset_ms=0, content_type="audio/mp4", sha256="b" * 64)
    model = {"manifestSha256": "a" * 64, "difficultySha256": "d" * 64, "referencePlanSha256": "e" * 64,
             "rows": [row], "references": {"pitches": [], "instruments": [], "jangdans": []}}
    song = {k: row[k] for k in publication.loader.SONG_FIELDS}
    song.update(id=77, jangdan_id=None, umulsai_text=None, is_scorable=0, status="DRAFT", deleted_at=None)
    audio = {k: row[k] for k in publication.loader.AUDIO_FIELDS}
    audio.update(song_id=77, bpm=90, status="DRAFT")
    return model, {"pitches": [], "instruments": [], "jangdans": [], "songs": [song], "audios": [audio]}


class PublicationTests(unittest.TestCase):
    def test_selection_is_explicit_exact_identity_and_never_a_title(self):
        model, _ = fixture()
        self.assertEqual(publication.select(model, all_cases=True)["rows"], model["rows"])
        self.assertEqual(publication.select(model, cases=["danso-001-p1"])["rows"], model["rows"])
        for args in ({}, {"all_cases": True, "cases": ["danso-001-p1"]}, {"cases": ["연습곡"]}, {"cases": ["danso-001-p1"] * 2}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                publication.select(model, **args)

    def test_publish_accepts_exact_draft_and_idempotent_target_but_rejects_changed_payload(self):
        model, inventory = fixture()
        self.assertEqual(publication.verify(model, inventory, "publish", "DRAFT")["wouldChange"], 1)
        inventory["songs"][0]["status"] = inventory["audios"][0]["status"] = "PUBLISHED"
        self.assertEqual(publication.verify(model, inventory, "publish", "DRAFT")["wouldChange"], 0)
        for key, value in (("object_key", "recordings/private.m4a"), ("source_common_sha256", "f" * 64),
                           ("sha256", "f" * 64), ("generation_metadata", {})):
            changed = copy.deepcopy(inventory)
            changed["audios"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                publication.verify(model, changed, "publish", "DRAFT")

    def test_hide_does_not_depend_on_other_catalog_reference_state_or_edited_display_metadata(self):
        model, inventory = fixture()
        inventory["songs"][0].update(status="PUBLISHED", title="수정한 제목", description="설명", difficulty=3)
        inventory["audios"][0]["status"] = "PUBLISHED"
        inventory["instruments"] = [{"code": "SOGEUM", "isActive": True}]
        result = publication.verify(model, inventory, "hide", "PUBLISHED")
        self.assertEqual(result["wouldChange"], 1)
        self.assertEqual(result["songIds"], {model["rows"][0]["source"]: 77})
        inventory["audios"][0]["sha256"] = "f" * 64
        with self.assertRaises(ValueError):
            publication.verify(model, inventory, "hide", "PUBLISHED")

    def test_mismatched_status_pair_missing_or_duplicate_target_never_writes(self):
        model, original = fixture()
        for kind in ("status", "missing", "duplicate", "version"):
            inventory = copy.deepcopy(original)
            if kind == "status": inventory["audios"][0]["status"] = "PUBLISHED"
            elif kind == "missing": inventory["songs"] = []
            elif kind == "duplicate": inventory["songs"].append(copy.deepcopy(inventory["songs"][0]))
            else: inventory["songs"][0]["score_version"] = 2
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                publication.verify(model, inventory, "publish", "DRAFT")

    def test_publish_apply_needs_deployed_app_readiness_but_hide_does_not(self):
        model, inventory = fixture()
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(publication.loader, "inventory", return_value=inventory), self.assertRaises(ValueError):
                publication.run(model, Path(directory) / "refused.json", action="publish", from_status="DRAFT", apply=True)
            self.assertFalse((Path(directory) / "refused.json").exists())
            result = publication.run(model, Path(directory) / "check.json", action="publish", from_status="DRAFT",
                                     runner=lambda _: "\n".join(json.dumps({k: v}) for k, v in inventory.items()))
            self.assertEqual(result["status"], "validated-dry-run")
            self.assertEqual(result["wouldChange"], 1)


if __name__ == "__main__":
    unittest.main()
