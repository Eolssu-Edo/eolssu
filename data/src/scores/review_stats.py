"""검토 화면(review_page.html)이 저장한 검토 기록(<제목>.review.json)으로 곡별 변환 정확도를 집계한다.

실행: python data/src/scores/review_stats.py <검토 기록 폴더> [--json 결과.json]

사람이 고친 최종 결과를 정답으로 보고, 처음 변환 결과(original)와 정간마다 비교한다.
같은 폴더에 <제목>.jgb.json이 있으면 그 선율을 최종 결과로 쓴다(검토 뒤 우물사이에서 더 고친 내용 반영).
정간은 순서대로 맞추며, 검토 기록보다 많은 정간은 직접 넣은 정간으로 센다.
  정간 일치   처음 결과가 최종 결과와 글자까지 같은 정간
  칸 모양     줄 수와 줄마다 음 단위 수(반칸)가 같은 정간
  율명        칸 모양이 같은 정간에서 같은 자리의 율명·이음·쉼표가 같은 수 / 최종 결과의 전체 수
  시김새      같은 자리의 같은 시김새 수 / 최종 결과의 시김새 수
칸 모양이 다르면 그 정간의 율명·시김새는 모두 틀린 것으로 센다. 직접 넣은 정간도 같다.
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

# 우물사이 정간 문법(review_page.html parseUm과 같다): 띄어쓰기 = 줄, 이어 쓰기 = 음 단위(반칸),
# 음 단위 = 율명·이음·쉼표 + 숨표 + 시김새({…}·(…)·[…])
YUL = r"(?:하하배|하배|중청|배|청)?[황대태협고중유임이남무응]"
UNIT = re.compile(rf"({YUL}|-|쉼표|쉼)?(<)?((?:[{{(\[][^{{}}()\[\]]+[}})\]])*)")
SYM = re.compile(r"[{(\[]([^{}()\[\]]+)[})\]]")


def jg_to_um(jg):
    """JG-like 정간(':2 청황 :8 청태')을 우물사이 문법으로 바꾼다.
    jeongganbo-generator tools/import-ngc-omr.mjs(MIT)와 review_page.html jgToUm의 글자 만드는 부분과 같다."""
    alias = {"니나*": "{니나}", "니나": "{니라}", "느니-르": "{느니르}", "니루-니": "{니루니}",
             "반길이표/덧길이표": "{반길이표}{덧길이표}", "덧길이표/반길이표": "{덧길이표}{반길이표}", "쉼표": "쉼표"}
    groups = []
    for t in jg.split():
        if re.fullmatch(r":\d+", t):
            groups.append([int(t[1:]), []])
        else:
            groups[-1][1].append(t)
    if not groups:
        return ""

    def slot(p):
        if p == 0:
            return 1, 0, "w"
        if p <= 9:
            return 3, (p - 1) // 3, "lwr"[(p - 1) % 3]
        return (2,) + {10: (0, "w"), 11: (1, "w"), 12: (0, "l"), 13: (0, "r"), 14: (1, "l"), 15: (1, "r")}[p]

    token = lambda t: alias.get(t) or (t if t == "-" or re.fullmatch(YUL, t) else "{" + t + "}")
    cell_of = lambda toks: "".join(map(token, toks)) if toks else "-"
    grid = [dict(w=None, l=None, r=None, extra=[]) for _ in range(slot(groups[0][0])[0])]
    for p, names in groups:
        _, r, c = slot(p)
        (grid[r]["extra"].append(names) if grid[r][c] else grid[r].__setitem__(c, names))
    rows = []
    for g in grid:
        s = (cell_of(g["w"]) if g["w"] and not (g["l"] or g["r"])
             else cell_of(g["l"]) + cell_of(g["r"]) + (cell_of(g["w"]) if g["w"] else "") if (g["l"] or g["r"]) else "-")
        rows.append(s + "".join(map(cell_of, g["extra"])))
    return " ".join(rows)


def parse(cell):
    rows = []
    for word in cell.split():
        units, i = [], 0
        while i < len(word):
            m = UNIT.match(word, i)
            if not m or not m.group(0):
                raise ValueError(f"읽을 수 없음: {cell!r}")
            core = m.group(1) or ""
            units.append(("쉼표" if core == "쉼" else core, SYM.findall(m.group(3))))
            i = m.end()
        rows.append(units)
    return rows


def song_stats(review):
    s = Counter(cells=0, exact=0, shape=0, pitch=0, pitch_ok=0, sym=0, sym_ok=0, sym_wrong=0, inserted=0)
    confusion, missed_syms = Counter(), Counter()
    for c in review["cells"]:
        final, orig = parse(c["cell"]), parse(c["original"])
        s["cells"] += 1
        s["inserted"] += bool(c.get("inserted"))
        s["exact"] += c["cell"].split() == c["original"].split()
        f_units = [u for row in final for u in row]
        s["pitch"] += sum(1 for core, _ in f_units if core)
        s["sym"] += sum(len(syms) for _, syms in f_units)
        same_shape = [len(r) for r in final] == [len(r) for r in orig]
        if not same_shape:
            missed_syms.update(n for _, syms in f_units for n in syms)
            continue
        s["shape"] += 1
        for (fc, fs), (oc, os_) in zip(f_units, [u for row in orig for u in row]):
            if fc:
                s["pitch_ok"] += fc == oc
                if fc != oc:
                    confusion[(oc or "(없음)", fc)] += 1
            hit = Counter(fs) & Counter(os_)
            s["sym_ok"] += sum(hit.values())
            missed_syms.update(Counter(fs) - hit)
            s["sym_wrong"] += sum((Counter(os_) - hit).values())
    return s, confusion, missed_syms


def with_jgb(review, jgb_path):
    """우물사이 문서의 선율을 최종 결과로 덮어쓰고, 검토 기록과 달라진 정간 번호를 돌려준다."""
    if not jgb_path.exists():
        return review, []
    melody = json.loads(jgb_path.read_text(encoding="utf-8"))["parts"][0]["melody"]
    finals = [" ".join(x.split()) for line in melody.split("\n") for x in line.split("|")]
    cells = [dict(c) for c in review["cells"]]
    if len(finals) < len(cells):
        raise ValueError(f"{review['title']}: 우물사이 문서의 정간({len(finals)})이 검토 기록({len(cells)})보다 적음")
    changed = [i + 1 for i, (c, f) in enumerate(zip(cells, finals)) if c["cell"].split() != f.split()]
    for c, f in zip(cells, finals):
        c["cell"] = f
    for f in finals[len(cells):]:
        cells.append(dict(cell=f, original="", conf=None, inserted=True))
        changed.append(len(cells))
    return dict(review, cells=cells), changed


def pct(a, b):
    return f"{a}/{b} ({100 * a / b:.0f}%)" if b else "-"


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("folder", type=Path)
    ap.add_argument("--json", type=Path)
    args = ap.parse_args()
    out, total_conf, total_missed, notes = {}, Counter(), Counter(), []
    print("| 곡 | 읽은 방식 | 정간 | 정간 일치 | 칸 모양 | 율명 | 시김새 | 잘못 붙은 시김새 |")
    print("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |")
    for f in sorted(args.folder.glob("*.review.json")):
        r = json.loads(f.read_text(encoding="utf-8"))
        r, from_jgb = with_jgb(r, f.with_name(f"{r['title']}.jgb.json"))
        if from_jgb:
            notes.append(f"{r['title']}: 우물사이 문서 기준으로 바뀐 정간 {from_jgb}")
        s, conf, missed = song_stats(r)
        way = "글자 층" if all(c["conf"] is None for c in r["cells"] if not c.get("inserted")) else "OMR"
        total_conf += conf if way == "OMR" else Counter()
        total_missed += missed if way == "OMR" else Counter()
        print(f"| {r['title']} | {way} | {s['cells']} | {pct(s['exact'], s['cells'])} | {pct(s['shape'], s['cells'])} | "
              f"{pct(s['pitch_ok'], s['pitch'])} | {pct(s['sym_ok'], s['sym'])} | {s['sym_wrong']} |")
        out[r["title"]] = dict(way=way, **s, confusion={f"{a}→{b}": n for (a, b), n in conf.most_common()},
                               missed_sigimsae=dict(missed.most_common()))
    print("\n" + "\n".join(notes))
    print("\nOMR 율명 혼동(처음 → 고친 결과):", ", ".join(f"{a}→{b} {n}" for (a, b), n in total_conf.most_common(12)))
    print("OMR이 놓친 시김새:", ", ".join(f"{k} {n}" for k, n in total_missed.most_common(12)))
    if args.json:
        args.json.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
