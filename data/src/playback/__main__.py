"""python -m data.src.playback --score ... --assets ... --out ..."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import tempfile
import numpy as np
import soundfile as sf
from .score import PROFILES, compile_score, require
from .midi import PLAYBACK_VIBRATO, RELEASE_CC, write_midi, read_midi
from .sampler import CatSampler, FS, CREDIT, sha
from .jgb import convert_jgb, SCALES
from .audio import FORMATS, AAC_BIT_RATE, add_audio_arguments, resolve_encoder, write_audio


def dump(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")


def run(score_path, assets_path, out, instruments, transpose=0, *, part=None, bpm=None,
        speed=1, hwang_midi=None, jo=None, audio_format="m4a", ffmpeg=None, ffprobe=None,
        encoder=None):
    require(audio_format in FORMATS, "오디오 출력 형식은 m4a 또는 wav여야 합니다")
    input_bytes = Path(score_path).read_bytes()
    input_hash = hashlib.sha256(input_bytes).hexdigest()
    source = json.loads(input_bytes.decode("utf-8-sig"))
    source_map = None
    is_jgb = Path(score_path).name.endswith(".jgb.json") or isinstance(source, dict) and "v" in source
    if is_jgb:
        require(instruments == ["danso"], "JGB 입력은 단소 한 성부만 지원합니다")
        score, source_map = convert_jgb(source, source_name=Path(score_path).name, part=part,
                                       bpm=bpm, speed=speed, hwang_midi=hwang_midi, jo=jo)
        source_map["input_sha256"] = input_hash
        score["source"]["jgb_sha256"] = source_map["input_sha256"]
    else:
        require(part is None and bpm is None and speed == 1 and hwang_midi is None and jo is None,
                "--part/--bpm/--speed/--hwang-midi/--jo는 JGB 입력용입니다")
        score = source
    assets = json.loads(Path(assets_path).read_text(encoding="utf-8-sig"))
    out = Path(out)
    outputs = ["score.json", "manifest.json"]
    outputs += [f"{key}.{suffix}" for key in instruments for suffix in ("mid", audio_format, "json")]
    if is_jgb:
        outputs += ["source.jgb.json", "source-map.json"]
    targets = {(out / name).resolve() for name in outputs}
    require(Path(score_path).resolve() not in targets and Path(assets_path).resolve() not in targets,
            "출력 파일이 원본 입력 또는 assets 파일과 겹칩니다. 다른 --out 폴더를 사용하세요")
    require(is_jgb or not any((out / name).exists() for name in ("source.jgb.json", "source-map.json")),
            "이 폴더에 이전 JGB 원본·대응표가 있습니다. 일반 악보 JSON은 다른 --out 폴더에 생성하세요")
    other_format = "wav" if audio_format == "m4a" else "m4a"
    require(not any((out / f"{key}.{other_format}").exists() for key in instruments),
            "출력 폴더에 이전 형식의 음원이 있습니다. 새 --out 폴더를 사용하세요")
    encoder = (encoder or resolve_encoder(ffmpeg, ffprobe)) if audio_format == "m4a" else None
    out.mkdir(parents=True, exist_ok=True)
    reports, renders = [], []
    # Validate staged artifacts before replacing previous conversion outputs.
    with tempfile.TemporaryDirectory(prefix="playback-", dir=out) as tmp:
        stage = Path(tmp)
        for key in instruments:
            perf = compile_score(score, key, transpose)
            require(key in assets, f"Missing asset mapping for {key}")
            archive = Path(assets[key])
            if not archive.is_absolute():
                archive = Path(assets_path).resolve().parent / archive
            sampler = CatSampler(archive, key)
            try:
                mid = stage/f"{key}.mid"
                write_midi(perf, mid)
                playback = read_midi(mid)
                require(len(playback["notes"]) == len(perf["events"]), "MIDI event count changed")
                for n, e in zip(playback["notes"], perf["events"]):
                    require(n["pitch"] == e["pitch"] and n["velocity"] == e["velocity"], "MIDI event changed")
                    require(abs(n["start"]-e["start_tick"]*perf["tempo_us"]/1e6/perf["ppqn"]) < 1e-8, "MIDI onset changed")
                audio, trace = sampler.render(playback)
                reports.append({**perf, "playback_vibrato":dict(PLAYBACK_VIBRATO),
                                "notes":len(trace), "seconds":len(audio)/FS,
                                "sampler":sampler.manifest, "render_trace":trace})
                renders.append(audio)
            finally:
                sampler.close()
        # One shared audition gain preserves relative preset level differences.
        peak = max(float(np.max(np.abs(x))) for x in renders)
        gain = .85/peak
        for report, audio in zip(reports, renders):
            key = report["instrument"]
            audio_path = stage/f"{key}.{audio_format}"
            report["audio"] = {**write_audio(audio*gain, audio_path, encoder), "shared_gain":gain,
                               "midi_sha256":sha(stage/f"{key}.mid"),
                               f"{audio_format}_sha256":sha(audio_path)}
            dump(stage/f"{key}.json", report)
        manifest = {"input_sha256":input_hash, "credit":CREDIT, "engine":"custom CAT sample player, not Ableton/Kontakt/Sori",
                    "release_controller":dict(RELEASE_CC),
                    "audio_output":{"format":audio_format,
                                    "requested_bit_rate":AAC_BIT_RATE if encoder else None,
                                    "encoder":{k:v for k,v in encoder.items() if k.endswith("_version")} if encoder else None},
                    "python":platform.python_version(), "numpy":np.__version__, "soundfile":sf.__version__,
                    "playback_vibrato":dict(PLAYBACK_VIBRATO),
                    "source_sha256":{name:sha(Path(__file__).parent/name) for name in ["score.py","midi.py","sampler.py","audio.py","__main__.py","jgb.py"]},
                    "profile_sha256":{path.name:sha(path) for path in sorted(Path(__file__).with_name("profiles").glob("*.json"))},
                    "results":[{"instrument":r["instrument"],"notes":r["notes"],"seconds":r["seconds"],"audio":r["audio"]} for r in reports],
                    "limitations":["JGB v1/v2 import supports one selected danso part and the documented notation subset.",
                    "Same sounding melody audition, not instrument-specific arrangement or expert-validated model performance.",
                    "Linear sample interpolation, mono mixdown, held MIDI CC11 and envelope/legato approximations.",
                    "No full Ableton filter/FX/velocity emulation. Sample tuning not exhaustively measured.",
                    "CAT raw samples stay local; check applicable distribution/service terms separately."]}
        dump(stage/"score.json", score)
        if source_map is not None:
            (stage/"source.jgb.json").write_bytes(input_bytes)
            dump(stage/"source-map.json", source_map)
            manifest["jgb"] = {k:v for k,v in source_map.items() if k != "notes"}
            manifest["normalized_score_sha256"] = sha(stage/"score.json")
            manifest["source_map_sha256"] = sha(stage/"source-map.json")
        dump(stage/"manifest.json", manifest)
        for path in stage.iterdir():
            path.replace(out/path.name)
    return manifest


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--score", required=True)
    p.add_argument("--assets", required=True, help="JSON: instrument key → local v1.3 CAT installer ZIP")
    p.add_argument("--out", required=True)
    p.add_argument("--instrument", choices=["all", *PROFILES], default="all")
    p.add_argument("--transpose", type=int, default=0, help="Explicit semitone audition transposition; never automatic")
    p.add_argument("--part", type=int, help="JGB part number, starting at 1; required for multiple parts")
    p.add_argument("--bpm", type=float, help="JGB tempo override in jeonggan cells per minute")
    p.add_argument("--speed", type=float, default=1, help="JGB tempo multiplier (0.25..4), default 1")
    p.add_argument("--hwang-midi", type=int, help="JGB huangjong MIDI override; otherwise use hwangPitch")
    p.add_argument("--jo", choices=list(SCALES), help="Explicit JGB scale for relative ornaments")
    add_audio_arguments(p)
    a = p.parse_args()
    try:
        result = run(a.score, a.assets, a.out, list(PROFILES) if a.instrument == "all" else [a.instrument],
                     a.transpose, part=a.part, bpm=a.bpm, speed=a.speed, hwang_midi=a.hwang_midi, jo=a.jo,
                     audio_format=a.audio_format, ffmpeg=a.ffmpeg, ffprobe=a.ffprobe)
    except ValueError as exc:
        p.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
