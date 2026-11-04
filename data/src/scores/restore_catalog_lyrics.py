"""Restore JGB lyrics onto an existing catalog score without retiming it."""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re

from jsonschema import validate

SCHEMA = Path(__file__).with_name("score_data.schema.json")


def grid(text):
    """Read JGB rows in play order, retaining empty cells at either end."""
    return [row.split("|") for row in re.split(r"\|\|\r?\n?|\r?\n", text.strip("\r\n"))]


def add_field(target, key, value):
    if key in target and target[key] != value:
        raise ValueError(f"기존 공통 악보와 {key} 값이 다릅니다")
    target[key] = value


def restore(score, source_bytes):
    """Return a new common score. The exact source and every cell position must match."""
    source_hash = score["provenance"]["jgbSha256"]
    lf = source_bytes.replace(b"\r\n", b"\n")
    source_encodings = (source_bytes, lf, lf.replace(b"\n", b"\r\n"))
    if not any(hashlib.sha256(raw).hexdigest() == source_hash for raw in source_encodings):
        raise ValueError("공통 악보에 기록된 JGB 해시와 다릅니다")

    doc = json.loads(source_bytes.decode("utf-8-sig"))
    parts = doc.get("parts") or [doc]
    part_index = score["score"]["part"]
    if not 0 <= part_index < len(parts):
        raise ValueError("공통 악보 성부에 해당하는 JGB 성부가 없습니다")
    part = parts[part_index]

    result = deepcopy(score)
    columns = result["columns"]
    shape = [len(column["cells"]) for column in columns]
    if [len(row) for row in grid(part["melody"])] != shape:
        raise ValueError("JGB 선율과 공통 악보의 각·정간 배치가 다릅니다")
    for i, column in enumerate(columns):
        if column["index"] != i or any(
            cell["index"] != j or cell["id"] != f"c{i}-j{j}"
            for j, cell in enumerate(column["cells"])
        ):
            raise ValueError("공통 악보의 각·정간 식별자가 위치와 다릅니다")

    lyrics = part.get("lyrics") or ""
    if lyrics.strip(" \t\r\n|"):
        rows = grid(lyrics)
        if [len(row) for row in rows] != shape:
            raise ValueError("JGB 가사와 공통 악보의 각·정간 배치가 다릅니다")
        for column, row in zip(columns, rows):
            for cell, text in zip(column["cells"], row):
                if text.strip():
                    add_field(cell, "lyric", text.strip())

    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("score", type=Path, help="현재 공통 악보 JSON")
    parser.add_argument("jgb", type=Path, help="해시가 일치하는 JGB 원본")
    parser.add_argument("--out", type=Path, required=True, help="새 공통 악보 JSON 경로")
    args = parser.parse_args()
    score = json.loads(args.score.read_text(encoding="utf-8"))
    result = restore(score, args.jgb.read_bytes())
    validate(result, json.loads(SCHEMA.read_text(encoding="utf-8")))
    with args.out.open("x", encoding="utf-8", newline="\n") as output:
        output.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
