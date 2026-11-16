#!/usr/bin/env python3
"""Run one app API scenario against the command center's disposable QA stack."""
import asyncio
from contextlib import AsyncExitStack, contextmanager
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import secrets
from uuid import uuid4

ORIGIN = "http://127.0.0.1:18181"
SONG, BPM, BEAT_UNIT = "1", 96, "DOTTED_QUARTER"
FIRST_NOTE, LAST_NOTE = 0, 7
NOTE_INDICES = list(range(FIRST_NOTE, LAST_NOTE + 1))
ALTERED_NOTE = 4
SCORE_LABELS = {
    "totalScore": "종합 점수", "pitchScore": "음정 점수",
    "timingScore": "박자 점수", "durationScore": "음 길이 점수",
}
VERDICT_LABELS = {"CORRECT": "정확", "PITCH": "음정", "TIMING": "박자", "LENGTH": "길이", "MISSED": "놓침"}


class ScenarioFailure(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ScenarioFailure(message)


def atomic_json(path, value):
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    temporary.replace(path)


class Progress:
    def __init__(self, scenario, path):
        self.path = path
        self.document = {"scenario": scenario, "status": "running", "steps": [], "result": None}
        self.write()

    def write(self):
        atomic_json(self.path, self.document)

    @contextmanager
    def step(self, label, detail):
        row = {"label": label, "status": "running", "detail": detail}
        self.document["steps"].append(row)
        self.write()
        try:
            yield row
        except Exception as error:
            row.update(status="fail", detail=safe_failure(error))
            self.write()
            raise
        else:
            row["status"] = "pass"
            self.write()

    def finish(self, status, result):
        self.document.update(status=status, result=result)
        self.write()


def safe_failure(error):
    if isinstance(error, ScenarioFailure):
        return str(error)
    # Unexpected library errors can carry sensitive request or storage details.
    return f"격리 QA 요청을 완료하지 못했습니다 ({type(error).__name__})."


def metrics(summary, *values, **extra):
    return {"summary": summary, "metrics": [{"label": key, "value": str(value)} for key, value in values], **extra}


def details(response, expected):
    require(response.status_code == expected, f"HTTP {expected} 응답이 필요했지만 HTTP {response.status_code}을 받았습니다.")
    try:
        document = response.json()
    except ValueError:
        document = None
    require(isinstance(document, dict) and document.get("isSuccess") is (expected < 400)
            and isinstance(document.get("result"), dict), f"HTTP {expected} 응답의 결과 형식이 일치하지 않습니다.")
    return document["result"]


class QA:
    def __init__(self, stack, work, httpx, api, evidence, credentials, model_version="pyin-9"):
        self.stack = stack
        self.work = work
        self.httpx = httpx
        self.api = api
        self.evidence = evidence
        self.credentials = credentials
        self.version = None
        self.note_names = []
        self.model_version = model_version

    async def login(self, index):
        client = await self.stack.enter_async_context(self.httpx.AsyncClient(
            base_url=ORIGIN, trust_env=False, follow_redirects=False, timeout=12,
        ))
        response = await client.post("/api/auth/sign-in", json=self.credentials[index])
        user_id = self.api.checked_id(details(response, 200).get("userId"))
        if self.version is None:
            song = details(await client.get(f"/api/songs/{SONG}"), 200)
            self.version = song.get("scoreVersion")
            require(song.get("songId") == SONG and song.get("isScorable") is True
                    and type(self.version) is int and self.version > 0
                    and type(song.get("noteCount")) is int and song["noteCount"] > LAST_NOTE,
                    "격리 API의 아리랑 악보가 시험 구간과 일치하지 않습니다.")
            note_events = [event for event in song["score"]["events"] if event["kind"] == "NOTE"]
            self.note_names = [event["yul"] for event in note_events[FIRST_NOTE:LAST_NOTE + 1]]
            require(self.note_names == ["중", "임", "중", "임", "무", "황", "무", "황"],
                    "아리랑 첫 8음표가 합성 녹음의 악보와 다릅니다.")
        return client, user_id

    async def session(self, client):
        response = await client.post("/api/practice-sessions", headers={"Idempotency-Key": secrets.token_hex(16)}, json={
            "songId": SONG, "bpm": BPM, "bpmUnit": BEAT_UNIT, "startNoteIdx": FIRST_NOTE,
            "endNoteIdx": LAST_NOTE, "expectedScoreVersion": self.version,
        })
        session = details(response, 201)
        require(session.get("status") == "RECORDING", "새 연습 세션이 녹음 대기 상태가 아닙니다.")
        return self.api.checked_id(session.get("sessionId"))

    async def upload(self, client, sid, audio):
        return await client.post(f"/api/practice-sessions/{sid}/recording",
                                 headers={"X-Evaluation-Timing-Id": str(uuid4())},
                                 files={"audio": ("practice.m4a", audio, "audio/mp4")})

    def accepted(self, response, sid):
        result = details(response, 202)
        require(result.get("sessionId") == sid and result.get("status") == "PENDING"
                and result.get("evaluationId") is None, "같은 연습 세션의 채점 대기 응답을 확인하지 못했습니다.")

    async def wait(self, client, sid, expected):
        loop = asyncio.get_running_loop()
        began = loop.time()
        async with asyncio.timeout(75):
            while True:
                state = details(await client.get(f"/api/practice-sessions/{sid}", timeout=30), 200)
                require(state.get("sessionId") == sid, "상태 조회에서 다른 연습 세션이 반환됐습니다.")
                current = state.get("status")
                if current == expected:
                    return state
                if current != "PENDING":
                    code = state.get("failReason")
                    code = code if isinstance(code, str) and re.fullmatch(r"[A-Z][A-Z0-9_]{1,60}", code) else "-"
                    safe_status = current if current in {"RECORDING", "FAILED", "DONE"} else "UNKNOWN"
                    raise ScenarioFailure(f"예상 상태 {expected} 대신 {safe_status} ({code})를 받았습니다.")
                await asyncio.sleep(1 if loop.time() - began < 8 else 2)

    async def result(self, client, sid, state):
        evaluation_id = self.api.checked_id(state.get("evaluationId"))
        response = await client.get(f"/api/practice-sessions/{sid}/evaluation", timeout=30)
        result = details(response, 200)
        require(result.get("sessionId") == sid and result.get("evaluationId") == evaluation_id
                and result.get("songId") == SONG and result.get("scoreVersion") == self.version
                and result.get("bpm") == BPM and result.get("bpmUnit") == BEAT_UNIT,
                "조회한 평가가 요청한 세션·악보·템포와 일치하지 않습니다.")
        require(all(type(result.get(key)) is int and 0 <= result[key] <= 100 for key in SCORE_LABELS),
                "평가 점수 범위가 일치하지 않습니다.")
        notes = result.get("notes")
        require(isinstance(notes, list) and all(isinstance(note, dict) for note in notes)
                and [note.get("noteIdx") for note in notes] == NOTE_INDICES
                and all(note.get("verdict") in VERDICT_LABELS for note in notes)
                and all(note.get(key) is None or type(note[key]) is int
                        for note in notes for key in ("pitchErrorCent", "onsetErrorMs", "durationErrorMs")),
                "요청 구간의 음표별 판정과 오차를 확인하지 못했습니다.")
        return result

    async def grade(self, client, sid, audio):
        self.accepted(await self.upload(client, sid, audio), sid)
        return await self.result(client, sid, await self.wait(client, sid, "DONE"))

    def saved(self, sid, evaluation, attempt):
        db = self.evidence.stage_database({sid})
        row = db["sessions"].get(sid, {})
        require(db["verified"] and (row.get("status"), row.get("failReason"), row.get("attempts"),
                                    row.get("evaluations"), row.get("notes"), row.get("modelVersion")) ==
                ("DONE", None, attempt, 1, len(NOTE_INDICES), self.model_version),
                "DB에 저장된 채점 시도·평가·음표 개수가 일치하지 않습니다.")
        saved_id = self.evidence.query(f"SELECT id FROM evaluations WHERE session_id={sid};")
        require(saved_id == evaluation["evaluationId"], "조회한 평가 ID와 DB 평가 ID가 다릅니다.")
        events = []
        for path in (self.work / "runtime").glob("ai-18*.log"):
            events.extend(re.findall(
                rf"analysis session={sid} attempt=(\d+) result=(\w+) ms=\d+", path.read_text(errors="replace"),
            ))
        require(events == [(str(attempt), "DONE")], "실제 AI 완료 로그가 예상 시도와 일치하지 않습니다.")

    def recording_fingerprint(self, sid):
        fingerprint = self.evidence.query(f"SELECT SHA2(recording_url,256) FROM practice_sessions WHERE id={sid};")
        require(re.fullmatch(r"[a-f0-9]{64}", fingerprint) is not None, "저장된 녹음의 식별자를 확인하지 못했습니다.")
        return fingerprint


async def feedback(qa, progress, baseline, audio_dir):
    altered = (audio_dir / "arirang-altered.m4a").read_bytes()
    require(sha256(baseline).digest() != sha256(altered).digest(), "두 아리랑 합성 녹음이 같은 파일입니다.")
    with progress.step("같은 아리랑 구간 준비", "96 BPM으로 음표 8개의 연습 세션 두 개를 만듭니다.") as step:
        client, _ = await qa.login(0)
        original_id = await qa.session(client)
        altered_id = await qa.session(client)
        require(original_id != altered_id, "기준 녹음과 변형 녹음의 세션이 같습니다.")
        step["detail"] = "같은 악보 버전과 96 BPM으로 서로 다른 세션을 만들었습니다."
    with progress.step("기준 녹음 채점", "기준 녹음을 제출하고 본인 평가를 조회합니다.") as step:
        original = await qa.grade(client, original_id, baseline)
        qa.saved(original_id, original, 1)
        step["detail"] = f"종합 {original['totalScore']}점, 음표 {len(original['notes'])}개의 결과를 확인했습니다."
    with progress.step("변형 녹음 채점", "음정을 바꾼 녹음을 제출하고 본인 평가를 조회합니다.") as step:
        variant = await qa.grade(client, altered_id, altered)
        qa.saved(altered_id, variant, 1)
        step["detail"] = f"종합 {variant['totalScore']}점, 음표 {len(variant['notes'])}개의 결과를 확인했습니다."
    with progress.step("점수·음표 피드백 비교", "두 녹음의 실제 AI 판정을 같은 음표 순서로 대조합니다.") as step:
        changed = [(left, right) for left, right in zip(original["notes"], variant["notes"])
                   if left["verdict"] != right["verdict"]]
        expected = [(ALTERED_NOTE, "CORRECT", "PITCH")]
        require(original["totalScore"] > variant["totalScore"] and original["pitchScore"] > variant["pitchScore"]
                and all(note["verdict"] == "CORRECT" for note in original["notes"])
                and [(left["noteIdx"], left["verdict"], right["verdict"]) for left, right in changed] == expected,
                "기준보다 낮은 변형 음정·종합 점수와 다섯 번째 무 음의 판정 변화를 함께 확인하지 못했습니다.")
        step["detail"] = f"음정 점수 {original['pitchScore']} → {variant['pitchScore']}점, 다섯 번째 무 음의 판정 변화."

    def feedback_cell(note):
        verdict = VERDICT_LABELS[note["verdict"]]
        cents = note.get("pitchErrorCent")
        return f"{verdict} · {cents:+d}센트" if cents is not None else verdict

    rows = [[name, original[key], variant[key]] for key, name in SCORE_LABELS.items()]
    for left, right in zip(original["notes"], variant["notes"]):
        index = left["noteIdx"]
        label = f"{index + 1}번째 음표 ({qa.note_names[index - FIRST_NOTE]})"
        if index == ALTERED_NOTE:
            label += " · 변경"
        rows.append([label, feedback_cell(left), feedback_cell(right)])
    return metrics("같은 구간의 녹음을 바꾸자 실제 채점 점수와 음표 판정이 달라졌습니다.",
                   ("연습 구간", "아리랑 첫 8음표"), ("판정 변화", "5번째 무"),
                   feedback={"columns": ["항목", "기준 녹음", "변형 녹음"], "rows": rows})


async def retry(qa, progress, baseline, _audio_dir):
    with progress.step("같은 녹음으로 재시도 준비", "격리 QA 계정으로 연습 세션을 만들고 첫 AI 요청에만 실패를 설정합니다.") as step:
        client, _ = await qa.login(0)
        sid = await qa.session(client)
        gate = qa.work / "runtime/retry-first-attempt.json"
        consumed = gate.with_name(gate.name + ".consumed")
        require(os.environ.get("EOLSSU_QA_RETRY_GATE") == str(gate) and not gate.exists() and not consumed.exists(),
                "이 격리 실행에서만 쓰는 첫 시도 실패 설정을 확인하지 못했습니다.")
        atomic_json(gate, {"sessionId": sid})
        step["detail"] = "이 세션의 첫 AI 호출에만 HTTP 503을 설정했습니다."
    with progress.step("첫 채점 실패 확인", "녹음을 한 번 업로드한 뒤 앱의 상태 조회로 실패를 확인합니다.") as step:
        qa.accepted(await qa.upload(client, sid, baseline), sid)
        failed = await qa.wait(client, sid, "FAILED")
        require(failed.get("failReason") == "SERVER_ERROR" and failed.get("retryable") is True
                and failed.get("evaluationId") is None and failed.get("recordingAvailable") is True,
                "첫 시도에서 같은 녹음으로 재시도 가능한 서버 오류가 확인되지 않았습니다.")
        require(not gate.exists() and consumed.is_file() and json.loads(consumed.read_text()) == {"sessionId": sid},
                "첫 AI 요청에서 격리 장애 설정이 한 번 소비됐다는 증거가 없습니다.")
        db = qa.evidence.stage_database({sid})["sessions"].get(sid, {})
        require((db.get("status"), db.get("failReason"), db.get("attempts"), db.get("evaluations")) ==
                ("FAILED", "SERVER_ERROR", 1, 0), "DB 첫 시도에 실패가 저장되지 않았거나 평가가 생겼습니다.")
        recording = qa.recording_fingerprint(sid)
        step["detail"] = "첫 시도 SERVER_ERROR, 재시도 가능, 저장된 평가는 0건입니다."
    with progress.step("재시도 API 요청", "저장된 녹음이 있는 세션의 재시도 API를 실행합니다.") as step:
        timing = str(uuid4())
        response = await client.post(f"/api/practice-sessions/{sid}/retry", headers={
            "Idempotency-Key": secrets.token_hex(16), "X-Evaluation-Timing-Id": timing,
        })
        result = details(response, 202)
        require(result.get("sessionId") == sid and result.get("status") == "PENDING"
                and result.get("retryable") is False
                and response.headers.get("X-Evaluation-Retry-Status") == "started"
                and response.headers.get("X-Evaluation-Timing-Id") == timing,
                "같은 세션에서 새 채점 시도가 시작되지 않았습니다.")
        step["detail"] = "재시도 HTTP 202를 받았습니다. 녹음 업로드는 처음 한 번만 수행했습니다."
    with progress.step("두 번째 실제 채점 검증", "본인 결과와 DB 저장 내용을 확인합니다.") as step:
        evaluation = await qa.result(client, sid, await qa.wait(client, sid, "DONE"))
        qa.saved(sid, evaluation, 2)
        require(qa.recording_fingerprint(sid) == recording, "재시도 후 저장된 녹음이 바뀌었습니다.")
        step["detail"] = "같은 녹음의 두 번째 시도가 완료됐고 평가 1건을 저장했습니다."
    return metrics("첫 AI 요청에 HTTP 503을 한 번 주입한 뒤, 같은 녹음으로 실제 채점에 성공했습니다.",
                   ("녹음 업로드", "1회"), ("첫 채점", "SERVER_ERROR"), ("두 번째 채점", "DONE"), ("DB 평가", "1건"))


async def duplicate(qa, progress, baseline, _audio_dir):
    with progress.step("같은 계정과 연습 세션 준비", "한 계정으로 두 HTTP 연결을 인증합니다.") as step:
        first, owner = await qa.login(0)
        second, second_owner = await qa.login(0)
        require(owner == second_owner, "두 HTTP 연결이 같은 사용자로 로그인되지 않았습니다.")
        sid = await qa.session(first)
        step["detail"] = "같은 계정과 세션에 같은 녹음을 제출할 두 연결을 준비했습니다."
    with progress.step("녹음 두 건 동시 제출", "두 요청을 같은 시점에 시작합니다.") as step:
        gate = asyncio.Event()
        loop = asyncio.get_running_loop()
        starts = []

        async def upload(client):
            await gate.wait()
            starts.append(loop.time())
            return await qa.upload(client, sid, baseline)

        requests = [asyncio.create_task(upload(client)) for client in (first, second)]
        gate.set()
        responses = sorted(await asyncio.gather(*requests), key=lambda response: response.status_code)
        skew = round((max(starts) - min(starts)) * 1000, 3)
        require(skew <= 100, "두 업로드의 시작 시점 차이가 100ms를 넘었습니다.")
        qa.accepted(responses[0], sid)
        require(details(responses[1], 409).get("errorCode") == "SESSION_NOT_RECORDING",
                "중복 요청에 대한 서버 충돌 사유가 일치하지 않습니다.")
        step["detail"] = f"시작 간격 {skew:g}ms. HTTP 202 접수 1건, HTTP 409 중복 거절 1건을 확인했습니다."
    with progress.step("채점과 평가 저장 횟수 확인", "DB 및 실제 AI 로그를 본인 결과와 대조합니다.") as step:
        evaluation = await qa.result(first, sid, await qa.wait(first, sid, "DONE"))
        qa.saved(sid, evaluation, 1)
        step["detail"] = "실제 AI 채점 시도 1회와 DB 평가 1건을 확인했습니다."
    return metrics("두 제출을 동시에 시작했으며 서버가 한 건만 접수·채점했습니다.",
                   ("업로드 시작 간격", f"{skew:g}ms"), ("접수", "HTTP 202 · 1건"),
                   ("중복 거절", "HTTP 409 · 1건"), ("채점 / 평가 저장", "1회 / 1건"))


async def ownership(qa, progress, baseline, _audio_dir):
    with progress.step("서로 다른 QA 계정 로그인", "소유자와 다른 사용자의 인증 연결을 만듭니다.") as step:
        owner, owner_id = await qa.login(0)
        other, other_id = await qa.login(1)
        require(owner_id != other_id, "서로 다른 로그인 계정을 확인하지 못했습니다.")
        sid = await qa.session(owner)
        step["detail"] = "로그인 사용자 두 명이 서로 다르고 세션은 소유자가 만들었습니다."
    with progress.step("소유자 본인 결과 확인", "소유자가 녹음을 제출하고 실제 채점 결과를 조회합니다.") as step:
        evaluation = await qa.grade(owner, sid, baseline)
        qa.saved(sid, evaluation, 1)
        step["detail"] = f"HTTP 200으로 점수와 음표 {len(evaluation['notes'])}개를 확인했습니다."
    with progress.step("다른 계정의 같은 결과 요청", "동일한 평가 URL을 다른 로그인 계정으로 호출합니다.") as step:
        response = await other.get(f"/api/practice-sessions/{sid}/evaluation", timeout=30)
        require(details(response, 404).get("errorCode") == "RESOURCE_NOT_FOUND",
                "다른 계정에 대한 결과 숨김 정책이 일치하지 않습니다.")
        step["detail"] = "다른 계정에는 HTTP 404 RESOURCE_NOT_FOUND를 반환했습니다."
    return metrics("같은 평가 URL을 소유자는 조회했고 다른 로그인 사용자는 조회할 수 없었습니다.",
                   ("소유자", "HTTP 200"), ("다른 로그인 계정", "HTTP 404"), ("결과 구간", f"음표 {len(NOTE_INDICES)}개"))


SCENARIOS = {"feedback": feedback, "retry": retry, "duplicate": duplicate, "ownership": ownership}
