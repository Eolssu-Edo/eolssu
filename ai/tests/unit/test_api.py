"""POST /v1/analyses를 합성음 m4a와 가짜 다운로드로 검사한다. ffmpeg가 PATH에 있어야 한다.

응답 모양은 백엔드 HttpAnalysisClient가 읽는 필드 이름·타입과 맞춘다. 채점 품질 검증이 아니다.
"""

import logging
import subprocess
import traceback

import httpx
import numpy as np
import pytest
from fastapi.testclient import TestClient

from ai_service import api
from pydantic import TypeAdapter

from ai_service.api import AnalysisDone, AnalysisError, AnalysisResult, DownloadError, InvalidRequest, NoteOut, app, get_fetcher
from ai_service.pipelines.scoring import HOP_S, Note, combine, total_score
from evaluation.synthetic import BPM, SR, synth

SIGNED_URL = "https://r2.example/recordings/1.m4a?X-Amz-Signature=secret-token"
NOTES = [Note(0, 1), Note(2, 1), Note(4, 1), Note(5, 1)]
FIRST_IDX = 7  # 곡 중간에서 시작하고 간격이 있어도 요청 idx를 그대로 돌려줘야 한다
START_BEAT = 12.0  # 곡 처음부터 센 첫 음의 시작 박

DONE_KEYS = {
    "status", "modelVersion", "scoringVersion", "scoreWeights", "total", "pitch", "timing", "duration",
    "tuningOffsetCent", "tuningOutOfRange", "notes",
}
NOTE_KEYS = {
    "idx", "verdict", "pitchErrorCent", "onsetErrorMs", "durationErrorMs", "detectedSemitone", "recordingOffsetMs",
    "pitchScore", "timingScore", "durationScore", "weight",
}


def m4a(audio, tmp_path):
    """16kHz float 배열을 앱 녹음처럼 조각이 아닌 AAC m4a(moov 앞)로 만들어 바이트로 돌려준다.

    조각 mp4는 AAC 앞부분 지연(edit list)을 적지 않아 음이 늦게 시작한 것처럼 디코딩된다.
    """
    path = tmp_path / "recording.m4a"
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
           "-c:a", "aac", "-movflags", "+faststart", str(path)]
    subprocess.run(cmd, input=np.clip(audio, -1, 1).astype("<f4").tobytes(), check=True)
    return path.read_bytes()


def body(notes=NOTES, **override):
    return {
        "sessionId": 1,
        "attempt": 2,
        "audioUrl": SIGNED_URL,
        "bpm": BPM,
        "bpmUnit": "JEONGGAN",
        "notes": [
            {"idx": FIRST_IDX + 2 * i, "semitone": n.semitone, "startBeat": START_BEAT + i * n.beats, "beats": n.beats,
             "sigimsae": n.sigimsae}
            for i, n in enumerate(notes)
        ],
    } | override


def aliases(model):
    return {field.alias or name for name, field in model.model_fields.items()}


def assert_documented(response):
    """실제 응답이 OpenAPI 문서의 상태별 모델과 같은 키·값 범위인지 본다."""
    result = response.json()
    model = {200: AnalysisResult, 422: InvalidRequest, 500: AnalysisError, 502: AnalysisError}[response.status_code]
    parsed = TypeAdapter(model).validate_python(result)
    if isinstance(parsed, AnalysisDone):
        assert set(result) == aliases(AnalysisDone)
        assert all(set(n) == aliases(NoteOut) for n in result["notes"])
    elif response.status_code == 200:
        assert set(result) == {"status", "failReason"}


@pytest.fixture
def serve():
    """녹음 바이트를 돌려주는 가짜 다운로드를 끼운 클라이언트. 받은 URL을 기록한다."""
    asked = []

    def use(recording):
        def fetch(url, dest):
            asked.append(url)
            if isinstance(recording, Exception):
                raise recording
            dest.write_bytes(recording)

        app.dependency_overrides[get_fetcher] = lambda: fetch
        return TestClient(app, raise_server_exceptions=False)

    use.asked = asked
    yield use
    app.dependency_overrides.clear()


def assert_documented_note_scores(result):
    for note in result["notes"]:
        assert type(note["timingScore"]) is float and 0 <= note["timingScore"] <= 100
        assert type(note["durationScore"]) is float and 0 <= note["durationScore"] <= 100
        assert type(note["pitchScore"]) is float and 0 <= note["pitchScore"] <= 100
        assert note["weight"] == 1.0


def test_correct_playing_is_done_with_requested_idx(serve, tmp_path):
    response = serve(m4a(synth(NOTES), tmp_path)).post("/v1/analyses", json=body())
    assert response.status_code == 200
    result = response.json()
    assert set(result) == DONE_KEYS
    assert result["status"] == "DONE"
    assert result["modelVersion"] == "pyin-8"
    assert [n["idx"] for n in result["notes"]] == [FIRST_IDX + 2 * i for i in range(len(NOTES))]
    assert all(set(n) == NOTE_KEYS for n in result["notes"])
    assert [n["verdict"] for n in result["notes"]] == ["CORRECT"] * len(NOTES)
    for key in ("total", "pitch", "timing", "duration"):
        assert type(result[key]) is int and 0 <= result[key] <= 100
    for note in result["notes"]:  # 백엔드는 Int로 읽는다. 20.0은 받지 않는다
        assert type(note["onsetErrorMs"]) is int and type(note["durationErrorMs"]) is int
        assert type(note["detectedSemitone"]) is int and type(note["recordingOffsetMs"]) is int
    # 맞게 분 음은 인식한 반음이 요청 semitone과 같다(S15P31E102-482).
    assert [n["detectedSemitone"] for n in result["notes"]] == [n.semitone for n in NOTES]
    assert serve.asked == [SIGNED_URL]
    assert_documented(response)


@pytest.mark.parametrize(("instrument", "verdict"), [
    (None, "PITCH"), ("DANSO", "PITCH"), ("SOGEUM", "PITCH"), ("JEONGAK_DAEGEUM", "CORRECT"), ("SANJO_DAEGEUM", "CORRECT"),
])
def test_only_daegeum_lifts_a_whole_octave_low_playing(serve, tmp_path, instrument, verdict):
    # 악보는 청황(12)~청중(17)인데 한 옥타브 낮게 불었다. 대금만 곡 음역 아래 값을 두 배로 고친다(S15P31E102-848).
    high = [Note(n.semitone + 12, n.beats) for n in NOTES]
    request = body(high) | ({"instrument": instrument} if instrument else {})
    result = serve(m4a(synth(NOTES), tmp_path)).post("/v1/analyses", json=request).json()
    assert result["status"] == "DONE"
    assert {n["verdict"] for n in result["notes"]} == {verdict}


def test_wrong_note_is_pitch_not_failure(serve, tmp_path):
    response = serve(m4a(synth(NOTES, shift={2: 2}), tmp_path)).post("/v1/analyses", json=body())
    result = response.json()
    assert result["status"] == "DONE"
    assert [n["verdict"] for n in result["notes"]][2] == "PITCH"


def test_missed_note_has_null_errors(serve, tmp_path):
    result = serve(m4a(synth(NOTES, drop=(1,)), tmp_path)).post("/v1/analyses", json=body()).json()
    missed = result["notes"][1]
    assert missed == {"idx": FIRST_IDX + 2, "verdict": "MISSED", "pitchErrorCent": None, "onsetErrorMs": None,
                      "durationErrorMs": None, "detectedSemitone": None, "recordingOffsetMs": None,
                      "pitchScore": 0.0, "timingScore": 0.0, "durationScore": 0.0, "weight": 1.0}
    TypeAdapter(AnalysisResult).validate_python(result)


def test_note_scores_add_up_to_the_reported_scores(serve, tmp_path):
    # 규칙 A: 백엔드가 구간 점수를 만드는 것과 같은 공식으로 전체를 합치면 응답 점수와 같다.
    result = serve(m4a(synth(NOTES, shift={2: 2}, drop=(1,)), tmp_path)).post("/v1/analyses", json=body()).json()
    assert result["scoringVersion"] == "note-mean-2"
    weights = result["scoreWeights"]
    assert set(weights) == {"pitch", "timing", "duration"} and sum(weights.values()) == pytest.approx(1)

    def item(key):
        return combine([n[key] for n in result["notes"]], [n["weight"] for n in result["notes"]])

    parts = {name: item(f"{name}Score") for name in weights}
    assert parts == {name: result[name] for name in weights}
    assert result["total"] == total_score(parts["pitch"], parts["timing"], parts["duration"], weights)
    assert_documented_note_scores(result)


def test_silence_is_no_sound(serve, tmp_path):
    response = serve(m4a(np.zeros(SR * 2), tmp_path)).post("/v1/analyses", json=body())
    assert response.status_code == 200
    assert response.json() == {"status": "FAILED", "failReason": "NO_SOUND"}
    assert_documented(response)


def test_recording_under_half_length_is_too_short(serve, tmp_path):
    short = synth(NOTES, seconds=0.5)  # 정답 2초의 절반 미만
    result = serve(m4a(short, tmp_path)).post("/v1/analyses", json=body()).json()
    assert result == {"status": "FAILED", "failReason": "TOO_SHORT"}


def test_unreadable_file_is_decode_error(serve):
    response = serve(b"not an audio file").post("/v1/analyses", json=body())
    assert response.status_code == 200
    assert response.json() == {"status": "FAILED", "failReason": "DECODE_ERROR"}
    assert_documented(response)


def test_download_failure_is_server_error_without_url(serve):
    response = serve(DownloadError("403")).post("/v1/analyses", json=body())
    assert response.status_code == 502
    assert "secret-token" not in response.text
    assert_documented(response)


def test_unexpected_error_is_500_without_message(serve):
    response = serve(RuntimeError(f"boom {SIGNED_URL}")).post("/v1/analyses", json=body())
    assert response.status_code == 500
    assert response.json() == {"error": "ANALYSIS_ERROR"}  # 예외가 밖으로 나가면 uvicorn이 메시지째 로그에 남긴다
    assert_documented(response)


def test_invalid_request_does_not_echo_signed_url(serve):
    missing_bpm = {k: v for k, v in body().items() if k != "bpm"}  # 기본 422는 빠진 칸의 상위 객체 전체를 되돌린다
    response = serve(b"").post("/v1/analyses", json=missing_bpm)
    assert response.status_code == 422
    assert "secret-token" not in response.text
    assert serve.asked == []
    assert_documented(response)


def with_start_beats(*beats):
    request = body()
    for note, start in zip(request["notes"], beats):
        note["startBeat"] = start
    return request


@pytest.mark.parametrize(
    "request_body",
    [
        body(notes=[]),
        body(bpmUnit="MINUTE"),
        body(audioUrl="file:///etc/passwd"),
        with_start_beats(-1.0, 0.0, 1.0, 2.0),
        with_start_beats(0.0, 2.0, 1.0, 3.0),  # 음표 순서대로 늘지 않음
        with_start_beats(0.0, 1.0, 1.0, 2.0),  # 같은 시작 박
        with_start_beats(0.0, 0.5, 1.5, 2.5),  # 앞 음(1박)이 끝나기 전에 시작
        {**body(), "notes": [{k: v for k, v in n.items() if k != "startBeat"} for n in body()["notes"]]},
    ],
    ids=["no-notes", "unknown-unit", "file-url", "negative-start", "decreasing-start", "same-start", "overlapping",
         "missing-start"],
)
def test_rejects_requests_outside_contract(serve, request_body):
    assert serve(b"").post("/v1/analyses", json=request_body).status_code == 422


REST_STARTS = (0.0, 1.0, 3.0, 4.0)  # 2박째에 한 박 쉰다
RESTED = [Note(n.semitone, n.beats, start_beat=b) for n, b in zip(NOTES, REST_STARTS)]


def decoded(monkeypatch, audio):
    """디코딩(test_audio.py에서 검사)을 건너뛰고 합성음을 녹음으로 쓴다. ffmpeg 없이 돈다."""
    monkeypatch.setattr(api, "decode_recording", lambda _: (np.clip(audio, -1, 1) * 32767).astype(np.int16))
    return b"recording"


def test_rest_gap_is_scored_with_start_beats(serve, monkeypatch):
    """쉼이 있으면 startBeat가 앞 음의 beats보다 더 벌어진다. 쉼을 지켜 분 연주는 모두 맞고, idx는 요청 그대로다."""
    response = serve(decoded(monkeypatch, synth(RESTED))).post("/v1/analyses", json=with_start_beats(*REST_STARTS))
    result = response.json()
    assert result["modelVersion"] == "pyin-8"
    assert [(n["idx"], n["verdict"]) for n in result["notes"]] == [(FIRST_IDX + 2 * i, "CORRECT") for i in range(4)]


def test_dtw_estimate_counts_rests(serve, monkeypatch):
    """정답 길이는 beats 합(4박)이 아니라 첫 음부터 마지막 음 끝(5박)까지다."""
    audio = synth(RESTED)
    beat_frames, recording_frames = 60.0 / BPM / HOP_S, len(audio) / SR / HOP_S
    monkeypatch.setattr(api, "MAX_DTW_BYTES", 4.5 * beat_frames * recording_frames * api.DTW_BYTES_PER_CELL)
    called = []
    monkeypatch.setattr(api, "pyin_f0", lambda *a, **k: called.append(1))
    response = serve(decoded(monkeypatch, audio)).post("/v1/analyses", json=with_start_beats(*REST_STARTS))
    assert response.status_code == 500
    assert called == []


def test_signed_url_is_not_logged(serve, caplog, tmp_path, monkeypatch):
    caplog.set_level(logging.DEBUG)
    monkeypatch.setattr(api.log, "handlers", [caplog.handler])  # 모듈 로거는 루트로 넘기지 않는다
    client = serve(m4a(np.zeros(SR * 2), tmp_path))
    client.post("/v1/analyses", json=body())
    client.post("/v1/analyses", json=body(bpm=-1))
    serve(RuntimeError(SIGNED_URL)).post("/v1/analyses", json=body())
    assert "session=1 attempt=2 result=NO_SOUND" in caplog.text
    assert "result=ERROR:RuntimeError" in caplog.text  # 예외는 서버 밖으로 나가 메시지째 로그에 남지 않는다
    assert "secret-token" not in caplog.text


def fake_storage(monkeypatch, handler):
    """download()가 쓰는 httpx.stream을 가짜 저장소 응답으로 바꾼다."""
    client = httpx.Client(transport=httpx.MockTransport(handler))
    monkeypatch.setattr(api.httpx, "stream", lambda method, url, **_: client.stream(method, url))


def test_download_writes_recording_without_logging_url(monkeypatch, tmp_path, caplog):
    caplog.set_level(logging.DEBUG)  # 루트 로그 수준을 낮춰도 httpx는 URL을 남기지 않는다
    fake_storage(monkeypatch, lambda request: httpx.Response(200, content=b"m4a bytes"))
    api.download(SIGNED_URL, tmp_path / "r")
    assert (tmp_path / "r").read_bytes() == b"m4a bytes"
    assert "secret-token" not in caplog.text


@pytest.mark.parametrize(
    "handler",
    [
        lambda request: httpx.Response(403, text=f"denied {request.url}"),
        lambda request: httpx.Response(200, content=b"x" * (api.MAX_DOWNLOAD_BYTES + 1)),
        lambda request: (_ for _ in ()).throw(httpx.ConnectError(f"cannot reach {request.url}", request=request)),
    ],
    ids=["expired-url", "too-large", "network"],
)
def test_download_failure_hides_url(monkeypatch, tmp_path, handler):
    fake_storage(monkeypatch, handler)
    with pytest.raises(DownloadError) as error:
        api.download(SIGNED_URL, tmp_path / "r")
    assert "secret-token" not in "".join(traceback.format_exception(error.value))  # 연결된 httpx 예외까지


def test_log_line_is_not_duplicated_through_root():
    assert api.log.propagate is False  # 루트 로그 설정이 있으면 같은 줄이 두 번 찍힌다


def test_recording_too_long_for_dtw_is_500_before_scoring(serve, tmp_path, monkeypatch):
    def must_not_run(*_, **__):
        raise AssertionError("음높이 추정 전에 거절해야 한다")

    monkeypatch.setattr(api, "pyin_f0", must_not_run)
    monkeypatch.setattr(api, "MAX_DTW_BYTES", 1)
    response = serve(m4a(synth(NOTES), tmp_path)).post("/v1/analyses", json=body())
    assert response.status_code == 500
    assert response.json() == {"error": "ANALYSIS_ERROR"}


def test_dtw_limit_allows_two_minutes_against_two_minutes():
    """측정 기록(analysis-latency): 120초 녹음은 제한 시간 안에 든다. 칸당 24바이트로 이 크기는 받아야 한다."""
    frames = 120 / HOP_S
    assert frames * frames * api.DTW_BYTES_PER_CELL <= api.MAX_DTW_BYTES


def test_download_has_overall_deadline(monkeypatch, tmp_path):
    fake_storage(monkeypatch, lambda request: httpx.Response(200, content=b"x" * 10))
    monkeypatch.setattr(api, "DOWNLOAD_TIMEOUT_S", -1.0)  # 이미 지난 마감: 받는 중에 끊어야 한다
    with pytest.raises(DownloadError):
        api.download(SIGNED_URL, tmp_path / "r")


def test_openapi_documents_every_route_and_status():
    spec = app.openapi()
    routes = {(path, method) for path, item in spec["paths"].items() for method in item}
    assert routes == {("/v1/analyses", "post")}
    operation = spec["paths"]["/v1/analyses"]["post"]
    assert operation["summary"] and operation["description"]
    assert set(operation["responses"]) == {"200", "422", "500", "502"}
    assert all(r["content"]["application/json"]["schema"] for r in operation["responses"].values())
    schemas = spec["components"]["schemas"]
    request = schemas["AnalysisIn"]["properties"]
    assert set(request) == {"sessionId", "attempt", "audioUrl", "bpm", "bpmUnit", "notes", "instrument"}
    assert "instrument" not in schemas["AnalysisIn"]["required"]  # 백엔드가 아직 안 보내도 된다
    assert set(schemas["NoteIn"]["properties"]) == {"idx", "semitone", "startBeat", "beats", "sigimsae"}
    assert set(schemas["AnalysisDone"]["required"]) == DONE_KEYS
    assert set(schemas["NoteOut"]["required"]) == NOTE_KEYS
    assert schemas["AnalysisFailed"]["properties"]["failReason"]["enum"] == ["NO_SOUND", "TOO_SHORT", "DECODE_ERROR"]


@pytest.mark.parametrize("path", ["/docs", "/redoc", "/openapi.json"])
def test_documentation_stays_private(path):
    assert TestClient(app).get(path).status_code == 404
