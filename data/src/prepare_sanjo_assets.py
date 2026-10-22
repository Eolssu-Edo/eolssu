"""Prepare local Sanjo audio and score references; no application or UI build."""

import argparse
import csv
import hashlib
import json
import shutil
import wave
from pathlib import Path

import numpy as np

from analyze_basic_tones import read_wave, yin
from analyze_digital_collection import summarize_pitch
from link_sanjo_basic_notes import group_repeated_regions, YUL as SEVEN_YUL


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs/knowledge/data/instruments/daegeum/sanjo"
REGISTERS = {"저취": "jeochwi", "평취": "pyeongchwi", "역취": "yeokchwi"}
YUL = {"임": "im", "남": "nam", "황": "hwang", "태": "tae", "중": "jung", "무": "mu", "고": "go"}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_mapping_source(mapping, sample):
    # The single-sample manifest predates the collection ID convention.
    expected_mapping_id = {"ngc-sanjo-2550": "ngc-sanjo-basic-2550"}.get(sample["id"])
    if (expected_mapping_id is None or mapping.get("sampleId") != expected_mapping_id or
            mapping.get("sourceSha256", "").lower() != sample["sha256"].lower()):
        raise ValueError("Basic tone mapping belongs to a different source version")


def validate_inputs(manifest, mapping, analysis, score):
    """Reject missing/stale source inputs before writing any prepared asset."""
    basic = next(s for s in manifest["samples"] if s["id"] == "ngc-sanjo-2550")
    validate_mapping_source(mapping, basic)
    for sample in manifest["samples"]:
        if digest(ROOT / sample["rawPath"]) != sample["sha256"].lower():
            raise ValueError(f"Source hash mismatch: {sample['id']}")
        directory = analysis / sample["id"]
        measured = read_json(directory / "analysis.json")["summary"]
        if measured["id"] != sample["id"] or measured["sha256"].lower() != sample["sha256"].lower():
            raise ValueError("Measurement/source identity mismatch")
        if sample["category"] != "연주" and not (directory / "frames.csv").is_file():
            raise ValueError(f"Missing pitch frames: {sample['id']}")
    if digest(ROOT / score["rawPath"]) != score["sha256"].lower():
        raise ValueError("Score PDF hash mismatch")


def crop_frames(samples, rate, start, end):
    first, last = round(start * rate), round(end * rate)
    if first < 0 or last > len(samples) or last <= first:
        raise ValueError("Invalid source crop interval")
    return samples[first:last], first, last


def write_audio(path, samples, rate):
    mono = samples.mean(axis=1)
    peak = float(np.max(np.abs(mono)))
    gain = min(1.0, .999 / peak) if peak else 1.0
    pcm = np.rint(mono * gain * 32767).astype("<i2")
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(rate)
        stream.writeframes(pcm.tobytes())
    return {"format": "WAV_PCM16", "channels": 1, "sampleRateHz": rate,
            "frames": len(pcm), "durationSeconds": len(pcm) / rate,
            "gain": gain, "inputMonoPeak": peak,
            "processing": "channel mean; attenuate only if peak exceeds 0.999; PCM16 quantization; no resampling/pitch correction/fade",
            "sha256": digest(path), "bytes": path.stat().st_size}


def split_units(measurement, reviewed_groups=None):
    """Keep sustained ornaments together; preserve the short staccato attacks."""
    source_id = measurement["summary"]["id"]
    if source_id == "ngc-sanjo-2551":
        groups = group_repeated_regions(measurement["regions"])
        if len(groups) != 18:
            raise ValueError("The declared vibrato sequence hypothesis requires 18 groups")
        return [{"unit_id": f"group-{i+1:03d}", "start_seconds": g["startSeconds"],
                 "end_seconds": g["endSeconds"], "source_region_ids": g["regionIds"],
                 "source_analyses": [r for r in measurement["regions"] if r["region_id"] in g["regionIds"]],
                 "kind": "adjacent_same_pitch_regions", "hypothesis_yul": SEVEN_YUL[i % 7],
                 "hypothesis_register": list(REGISTERS)[i // 7]} for i, g in enumerate(groups)]
    if source_id == "ngc-sanjo-2555":
        return [{"unit_id": a["attack_id"], "start_seconds": a["start_seconds"],
                 "end_seconds": a["end_seconds"], "kind": "short_rms_articulation"}
                for a in measurement["articulations"]]
    units = [dict(r, unit_id=r["region_id"], source_region_ids=[r["region_id"]],
                  kind="rms_sounding_region") for r in measurement["regions"]]
    for review in reviewed_groups or []:
        ids = review["sourceRegionIds"]
        indices = [i for i, u in enumerate(units) if u["unit_id"] in ids]
        if len(indices) != len(ids) or indices != list(range(indices[0], indices[-1]+1)):
            raise ValueError("Reviewed merge must identify existing adjacent source regions exactly once")
        originals = units[indices[0]:indices[-1]+1]
        if (review["startSeconds"] > originals[0]["start_seconds"] or
                review["endSeconds"] < originals[-1]["end_seconds"]):
            raise ValueError("Reviewed merge must preserve the original regions")
        merged = {"unit_id": review["unitId"], "start_seconds": review["startSeconds"],
                  "end_seconds": review["endSeconds"], "source_region_ids": ids,
                  "source_analyses": originals, "kind": "raw_waveform_reviewed_merge",
                  "segmentation_review": review}
        units[indices[0]:indices[-1]+1] = [merged]
    return units


def load_pitch_frames(path):
    with path.open(encoding="utf-8") as stream:
        return [{"seconds": float(f["seconds"]), "f0_hz": float(f["f0_hz"]) if f["f0_hz"] else None,
                 "accepted": f["accepted_for_summary"] == "True"} for f in csv.DictReader(stream)]


def pitch_summary(unit, frames):
    trim = min(.2, (unit["end_seconds"] - unit["start_seconds"]) / 5)
    inside = [f for f in frames if unit["start_seconds"] + trim <= f["seconds"] <= unit["end_seconds"] - trim]
    hz = [f["f0_hz"] for f in inside if f["accepted"]]
    return dict(summarize_pitch(hz), valid_pitch_ratio=len(hz) / max(1, len(inside)),
                valid_pitch_frames=len(hz), interior_trim_seconds=trim)


def build_pitch_reference(measurement, frames):
    """The full 18-group hypothesis, including pitches missing from basic13."""
    result = []
    for i, unit in enumerate(split_units(measurement), 1):
        observed = pitch_summary(unit, frames)
        if not observed["median_hz"]:
            raise ValueError("No measurable pitch in vibrato reference")
        result.append({"groupIndex": i, "register": unit["hypothesis_register"],
                       "yulmyeong": unit["hypothesis_yul"], "labelStatus": "inferred",
                       "hz": observed["median_hz"], "sourceRegionIds": unit["source_region_ids"],
                       "sourceStartSeconds": unit["start_seconds"], "sourceEndSeconds": unit["end_seconds"],
                       "basis": "seven_yul_sequence_hypothesis"})
    if any(b["hz"] <= a["hz"] for a, b in zip(result, result[1:])):
        raise ValueError("Reference groups must ascend")
    return result


def rank_pitch(hz, references):
    if hz is None or not np.isfinite(hz) or hz <= 0:
        raise ValueError("No valid measured pitch to label")
    rows = [dict(r, distanceCents=round(float(1200 * np.log2(hz / r["hz"])), 2)) for r in references]
    return sorted(rows, key=lambda r: abs(r["distanceCents"]))


def assign_pitch(hz, references, review=None):
    ranked = rank_pitch(hz, references)
    chosen = next((r for r in ranked if r["groupIndex"] == review["groupIndex"]), None) if review else ranked[0]
    if chosen is None or abs(chosen["distanceCents"]) > 120:
        raise ValueError("Pitch correspondence requires a fresh audit; refusing an arbitrary label")
    alternatives = [r for r in ranked if r["groupIndex"] != chosen["groupIndex"]][:2]
    margin = min(abs(r["distanceCents"]) for r in alternatives) - abs(chosen["distanceCents"])
    return {"inferred_yulmyeong": chosen["yulmyeong"], "reference_register": chosen["register"],
            "reference_label_status": "inferred", "reference_group_index": chosen["groupIndex"],
            "reference_hz": chosen["hz"], "reference_distance_cents": chosen["distanceCents"],
            "mapping_status": "sequence_context_inference" if review else "measured_pitch_correspondence",
            "alternative_matches": alternatives, "candidate_margin_cents": round(margin, 2),
            "ambiguous_between_candidates": margin < 40,
            "sequence_review": review, "eligible_for_scoring": False}


def unit_analysis(unit, frames, references, review=None):
    # Earlier basic13 label candidates are superseded; retain acoustic data.
    summary = pitch_summary(unit, frames)
    result = {**summary, **assign_pitch(summary["median_hz"], references, review),
              "source_unit_analysis": acoustic_measurements(unit)}
    if unit.get("hypothesis_yul"):
        result["mapping_status"] = "seven_yul_sequence_hypothesis"
    return result


def acoustic_measurements(value):
    if isinstance(value, list):
        return [acoustic_measurements(v) for v in value]
    if isinstance(value, dict):
        return {k: acoustic_measurements(v) for k, v in value.items()
                if not k.startswith("reference_") and k not in {"inferred_yulmyeong", "mapping_status", "eligible_for_scoring"}}
    return value


def short_pitch_frames(samples, rate):
    """40ms/10ms analysis for the transitions blurred by the earlier 85ms window."""
    mono = samples.mean(axis=1)
    window, hop = round(.04 * rate), round(.01 * rate)
    frames = []
    for start in range(0, len(mono) - window + 1, hop):
        frame = mono[start:start + window]
        rms = float(np.sqrt(np.mean(frame ** 2)))
        hz, periodicity = yin(frame, rate, 180, 1800)
        frames.append({"seconds": round((start + window / 2) / rate, 6), "f0_hz": hz,
                       "rms": rms, "periodicity": periodicity,
                       "accepted": hz is not None and periodicity > .90})
    return frames


def window_pitch(frames, windows):
    selected = [f for f in frames if f["accepted"] and any(a <= f["seconds"] <= b for a, b in windows)]
    if len(selected) < 3:
        raise ValueError("Insufficient pitch observations in a reviewed ornament window")
    selected.sort(key=lambda f: f["f0_hz"])
    weights = np.array([f["rms"] ** 2 for f in selected])
    index = int(np.searchsorted(np.cumsum(weights), weights.sum() / 2))
    return {"weighted_median_hz": round(selected[index]["f0_hz"], 3),
            "windows_seconds": windows, "accepted_frames": len(selected),
            "method": "RMS squared weighted median; reviewed anchor windows; 40ms YIN / 10ms hop; periodicity > 0.90"}


def daruchigi_analysis(unit, frames, references, review):
    for window in review["startWindows"] + review["destinationWindows"]:
        if not unit["start_seconds"] <= window[0] < window[1] <= unit["end_seconds"]:
            raise ValueError("Reviewed window lies outside its source clip")
    start = window_pitch(frames, review["startWindows"])
    destination = window_pitch(frames, review["destinationWindows"])
    if "expectedAcceptedFrames" in review and [start["accepted_frames"], destination["accepted_frames"]] != review["expectedAcceptedFrames"]:
        raise ValueError("Reviewed ornament anchor observations changed; recheck analysis settings")
    start.update(assign_pitch(start["weighted_median_hz"], references))
    destination.update(assign_pitch(destination["weighted_median_hz"], references))
    return {"lower_anchor": start, "destination": destination, "review": review,
            "interpretation": "measured pitch correspondence; the performed/intended yulmyeong is not a producer annotation",
            "repeated_rises": 2,
            "trajectory": [f for f in frames if unit["start_seconds"] <= f["seconds"] <= unit["end_seconds"]]}


def remove_previous_generated_files(output, previous, keep):
    """Remove only obsolete, unchanged files listed by the previous catalog."""
    root = output.resolve()
    obsolete = []
    for old in previous.get("audio", []):
        if old["file"] in keep:
            continue
        path = (output / old["file"]).resolve()
        if not path.is_relative_to(root):
            raise ValueError("Previous generated path escapes the data directory")
        if path.is_file():
            if digest(path) != old["sha256"]:
                raise ValueError(f"Preserving a modified previous asset: {path}")
            obsolete.append(path)
    # A later invalid entry must not cause an earlier valid entry to disappear.
    for path in obsolete:
        path.unlink()


def prepare(output, analysis):
    manifest = read_json(DOCS / "collection-manifest.json")
    mapping = read_json(DOCS / "note-fingering-map.json")
    performance = read_json(DOCS / "performance-scores.json")
    score_manifest = read_json(DOCS.parent / "score-manifest.json")
    score = next(a for a in score_manifest["assets"] if a["id"] == performance["scoreAssetId"])
    validate_inputs(manifest, mapping, analysis, score)
    label_review_path = DOCS / "ornament-label-review.json"
    review = read_json(label_review_path)
    for source_id, expected_hash in review["sourceHashes"].items():
        sample = next(s for s in manifest["samples"] if s["id"] == source_id)
        if sample["sha256"].lower() != expected_hash.lower():
            raise ValueError("Label review belongs to a different source version")
    notes = mapping["segments"]
    previous = read_json(output / "catalog.json") if (output / "catalog.json").exists() else {}
    reference_dir = analysis / "ngc-sanjo-2551"
    reference_measurement = read_json(reference_dir / "analysis.json")
    reference_source = next(s for s in manifest["samples"] if s["id"] == "ngc-sanjo-2551")
    if reference_measurement["summary"]["sha256"].lower() != reference_source["sha256"].lower():
        raise ValueError("Pitch reference source hash mismatch")
    references = build_pitch_reference(reference_measurement, load_pitch_frames(reference_dir / "frames.csv"))
    if len(notes) != 13 or len({r["segmentId"] for r in notes}) != 13:
        raise ValueError("Expected the reviewed mapping of 13 distinct basic tones")
    catalog = {"schemaVersion": 4, "task": manifest["task"], "instrument": "산조대금",
               "groups": ["기본음계", "시김새", "연주"], "audio": [], "scores": [],
               "sourceManifestSha256": digest(DOCS / "collection-manifest.json"),
               "noteMappingSha256": digest(DOCS / "note-fingering-map.json"),
               "labelReviewSha256": digest(label_review_path),
               "preparationScriptSha256": digest(Path(__file__)),
               "daruchigiMethod": review["daruchigiMethod"],
               "repeatAudit": review["repeatAudit"],
               "pitchReference": {"sourceId": "ngc-sanjo-2551", "sourceSha256": reference_source["sha256"],
                                  "analysisSha256": digest(reference_dir / "analysis.json"),
                                  "framesSha256": digest(reference_dir / "frames.csv"), "groups": references,
                                  "basis": review["referenceBasis"]},
               "limitations": ["율명은 배열 가설을 포함한 추정; 제작 라벨·독립 실연 검수 전",
                               "실제 녹음 운지는 미확인; 참고 운지와 구분",
                               "악보는 성악 오선보·단소 정간보 일부; 이 녹음 전곡과 대응 미확인",
                               "채점 정답 데이터로 검증되지 않음"]}
    for sample in manifest["samples"]:
        source_path = ROOT / sample["rawPath"]
        samples, rate, source_hash = read_wave(source_path)
        if source_hash != sample["sha256"].upper():
            raise ValueError(f"Source hash mismatch: {sample['id']}")
        measurement = read_json(analysis / sample["id"] / "analysis.json")
        if measurement["summary"]["sha256"].upper() != source_hash:
            raise ValueError("Measurement/source hash mismatch")
        source = {"id": sample["id"], "path": sample["rawPath"], "sha256": source_hash,
                  "url": manifest["source"]["url"], "license": sample["license"],
                  "attribution": sample["attribution"],
                  "analysisSha256": digest(analysis / sample["id"] / "analysis.json")}
        if sample["id"] == "ngc-sanjo-2550":
            for note in notes:
                clip, first, last = crop_frames(samples, rate, note["startSeconds"], note["endSeconds"])
                relative = Path("기본음계/기본음") / f"{note['register']}_{note['yulmyeong']}.wav"
                audio_info = write_audio(output / relative, clip, rate)
                catalog["audio"].append({"key": f"sanjo.{REGISTERS[note['register']]}.{YUL[note['yulmyeong']]}",
                    "category": "기본음계", "title": f"{note['register']} {note['yulmyeong']}",
                    "file": relative.as_posix(), "source": source, "sourceSegmentId": note["segmentId"],
                    "sourceStartFrame": first, "sourceEndFrameExclusive": last,
                    "sourceStartSeconds": first / rate, "sourceEndSeconds": last / rate,
                    "register": note["register"], "yulmyeong": note["yulmyeong"],
                    "labelStatus": note["status"], "f0MedianHz": note["estimatedF0Hz"],
                    "labelF0Hz": note["estimatedF0Hz"], "labelMeaning": "중심음 율명 추정",
                    "actualFingeringStatus": "unconfirmed", "referenceFingerings": note["referenceFingeringCandidates"],
                    "eligibleForNoteScoring": False, **audio_info})
        elif sample["category"] != "연주":
            frames = load_pitch_frames(analysis / sample["id"] / "frames.csv")
            source["framesSha256"] = digest(analysis / sample["id"] / "frames.csv")
            transient_frames = short_pitch_frames(samples, rate) if sample["id"] == "ngc-sanjo-2566" else None
            reviewed_groups = review.get("segmentationDecisions", {}).get(sample["id"], [])
            reanalyzed_frames = (short_pitch_frames(samples[:round((max(g["endSeconds"] for g in reviewed_groups)+.1)*rate)], rate)
                                 if reviewed_groups else None)
            for i, unit in enumerate(split_units(measurement, reviewed_groups), 1):
                override = review["sequenceDecisions"].get(f"{sample['id']}.{unit['unit_id']}")
                observed = unit_analysis(unit, reanalyzed_frames if unit["kind"] == "raw_waveform_reviewed_merge" else frames,
                                         references, override)
                if unit["kind"] == "raw_waveform_reviewed_merge":
                    observed["pitch_reanalysis"] = {"input": "original WAV", "windowSeconds": .04, "hopSeconds": .01,
                                                    "yinRangeHz": [180, 1800], "periodicityMinimumExclusive": .90,
                                                    "rmsGate": None}
                gesture = None
                if transient_frames is not None:
                    gesture = daruchigi_analysis(unit, transient_frames, references, review["daruchigi"][unit["unit_id"]])
                    observed.update(gesture["destination"])
                    observed["mapping_status"] = "ornament_anchor_pitch_correspondence"
                yul, register = observed["inferred_yulmyeong"], observed["reference_register"]
                display = f"{register} {yul}"
                if gesture:
                    lower = gesture["lower_anchor"]
                    display = f"{lower['reference_register']} {lower['inferred_yulmyeong']}→{display}"
                name = display.replace(" ", "_").replace("→", "-")
                relative = Path(sample["category"]) / sample["title"] / f"{i:03d}_{name}.wav"
                clip, first, last = crop_frames(samples, rate, unit["start_seconds"], unit["end_seconds"])
                audio_info = write_audio(output / relative, clip, rate)
                catalog["audio"].append({"key": f"{sample['id']}.{unit['unit_id']}",
                    "category": sample["category"], "technique": sample["title"],
                    "title": f"{sample['title']} {i:03d} {display}", "displayLabel": display,
                    "file": relative.as_posix(), "source": source, "sourceSegmentId": unit["unit_id"],
                    "sourceStartFrame": first, "sourceEndFrameExclusive": last,
                    "sourceStartSeconds": first / rate, "sourceEndSeconds": last / rate,
                    "register": register, "yulmyeong": yul,
                    "labelStatus": "inferred", "labelMeaning": "도착 안정음의 음고 대응" if gesture else "중심음 율명 추정",
                    "labelF0Hz": gesture["destination"]["weighted_median_hz"] if gesture else observed["median_hz"],
                    "f0MedianHz": observed["median_hz"], "segmentationMethod": unit["kind"],
                    "segmentationStatus": "raw_waveform_reviewed" if unit["kind"] == "raw_waveform_reviewed_merge" else "automatic_unreviewed",
                    "analysis": observed,
                    "repeatAssessment": review.get("repeatAssessments", {}).get(f"{sample['id']}.{unit['unit_id']}"),
                    "ornament": gesture,
                    "actualFingeringStatus": "unconfirmed", "eligibleForNoteScoring": False, **audio_info})
        else:
            relative = Path("연주/태평가/태평가.wav")
            audio_info = write_audio(output / relative, samples, rate)
            catalog["audio"].append({"key": sample["id"], "category": sample["category"],
                "title": sample["title"], "file": relative.as_posix(), "source": source,
                "sourceStartFrame": 0, "sourceEndFrameExclusive": len(samples),
                "sourceStartSeconds": 0, "sourceEndSeconds": len(samples) / rate,
                "analysisStatus": "automatic_unreviewed", "eligibleForNoteScoring": False,
                "regions": measurement["regions"], **audio_info})
    source_pdf = ROOT / score["rawPath"]
    if digest(source_pdf) != score["sha256"].lower():
        raise ValueError("Score PDF hash mismatch")
    pdf = Path("연주/태평가/태평가_오선보·정간보_원본악보집.pdf")
    shutil.copyfile(source_pdf, output / pdf)
    for reference in performance["references"]:
        page = reference["pdfPages"][0]
        catalog["scores"].append({"id": reference["id"], "performanceKey": "ngc-sanjo-2625",
            "notation": reference["notation"], "file": pdf.as_posix(), "pdfPage": page,
            "openAtPage": f"{pdf.as_posix()}#page={page}", "instrument": reference["instrument"],
            "coverage": reference["coverage"], "samePerformanceEditionVerified": False,
            "license": score["license"], "redistribution": score["redistribution"],
            "sourceUrl": score["sourceUrl"], "publisher": score["publisher"],
            "sourceTitle": score["title"], "sha256": score["sha256"], "modified": False})
    named = [a for a in catalog["audio"] if a["category"] != "연주"]
    if len(named) != 121 or any(not a.get("yulmyeong") or not a.get("register") for a in named):
        raise ValueError("Incomplete note labels in the prepared dataset")
    catalog["labelSummary"] = {"namedIndividualClips": len(named), "inferred": len(named), "unassigned": 0,
                               "performanceRecordings": 1, "basis": "same-source reference + sequence/anchor analysis"}
    remove_previous_generated_files(output, previous, {a["file"] for a in catalog["audio"]})
    (output / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "README.md").write_text("""# 산조대금 사용 자료

## 기본음계

- `기본음계/기본음/저취_황.wav`처럼 취법·율명으로 찾는 개별 기본음 13개.
- 저취 임·남·황·태·중 / 평취 임·남·황·태·중 / 역취 임·남·무.
- `기본음+요성/` 18개, `기본음+깊은요성/` 15개도 각각 음군·발음 구간별로 분리했다.
- 깊은요성 저취 임은 연속된 한 연주다. 잘못 나뉜 두 조각과 누락된 끝음을 복구해 원본0.6–7.1초의 한 파일로 합쳤다.
- 율명은 추정이며 제작처 정답 라벨이 아니다. 실제 녹음 운지는 미확인.

## 시김새

`시김새/` 아래 항목별로 분리했다: 노니로 18개·니라 14개·스타카토 17개·크레센도 16개·다루치기 10개.
예: `시김새/크레센도/001_저취_임.wav`. 순번은 원본의 구간 순서다.
개별 음원 121개 모두 취법·율명을 연결했다. 발음 구간·Hz·강약·요성 후보와 18음 기준 대응은 `catalog.json`의 각 `analysis`에 포함한다.
다루치기는 `001_저취_임-저취_남.wav`처럼 아래 안정음→도착 안정음을 함께 표시한다.
다루치기 이름은 실측 음고의 대응이다. 꺾거나 끌어올린 주법의 의도된 율명과 차이가 날 수 있어 인접 후보도 기록했다.
같은 주법의 연결된 가락은 한 구간에 여러 음이 들어갈 수 있다. 한 주법을 임의로 개별 음표로 잘라내지 않았다.

## 연주 — 태평가

- [음원 95.68초](연주/태평가/태평가.wav)
- [오선보: 원본 PDF 127쪽](연주/태평가/태평가_오선보·정간보_원본악보집.pdf#page=127) — 성악용 본절·후렴.
- [정간보: 원본 PDF 176쪽](연주/태평가/태평가_오선보·정간보_원본악보집.pdf#page=176) — 단소용 본절 일부.

두 악보는 한 원본 악보집에 있어 PDF를 한 번만 보관했다. 원본 전체를 무변경 복사했다.
이 산조대금 녹음의 전곡·장식음·반복과 일치하는 악보 쌍은 미확보다.

## 사용 정보

`catalog.json`에서 `sanjo.jeochwi.hwang` → 파일·Hz·원본 구간·라벨 상태를 찾을 수 있다.
준비 음원은 mono PCM16 WAV, 원래 샘플레이트 유지. 음높이 보정·리샘플링 없음.
모노 변환 후 피크가 0.999를 넘는 경우에만 감쇠하고 gain을 기록했다. 원본은 변경하지 않았다.
기본음계 46개·시김새 75개는 개별 파일이며 태평가 1개만 전곡이다.
요성은 인접 동일 음높이의 짧은 끊김을 묶은 18음군, 스타카토는 20ms RMS 기반 짧은 발음 17개,
깊은요성 첫 음은 원본 파형을 재분석해 분할 오류를 수정했고, 나머지는 85ms 분석 창 기반 발음 구간을 사용했다. 독립 연주자 청취 검수 전이다.
요성의 한글 율명은 주요7율명 반복 가설에 따른 추정이다. 전체 18음(저취7·평취7·역취4)을 비교 기준으로 사용했다.
깊은요성은 중심 음높이로 대응하며 진동 폭이 넓다는 이유로 제외하지 않는다.
노니로007·스타카토013은 인접 음과 옥타브 대응을 함께 판단했다. 노니로015·016은 원본에서3.795초 간격을 둔 같은 음고의 별도 발음이므로 모두 보존했다.
다루치기는 40ms 창으로 재분석한 아래·위 안정 구간을 사용했다. 004의 아래음은 두 번째 반복에서 측정했다.
일반 항목은 중심값, 다루치기는 도착 안정음이 `labelF0Hz`이며, 구간 전체 중앙값 `f0MedianHz`와 구분했다.
데이터는 재생·참조용이며 채점 정답으로 검증되지 않았다.

출처: 국립국악원, 국악기 디지털 음원, 산조대금. 원음은 공공누리 제1유형(출처표시).
가공: 기본음계·시김새 구간 추출·채널 평균·일부 피크 감쇠·PCM16 변환. 항목별 출처·해시는 catalog.json 참조.
악보 출처: 국립국악원 『2022 국립국악원 개정교과서 표준악보집』.
악보는 공공누리 제4유형(출처표시·비상업·무변경)이며 상업 앱 배포용으로 승인된 자료가 아니다.

작업: S15P31E102-52. 생성 명령은 저장소 data/README.md 참조.
""", encoding="utf-8")
    print(f"Prepared {len(catalog['audio'])} audio assets, 2 score references in 1 unchanged PDF: {output}")
    return catalog


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "data/processed/instruments/daegeum/sanjo/ready-v1")
    parser.add_argument("--analysis-dir", type=Path, default=ROOT / "data/processed/instruments/daegeum/sanjo/collection-v1")
    args = parser.parse_args()
    prepare(args.output_dir, args.analysis_dir)
