"""Optional visual key/clef metadata for a single Audiveris staff.

The local vision model only reads graph-derived header crops. It does not
generate replacement notes or certify the OMR result. Raw XML stays intact.
"""
from copy import deepcopy
import hashlib
import io
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import cv2
import numpy as np
from PIL import Image

from .recognition import RecognitionError, _chat, _json, _require, inference_options

KEY_PROMPT = ("Count only the key-signature accidental signs in this music staff crop. "
              "Ignore printed time-signature numbers and staff lines. Do not infer a key from a tune. "
              "Return sign type (flat, sharp, none, unknown), total number of accidental signs "
              "actually visible, and uncertain=true only if the crop is unreadable.")
CLEF_PROMPT = ("Read this clef crop. Is there a small octave numeral directly above or below the clef? "
               "Return only the visible numeral (none, 8, 15, unknown), its position "
               "(none, above, below, unknown), and whether the shape is visually uncertain. "
               "Do not infer anything from instrument or melody.")
KEY_SCHEMA = {"type": "object", "additionalProperties": False, "properties": {
    "sign": {"type": "string", "enum": ["flat", "sharp", "none", "unknown"]},
    "count": {"type": "integer", "minimum": 0, "maximum": 7},
    "uncertain": {"type": "boolean"}}, "required": ["sign", "count", "uncertain"]}
CLEF_SCHEMA = {"type": "object", "additionalProperties": False, "properties": {
    "numeral": {"type": "string", "enum": ["none", "8", "15", "unknown"]},
    "position": {"type": "string", "enum": ["none", "above", "below", "unknown"]},
    "uncertain": {"type": "boolean"}}, "required": ["numeral", "position", "uncertain"]}
TEMPO_PROMPT = ("Read only a printed metronome marking in this music crop. Return the note beat unit, "
                "whether it has an augmentation dot, and the number after its equals sign. "
                "Do not infer tempo from the tune or other numbers. If no clear metronome marking is "
                "visible, use visible=false, beat_unit=unknown, per_minute=0 and uncertain=true.")
TEMPO_SCHEMA = {"type": "object", "additionalProperties": False, "properties": {
    "visible": {"type": "boolean"}, "beat_unit": {"type": "string", "enum": ["whole", "half", "quarter", "eighth", "16th", "unknown"]},
    "dotted": {"type": "boolean"}, "per_minute": {"type": "integer", "minimum": 0, "maximum": 400},
    "uncertain": {"type": "boolean"}}, "required": ["visible", "beat_unit", "dotted", "per_minute", "uncertain"]}


def _checked(value, schema):
    _require(isinstance(value, dict) and set(value) == set(schema["required"]), "Invalid staff metadata fields")
    _require(type(value["uncertain"]) is bool, "Invalid staff metadata confidence flag")
    for key, spec in schema["properties"].items():
        if "enum" in spec:
            _require(value[key] in spec["enum"], "Invalid staff metadata enum")
    if "count" in value:
        _require(type(value["count"]) is int and 0 <= value["count"] <= 7, "Invalid accidental count")
        _require(value["sign"] == "unknown" or (value["sign"] == "none") == (value["count"] == 0),
                 "Inconsistent key metadata")
    else:
        _require("unknown" in (value["numeral"], value["position"]) or
                 (value["numeral"] == "none") == (value["position"] == "none"), "Inconsistent clef metadata")
    return value


def _alter(step, fifths):
    order = "BEADGCF" if fifths < 0 else "FCGDAEB"
    return (-1 if fifths < 0 else 1) if step in order[:abs(fifths)] else 0


def apply_tempo(root, value):
    """Insert a clear visual metronome only when XML has no tempo information."""
    _require(isinstance(value, dict) and set(value) == set(TEMPO_SCHEMA["required"]), "Invalid tempo metadata fields")
    _require(all(type(value[k]) is bool for k in ("visible", "dotted", "uncertain")), "Invalid tempo flags")
    _require(type(value["per_minute"]) is int and 0 <= value["per_minute"] <= 400, "Invalid metronome number")
    _require(value["beat_unit"] in TEMPO_SCHEMA["properties"]["beat_unit"]["enum"], "Invalid metronome unit")
    result = deepcopy(root)
    report = {"corrections": [], "warnings": []}
    if root.findall(".//metronome") or any(e.get("tempo") is not None for e in root.findall(".//sound")):
        report["warnings"].append("원래 MusicXML 빠르기 표기를 유지하여 추가 빠르기 보정을 생략했습니다.")
    elif not value["visible"] or value["uncertain"] or value["beat_unit"] == "unknown":
        report["warnings"].append("사진의 메트로놈 표기가 불확실하여 빠르기를 추가하지 않았습니다.")
    else:
        factor = {"whole": 4, "half": 2, "quarter": 1, "eighth": .5, "16th": .25}[value["beat_unit"]]
        bpm = value["per_minute"] * factor * (1.5 if value["dotted"] else 1)
        _require(10 <= bpm <= 400, "Unsupported visual tempo")
        measure = result.find("part/measure")
        _require(measure is not None, "No measure for tempo")
        direction = ET.Element("direction", placement="above")
        metronome = ET.SubElement(ET.SubElement(direction, "direction-type"), "metronome")
        ET.SubElement(metronome, "beat-unit").text = value["beat_unit"]
        if value["dotted"]:
            ET.SubElement(metronome, "beat-unit-dot")
        ET.SubElement(metronome, "per-minute").text = str(value["per_minute"])
        ET.SubElement(direction, "sound", tempo=format(bpm, "g"))
        first_note = measure.find("note")
        measure.insert(list(measure).index(first_note) if first_note is not None else len(measure), direction)
        report["corrections"].append({"field": "quarter_bpm", "before": None, "after": bpm,
                                      "basis": "visible printed metronome in graph-derived crop"})
    return result, report


def apply_metadata(root, key, clef):
    """Copy XML, update key-derived alterations and add a missing clef octave.

    Existing note-local accidentals (including their carry within a measure),
    unexplained pitch alterations, and explicit clef octave changes take priority.
    """
    key, clef = _checked(key, KEY_SCHEMA), _checked(clef, CLEF_SCHEMA)
    result = deepcopy(root)
    report = {"corrections": [], "warnings": []}
    parts = result.findall("part")
    if len(parts) != 1:
        report["warnings"].append("조표·음자리표 보정은 단일 파트에만 적용합니다.")
        return result, report
    keys = result.findall(".//attributes/key")
    if key["uncertain"] or key["sign"] == "unknown":
        report["warnings"].append("사진의 조표가 불확실하여 원래 OMR 조표를 유지했습니다.")
    elif len(keys) != 1 or keys[0].find("fifths") is None:
        report["warnings"].append("조표가 없거나 변경되는 악보이므로 자동 조표 보정을 생략했습니다.")
    else:
        old = int(keys[0].findtext("fifths"))
        new = key["count"] * (-1 if key["sign"] == "flat" else 1)
        _require(-7 <= old <= 7, "Unsupported source key")
        if new != old:
            changed = 0
            for measure in parts[0].findall("measure"):
                local = {}
                for note in measure.findall("note"):
                    pitch = note.find("pitch")
                    if pitch is None:
                        continue
                    step, octave = pitch.findtext("step"), pitch.findtext("octave")
                    _require(step in "ABCDEFG" and octave is not None, "Invalid MusicXML pitch")
                    alteration = pitch.find("alter")
                    value = float(alteration.text) if alteration is not None else 0
                    _require(math.isfinite(value), "Nonfinite MusicXML alter")
                    address = (step, octave)
                    if note.find("accidental") is not None:
                        local[address] = value
                    elif address in local:
                        continue
                    elif value != _alter(step, old):
                        report["warnings"].append("명시되지 않은 임시표 가능성이 있어 해당 음높이는 유지했습니다.")
                    else:
                        target = _alter(step, new)
                        if target != value:
                            if alteration is None:
                                alteration = ET.Element("alter")
                                pitch.insert(list(pitch).index(pitch.find("octave")), alteration)
                            alteration.text = str(target)
                            changed += 1
            keys[0].find("fifths").text = str(new)
            report["corrections"].append({"field": "key_fifths", "before": old, "after": new,
                                          "changed_pitch_alters": changed, "basis": "visual key-header crop"})
    clefs = result.findall(".//attributes/clef")
    if clef["uncertain"] or "unknown" in (clef["numeral"], clef["position"]):
        report["warnings"].append("사진의 옥타브 표기가 불확실하여 원래 OMR 표기를 유지했습니다.")
    elif clef["numeral"] in ("8", "15"):
        if len(clefs) != 1:
            report["warnings"].append("음자리표가 없거나 변경되어 자동 옥타브 보정을 생략했습니다.")
        elif clefs[0].find("clef-octave-change") is None:
            shift = (1 if clef["numeral"] == "8" else 2) * (1 if clef["position"] == "above" else -1)
            pitches = result.findall(".//note/pitch/octave")
            for octave in pitches:
                octave.text = str(int(octave.text) + shift)
            ET.SubElement(clefs[0], "clef-octave-change").text = str(shift)
            report["corrections"].append({"field": "clef_octave_change", "before": None, "after": shift,
                                          "changed_pitch_octaves": len(pitches),
                                          "basis": "visible octave numeral in clef crop"})
    report["warnings"] = list(dict.fromkeys(report["warnings"]))
    return result, report


def _graph(omr_path):
    with zipfile.ZipFile(omr_path) as archive:
        info = archive.getinfo("sheet#1/sheet#1.xml")
        _require(info.file_size <= 8*1024*1024, "OMR graph too large")
        raw = archive.read(info)
    _require(b"<!DOCTYPE" not in raw.upper() and b"<!ENTITY" not in raw.upper(), "OMR entities unsupported")
    return ET.fromstring(raw)


def _remove_confirmed_header_flats(root, omr_path, key, crop):
    """Remove a false note only with positive flat-shape and XML-position evidence.

    The bounded matcher uses an OMR-recognized flat as its template. Every
    predicted key sign must match that shape, regular spacing and conventional
    flat heights; the local model must independently agree with their count.
    XML notes are mapped to graph heads only for one monophonic staff, with
    matching counts and per-measure coordinate consistency.
    """
    evidence = {"removed_notes": [], "matches": [], "accepted": False}
    if key["uncertain"] or key["sign"] != "flat" or key["count"] < 2:
        evidence["reason"] = "No confident multiple-flat key"
        return root, evidence
    graph = _graph(omr_path)
    _require(len(graph.findall(".//staff")) == 1, "Flat filtering requires one staff")
    il = int(graph.find("scale/interline").get("main"))
    recognized = graph.findall('.//key-alter[@shape="FLAT"]')
    if not recognized:
        evidence["reason"] = "No OMR flat template"
        return root, evidence
    first = min(recognized, key=lambda e: int(e.find("bounds").get("x")))
    glyph = graph.find('.//glyph[@id="'+first.get("glyph")+'"]')
    runs = glyph.find("run-table")
    _require(runs.get("orientation") == "VERTICAL", "Unsupported flat template orientation")
    w, h = int(runs.get("width")), int(runs.get("height"))
    _require(1 < w <= 3*il and il < h <= 5*il, "Invalid flat template dimensions")
    template = np.zeros((h, w), dtype=bool)
    columns = runs.findall("runs")
    _require(len(columns) == w, "Invalid flat template columns")
    for x, column in enumerate(columns):
        y = 0
        for index, length in enumerate(map(int, (column.text or "").split())):
            _require(length >= 0 and y+length <= h, "Invalid flat template runs")
            if index % 2 == 0:
                template[y:y+length, x] = True
            y += length
    with zipfile.ZipFile(omr_path) as archive:
        info = archive.getinfo("sheet#1/BINARY.png")
        _require(info.file_size <= 20*1024*1024, "OMR binary image too large")
        with Image.open(io.BytesIO(archive.read(info))) as image:
            _require(image.width*image.height <= 25_000_000, "OMR image too large")
            binary = np.asarray(image.convert("L")) < 128
    x0, y0, x1, y1 = crop["bbox_pixels_xyxy"]
    _require(0 <= x0 < x1 <= binary.shape[1] and 0 <= y0 < y1 <= binary.shape[0], "Invalid flat search area")
    _require((x1-x0)*(y1-y0) <= 500_000, "Flat search area too large")
    estimated_ops = max(0,x1-x0-w+1)*max(0,y1-y0-h+1)*w*h
    evidence["estimated_pixel_operations"] = estimated_ops
    _require(estimated_ops <= 100_000_000, "Flat matcher work limit exceeded")
    ys = [float(e.find("point").get("y")) for e in graph.findall(".//staff/lines/line")]
    candidates = []
    for y in range(y0, y1-h+1):
        mask = np.ones((h, w), dtype=bool)
        for staff_y in ys:
            mask[np.abs(np.arange(h)+y-staff_y) <= max(1, round(il*.14)), :] = False
        fg, bg = template & mask, ~template & mask
        if not fg.any() or not bg.any():
            continue
        patches = np.lib.stride_tricks.sliding_window_view(binary[y:y+h, x0:x1], (h, w))[0]
        scores = .5*((patches & fg).sum(axis=(1, 2))/fg.sum() + (~patches & bg).sum(axis=(1, 2))/bg.sum())
        for offset in np.flatnonzero(scores >= .85):
            candidates.append((float(scores[offset]), x0+int(offset), y))
    peaks = []
    for score, x, y in sorted(candidates, reverse=True):
        if all(abs(x-p[1]) > .7*w for p in peaks):
            peaks.append((score, x, y))
    peaks.sort(key=lambda p: p[1])
    evidence["template_glyph_id"] = glyph.get("id")
    evidence["matches"] = [{"score": s, "bbox_pixels_xywh": [x,y,w,h]} for s,x,y in peaks]
    if len(peaks) != key["count"]:
        evidence["reason"] = "Template count disagrees with visual model"
        return root, evidence
    xs = np.array([p[1] for p in peaks]); gaps = np.diff(xs)
    expected = np.array([0, -1.5, .5, -1, 1, -.5, 1.5])[:len(peaks)]*il
    actual = np.array([p[2] for p in peaks])-peaks[0][2]
    if not (.65*il <= float(np.median(gaps)) <= 1.5*il and
            np.max(abs(gaps-np.median(gaps))) <= .25*il and np.max(abs(actual-expected)) <= .25*il):
        evidence["reason"] = "Flat spacing or staff positions disagree"
        return root, evidence
    notes = root.findall(".//part/measure/note")
    heads = sorted(graph.findall(".//head"), key=lambda e: int(e.find("bounds").get("x")))
    if (len(root.findall("part")) != 1 or len(heads) != len(notes) or len(notes) < 2 or
            root.findall(".//backup") or any(n.find("pitch") is None or n.find("chord") is not None for n in notes) or
            len({n.findtext("voice", "1") for n in notes}) != 1):
        evidence["reason"] = "Graph and XML are not a bijective monophonic sequence"
        return root, evidence
    index = 0
    for measure in root.findall(".//part/measure"):
        offsets = []
        for note in measure.findall("note"):
            offsets.append(float(note.get("default-x"))-int(heads[index].find("bounds").get("x"))*10/il)
            index += 1
        if offsets and max(offsets)-min(offsets) > 2.0:
            evidence["reason"] = "Graph and XML note positions disagree"
            return root, evidence
    removals = []
    for index, head in enumerate(heads):
        if float(head.get("ctx-grade", head.get("grade", "0"))) >= .8:
            continue
        b = {k: int(v) for k,v in head.find("bounds").attrib.items()}
        for score, x, y in peaks:
            overlap_x = max(0,min(x+w,b["x"]+b["w"])-max(x,b["x"]))
            overlap_y = max(0,min(y+h,b["y"]+b["h"])-max(y,b["y"]))
            if (abs(x-b["x"]) <= .3*il and overlap_x >= .65*b["w"] and
                    overlap_x*overlap_y >= .5*b["w"]*b["h"]):
                removals.append(index)
                evidence["removed_notes"].append({"index": index, "head_id": head.get("id"),
                    "head_bbox_pixels_xywh": [b[k] for k in ("x","y","w","h")],
                    "flat_bbox_pixels_xywh": [x,y,w,h], "template_score": score,
                    "removed_note_xml": ET.tostring(notes[index], encoding="unicode")})
                break
    # Only header false positives may be removed, never notes interleaved into music.
    if removals and removals != list(range(len(removals))):
        evidence["reason"] = "Flat overlap is not an initial prefix"
        evidence["removed_notes"] = []
        return root, evidence
    result = deepcopy(root)
    targets = result.findall(".//part/measure/note")
    for measure in result.findall(".//part/measure"):
        for index in removals:
            if targets[index] in list(measure):
                measure.remove(targets[index])
    evidence["accepted"] = True
    evidence["reason"] = "Visual count, template shapes, flat geometry and XML coordinates agree"
    return result, evidence


def _crops(omr_path, image_path):
    graph = _graph(omr_path)
    _require(len(graph.findall(".//staff")) == 1, "Metadata requires one staff")
    interline = int(graph.find("scale/interline").get("main"))
    _require(1 <= interline <= 500, "Invalid staff scale")
    clef = graph.find(".//clef[@kind]")
    _require(clef is not None, "No recognized clef")
    b = {k: int(v) for k, v in clef.find("bounds").attrib.items()}
    heads = [e for e in graph.findall(".//head") if float(e.get("ctx-grade", e.get("grade", "0"))) >= .8]
    _require(heads, "No confident head bounds the header crop")
    first = min(heads, key=lambda e: int(e.find("bounds").get("x")))
    first_x = int(first.find("bounds").get("x"))
    ys = [float(e.find("point").get("y")) for e in graph.findall(".//staff/lines/line")]
    boxes = {"clef-octave": [b["x"]-interline, b["y"]-2*interline,
                             b["x"]+b["w"]+interline//2, b["y"]+b["h"]+interline],
             "key-time-header": [b["x"]+b["w"], math.floor(min(ys)-2*interline),
                                  first_x-interline//3, math.ceil(max(ys)+interline)],
             "metronome": [b["x"]+b["w"], math.floor(min(ys)-7*interline),
                            first_x+6*interline, math.ceil(min(ys)-interline)]}
    result = {}
    with Image.open(image_path) as image:
        picture = graph.find("picture")
        _require(image.size == (int(picture.get("width")), int(picture.get("height"))), "OMR image dimensions mismatch")
        for name, box in boxes.items():
            box = [max(0, box[0]), max(0, box[1]), min(image.width, box[2]), min(image.height, box[3])]
            if name == "metronome" and (box[0] >= box[2] or box[1] >= box[3]):
                continue
            _require(box[0] < box[2] and box[1] < box[3], "Invalid header crop")
            stream = io.BytesIO()
            image.crop(box).save(stream, format="PNG")
            result[name] = {"image": stream.getvalue(), "bbox_pixels_xyxy": box,
                            "sha256": hashlib.sha256(stream.getvalue()).hexdigest()}
            if name == "clef-octave":
                result[name].update(clef_bbox_pixels_xywh=[b[k] for k in ("x", "y", "w", "h")],
                                    interline_pixels=interline)
    return result


def _octave_mark_evidence(crop, value):
    """Require visible ink outside the clef before accepting a VLM octave claim.

    This is a presence guard, not a numeral classifier. A treble clef's own
    upper loop must never stand in for an attached 8. The VLM remains responsible
    for the numeral, while uncertain/absent geometric support preserves OMR.
    """
    evidence = {"accepted": False, "method": "outside-clef-ink-v1", "components": []}
    if value['uncertain'] or value['numeral'] not in ('8', '15'):
        evidence['reason'] = 'No confident octave numeral to apply'
        return evidence
    x, y, w, h = crop['clef_bbox_pixels_xywh']
    il = crop['interline_pixels']
    cx, cy, _, _ = crop['bbox_pixels_xyxy']
    with Image.open(io.BytesIO(crop['image'])) as image:
        gray = np.array(image.convert('L'))
    # Leave a quarter-interline tolerance around the graph's clef body. A
    # touching printed numeral can still extend into the outside evidence band.
    left, right = max(0, math.floor(x-cx-.15*il)), min(gray.shape[1], math.ceil(x+w-cx+.15*il))
    if value['position'] == 'above':
        top, bottom = max(0, math.floor(y-cy-2*il)), max(0, math.floor(y-cy-.25*il))
    elif value['position'] == 'below':
        top, bottom = min(gray.shape[0], math.ceil(y+h-cy+.25*il)), min(gray.shape[0], math.ceil(y+h-cy+2*il))
    else:
        evidence['reason'] = 'Unknown octave numeral position'
        return evidence
    evidence['band_pixels_xyxy'] = [left+cx, top+cy, right+cx, bottom+cy]
    if left >= right or top >= bottom:
        evidence['reason'] = 'No visible band outside clef'
        return evidence
    binary = (gray[top:bottom, left:right] < 160).astype(np.uint8)
    _, _, stats, _ = cv2.connectedComponentsWithStats(binary)
    for sx, sy, sw, sh, area in stats[1:]:
        center_x = sx+left+cx+sw/2
        if (.15*il <= sw <= 1.8*il and .15*il <= sh <= 1.8*il
                and x+.2*w <= center_x <= x+.9*w
                and area >= max(4, .05*il*il)):
            evidence['components'].append([int(sx+left+cx), int(sy+top+cy), int(sw), int(sh), int(area)])
    evidence['accepted'] = bool(evidence['components'])
    evidence['reason'] = ('Compact foreground outside clef supports model numeral location' if evidence['accepted']
                          else 'No compact numeral-sized foreground outside clef; preserve OMR octaves')
    return evidence


def enrich_musicxml(root, image_path, omr_path, *, model, base_url="http://127.0.0.1:11434", trace_path=None):
    """Return XML copy plus evidence; unavailable metadata leaves raw OMR intact."""
    report = {"corrections": [], "warnings": [], "crops": {}, "responses": [], "model": model,
              "scope": "Graph-derived key/clef/tempo metadata; positive flat-template filtering only. Notes are never generated."}
    options = {**inference_options(model), "temperature": 0, "num_ctx": 4096, "num_batch": 128, "num_predict": 128}
    report["options"] = options
    result = deepcopy(root)
    try:
        crops = _crops(omr_path, image_path)
        report["crops"] = {name: {k: v for k, v in crop.items() if k != "image"} for name, crop in crops.items()}
        values = {}
        for name, prompt, schema in (("key-time-header", KEY_PROMPT, KEY_SCHEMA),
                                     ("clef-octave", CLEF_PROMPT, CLEF_SCHEMA)):
            if trace_path is not None:
                path = Path(trace_path).with_name(Path(trace_path).stem+"-"+name+".png")
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(crops[name]["image"])
                report["crops"][name]["path"] = str(path)
            envelope = _chat(crops[name]["image"], prompt, schema, model=model, base_url=base_url,
                             timeout=120, options=options)
            report["responses"].append({"crop": name, "prompt": prompt, "schema": schema, "options": options, "raw_response": envelope})
            _require(envelope.get("done_reason") != "length", "Truncated metadata response")
            values[name] = _checked(_json(envelope["message"]["content"]), schema)
        report["metadata"] = values
        verified_clef = deepcopy(values['clef-octave'])
        rejected_octave = False
        if not verified_clef['uncertain'] and verified_clef['numeral'] in ('8', '15'):
            evidence = _octave_mark_evidence(crops['clef-octave'], verified_clef)
            report['octave_geometry'] = evidence
            if not evidence['accepted']:
                verified_clef['uncertain'] = True
                rejected_octave = True
        result, changes = apply_metadata(root, values["key-time-header"], verified_clef)
        report.update(changes)
        if rejected_octave:
            report['warnings'].append('모델의 옥타브 숫자 응답을 뒷받침하는 음자리표 바깥 획이 없어 이조하지 않았습니다.')
        try:
            result, filtering = _remove_confirmed_header_flats(result, omr_path, values["key-time-header"], crops["key-time-header"])
            report["flat_filter"] = filtering
            if filtering["removed_notes"]:
                report["corrections"].append({"field": "false_header_flat_notes", "removed_count": len(filtering["removed_notes"]),
                    "basis": "Visual count and graph-derived flat template/spacing/position agree; see flat_filter evidence"})
        except (OSError, ValueError, KeyError, TypeError, AttributeError, ET.ParseError, zipfile.BadZipFile) as error:
            report["warnings"].append("조표 모양 검증을 사용할 수 없어 인식된 음표를 모두 유지했습니다: "+str(error))
        if not root.findall(".//metronome") and not any(e.get("tempo") is not None for e in root.findall(".//sound")):
            try:
                _require("metronome" in crops, "No visible area above staff for metronome crop")
                if trace_path is not None:
                    path = Path(trace_path).with_name(Path(trace_path).stem+"-metronome.png")
                    path.write_bytes(crops["metronome"]["image"])
                    report["crops"]["metronome"]["path"] = str(path)
                envelope = _chat(crops["metronome"]["image"], TEMPO_PROMPT, TEMPO_SCHEMA, model=model,
                                 base_url=base_url, timeout=120, options=options)
                report["responses"].append({"crop": "metronome", "prompt": TEMPO_PROMPT, "schema": TEMPO_SCHEMA,
                                             "options": options, "raw_response": envelope})
                _require(envelope.get("done_reason") != "length", "Truncated tempo response")
                value = _json(envelope["message"]["content"])
                result, tempo_report = apply_tempo(result, value)
                report["metadata"]["metronome"] = value
                report["corrections"].extend(tempo_report["corrections"])
                report["warnings"].extend(tempo_report["warnings"])
            except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
                report["warnings"].append("추가 빠르기 인식을 사용할 수 없어 빠르기를 추가하지 않았습니다: "+str(error))
        else:
            report["warnings"].append("원래 MusicXML 빠르기 표기가 있어 추가 빠르기 인식을 생략했습니다.")
    except (OSError, ValueError, KeyError, TypeError, AttributeError, ET.ParseError, zipfile.BadZipFile) as error:
        result = deepcopy(root)
        report["corrections"] = []
        report["warnings"].append("조표·음자리표 추가 인식을 사용할 수 없어 원래 OMR을 유지했습니다: "+str(error))
    if trace_path is not None:
        path = Path(trace_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")
    return result, report
