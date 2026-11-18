"""배포 예열(ai_service.warmup)과 /health가 실제 채점 경로를 거쳐 정상 결과를 내는지 본다. ffmpeg가 PATH에 있어야 한다."""

import json

from fastapi.testclient import TestClient

from ai_service import warmup
from ai_service.api import MODEL_VERSION, app


def test_health_reports_model_version_without_openapi():
    client = TestClient(app)
    assert client.get("/health").json() == {"status": "ok", "modelVersion": MODEL_VERSION}
    assert client.get("/openapi.json").status_code == 404


def test_warmup_scores_synthetic_tone(capsys):
    assert warmup.main() == 0
    report = json.loads(capsys.readouterr().out)
    assert report["status"] == "ok"
    assert report["result"] == "DONE"
    assert report["verdicts"] == ["CORRECT"]


def test_warmup_reports_error_type_only(monkeypatch, capsys):
    def broken(path):
        raise FileNotFoundError("/secret/path")

    monkeypatch.setattr(warmup, "decode_recording", broken)
    assert warmup.main() == 1
    output = capsys.readouterr().out
    assert json.loads(output) == {"status": "error", "error": "FileNotFoundError"}
    assert "secret" not in output
