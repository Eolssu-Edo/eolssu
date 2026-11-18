"""정간보 OMR 결과(jeongganbo_omr.py의 cells.json)의 시김새를 저장소 정답(data/annotations/scores)과 비교한다.

실행: python data/src/scores/sigimsae_eval.py <원본 ID>=<cells.json> [...]
  원본 ID   data/annotations/scores/<원본 ID>/*.jgb.json이 정답이다(첫 성부).

정간 수가 정답과 같은 곡만 순서대로 맞춘다(다르면 '정간 수 다름'으로 건너뛴다).
  맞음    같은 정간에서 이름이 같은 시김새 수(자리와 상관없이)
  자리    그중 같은 줄·같은 음 단위·같은 율명에 붙은 수
  더함    정답에 없는 시김새, 놓침: 결과에 없는 정답 시김새
정답은 율명에 붙은 И를 '니나'로 적고, OMR 경로는 같은 기호를 '니라'(모델의 붙임 니나)나 '니나'(획 보정)로 낸다.
비교할 때는 '니라'를 '니나'로 본다. '@' 뒤의 위치 값은 뺀다.
"""
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))  # python -I로 실행해도 같은 폴더의 review_stats를 찾게
from review_stats import jg_to_um, parse

DATA = Path(__file__).resolve().parents[2]
SAME = {"니라": "니나"}


def answer_cells(source_id, data=DATA):
    jgb = next((data / "annotations/scores" / source_id).glob("*.jgb.json"))
    melody = json.loads(jgb.read_text(encoding="utf-8"))["parts"][0]["melody"]
    return [c.strip() for line in melody.split("\n") if line.strip() for c in line.split("|")]


def marks(cell):
    """우물사이 정간 → [(줄, 단위, 율명, 시김새)]"""
    return [(r, u, core, SAME.get(name.split("@")[0], name.split("@")[0]))
            for r, row in enumerate(parse(cell)) for u, (core, names) in enumerate(row) for name in names]


def compare(answers, cells):
    """정답 정간 목록과 OMR 정간 목록 → (집계, 정답 이름 → 결과 이름 짝)"""
    total, pairs = Counter(answer=sum(len(marks(a)) for a in answers)), Counter()
    if len(answers) != len(cells):
        return total, pairs
    for answer, cell in zip(answers, cells):
        want, got = marks(answer), marks(jg_to_um(cell.get("jg", "")))
        hit = Counter(m[3] for m in want) & Counter(m[3] for m in got)
        total["hit"] += sum(hit.values())
        total["place"] += sum((Counter(want) & Counter(got)).values())
        total["extra"] += sum((Counter(m[3] for m in got) - hit).values())
        total["missed"] += sum((Counter(m[3] for m in want) - hit).values())
        at_want, at_got = {}, {}
        for r, u, _, name in want:
            at_want.setdefault((r, u), []).append(name)
        for r, u, _, name in got:
            at_got.setdefault((r, u), []).append(name)
        for key in set(at_want) | set(at_got):
            pairs[(",".join(at_want.get(key, ["-"])), ",".join(at_got.get(key, ["-"])))] += 1
    return total, pairs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    all_total, all_pairs = Counter(), Counter()
    print("| 원본 ID | 정답 | 맞음 | 자리 | 더함 | 놓침 |\n| --- | ---: | ---: | ---: | ---: | ---: |")
    for arg in sys.argv[1:]:
        source_id, path = arg.split("=", 1)
        answers = answer_cells(source_id)
        cells = json.loads(Path(path).read_text(encoding="utf-8"))["cells"]
        total, pairs = compare(answers, cells)
        if len(answers) != len(cells):
            print(f"| {source_id} | {total['answer']} | 정간 수 다름({len(cells)}/{len(answers)}) | | | |")
            continue
        all_total += total
        all_pairs += pairs
        print(f"| {source_id} | {total['answer']} | {total['hit']} | {total['place']} | {total['extra']} | {total['missed']} |")
    print(f"| 합계 | {all_total['answer']} | {all_total['hit']} | {all_total['place']} | {all_total['extra']} | {all_total['missed']} |")
    print("\n정답 → 결과(같은 정간·줄·음 단위):")
    for (want, got), n in all_pairs.most_common():
        print(f"  {want} → {got}: {n}")
