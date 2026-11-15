#!/usr/bin/env python3
"""Report production replica health without changing source or replica state."""

import subprocess
import sys


CONTAINER = "eolssu-replica-579-mysql-1"
EXPECTED_SOURCE = ("172.30.79.1", "13306", "1")
LAG_LIMIT_SECONDS = 10


def evaluate(rows):
    if len(rows) != 2:
        return "expected one replication channel"
    fields, values = (line.split("\t") for line in rows)
    if len(fields) != len(values):
        return "replication status output was incomplete"
    state = dict(zip(fields, values))
    endpoint = tuple(state.get(field) for field in ("Source_Host", "Source_Port", "Source_Server_Id"))
    if endpoint != EXPECTED_SOURCE or state.get("Auto_Position") != "1":
        return "source identity or GTID mode differs from the rollout"
    for thread, error in (("Replica_IO_Running", "Last_IO_Errno"), ("Replica_SQL_Running", "Last_SQL_Errno")):
        if state.get(thread) != "Yes" or state.get(error) != "0":
            return f"{thread}={state.get(thread, 'unknown')} {error}={state.get(error, 'unknown')}"
    lag = state.get("Seconds_Behind_Source", "")
    if not lag.isascii() or not lag.isdecimal() or int(lag) > LAG_LIMIT_SECONDS:
        return f"replication lag is unknown or exceeds {LAG_LIMIT_SECONDS}s"
    return None


def main():
    try:
        result = subprocess.run(
            ["docker", "exec", CONTAINER, "sh", "-c",
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch -e "SHOW REPLICA STATUS"'],
            capture_output=True, text=True, timeout=6, check=True,
        )
        problem = evaluate(result.stdout.splitlines())
    except (OSError, subprocess.SubprocessError):
        problem = "could not read replica status"
    if problem:
        print(f"REPLICATION ALERT: {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
