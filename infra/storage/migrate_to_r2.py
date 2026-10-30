#!/usr/bin/env python3
"""Move domestic-disk recordings back to R2. Read-only by default; every write or delete needs an explicit flag.

R2 lifecycle ages count from the R2 upload, so copies carry the original upload time and --purge-expired deletes
them 30 days after that time instead.
"""
import argparse
import base64
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import sys
from datetime import datetime, timedelta, timezone

KEY = re.compile(r"recordings/[1-9][0-9]*/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.m4a")
RETENTION = timedelta(days=30)
UPLOADED_AT = "original-uploaded-at"


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


def inventory(root):
    """Validate the whole tree before any copy or delete. Interrupted backend uploads (.upload-*) are not recordings."""
    files = []
    base = root / "recordings"
    if not base.exists():
        return files
    for path in sorted(base.rglob("*")):
        if path.is_symlink():
            raise ValueError("symlink in recording directory rejected")
        if path.is_dir() or path.name.startswith(".upload-"):
            continue
        key = path.relative_to(root).as_posix()
        if not KEY.fullmatch(key):
            raise ValueError("unexpected file in recording directory; review it privately")
        uploaded = datetime.fromtimestamp(path.stat().st_mtime, timezone.utc)
        files.append({"key": key, "path": path, "bytes": path.stat().st_size, "uploaded_at": uploaded})
    return files


def digests(path):
    sha, md5 = hashlib.sha256(), hashlib.md5()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
            md5.update(chunk)
    return sha.hexdigest(), base64.b64encode(md5.digest()).decode()


def remote(client, bucket, key):
    try:
        return client.head_object(Bucket=bucket, Key=key)
    except Exception as error:
        if getattr(error, "response", {}).get("Error", {}).get("Code") in ("404", "NoSuchKey", "NotFound"):
            return None
        raise


def matches(head, item, sha):
    return head is not None and head["ContentLength"] == item["bytes"] and head.get("Metadata", {}).get("sha256") == sha


def migrate(client, bucket, root, apply=False, now=None):
    now = now or datetime.now(timezone.utc)
    files = inventory(root)
    report = {"files": len(files), "bytes": sum(f["bytes"] for f in files), "expired": 0, "copied": 0, "verified_existing": 0}
    manifest = []
    for item in files:
        if item["uploaded_at"] + RETENTION <= now:
            report["expired"] += 1
            continue
        if not apply:
            continue
        sha, md5 = digests(item["path"])
        head = remote(client, bucket, item["key"])
        if head is not None:
            if not matches(head, item, sha):
                raise ValueError("existing R2 object differs; do not overwrite")
            report["verified_existing"] += 1
        else:
            client.put_object(Bucket=bucket, Key=item["key"], Body=item["path"].read_bytes(), ContentType="audio/mp4",
                              ContentMD5=md5, Metadata={UPLOADED_AT: item["uploaded_at"].isoformat(), "sha256": sha})
            if not matches(remote(client, bucket, item["key"]), item, sha):
                raise ValueError("R2 verification failed after upload")
            report["copied"] += 1
        manifest.append({"key": item["key"], "bytes": item["bytes"], "sha256": sha, "uploaded_at": item["uploaded_at"].isoformat()})
    return report, manifest


def purge_expired(client, bucket, now=None, delete=False):
    """Objects without the marker are backend uploads that the bucket's 30-day lifecycle rule removes."""
    now = now or datetime.now(timezone.utc)
    report = {"tracked": 0, "expired": 0, "deleted": 0}
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix="recordings/"):
        for obj in page.get("Contents", []):
            head = remote(client, bucket, obj["Key"])
            marker = head and head.get("Metadata", {}).get(UPLOADED_AT)
            if not marker:
                continue
            report["tracked"] += 1
            if datetime.fromisoformat(marker) + RETENTION > now:
                continue
            report["expired"] += 1
            if delete:
                client.delete_object(Bucket=bucket, Key=obj["Key"])
                report["deleted"] += 1
    return report


def remove_local(client, bucket, root, now=None, delete=False):
    """Delete the disk copies only after every unexpired file is verified in R2; expired files are deleted as overdue."""
    now = now or datetime.now(timezone.utc)
    files = inventory(root)
    report = {"verified": 0, "expired": 0, "deleted": 0}
    for item in files:
        if item["uploaded_at"] + RETENTION <= now:
            report["expired"] += 1
        elif matches(remote(client, bucket, item["key"]), item, digests(item["path"])[0]):
            report["verified"] += 1
        else:
            raise ValueError("a live recording is not verified in R2; nothing deleted")
    if delete:
        for item in files:
            item["path"].unlink()
            report["deleted"] += 1
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--source", required=True, type=Path, help="domestic recording root, e.g. /opt/eolssu/recordings")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="copy unexpired recordings to R2")
    mode.add_argument("--purge-expired", action="store_true", help="report migrated R2 objects past 30 days")
    mode.add_argument("--remove-local", action="store_true", help="report disk copies safe to delete")
    parser.add_argument("--delete", action="store_true", help="actually delete with --purge-expired or --remove-local")
    parser.add_argument("--uploads-paused", action="store_true")
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    if args.apply and (not args.uploads_paused or args.manifest is None):
        parser.error("--apply requires --uploads-paused and a private --manifest path")
    if args.delete and not (args.purge_expired or args.remove_local):
        parser.error("--delete only applies to --purge-expired or --remove-local")
    os.umask(0o077)
    import boto3
    from botocore.config import Config
    logging.getLogger("botocore").setLevel(logging.CRITICAL)
    env = read_env(args.env_file)
    client = boto3.client("s3", endpoint_url=env["RECORDING_STORAGE_ENDPOINT"],
        aws_access_key_id=env["RECORDING_STORAGE_ACCESS_KEY_ID"], aws_secret_access_key=env["RECORDING_STORAGE_SECRET_ACCESS_KEY"],
        region_name=env.get("RECORDING_STORAGE_REGION", "auto"), config=Config(signature_version="s3v4", retries={"max_attempts": 2}, connect_timeout=5, read_timeout=30))
    bucket, root = env["RECORDING_STORAGE_BUCKET"], args.source.absolute()
    if args.purge_expired:
        report = purge_expired(client, bucket, delete=args.delete)
    elif args.remove_local:
        report = remove_local(client, bucket, root, delete=args.delete)
    else:
        report, manifest = migrate(client, bucket, root, args.apply)
        if args.apply:
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
        print(f"Migration failed ({type(error).__name__}, line {frame.lineno}); review before retrying", file=sys.stderr)
        sys.exit(1)
