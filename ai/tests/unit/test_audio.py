"""녹음 원본 디코딩(PRA-02 '분석용 변환')을 테스트 중 만든 몇 초짜리 AAC로 검사한다. ffmpeg가 PATH에 있어야 한다."""

import subprocess

import numpy as np
import pytest

from ai_service.pipelines.audio import SAMPLE_RATE, DecodeError, decode_recording

SECONDS = 2.0


def make_m4a(path, inputs, filter_complex=None):
    """ffmpeg lavfi 입력으로 AAC m4a를 만든다. 앱 녹음처럼 메타데이터(moov)를 앞에 둔다."""
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y"]
    for source in inputs:
        cmd += ["-f", "lavfi", "-i", source]
    if filter_complex:
        cmd += ["-filter_complex", filter_complex]
    cmd += ["-t", str(SECONDS), "-c:a", "aac", "-movflags", "+faststart", str(path)]
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
    mono = make_m4a(tmp_path / "mono.m4a", [sine(440, 44100)])
    both = make_m4a(tmp_path / "both.m4a", [sine(440, 44100), sine(440, 44100)], "[0][1]amerge=inputs=2")
    left = make_m4a(
        tmp_path / "left.m4a", [sine(440, 44100), "anullsrc=r=44100:cl=mono"], "[0][1]amerge=inputs=2"
    )
    level = rms(decode_recording(mono))
    # 평균이면 같은 소리 두 채널은 모노와 같고, 한쪽만 소리면 절반이다. 합이면 2배·1배가 된다.
    assert rms(decode_recording(both)) / level == pytest.approx(1.0, abs=0.05)
    assert rms(decode_recording(left)) / level == pytest.approx(0.5, abs=0.05)


def test_truncated_file_is_decode_error(tmp_path):
    whole = make_m4a(tmp_path / "w.m4a", [sine(440, 44100)])
    cut = tmp_path / "cut.m4a"
    cut.write_bytes(whole.read_bytes()[:300])
    with pytest.raises(DecodeError) as error:
        decode_recording(cut)
    assert error.value.fail_reason == "DECODE_ERROR"


def test_damaged_middle_is_decode_error_not_shorter_audio(tmp_path):
    data = bytearray(make_m4a(tmp_path / "w.m4a", [sine(440, 44100)]).read_bytes())
    third = len(data) // 3
    data[third : 2 * third] = bytes(third)  # 오디오 데이터 가운데를 0으로 덮는다
    damaged = tmp_path / "mid.m4a"
    damaged.write_bytes(data)
    with pytest.raises(DecodeError):
        decode_recording(damaged)


def test_cut_off_end_is_decode_error(tmp_path):
    data = make_m4a(tmp_path / "w.m4a", [sine(440, 44100)]).read_bytes()
    cut = tmp_path / "tail.m4a"
    cut.write_bytes(data[: len(data) // 2])
    with pytest.raises(DecodeError):
        decode_recording(cut)


def test_decode_error_message_hides_input_path(tmp_path):
    folder = tmp_path / "SECRET-TOKEN"
    folder.mkdir()
    junk = folder / "junk.m4a"
    junk.write_bytes(bytes(range(256)) * 16)
    with pytest.raises(DecodeError) as error:
        decode_recording(junk)
    assert "SECRET-TOKEN" not in str(error.value)


def test_missing_file_is_not_a_decode_error(tmp_path):
    with pytest.raises(FileNotFoundError) as error:
        decode_recording(tmp_path / "SECRET-TOKEN.m4a")
    assert not isinstance(error.value, DecodeError)
    assert "SECRET-TOKEN" not in str(error.value)


def test_url_is_not_fetched_and_not_echoed():
    url = "http://127.0.0.1:9/recordings/1.m4a?X-Amz-Signature=SECRET-TOKEN"
    with pytest.raises(FileNotFoundError) as error:
        decode_recording(url)
    assert "SECRET-TOKEN" not in str(error.value)


def test_timeout_hides_input_path(tmp_path):
    folder = tmp_path / "SECRET-TOKEN"
    folder.mkdir()
    src = make_m4a(folder / "w.m4a", [sine(440, 44100)])
    with pytest.raises(TimeoutError) as error:
        decode_recording(src, timeout_s=0.001)
    assert "SECRET-TOKEN" not in str(error.value)


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
