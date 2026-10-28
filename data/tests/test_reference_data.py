import json
import math
from pathlib import Path
import re
import unittest

DATA = Path(__file__).resolve().parents[1]
REFERENCE = DATA / "reference"
YULS = "황대태협고중유임이남무응"
# 옥타브 번호와 율명 앞말(backend V1 pitches.octave: -1 배, 0 중, 1 청, 2 중청)
PREFIX = {-1: "배", 0: "", 1: "청", 2: "중청"}


def load(name):
    return json.loads((REFERENCE / name).read_text(encoding="utf-8"))


def umulsai_hanja():
    """검토 화면이 옮겨 둔 우물사이 옥타브 한자표(review_page.html HANJA)."""
    text = (DATA / "src/scores/review_page.html").read_text(encoding="utf-8")
    body = re.search(r"const HANJA = \{(.*?)\};", text, re.S).group(1)
    table = {"": list("黃大太夾姑仲蕤林夷南無應")}
    for prefix, items in re.findall(r'"(중청|청|배)": \[([^\]]*)\]', body):
        table[prefix] = re.findall(r'"([^"]*)"', items)
    return table


class PitchTableTests(unittest.TestCase):
    def setUp(self):
        self.rows = load("pitches.json")["rows"]

    def test_rows_follow_backend_columns_and_keys(self):
        self.assertTrue(self.rows)
        for row in self.rows:
            self.assertEqual(set(row), {"yul", "octave", "hanja", "hangul", "semitone"})
            self.assertIn(row["yul"], YULS)
            self.assertIn(row["octave"], PREFIX)
        self.assertEqual(len({(r["yul"], r["octave"]) for r in self.rows}), len(self.rows))
        self.assertEqual(len({r["semitone"] for r in self.rows}), len(self.rows))

    def test_semitone_hangul_and_hanja_follow_yul_and_octave(self):
        hanja = umulsai_hanja()
        for row in self.rows:
            index = YULS.index(row["yul"])
            self.assertEqual(row["semitone"], row["octave"] * 12 + index, row)
            self.assertEqual(row["hangul"], PREFIX[row["octave"]] + row["yul"], row)
            self.assertEqual(row["hanja"], hanja[PREFIX[row["octave"]]][index], row)

    def test_every_pitch_in_scoring_answers_is_listed(self):
        names = {r["hangul"] for r in self.rows}
        yul = r"(?:하하배|하배|중청|배|청)?[황대태협고중유임이남무응]"
        answers = sorted((DATA / "annotations/scores").glob("*/*.jgb.json"))
        self.assertTrue(answers)
        for path in answers:
            melody = json.loads(path.read_text(encoding="utf-8"))["parts"][0]["melody"]
            self.assertLessEqual(set(re.findall(yul, melody)), names, path.name)


class PitchFrequencyTests(unittest.TestCase):
    def test_target_frequency_is_within_50_cents_of_danso_recordings(self):
        """황=E♭4(311.13Hz) 기준 목표 주파수와 단소 기본음계 녹음의 대표 F0를 비교한다.
        녹음 라벨의 율명에는 옥타브가 없다. 평취는 중이 가장 낮으므로 황·태가 한 옥타브 위(청)이고,
        역취는 평취보다 한 옥타브 위다."""
        pitches = {(r["yul"], r["octave"]): r["semitone"] for r in load("pitches.json")["rows"]}
        measured = []
        for path in (DATA / "annotations/audio/danso").glob("*/labels.json"):
            labels = json.loads(path.read_text(encoding="utf-8"))
            column = {name: i for i, name in enumerate(labels["columns"])}
            for group in labels["groups"]:
                if group["category"] == "기본음계":
                    measured += [(r[column["register"]], r[column["yulmyeong"]], r[column["labelF0Hz"]])
                                 for r in group["rows"]]
        self.assertEqual(len(measured), 30)
        for register, yul, hz in measured:
            octave = (yul in "황태") + (register == "역취")
            target = 311.13 * 2 ** (pitches[(yul, octave)] / 12)
            cents = 1200 * math.log2(hz / target)
            self.assertLessEqual(abs(cents), 50, f"{register} {yul} {hz}Hz: {cents:+.1f}센트")


class InstrumentTableTests(unittest.TestCase):
    def test_danso_range_points_at_listed_pitches(self):
        pitches = {(r["yul"], r["octave"]): r for r in load("pitches.json")["rows"]}
        (danso,) = [r for r in load("instruments.json")["rows"] if r["code"] == "DANSO"]
        low, high = (pitches[(p["yul"], p["octave"])] for p in (danso["range_low"], danso["range_high"]))
        self.assertLess(low["semitone"], high["semitone"])
        self.assertTrue(0 <= danso["pitch_tolerance_cent"] <= 100)
        inside = [r for r in pitches.values() if low["semitone"] <= r["semitone"] <= high["semitone"]]
        self.assertEqual(len(inside), len(pitches))


if __name__ == "__main__":
    unittest.main()
