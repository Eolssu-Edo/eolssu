"""Prepare a reviewed, version-bound draft repair; this tool never writes to a database."""

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path


def digest(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def prepare(snapshot, target, *, user_id, sheet_id, revision):
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    require(snapshot.get("userId") == user_id and snapshot.get("sheetId") == sheet_id,
            "The snapshot belongs to a different account or score")
    require(snapshot.get("draftRevision") == revision, "The draft revision changed")
    version = snapshot.get("scoreVersion")
    require(type(version) is int and version > 0 and snapshot.get("draftBaseVersion") == version,
            "The draft is not based on the current score version")
    require(snapshot.get("source") == "STAFF_OMR", "Only the diagnosed staff import is supported")
    require(all(target.get(key) == value for key, value in {
        "jangdanId": "12", "name": "4박 기본 반주", "bpmUnit": "QUARTER",
        "defaultBpm": 160, "beatCount": 4, "patternCellEighths": 2,
    }.items()), "The reviewed four-beat catalog entry changed")
    draft = snapshot.get("draft")
    require(isinstance(draft, dict), "There is no saved draft")
    require("expressions" in snapshot and snapshot["expressions"] is None and
            "expressions" in draft and draft["expressions"] is None,
            "A tempo companion must be reviewed separately")
    require(draft.get("bpm") == 66 and draft.get("jangdanId") in (None, "2"),
            "The user changed the default tempo or selected a different accompaniment")
    score = draft.get("score", {})
    music = score.get("music", {})
    require(music.get("bpmUnit") == "DOTTED_QUARTER" and music.get("ticksPerBeat") == 18 and
            music.get("ticksPerJeonggan") == 12 and music.get("jangdan") in ("", "굿거리"),
            "The draft does not have the diagnosed timing")
    require(score.get("provenance", {}).get("converter", "").startswith("staff-omr/"),
            "The score was not recognized as staff notation")
    columns = score.get("columns", [])
    events = score.get("events", [])
    require(bool(columns) and all(len(column.get("cells", [])) == 4 for column in columns),
            "Every column must contain a complete four-beat cycle")
    require(bool(events) and events[0].get("cellId") == "c0-j0" and events[0].get("startTick") == 0,
            "The accompaniment start is unknown")
    authoring = snapshot.get("authoring") or snapshot.get("recognition", {}).get("authoring")
    source = (authoring or {}).get("source")
    require(isinstance(source, dict) and source == draft.get("authoring", {}).get("source"),
            "The immutable recognition source changed")
    storage_sha = snapshot.get("draftStorageSha256", "")
    require(len(storage_sha) == 64 and all(c in "0123456789abcdef" for c in storage_sha),
            "The database draft fingerprint is missing")

    repaired = deepcopy(draft)
    repaired["bpm"], repaired["jangdanId"] = 160, "12"
    repaired["score"]["music"].update(bpmUnit="QUARTER", ticksPerBeat=12, jangdan="4박 기본 반주")
    return {"sheetId": sheet_id, "userId": user_id, "expectedScoreVersion": version,
            "expectedDraftRevision": revision, "expectedStorageSha256": storage_sha,
            "beforeDraftSha256": digest(draft), "afterDraftSha256": digest(repaired), "draft": repaired}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--sheet-id", type=int, required=True)
    parser.add_argument("--expected-revision", type=int, required=True)
    args = parser.parse_args()
    snapshot = json.loads(args.snapshot.read_text(encoding="utf-8-sig"))
    catalog = json.loads(args.catalog.read_text(encoding="utf-8-sig"))["result"]["items"]
    target = next(item for item in catalog if item["jangdanId"] == "12")
    result = prepare(snapshot, target, user_id=args.user_id, sheet_id=args.sheet_id,
                     revision=args.expected_revision)
    with args.output.open("x", encoding="utf-8") as output:
        output.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
