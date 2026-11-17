from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/scores"))
from sigimsae_eval import answer_cells, compare


class SigimsaeEvalTest(unittest.TestCase):
    def test_counts_names_places_extras_and_misses(self):
        answers = ["청태{풀어내림표} - 청임", "무 청황{니레} -", "임 - 무{니나}", "청태"]
        cells = [{"jg": ":2 청태 풀어내림표 :5 - :8 청임"},  # 같은 자리
                 {"jg": ":2 무 니레 :5 청황 :8 -"},  # 이름은 맞고 자리가 다름
                 {"jg": ":2 임 :5 - :8 무 니나"},  # 모델의 붙임 니나(우물사이 니라)도 정답 니나로 본다
                 {"jg": ":0 청태 흘림표"}]
        total, pairs = compare(answers, cells)
        self.assertEqual({k: total[k] for k in ("answer", "hit", "place", "extra", "missed")},
                         {"answer": 3, "hit": 3, "place": 2, "extra": 1, "missed": 0})
        self.assertEqual(pairs[("-", "흘림표")], 1)
        self.assertEqual(pairs[("니레", "-")], 1)

    def test_different_cell_counts_are_not_compared(self):
        total, pairs = compare(["청태{니레}"], [])
        self.assertEqual((total["answer"], total["hit"], pairs), (1, 0, {}))

    def test_reads_committed_answer(self):
        cells = answer_cells("ngc-silgi-2016-18875")
        self.assertEqual(len(cells), 32)
        self.assertIn("{풀어내림표}", cells[4])


if __name__ == "__main__":
    unittest.main()
