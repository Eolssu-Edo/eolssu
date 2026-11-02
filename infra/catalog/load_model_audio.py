#!/usr/bin/env python3
"""Validate and inventory DRAFT107; --apply inserts verified, non-scorable catalog rows."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote as urlquote, urlsplit

from stage_model_audio import checked_bytes, parse_json, require, sha, validate_bundle

SONG_FIELDS = ("source", "title", "description", "instrument_code", "jangdan_name", "difficulty", "default_bpm", "score_json", "score_version", "note_count")
AUDIO_FIELDS = ("score_version", "audio_url", "bpm_unit", "duration_ms", "start_offset_ms", "content_type", "sha256")
POLICY = "score-difficulty/provisional-fe3/v1"


def verify_inventory(model, inventory, complete=False):
    jangdans = {}
    for expected in model["references"]["jangdans"]:
        matches = [j for j in inventory["jangdans"] if j["name"] == expected["name"]]
        require(len(matches) == 1 and matches[0]["bpmUnit"] == expected["bpmUnit"], "Required existing jangdan/unit conflicts or is missing")
        jangdans[expected["name"]] = matches[0]["id"]
    for expected in model["references"]["pitches"]:
        matches = [p for p in inventory["pitches"] if p["semitone"] == expected["semitone"] or
                   (p["yul"], p["octave"]) == (expected["yul"], expected["octave"])]
        require(len(matches) <= 1 and all(all(p[k] == expected[k] for k in expected) for p in matches), "Conflicting pitch reference")
        require(not complete or len(matches) == 1, "Pitch reference missing after load")
    for expected in model["references"]["instruments"]:
        matches = [i for i in inventory["instruments"] if i["code"].casefold() == expected["code"].casefold()]
        require(len(matches) <= 1 and all(all(i[k] == expected[k] for k in expected) for i in matches), "Conflicting instrument reference")
        require(not complete or len(matches) == 1, "Instrument reference missing after load")
    require(sum(i["code"] == "DANSO" for i in inventory["instruments"]) == 1, "Existing DANSO must be preserved")
    expected = {row["source"]: row for row in model["rows"]}
    found = {}
    for song in inventory["songs"]:
        source = song["source"]
        require(source in expected and source not in found, "Duplicate or conflicting canonical source")
        row = expected[source]
        require(all(song[k] == row[k] for k in SONG_FIELDS) and song["jangdan_id"] == jangdans.get(row["jangdan_name"]) and song["umulsai_text"] is None and
                song["is_scorable"] == 0 and song["status"] == "DRAFT" and song["deleted_at"] is None, "Existing song conflicts")
        audio = [a for a in inventory["audios"] if a["song_id"] == song["id"]]
        require(len(audio) == 1 and all(audio[0][k] == row[k] for k in AUDIO_FIELDS) and
                audio[0]["bpm"] == row["default_bpm"] and audio[0]["status"] == "DRAFT", "Existing audio conflicts or is missing")
        found[source] = song["id"]
    require(len(inventory["audios"]) == len(found), "Unmatched audio rows")
    require(not complete or set(found) == set(expected), "Read-back is incomplete")
    return found


def quote(value):
    return "CONVERT(X'" + value.encode("utf-8").hex() + "' USING utf8mb4)"


def validate_receipt(checksum, items, receipt):
    prefix = f"model-audio/drafts/{checksum}/"
    require(receipt.get("status") == "uploaded" and receipt.get("phase") == "complete" and
            receipt.get("manifestSha256") == checksum and receipt.get("prefix") == prefix, "Upload receipt is not complete")
    objects = receipt.get("objects")
    require(isinstance(objects, list) and len(objects) == len(items), "Upload receipt object count mismatch")
    indexed = {item["path"]: item for item in objects}
    require(len(indexed) == len(items), "Duplicate uploaded object")
    for item in items:
        actual = indexed.get(item["path"], {})
        require(all(actual.get(k) == item[k] for k in ("path", "role", "sha256", "bytes")) and
                actual.get("key") == prefix + item["path"] and actual.get("status") in ("new", "reused", "reused-race"),
                "Uploaded object was not byte-verified")
    require(receipt.get("new") == sum(item["status"] == "new" for item in objects) and
            receipt.get("reused") == sum(item["status"] != "new" for item in objects), "Upload counts mismatch")
    return prefix


def validate_difficulty(cases, checksum, difficulty):
    require(difficulty.get("schema") == "model-audio-difficulty-batch/v1" and difficulty.get("policyVersion") == POLICY and
            difficulty.get("manifestSha256") == checksum and difficulty.get("count") == len(cases), "Difficulty batch mismatch")
    records = difficulty["cases"]
    indexed = {row["caseId"]: row for row in records}
    require(len(indexed) == len(records) == len(cases), "Duplicate/missing difficulty case")
    for case in cases:
        row = indexed.get(case["caseId"], {})
        require(row.get("sourceId") == case["sourceId"] and row.get("inputSha256") == case["sourceCommonSha256"] and
                row.get("bpm") == case["defaultBpm"] and type(row.get("difficulty")) is int and row["difficulty"] in (1, 2, 3),
                "Difficulty does not bind the canonical case")
        evidence = row["evidence"]
        require(evidence["policyVersion"] == POLICY and evidence["level"] == row["difficulty"] and
                evidence["measurements"]["noteCount"] == case["noteCount"], "Provisional difficulty evidence mismatch")
    return indexed


def prepare(bundle, difficulty_path, upload_receipt, reference_plan, env_file):
    root, checksum, items = validate_bundle(bundle)
    indexed_files = {item["path"]: item for item in items}
    manifest = parse_json(checked_bytes(root, indexed_files["manifest.json"]))
    prefix = validate_receipt(checksum, items, parse_json(Path(upload_receipt).read_bytes()))
    difficulty_bytes, reference_bytes = Path(difficulty_path).read_bytes(), Path(reference_plan).read_bytes()
    difficulty = validate_difficulty(manifest["cases"], checksum, parse_json(difficulty_bytes))
    references = parse_json(reference_bytes)
    require(references["schema"] == "model-audio-reference-plan/v1" and references["manifestSha256"] == checksum and
            references["basis"]["activationApproved"] is False and references["basis"]["scoringApproved"] is False, "Reference plan mismatch")
    require(references.get("jangdans") == [{"name": "중모리", "bpmUnit": "QUARTER"}], "Explicit existing jangdan plan is required")
    offsets = {yul: offset for offset, yul in enumerate("황대태협고중유임이남무응")}
    pitch_keys = set()
    for pitch in references["pitches"]:
        require(set(pitch) == {"yul", "octave", "hanja", "hangul", "semitone"} and pitch["yul"] in offsets and
                type(pitch["octave"]) is int and -2 <= pitch["octave"] <= 2 and type(pitch["semitone"]) is int and
                pitch["semitone"] == 12 * pitch["octave"] + offsets[pitch["yul"]] and
                0 < len(pitch["hanja"]) <= 5 and 0 < len(pitch["hangul"]) <= 10, "Invalid explicit pitch reference")
        key = (pitch["yul"], pitch["octave"])
        require(key not in pitch_keys, "Duplicate pitch reference")
        pitch_keys.add(key)
    instruments = references["instruments"]
    require(len(instruments) == 3 and {i["code"] for i in instruments} == {"SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM"}, "Unexpected new instrument")
    for instrument in instruments:
        require(set(instrument) == {"code", "name", "lowSemitone", "highSemitone", "pitchToleranceCent", "isActive"} and
                instrument["isActive"] is False and type(instrument["pitchToleranceCent"]) is int and instrument["pitchToleranceCent"] == 50 and
                0 < len(instrument["name"]) <= 50 and type(instrument["lowSemitone"]) is int and type(instrument["highSemitone"]) is int,
                "New instruments must remain inactive with explicit initial values")
    values = {}
    for line in Path(env_file).read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and key.strip() in ("RECORDING_STORAGE_ENDPOINT", "RECORDING_STORAGE_BUCKET"):
            values[key.strip()] = value.strip().strip("\"'")
    endpoint, bucket = values["RECORDING_STORAGE_ENDPOINT"], values["RECORDING_STORAGE_BUCKET"]
    url = urlsplit(endpoint)
    require(url.scheme == "https" and url.hostname and not url.username and not url.password and not url.query and not url.fragment and
            url.path in ("", "/") and re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket), "Invalid private storage address")
    rows, ranges = [], {instrument["code"]: [] for instrument in instruments}
    for case in manifest["cases"]:
        common = parse_json(checked_bytes(root, indexed_files[f'cases/{case["caseId"]}/source.common.json']))
        require(case["sourceId"] == "mr203-fixed-" + case["caseId"] and 0 < len(case["title"]) <= 100 and
                type(case["defaultBpm"]) in (int, float) and float(case["defaultBpm"]).is_integer() and 30 <= case["defaultBpm"] <= 400 and
                case["bpmUnit"] == common["music"]["bpmUnit"] == "QUARTER", "Unsupported canonical case metadata")
        instrument_code = case["instrument"].upper().replace("-", "_")
        require(case["instrumentCode"] == common["score"]["instrument"] == instrument_code, "Instrument binding changed")
        for note in common["events"]:
            if note["kind"] == "NOTE":
                require((note["yul"], note["octave"]) in pitch_keys, "Reference plan lacks a canonical pitch")
                if instrument_code in ranges:
                    ranges[instrument_code].append(12 * note["octave"] + offsets[note["yul"]])
        audio = case["audio"]
        require(type(audio["durationMs"]) is int and audio["durationMs"] > 0 and audio["contentType"] == "audio/mp4", "Invalid measured audio metadata")
        evidence = {"task": "S15P31E102-339", "manifestSha256": checksum, "caseId": case["caseId"],
                    "sourceCommonSha256": case["sourceCommonSha256"], "rights": case["rights"], "review": case["review"],
                    "difficulty": difficulty[case["caseId"]]["evidence"]}
        rows.append(dict(source=case["sourceId"], title=case["title"], description=json.dumps(evidence, ensure_ascii=False, sort_keys=True),
                         instrument_code=instrument_code, jangdan_name="중모리" if common["music"]["jangdan"] == "중모리" else None,
                         difficulty=difficulty[case["caseId"]]["difficulty"], default_bpm=int(case["defaultBpm"]),
                         score_json=common, score_version=case["scoreVersion"], note_count=case["noteCount"], bpm_unit=case["bpmUnit"],
                         audio_url=endpoint.rstrip("/") + "/" + bucket + "/" + urlquote(prefix + f'cases/{case["caseId"]}/{case["instrument"]}.m4a', safe="/"),
                         duration_ms=audio["durationMs"], start_offset_ms=0, content_type="audio/mp4", sha256=audio["sha256"]))
    for instrument in instruments:
        observed = ranges[instrument["code"]]
        require(observed and (min(observed), max(observed)) == (instrument["lowSemitone"], instrument["highSemitone"]), "Instrument range differs from explicit corpus plan")
    return {"manifestSha256": checksum, "rows": rows, "references": references,
            "difficultySha256": sha(difficulty_bytes), "referencePlanSha256": sha(reference_bytes)}


def inventory_sql(model):
    sources = ",".join(quote(row["source"]) for row in model["rows"])
    target = f"(s.source IN ({sources}) OR JSON_UNQUOTE(JSON_EXTRACT(s.score_json,'$.score.sourceId')) IN ({sources}))"
    queries = [
        ("jangdans", [("id", "id"), ("name", "name"), ("bpmUnit", "bpm_unit")], "jangdans"),
        ("pitches", [(k, k) for k in ("id", "yul", "octave", "hanja", "hangul", "semitone")], "pitches"),
        ("instruments", [("code", "i.code"), ("name", "i.name"), ("lowSemitone", "lo.semitone"), ("highSemitone", "hi.semitone"),
                         ("pitchToleranceCent", "i.pitch_tolerance_cent"), ("isActive", "i.is_active")],
         "instruments i JOIN pitches lo ON lo.id=i.range_low_pitch_id JOIN pitches hi ON hi.id=i.range_high_pitch_id"),
        ("songs", [(k, "s." + k) for k in (*SONG_FIELDS, "id", "jangdan_id", "umulsai_text", "is_scorable", "status", "deleted_at") if k not in ("instrument_code", "jangdan_name")] + [("instrument_code", "i.code"), ("jangdan_name", "j.name")],
         "songs s LEFT JOIN instruments i ON i.id=s.instrument_id LEFT JOIN jangdans j ON j.id=s.jangdan_id WHERE " + target),
        ("audios", [(k, "a." + k) for k in (*AUDIO_FIELDS, "song_id", "bpm", "status")],
         "song_model_audios a JOIN songs s ON s.id=a.song_id WHERE " + target),
    ]
    return "\n".join("SELECT JSON_OBJECT('" + name + "',COALESCE(JSON_ARRAYAGG(JSON_OBJECT(" +
                     ",".join("'" + key + "'," + value for key, value in fields) + ")),JSON_ARRAY())) FROM " + table + ";"
                     for name, fields, table in queries)


def build_sql(model):
    fields = list(model["rows"][0])
    types = {key: "INT NOT NULL" for key in ("difficulty", "default_bpm", "score_version", "note_count", "duration_ms", "start_offset_ms")}
    types.update(source="VARCHAR(255) PRIMARY KEY", title="VARCHAR(100) NOT NULL", description="LONGTEXT NOT NULL", instrument_code="VARCHAR(20) NOT NULL", jangdan_name="VARCHAR(30) NULL",
                 score_json="JSON NOT NULL", audio_url="VARCHAR(2048) NOT NULL", bpm_unit="VARCHAR(32) NOT NULL", content_type="VARCHAR(64) NOT NULL", sha256="CHAR(64) NOT NULL")
    require(set(fields) == set(types), "Unexpected insert fields")
    sql = ["CREATE TEMPORARY TABLE _catalog_assert (ok INT NOT NULL CHECK(ok=1));",
           "CREATE TEMPORARY TABLE _catalog_expected (" + ",".join(key + " " + types[key] for key in fields) + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"]
    for row in model["rows"]:
        values = ["NULL" if row[key] is None else str(row[key]) if type(row[key]) is int else quote(json.dumps(row[key], ensure_ascii=False) if key == "score_json" else row[key]) for key in fields]
        sql.append("INSERT INTO _catalog_expected (" + ",".join(fields) + ") VALUES (" + ",".join(values) + ");")
    sql.extend(["SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "START TRANSACTION;"])
    def check(expression):
        sql.append("INSERT INTO _catalog_assert VALUES (IF(COALESCE((" + expression + "),0),1,0));")
    check("(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('pitches','instruments','songs','song_model_audios') AND engine='InnoDB')=4")
    check("(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='songs' AND column_name='jangdan_id' AND is_nullable='YES')=1")
    check("(SELECT COUNT(*) FROM instruments WHERE BINARY code='DANSO')=1")
    for jangdan in model["references"]["jangdans"]:
        check(f"(SELECT COUNT(*) FROM jangdans WHERE BINARY name=BINARY {quote(jangdan['name'])} AND BINARY bpm_unit=BINARY {quote(jangdan['bpmUnit'])})=1")
    inserts = []
    for pitch in model["references"]["pitches"]:
        matching = f"semitone={pitch['semitone']} OR (yul={quote(pitch['yul'])} AND octave={pitch['octave']})"
        same = " AND ".join(key + "=" + str(value) if type(value) is int else "BINARY " + key + "=BINARY " + quote(value) for key, value in pitch.items())
        check(f"(SELECT COUNT(*) FROM pitches WHERE ({matching}) AND NOT({same}))=0")
        inserts.append("INSERT INTO pitches (" + ",".join(pitch) + ") SELECT " + ",".join(str(v) if type(v) is int else quote(v) for v in pitch.values()) + f" WHERE NOT EXISTS(SELECT 1 FROM pitches WHERE {matching});")
    for instrument in model["references"]["instruments"]:
        code = quote(instrument["code"])
        same = f"BINARY i.code=BINARY {code} AND BINARY i.name=BINARY {quote(instrument['name'])} AND lo.semitone={instrument['lowSemitone']} AND hi.semitone={instrument['highSemitone']} AND i.pitch_tolerance_cent=50 AND i.is_active=0"
        check(f"(SELECT COUNT(*) FROM instruments i LEFT JOIN pitches lo ON lo.id=i.range_low_pitch_id LEFT JOIN pitches hi ON hi.id=i.range_high_pitch_id WHERE i.code={code} AND NOT(COALESCE(({same}),0)))=0")
        inserts.append(f"INSERT INTO instruments (code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active) SELECT {code},{quote(instrument['name'])},lo.id,hi.id,50,0 FROM pitches lo JOIN pitches hi ON hi.semitone={instrument['highSemitone']} WHERE lo.semitone={instrument['lowSemitone']} AND NOT EXISTS(SELECT 1 FROM instruments WHERE code={code});")
    joined = "FROM songs s JOIN _catalog_expected e ON (s.source=e.source OR JSON_UNQUOTE(JSON_EXTRACT(s.score_json,'$.score.sourceId'))=e.source) LEFT JOIN instruments i ON i.id=s.instrument_id"
    same_song = " AND ".join("BINARY s." + key + " <=> BINARY e." + key for key in ("source", "title", "description"))
    same_song += " AND " + " AND ".join("s." + key + "=e." + key for key in ("difficulty", "default_bpm", "score_version", "note_count"))
    same_song += " AND BINARY i.code=BINARY e.instrument_code AND BINARY CAST(s.score_json AS CHAR)=BINARY CAST(e.score_json AS CHAR) AND ((e.jangdan_name IS NULL AND s.jangdan_id IS NULL) OR EXISTS(SELECT 1 FROM jangdans j WHERE j.id=s.jangdan_id AND BINARY j.name=BINARY e.jangdan_name AND BINARY j.bpm_unit=BINARY e.bpm_unit)) AND s.umulsai_text IS NULL AND s.is_scorable=0 AND BINARY s.status='DRAFT' AND s.deleted_at IS NULL"
    same_audio = " AND ".join("BINARY a." + key + "=BINARY e." + key for key in AUDIO_FIELDS)
    same_audio += " AND a.bpm=e.default_bpm AND BINARY a.status='DRAFT'"
    check(f"(SELECT COUNT(*) {joined} WHERE NOT(COALESCE(({same_song}),0)))=0")
    check(f"(SELECT COUNT(*) FROM (SELECT e.source {joined} GROUP BY e.source HAVING COUNT(*)>1) duplicated)=0")
    check(f"(SELECT COUNT(*) {joined} WHERE (SELECT COUNT(*) FROM song_model_audios a WHERE a.song_id=s.id)<>1 OR NOT EXISTS(SELECT 1 FROM song_model_audios a WHERE a.song_id=s.id AND {same_audio}))=0")
    sql.append("SET @catalog_existing=(SELECT COUNT(*) FROM songs s JOIN _catalog_expected e ON s.source=e.source);")
    sql.extend(inserts)  # Every existing-row guard precedes the first persistent INSERT.
    sql.append("INSERT INTO songs (instrument_id,jangdan_id,title,description,difficulty,default_bpm,score_json,score_version,note_count,source,is_scorable,status) SELECT i.id,(SELECT id FROM jangdans j WHERE BINARY j.name=BINARY e.jangdan_name AND BINARY j.bpm_unit=BINARY e.bpm_unit),e.title,e.description,e.difficulty,e.default_bpm,e.score_json,e.score_version,e.note_count,e.source,0,'DRAFT' FROM _catalog_expected e JOIN instruments i ON BINARY i.code=BINARY e.instrument_code WHERE NOT EXISTS(SELECT 1 FROM songs s WHERE s.source=e.source OR JSON_UNQUOTE(JSON_EXTRACT(s.score_json,'$.score.sourceId'))=e.source);")
    sql.append("INSERT INTO song_model_audios (song_id,score_version,audio_url,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status) SELECT s.id,e.score_version,e.audio_url,e.default_bpm,e.bpm_unit,e.duration_ms,e.start_offset_ms,e.content_type,e.sha256,'DRAFT' FROM _catalog_expected e JOIN songs s ON BINARY s.source=BINARY e.source WHERE NOT EXISTS(SELECT 1 FROM song_model_audios a WHERE a.song_id=s.id);")
    check(f"(SELECT COUNT(*) {joined} WHERE {same_song})={len(model['rows'])}")
    check(f"(SELECT COUNT(*) {joined} JOIN song_model_audios a ON a.song_id=s.id WHERE {same_audio})={len(model['rows'])}")
    sql.extend(["COMMIT;", f"SELECT JSON_OBJECT('new',{len(model['rows'])}-@catalog_existing,'reused',@catalog_existing);"])
    return "\n".join(sql)


def mysql(sql):
    # Same deployed-container discovery as infra/terms/publish.py. No password enters host arguments or output.
    inspected = subprocess.run(["docker", "inspect", "eolssu-runtime-backend-1"], capture_output=True, timeout=15, check=True)
    environment = dict(item.split("=", 1) for item in json.loads(inspected.stdout)[0]["Config"]["Env"] if "=" in item)
    matched = re.fullmatch(r"jdbc:mysql://[^/]+/([A-Za-z0-9_]+)(?:\?.*)?", environment.get("DB_URL", ""))
    require(matched is not None, "Active backend database could not be resolved")
    command = ["docker", "exec", "-i", "eolssu-runtime-mysql-1", "sh", "-c",
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --no-defaults --skip-reconnect --default-character-set=utf8mb4 --user=root --database=' +
               matched.group(1) + " --batch --raw --skip-column-names"]
    result = subprocess.run(command, input=sql.encode("utf-8"), capture_output=True, timeout=120)
    if result.returncode:
        raise RuntimeError("Catalog database batch failed; read back before retrying")
    return result.stdout.decode("utf-8").strip()


def inventory(model, runner):
    result = {}
    for line in runner(inventory_sql(model)).splitlines():
        result.update(parse_json(line))
    require(set(result) == {"jangdans", "pitches", "instruments", "songs", "audios"}, "Database inventory is incomplete")
    return result


def run(model, receipt, *, apply=False, runner=mysql):
    report = {"status": "inventory", "manifestSha256": model["manifestSha256"], "difficultySha256": model["difficultySha256"],
              "referencePlanSha256": model["referencePlanSha256"], "new": 0, "reused": 0, "songIds": {}}
    fd = os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        try:
            before = verify_inventory(model, inventory(model, runner))
            report.update(status="validated-dry-run", wouldInsert=len(model["rows"]) - len(before), reused=len(before), songIds=before)
            if apply:
                report["status"] = "apply-outcome-unknown"
                counts = parse_json(runner(build_sql(model)))
                report["status"] = "committed-readback-pending"
                after = verify_inventory(model, inventory(model, runner), complete=True)
                require(all(after.get(source) == identity for source, identity in before.items()), "Existing identity changed")
                require(counts["new"] + counts["reused"] == len(model["rows"]), "Database write count mismatch")
                report.update(status="applied", new=counts["new"], reused=counts["reused"], songIds=after)
        except BaseException as error:
            report["errorType"] = type(error).__name__
            raise
        finally:
            report["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("bundle", "difficulty", "upload-receipt", "reference-plan", "env-file", "receipt"):
        parser.add_argument("--" + option, type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    try:
        require(not args.receipt.resolve().is_relative_to(args.bundle.resolve()), "Receipt must stay outside the immutable bundle")
        model = prepare(args.bundle, args.difficulty, args.upload_receipt, args.reference_plan, args.env_file)
        report = run(model, args.receipt, apply=args.apply)
    except Exception as error:
        print("Catalog load stopped (" + type(error).__name__ + "); inspect receipt and existing rows before retrying", file=sys.stderr)
        return 1
    print(json.dumps({key: report[key] for key in ("status", "manifestSha256", "new", "reused")}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
