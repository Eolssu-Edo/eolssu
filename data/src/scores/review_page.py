"""변환 결과 폴더(page.png + cells.json)를 브라우저에서 바로 여는 검토 화면 HTML 한 장으로 묶는다.

실행: python data/src/scores/review_page.py <결과 폴더> [<결과 폴더> ...] --jgb <.jgb.json 폴더> \
        --umulsai <jeongganbo-generator 폴더> --out <HTML 폴더>

결과 폴더는 jeongganbo_omr.py·standard_score_text.py의 출력 폴더다. --jgb는 그 결과를 import-ngc-omr.mjs로
처음 변환한 .jgb.json 폴더다. 곡마다 <제목>.review.html을 만든다.
화면에서 악보 그림과 정간 상자를 보며 결과를 고치고, 고친 우물사이 문서(.jgb.json)와 검토 기록(.json)을
내려받는다. 문서의 레이아웃·대강 등은 처음 변환한 문서를 그대로 쓰고 선율·각 수·각별 정간 수만 바꾼다.
HTML에 원본 악보 그림이 들어가므로 공유 위치에 올리지 않는다.
"""
import argparse
import base64
import json
import subprocess
from pathlib import Path

TEMPLATE = Path(__file__).with_name("review_page.html")

parser = argparse.ArgumentParser()
parser.add_argument("folders", nargs="+", type=Path)
parser.add_argument("--jgb", type=Path, required=True, help="처음 변환한 <제목>.jgb.json이 있는 폴더")
parser.add_argument("--umulsai", type=Path, required=True, help="jeongganbo-generator 클론 폴더. 시김새 그림을 가져온다")
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
template = TEMPLATE.read_text(encoding="utf-8")

# 우물사이 기호 사전(js/symbols-registry.js)과 기호 그림(js/symbols-data.js, MIT)을 읽어
# 시김새 한글 이름 → SVG data URI 표를 만든다. 두 파일이 JS라 Node로 읽는다.
JS = """
const fs = require("fs"), g = {}, d = process.argv[1];
new Function("window", fs.readFileSync(d + "/js/symbols-registry.js", "utf8") + ";" + fs.readFileSync(d + "/js/symbols-data.js", "utf8"))(g);
const out = {};
for (const e of g.JGB_SYM.list) if (g.SYM_DATA[e.id]) out[e.ko] = g.SYM_DATA[e.id];
process.stdout.write(JSON.stringify(out));
"""
symbols = json.loads(subprocess.run(["node", "-e", JS, str(args.umulsai.resolve())], capture_output=True,
                                    check=True, encoding="utf-8").stdout)
for folder in args.folders:
    data = json.loads((folder / "cells.json").read_text(encoding="utf-8"))
    data["image"] = "data:image/png;base64," + base64.b64encode((folder / data["image"]).read_bytes()).decode()
    data["source"] = folder.name
    data["doc"] = json.loads((args.jgb / f"{data['title']}.jgb.json").read_text(encoding="utf-8"))
    data["symbols"] = symbols
    # </script>가 데이터 안에 있어도 스크립트가 끊기지 않게 '/'를 이스케이프한다.
    html = template.replace("/*DATA*/null", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
    path = args.out / f"{data['title']}.review.html"
    path.write_text(html, encoding="utf-8")
    print(f"{path}  정간 {len(data['cells'])}개")
