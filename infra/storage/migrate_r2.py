#!/usr/bin/env python3
"""Read-only inventory by default. --apply requires an upload pause and copies only, never deletes R2."""
import argparse
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import sys
import tempfile
from datetime import datetime, timedelta, timezone

KEY = re.compile(r"recordings/[1-9][0-9]*/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.m4a")


def read_env(path):
    result = {}
    for line in Path(path).read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            value = value.strip()
            if value[:1] in ("'", '"') and value[-1:] == value[:1]:
                value = value[1:-1]
            result[key.strip()] = value
    return result


def checked_target(root, key):
    if not KEY.fullmatch(key):
        raise ValueError("unexpected object key; review inventory privately")
    target = root / key
    if any(parent.is_symlink() for parent in (target, *target.parents)):
        raise ValueError("symlink destination rejected")
    return target


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def migrate(client, bucket, root, apply=False, now=None):
    now = now or datetime.now(timezone.utc)
    objects = []
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix="recordings/"):
        objects.extend(page.get("Contents", []))
    # Validate the entire inventory before creating any destination files.
    for obj in objects:
        checked_target(root, obj["Key"])
    report = {"objects": len(objects), "bytes": sum(o["Size"] for o in objects), "expired": 0, "copied": 0, "verified_existing": 0}
    manifest = []
    for obj in objects:
        if obj["LastModified"] + timedelta(days=30) <= now:
            report["expired"] += 1
            continue
        if not apply:
            continue
        target = checked_target(root, obj["Key"])
        target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        checked_target(root, obj["Key"])
        response = client.get_object(Bucket=bucket, Key=obj["Key"], IfMatch=obj["ETag"])
        # ListObjects can include milliseconds; the HTTP Last-Modified header has whole-second precision.
        # IfMatch still protects source identity. Preserve the precise inventory timestamp on disk.
        if response["ContentLength"] != obj["Size"] or response["LastModified"].replace(microsecond=0) != obj["LastModified"].replace(microsecond=0):
            response["Body"].close()
            raise ValueError("source changed during migration; repeat inventory with uploads paused")
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(prefix=".upload-migrate-", suffix=".tmp", dir=target.parent, delete=False) as output:
                temporary = Path(output.name)
                with response["Body"]:
                    for chunk in response["Body"].iter_chunks(chunk_size=1024 * 1024):
                        output.write(chunk)
                output.flush()
                os.fsync(output.fileno())
            if temporary.stat().st_size != obj["Size"]:
                raise ValueError("download size mismatch")
            sha = digest(temporary)
            uploaded = obj["LastModified"].timestamp()
            os.utime(temporary, (uploaded, uploaded))
            if target.exists():
                if target.stat().st_size != obj["Size"] or digest(target) != sha:
                    raise ValueError("existing local object differs; do not overwrite")
                if abs(target.stat().st_mtime - uploaded) > 0.001:
                    raise ValueError("existing local object upload timestamp differs")
                report["verified_existing"] += 1
            else:
                # Hard-link publishes atomically and refuses to replace an existing target.
                os.link(temporary, target)
                report["copied"] += 1
            if digest(target) != sha:
                raise ValueError("local verification failed")
            manifest.append({"key": obj["Key"], "bytes": obj["Size"], "sha256": sha, "uploaded_at": obj["LastModified"].isoformat()})
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
    return report, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--stage", action="store_true", help="copy a staging snapshot without claiming final cutover")
    parser.add_argument("--uploads-paused", action="store_true")
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    if args.apply and args.stage:
        parser.error("choose either --stage or --apply")
    if args.apply and (not args.uploads_paused or args.manifest is None):
        parser.error("--apply requires --uploads-paused and a private --manifest path")
    if args.stage and args.manifest is None:
        parser.error("--stage requires a private --manifest path")
    os.umask(0o077)
    import boto3
    from botocore.config import Config
    logging.getLogger("botocore").setLevel(logging.CRITICAL)
    env = read_env(args.env_file)
    client = boto3.client("s3", endpoint_url=env["RECORDING_STORAGE_ENDPOINT"],
        aws_access_key_id=env["RECORDING_STORAGE_ACCESS_KEY_ID"], aws_secret_access_key=env["RECORDING_STORAGE_SECRET_ACCESS_KEY"],
        region_name=env.get("RECORDING_STORAGE_REGION", "auto"), config=Config(signature_version="s3v4", retries={"max_attempts": 2}, connect_timeout=5, read_timeout=30))
    report, manifest = migrate(client, env["RECORDING_STORAGE_BUCKET"], args.destination.absolute(), args.apply or args.stage)
    report["mode"] = "final-copy" if args.apply else "staging-copy" if args.stage else "inventory"
    if args.apply or args.stage:
        # Exclusive creation: never overwrite a previous audit manifest.
        fd = os.open(args.manifest, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as out:
            json.dump({"checked_at": datetime.now(timezone.utc).isoformat(), "report": report, "objects": manifest}, out)
    print(json.dumps(report))  # No credentials, keys, signed URLs or personal data in console output.


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        import traceback
        frame = traceback.extract_tb(error.__traceback__)[-1]
        print(f"Migration failed ({type(error).__name__}, line {frame.lineno}); source left unchanged", file=sys.stderr)
        sys.exit(1)
