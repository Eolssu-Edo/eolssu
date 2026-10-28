"""Explicit JGB provenance and audition choices, never inferred instrument style."""
from copy import deepcopy
from .score import PROFILES, finite, reject_unknown, require


def declared_instrument(document, voice, instrument):
    require(instrument in PROFILES, "지원하지 않는 악기 프로필입니다")
    metadata = document.get("playback", {})
    require(isinstance(metadata, dict), "playback은 객체여야 합니다")
    declared = voice.get("instrument")
    override = metadata.get("instrument")
    if override is not None:
        require(override == instrument, "playback.instrument와 선택 악기가 다릅니다")
    # Preserve the legacy Danso importer only. Missing/all/daegeum is not proof
    # of another instrument or a Daegeum subtype.
    if declared is None and instrument == "danso" and override is None:
        return "Legacy Danso input; instrument omitted."
    if declared == instrument:
        basis = metadata.get("instrument_basis")
        return basis if isinstance(basis, str) and basis.strip() else f"JGB part instrument: {declared}"
    require(declared in (None, "all", "daegeum"), "선택한 성부의 악기와 요청한 악기가 다릅니다")
    require(declared != "daegeum" or instrument in ("jeongak-daegeum", "sanjo-daegeum"),
            "대금 성부를 다른 악기로 바꿀 수 없습니다")
    basis = metadata.get("instrument_basis")
    require(override == instrument and isinstance(basis, str) and basis.strip(),
            "악기/대금 기종이 미지정입니다. 판본 근거와 playback.instrument·instrument_basis가 필요합니다")
    return basis


def expression_policy(document, instrument, danso_default):
    policy = document.get("playback", {}).get("expression_policy")
    if policy is None:
        return deepcopy(danso_default) if instrument == "danso" else None
    reject_unknown(policy, ["name", "basis", "status", "vibrato", "fall"], "expression_policy")
    require(all(isinstance(policy.get(k), str) and policy[k].strip() for k in ("name", "basis")),
            "표현 정책의 name·basis가 필요합니다")
    require(policy.get("status") == "audition-policy-not-performance-standard",
            "표현 수치는 연주 표준이 아닌 청취용 정책으로 명시해야 합니다")
    if "vibrato" in policy:
        value = policy["vibrato"]
        keys = ["mode", "hz", "cents", "depth", "delay_seconds"]
        reject_unknown(value, keys, "vibrato")
        require(set(value) == set(keys), "농음 mode·hz·cents·depth·delay_seconds를 모두 명시하세요")
        require(value["mode"] in ("amplitude", "pitch", "mixed"), "농음 mode가 잘못되었습니다")
        for key, low, high in [("hz", .1, 10), ("cents", 0, 100), ("depth", 0, .5), ("delay_seconds", 0, 20)]:
            finite(value[key], f"vibrato.{key}", low, high)
    if "fall" in policy:
        value = policy["fall"]
        reject_unknown(value, ["end_cents", "tail_fraction", "maximum_seconds"], "fall")
        for key, low, high in [("end_cents", -150, 0), ("tail_fraction", .01, 1), ("maximum_seconds", .005, 1)]:
            finite(value.get(key), f"fall.{key}", low, high)
    return deepcopy(policy)


def relative_scale(document):
    value = document.get("playback", {}).get("relative_scale")
    if value is None:
        return None
    reject_unknown(value, ["pitch_classes", "basis"], "relative_scale")
    classes = value.get("pitch_classes")
    require(isinstance(classes, list) and 2 <= len(classes) <= 12 and
            all(type(c) is int and 0 <= c < 12 for c in classes) and classes == sorted(set(classes)),
            "relative_scale.pitch_classes는 황종 기준 0..11의 오름차순 중복 없는 배열이어야 합니다")
    require(isinstance(value.get("basis"), str) and value["basis"].strip(), "상대음 음계의 basis가 필요합니다")
    return deepcopy(value)
