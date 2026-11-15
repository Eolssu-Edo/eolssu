#!/usr/bin/env python3
"""Run the authorized 2026-11-13 16:25 KST replication proxy transition once."""

import argparse
import contextlib
import datetime as dt
import hashlib
import json
import re
import select
import shlex
import subprocess
import time
import uuid
from urllib.parse import urlencode

SSH = ["ssh", "-o", "ClearAllForwardings=yes", "-o", "BatchMode=yes",
       "-o", "ConnectTimeout=5", "-o", "ServerAliveInterval=4", "-o", "ServerAliveCountMax=2"]
WHEN = dt.datetime(2026, 11, 13, 7, 25, tzinfo=dt.timezone.utc)
LATEST_START = WHEN + dt.timedelta(minutes=3)
STAMP = WHEN.strftime("%Y%m%dT%H%M%SZ")
PROXY = "eolssu-replication-proxy-579"
PRIMARY = "eolssu-runtime-mysql-1"
REPLICA = "eolssu-replica-579-mysql-1"
TUNNEL = "eolssu-replication-tunnel.service"
TIMER = "eolssu-replication-watch.timer"
WATCH = "eolssu-replication-watch.service"
NETWORK = "eolssu-runtime_default"
CONF = "/opt/eolssu-replication-579/source-stream.conf"
CONF_HASH = "bfdb8518220c3833a9ba32002bba40ee1f58fce0a06a4eb4fefd9a9476a31dba"
PORT = {"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "13306"}]}
ROTATION = {"Type": "json-file", "Config": {"max-size": "10m", "max-file": "3"}}
ORIGINAL_LOG = {"Type": "json-file", "Config": {}}
EVIDENCE = f"/var/backups/edo-docker-713/{STAMP}/replication-proxy"
BACKUP_LOCK = "/var/lib/eolssu-backup/source.lock"
DEPLOY_LOCK = "/run/lock/eolssu-backend-deploy.lock"


class Gate(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Gate(message)


def remote_cmd(host, *args):
    return [*SSH, host, shlex.join(["sudo", "-n", *args])]


def call(host, *args, data=None, timeout=12):
    result = subprocess.run(remote_cmd(host, *args), input=data, text=True, capture_output=True, timeout=timeout)
    require(result.returncode == 0, f"{host} {args[0]} {args[1] if len(args) > 1 else ''} failed ({result.returncode})")
    return result.stdout


def docker(*args, host="edo1", timeout=12):
    return call(host, "docker", *args, timeout=timeout)


def inspect(name, host="edo1"):
    [row] = json.loads(docker("inspect", "--type", "container", name, host=host))
    return row


def running(host):
    return dict(row.split() for row in docker(
        "ps", "--no-trunc", "--format", "{{.Names}} {{.ID}}", host=host
    ).splitlines())


def system_busy(host):
    rows = call(host, "systemctl", "list-units", "--type=service", "--state=running,activating",
                "--no-pager", "--no-legend")
    names = [row.split()[0] for row in rows.splitlines() if row.strip()]
    return [name for name in names if re.search(r"backup@|backup-(?:prune|restore-test)|deploy(?:ment)?[.@-]|promot|failover", name)]


@contextlib.contextmanager
def deployment_locks():
    code = 'printf "locked\\n"; cat >/dev/null'
    args = remote_cmd("edo1", "flock", "-n", DEPLOY_LOCK, "flock", "-n", BACKUP_LOCK, "sh", "-c", code)
    with subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL) as process:
        try:
            require(select.select([process.stdout], [], [], 8)[0] and process.stdout.readline() == b"locked\n",
                    "deployment/backup lock busy or unavailable")
            yield process
        finally:
            process.stdin.close()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


def sql(host, container, query, columns=False, timeout=12):
    flags = () if columns else ("--skip-column-names",)
    return call(host, "docker", "exec", "-i", container, "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch "$@"',
                "sh", *flags, data=query + ";\n", timeout=timeout).rstrip("\r\n")


def gtid(text):
    require(bool(re.fullmatch(r"[0-9A-Fa-f:,\s-]+", text)), "invalid GTID format")
    return text


def source_gtid():
    fields = sql("edo1", PRIMARY, "SELECT @@GLOBAL.server_id,@@GLOBAL.gtid_mode,@@GLOBAL.read_only,@@GLOBAL.gtid_executed").split("\t", 3)
    require(fields[:3] == ["1", "ON", "0"] and len(fields) == 4, "source MySQL identity or settings differ")
    return gtid(fields[3])


def replica_gtid():
    fields = sql("edo2", REPLICA, "SELECT @@GLOBAL.server_id,@@GLOBAL.read_only,@@GLOBAL.super_read_only,"
                 "@@GLOBAL.gtid_mode,@@GLOBAL.gtid_executed").split("\t", 4)
    require(fields[:4] == ["57902", "1", "1", "ON"] and len(fields) == 5, "replica identity or read-only settings differ")
    return gtid(fields[4])


def verify_gtid(current, prior_replica, prior_source):
    query = (f"SELECT WAIT_FOR_EXECUTED_GTID_SET('{current}',30),"
             f"GTID_SUBSET('{prior_replica}',@@GLOBAL.gtid_executed),"
             f"GTID_SUBSET('{current}','{prior_source}')")
    values = sql("edo2", REPLICA, query, timeout=38).split("\t")
    require(len(values) == 3 and values[:2] == ["0", "1"] and values[2] in ("0", "1"),
            "GTID wait or prior replica set failed")
    return {"wait": 0, "previous_replica_preserved": True, "new_source_gtid": values[2] == "0"}


def replica_check():
    for unit in (TUNNEL, TIMER):
        require(call("edo2", "systemctl", "is-active", unit).strip() == "active", f"{unit} is not active")
    call("edo2", "/usr/bin/python3", "-B", "/opt/eolssu-replication-579/check-replication.py")
    rows = sql("edo2", REPLICA, "SHOW REPLICA STATUS", columns=True).splitlines()
    require(len(rows) == 2, "expected one replication channel")
    keys, values = (row.split("\t") for row in rows)
    require(len(keys) == len(values), "incomplete replication state")
    state = dict(zip(keys, values))
    expected = {"Source_Host": "172.30.79.1", "Source_Port": "13306", "Source_Server_Id": "1",
                "Replica_IO_Running": "Yes", "Replica_SQL_Running": "Yes",
                "Last_IO_Errno": "0", "Last_SQL_Errno": "0", "Auto_Position": "1"}
    require(all(state.get(key) == value for key, value in expected.items()), "replication thread/source/error gate failed")
    lag = state.get("Seconds_Behind_Source", "")
    require(re.fullmatch(r"[0-9]+", lag) and int(lag) <= 10, "replication lag exceeds 10s or unavailable")
    return dict(expected, Seconds_Behind_Source=int(lag))


def watch_streak(output):
    pending = False
    successful = []
    for row in output.splitlines():
        if "Starting eolssu-replication-watch.service" in row:
            pending = True
        elif "Finished eolssu-replication-watch.service" in row and pending:
            successful.append(row.split()[0])
            pending = False
        elif ("Failed" in row or "REPLICATION ALERT" in row or "Main process exited" in row):
            pending = False
            successful.clear()
    return successful


def watch_twice(since, deadline):
    while time.monotonic() < deadline:
        remaining = deadline - time.monotonic()
        rows = call("edo2", "journalctl", "-u", WATCH, "--since", f"@{since}", "--until",
                    f"@{float(since) + 60:.6f}", "--output=short-iso-precise", "--no-pager",
                    timeout=min(7, remaining))
        successful = watch_streak(rows)
        if len(set(successful)) >= 2:
            return successful[-2:]
        time.sleep(min(2, max(0, deadline - time.monotonic())))
    raise Gate("fewer than two consecutive post-change watcher successes in 60s")


def ready_web():
    base = "https://k15e102.p.ssafy.io"
    for path in ("/", "/api/terms", "/api/actuator/health/readiness"):
        response = call("edo1", "curl", "-fsS", "--max-time", "4", "--write-out", "\n%{http_code}", base + path)
        body, status = response.rsplit("\n", 1)
        require(status == "200", f"public endpoint {path} HTTP {status}")
        if path.endswith("/readiness"):
            require(json.loads(body).get("status") == "UP", "API readiness is not UP")
    require(inspect(PRIMARY)["State"]["Health"]["Status"] == "healthy", "primary MySQL health failed")


def listener():
    rows = call("edo1", "ss", "-H", "-ltn", "sport", "=", ":13306").splitlines()
    require(rows and {row.split()[3] for row in rows} == {"127.0.0.1:13306"}, "private listener missing or different")


def identity(c):
    return {"id": c["Id"], "mounts": c["Mounts"], "networks": {
        name: (n["NetworkID"], n["IPAddress"]) for name, n in c["NetworkSettings"]["Networks"].items()
    }}


def save(name, content):
    call("edo1", "tee", f"{EVIDENCE}/{name}", data=json.dumps(content, indent=2) + "\n")


def event(label, **details):
    record = {"utc": dt.datetime.now(dt.timezone.utc).isoformat(), "stage": label, **details}
    call("edo1", "tee", "-a", f"{EVIDENCE}/events.jsonl", data=json.dumps(record) + "\n")
    print(json.dumps(record), flush=True)


def claim():
    call("edo1", "mkdir", "-p", f"/var/backups/edo-docker-713/{STAMP}")
    call("edo1", "mkdir", "-m", "0700", EVIDENCE)


def config_hash(container=None):
    if container is None:
        return call("edo1", "sha256sum", CONF).split()[0]
    return hashlib.sha256(docker("exec", container, "cat", "/etc/nginx/nginx.conf").encode()).hexdigest()


def compare(old, new, logging):
    expected = dict(old["HostConfig"], LogConfig=logging)
    actual = dict(new["HostConfig"])
    allowed = []
    if expected.get("OomKillDisable") is None and actual.get("OomKillDisable") is False:
        require(docker("info", "--format", "{{.CgroupVersion}}").strip() == "2", "OOM field differs outside cgroup v2")
        actual["OomKillDisable"] = None
        allowed.append("OomKillDisable: null -> false (cgroup v2)")
    old_config = dict(old["Config"])
    new_config = dict(new["Config"])
    if old_config.get("Hostname") == old["Id"][:12] and new_config.get("Hostname") == new["Id"][:12]:
        old_config.pop("Hostname")
        new_config.pop("Hostname")
    diffs = []
    for prefix, left, right in (("HostConfig", expected, actual), ("Config", old_config, new_config)):
        diffs.extend(f"{prefix}.{key}" for key in sorted(left.keys() | right.keys())
                     if left.get(key) != right.get(key))
    if old["Mounts"] != new["Mounts"]:
        diffs.append("Mounts")
    if old["Image"] != new["Image"]:
        diffs.append("Image")
    return {"unexpected": diffs, "allowed": allowed}


def baseline():
    old = inspect(PROXY)
    host = old["HostConfig"]
    require(old["State"]["Running"] and host["LogConfig"] == ORIGINAL_LOG, "original proxy state or log settings differ")
    require(host["PortBindings"] == PORT and host["NetworkMode"] == NETWORK, "original proxy port or network differs")
    require(old["Config"]["StopSignal"] == "SIGQUIT" and not host["AutoRemove"], "original proxy lifecycle differs")
    require(config_hash() == config_hash(old["Id"]) == CONF_HASH, "proxy configuration hash differs")
    listener()
    ready_web()
    replica_check()
    before_source, before_replica = source_gtid(), replica_gtid()
    return old, {
        "source": identity(inspect(PRIMARY)), "replica": identity(inspect(REPLICA, "edo2")),
        "others": {n: i for n, i in running("edo1").items() if n != PROXY},
        "daemon": call("edo1", "systemctl", "show", "docker.service", "-p", "MainPID,ActiveEnterTimestampMonotonic"),
        "source_gtid": before_source, "replica_gtid": before_replica,
    }


def create(old, name):
    config = old["Config"]
    labels = config["Labels"]
    require(all(labels.get(key) for key in ("com.eolssu.owner", "maintainer")), "existing proxy labels unavailable")
    require(config["AttachStdout"] is False and config["AttachStderr"] is False,
            "original proxy stdout/stderr attachment differs")
    request = dict(config, Image=old["Image"], AttachStdout=False, AttachStderr=False,
                   HostConfig=dict(old["HostConfig"], LogConfig=ROTATION))
    if config["Hostname"] == old["Id"][:12]:
        request["Hostname"] = ""
    version = docker("version", "--format", "{{.Server.APIVersion}}").strip()
    require(re.fullmatch(r"[0-9]+\.[0-9]+", version), "Docker Engine API version unavailable")
    url = f"http://localhost/v{version}/containers/create?{urlencode({'name': name})}"
    result = json.loads(call("edo1", "curl", "--disable", "--noproxy", "*", "--unix-socket", "/var/run/docker.sock",
                             "--silent", "--show-error", "--fail", "--max-time", "10", "--request", "POST",
                             "--header", "Content-Type: application/json", "--data-binary", "@-", url,
                             data=json.dumps(request)))
    ident = result.get("Id", "")
    require(re.fullmatch(r"[0-9a-f]{64}", ident), f"Docker Engine did not return a container ID for {name}")
    return ident


def candidate_ids(name):
    return docker("ps", "--all", "--quiet", "--no-trunc", "--filter", f"name=^/{name}$").splitlines()


def require_created(candidate, name):
    state = candidate["State"]
    require(candidate["Name"] == f"/{name}" and state["Status"] == "created" and not state["Running"]
            and state["Pid"] == 0 and state["StartedAt"] == "0001-01-01T00:00:00Z",
            f"candidate {name} is not in its initial created state")


def remove_created_candidate(name, ident):
    # The API may have created the container even if its response was lost.
    pending = candidate_ids(name)
    if pending:
        require(len(pending) == 1 and ident in (None, pending[0]), f"candidate identity changed for {name}")
        require_created(inspect(pending[0]), name)
        docker("rm", pending[0])
    require(not candidate_ids(name), f"candidate cleanup failed for {name}")


def check_candidate():
    name = f"{PROXY}-check-713-{uuid.uuid4().hex[:12]}"
    ident = None
    with deployment_locks() as locks:
        for host in ("edo1", "edo2"):
            require(not system_busy(host), f"{host} backup/deploy/failover job is active")
        old, _ = baseline()
        require(not candidate_ids(name) and locks.poll() is None, "candidate name or maintenance locks changed")
        try:
            ident = create(old, name)
            candidate = inspect(ident)
            require_created(candidate, name)
            report = compare(old, candidate, ROTATION)
            report.update(candidate=ident, candidate_state=candidate["State"]["Status"],
                          started_at=candidate["State"]["StartedAt"],
                          attachments={key: {"original": old["Config"][key], "candidate": candidate["Config"][key]}
                                       for key in ("AttachStdout", "AttachStderr")},
                          original_logging=old["HostConfig"]["LogConfig"],
                          candidate_logging=candidate["HostConfig"]["LogConfig"])
            print(json.dumps(report), flush=True)
            require(not report["unexpected"], "candidate differs from approved settings")
        finally:
            remove_created_candidate(name, ident)
        current = inspect(PROXY)
        require(current["Id"] == old["Id"] and current["State"]["Running"]
                and current["State"]["StartedAt"] == old["State"]["StartedAt"], "production proxy changed")
        require(config_hash() == config_hash(current["Id"]) == CONF_HASH, "proxy configuration hash changed")
        listener()
        replica = replica_check()
        require(locks.poll() is None, "maintenance locks were lost")
        print(json.dumps({"candidate_removed": True, "original": current["Id"],
                          "original_started_at": current["State"]["StartedAt"],
                          "listener": "127.0.0.1:13306", "replication": replica}), flush=True)
    return 0


def cutover_stop(ident):
    docker("stop", "--signal", "SIGTERM", "--timeout", "10", ident, timeout=15)
    state = inspect(ident)["State"]
    require(state["Status"] == "exited" and state["ExitCode"] == 0, "proxy did not stop with exit code 0")


def verify_live(old, expected_id, expected_logging, before):
    current = inspect(PROXY)
    require(current["Id"] == expected_id and current["State"]["Running"], "running proxy ID or state differs")
    require(current["HostConfig"]["LogConfig"] == expected_logging and current["NetworkSettings"]["Ports"] == PORT,
            "running port or log rotation differs")
    networks = current["NetworkSettings"]["Networks"]
    require(set(networks) == {NETWORK} and networks[NETWORK]["NetworkID"] ==
            old["NetworkSettings"]["Networks"][NETWORK]["NetworkID"], "running proxy network differs")
    begin = time.monotonic()
    while True:
        try:
            listener()
            break
        except Gate:
            if time.monotonic() - begin >= 9:
                raise
            time.sleep(0.3)
    deadline = time.monotonic() + 60
    since = call("edo2", "date", "+%s.%6N").strip()
    require(re.fullmatch(r"[0-9]+\.[0-9]+", since), "invalid watch start time")
    require(config_hash() == config_hash(current["Id"]) == CONF_HASH, "running proxy config hash differs")
    require(identity(inspect(PRIMARY)) == before["source"] and identity(inspect(REPLICA, "edo2")) == before["replica"],
            "MySQL IDs, mounts or addresses changed")
    require({n: i for n, i in running("edo1").items() if n != PROXY} == before["others"],
            "unrelated edo1 containers changed")
    require(call("edo1", "systemctl", "show", "docker.service", "-p", "MainPID,ActiveEnterTimestampMonotonic") == before["daemon"],
            "Docker daemon changed")
    ready_web()
    finished = watch_twice(since, deadline)
    replica = replica_check()
    gtid_result = verify_gtid(source_gtid(), before["replica_gtid"], before["source_gtid"])
    return {"proxy": expected_id, "listener": "127.0.0.1:13306", "logging": expected_logging,
            "watch_successes": finished, "replication": replica, "gtid": gtid_result}


def rollback(old, candidate):
    orig = inspect(old)
    new = inspect(candidate)
    require(orig["Name"] in (f"/{PROXY}", f"/{PROXY}-before-713-{STAMP}"), "rollback original identity/name differs")
    require(new["Name"] in (f"/{PROXY}", f"/{PROXY}-candidate-713-{STAMP}"), "rollback candidate identity/name differs")
    if new["State"]["Running"]:
        docker("stop", "--signal", "SIGTERM", "--timeout", "10", candidate, timeout=15)
        require(inspect(candidate)["State"]["Status"] == "exited", "candidate still holds the proxy port")
    if new["Name"] == f"/{PROXY}":
        docker("rename", candidate, f"{PROXY}-failed-713-{STAMP}")
    if orig["Name"] != f"/{PROXY}":
        docker("rename", old, PROXY)
    if not orig["State"]["Running"]:
        docker("start", old)
    require(inspect(PROXY)["Id"] == old, "rollback did not restore original ID")


def run():
    require(WHEN <= dt.datetime.now(dt.timezone.utc) <= LATEST_START,
            "outside the authorized 16:25–16:28 KST start window")
    claim()
    event("claimed")
    switched = False
    candidate = None
    candidate_name = f"{PROXY}-candidate-713-{STAMP}"
    create_attempted = False
    try:
        with deployment_locks() as locks:
            try:
                for host in ("edo1", "edo2"):
                    require(not system_busy(host), f"{host} backup/deploy/failover job is active")
                old, before = baseline()
                verify_gtid(before["source_gtid"], before["replica_gtid"], before["source_gtid"])
                require(not candidate_ids(candidate_name), "candidate name already exists")
                create_attempted = True
                candidate = create(old, candidate_name)
                next_proxy = inspect(candidate)
                report = compare(old, next_proxy, ROTATION)
                save("candidate.diff.json", report)
                require_created(next_proxy, candidate_name)
                require(not report["unexpected"], "candidate differs from approved settings")
                require(inspect(PROXY)["Id"] == old["Id"] and locks.poll() is None, "original or maintenance locks changed")
                require(config_hash() == CONF_HASH, "proxy config changed before cutover")
                replica_check()
                event("cutover_ready", original=old["Id"], candidate=candidate,
                      original_logging=ORIGINAL_LOG, allowed_differences=report["allowed"])
                require(dt.datetime.now(dt.timezone.utc) <= LATEST_START,
                        "change window ended before cutover")
                require(locks.poll() is None, "maintenance locks were lost before cutover")
                switched = True
                cutover_stop(old["Id"])
                docker("rename", old["Id"], f"{PROXY}-before-713-{STAMP}")
                docker("rename", candidate, PROXY)
                docker("start", candidate)
                result = verify_live(old, candidate, ROTATION, before)
                require(locks.poll() is None, "maintenance locks were lost")
                event("applied", **result)
            except (Exception, KeyboardInterrupt) as error:
                reason = str(error) if isinstance(error, Gate) else type(error).__name__
                if switched:
                    print(f"ROLLBACK_REQUIRED {reason}", flush=True)
                    try:
                        rollback(old["Id"], candidate)
                    except Exception as rollback_error:
                        print("ROLLBACK_INCOMPLETE", flush=True)
                        raise rollback_error from error
                    try:
                        recovery = verify_live(old, old["Id"], ORIGINAL_LOG, before)
                    except Exception:
                        event("original_restored_verification_failed", reason=reason, original=old["Id"])
                        return 3
                    event("rolled_back", reason=reason, **recovery)
                    return 2
                if create_attempted:
                    try:
                        remove_created_candidate(candidate_name, candidate)
                    except Exception:
                        event("candidate_cleanup_failed", candidate_name=candidate_name, candidate=candidate)
                        return 4
                event("skipped", reason=reason)
                return 0
    except Gate as error:
        if switched:
            raise
        event("skipped", reason=str(error))
        return 0
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="read-only production baseline check")
    mode.add_argument("--check-candidate", action="store_true", help="create, compare and remove a stopped candidate")
    args = parser.parse_args()
    try:
        if args.check:
            old, state = baseline()
            require(not system_busy("edo1") and not system_busy("edo2"), "backup or deployment active")
            result = verify_gtid(state["source_gtid"], state["replica_gtid"], state["source_gtid"])
            print(json.dumps({"proxy": old["Id"], "read_only_preflight": "passed", "gtid": result}))
            return 0
        if args.check_candidate:
            return check_candidate()
        return run()
    except (Exception, KeyboardInterrupt) as error:
        print(f"STOP {error if isinstance(error, Gate) else type(error).__name__}", flush=True)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
