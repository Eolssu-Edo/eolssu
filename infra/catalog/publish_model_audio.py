#!/usr/bin/env python3
"""Publish or hide exact generated cases. Defaults to read-only; never deletes assets."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys

import load_model_audio as loader

PAIRS = {"DRAFT": ("DRAFT", "DRAFT"), "PUBLISHED": ("PUBLISHED", "PUBLISHED"), "HIDDEN": ("HIDDEN", "DRAFT")}
TARGETS = {"publish": "PUBLISHED", "hide": "HIDDEN"}
IDENTITY_AUDIO = ("score_version", "object_key", "performance_object_key", "source_common_sha256", "sha256")


def select(model, *, all_cases=False, cases=()):
    loader.require(bool(all_cases) != bool(cases), "Select --all or exact --case identities")
    loader.require(len(cases) == len(set(cases)), "Duplicate case selection")
    indexed = {row["generation_metadata"]["caseId"]: row for row in model["rows"]}
    loader.require(len(indexed) == len(model["rows"]) and set(cases) <= set(indexed), "Unknown case identity")
    return dict(model, rows=list(model["rows"]) if all_cases else [indexed[case] for case in cases])


def verify(model, inventory, action, from_status):
    loader.require(action in TARGETS and from_status in PAIRS, "Unknown publication transition")
    target = PAIRS[TARGETS[action]]
    expected = {row["source"]: row for row in model["rows"]}
    loader.require(expected and len(expected) == len(model["rows"]), "Empty/duplicate source selection")
    found, before, changed = {}, {}, 0
    for song in inventory["songs"]:
        source = song["source"]
        loader.require(source in expected and source not in found, "Unexpected or duplicate source identity")
        row = expected[source]
        loader.require(song["score_version"] == row["score_version"] and song["deleted_at"] is None, "Current score identity changed")
        audios = [audio for audio in inventory["audios"] if audio["song_id"] == song["id"]]
        loader.require(len(audios) == 1 and all(audios[0][key] == row[key] for key in IDENTITY_AUDIO), "Current audio identity changed")
        audio = audios[0]
        pair = (song["status"], audio["status"])
        loader.require(pair in (PAIRS[from_status], target), "Current publication status differs")
        if action == "publish":
            loader.require(all(song[key] == row[key] for key in loader.SONG_FIELDS) and song["is_scorable"] == 0 and
                           song["umulsai_text"] is None and loader.audio_matches(row, audio) and
                           audio["bpm"] == row["default_bpm"], "Publication payload differs from verified source")
        found[source] = song["id"]
        before[source] = {"songId": song["id"], "songStatus": pair[0], "audioStatus": pair[1]}
        changed += pair != target
    loader.require(set(found) == set(expected) and len(inventory["audios"]) == len(found), "Selected catalog rows are incomplete")
    return {"songIds": found, "before": before, "wouldChange": changed}


def build_sql(model, action, from_status):
    loader.require(action in TARGETS and from_status in PAIRS, "Unknown publication transition")
    target = PAIRS[TARGETS[action]]
    original = PAIRS[from_status]
    sql = loader.expected_table_sql(model)
    sql.extend(["SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "START TRANSACTION;"])
    def check(expression):
        sql.append(loader.assertion_sql(expression))
    check("(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('songs','song_model_audios') AND engine='InnoDB')=2")
    joined, same_song, same_audio = loader.matching_rows_sql()
    if action == "hide":
        same_song = "BINARY s.source=BINARY e.source AND s.score_version=e.score_version AND s.deleted_at IS NULL"
        same_audio = " AND ".join("BINARY a." + key + "=BINARY e." + key for key in IDENTITY_AUDIO)
    joined += " JOIN song_model_audios a ON a.song_id=s.id"
    pair = lambda states: f"BINARY s.status='{states[0]}' AND BINARY a.status='{states[1]}'"
    check(f"(SELECT COUNT(*) {joined})={len(model['rows'])}")
    check(f"(SELECT COUNT(DISTINCT e.source) {joined})={len(model['rows'])}")
    check(f"(SELECT COUNT(*) {joined} WHERE NOT(COALESCE(({same_song} AND {same_audio}),0)))=0")
    check(f"(SELECT COUNT(*) {joined} WHERE NOT(({pair(original)}) OR ({pair(target)})))=0")
    sql.append(f"SET @catalog_changed=(SELECT COUNT(*) {joined} WHERE NOT({pair(target)}));")
    sql.append("UPDATE songs s JOIN _catalog_expected e ON BINARY s.source=BINARY e.source JOIN song_model_audios a ON a.song_id=s.id " +
               f"SET s.status='{target[0]}',a.status='{target[1]}' WHERE ({pair(original)}) AND NOT({pair(target)});")
    check(f"(SELECT COUNT(*) {joined} WHERE {pair(target)} AND {same_song} AND {same_audio})={len(model['rows'])}")
    sql.extend(["COMMIT;", f"SELECT JSON_OBJECT('changed',@catalog_changed,'reused',{len(model['rows'])}-@catalog_changed);"])
    return "\n".join(sql)


def run(model, receipt, *, action, from_status, apply=False, application_ready=False, runner=loader.mysql):
    loader.require(not apply or action != "publish" or application_ready,
                   "Publish requires deployed BE/FE playback checks (--application-ready); listening approval is not required")
    report = {"status": "inventory", "manifestSha256": model["manifestSha256"], "action": action, "fromStatus": from_status,
              "applicationReady": application_ready, "changed": 0, "reused": 0}
    fd = os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        try:
            before = verify(model, loader.inventory(model, runner), action, from_status)
            report.update(before, status="validated-dry-run")
            if apply:
                report["status"] = "apply-outcome-unknown"
                counts = loader.parse_json(runner(build_sql(model, action, from_status)))
                report["status"] = "committed-readback-pending"
                after = verify(model, loader.inventory(model, runner), action, TARGETS[action])
                loader.require(after["songIds"] == before["songIds"] and after["wouldChange"] == 0 and
                               counts["changed"] + counts["reused"] == len(model["rows"]), "Publication read-back differs")
                report.update(status="applied", after=after["before"], **counts)
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
    for name in ("bundle", "difficulty", "upload-receipt", "reference-plan", "receipt"):
        parser.add_argument("--" + name, type=Path, required=True)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--all", action="store_true")
    selection.add_argument("--case", action="append", default=[])
    parser.add_argument("--action", choices=TARGETS, required=True)
    parser.add_argument("--from-status", choices=PAIRS, required=True)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--application-ready", action="store_true", help="Deployed BE/FE list, timing and media playback were checked")
    args = parser.parse_args()
    try:
        loader.require(not args.receipt.resolve().is_relative_to(args.bundle.resolve()), "Receipt must stay outside immutable bundle")
        model = select(loader.prepare(args.bundle, args.difficulty, args.upload_receipt, args.reference_plan), all_cases=args.all, cases=args.case)
        result = run(model, args.receipt, action=args.action, from_status=args.from_status, apply=args.apply, application_ready=args.application_ready)
    except Exception as error:
        print("Catalog publication stopped (" + type(error).__name__ + "); inspect receipt and current rows before retrying", file=sys.stderr)
        return 1
    print(json.dumps({key: result[key] for key in ("status", "action", "changed", "reused")}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
