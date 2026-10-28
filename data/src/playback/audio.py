"""Persist synthesized mono PCM as AAC-LC/M4A or optional PCM16/WAV."""
import json
import os
from pathlib import Path
import shutil
import subprocess

import numpy as np
import soundfile as sf

from .sampler import FS, sha
from .score import require

FORMATS = ("m4a", "wav")
AAC_BIT_RATE = 192_000
AAC_FRAME_SAMPLES = 1024


def command(args, payload=None, timeout=180):
    try:
        result = subprocess.run(
            [str(arg) for arg in args], input=payload, capture_output=True,
            timeout=timeout, check=False,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ValueError(f"오디오 도구 실행 실패 ({Path(args[0]).name}): {exc}") from exc
    if result.returncode:
        message = result.stderr.decode("utf-8", errors="replace").strip()[-2000:]
        raise ValueError(f"오디오 도구 실패 ({Path(args[0]).name}): {message}")
    return result.stdout


def resolve_encoder(ffmpeg=None, ffprobe=None):
    """Resolve once per batch; never download or change the user's PATH."""
    encoder = {}
    for name, requested in (("ffmpeg", ffmpeg), ("ffprobe", ffprobe)):
        requested = requested or os.environ.get(f"{name.upper()}_BINARY") or name
        path = shutil.which(str(requested))
        require(path is not None, f"{name} 실행 파일이 없습니다. 설치 후 --{name} 경로를 지정하세요")
        encoder[name] = str(Path(path).resolve())
        version = command([path, "-version"], timeout=15).decode("utf-8", errors="replace")
        require(version.startswith(f"{name} version "), f"{name} 실행 파일을 확인하세요")
        encoder[f"{name}_version"] = version.splitlines()[0]
    return encoder


def validate_m4a(path, frames, encoder):
    metadata = json.loads(command([
        encoder["ffprobe"], "-v", "error", "-show_streams", "-show_format",
        "-of", "json", path,
    ]))
    streams = metadata.get("streams", [])
    require(len(streams) == 1, "M4A에는 오디오 스트림 하나만 있어야 합니다")
    stream = streams[0]
    require(stream.get("codec_name") == "aac" and stream.get("profile") == "LC",
            "M4A AAC-LC 코덱 검증 실패")
    require(int(stream.get("sample_rate", 0)) == FS and stream.get("channels") == 1,
            "M4A 샘플레이트·채널 검증 실패")
    duration = float(stream.get("duration", 0))
    require(np.isfinite(duration) and abs(duration - frames / FS) <= 2 / FS,
            "M4A 표시 길이가 합성 PCM과 다릅니다")
    require(abs(float(stream.get("start_time", 0))) <= 1 / FS,
            "M4A 재생 시작 시각이 0이 아닙니다")
    payload = command([
        encoder["ffmpeg"], "-hide_banner", "-loglevel", "error", "-nostdin",
        "-i", path, "-map", "0:a:0", "-f", "f32le", "-c:a", "pcm_f32le", "pipe:1",
    ])
    require(len(payload) % 4 == 0, "M4A 디코딩 PCM이 손상됐습니다")
    decoded = np.frombuffer(payload, dtype="<f4")
    # Edit-list priming is removed by the decoder; a final AAC block can retain
    # up to 1023 padding samples. Keep source duration separate from this count.
    require(frames <= len(decoded) < frames + AAC_FRAME_SAMPLES,
            "M4A 디코딩 길이가 AAC 끝 패딩 허용 범위를 벗어났습니다")
    require(np.isfinite(decoded).all() and np.max(np.abs(decoded)) < .99,
            "M4A 디코딩 PCM 유한값·피크 검증 실패")
    return {
        "codec": "aac", "profile": "LC", "container_seconds": duration,
        "requested_bit_rate": AAC_BIT_RATE,
        "encoded_bit_rate": int(stream.get("bit_rate", 0)),
        "decoded_frames": len(decoded), "end_padding_frames": len(decoded) - frames,
        "peak": float(np.max(np.abs(decoded))),
        "rms": float(np.sqrt(np.mean(decoded.astype(np.float64) ** 2))),
    }


def write_audio(audio, path, encoder=None):
    """Validate the staged artifact before the caller publishes any outputs."""
    path = Path(path)
    kind = path.suffix.removeprefix(".")
    require(kind in FORMATS, "오디오 출력 형식은 m4a 또는 wav여야 합니다")
    require(audio.ndim == 1 and len(audio) > 0 and np.isfinite(audio).all()
            and np.max(np.abs(audio)) < .99, "합성 PCM 유한값·채널·피크 검증 실패")
    frames = len(audio)
    if kind == "m4a":
        require(encoder is not None, "M4A 생성에는 FFmpeg·ffprobe가 필요합니다")
        command([
            encoder["ffmpeg"], "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
            "-f", "f32le", "-ar", str(FS), "-ac", "1", "-i", "pipe:0",
            "-map", "0:a:0", "-map_metadata", "-1", "-c:a", "aac",
            "-profile:a", "aac_low", "-b:a", str(AAC_BIT_RATE), "-threads", "1",
            "-movflags", "+faststart", "-movie_timescale", str(FS), "-f", "ipod", path,
        ], np.asarray(audio, dtype="<f4").tobytes())
        details = validate_m4a(path, frames, encoder)
    else:
        sf.write(path, audio, FS, subtype="PCM_16")
        decoded, rate = sf.read(path, dtype="float64")
        require(rate == FS and decoded.ndim == 1 and len(decoded) == frames
                and np.isfinite(decoded).all() and np.max(np.abs(decoded)) < .99,
                "WAV validation failed")
        details = {"codec": "pcm_s16le", "decoded_frames": frames,
                   "container_seconds": frames / FS, "end_padding_frames": 0,
                   "peak": float(np.max(np.abs(decoded))),
                   "rms": float(np.sqrt(np.mean(decoded ** 2)))}
    return {
        "file": path.name, "format": kind, "sha256": sha(path),
        "bytes": path.stat().st_size, "sample_rate": FS, "channels": 1,
        "frames": frames, "seconds": frames / FS, "frame_basis": "synthesized_pcm",
        **details,
    }


def add_audio_arguments(parser):
    parser.add_argument("--audio-format", choices=FORMATS, default="m4a",
                        help="Default: AAC-LC 192kbps M4A; WAV is optional verification output")
    parser.add_argument("--ffmpeg", help="FFmpeg executable; otherwise FFMPEG_BINARY or PATH")
    parser.add_argument("--ffprobe", help="ffprobe executable; otherwise FFPROBE_BINARY or PATH")
