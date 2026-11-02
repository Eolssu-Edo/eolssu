"""python -m data.src.playback --score ... --assets ... --out ..."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import tempfile
import time
import uuid
import numpy as np
import soundfile as sf
from .score import PROFILES, compile_score, require
from .midi import RELEASE_CC, write_midi, read_midi
from .sampler import CatSampler, FS, CREDIT, sha
from .jgb import convert_jgb
from .common_score import convert_common_score
from .expressions import SCALES, playback_vibrato
from .audio import FORMATS, AAC_BIT_RATE, add_audio_arguments, resolve_encoder, write_audio


def dump(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")


def replace_directory(source, target):
    """Retry transient Windows directory locks without weakening bundle rollback."""
    for attempt in range(5):
        try:
            source.replace(target)
            return
        except PermissionError as exc:
            if getattr(exc, "winerror", None) not in (5, 32, 33) or attempt == 4:
                raise
            time.sleep(.1 * 2 ** attempt)


def publish(stage, out, outputs):
    """Replace a result directory as a bundle; restore it on a rename failure."""
    require(not out.is_symlink(), "출력 폴더의 심볼릭 링크는 지원하지 않습니다")
    require(not out.exists() or out.is_dir(), "출력 경로는 폴더여야 합니다")
    if out.exists():
        require(all(p.is_file() and p.name in outputs for p in out.iterdir()),
                "출력 폴더에 다른 파일/폴더가 있습니다. 변환 전용 --out 폴더를 사용하세요")
    backup = out.with_name(f".{out.name}-previous-{uuid.uuid4().hex}")
    had_previous = out.exists()
    if had_previous:
        replace_directory(out, backup)
    try:
        replace_directory(stage, out)
    except OSError:
        if had_previous:
            replace_directory(backup, out)
        raise
    if had_previous:
        # Only the validated previous conversion bundle is removed. If a reader
        # still holds a file on Windows, leave its backup for manual cleanup.
        shutil.rmtree(backup, ignore_errors=True)


def run(score_path, assets_path, out, instruments, transpose=0, *, part=None, bpm=None,
        speed=1, hwang_midi=None, jo=None, audio_format="m4a", ffmpeg=None, ffprobe=None,
        encoder=None, max_duration_seconds=600, performance_plan=None, expression_profile=None,
        assume_danso=False, common_bpm=None, notation_policy=None, common_expressions=None):
    require(audio_format in FORMATS, "오디오 출력 형식은 m4a 또는 wav여야 합니다")
    input_bytes = Path(score_path).read_bytes()
    input_hash = hashlib.sha256(input_bytes).hexdigest()
    source = json.loads(input_bytes.decode("utf-8-sig"))
    configs = {}
    for name, path in (("performance-plan.json", performance_plan), ("expression-profile.json", expression_profile),
                       ("expressions.common.json", common_expressions)):
        if path is not None:
            payload = Path(path).read_bytes()
            value = json.loads(payload.decode("utf-8-sig"))
            require(isinstance(value, dict), f"{name}: JSON 객체가 필요합니다")
            configs[name] = {"path": Path(path).resolve(), "bytes": payload,
                             "value": value,
                             "sha256": hashlib.sha256(payload).hexdigest()}
    source_map = None
    is_jgb = Path(score_path).name.endswith(".jgb.json") or isinstance(source, dict) and "v" in source
    is_common = isinstance(source, dict) and "schemaVersion" in source
    require(not (is_jgb and is_common), "JGB와 공통 악보 형식이 섞여 있습니다")
    require(is_common or common_bpm is None, "--common-bpm은 서비스 공통 악보 입력용입니다")
    require(is_common or (notation_policy is None and common_expressions is None),
            "--notation-policy/--common-expressions는 공통 악보 입력용입니다; JGB는 playback.notation_policy를 사용하세요")
    if is_jgb:
        require(len(instruments) == 1, "JGB 입력은 악기 하나와 한 성부를 선택해야 합니다")
        score, source_map = convert_jgb(source, source_name=Path(score_path).name, part=part,
                                       bpm=bpm, speed=speed, hwang_midi=hwang_midi, jo=jo,
                                       performance_plan=configs.get("performance-plan.json", {}).get("value"),
                                       input_sha256=input_hash,
                                       expression_profile=configs.get("expression-profile.json", {}).get("value"),
                                       assume_danso=assume_danso, instrument=instruments[0])
        source_map["input_sha256"] = input_hash
        score["source"]["jgb_sha256"] = source_map["input_sha256"]
    else:
        require(part is None and bpm is None and speed == 1 and hwang_midi is None and jo is None
                and performance_plan is None and expression_profile is None and not assume_danso,
                "성부·빠르기·황종·조·연주 계획·표현 프로필·단소 선택 옵션은 JGB 입력용입니다")
        if is_common:
            require(len(instruments) == 1, "공통 악보 입력은 --instrument로 원본 악기 하나를 선택하세요")
            score, source_map = convert_common_score(source, bpm=common_bpm, instrument=instruments[0],
                                                     input_sha256=input_hash, notation_policy=notation_policy,
                                                     expressions=configs.get('expressions.common.json', {}).get('value'))
            source_map["transpose_semitones"] = transpose
        else:
            score = source
    assets = json.loads(Path(assets_path).read_text(encoding="utf-8-sig"))
    out = Path(out).absolute()
    outputs = ["score.json", "manifest.json"]
    outputs += [f"{key}.{suffix}" for key in instruments for suffix in ("mid", audio_format, "json")]
    if is_jgb:
        outputs += ["source.jgb.json", "source-map.json"]
    if is_common:
        outputs += ["source.common.json", "source-map.json"]
        if notation_policy is not None:
            outputs += ["performance.common.json"]
    outputs += list(configs)
    targets = {(out / name).resolve() for name in outputs}
    require(Path(score_path).resolve() not in targets and Path(assets_path).resolve() not in targets
            and not any(config["path"] in targets for config in configs.values()),
            "출력 파일이 원본 입력 또는 assets 파일과 겹칩니다. 다른 --out 폴더를 사용하세요")
    require(is_jgb or is_common or not any((out / name).exists() for name in ("source.jgb.json", "source.common.json", "source-map.json")),
            "이 폴더에 이전 JGB 원본·대응표가 있습니다. 일반 악보 JSON은 다른 --out 폴더에 생성하세요")
    other_format = "wav" if audio_format == "m4a" else "m4a"
    require(not any((out / f"{key}.{other_format}").exists() for key in instruments),
            "출력 폴더에 이전 형식의 음원이 있습니다. 새 --out 폴더를 사용하세요")
    encoder = (encoder or resolve_encoder(ffmpeg, ffprobe)) if audio_format == "m4a" else None
    out.parent.mkdir(parents=True, exist_ok=True)
    reports, renders = [], []
    # Validate staged artifacts before replacing previous conversion outputs.
    with tempfile.TemporaryDirectory(prefix=f".{out.name}-playback-", dir=out.parent) as tmp:
        stage = Path(tmp)
        for key in instruments:
            perf = compile_score(score, key, transpose, max_duration_seconds=max_duration_seconds)
            if is_common:
                source_map["midi_score_seconds"] = perf["end_tick"] * perf["tempo_us"] / 1e6 / perf["ppqn"]
                if notation_policy is not None:
                    require(abs(source_map['performanceTimeline']['durationMs'] / 1000 - source_map['midi_score_seconds']) < 1e-6,
                            '수행 시간표와 컴파일된 MIDI 길이가 다릅니다')
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
                reports.append({**perf, "playback_vibrato":playback_vibrato(key),
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
                    "resource_limits":{"max_duration_seconds":max_duration_seconds},
                    "release_controller":dict(RELEASE_CC),
                    "audio_output":{"format":audio_format,
                                    "requested_bit_rate":AAC_BIT_RATE if encoder else None,
                                    "encoder":{k:v for k,v in encoder.items() if k.endswith("_version")} if encoder else None},
                    "python":platform.python_version(), "numpy":np.__version__, "soundfile":sf.__version__,
                    "playback_vibrato":playback_vibrato(instruments[0]) if len(instruments) == 1 else None,
                    "playback_vibrato_by_instrument":{key:playback_vibrato(key) for key in instruments},
                    "source_sha256":{name:sha(Path(__file__).parent/name) for name in ["score.py","midi.py","sampler.py","audio.py","__main__.py","jgb.py","common_score.py","common_preview.py","expressions.py","preview.py","tempo_repeat.py"]},
                    "profile_sha256":{path.name:sha(path) for path in sorted(Path(__file__).with_name("profiles").glob("*.json"))},
                    "results":[{"instrument":r["instrument"],"notes":r["notes"],"seconds":r["seconds"],"audio":r["audio"]} for r in reports],
                    "limitations":["JGB v1/v2 import supports one selected instrument part and the documented notation subset.",
                    "Same sounding melody audition, not instrument-specific arrangement or expert-validated model performance.",
                    "Linear sample interpolation, mono mixdown, held MIDI CC11 and envelope/legato approximations.",
                    "No full Ableton filter/FX/velocity emulation. Sample tuning not exhaustively measured.",
                    "CAT raw samples stay local; check applicable distribution/service terms separately."]}
        dump(stage/"score.json", score)
        for name, config in configs.items():
            (stage/name).write_bytes(config["bytes"])
        manifest["configuration_sha256"] = {name: config["sha256"] for name, config in configs.items()}
        if source_map is not None:
            (stage/("source.common.json" if is_common else "source.jgb.json")).write_bytes(input_bytes)
            if is_common and notation_policy is not None:
                timeline = source_map['performanceTimeline']
                timeline['audioSha256'] = reports[0]['audio'][f'{audio_format}_sha256']
                dump(stage/'performance.common.json', timeline)
                manifest['performance_timeline_sha256'] = sha(stage/'performance.common.json')
            dump(stage/"source-map.json", source_map)
            manifest["common_score" if is_common else "jgb"] = {
                k:v for k,v in source_map.items() if k not in ("notes", "events", "expression_source_map", "performanceTimeline")}
            manifest["normalized_score_sha256"] = sha(stage/"score.json")
            manifest["source_map_sha256"] = sha(stage/"source-map.json")
        dump(stage/"manifest.json", manifest)
        publish(stage, out, outputs)
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
    p.add_argument("--common-bpm", type=float, help="Service score v1 BPM in music.bpmUnit beats; required for common scores")
    p.add_argument("--notation-policy", choices=['educational-preview/v1'], help="Opt-in educational defaults for common-score ornaments")
    p.add_argument("--common-expressions", help="SHA256-bound common-score expression placements and optional performance choices")
    p.add_argument("--speed", type=float, default=1, help="JGB tempo multiplier (0.25..4), default 1")
    p.add_argument("--hwang-midi", type=int, help="JGB huangjong MIDI override; otherwise use hwangPitch")
    p.add_argument("--jo", choices=list(SCALES), help="Explicit JGB scale for relative ornaments")
    p.add_argument("--max-duration-seconds", type=float, default=600,
                   help="Memory/resource limit, not a musical duration rule; default 600")
    p.add_argument("--performance-plan", help="Local tempo/repeat plan JSON bound to the source SHA256")
    p.add_argument("--expression-profile", help="Local explicit expression parameters and their basis")
    p.add_argument("--assume-danso", action="store_true", help="Explicitly select danso for an instrument=all part")
    add_audio_arguments(p)
    a = p.parse_args()
    try:
        result = run(a.score, a.assets, a.out, list(PROFILES) if a.instrument == "all" else [a.instrument],
                     a.transpose, part=a.part, bpm=a.bpm, speed=a.speed, hwang_midi=a.hwang_midi, jo=a.jo,
                     audio_format=a.audio_format, ffmpeg=a.ffmpeg, ffprobe=a.ffprobe,
                     max_duration_seconds=a.max_duration_seconds, performance_plan=a.performance_plan,
                     expression_profile=a.expression_profile, assume_danso=a.assume_danso, common_bpm=a.common_bpm,
                     notation_policy=a.notation_policy, common_expressions=a.common_expressions)
    except ValueError as exc:
        p.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
