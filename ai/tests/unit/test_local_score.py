"""로컬 채점 입력과 실패 구분을 검사한다."""

import wave

import numpy as np
import pytest

from evaluation.local_score import load_notes, score_recording
from ai_service.api import RecordingTooLong
from ai_service.pipelines.audio import DecodeError


@pytest.mark.parametrize("source_id,count", [
    ("ngc-standard-2022-p171", 91), ("ngc-standard-2022-p172", 85),
    ("ngc-standard-2022-p173", 61), ("ngc-standard-2022-p174", 68),
])
def test_reviewed_danso_score_keeps_original_note_numbers(source_id, count):
    notes = load_notes(source_id)
    assert len(notes) == count
    assert [note.idx for note in notes] == list(range(count))


def test_unknown_score_is_rejected():
    with pytest.raises(ValueError, match="악보"):
        load_notes("ngc-standard-2022-p999")


def test_unreviewed_score_is_rejected(monkeypatch):
    from evaluation import local_score
    monkeypatch.setattr(local_score, 'convert_answer', lambda _: {'provenance': {'review': 'pending'}})
    with pytest.raises(ValueError, match="검수"):
        load_notes("ngc-standard-2022-p173")


@pytest.mark.parametrize("bpm,start", [(0, 0), (-1, 0), (96, -1), (float('nan'), 0), (96, float('nan'))])
def test_bad_timing_inputs_are_rejected(bpm, start):
    with pytest.raises(ValueError):
        score_recording("missing.m4a", "ngc-standard-2022-p173", bpm, start)


def test_missing_and_corrupt_recordings_are_distinct(tmp_path):
    with pytest.raises(FileNotFoundError):
        score_recording(tmp_path / "missing.m4a", "ngc-standard-2022-p173", 96, 0)
    broken = tmp_path / "broken.m4a"
    broken.write_bytes(b"not audio")
    with pytest.raises(DecodeError):
        score_recording(broken, "ngc-standard-2022-p173", 96, 0)


def test_silence_is_failure_and_start_beyond_audio_is_rejected(tmp_path):
    audio = tmp_path / "silence.wav"
    with wave.open(str(audio), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(b'\0\0' * 16000)
    assert score_recording(audio, "ngc-standard-2022-p173", 96, 0).fail_reason == "NO_SOUND"
    with pytest.raises(ValueError, match="시작"):
        score_recording(audio, "ngc-standard-2022-p173", 96, 1)


def test_short_recording_is_not_counted_as_success(tmp_path):
    audio = tmp_path / "short.wav"
    tone = (np.sin(2 * np.pi * 440 * np.arange(16000) / 16000) * 10000).astype('<i2')
    with wave.open(str(audio), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(tone.tobytes())
    result = score_recording(audio, "ngc-standard-2022-p173", 96, 0)
    assert (result.status, result.fail_reason) == ("FAILED", "TOO_SHORT")


def test_oversized_alignment_is_rejected_before_scoring(tmp_path):
    audio = tmp_path / "short.wav"
    with wave.open(str(audio), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(b'\0\0' * 16000)
    with pytest.raises(RecordingTooLong):
        score_recording(audio, "ngc-standard-2022-p173", 0.00001, 0)
