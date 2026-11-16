"""Unit tests for the demo load rehearsal helpers (no backend, no network beyond localhost)."""
from __future__ import annotations

import asyncio
import json
import smtplib
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import fake_ai  # noqa: E402
import fake_smtp  # noqa: E402
import loadtest  # noqa: E402


class PercentileTests(unittest.TestCase):
    def test_nearest_rank(self) -> None:
        values = [float(v) for v in range(1, 21)]
        self.assertEqual(loadtest.percentile(values, 0.50), 10.0)
        self.assertEqual(loadtest.percentile(values, 0.95), 19.0)
        self.assertEqual(loadtest.percentile([7.0], 0.95), 7.0)
        self.assertEqual(loadtest.percentile([], 0.95), 0.0)


class SummaryTests(unittest.TestCase):
    def test_counts_expected_status_per_step(self) -> None:
        samples = [
            ("1-send-code", 202, 10.0, ""),
            ("1-send-code", 503, 5000.0, "503 SERVICE_UNAVAILABLE"),
            ("sign-in", 200, 80.0, ""),
            ("2-read-code", 0, 0.0, "code not found in mail log"),
        ]
        rows = {row["step"]: row for row in loadtest.summarize(samples, loadtest.ok)}
        self.assertEqual(rows["1-send-code"]["ok"], 1)
        self.assertEqual(rows["1-send-code"]["statuses"], {"202": 1, "503": 1})
        self.assertEqual(rows["1-send-code"]["errors"], ["503 SERVICE_UNAVAILABLE"])
        self.assertEqual(rows["sign-in"]["ok"], 1)
        self.assertEqual(rows["2-read-code"]["statuses"], {"ERR": 1})
        self.assertEqual([row["step"] for row in loadtest.summarize(samples, loadtest.ok)],
                         ["1-send-code", "sign-in", "2-read-code"])


class HttpHelperTests(unittest.TestCase):
    def test_parse_cookies_keeps_name_and_value_only(self) -> None:
        cookies = loadtest.parse_cookies([
            "access_token=abc.def; Path=/api; HttpOnly; SameSite=Lax",
            "refresh_token=xyz; Path=/api/auth; Max-Age=1209600",
        ])
        self.assertEqual(cookies, {"access_token": "abc.def", "refresh_token": "xyz"})

    def test_multipart_wraps_one_file_part(self) -> None:
        body, content_type = loadtest.multipart("audio", "take.m4a", b"\x00\x01", "audio/mp4")
        boundary = content_type.split("boundary=", 1)[1]
        self.assertTrue(content_type.startswith("multipart/form-data; boundary="))
        self.assertTrue(body.startswith(f"--{boundary}\r\n".encode()))
        self.assertIn(b'name="audio"; filename="take.m4a"', body)
        self.assertIn(b"\r\n\r\n\x00\x01\r\n", body)
        self.assertTrue(body.endswith(f"--{boundary}--\r\n".encode()))


class FindCodeTests(unittest.TestCase):
    def test_reads_newest_code_for_recipient(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            log = Path(folder) / "mail.jsonl"
            now = time.time()
            entries = [
                {"at": now, "to": ["a@example.test"], "body": "얼쑤 회원가입 인증번호는 111111 입니다."},
                {"at": now, "to": ["b@example.test"], "body": "얼쑤 회원가입 인증번호는 222222 입니다."},
                {"at": now + 1, "to": ["a@example.test"], "body": "얼쑤 회원가입 인증번호는 333333 입니다.\n3분 안에"},
            ]
            log.write_text("".join(json.dumps(e, ensure_ascii=False) + "\n" for e in entries), encoding="utf-8")
            self.assertEqual(loadtest.find_code(log, "a@example.test", now, timeout=1), "333333")
            self.assertEqual(loadtest.find_code(log, "b@example.test", now, timeout=1), "222222")
            self.assertIsNone(loadtest.find_code(log, "c@example.test", now, timeout=0.2))
            self.assertIsNone(loadtest.find_code(log, "a@example.test", now + 60, timeout=0.2))


class FakeSmtpTests(unittest.TestCase):
    def test_address_parsing(self) -> None:
        self.assertEqual(fake_smtp.address("FROM:<noreply@example.test> SIZE=120"), "noreply@example.test")
        self.assertEqual(fake_smtp.address("TO:<a@example.test>"), "a@example.test")
        self.assertEqual(fake_smtp.address("FROM:<>"), "")

    def test_plain_session_records_message_after_delay(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder) / "mail.jsonl"
            sink = fake_smtp.SmtpSink(out, delay=0.2, tls=None)
            loop = asyncio.new_event_loop()
            server = loop.run_until_complete(asyncio.start_server(sink.handle, "127.0.0.1", 0))
            port = server.sockets[0].getsockname()[1]
            thread = threading.Thread(target=loop.run_forever, daemon=True)
            thread.start()
            try:
                started = time.perf_counter()
                with smtplib.SMTP("127.0.0.1", port, timeout=5) as client:
                    client.login("user", "pass")
                    client.sendmail("noreply@example.test", ["a@example.test"],
                                    "Subject: code\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n인증번호는 123456 입니다.".encode())
                elapsed = time.perf_counter() - started
            finally:
                loop.call_soon_threadsafe(server.close)
                loop.call_soon_threadsafe(loop.stop)
                thread.join(timeout=5)
                loop.close()
            entry = json.loads(out.read_text(encoding="utf-8").strip())
            self.assertEqual(entry["to"], ["a@example.test"])
            self.assertIn("123456", entry["body"])
            self.assertGreaterEqual(elapsed, 0.2)


class FakeAiTests(unittest.TestCase):
    def test_done_body_judges_every_requested_note_in_order(self) -> None:
        body = fake_ai.done_body({"notes": [{"idx": 0}, {"idx": 1}, {"idx": 5}]})
        self.assertEqual(body["status"], "DONE")
        self.assertEqual([note["idx"] for note in body["notes"]], [0, 1, 5])
        self.assertTrue(all(note["verdict"] == "CORRECT" for note in body["notes"]))
        self.assertTrue(1 <= len(body["modelVersion"]) <= 30)
        self.assertTrue(all(0 <= body[key] <= 100 for key in ("total", "pitch", "timing", "duration")))


if __name__ == "__main__":
    unittest.main()
