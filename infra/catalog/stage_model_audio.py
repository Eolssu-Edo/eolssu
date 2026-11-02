#!/usr/bin/env python3
"""Validate a DRAFT107 bundle locally; --upload stages immutable private R2 objects."""
import argparse
import hashlib
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import logging
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
from urllib.parse import urlsplit


COUNTS = {"danso": 46, "sogeum": 23, "jeongak-daegeum": 28, "sanjo-daegeum": 10}
ROLES = {
    "source.common.json": "canonical_common_score", "expressions.common.json": "common_expression_companion",
    "performance.common.json": "audio_bound_performance_timeline", "manifest.json": "original_generation_manifest",
    "source-map.json": "common_source_mapping", "score.json": "internal_performed_score", "ffprobe.json": "actual_audio_probe",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def parse_json(data):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON key")
            result[key] = value
        return result
    return json.loads(data, object_pairs_hook=unique)


def flags(value, expected):
    require(isinstance(value, dict), "Missing DRAFT approval metadata")
    for key, wanted in expected.items():
        actual = value.get(key)
        require(actual is wanted if isinstance(wanted, bool) else actual == wanted,
                "DRAFT approval metadata changed")


def safe_file(root, relative):
    require(isinstance(relative, str), "Invalid bundle path")
    parts = PurePosixPath(relative)
    require(relative == parts.as_posix() and not parts.is_absolute() and
            all(p not in (".", "..") for p in parts.parts) and "\\" not in relative and ":" not in relative,
            "Unsafe bundle path")
    path = root / relative
    require(path.resolve().is_relative_to(root.resolve()), "Bundle path escapes root")
    for item in [path, *path.parents]:
        if item == root.parent:
            break
        info = item.lstat()
        require(not item.is_symlink() and not (getattr(info, "st_file_attributes", 0) &
                getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 1024)), "Symlink/reparse path rejected")
    require(path.is_file(), "Bundle member is not a regular file")
    return path


def checked_bytes(root, item):
    data = safe_file(root, item["path"]).read_bytes()
    require(len(data) == item["bytes"] and sha(data) == item["sha256"], "Bundle hash/size mismatch")
    return data


def validate_bundle(root):
    root = Path(root).absolute()
    raw = safe_file(root, "manifest.json").read_bytes()
    manifest = parse_json(raw)
    require(manifest.get("schema") == "eolssu-draft-model-audio-batch/v1" and
            manifest.get("task") == "S15P31E102-339" and manifest.get("catalogStatus") == "DRAFT",
            "Only the Task339 DRAFT batch is accepted")
    flags(manifest.get("rights"), {"status": "UNCONFIRMED", "sourceReview": "inferred",
          "serviceDatasetApproved": False, "expertPerformanceApproved": False,
          "rawCatLibraryIncluded": False, "rawJgbSourceIncluded": False})
    cases = manifest.get("cases")
    require(isinstance(cases, list) and len(cases) == 107, "Expected 107 cases")
    items, identifiers, sources, observed = [], set(), set(), dict.fromkeys(COUNTS, 0)
    for case in cases:
        cid, instrument = case.get("caseId"), case.get("instrument")
        require(isinstance(cid, str) and re.fullmatch(r"[a-z0-9-]{1,100}", cid) and cid not in identifiers,
                "Invalid or duplicate case ID")
        require(instrument in COUNTS, "Unexpected instrument")
        identifiers.add(cid)
        observed[instrument] += 1
        require(case.get("catalogStatus") == "DRAFT" and "serverSongId" in case and case["serverSongId"] is None,
                "Existing/published song targeting is forbidden")
        flags(case.get("rights"), {"status": "UNCONFIRMED", "serviceDatasetApproved": False, "rawLibraryIncluded": False})
        flags(case.get("review"), {"source": "inferred", "expertPerformanceApproved": False, "scoringApproved": False})
        roles = dict(ROLES, **{instrument + ".m4a": "rendered_model_audio", instrument + ".mid": "generated_midi",
                              instrument + ".json": "render_and_audio_validation_report"})
        files = case.get("files")
        require(isinstance(files, list) and len(files) == len(roles), "Unexpected case file count")
        bodies = {}
        for item in files:
            relative = item.get("path")
            require(isinstance(relative, str) and relative.startswith(f"cases/{cid}/"), "Case path mismatch")
            name = relative.removeprefix(f"cases/{cid}/")
            require(name in roles and name not in bodies and item.get("role") == roles[name], "Unexpected/duplicate file role")
            require(type(item.get("bytes")) is int and item["bytes"] > 0 and
                    isinstance(item.get("sha256"), str) and re.fullmatch(r"[0-9a-f]{64}", item["sha256"]), "Invalid file digest")
            bodies[name] = checked_bytes(root, item)
            items.append(dict(item))
        common = parse_json(bodies["source.common.json"])
        provenance = common["provenance"]
        require(provenance.get("review") == "inferred" and provenance.get("converter") == "jgb-playback-import/v1" and
                provenance == case.get("provenance"), "Source review metadata changed")
        score = common["score"]
        require(all(score[k] == case[k] for k in ("sourceId", "scoreVersion", "title")) and
                score["instrument"].lower().replace("_", "-") == instrument and
                score["sourceId"] == provenance["sourceId"] and score["sourceId"] not in sources, "Canonical identity mismatch")
        sources.add(score["sourceId"])
        require(case["noteCount"] == sum(e["kind"] == "NOTE" for e in common["events"]), "NOTE count mismatch")
        checksum = sha(bodies["source.common.json"])
        performance = parse_json(bodies["performance.common.json"])
        require(checksum == case["sourceCommonSha256"] == parse_json(bodies["expressions.common.json"])["sourceSha256"] ==
                performance["sourceSha256"] == parse_json(bodies["manifest.json"])["input_sha256"], "Common hash binding mismatch")
        require(sha(bodies[instrument + ".m4a"]) == case["audio"]["sha256"] == performance["audioSha256"] and
                type(case["audio"]["startOffsetMs"]) is int and case["audio"]["startOffsetMs"] == 0, "Audio binding mismatch")
    require(observed == COUNTS and manifest["counts"]["byInstrument"] == COUNTS and
            manifest["counts"]["cases"] == 107 and manifest["counts"]["payloadFiles"] == len(items), "Batch counts changed")
    allowed = {item["path"] for item in items} | {"manifest.json"}
    actual = set()
    for folder, directories, files in os.walk(root, followlinks=False):
        for name in directories:
            directory = Path(folder) / name
            info = directory.lstat()
            require(not directory.is_symlink() and not (getattr(info, "st_file_attributes", 0) & 1024), "Linked directory rejected")
        for name in files:
            relative = (Path(folder) / name).relative_to(root).as_posix()
            safe_file(root, relative)
            actual.add(relative)
    require(actual == allowed, "Unlisted or missing bundle files")
    items.sort(key=lambda item: item["path"])
    items.append({"path": "manifest.json", "role": "batch_commit_marker", "sha256": sha(raw), "bytes": len(raw)})
    return root, sha(raw), items


def create_client(env_file):
    """Credentials are read only for an explicitly requested remote upload."""
    values = {}
    for line in Path(env_file).read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            value = value.strip()
            if value[:1] in ("'", '"') and value[-1:] == value[:1]:
                value = value[1:-1]
            values[key.strip()] = value
    names = ("ENDPOINT", "BUCKET", "ACCESS_KEY_ID", "SECRET_ACCESS_KEY")
    require(all(values.get("RECORDING_STORAGE_" + name) for name in names), "Storage configuration is incomplete")
    endpoint = urlsplit(values["RECORDING_STORAGE_ENDPOINT"])
    require(endpoint.scheme == "https" and endpoint.hostname and not endpoint.username and not endpoint.password
            and not endpoint.query and not endpoint.fragment, "Storage endpoint must be HTTPS without credentials")
    import boto3
    from botocore.config import Config
    logging.getLogger("botocore").setLevel(logging.CRITICAL)
    options = dict(signature_version="s3v4", retries={"max_attempts": 2}, connect_timeout=5, read_timeout=30,
                   max_pool_connections=8)
    try:
        config = Config(**options, request_checksum_calculation="when_required", response_checksum_validation="when_required")
    except TypeError:  # The deployed 1.34 SDK predates these checksum options.
        config = Config(**options)
    client = boto3.client("s3", endpoint_url=values["RECORDING_STORAGE_ENDPOINT"],
                          aws_access_key_id=values["RECORDING_STORAGE_ACCESS_KEY_ID"],
                          aws_secret_access_key=values["RECORDING_STORAGE_SECRET_ACCESS_KEY"],
                          region_name=values.get("RECORDING_STORAGE_REGION", "auto"), config=config)
    return client, values["RECORDING_STORAGE_BUCKET"]


def conditional_puts(client):
    # R2 documents this event hook for SDKs whose PutObject model lacks the header:
    # https://developers.cloudflare.com/r2/examples/aws/custom-header/
    # Atomic If-None-Match semantics: https://developers.cloudflare.com/r2/api/s3/extensions/
    def if_absent(params, **kwargs):
        params["headers"]["If-None-Match"] = "*"
    client.meta.events.register("before-call.s3.PutObject", if_absent, unique_id="draft-model-audio-if-absent")


def error_code(error):
    return str(getattr(error, "response", {}).get("Error", {}).get("Code", ""))


def verify_remote(client, bucket, item):
    response = client.get_object(Bucket=bucket, Key=item["key"])
    body = response["Body"]
    digest, size = hashlib.sha256(), 0
    try:
        require(response["ContentLength"] == item["bytes"], "Remote object size conflicts")
        for chunk in iter(lambda: body.read(1024 * 1024), b""):
            size += len(chunk)
            require(size <= item["bytes"], "Remote object is larger than declared")
            digest.update(chunk)
    finally:
        body.close()
    require(size == item["bytes"] and digest.hexdigest() == item["sha256"], "Remote object hash conflicts")


def parallel(function, items, workers):
    pool = ThreadPoolExecutor(max_workers=workers)
    try:
        list(pool.map(function, items))
    finally:
        # Cancel queued work after a failure is observed and wait for in-flight
        # operations before writing the partial receipt.
        pool.shutdown(wait=True, cancel_futures=True)


def upload_objects(root, client, bucket, report, workers):
    conditional_puts(client)  # Unsupported hook support fails before any PUT.
    objects = report["objects"]

    def inspect(item):
        try:
            head = client.head_object(Bucket=bucket, Key=item["key"])
        except Exception as error:
            if error_code(error) not in ("404", "NoSuchKey", "NotFound"):
                raise
            item["status"] = "missing"
        else:
            require(head["ContentLength"] == item["bytes"], "Remote object size conflicts")
            verify_remote(client, bucket, item)
            item["status"] = "reused"

    report["phase"] = "remote-inventory"
    parallel(inspect, objects, workers)
    require(objects[-1]["status"] != "reused" or all(item["status"] == "reused" for item in objects),
            "Existing commit marker has missing payload; manual review required")

    def put(item):
        if item["status"] == "reused":
            return
        data = checked_bytes(root, item)  # Snapshot immutable bytes immediately before PUT.
        suffix = PurePosixPath(item["path"]).suffix
        content_type = {".json": "application/json", ".m4a": "audio/mp4", ".mid": "audio/midi"}[suffix]
        try:
            client.put_object(Bucket=bucket, Key=item["key"], Body=data, ContentType=content_type,
                              Metadata={"sha256": item["sha256"], "catalog-status": "draft"})
        except Exception as error:
            if error_code(error) not in ("412", "PreconditionFailed"):
                raise
            verify_remote(client, bucket, item)
            item["status"] = "reused-race"
        else:
            item["status"] = "uploaded-unverified"
            verify_remote(client, bucket, item)
            item["status"] = "new"

    report["phase"] = "payload-upload"
    parallel(put, objects[:-1], workers)
    report["phase"] = "commit-marker"
    put(objects[-1])  # Manifest becomes visible only after all payload hashes passed.
    report["phase"] = "complete"


def stage(bundle, receipt, *, upload=False, client=None, bucket=None, env_file=None, workers=4):
    root, manifest_sha, items = validate_bundle(bundle)
    receipt = Path(receipt).absolute()
    require(not receipt.resolve().is_relative_to(root.resolve()), "Receipt must be outside bundle")
    prefix = f"model-audio/drafts/{manifest_sha}/"
    report = {"status": "validated-local", "phase": "local-validation", "manifestSha256": manifest_sha, "prefix": prefix, "new": 0, "reused": 0,
              "objects": [dict(item, key=prefix + item["path"], status="validated-local") for item in items]}
    fd = os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        try:
            if upload:
                require(type(workers) is int and 1 <= workers <= 8, "Invalid worker count")
                if client is None:
                    client, bucket = create_client(env_file)
                require(isinstance(bucket, str) and bucket, "Storage bucket is required")
                upload_objects(root, client, bucket, report, workers)
                report["status"] = "uploaded"
        except BaseException as error:
            report.update(status="failed", errorType=type(error).__name__)
            raise
        finally:
            report["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
            report["new"] = sum(item["status"] == "new" for item in report["objects"])
            report["reused"] = sum(item["status"] in ("reused", "reused-race") for item in report["objects"])
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True, help="Clean extracted bundle, containing only its listed files")
    parser.add_argument("--env-file", type=Path, required=True, help="Read only with --upload; default does not load credentials")
    parser.add_argument("--receipt", type=Path, required=True, help="New receipt file outside the bundle")
    parser.add_argument("--upload", action="store_true", help="Conditionally create and verify private draft objects")
    parser.add_argument("--workers", type=int, choices=(4, 8), default=4)
    args = parser.parse_args()
    try:
        report = stage(args.bundle, args.receipt, upload=args.upload, env_file=args.env_file, workers=args.workers)
    except Exception as error:
        # SDK exceptions can contain request URLs or credentials: never print their text.
        print(f"Draft staging failed ({type(error).__name__}); inspect the local receipt if created", file=sys.stderr)
        return 1
    print(json.dumps({key: report[key] for key in ("status", "manifestSha256", "prefix", "new", "reused")}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
