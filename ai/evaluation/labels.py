"""녹음 정답 기록을 읽고 검사한다. 형식은 data/src/recordings/recording_label.schema.json이 정한다.

형식(JSON Schema)과 함께, 녹음·악보 파일의 SHA-256, 연습 구간이 곡의 음 수 안인지,
의도한 오류가 구간 안에 한 번씩만 있는지를 본다. 문제가 있으면 모두 모아 LabelError로 멈춘다.
"""

import hashlib
import json
from pathlib import Path

from jsonschema import Draft202012Validator

REPO = Path(__file__).resolve().parents[2]
_VALIDATOR = Draft202012Validator(
    json.loads((REPO / "data/src/recordings/recording_label.schema.json").read_text(encoding="utf-8"))
)


class LabelError(ValueError):
    """정답 기록을 믿을 수 없다. 이 녹음은 평가에 쓰지 않는다."""


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_label(path: Path, root: Path = REPO) -> dict:
    """root는 기록 안 경로(audio·score)의 기준 폴더다."""
    label = json.loads(Path(path).read_text(encoding="utf-8"))
    problems = [
        f'{"/".join(map(str, e.absolute_path)) or "(최상위)"}: {e.message}' for e in _VALIDATOR.iter_errors(label)
    ]
    if problems:
        raise LabelError(f"{path}: " + "; ".join(problems))

    if label["review"] == "rejected" or (label["conditions"]["kind"] == "PERFORMED" and label["review"] != "reviewed"):
        raise LabelError(f"{path}: review: 실제 녹음은 reviewed가 필요하며 rejected는 사용할 수 없다")

    for part in ("audio", "score"):
        if _sha256(root / label[part]["path"]) != label[part]["sha256"]:
            problems.append(f'{part}: SHA-256이 기록과 다르다({label[part]["path"]})')
    score = json.loads((root / label["score"]["path"]).read_text(encoding="utf-8"))
    notes = sum(e["kind"] == "NOTE" for e in score["events"])
    start, end = label["range"]["startNoteIdx"], label["range"]["endNoteIdx"]
    if not 0 <= start <= end < notes:
        problems.append(f"range: {start}~{end}가 곡의 음 0~{notes - 1} 밖이다")
    seen = set()
    for error in label["errors"]:
        idx = error["idx"]
        if not start <= idx <= end:
            problems.append(f"errors: idx {idx}가 연습 구간 {start}~{end} 밖이다")
        if idx in seen:
            problems.append(f"errors: idx {idx}에 오류가 두 번 적혔다")
        seen.add(idx)
    if problems:
        raise LabelError(f"{path}: " + "; ".join(problems))
    return label
