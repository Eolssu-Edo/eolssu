"""Shared fixed inputs and synthetic audio; no test cases or real CAT assets."""
import json
import os
from pathlib import Path
import shutil
import tempfile

import numpy as np

from data.src.playback.jgb import convert_jgb
from data.src.playback.midi import read_midi, write_midi
from data.src.playback.sampler import CatSampler, FS
from data.src.playback.score import compile_score

EXAMPLE = Path(__file__).parents[1] / "examples/seryeongsan-first-jangdan.json"
COMMON_SCORE_FIXTURE = Path(__file__).resolve().parents[3] / "tests/fixtures/scores/ngc-standard-2022-p173.score.json"
HAVE_ENCODER = all(shutil.which(os.environ.get(f"{name.upper()}_BINARY", name))
                   for name in ("ffmpeg", "ffprobe"))


def inputs(root):
    source, assets = root / "input.jgb.json", root / "assets.json"
    source.write_text(json.dumps(document("청황 | 청태")), encoding="utf-8")
    assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
    return source, assets


def common_small_score():
    """Authored four-beat fixture: rest, 중, newly attacked 중, rest."""
    value = json.loads(COMMON_SCORE_FIXTURE.read_text(encoding="utf-8"))
    value["music"].update(bpmUnit="QUARTER", ticksPerBeat=1, ticksPerJeonggan=1)
    value["columns"] = [{"index": 0, "cells": []}]
    value["events"] = []
    for i, symbol in enumerate(["쉼", "중", "중", "쉼"]):
        cid, eid = f"c0-j{i}", f"c0-j{i}-r0-u0"
        value["columns"][0]["cells"].append({"id": cid, "index": i, "units": [
            {"row": 0, "col": 0, "symbol": symbol, "eventId": eid}]})
        rest = symbol == "쉼"
        value["events"].append({"id": eid, "kind": "REST" if rest else "NOTE", "yul": None if rest else symbol,
                                "octave": None if rest else 0, "startTick": i, "durationTick": 1,
                                "cellId": cid, "breathAfter": False, "sigimsae": []})
    return value


def sampler():
    fake = synthetic_sampler()
    fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
    return fake


def document(melody="청황 | 청태", version=2, **controls):
    doc = {"v": version, "tempoBpmUserSet": True, "controls": {"title": "고정 검사", "beats": "2", "gakBeats": "",
           "tempoBpm": "60", "hwangPitch": "63", "staffUnit": "dotted",
           "joPreset": "jung-pyeong", **controls}}
    if version == 1:
        doc["melody"] = melody
    else:
        doc["parts"] = [{"id": "part-17", "name": "단소", "instrument": "danso", "melody": melody}]
    return doc


def converted(doc, **options):
    return convert_jgb(doc, source_name="synthetic.jgb.json", **options)


def midi(score):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)/"test.mid"
        write_midi(compile_score(score, "danso"), path)
        return read_midi(path)


def score(notes=None):
    value = json.loads(EXAMPLE.read_text(encoding="utf-8"))
    value["quarter_bpm"] = 60
    value["notes"] = notes or [{"id":"a", "pitch":69, "duration":"1"}]
    return value


def wire(value, instrument="danso"):
    perf = compile_score(value, instrument)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)/"test.mid"
        write_midi(perf, path)
        return perf, read_midi(path)


def synthetic_sampler(length=5):
    sampler = CatSampler.__new__(CatSampler)
    sampler.instrument, sampler.attack, sampler.release, sampler.gain = "danso", 0, .05, 1
    sampler.zones = [{"low":66,"high":92,"root":69,"detune":0,"file":"sine.wav","volume":1}]
    data = .2*np.sin(2*np.pi*440*np.arange(round(length*FS))/FS)
    sampler.samples = lambda z: (data, FS, "synthetic")
    return sampler
