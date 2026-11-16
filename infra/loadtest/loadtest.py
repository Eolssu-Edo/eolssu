#!/usr/bin/env python3
"""Demo-scale concurrency rehearsal for the backend API (S15P31E102-940).

Every scenario starts its virtual users together behind a barrier, so N users
hit the same step at the same moment, like an audience following a QR code.
Only the standard library is used. Run it against an isolated local backend
(see infra/loadtest/README.md); do not point it at production without an
approved time window.

Scenarios:
  signup    N users run the full email sign-up at once: send code, read it from
            the fake SMTP log, confirm, sign up. Writes the accounts to --accounts.
  login     N accounts sign in at once.
  browse    N signed-in users loop over the catalog/growth reads for --duration.
  practice  N signed-in users create a session, upload a recording at once and
            poll until the analysis ends.

Example:
  python3 infra/loadtest/loadtest.py --base http://127.0.0.1:18940 signup \
      --users 10 --mail-log mail.jsonl --accounts accounts.json --tag r1
"""
from __future__ import annotations

import argparse
import json
import re
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter, defaultdict
from pathlib import Path
from typing import Callable

PASSWORD = "Passw0rd!load"
CODE_PATTERN = re.compile(r"(?<!\d)(\d{6})(?!\d)")
TERMINAL = {"DONE", "FAILED"}


# --------------------------------------------------------------------------- results


class Recorder:
    """Thread-safe list of (step, status, elapsed_ms, error) samples."""

    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.samples: list[tuple[str, int, float, str]] = []

    def add(self, step: str, status: int, elapsed_ms: float, error: str = "") -> None:
        with self.lock:
            self.samples.append((step, status, elapsed_ms, error))


def percentile(values: list[float], fraction: float) -> float:
    """Nearest-rank percentile; 0 for an empty list."""
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, -(-int(fraction * 1000) * len(ordered) // 1000))
    return ordered[min(rank, len(ordered)) - 1]


def summarize(samples: list[tuple[str, int, float, str]], ok: Callable[[str, int], bool]) -> list[dict]:
    by_step: dict[str, list[tuple[int, float, str]]] = defaultdict(list)
    order: list[str] = []
    for step, status, elapsed, error in samples:
        if step not in by_step:
            order.append(step)
        by_step[step].append((status, elapsed, error))
    rows = []
    for step in order:
        items = by_step[step]
        times = [elapsed for _, elapsed, _ in items]
        statuses = Counter(str(status) if status else "ERR" for status, _, _ in items)
        rows.append({
            "step": step,
            "count": len(items),
            "ok": sum(1 for status, _, _ in items if ok(step, status)),
            "statuses": dict(sorted(statuses.items())),
            "p50_ms": round(percentile(times, 0.50)),
            "p95_ms": round(percentile(times, 0.95)),
            "max_ms": round(max(times) if times else 0),
            "errors": sorted({error for _, _, error in items if error})[:5],
        })
    return rows


def print_table(rows: list[dict]) -> None:
    print(f"{'step':<22}{'n':>4}{'ok':>5}{'p50ms':>8}{'p95ms':>8}{'maxms':>8}  statuses")
    for row in rows:
        print(f"{row['step']:<22}{row['count']:>4}{row['ok']:>5}{row['p50_ms']:>8}{row['p95_ms']:>8}{row['max_ms']:>8}  {row['statuses']}")
        for error in row["errors"]:
            print(f"{'':<22}  ! {error}")


# --------------------------------------------------------------------------- HTTP


def parse_cookies(set_cookie_headers: list[str]) -> dict[str, str]:
    cookies = {}
    for header in set_cookie_headers:
        name, _, rest = header.partition("=")
        value = rest.split(";", 1)[0]
        if name.strip():
            cookies[name.strip()] = value
    return cookies


def multipart(field: str, filename: str, content: bytes, content_type: str) -> tuple[bytes, str]:
    boundary = "loadtest" + uuid.uuid4().hex
    head = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode()
    tail = f"\r\n--{boundary}--\r\n".encode()
    return head + content + tail, f"multipart/form-data; boundary={boundary}"


class Client:
    """One virtual user: keeps its auth cookies and records every call."""

    def __init__(self, base: str, recorder: Recorder, timeout: float = 70.0, *, opener=None) -> None:
        self.base = base.rstrip("/")
        self.recorder = recorder
        self.timeout = timeout
        self.cookies: dict[str, str] = {}
        self.open = opener.open if opener is not None else urllib.request.urlopen

    def call(self, step: str, method: str, path: str, body: object = None, *,
             raw: bytes | None = None, content_type: str | None = None,
             headers: dict[str, str] | None = None) -> tuple[int, dict]:
        data = raw
        all_headers = dict(headers or {})
        if body is not None:
            data = json.dumps(body).encode()
            all_headers["Content-Type"] = "application/json"
        elif content_type:
            all_headers["Content-Type"] = content_type
        if self.cookies:
            all_headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=all_headers)
        started = time.perf_counter()
        status, payload, error = 0, {}, ""
        try:
            with self.open(request, timeout=self.timeout) as response:
                status = response.status
                self.cookies.update(parse_cookies(response.headers.get_all("Set-Cookie") or []))
                payload = json.loads(response.read() or b"{}")
        except urllib.error.HTTPError as failure:
            status = failure.code
            try:
                payload = json.loads(failure.read() or b"{}")
            except ValueError:
                payload = {}
            error = f"{status} {payload.get('result', {}).get('errorCode', '') if isinstance(payload.get('result'), dict) else ''}".strip()
        except Exception as failure:  # noqa: BLE001 - timeouts and resets are results here
            error = f"HTTP {status} incomplete: {type(failure).__name__}: {failure}"[:160]
            status, payload = 0, {}
        self.recorder.add(step, status, (time.perf_counter() - started) * 1000, error)
        return status, payload


# --------------------------------------------------------------------------- helpers


def find_code(mail_log: Path, email: str, after: float, timeout: float = 15.0) -> str | None:
    """Return the newest 6-digit code mailed to email at or after `after` (epoch seconds)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if mail_log.exists():
            for line in reversed(mail_log.read_text(encoding="utf-8").splitlines()):
                entry = json.loads(line)
                if email in entry.get("to", []) and entry.get("at", 0) >= after - 1:
                    match = CODE_PATTERN.search(entry.get("body", ""))
                    if match:
                        return match.group(1)
        time.sleep(0.1)
    return None


def run_users(count: int, work: Callable[[int, threading.Barrier], None]) -> float:
    barrier = threading.Barrier(count)
    threads = [threading.Thread(target=work, args=(index, barrier), daemon=True) for index in range(count)]
    started = time.perf_counter()
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    return time.perf_counter() - started


def load_accounts(path: Path, count: int) -> list[dict]:
    accounts = json.loads(path.read_text(encoding="utf-8"))
    if len(accounts) < count:
        raise SystemExit(f"{path} has {len(accounts)} accounts, {count} needed")
    return accounts[:count]


def sign_in_all(base: str, accounts: list[dict], recorder: Recorder) -> list[Client]:
    """Sign in one by one (not measured as load) and return logged-in clients."""
    clients = []
    for account in accounts:
        client = Client(base, Recorder())
        status, _ = client.call("setup-sign-in", "POST", "/api/auth/sign-in", {"email": account["email"], "password": account["password"]})
        if status != 200:
            raise SystemExit(f"setup sign-in failed for {account['email']}: {status}")
        client.recorder = recorder
        clients.append(client)
    return clients


# --------------------------------------------------------------------------- scenarios


def scenario_signup(args: argparse.Namespace, recorder: Recorder) -> dict:
    terms_client = Client(args.base, Recorder())
    status, payload = terms_client.call("terms", "GET", "/api/terms")
    if status != 200:
        raise SystemExit(f"GET /api/terms failed: {status}")
    agreements = [{"termId": item["termId"], "version": item["version"], "agreed": True} for item in payload["result"]["items"]]
    accounts: list[dict | None] = [None] * args.users

    def work(index: int, barrier: threading.Barrier) -> None:
        email = f"lt-{args.tag}-{index:03d}@example.test"
        client = Client(args.base, recorder)
        barrier.wait()
        if args.spread > 0:  # user i starts at i * spread / N seconds: an evenly paced arrival
            time.sleep(index * args.spread / args.users)
        sent_at = time.time()
        status, payload = client.call("1-send-code", "POST", "/api/auth/email-verifications", {"email": email, "purpose": "SIGN_UP"})
        if status != 202:
            return
        code = find_code(args.mail_log, email, sent_at)
        if code is None:
            recorder.add("2-read-code", 0, 0, "code not found in mail log")
            return
        verification_id = payload["result"]["verificationId"]
        status, payload = client.call("3-confirm-code", "POST", f"/api/auth/email-verifications/{verification_id}/confirm", {"code": code})
        if status != 200:
            return
        body = {
            "email": email, "password": PASSWORD, "nickname": f"l{args.tag[:5]}{index:03d}"[:10],
            "verificationToken": payload["result"]["verificationToken"], "agreements": agreements,
        }
        status, _ = client.call("4-sign-up", "POST", "/api/auth/sign-up", body)
        if status == 201:
            accounts[index] = {"email": email, "password": PASSWORD}

    wall = run_users(args.users, work)
    created = [account for account in accounts if account]
    if args.accounts:
        existing = json.loads(args.accounts.read_text(encoding="utf-8")) if args.accounts.exists() else []
        args.accounts.write_text(json.dumps(existing + created, indent=1), encoding="utf-8")
    return {"wall_s": round(wall, 2), "created": len(created)}


def scenario_login(args: argparse.Namespace, recorder: Recorder) -> dict:
    accounts = load_accounts(args.accounts, args.users)

    def work(index: int, barrier: threading.Barrier) -> None:
        client = Client(args.base, recorder)
        barrier.wait()
        client.call("sign-in", "POST", "/api/auth/sign-in", {"email": accounts[index]["email"], "password": accounts[index]["password"]})

    return {"wall_s": round(run_users(args.users, work), 2)}


def scenario_browse(args: argparse.Namespace, recorder: Recorder) -> dict:
    clients = sign_in_all(args.base, load_accounts(args.accounts, args.users), recorder)
    probe = Client(args.base, Recorder())
    _, songs = probe.call("songs", "GET", "/api/songs")
    song_ids = [item["songId"] for item in songs["result"]["items"]] or ["1"]
    today = time.strftime("%Y-%m-%d")
    month_ago = time.strftime("%Y-%m-%d", time.localtime(time.time() - 29 * 86400))

    def work(index: int, barrier: threading.Barrier) -> None:
        client = clients[index]
        barrier.wait()
        deadline = time.time() + args.duration
        turn = 0
        while time.time() < deadline:
            song = song_ids[(index + turn) % len(song_ids)]
            client.call("GET songs", "GET", "/api/songs")
            client.call("GET song", "GET", f"/api/songs/{song}")
            client.call("GET score", "GET", f"/api/songs/{song}/score")
            client.call("GET growth/summary", "GET", f"/api/users/me/growth/summary?from={month_ago}&to={today}")
            client.call("GET records/recent", "GET", f"/api/users/me/practice-records/recent?weekOf={today}")
            turn += 1
            time.sleep(args.think)

    return {"wall_s": round(run_users(args.users, work), 2)}


def scenario_practice(args: argparse.Namespace, recorder: Recorder) -> dict:
    clients = sign_in_all(args.base, load_accounts(args.accounts, args.users), recorder)
    audio = args.audio.read_bytes()
    probe = Client(args.base, Recorder())
    _, song = probe.call("song", "GET", f"/api/songs/{args.song}")
    song = song["result"]
    outcome: dict[int, dict] = {}

    def work(index: int, barrier: threading.Barrier) -> None:
        client = clients[index]
        body = {"songId": str(args.song), "bpm": song["defaultBpm"], "expectedScoreVersion": song["scoreVersion"]}
        status, created = client.call("1-create-session", "POST", "/api/practice-sessions", body,
                                      headers={"Idempotency-Key": f"lt-{args.tag}-{index}-{uuid.uuid4().hex[:8]}"})
        payload, content_type = multipart("audio", "take.m4a", audio, "audio/mp4")
        barrier.wait()  # every user waits here, even after a failed create, so nobody blocks forever
        if status != 201:
            outcome[index] = {"status": f"create {status}"}
            return
        session = created["result"]["sessionId"]
        uploaded = time.perf_counter()
        status, _ = client.call("2-upload", "POST", f"/api/practice-sessions/{session}/recording", raw=payload, content_type=content_type)
        if status != 202:
            outcome[index] = {"status": f"upload {status}"}
            return
        final = "?"
        while time.perf_counter() - uploaded < args.poll_limit:
            time.sleep(args.poll)
            status, detail = client.call("3-poll-status", "GET", f"/api/practice-sessions/{session}")
            result = detail.get("result", {}) if status == 200 else {}
            if result.get("status") in TERMINAL:
                final = result["status"] + (f"/{result['failReason']}" if result.get("failReason") else "")
                break
        waited = (time.perf_counter() - uploaded) * 1000
        recorder.add("4-upload-to-result", 200 if final == "DONE" else 0, waited, "" if final == "DONE" else final)
        outcome[index] = {"status": final, "ms": round(waited)}

    wall = run_users(args.users, work)
    return {"wall_s": round(wall, 2), "results": dict(Counter(item["status"] for item in outcome.values()))}


SCENARIOS = {
    "signup": scenario_signup,
    "login": scenario_login,
    "browse": scenario_browse,
    "practice": scenario_practice,
}
OK_STATUS = {
    "1-send-code": 202, "3-confirm-code": 200, "4-sign-up": 201, "sign-in": 200,
    "1-create-session": 201, "2-upload": 202, "4-upload-to-result": 200,
}


def ok(step: str, status: int) -> bool:
    return status == OK_STATUS.get(step, 200)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", required=True, help="backend base URL, e.g. http://127.0.0.1:18940")
    parser.add_argument("--json-out", type=Path, help="append the summary as one JSON line")
    sub = parser.add_subparsers(dest="scenario", required=True)
    signup = sub.add_parser("signup")
    signup.add_argument("--users", type=int, required=True)
    signup.add_argument("--mail-log", type=Path, required=True)
    signup.add_argument("--accounts", type=Path)
    signup.add_argument("--tag", required=True, help="unique per run; part of every email")
    signup.add_argument("--spread", type=float, default=0.0, help="spread the starts evenly over this many seconds")
    login = sub.add_parser("login")
    login.add_argument("--users", type=int, required=True)
    login.add_argument("--accounts", type=Path, required=True)
    browse = sub.add_parser("browse")
    browse.add_argument("--users", type=int, required=True)
    browse.add_argument("--accounts", type=Path, required=True)
    browse.add_argument("--duration", type=float, default=30.0)
    browse.add_argument("--think", type=float, default=1.0, help="seconds between a user's loops")
    practice = sub.add_parser("practice")
    practice.add_argument("--users", type=int, required=True)
    practice.add_argument("--accounts", type=Path, required=True)
    practice.add_argument("--audio", type=Path, required=True, help="MP4/AAC recording to upload")
    practice.add_argument("--song", default="1")
    practice.add_argument("--tag", default="p")
    practice.add_argument("--poll", type=float, default=2.0, help="seconds between status polls (server hint 2s)")
    practice.add_argument("--poll-limit", type=float, default=90.0)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    recorder = Recorder()
    extra = SCENARIOS[args.scenario](args, recorder)
    rows = summarize(recorder.samples, ok)
    print(f"scenario={args.scenario} users={args.users} {json.dumps(extra, ensure_ascii=False)}")
    print_table(rows)
    if args.json_out:
        record = {"scenario": args.scenario, "users": args.users, "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"), **extra, "steps": rows}
        with args.json_out.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
