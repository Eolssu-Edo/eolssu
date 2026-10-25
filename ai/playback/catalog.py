"""Local instrument choices; score interpretation stays independent of timbre."""
import json
from pathlib import Path
from typing import Literal

from .score import PROFILES

Instrument = Literal["danso", "jeongak-daegeum", "sanjo-daegeum", "sogeum"]
INSTRUMENTS = ("danso", "jeongak-daegeum", "sanjo-daegeum", "sogeum")
LABELS = dict(zip(INSTRUMENTS, ("단소", "정악대금", "산조대금", "소금")))
DAEGEUM_NOTICE = "현재 CAT 정악대금·산조대금은 공통 음역에서 같은 샘플을 사용해 소리가 같을 수 있습니다."


def instrument_catalog(assets_path):
    path = Path(assets_path)
    try:
        assets = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(assets, dict):
            assets = {}
    except (OSError, ValueError):
        assets = {}
    choices = []
    for key in INSTRUMENTS:
        profile = PROFILES.get(key)
        value = assets.get(key)
        archive = Path(value) if isinstance(value, str) and value else None
        if archive is not None and not archive.is_absolute():
            archive = path.resolve().parent / archive
        available = profile is not None and archive is not None and archive.is_file()
        message = DAEGEUM_NOTICE if key.endswith("daegeum") else ""
        if not available:
            message = "이 악기의 로컬 프로필·CAT 파일 설정이 필요합니다."
        choices.append({"id": key, "label": LABELS[key], "available": available,
                        "message": message, "range": profile.get("technical_range") if profile else None})
    return choices


def selected_instrument(job, requested=None):
    key = requested if requested is not None else job.get("instrument", "danso")
    if key not in INSTRUMENTS or key not in PROFILES:
        raise ValueError("지원하지 않는 악기입니다")
    return key
