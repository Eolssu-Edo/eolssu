import unittest

from fetch_song_source import case_names, json_select


class FetchSongSourceTests(unittest.TestCase):
    def test_json_select_names_every_column(self):
        self.assertEqual(json_select("songs", ["id", "title"], "id=93"),
                         "SELECT JSON_OBJECT('id',`id`,'title',`title`) FROM songs WHERE id=93;")

    def test_case_names_come_from_release_case_folders_only(self):
        sha = "f" * 64
        keys = [f"model-audio/drafts/{sha}/cases/sogeum-005-p1/sogeum.m4a",
                f"model-audio/drafts/{sha}/cases/sogeum-005-p1/performance.common.json",
                f"model-audio/drafts/{sha}/release.json", "recordings/x.m4a"]
        self.assertEqual(case_names(keys), ["sogeum-005-p1"])


if __name__ == "__main__":
    unittest.main()
