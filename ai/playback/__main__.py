"""python -m playback --score ... --assets ... --out ..."""
import argparse
import html
import json
from pathlib import Path
import platform
import tempfile
import numpy as np
import soundfile as sf
from .score import PROFILES, compile_score, require
from .expression import apply_expression
from .midi import write_midi, read_midi
from .sampler import CatSampler, FS, CREDIT, sha


def dump(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")


def audition_page(reports):
    cards = []
    for report in reports:
        key = report["instrument"]
        warnings = " ".join(report["warnings"])
        cards.append(f'''<section><h2>{html.escape(PROFILES[key]['label'])}</h2>
        <p>{report['notes']}음 · {report['seconds']:.2f}초</p>
        <audio controls preload="metadata" src="{key}.wav"></audio>
        <p>{html.escape(warnings)}</p><a href="{key}.mid" download>MIDI</a> ·
        <a href="{key}.json">해석·샘플 기록</a></section>''')
    title = html.escape(reports[0]["title"])
    by_key = {r["instrument"]:r for r in reports}
    shared = {"jeongak-daegeum", "sanjo-daegeum"} <= by_key.keys()
    same = shared and by_key["jeongak-daegeum"]["audio"]["wav_sha256"] == by_key["sanjo-daegeum"]["audio"]["wav_sha256"]
    notice = ('두 대금 출력의 WAV가 동일합니다.' if same else '공통 음역에서 같은 샘플을 사용하며, 표현 설정에 따라 출력은 달라질 수 있습니다.')
    return '''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>악보에서 국악기로</title><style>body{background:#f4f6f2;color:#203832;font:16px/1.7 'Malgun Gothic',sans-serif;margin:0}main{max-width:1000px;margin:auto;padding:32px 24px}h1{font-size:32px;line-height:1.3}.cards{display:grid;grid-template-columns:1fr 1fr;gap:18px}section{background:white;padding:24px;border:1px solid #ccd9cf;border-radius:16px}audio{width:100%}h2{margin-top:0}p{color:#425b51}a{color:#176b50}.notice{background:#fff1d1;padding:18px;border-radius:12px}footer{font-size:13px;margin-top:24px}@media(max-width:700px){.cards{grid-template-columns:1fr}}</style>
    <main><h1>악보에서 국악기 소리로</h1><p>''' + title + '''</p><p>입력한 실음·박자·장식음을 선택한 CAT 프리셋으로 재생했습니다. 다른 악기로 편곡한 결과는 아닙니다.</p>
    <p class="notice"><strong>CAT 대금 프리셋의 특징</strong><br>이번 simple 배포본은 정악·산조 공통 음역에서 같은 샘플과 재생 설정을 사용합니다. ''' + notice + '''</p>
    <p>악보 입력 → 기보음/실음 구분 → 장식과 숨표 적용 → MIDI → CAT 샘플 WAV</p><div class="cards">''' + "".join(cards) + '''</div>
    <p>전문가 검수 전 청취 실험입니다. 악보의 이음줄은 샘플 연결로 근사하며, 농음·호흡의 음색 변화를 물리적으로 재현하지 않습니다. 수치가 없는 장식 길이·음량은 작성한 설정값입니다.</p>
    <p><a href="score.json">입력 악보 데이터</a> · <a href="manifest.json">전체 재현 기록</a></p><footer>''' + CREDIT + '''</footer></main>
    <script>const players=[...document.querySelectorAll('audio')];players.forEach(p=>p.addEventListener('play',()=>players.forEach(q=>{if(q!==p)q.pause()})));</script></html>'''


def run(score_path, assets_path, out, instruments, transpose=0, vibrato="none"):
    score = json.loads(Path(score_path).read_text(encoding="utf-8-sig"))
    assets = json.loads(Path(assets_path).read_text(encoding="utf-8-sig"))
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    reports, renders = [], []
    # Stage everything first: failure must not leave a new partial audition page.
    with tempfile.TemporaryDirectory(prefix="playback-", dir=out) as tmp:
        stage = Path(tmp)
        for key in instruments:
            perf = apply_expression(compile_score(score, key, transpose), vibrato)
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
                reports.append({**perf, "notes":len(trace), "seconds":len(audio)/FS,
                                "sampler":sampler.manifest, "render_trace":trace})
                renders.append(audio)
            finally:
                sampler.close()
        # One shared audition gain preserves relative preset level differences.
        peak = max(float(np.max(np.abs(x))) for x in renders)
        gain = .85/peak
        for report, audio in zip(reports, renders):
            key = report["instrument"]
            wav = stage/f"{key}.wav"
            sf.write(wav, audio*gain, FS, subtype="PCM_16")
            pcm, fs = sf.read(wav, dtype="float64")
            require(fs == FS and len(pcm) == len(audio) and np.isfinite(pcm).all() and np.max(np.abs(pcm)) < .99, "WAV validation failed")
            report["audio"] = {"sample_rate": FS, "frames":len(pcm), "shared_gain":gain,
                               "peak":float(np.max(np.abs(pcm))), "rms":float(np.sqrt(np.mean(pcm**2))),
                               "midi_sha256":sha(stage/f"{key}.mid"), "wav_sha256":sha(wav)}
            dump(stage/f"{key}.json", report)
        by_key = {r["instrument"]:r for r in reports}
        same_daegeum = None
        if {"jeongak-daegeum", "sanjo-daegeum"} <= by_key.keys():
            same_daegeum = by_key["jeongak-daegeum"]["audio"]["wav_sha256"] == by_key["sanjo-daegeum"]["audio"]["wav_sha256"]
        manifest = {"input_sha256":sha(score_path), "credit":CREDIT, "engine":"custom CAT sample player, not Ableton/Kontakt/Sori",
                    "python":platform.python_version(), "numpy":np.__version__, "soundfile":sf.__version__,
                    "vibrato_preset":vibrato,
                    "source_sha256":{name:sha(Path(__file__).parent/name) for name in ["score.py","midi.py","sampler.py","expression.py","__main__.py"]},
                    "profile_sha256":{path.name:sha(path) for path in sorted(Path(__file__).with_name("profiles").glob("*.json"))},
                    "same_daegeum_pcm":same_daegeum, "results":[{"instrument":r["instrument"],"notes":r["notes"],"seconds":r["seconds"],"audio":r["audio"]} for r in reports],
                    "limitations":["Reviewed JSON input only; no automatic image OMR or MusicXML importer.",
                    "Same sounding melody audition, not instrument-specific arrangement or expert-validated model performance.",
                    "Linear sample interpolation, mono mixdown, held MIDI CC11 and envelope/legato approximations.",
                    "No full Ableton filter/FX/velocity emulation. Sample tuning not exhaustively measured.",
                    "CAT raw samples stay local; check applicable distribution/service terms separately."]}
        dump(stage/"manifest.json", manifest)
        dump(stage/"score.json", score)
        (stage/"comparison.html").write_text(audition_page(reports), encoding="utf-8")
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
    p.add_argument("--vibrato", choices=["none","quarter","third"], default="none", help="Additional pitch motion: none, +/-25c, +/-33.333c")
    a = p.parse_args()
    result = run(a.score, a.assets, a.out, list(PROFILES) if a.instrument == "all" else [a.instrument], a.transpose, a.vibrato)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
