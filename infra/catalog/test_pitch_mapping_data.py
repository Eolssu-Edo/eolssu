"""Check curated mappings against committed annotations, not a name-only generated join."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAPPINGS = ROOT / "infra/catalog/pitch-mappings"


def annotations(dataset):
    result = {}
    for path in (ROOT / "data/annotations/audio" / dataset).glob("*/labels.json"):
        document = json.loads(path.read_text(encoding="utf-8"))
        for group in document["groups"]:
            for values in group["rows"]:
                row = dict(zip(document["columns"], values))
                result[row["id"]] = (row, group, document)
    return result


class CuratedPitchMappingTests(unittest.TestCase):
    def mapping(self, dataset):
        return json.loads((MAPPINGS / f"{dataset}-basic-v1.json").read_text(encoding="utf-8"))

    def test_all_four_mapping_files_cover_219_unique_existing_pitch_targets(self):
        rows = [row for path in MAPPINGS.glob("*-basic-v1.json")
                for row in json.loads(path.read_text(encoding="utf-8"))["mappings"]]
        self.assertEqual(Counter({"danso": 30, "jeongak-daegeum": 54, "sogeum": 90, "sanjo-daegeum": 45}),
                         Counter(row["datasetId"] for row in rows))
        self.assertEqual(219, len({row["assetId"] for row in rows}))
        pitches = json.loads((ROOT / "data/reference/pitches.json").read_text(encoding="utf-8"))["rows"]
        available = {(pitch["yul"], pitch["octave"]) for pitch in pitches} | {
            ("임", -1), ("남", -1), ("무", -1), ("황", 0), ("태", 0), ("고", 0),
            ("남", 0), ("고", 1), ("남", 1), ("고", 2), ("임", 2)}
        for row in rows:
            self.assertIn((row["yul"], row["octave"]), available)
            self.assertEqual(hashlib.sha256(f"{row['datasetId']}\n{row['labelId']}".encode()).hexdigest(), row["assetId"])

    def test_added_mappings_preserve_actual_annotation_identity_and_group_basis(self):
        for dataset in ("jeongak-daegeum", "sogeum", "sanjo-daegeum"):
            document = self.mapping(dataset)
            source_rows = annotations(dataset)
            self.assertIn(f"docs/knowledge/data/{dataset}.md", document["basis"])
            self.assertIn("groups.analysisBasis", document["basis"])
            self.assertIn("not", document["basis"])
            for mapping in document["mappings"]:
                row, group, source = source_rows[mapping["labelId"]]
                self.assertEqual("기본음계", group["category"])
                self.assertEqual(group["category"], mapping["category"])
                self.assertEqual(row["register"], mapping["register"])
                self.assertEqual(row["yulmyeong"], mapping["yulmyeong"])
                self.assertEqual("inferred", source["review"])
                self.assertFalse(source["eligibleForNoteScoring"])
                self.assertTrue(group["analysisBasis"])
                self.assertFalse(group.get("evidence", {}).get(row["id"], {}).get("alternatives"))

    def test_jeongak_tone_order_and_sogeum_registers_have_exact_existing_target_coverage(self):
        # Independent annotation landmarks: the three jeongak sources share an 18-tone sequence.
        landmarks = {1: ("임", -1), 2: ("남", -1), 3: ("무", -1), 4: ("황", 0), 5: ("태", 0), 6: ("고", 0),
                     9: ("남", 0), 13: ("고", 1), 16: ("남", 1), 7: ("중", 0), 8: ("임", 0), 10: ("무", 0), 11: ("황", 1), 12: ("태", 1),
                     14: ("중", 1), 15: ("임", 1), 17: ("무", 1), 18: ("황", 2)}
        expected = {f"ngc-jeongak-{source}.tone-{number:03}": target
                    for source in (2557, 2558, 2559) for number, target in landmarks.items()}
        self.assertEqual(expected, {row["labelId"]: (row["yul"], row["octave"])
                                    for row in self.mapping("jeongak-daegeum")["mappings"]})
        # Sogeum's pyeongchwi starts at im0; hwang/tae/jung cross the octave boundary.
        targets = {("평취", "남"): ("남", 0), ("평취", "고"): ("고", 1),
                   ("역취", "남"): ("남", 1), ("역취", "고"): ("고", 2), ("역취", "중청임"): ("임", 2),
                   ("평취", "임"): ("임", 0), ("평취", "무"): ("무", 0),
                   ("평취", "황"): ("황", 1), ("평취", "태"): ("태", 1), ("평취", "중"): ("중", 1),
                   ("역취", "임"): ("임", 1), ("역취", "무"): ("무", 1),
                   ("역취", "황"): ("황", 2), ("역취", "태"): ("태", 2), ("역취", "중"): ("중", 2)}
        expected = {key: targets[row["register"], row["yulmyeong"]] for key, (row, group, _) in annotations("sogeum").items()
                    if group["category"] == "기본음계" and (row["register"], row["yulmyeong"]) in targets}
        self.assertEqual(90, len(expected))
        self.assertEqual(expected, {row["labelId"]: (row["yul"], row["octave"])
                                    for row in self.mapping("sogeum")["mappings"]})

    def test_sanjo_uses_nominal_notation_octaves_and_preserves_individual_ambiguity(self):
        document = self.mapping("sanjo-daegeum")
        self.assertIn("https://www.gugak.go.kr/ency/topic/view/108", document["basis"])
        self.assertIn("2026-11-02", document["basis"])
        self.assertIn("㑣", document["basis"])
        counts = Counter()
        for mapping in document["mappings"]:
            row, group, _ = annotations("sanjo-daegeum")[mapping["labelId"]]
            counts[group["sourceId"]] += 1
            # 저취 임−1→황0 boundary, then one octave per register, anchored by notation rather than Hz.
            base = -1 if row["yulmyeong"] in ("임", "남", "무") else 0
            expected_octave = base + {"저취": 0, "평취": 1, "역취": 2}[row["register"]]
            self.assertEqual((row["yulmyeong"], expected_octave), (mapping["yul"], mapping["octave"]))
        self.assertEqual(Counter({"ngc-sanjo-2550": 13, "ngc-sanjo-2551": 18, "ngc-sanjo-2552": 14}), counts)
        ambiguous = annotations("sanjo-daegeum")["ngc-sanjo-2552.region-004"]
        self.assertTrue(ambiguous[1]["evidence"][ambiguous[0]["id"]]["alternatives"])
        self.assertNotIn(ambiguous[0]["id"], {row["labelId"] for row in document["mappings"]})

    def test_unlinked_rows_keep_only_207_ornaments_and_four_ambiguities(self):
        mapped = {row["labelId"] for path in MAPPINGS.glob("*-basic-v1.json")
                  for row in json.loads(path.read_text(encoding="utf-8"))["mappings"]}
        ambiguities = {f"ngc-sogeum-{source}.group-006" for source in (2654, 2655, 2656)} | {"ngc-sanjo-2552.region-004"}
        self.assertFalse(mapped & ambiguities)
        ornaments, missing = 0, Counter()
        for dataset in ("danso", "jeongak-daegeum", "sogeum", "sanjo-daegeum"):
            for key, (_, group, _) in annotations(dataset).items():
                if key in mapped:
                    continue
                if group["category"] == "시김새":
                    ornaments += 1
                elif key in ambiguities:
                    self.assertTrue(group["evidence"][key]["alternatives"])
                else:
                    missing[dataset] += 1
        self.assertEqual(207, ornaments)
        self.assertEqual(Counter(), missing)


if __name__ == "__main__":
    unittest.main()
