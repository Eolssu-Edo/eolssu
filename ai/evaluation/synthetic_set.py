"""아리랑 공통 악보 샘플로 정상·오류 합성 녹음과 정답 기록 묶음을 만든다.

실행: uv run python -m evaluation.synthetic_set [--out data/processed/recordings/danso/arirang-synthetic]
평가: uv run python -m evaluation.error_detection <out>/*.label.json --out ai/evaluation/baselines/arirang-synthetic-pyin-4.json

정상 1개와 오류 종류(PITCH·OCTAVE·TIMING·MISSED)마다 3개, 녹음 하나에 오류 5개라 종류마다 틀린 음이 15개다.
같은 계획이면 녹음까지 같은 바이트로 다시 만들어 묶음 해시가 유지된다. 실제 녹음 검출률을 대신하지 않는다.
"""

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import soundfile as sf

from evaluation.error_detection import score_notes
from evaluation.labels import REPO
from evaluation.synthetic import SR, synth

SCORE = "data/tests/fixtures/scores/ngc-standard-2022-p173.score.json"
NOTES = score_notes(json.loads((REPO / SCORE).read_text(encoding="utf-8")), 0, 60)
BPM = 96  # 아리랑 기본 빠르기(세마치, 점4분음표)
GAP = 0.04  # 같은 음이 이어지는 곳이 있어 음마다 끊어 분다
GENERATOR = "tool:evaluation.synthetic_set@1"  # 오류 배치·합성 방법을 바꾸면 올린다
TIMING_MS = (100, -100, 150, -150, 200)  # 허용값 62ms·125ms(96BPM의 1/8·0.2박) 양쪽에 걸친다


def plan(per_recording: int = 5, recordings: int = 3, seed: int = 0) -> list[dict]:
    """녹음마다 의도한 오류 목록. 이웃한 음에는 같이 넣지 않고, 첫 음·끝 음과 지연·누락의 짧은 음은 피한다."""
    rng = np.random.default_rng(seed)
    inner = range(1, len(NOTES) - 1)
    long = [k for k in inner if NOTES[k].beats >= 2 / 3]
    specs = [{"recordingId": "arirang-synth-normal", "errors": []}]
    for kind in ("PITCH", "OCTAVE", "TIMING", "MISSED"):
        for r in range(recordings):
            chosen = []
            for k in rng.permutation(long if kind in ("TIMING", "MISSED") else inner):
                if all(abs(int(k) - c) > 1 for c in chosen):
                    chosen.append(int(k))
                if len(chosen) == per_recording:
                    break
            errors = []
            for i, k in enumerate(sorted(chosen)):
                size = {
                    "PITCH": {"semitones": 1 if i % 2 == 0 else -1},
                    "OCTAVE": {"octaves": 1 if i % 2 == 0 else -1},
                    "TIMING": {"ms": TIMING_MS[i]},
                    "MISSED": {},
                }[kind]
                errors.append({"idx": k, "kind": kind, **size})
            specs.append({"recordingId": f"arirang-synth-{kind.lower()}-{r + 1}", "errors": errors})
    return specs


def render(errors: list[dict]) -> np.ndarray:
    shift = {e["idx"]: e.get("semitones", 12 * e.get("octaves", 0)) for e in errors if e["kind"] in ("PITCH", "OCTAVE")}
    delay = {e["idx"]: e["ms"] / 1000 for e in errors if e["kind"] == "TIMING"}
    drop = tuple(e["idx"] for e in errors if e["kind"] == "MISSED")
    return synth(NOTES, gap=GAP, bpm=BPM, shift=shift, delay=delay, drop=drop)


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_set(out: Path, specs: list[dict], root: Path = REPO) -> list[Path]:
    """녹음(16kHz PCM16 WAV)과 정답 기록을 out에 쓴다. 기록 안 경로는 root 기준이라 out은 root 아래여야 한다."""
    out.mkdir(parents=True, exist_ok=True)
    paths = []
    for spec in specs:
        wav = out / f'{spec["recordingId"]}.wav'
        sf.write(wav, render(spec["errors"]), SR, subtype="PCM_16")
        label = {
            "schemaVersion": 1,
            "recordingId": spec["recordingId"],
            "method": GENERATOR,
            "review": "inferred",
            "audio": {"path": wav.relative_to(root).as_posix(), "sha256": _sha256(wav)},
            "score": {"path": SCORE, "sha256": _sha256(root / SCORE)},
            "range": {"startNoteIdx": NOTES[0].idx, "endNoteIdx": NOTES[-1].idx},
            "bpm": BPM,
            "bpmUnit": "DOTTED_QUARTER",
            "errors": spec["errors"],
            "conditions": {"kind": "SYNTHETIC", "generator": "sine-harmonics", "seed": 0, "noiseDb": None, "gapSeconds": GAP},
        }
        path = out / f'{spec["recordingId"]}.label.json'
        path.write_text(json.dumps(label, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
        paths.append(path)
    return paths


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=REPO / "data/processed/recordings/danso/arirang-synthetic")
    args = parser.parse_args()
    for path in write_set(args.out.resolve(), plan()):
        print(path.relative_to(REPO).as_posix())


if __name__ == "__main__":
    main()
