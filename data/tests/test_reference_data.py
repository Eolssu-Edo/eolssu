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
        # 율명 표는 단소 음역이라 서비스 곡(songs.json)의 정답만 본다. 평가용 소금 정답은 고·남 등을 쓴다.
        sources = {row["source"] for row in load("songs.json")["rows"]}
        answers = sorted(p for p in (DATA / "annotations/scores").glob("*/*.jgb.json") if p.parent.name in sources)
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

    def test_sogeum_recordings_sound_at_rule_octave(self):
        """소금 기본음계 녹음이 반음 = 옥타브 × 12 + 율 순서(황 0 = E♭4) 규칙의 옥타브에서 난다(반음 안).
        소금은 평취 임이 가장 낮아 황~유가 청이고, 역취는 한 옥타브 위, '중청임'은 라벨에 옥타브가 있다.
        국악사전 「소금」은 최고음 㵜(중청남)을 C6로 적었지만 이 규칙으로 C7이고 녹음이 C7 쪽과 맞다(S15P31E102-387).
        이 악기는 E♭4 기준보다 50~85센트 높게 조율돼 있어 단소처럼 ±50센트가 아니라 옥타브만 가린다.
        소금 율명 행은 기준 데이터가 아니라 V13·S15P31E102-339 적재로 DB에 있다."""
        checked = 0
        for path in (DATA / "annotations/audio/sogeum").glob("*기본음*/labels.json"):
            labels = json.loads(path.read_text(encoding="utf-8"))
            column = {name: i for i, name in enumerate(labels["columns"])}
            for group in labels["groups"]:
                for r in group["rows"]:
                    yul, register = r[column["yulmyeong"]], r[column["register"]]
                    base = yul.removeprefix("중청")
                    if base not in "임남황태고":  # 소금 곡의 다섯 율만 본다. 녹음의 중·무·협은 반음 넘게 높다
                        continue
                    octave = 2 if yul != base else (YULS.index(base) < YULS.index("임")) + (register == "역취")
                    target = 311.13 * 2 ** ((octave * 12 + YULS.index(base)) / 12)
                    cents = 1200 * math.log2(r[column["labelF0Hz"]] / target)
                    self.assertLess(abs(cents), 100, f"{register} {yul}: {cents:+.1f}센트")
                    checked += 1
        self.assertEqual(checked, 33)  # 녹음 3개 × 11음(평취 임·남·황·태·고, 역취 같은 5음, 중청임)


class JangdanTableTests(unittest.TestCase):
    # 기존3종은 기획141, 추가2종은 Task411에서 확인한 공식 기본형의 시작값.
    EXPECTED = {"세마치": (96, "DOTTED_QUARTER", 9), "굿거리": (66, "DOTTED_QUARTER", 12),
                "중모리": (88, "QUARTER", 12), "자진모리": (96, "DOTTED_QUARTER", 12),
                "중중모리": (80, "DOTTED_QUARTER", 12)}

    def test_jangdans_match_planning_tempo_and_unit(self):
        rows = {r["name"]: r for r in load("jangdans.json")["rows"]}
        self.assertEqual(set(rows), set(self.EXPECTED))
        for name, (bpm, unit, _) in self.EXPECTED.items():
            row = rows[name]
            self.assertEqual(set(row), {"name", "pattern", "default_bpm", "bpm_unit", "source"})
            self.assertEqual((row["default_bpm"], row["bpm_unit"]), (bpm, unit), name)
            self.assertTrue(row["source"], name)

    def test_pattern_length_and_groups_cover_one_cycle(self):
        for row in load("jangdans.json")["rows"]:
            pattern = row["pattern"]
            length = self.EXPECTED[row["name"]][2]
            self.assertEqual(len(pattern["beats"]), length, row["name"])
            self.assertEqual(sum(pattern["group"]), length, row["name"])
            self.assertTrue(pattern["beats"][0], row["name"])  # 첫 박은 합장단으로 시작한다


class SongTableTests(unittest.TestCase):
    def test_songs_point_at_jangdans_and_reviewed_answers(self):
        jangdans = {r["name"]: r for r in load("jangdans.json")["rows"]}
        rows = load("songs.json")["rows"]
        self.assertEqual({r["title"] for r in rows}, {"아리랑", "도라지타령", "밀양아리랑", "늴리리야"})
        for row in rows:
            self.assertEqual(set(row), {"title", "instrument", "jangdan", "difficulty", "default_bpm",
                                        "description", "source", "is_scorable", "status"})
            self.assertEqual(row["instrument"], "DANSO")
            self.assertEqual(row["default_bpm"], jangdans[row["jangdan"]]["default_bpm"], row["title"])
            self.assertIn(row["status"], {"DRAFT", "PUBLISHED", "HIDDEN"})
            self.assertIn(row["difficulty"], range(1, 6), row["title"])  # songs.difficulty NOT NULL, 1~5
            # 채점 가능 곡은 사람이 확정한 정답 jgb가 annotations에 있어야 한다
            answers = list((DATA / "annotations/scores" / row["source"]).glob("*.jgb.json"))
            self.assertEqual(len(answers) == 1, row["is_scorable"], row["title"])


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
