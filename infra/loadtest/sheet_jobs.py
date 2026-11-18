#!/usr/bin/env python3
"""Bounded sheet import/audio journeys against an isolated QA backend.

Uses real HTTP login, ingress, scheduled workers and signed object downloads.
Accounts, source PDFs and evidence belong outside Git. See README.md.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import re
import subprocess
from pathlib import Path
import threading
import time
import urllib.parse
import urllib.request
import uuid

from loadtest import Client, Recorder


class JourneyFailure(RuntimeError):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


LOCAL_HTTP = urllib.request.build_opener(NoRedirect())


def verify_score(result, sheet_id):
    if str(result.get('sheetId')) != str(sheet_id):
        raise JourneyFailure('Score response belongs to another sheet')
    version=str(result.get('scoreVersion', ''))
    if not version.isdecimal() or int(version)<1:
        raise JourneyFailure('Missing score version')
    draft=result.get('draft') or {}
    common=result.get('scoreData') or draft.get('score')
    if not isinstance(common,dict) or common.get('schemaVersion')!=1:
        raise JourneyFailure('No decoded score or editable draft')
    columns=common.get('columns'); events=common.get('events')
    if not isinstance(columns,list) or not columns or not isinstance(events,list) or not events or not all(isinstance(event,dict) for event in events):
        raise JourneyFailure('Score columns/events are not nonempty lists')
    for column in columns:
        if not isinstance(column,dict) or not isinstance(column.get('cells'),list) or not column['cells']:
            raise JourneyFailure('Score column has no cell structure')
        if not all(isinstance(cell,dict) and isinstance(cell.get('units'),list) for cell in column['cells']):
            raise JourneyFailure('Score cell has no unit structure')
    if str((common.get('score') or {}).get('scoreVersion')) != version:
        raise JourneyFailure('Decoded score version mismatch')
    return version


def verify_audio(result, sheet_id, expected_version):
    if str(result.get('sheetId')) != str(sheet_id):
        raise JourneyFailure('Audio response belongs to another sheet')
    if result.get('outdated') is not False or str(result.get('scoreVersion')) != expected_version or str(result.get('currentScoreVersion')) != expected_version:
        raise JourneyFailure('Audio version mismatch')
    audio=result.get('audio') or {}
    performance=audio.get('performance') or {}
    source=performance.get('sourceCommonSha256') or ''
    timeline=performance.get('timeline') or {}
    if str(timeline.get('scoreVersion')) != expected_version:
        raise JourneyFailure('Performance score version mismatch')
    if not re.fullmatch('[a-f0-9]{64}',source) or timeline.get('sourceSha256') != source or timeline.get('audioSha256') != audio.get('sha256'):
        raise JourneyFailure('Performance source/audio hash binding is absent or mismatched')
    return audio


def import_body(path: Path, metadata: dict) -> tuple[bytes, str]:
    boundary = "qa954" + uuid.uuid4().hex
    parts = [
        f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\nContent-Type: application/json\r\n\r\n'.encode(),
        json.dumps(metadata).encode(),
        f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="input.pdf"\r\nContent-Type: application/pdf\r\n\r\n'.encode(),
        path.read_bytes(), f'\r\n--{boundary}--\r\n'.encode(),
    ]
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


class Journal:
    def __init__(self, target: Path):
        self.target = target
        self.started = time.monotonic()
        self.lock = threading.Lock()
        self.events: list[dict] = []

    def add(self, **event):
        with self.lock:
            self.events.append({"elapsedMs": round((time.monotonic()-self.started)*1000), **event})

    def save(self):
        self.target.parent.mkdir(parents=True, exist_ok=True)
        self.target.write_text(json.dumps(self.events, ensure_ascii=False, indent=2), encoding="utf-8")


def checked(client, journal, user, step, method, path, expected, body=None, **kwargs):
    status, payload = client.call(step, method, path, body, **kwargs)
    result = payload.get("result") if isinstance(payload, dict) else None
    result = result if isinstance(result, dict) else {}
    journal.add(user=user, step=step, http=status, expected=expected,
                sheetId=result.get("sheetId"), state=result.get("status", result.get("conversionStatus")),
                error=result.get("errorCode"), failure=result.get("failureCode"))
    if status not in expected:
        raise JourneyFailure(f"{step}: expected {expected}, received {status}/{result.get('errorCode')}")
    return result


def wait_terminal(client, journal, user, path, deadline):
    previous = None
    while time.monotonic() < deadline:
        status, payload = client.call("poll", "GET", path)
        result = payload.get("result", {})
        state = result.get("status", result.get("conversionStatus"))
        if status != 200 or state != previous:
            journal.add(user=user, step="poll", http=status, state=state, failure=result.get("failureCode"))
        if status != 200:
            raise JourneyFailure(f"poll HTTP {status}")
        if state in ("DONE", "FAILED"):
            return result
        if state not in ("QUEUED", "RUNNING"):
            raise JourneyFailure(f"Unknown job state {state}")
        previous = state
        delay = max(.05, min(float(result.get("pollAfterMs") or 2000)/1000, 5.0))
        remaining = deadline-time.monotonic()
        if remaining > 0:
            threading.Event().wait(min(delay, remaining))
    raise JourneyFailure("Observation deadline exceeded; no terminal state proved")


def verify_download(audio, output, journal, user):
    expected_hash = audio.get("sha256")
    expected_size = audio.get("byteSize")
    if not expected_hash or not isinstance(expected_size, int) or not 0 < expected_size <= 15*1024*1024:
        raise JourneyFailure("Missing/invalid download size or hash")
    if type(audio.get('durationMs')) is not int or not 0<audio['durationMs']<=600_000:
        raise JourneyFailure('Missing/invalid audio duration')
    url = urllib.parse.urlsplit(audio["url"])
    if url.scheme != "http" or url.hostname not in ("localhost", "127.0.0.1", "host.docker.internal", "storage"):
        raise JourneyFailure("This isolated harness does not download from external storage")
    digest = hashlib.sha256()
    size = 0
    with LOCAL_HTTP.open(audio["url"], timeout=30) as response, output.open("wb") as target:
        if response.status!=200 or response.headers.get_content_type() not in ('audio/mp4','audio/x-m4a'):
            raise JourneyFailure('Download is not a complete M4A response')
        while chunk := response.read(65536):
            size += len(chunk)
            if size > expected_size:
                raise JourneyFailure("Download exceeds declared size")
            target.write(chunk)
            digest.update(chunk)
    if size != expected_size or digest.hexdigest() != expected_hash:
        raise JourneyFailure("Download size/hash mismatch")
    journal.add(user=user, step="download", bytes=size, sha256=digest.hexdigest(),
                durationMs=audio.get("durationMs"), file=output.name,
                performanceSource=(audio.get("performance") or {}).get("sourceCommonSha256"))


def verify_media(audio, output, journal, user):
    """Decode the complete AAC file; absent tools or partial decoding cannot pass."""
    probe=subprocess.run(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(output)],capture_output=True,text=True,check=True,timeout=30)
    info=json.loads(probe.stdout); streams=info.get('streams') or []
    if len(streams)!=1 or streams[0].get('codec_name')!='aac' or 'm4a' not in info.get('format',{}).get('format_name',''):
        raise JourneyFailure('Expected one AAC stream in an M4A container')
    duration=float(info['format']['duration'])
    if not 0<duration<=600 or abs(duration*1000-audio['durationMs'])>100:
        raise JourneyFailure('Decoded duration differs from declared duration (>100 ms)')
    subprocess.run(['ffmpeg','-v','error','-xerror','-i',str(output),'-f','null','-'],capture_output=True,check=True,timeout=30)
    journal.add(user=user,step='media-decoded',durationSeconds=duration,codec='aac',sampleRate=streams[0].get('sample_rate'),channels=streams[0].get('channels'))


def run(args):
    parsed = urllib.parse.urlsplit(args.base)
    if parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1", "::1", "backend"):
        raise ValueError("Use the isolated loopback/container backend only")
    accounts = json.loads(args.accounts.read_text(encoding="utf-8"))[:args.users]
    if len(accounts) != args.users:
        raise ValueError("Insufficient isolated accounts")
    args.out.mkdir(parents=True, exist_ok=True)
    journal = Journal(args.out/"events.json")
    recorder = Recorder()
    clients = []
    created: list[tuple[int, Client, str]] = []
    failure = None
    try:
        for index, account in enumerate(accounts):
            client = Client(args.base, recorder, timeout=110, opener=LOCAL_HTTP)
            checked(client,journal,index,"login","POST","/api/auth/sign-in",[200],account)
            clients.append(client)
        sheets = []
        versions = []
        if args.scenario == "audio":
            for index,client in enumerate(clients):
                result=checked(client,journal,index,"copy","POST","/api/users/me/sheets/copies",[201],
                               {"songId":args.song_id},headers={"Idempotency-Key":args.tag+f"-copy-{index}"})
                sheet_id=result["sheetId"]
                sheets.append(sheet_id)
                created.append((index,client,sheet_id))
                score=checked(client,journal,index,'copy-score','GET',f'/api/users/me/sheets/{sheet_id}/score',[200])
                versions.append(verify_score(score,sheet_id))
        else:
            if args.file is None:
                raise ValueError("import requires --file")
            if args.file.suffix.lower()!='.pdf':
                raise ValueError('This driver covers PDF imports; image imports require their own media type')
            content=args.file.read_bytes()
            journal.add(step="input",name=args.file.name,bytes=len(content),sha256=hashlib.sha256(content).hexdigest())
        barrier = threading.Barrier(len(clients))

        def journey(index):
            client=clients[index]
            barrier.wait(timeout=10)
            if args.scenario=="audio":
                sheet_id=sheets[index]
                path=f"/api/users/me/sheets/{sheet_id}/audio"
                result=checked(client,journal,index,"audio-submit","POST",path,[202,200],{})
            else:
                metadata={"title":f"qa954-{args.tag}-{index}","notation":args.notation,"instrumentId":args.instrument_id}
                if args.hwang_pitch:metadata["hwangPitch"]=args.hwang_pitch
                if args.jangdan_id:metadata["jangdanId"]=args.jangdan_id
                raw,content_type=import_body(args.file,metadata)
                result=checked(client,journal,index,"import-submit","POST","/api/users/me/sheets/imports",[202],
                               raw=raw,content_type=content_type,headers={"Idempotency-Key":args.tag+f"-import-{index}"})
                sheet_id=result["sheetId"]
                with journal.lock:created.append((index,client,sheet_id))
                path=f"/api/users/me/sheets/{sheet_id}/conversion"
            final=wait_terminal(client,journal,index,path,time.monotonic()+args.deadline)
            if final.get("status", final.get("conversionStatus"))!="DONE":
                raise JourneyFailure(f"user {index}: terminal failure {final.get('failureCode')}")
            if args.scenario=="audio":
                audio=verify_audio(final,sheet_id,versions[index])
                verify_download(audio,args.out/f"user-{index}.m4a",journal,index)
                verify_media(audio,args.out/f"user-{index}.m4a",journal,index)
                (args.out/f'user-{index}-performance.json').write_text(json.dumps(audio['performance'],ensure_ascii=False),encoding='utf-8')
                journal.add(user=index,step="versions",scoreVersion=final.get("scoreVersion"),currentScoreVersion=final.get("currentScoreVersion"))
            else:
                score=checked(client,journal,index,"recognized-score","GET",f"/api/users/me/sheets/{sheet_id}/score",[200])
                verify_score(score,sheet_id)
                snapshot={key:score.get(key) for key in ('sheetId','scoreVersion','title','bpm','jangdanId','instrumentId','scoreData','draft','issues','expressions','authoring')}
                (args.out/f'user-{index}-score.json').write_text(json.dumps(snapshot,ensure_ascii=False),encoding='utf-8')
                journal.add(user=index,step="recognition",reviewStatus=score.get("reviewStatus"),scoreVersion=score.get("scoreVersion"),
                            hasScore=score.get("scoreData") is not None,hasDraft=score.get("draft") is not None,issueCount=len(score.get("issues") or []))

        with ThreadPoolExecutor(max_workers=args.users) as pool:
            futures=[pool.submit(journey,index) for index in range(args.users)]
            errors=[]
            for future in futures:
                try:future.result()
                except Exception as error:errors.append(f"{type(error).__name__}: {error}")
            if errors:raise JourneyFailure("; ".join(errors))
    except Exception as error:
        failure=f"{type(error).__name__}: {error}"
        journal.add(step="failure",reason=failure)
    finally:
        if args.cleanup:
            for index,client,sheet_id in created:
                try:checked(client,journal,index,"cleanup","DELETE",f"/api/users/me/sheets/{sheet_id}",[204])
                except Exception as error:
                    failure=failure or f"cleanup: {error}"
                    journal.add(user=index,step="cleanup-failure",sheetId=sheet_id,reason=type(error).__name__)
        journal.save()
    print(json.dumps({"scenario":args.scenario,"users":args.users,"passed":failure is None,"failure":failure,"evidence":str(journal.target)}))
    return 1 if failure else 0


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenario",choices=["audio","import"])
    parser.add_argument("--base",required=True)
    parser.add_argument("--accounts",type=Path,required=True)
    parser.add_argument("--users",type=int,choices=range(1,5),default=1)
    parser.add_argument("--tag",required=True)
    parser.add_argument("--out",type=Path,required=True)
    parser.add_argument("--song-id",default="1")
    parser.add_argument("--file",type=Path)
    parser.add_argument("--notation",choices=["STAFF","JEONGGANBO"],default="STAFF")
    parser.add_argument("--instrument-id",default="1")
    parser.add_argument("--hwang-pitch")
    parser.add_argument("--jangdan-id")
    parser.add_argument("--deadline",type=float,default=720)
    parser.add_argument("--cleanup",action="store_true")
    return run(parser.parse_args())


if __name__=="__main__":
    raise SystemExit(main())
