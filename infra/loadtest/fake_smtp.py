#!/usr/bin/env python3
"""Local fake SMTP server that keeps every message on this machine.

The backend requires STARTTLS, SMTP AUTH and a server-identity check
(SmtpVerificationMailSender), so this server offers STARTTLS with a localhost
certificate that the test JVM trusts through its own truststore. AUTH is
accepted without checking. Each message is appended as one JSON line to --out
so the load test can read the verification code. --delay waits before the final
DATA reply to imitate a real provider's send time, which the backend spends
inside its email-verification lock.

Python 3.11+ (asyncio StreamWriter.start_tls).

Usage:
  python3.12 infra/loadtest/fake_smtp.py --port 2525 --out mail.jsonl \
      --tls-cert localhost.crt --tls-key localhost.key --delay 1.0
"""
from __future__ import annotations

import argparse
import asyncio
import email
import email.policy
import json
import ssl
import sys
import time
from pathlib import Path


def address(argument: str) -> str:
    """Return the bare address from 'FROM:<a@b> SIZE=1' or 'TO:<a@b>'."""
    value = argument.split(":", 1)[1].strip()
    if value.startswith("<"):
        value = value[1:].split(">", 1)[0]
    return value.split()[0] if value else ""


def message_text(data: bytes) -> tuple[str, str]:
    message = email.message_from_bytes(data, policy=email.policy.default)
    body = message.get_body(preferencelist=("plain", "html"))
    text = body.get_content() if body is not None else data.decode("utf-8", "replace")
    return str(message.get("Subject", "")), text


class SmtpSink:
    def __init__(self, out: Path, delay: float, tls: ssl.SSLContext | None) -> None:
        self.out = out
        self.delay = delay
        self.tls = tls
        self.count = 0

    def record(self, mail_from: str, rcpt: list[str], data: bytes) -> None:
        subject, body = message_text(data)
        entry = {"at": time.time(), "from": mail_from, "to": rcpt, "subject": subject, "body": body}
        with self.out.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(entry, ensure_ascii=False) + "\n")
        self.count += 1

    async def handle(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        async def reply(line: str) -> None:
            writer.write((line + "\r\n").encode())
            await writer.drain()

        secured = False
        mail_from, rcpt = "", []
        try:
            await reply("220 fake-smtp ready")
            while True:
                raw = await reader.readline()
                if not raw:
                    return
                line = raw.decode("utf-8", "replace").rstrip("\r\n")
                verb = line.split(" ", 1)[0].upper()
                if verb in ("EHLO", "HELO"):
                    lines = ["fake-smtp", "AUTH PLAIN LOGIN", "8BITMIME"]
                    if self.tls is not None and not secured:
                        lines.insert(1, "STARTTLS")
                    for index, text in enumerate(lines):
                        sep = " " if index == len(lines) - 1 else "-"
                        writer.write(f"250{sep}{text}\r\n".encode())
                    await writer.drain()
                elif verb == "STARTTLS" and self.tls is not None and not secured:
                    await reply("220 ready to start TLS")
                    await writer.start_tls(self.tls)
                    secured = True
                    mail_from, rcpt = "", []
                elif verb == "AUTH":
                    parts = line.split()
                    mechanism = parts[1].upper() if len(parts) > 1 else ""
                    if mechanism == "LOGIN" and len(parts) == 2:
                        await reply("334 VXNlcm5hbWU6")
                        await reader.readline()
                        await reply("334 UGFzc3dvcmQ6")
                        await reader.readline()
                    elif mechanism == "PLAIN" and len(parts) == 2:
                        await reply("334 ")
                        await reader.readline()
                    await reply("235 accepted")
                elif verb == "MAIL":
                    mail_from, rcpt = address(line), []
                    await reply("250 ok")
                elif verb == "RCPT":
                    rcpt.append(address(line))
                    await reply("250 ok")
                elif verb == "DATA":
                    await reply("354 end with <CRLF>.<CRLF>")
                    chunks = []
                    while True:
                        data_line = await reader.readline()
                        if not data_line or data_line in (b".\r\n", b".\n"):
                            break
                        if data_line.startswith(b".."):
                            data_line = data_line[1:]
                        chunks.append(data_line)
                    if self.delay > 0:
                        await asyncio.sleep(self.delay)
                    self.record(mail_from, rcpt, b"".join(chunks))
                    await reply("250 queued")
                elif verb == "RSET":
                    mail_from, rcpt = "", []
                    await reply("250 ok")
                elif verb == "NOOP":
                    await reply("250 ok")
                elif verb == "QUIT":
                    await reply("221 bye")
                    return
                else:
                    await reply("502 not implemented")
        except (ConnectionError, ssl.SSLError):
            return
        finally:
            writer.close()


def tls_context(cert: Path | None, key: Path | None) -> ssl.SSLContext | None:
    if cert is None and key is None:
        return None
    if cert is None or key is None:
        raise SystemExit("--tls-cert and --tls-key go together")
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(cert, key)
    return context


async def serve(host: str, port: int, sink: SmtpSink) -> None:
    server = await asyncio.start_server(sink.handle, host, port)
    tls = "starttls" if sink.tls else "plain"
    print(f"fake-smtp {tls} on {host}:{port} delay={sink.delay}s out={sink.out}", flush=True)
    async with server:
        await server.serve_forever()


def main() -> None:
    if sys.version_info < (3, 11):
        raise SystemExit("Python 3.11+ is required (asyncio start_tls)")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=2525)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--delay", type=float, default=0.0, help="seconds before accepting DATA")
    parser.add_argument("--tls-cert", type=Path)
    parser.add_argument("--tls-key", type=Path)
    args = parser.parse_args()
    if args.host not in ("127.0.0.1", "localhost", "::1"):
        parser.error("fake SMTP only binds to localhost")
    sink = SmtpSink(args.out, args.delay, tls_context(args.tls_cert, args.tls_key))
    asyncio.run(serve(args.host, args.port, sink))


if __name__ == "__main__":
    main()
