"""로컬 도구의 디코딩 크기 제한과 원본 보존을 검사한다."""

import wave
import subprocess
import os
import tempfile

import numpy as np
import pytest

from ai_service.api import RecordingTooLong
from ai_service.pipelines.audio import DecodeError, decode_recording
from evaluation.local_audio import decode_window


def wave_file(path):
    samples = (np.sin(2 * np.pi * 440 * np.arange(48000) / 16000) * 10000).astype('<i2')
    with wave.open(str(path), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(samples.tobytes())
    return path


def test_window_matches_existing_decoder_without_truncation(tmp_path):
    path = wave_file(tmp_path / 'tone.wav')
    original = decode_recording(path)
    assert np.array_equal(decode_window(path, 0, len(original)), original)
    assert np.array_equal(decode_window(path, 8000, len(original) - 8000), original[8000:])


def test_decoded_output_exceeding_limit_is_rejected(tmp_path):
    path = wave_file(tmp_path / 'tone.wav')
    with pytest.raises(RecordingTooLong):
        decode_window(path, 0, 16000)
    with pytest.raises(RecordingTooLong):
        decode_window(path, 8000, 16000)


def test_corrupt_and_start_beyond_recording_are_not_success(tmp_path):
    invalid = tmp_path / 'bad.m4a'
    invalid.write_bytes(b'not audio')
    with pytest.raises(DecodeError):
        decode_window(invalid, 0, 48000)
    with pytest.raises(ValueError, match='시작'):
        decode_window(wave_file(tmp_path / 'tone.wav'), 48000, 48000)


def test_aac_matches_existing_decoder_and_clipped_packet_is_rejected(tmp_path):
    audio = tmp_path / 'tone.m4a'
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-f', 'lavfi',
                    '-i', 'sine=frequency=440:sample_rate=44100:duration=2',
                    '-c:a', 'aac', '-movflags', '+faststart', str(audio)], check=True)
    original = decode_recording(audio)
    assert np.array_equal(decode_window(audio, 0, len(original)), original)
    assert np.array_equal(decode_window(audio, 8000, len(original) - 8000), original[8000:])
    data = audio.read_bytes()
    broken = tmp_path / 'broken.m4a'
    broken.write_bytes(data[:len(data) // 2])
    with pytest.raises(DecodeError):
        decode_window(broken, 0, len(original) * 2)


def test_stereo_44100_aac_window_matches_original_decoder(tmp_path):
    audio = tmp_path / 'stereo.m4a'
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y',
                    '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100:duration=2',
                    '-f', 'lavfi', '-i', 'sine=frequency=660:sample_rate=44100:duration=2',
                    '-filter_complex', '[0][1]amerge=inputs=2', '-c:a', 'aac', str(audio)], check=True)
    expected = decode_recording(audio)
    assert np.array_equal(decode_window(audio, 12345, len(expected) - 12345), expected[12345:])


def test_unavailable_container_duration_does_not_bypass_output_limit(tmp_path, monkeypatch):
    from evaluation import local_audio
    monkeypatch.setattr(local_audio, '_container_seconds', lambda _: None)
    with pytest.raises(RecordingTooLong):
        decode_window(wave_file(tmp_path / 'tone.wav'), 0, 16000)


def test_prefix_is_not_written_to_temporary_audio_file(tmp_path, monkeypatch):
    from evaluation import local_audio
    path = tmp_path / 'long.wav'
    with wave.open(str(path), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(b'\0\0' * 16000 * 10)
    real_temporary_file = tempfile.TemporaryFile
    sizes = []

    class WatchedFile:
        def __init__(self):
            self.file = real_temporary_file()

        def __enter__(self):
            return self.file

        def __exit__(self, *_):
            sizes.append(os.fstat(self.file.fileno()).st_size)
            self.file.close()

    monkeypatch.setattr(local_audio.tempfile, 'TemporaryFile', WatchedFile)
    pcm = decode_window(path, 9 * 16000, 16000)
    assert len(pcm) == 16000
    assert max(sizes) <= 16000 * 2
