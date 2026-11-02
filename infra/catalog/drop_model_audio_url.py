#!/usr/bin/env python3
"""After a verified object-key backend deployment, retire the legacy audio_url column."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import zipfile

import load_model_audio as loader

DROP_SQL = "ALTER TABLE song_model_audios DROP COLUMN audio_url;"
MAPPER_PATH = "BOOT-INF/classes/mybatis/song/ModelAudioMapper.xml"
KEY = re.compile(r"model-audio/drafts/[0-9a-f]{64}/cases/(danso|sogeum|jeongak-daegeum|sanjo-daegeum)-[0-9]{3}-p[0-9]+/\1\.m4a")
HASH = re.compile(r"[0-9a-f]{64}")


def verify_release(deployed_sha, helper, root=Path("/opt/eolssu")):
    loader.require(re.fullmatch(r"[0-9a-f]{40}", deployed_sha), "Expected full deployed commit SHA")
    deployed = json.loads((root / "deployment.json").read_text(encoding="utf-8"))
    loader.require(deployed.get("status") == "deployed" and deployed.get("sha") == deployed_sha,
                   "Requested release has not completed deployment")
    runtime = helper.Deployer(root)
    jar = runtime.current_jar()
    loader.require(jar.resolve() == (root / "releases" / deployed_sha / "app.jar").resolve() and
                   Path(deployed["jar"]).resolve() == jar.resolve() and helper.digest(jar) == deployed["jar_sha256"],
                   "Selected release does not match completed deployment")
    with zipfile.ZipFile(jar) as archive:
        mapper = archive.read(MAPPER_PATH).decode("utf-8")
    loader.require("object_key" in mapper and "audio_url" not in mapper, "Running release still needs audio_url")
    runtime.healthy()  # readiness plus actual running container/JAR mount, not just selected configuration
    return {"sha": deployed_sha, "jarSha256": deployed["jar_sha256"]}


def snapshot(runner):
    def rows(sql):
        return [loader.parse_json(line) for line in runner(sql).splitlines() if line.strip()]
    history = rows("SELECT JSON_OBJECT('applied', COUNT(*)) FROM flyway_schema_history WHERE version='15' AND success=1;")
    loader.require(history == [{"applied": 1}], "V15 must have completed before removing audio_url")
    columns = rows("SELECT JSON_OBJECT('table',table_name,'name',column_name,'type',column_type,'nullable',is_nullable,"
                   "'default',column_default,'collation',collation_name) FROM information_schema.columns "
                   "WHERE table_schema=DATABASE() AND table_name IN ('songs','song_model_audios') ORDER BY table_name,ordinal_position;")
    schema = {table: [row for row in columns if row["table"] == table and (table != "song_model_audios" or row["name"] != "audio_url")]
              for table in ("songs", "song_model_audios")}
    loader.require(all(schema.values()) and all(re.fullmatch(r"[a-z0-9_]+", row["name"]) for row in columns), "Unexpected catalog schema")
    names = {row["name"] for row in schema["song_model_audios"]}
    loader.require({"object_key", "performance_object_key", "source_common_sha256", "generation_metadata"} <= names and
                   next(row for row in columns if row["name"] == "object_key")["nullable"] == "NO", "Incomplete V15 schema")
    result = {"audioUrlPresent": any(row["table"] == "song_model_audios" and row["name"] == "audio_url" for row in columns),
              "schema": schema, "tables": {}}
    for table, order in (("songs", "id"), ("song_model_audios", "song_id,score_version")):
        fields = ",".join("`" + row["name"] + "`" for row in schema[table])
        metadata = ""
        if table == "song_model_audios":
            metadata = ",'key',object_key,'performance',performance_object_key,'sourceHash',source_common_sha256"
        found = rows(f"SELECT JSON_OBJECT('hash',SHA2(CAST(JSON_ARRAY({fields}) AS CHAR),256){metadata}) FROM {table} ORDER BY {order};")
        for row in found:
            loader.require(HASH.fullmatch(row["hash"]), "Invalid row digest")
            if table == "song_model_audios":
                key, performance, source = row["key"], row["performance"], row["sourceHash"]
                loader.require(isinstance(key, str) and KEY.fullmatch(key), "Unconverted model-audio object key")
                loader.require((performance is None and source is None) or
                               (performance == key.rsplit("/", 1)[0] + "/performance.common.json" and
                                isinstance(source, str) and HASH.fullmatch(source)), "Incomplete performance binding")
        result["tables"][table] = {"count": len(found), "sha256": hashlib.sha256("\n".join(row["hash"] for row in found).encode()).hexdigest()}
    return result


def run(receipt, deployed_sha, *, apply=False, runner=loader.mysql, release_check, backup):
    report = {"schema": "model-audio-url-removal/v1", "status": "checking", "deployedSha": deployed_sha}
    with os.fdopen(os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w", encoding="utf-8") as stream:
        def save():
            report["checkedAtUtc"] = datetime.now(timezone.utc).isoformat()
            stream.seek(0); stream.truncate()
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n"); stream.flush(); os.fsync(stream.fileno())
        try:
            report["release"] = release_check()
            report["before"] = before = snapshot(runner)
            report["status"] = "validated-dry-run" if before["audioUrlPresent"] else "already-removed"
            save()
            if not apply or not before["audioUrlPresent"]:
                return report
            backup()
            loader.require(release_check() == report["release"], "Deployment changed during backup")
            loader.require(snapshot(runner) == before, "Catalog changed during backup; start a new check")
            report["status"] = "apply-outcome-unknown"; save()
            runner(DROP_SQL)
            report["status"] = "applied-readback-pending"; save()
            report["after"] = after = snapshot(runner)
            loader.require(not after["audioUrlPresent"] and after["schema"] == before["schema"] and after["tables"] == before["tables"],
                           "Removal readback differs; inspect backup and current schema before retrying")
            loader.require(release_check() == report["release"], "Deployment changed after removal")
            report["status"] = "applied"
        except BaseException as error:
            report["errorType"] = type(error).__name__
            raise
        finally:
            save()
    return report


def deployment_helper():
    path = "/opt/eolssu/bin/deploy-backend"
    source = importlib.machinery.SourceFileLoader("model_audio_deploy_guard", path)
    spec = importlib.util.spec_from_loader(source.name, source)
    module = importlib.util.module_from_spec(spec)
    source.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deployed-sha", required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    try:
        loader.require(hasattr(os, "geteuid") and os.geteuid() == 0, "Run as root on the deployed Linux host")
        helper = deployment_helper()
        helper.validate_sha(args.deployed_sha)
        with open("/run/lock/eolssu-backend-deploy.lock", "w") as lock:
            helper.acquire_lock(lock)
            result = run(args.receipt, args.deployed_sha, apply=args.apply,
                         release_check=lambda: verify_release(args.deployed_sha, helper),
                         backup=lambda: helper.run("/opt/eolssu-backup/backup.py", "send", "prechange", timeout=600))
    except Exception as error:
        print("Model audio URL removal stopped (" + type(error).__name__ + "); inspect receipt and current schema before retrying", file=sys.stderr)
        return 1
    print(json.dumps({key: result[key] for key in ("status", "deployedSha")}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
