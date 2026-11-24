#!/usr/bin/env python3
"""Compare every object in the R2 buckets with the keys the production database references. Read-only.

Reports, per bucket and top-level prefix, objects no database row points at (orphans and old leftovers) and database
keys whose object is missing. It never writes, copies or deletes objects and never changes the database. Console output
holds counts and sizes only; the keys themselves go to an optional new root-only file (--keys-out).
"""
import argparse
import json
import logging
import os
from pathlib import Path
import sys
from datetime import datetime, timedelta, timezone

# Storage groups as the backend configures them (application.yml). Several groups may share one bucket.
GROUPS = ("RECORDING_STORAGE", "SHEET_STORAGE", "REFERENCE_AUDIO_STORAGE")

# Every database column that holds an object key, with the group whose bucket the backend reads it from.
# practice_sessions.recording_url: REC-04 record deletion and the unsaved-recording purge set it to NULL.
# song_model_audios: model audio is read through the recording storage (ModelAudioConfig).
REFERENCES = (
    ("RECORDING_STORAGE", "SELECT recording_url FROM practice_sessions WHERE recording_url IS NOT NULL;"),
    ("RECORDING_STORAGE", "SELECT object_key FROM song_model_audios WHERE object_key IS NOT NULL;"),
    ("RECORDING_STORAGE", "SELECT performance_object_key FROM song_model_audios WHERE performance_object_key IS NOT NULL;"),
    ("SHEET_STORAGE", "SELECT object_key FROM sheet_source_files;"),
    ("SHEET_STORAGE", "SELECT object_key FROM sheet_source_pages;"),
    ("SHEET_STORAGE", "SELECT object_key FROM sheet_audio_objects;"),
    ("REFERENCE_AUDIO_STORAGE", "SELECT object_key FROM reference_audio_assets;"),
)

# Prefixes whose objects belong to a member: an unreferenced object here means a deletion did not reach storage.
USER_PREFIXES = ("recordings/", "sheet-sources/", "sheet-audio/")
# Import uploads before conversion; the backend removes them itself, so only old ones are leftovers.
TEMPORARY_PREFIXES = ("sheet-import-temp/",)
DEFAULT_GRACE = timedelta(hours=24)


def read_env(path):
    """Same single-line KEY=value reading as the other storage tools; nothing is executed or expanded."""
    result = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):]
        key, separator, value = line.partition("=")
        if separator:
            value = value.strip()
            if value[:1] in ("'", '"') and value[-1:] == value[:1]:
                value = value[1:-1]
            result[key.strip()] = value
    return result


def buckets(env):
    """Distinct (endpoint, bucket) pairs with the groups that use them, in GROUPS order. Unconfigured groups are skipped."""
    found = {}
    for group in GROUPS:
        endpoint, bucket = env.get(f"{group}_ENDPOINT", ""), env.get(f"{group}_BUCKET", "")
        if endpoint and bucket:
            found.setdefault((endpoint, bucket), []).append(group)
    return found


def kind(key):
    if key.startswith(USER_PREFIXES):
        return "user"
    if key.startswith(TEMPORARY_PREFIXES):
        return "temporary"
    return "other"


def prefix(key):
    head, separator, _ = key.partition("/")
    return head + "/" if separator else "(root)"


def referenced_keys(query, groups):
    """Keys the database references for the given groups. Values that are not object keys (old URLs) are counted apart."""
    keys, non_keys = set(), 0
    for group, sql in REFERENCES:
        if group not in groups:
            continue
        for line in query(sql).splitlines():
            value = line.strip()
            if not value:
                continue
            if "://" in value or value.startswith("/"):
                non_keys += 1
            else:
                keys.add(value)
    return keys, non_keys


def list_objects(client, bucket):
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket):
        for item in page.get("Contents", []):
            yield item["Key"], item["Size"], item["LastModified"]


def audit(client, bucket, referenced, now, grace=DEFAULT_GRACE):
    """Classify one bucket. Query the database before listing: an object uploaded in between is new and falls under grace."""
    prefixes, unreferenced, seen = {}, [], set()
    for key, size, modified in list_objects(client, bucket):
        seen.add(key)
        row = prefixes.setdefault(prefix(key), {"kind": kind(key), "objects": 0, "bytes": 0, "referenced": 0,
                                                 "unreferenced": 0, "unreferenced_bytes": 0, "recent_unreferenced": 0,
                                                 "oldest_unreferenced": None})
        row["objects"] += 1
        row["bytes"] += size
        if key in referenced:
            row["referenced"] += 1
        elif now - modified < grace:
            # An upload whose database row is not written yet, or a temporary import still in use.
            row["recent_unreferenced"] += 1
        else:
            row["unreferenced"] += 1
            row["unreferenced_bytes"] += size
            day = modified.astimezone(timezone.utc).date().isoformat()
            if row["oldest_unreferenced"] is None or day < row["oldest_unreferenced"]:
                row["oldest_unreferenced"] = day
            unreferenced.append({"key": key, "bytes": size, "last_modified": modified.astimezone(timezone.utc).isoformat()})
    missing = sorted(referenced - seen)
    return {
        "prefixes": dict(sorted(prefixes.items())),
        "objects": sum(row["objects"] for row in prefixes.values()),
        "bytes": sum(row["bytes"] for row in prefixes.values()),
        "user_orphans": sum(row["unreferenced"] for row in prefixes.values() if row["kind"] == "user"),
        "missing": len(missing),
        "missing_by_prefix": _count_by_prefix(missing),
    }, unreferenced, missing


def _count_by_prefix(keys):
    counts = {}
    for key in keys:
        counts[prefix(key)] = counts.get(prefix(key), 0) + 1
    return dict(sorted(counts.items()))


def run(env, query, client_for, now=None, grace=DEFAULT_GRACE):
    """Audit every configured bucket. A bucket that cannot be listed is reported and makes the run incomplete."""
    now = now or datetime.now(timezone.utc)
    report, details = {"checked_at": now.isoformat(), "grace_hours": grace.total_seconds() / 3600, "buckets": []}, []
    for index, ((endpoint, bucket), groups) in enumerate(buckets(env).items(), start=1):
        # Bucket names are not secret but the label keeps output stable when names change between environments.
        entry = {"bucket": f"bucket-{index}", "groups": groups}
        referenced, non_keys = referenced_keys(query, groups)
        entry["database_keys"], entry["non_key_references"] = len(referenced), non_keys
        failure = None
        for group in groups:
            try:
                result, unreferenced, missing = audit(client_for(env, group), bucket, referenced, now, grace)
                break
            except Exception as error:  # Try the next group's credentials; never print the error text (may hold URLs).
                failure = f"{type(error).__name__} with {group} credentials"
        else:
            entry["error"] = failure
            report["buckets"].append(entry)
            continue
        entry.update(result)
        report["buckets"].append(entry)
        details.append({"bucket": entry["bucket"], "unreferenced": unreferenced, "missing": missing})
    report["complete"] = all("error" not in entry for entry in report["buckets"]) and bool(report["buckets"])
    report["user_orphans"] = sum(entry.get("user_orphans", 0) for entry in report["buckets"])
    report["missing"] = sum(entry.get("missing", 0) for entry in report["buckets"])
    return report, details


def write_keys(path, report, details):
    """Exclusive creation, owner-only: never overwrite an earlier audit and never leave keys readable by others."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as out:
        json.dump({"checked_at": report["checked_at"], "buckets": details}, out, ensure_ascii=False, indent=1)


def terms_tool():
    """The terms tool module, whose MySQL access finds the active database from the backend's DB_URL and keeps the password in the container."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("publish", Path(__file__).resolve().parents[1] / "terms" / "publish.py")
    publish = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(publish)
    return publish


def s3_client(env, group):
    import boto3
    from botocore.config import Config
    options = dict(signature_version="s3v4", retries={"max_attempts": 2}, connect_timeout=5, read_timeout=30)
    try:
        config = Config(**options, request_checksum_calculation="when_required", response_checksum_validation="when_required")
    except TypeError:  # botocore before 1.36
        config = Config(**options)
    return boto3.client("s3", endpoint_url=env[f"{group}_ENDPOINT"], aws_access_key_id=env[f"{group}_ACCESS_KEY_ID"],
                        aws_secret_access_key=env[f"{group}_SECRET_ACCESS_KEY"],
                        region_name=env.get(f"{group}_REGION") or "auto", config=config)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", required=True, help="server settings, e.g. /opt/eolssu/.env")
    parser.add_argument("--keys-out", type=Path, help="new root-only JSON file for the unreferenced and missing keys")
    parser.add_argument("--grace-hours", type=float, default=24, help="ignore unreferenced objects newer than this (default 24)")
    args = parser.parse_args()
    if args.grace_hours < 0:
        parser.error("--grace-hours must not be negative")
    os.umask(0o077)
    logging.getLogger("botocore").setLevel(logging.CRITICAL)
    env = read_env(args.env_file)
    if not buckets(env):
        parser.error("no *_STORAGE_ENDPOINT and *_STORAGE_BUCKET pair in the env file")
    query = terms_tool().mysql
    report, details = run(env, query, s3_client, grace=timedelta(hours=args.grace_hours))
    if args.keys_out:
        write_keys(args.keys_out, report, details)
    print(json.dumps(report, ensure_ascii=False, indent=1))  # Counts only: no credentials, keys or signed URLs.
    sys.exit(0 if report["complete"] else 2)


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as error:
        import traceback
        frame = traceback.extract_tb(error.__traceback__)[-1]
        print(f"Audit failed ({type(error).__name__}, {Path(frame.filename).name}:{frame.lineno}); nothing was changed", file=sys.stderr)
        sys.exit(1)
