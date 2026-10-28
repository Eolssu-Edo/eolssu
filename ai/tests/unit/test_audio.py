"""녹음 원본 디코딩(PRA-02 '분석용 변환')을 테스트 중 만든 몇 초짜리 AAC로 검사한다. ffmpeg가 PATH에 있어야 한다."""

import subprocess

import numpy as np
import pytest

from ai_service.pipelines.audio import SAMPLE_RATE, DecodeError, decode_recording

SECONDS = 2.0


def make_m4a(path, inputs, filter_complex=None):
    """ffmpeg lavfi 입력으로 AAC m4a를 만든다."""
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y"]
    for source in inputs:
        cmd += ["-f", "lavfi", "-i", source]
    if filter_complex:
        cmd += ["-filter_complex", filter_complex]
    cmd += ["-t", str(SECONDS), "-c:a", "aac", str(path)]
    subprocess.run(cmd, check=True)
    return path


def sine(hz, sr):
    return f"sine=frequency={hz}:sample_rate={sr}:duration={SECONDS}"


def peak_hz(pcm):
    spectrum = np.abs(np.fft.rfft(pcm.astype(np.float64)))
    return np.fft.rfftfreq(len(pcm), 1 / SAMPLE_RATE)[np.argmax(spectrum)]


def rms(pcm):
    return float(np.sqrt(np.mean(pcm.astype(np.float64) ** 2)))


def test_stereo_44k_decodes_to_16k_mono_pcm16(tmp_path):
    src = make_m4a(tmp_path / "s.m4a", [sine(440, 44100), sine(440, 44100)], "[0][1]amerge=inputs=2")
    pcm = decode_recording(src)
    assert pcm.dtype == np.int16 and pcm.ndim == 1
    assert abs(len(pcm) / SAMPLE_RATE - SECONDS) < 1.0
    assert peak_hz(pcm) == pytest.approx(440, abs=2)  # 16kHz로 바뀌지 않았으면 주파수가 어긋난다


def test_mono_48k_decodes_to_16k_mono_pcm16(tmp_path):
    pcm = decode_recording(make_m4a(tmp_path / "m.m4a", [sine(523, 48000)]))
    assert pcm.dtype == np.int16 and pcm.ndim == 1
    assert abs(len(pcm) / SAMPLE_RATE - SECONDS) < 1.0
    assert peak_hz(pcm) == pytest.approx(523, abs=2)


def test_stereo_is_average_of_two_channels(tmp_path):
    both = make_m4a(tmp_path / "both.m4a", [sine(440, 44100), sine(440, 44100)], "[0][1]amerge=inputs=2")
    left = make_m4a(
        tmp_path / "left.m4a", [sine(440, 44100), "anullsrc=r=44100:cl=mono"], "[0][1]amerge=inputs=2"
    )
    assert rms(decode_recording(left)) / rms(decode_recording(both)) == pytest.approx(0.5, abs=0.05)


def test_truncated_file_is_decode_error(tmp_path):
    whole = make_m4a(tmp_path / "w.m4a", [sine(440, 44100)])
    cut = tmp_path / "cut.m4a"
    cut.write_bytes(whole.read_bytes()[:300])
    with pytest.raises(DecodeError) as error:
        decode_recording(cut)
    assert error.value.fail_reason == "DECODE_ERROR"


def test_non_audio_bytes_are_decode_error(tmp_path):
    junk = tmp_path / "junk.m4a"
    junk.write_bytes(bytes(range(256)) * 16)
    with pytest.raises(DecodeError):
        decode_recording(junk)


def test_file_without_audio_stream_is_decode_error(tmp_path):
    video = tmp_path / "video.m4a"
    subprocess.run(
        ["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "lavfi", "-i", "color=size=16x16:duration=1",
         "-c:v", "mpeg4", "-f", "mp4", str(video)],
        check=True,
    )
    with pytest.raises(DecodeError):
        decode_recording(video)
