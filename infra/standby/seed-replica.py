#!/usr/bin/env python3
"""Seed the empty edo2 production replica from its restricted SSH tunnel."""

import importlib.util
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time


ROOT = Path("/opt/eolssu-replication-579")
CONTAINER = "eolssu-replica-579-mysql-1"
DUMP_OPTIONS = ("--default-character-set=utf8mb4", "--single-transaction", "--quick",
                "--skip-extended-insert", "--complete-insert", "--hex-blob",
                "--order-by-primary", "--skip-comments", "--skip-dump-date",
                "--skip-add-locks", "--skip-disable-keys", "--skip-lock-tables",
                "--no-tablespaces")


def run(args, *, data=None, env=None, timeout=20):
    result = subprocess.run(args, input=data, capture_output=True, env=env, timeout=timeout)
    if result.returncode:
        match = re.search(rb"(?:ERROR|Error:) (\d+)", result.stderr)
        detail = f" MySQL error {match[1].decode()}" if match else ""
        raise RuntimeError(f"{args[0]} {args[1]} failed{detail}")
    return result.stdout


def sql(statement):
    return run(["docker", "exec", "-i", CONTAINER, "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'],
               data=statement.encode()).decode().strip()


def fingerprint(sql_text, tables):
    spec = importlib.util.spec_from_file_location("backup", "/opt/eolssu-backup/backup.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.data_fingerprint(sql_text, tables)


def verify_empty():
    info = json.loads(run(["docker", "inspect", CONTAINER]))[0]
    if info["Config"]["Labels"].get("com.docker.compose.project") != "eolssu-replica-579":
        raise RuntimeError("replica container ownership mismatch")
    networks = info["NetworkSettings"]["Networks"]
    if list(networks) != ["eolssu-replica-579_default"] or any(info["NetworkSettings"]["Ports"].values()):
        raise RuntimeError("replica network does not match the isolated rollout")
    if sql("SELECT @@server_id,@@gtid_mode,@@super_read_only;") != "57902\tON\t1":
        raise RuntimeError("replica identity or read-only state differs from expected")
    if sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME NOT IN "
           "('mysql','information_schema','performance_schema','sys');") != "0":
        raise RuntimeError("replica already contains data, preserve it and inspect before retrying")
    if sql("SHOW REPLICA STATUS;"):
        raise RuntimeError("replica already has a channel")


def source_dump(password):
    return run(["docker", "exec", "-e", "MYSQL_PWD", CONTAINER, "timeout", "15", "mysqldump",
                "--host=172.30.79.1", "--port=13306", "--ssl-mode=REQUIRED",
                "--user=eolssu_seed579", *DUMP_OPTIONS,
                "--source-data=2", "--set-gtid-purged=ON", "--skip-triggers", "--databases", "eolssu"],
               env={**os.environ, "MYSQL_PWD": password}, timeout=20)


def main():
    credentials = dict(line.split("=", 1) for line in (ROOT / "db-init.env").read_text().splitlines())
    if not re.fullmatch(r"[a-f0-9]{48}", credentials["SEED_PASSWORD"]):
        raise RuntimeError("invalid seed credential")
    if not re.fullmatch(r"[a-f0-9]{30}", credentials["REPL_PASSWORD"]):
        raise RuntimeError("invalid replication credential")
    verify_empty()
    started = time.monotonic()
    dump = source_dump(credentials["SEED_PASSWORD"])
    if dump.count(b"GTID_PURGED") != 1 or dump.count(b"USE `eolssu`;") != 1:
        raise RuntimeError("source snapshot scope or GTID metadata was not verified")
    verify_empty()
    sql("RESET BINARY LOGS AND GTIDS; SET GLOBAL read_only=OFF;")
    try:
        run(["docker", "exec", "-i", CONTAINER, "sh", "-c",
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'], data=dump, timeout=40)
    finally:
        sql("SET PERSIST super_read_only=ON;")
    tables = sql("SELECT CONCAT(TABLE_SCHEMA,'.',TABLE_NAME) FROM information_schema.TABLES "
                 "WHERE TABLE_SCHEMA='eolssu' AND TABLE_TYPE='BASE TABLE' ORDER BY 1;").splitlines()
    restored = run(["docker", "exec", CONTAINER, "sh", "-c",
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot "$@"', "sh",
                    *DUMP_OPTIONS, "--set-gtid-purged=OFF", "--no-create-info", "--databases", "eolssu"])
    before = fingerprint(dump.decode(), tables)
    after = fingerprint(restored.decode(), tables)
    if not tables or before != after:
        raise RuntimeError("restored data fingerprints differ from the snapshot")
    print(f"verified snapshot data hashes: {len(tables)} tables, {sum(x['rows'] for x in after.values())} rows, "
          f"{time.monotonic() - started:.2f}s", flush=True)
    sql("CHANGE REPLICATION SOURCE TO SOURCE_HOST='172.30.79.1', SOURCE_PORT=13306, "
        f"SOURCE_USER='eolssu_repl579', SOURCE_PASSWORD='{credentials['REPL_PASSWORD']}', "
        "SOURCE_AUTO_POSITION=1, SOURCE_SSL=1, SOURCE_CONNECT_RETRY=3, SOURCE_HEARTBEAT_PERIOD=2; "
        "START REPLICA;")
    print("replica started; verify both threads and GTID catch-up before declaring healthy", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.SubprocessError, ValueError, KeyError) as error:
        if isinstance(error, RuntimeError):
            print(error, file=sys.stderr)
        else:
            print(f"replica seed failed ({type(error).__name__})", file=sys.stderr)
        raise SystemExit(1)
