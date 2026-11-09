"""Prepare KOGL-1 fingering M4A candidates from pinned catalog recordings."""

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import soundfile as sf

from .__main__ import dump
from .audio import command, resolve_encoder, write_audio
from .fingering_reference import NOTES, HOLD_SECONDS, hz
from .sampler import FS
from .score import require
from .verify_common import decode_audio, fundamental


# Previously published reference-audio IDs and exact content hashes.
SOURCES = {
    "ngc-danso-2338.tone-005": ("fa12c5e492fd28e6209b02356c75dcd1b4ae8cfccbbe4ffe1e8c715a71d0f451",
                              "431b1a4f01c011696cc3e3524c27252b603b8c0c4dbf6823f8c911f240c568cc"),
    "ngc-danso-2341.tone-001": ("ac3dff4aec32b8b9fa8ff852a9ef733a8c00e3f1fcb6e200ce4d39e939fbefcf",
                              "5c65a68cee5426a6f6d4773ece777b5c99203a128a76b0979206808a6712990a"),
    "ngc-danso-2341.tone-003": ("9beb0041bcbd96434348589bbafbe215fe3539cb02c4c19d2bf9accf19b99ffa",
                              "7ae307e75d05b978247aa5ae258d32d1c73981d34dbc18a9a626923094819453"),
    "ngc-danso-2341.tone-005": ("07ea9e93c16de6ec3291b6d7e22465f4c2d8e60dfa57dad6d50408827a3ce045",
                              "9ae9942deb068b79c7965ffc9b1dea05d094c861f2c1f1eaedfe288e2d2d49ea"),
    "sanjo.yeokchwi.mu": ("3515d502957fbdc16b4047d9b60719788958a9b735cd3d8141f87f1c6f8ff7ed",
                         "8df0ff782b8344f6dcfd84cddfa7db38d6a9c469e53ccde6e3143e3dffab991a"),
}
SOURCE_BY_TARGET = {
    78: "ngc-danso-2338.tone-005", 79: "ngc-danso-2341.tone-001", 84: "ngc-danso-2341.tone-003",
    90: "ngc-danso-2341.tone-005", 92: "ngc-danso-2341.tone-005", 91: "sanjo.yeokchwi.mu",
}
ORIGINAL_PAGE = "https://www.gugak.go.kr/digitaleum/front/monotone/list.do"
LICENSE_PAGE = "https://www.kogl.or.kr/info/introduce.do"
YOSEONG_HZ = 4
YOSEONG_CENTS = 50
PROCESSING = "기존 녹음에서 구간 선택, 목표음으로 음높이 변환, 페이드·레벨 조정, 44.1 kHz 모노 PCM16 및 AAC-LC 변환"
YOSEONG_PROCESSING = "기본 가공에 교육용 요성(4 Hz, 최대 ±50센트) 추가"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def load_source(source_dir, label_id, encoder):
    asset_id, expected = SOURCES[label_id]
    m4a = source_dir / f"{label_id}.m4a"
    detail_path = source_dir / f"{label_id}.json"
    detail = json.loads(detail_path.read_text(encoding="utf-8"))["result"]
    asset = detail["asset"]
    require(asset["assetId"] == asset_id and asset["labelId"] == label_id,
            f"{label_id}: source detail identity mismatch")
    require(digest(m4a) == expected and asset["sha256"] == expected,
            f"{label_id}: source audio digest mismatch")
    label, source = (detail["metadata"][name] for name in ("label", "source"))
    require(source["license"] == "KOGL-1" and source["sourceUrl"] == ORIGINAL_PAGE and source["attribution"],
            f"{label_id}: source reuse terms mismatch")
    raw = command([encoder["ffmpeg"], "-v", "error", "-nostdin", "-i", m4a, "-map", "0:a:0",
                   "-ar", str(FS), "-ac", "1", "-f", "f32le", "-c:a", "pcm_f32le", "pipe:1"])
    audio = np.frombuffer(raw, dtype="<f4")
    nominal = label["evidence"]["labelF0Hz"]
    return detail, audio, FS, nominal, digest(detail_path)


def shift_clip(audio, rate, ratio, yoseong):
    samples = HOLD_SECONDS * rate
    times = np.arange(samples) / rate
    ratios = np.full(samples, ratio)
    if yoseong:
        ramp = np.clip(np.minimum(times, HOLD_SECONDS - times) / .15, 0, 1)
        pitch = YOSEONG_CENTS * ramp * np.sin(2 * np.pi * YOSEONG_HZ * times)
        ratios *= np.exp2(pitch / 1200)
    positions = np.cumsum(ratios) - ratios[0]
    require(positions[-1] < len(audio) - 1, "Source is shorter than the resampled clip")
    result = np.interp(positions, np.arange(len(audio)), audio)
    require(np.max(np.abs(result)) > .02, "Source clip is silent")
    result *= np.clip(np.minimum(times / .015, (HOLD_SECONDS - times) / .12), 0, 1)
    result *= .8 / np.max(np.abs(result))
    return result


def measure(audio, rate, target):
    times = np.arange(.35, 2.65, .01)
    pitches = np.asarray([fundamental(audio[round((time - .015) * rate):round((time + .015) * rate)], rate)[0]
                          or np.nan for time in times])
    good = np.isfinite(pitches)
    require(np.mean(good) >= .95, "Insufficient periodic pitch windows")
    times = times[good]
    cents = 1200 * np.log2(pitches[good] / target)
    harmonics = np.column_stack((np.ones(len(times)), times,
                                 np.sin(2 * np.pi * YOSEONG_HZ * times),
                                 np.cos(2 * np.pi * YOSEONG_HZ * times)))
    coefficients = np.linalg.lstsq(harmonics, cents, rcond=None)[0]
    return {"medianCents": float(np.median(cents)), "medianHz": float(np.median(pitches[good])),
            "fourHzCents": float(np.linalg.norm(coefficients[-2:])), "validWindows": int(good.sum())}


def build(source_dir, out):
    encoder = resolve_encoder()
    require(not out.exists() and not out.is_symlink(), "Use a new derivative output directory")
    clips = out / "m4a"
    clips.mkdir(parents=True)
    wavs = out / "wav"
    wavs.mkdir()
    assets, verification = [], {}
    for instrument, suffix, yul, hwang_midi, midi in NOTES:
        if instrument == "sogeum":
            continue  # The catalog has both real recordings for 역취 태.
        original_label_id = SOURCE_BY_TARGET[midi]
        detail, source_pcm, rate, nominal, detail_hash = load_source(source_dir, original_label_id, encoder)
        original = detail["metadata"]["source"]
        original_asset = detail["asset"]
        require(original_asset["datasetId"] == instrument, "Source instrument differs from target instrument")
        initial_ratio = hz(midi) / nominal
        preview = shift_clip(source_pcm, rate, initial_ratio, False)
        center = measure(preview, rate, hz(midi))["medianCents"]
        ratio = initial_ratio / 2 ** (center / 1200)
        for variant, yoseong in (("basic", False), ("yoseong", True)):
            label_id = f"eolssu-603-{instrument}-{suffix}-{variant}"
            processing = f"{PROCESSING}. {YOSEONG_PROCESSING}" if yoseong else PROCESSING
            credit = f"{original['attribution']}. 가공: {processing}. 원본: {ORIGINAL_PAGE}"
            wav = wavs / f"{label_id}.wav"
            wav_meta = write_audio(shift_clip(source_pcm, rate, ratio, yoseong), wav)
            rendered_pcm, _ = sf.read(wav)
            m4a = clips / f"{label_id}.m4a"
            audio = write_audio(rendered_pcm, m4a, encoder, credit=credit)
            decoded, _, _, _ = decode_audio(m4a, encoder["ffmpeg"], encoder["ffprobe"])
            metrics = measure(decoded, rate, hz(midi))
            require(abs(metrics["medianCents"]) <= 12, f"{label_id}: center misses target pitch")
            amplitude = metrics["fourHzCents"]
            if yoseong:
                require(38 <= amplitude <= 62 and amplitude - baseline >= 33,
                        f"{label_id}: intended yoseong was not observed")
            else:
                require(amplitude <= 8, f"{label_id}: unexpected 4 Hz modulation")
                baseline = amplitude
            verification[label_id] = metrics
            classification = {"category": "기본음계", "technique": f"{'요성' if yoseong else '기본음'} · 음높이 가공",
                              "register": "역취", "yulmyeong": yul, "displayLabel": f"역취 {yul} (음높이 가공)",
                              "review": "inferred", "eligibleForNoteScoring": False}
            pcm_info = {"sha256": wav_meta["sha256"], "bytes": wav_meta["bytes"], "frames": wav_meta["frames"],
                        "sampleRateHz": rate, "channels": 1}
            label = {"id": label_id, "sourceId": original["id"], **classification, **pcm_info,
                     "processing": processing}
            asset_id = hashlib.sha256(f"{instrument}\n{label_id}".encode()).hexdigest()
            checksum = audio["sha256"]
            assets.append({"assetId": asset_id, "datasetId": instrument, "labelId": label_id, **classification,
                           "file": m4a.relative_to(out).as_posix(),
                           "objectKey": f"reference-audio/v1/{instrument}/{asset_id}/{checksum}.m4a",
                           "contentType": "audio/mp4", "sha256": checksum, "bytes": audio["bytes"],
                           "durationMs": round(audio["container_seconds"] * 1000), "sampleRateHz": rate,
                           "channels": 1, "metadata": {
                               "label": label, "source": original, "wav": pcm_info,
                               "catalogSha256": detail["metadata"]["catalogSha256"],
                               "encoding": {"codec": audio["codec"], "profile": audio["profile"],
                                            "decodedFrames": audio["decoded_frames"],
                                            "endPaddingFrames": audio["end_padding_frames"]},
                               "derivative": {"originalAssetId": original_asset["assetId"], "originalLabelId": original_label_id,
                                              "originalM4aSha256": original_asset["sha256"], "originalDetailSha256": detail_hash,
                                              "originalYulmyeong": original_asset["yulmyeong"], "originalRegister": original_asset["register"],
                                              "originalF0Hz": nominal, "sourceLicenseUrl": LICENSE_PAGE,
                                              "targetHwangMidi": hwang_midi, "targetMidi": midi, "targetHz": hz(midi),
                                              "shiftSemitones": float(12 * np.log2(ratio)), "processing": processing,
                                              "syntheticYoseong": yoseong, "verification": metrics},
                           }})
    dump(out / "manifest.json", {"schemaVersion": 1, "assets": assets})
    dump(out / "verification.json", verification)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", type=Path, required=True, help="Pinned audio and API detail JSON files")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    build(args.sources, args.out)


if __name__ == "__main__":
    main()
