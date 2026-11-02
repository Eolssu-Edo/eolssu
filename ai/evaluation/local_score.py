"""검수된 단소 악보와 로컬 녹음을 현재 서비스 기준으로 채점한다."""

import argparse
from dataclasses import asdict
import json
import math
from pathlib import Path
import sys

import numpy as np

from ai_service.api import DTW_BYTES_PER_CELL, MAX_DTW_BYTES, RecordingTooLong
from ai_service.pipelines.audio import SAMPLE_RATE
from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, frame_rms, pyin_f0, score
from evaluation.error_detection import score_notes
from evaluation.local_audio import decode_window

_SCORES = frozenset(f"ngc-standard-2022-p{page}" for page in range(171, 175))
_REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(_REPO / "data/src/scores"))
from score_data import convert_answer  # noqa: E402


def load_notes(source_id):
    """검수된 단소 4곡의 악보에서 원래 음 번호를 유지한다."""
    if source_id not in _SCORES:
        raise ValueError("지원하지 않는 악보입니다")
    sheet = convert_answer(source_id)
    if sheet['provenance']['review'] != 'reviewed':
        raise ValueError("검수가 완료되지 않은 악보입니다")
    return score_notes(sheet, 0, sys.maxsize)


def prepare_recording(audio_path, source_id, bpm, start_s=0):
    """녹음과 악보를 검증하고 음높이와 음량을 준비한다."""
    if not math.isfinite(bpm) or bpm <= 0:
        raise ValueError("BPM은 0보다 커야 합니다")
    if not math.isfinite(start_s) or start_s < 0:
        raise ValueError("시작 위치는 0초 이상이어야 합니다")
    notes = load_notes(source_id)
    if not notes:
        raise ValueError("악보에 음표가 없습니다")
    span_beats = notes[-1].start_beat + notes[-1].beats - notes[0].start_beat
    expected_frames = span_beats * 60.0 / bpm / HOP_S
    if not math.isfinite(expected_frames):
        raise RecordingTooLong("악보와 녹음이 분석 메모리 한도를 넘습니다")
    start_position = start_s * SAMPLE_RATE
    if not math.isfinite(start_position) or start_position > (2**63 - 3) // 2:
        raise ValueError("시작 위치가 녹음 길이 밖입니다")
    start_sample = round(start_position)
    denominator = expected_frames * DTW_BYTES_PER_CELL
    ceiling = (2**63 - 3) // 2 - start_sample
    allowed = MAX_DTW_BYTES * SAMPLE_RATE * HOP_S / denominator if denominator else math.inf
    max_samples = min(ceiling, math.floor(allowed)) if math.isfinite(allowed) else ceiling
    if max_samples < 1:
        raise RecordingTooLong("악보와 녹음이 분석 메모리 한도를 넘습니다")
    audio = decode_window(audio_path, start_sample, max_samples).astype(np.float32) / 32768.0
    return pyin_f0(audio, SAMPLE_RATE), notes, frame_rms(audio, SAMPLE_RATE)


def score_recording(audio_path, source_id, bpm, start_s=0):
    """녹음 앞 start_s초를 제외하고 서비스와 같은 기본 설정으로 채점한다."""
    f0, notes, rms = prepare_recording(audio_path, source_id, bpm, start_s)
    return score(f0, notes, bpm, HWANG_HZ, rms=rms)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", type=Path, help="로컬 녹음 파일")
    parser.add_argument("score_id", choices=sorted(_SCORES), help="검수된 단소 악보 ID")
    parser.add_argument("--bpm", required=True, type=float)
    parser.add_argument("--start", type=float, default=0, help="녹음에서 시작할 초")
    args = parser.parse_args()
    result = score_recording(args.audio, args.score_id, args.bpm, args.start)
    print(json.dumps(asdict(result), ensure_ascii=False, allow_nan=False))
    if result.status != "DONE":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
