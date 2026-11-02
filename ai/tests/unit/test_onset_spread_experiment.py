"""박자 배수 비교의 최소 허용과 잘못된 입력 거절을 확인한다."""

import subprocess
import sys
from pathlib import Path

import numpy as np
import pytest

from ai_service.pipelines import scoring


@pytest.mark.parametrize("jitter", [False, True])
def test_spread_changes_allowance_only_above_minimum(jitter):
    notes = [scoring.Note(i % 2 * 2, 1, idx=i) for i in range(16)]
    delays = [0, 4, 1, 7, 2, 9, 1, 6, 0, 10, 2, 8, 1, 7, 2, 9]
    f0 = np.full(800, np.nan)
    for i, note in enumerate(notes):
        delay = delays[i] if jitter else 0
        f0[i * 50 + delay:(i + 1) * 50] = scoring.HWANG_HZ * 2 ** (note.semitone / 12)

    allowances = []
    for multiplier in (3.0, 4.0):
        captured = {}

        def trace(frame, event, arg):
            if frame.f_code is scoring.score.__code__ and event == "return":
                captured["allowance"] = frame.f_locals["onset_tol_s"]
            return trace

        previous_trace = sys.gettrace()
        sys.settrace(trace)
        try:
            result = scoring.score(f0, notes, 120, scoring.HWANG_HZ, scoring.Tolerance(onset_spread=multiplier))
        finally:
            sys.settrace(previous_trace)
        assert result.status == "DONE"
        allowances.append(captured["allowance"])

    if jitter:
        assert allowances[0] == pytest.approx(0.1887, abs=0.001)
        assert allowances[1] == pytest.approx(0.2516, abs=0.001)
    else:
        assert allowances == pytest.approx([0.0625, 0.0625])


def test_experiment_rejects_wrong_recording_hash(tmp_path):
    (tmp_path / "아리랑_단소_2.m4a").write_bytes(b"wrong recording")
    output = tmp_path / "result.json"
    process = subprocess.run(
        [sys.executable, "-X", "utf8", "-m", "evaluation.compare_onset_spread",
         "--recordings", str(tmp_path), "--raw-root", str(tmp_path), "--out", str(output)],
        cwd=Path(__file__).resolve().parents[2], capture_output=True, text=True, encoding="utf-8",
    )
    assert process.returncode != 0
    assert "녹음 해시 불일치" in process.stderr
    assert not output.exists()
