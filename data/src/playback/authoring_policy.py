"""Canonical authoring capability policy shared by preflight and rendering."""
import json
from pathlib import Path

SERVICE_POLICY = json.loads((Path(__file__).parent / "profiles" / "authoring-policy.json").read_text(encoding="utf-8"))
