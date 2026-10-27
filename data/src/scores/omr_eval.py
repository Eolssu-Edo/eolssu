"""OMR 결과(jeongganbo_omr.py의 cells.json)를 사람이 대조한 정답과 비교한다. 옵션별 효과를 잴 때 쓴다.

실행: python data/src/scores/omr_eval.py <정답 폴더> <조합 이름>=<결과 폴더> [...]
  정답 폴더   검토 화면의 <제목>.review.json과 <제목>.jgb.json이 있는 폴더(review_stats.py와 같은 정답)
  결과 폴더   곡마다 하위 폴더에 cells.json이 있는 jeongganbo_omr.py 출력 폴더

정간은 칸 상자 위치(x, y)로 짝짓는다. 같은 --dpi로 칸을 찾았다면 옵션과 관계없이 상자가 같다.
결과에 상자가 없는 정답 정간은 빈 정간을 읽은 것으로 센다. 집계 기준은 review_stats.song_stats와 같다.
"""
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))  # python -I로 실행해도 같은 폴더의 review_stats를 찾게
from review_stats import jg_to_um, pct, song_stats, with_jgb

truth_dir = Path(sys.argv[1])
runs = [a.split("=", 1) for a in sys.argv[2:]]
truths = {}
for f in sorted(truth_dir.glob("*.review.json")):
    r = json.loads(f.read_text(encoding="utf-8"))
    if all(c["conf"] is None for c in r["cells"] if not c.get("inserted")):
        continue  # 글자 층으로 읽은 곡은 OMR 비교 대상이 아니다
    truths[r["title"]] = with_jgb(r, f.with_name(f"{r['title']}.jgb.json"))[0]

print("| 조합 | " + " | ".join(truths) + " | 합계 율명 | 합계 칸 모양 |")
print("| --- |" + " ---: |" * (len(truths) + 2))
summary = {}
for name, folder in runs:
    row, tot = [], Counter()
    for title, truth in truths.items():
        cells_file = next(p for p in Path(folder).glob("*/cells.json")
                          if json.loads(p.read_text(encoding="utf-8"))["title"] == title)
        omr = {(c["x"], c["y"]): jg_to_um(c["jg"]) for c in json.loads(cells_file.read_text(encoding="utf-8"))["cells"]}
        cells = [dict(c, original=omr.get(tuple(c["box"][:2]), "") if c.get("box") else "") for c in truth["cells"]]
        s, conf, _ = song_stats(dict(truth, cells=cells))
        tot += s
        row.append(pct(s["pitch_ok"], s["pitch"]).split(" ")[1])
        summary.setdefault(name, {})[title] = dict(s, confusion={f"{a}→{b}": n for (a, b), n in conf.most_common()})
    print(f"| {name} | " + " | ".join(row) + f" | {pct(tot['pitch_ok'], tot['pitch'])} | {pct(tot['shape'], tot['cells'])} |")
    summary[name]["_total"] = dict(tot)

print("\n조합별 율명 혼동 상위(처음 → 정답):")
for name, songs in summary.items():
    conf = Counter()
    for title, v in songs.items():
        conf.update(v.get("confusion", {}))
    print(f"- {name}: " + ", ".join(f"{k} {n}" for k, n in conf.most_common(6)))
