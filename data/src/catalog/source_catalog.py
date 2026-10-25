"""Read source catalogs and expand local, nonrecursive metadata profiles."""

from copy import deepcopy
import json


# Identity, paths, hashes, sizes and measurements belong to each original.
PROFILE_FIELDS = frozenset({
    "kind", "sourceUrl", "downloadUrl", "publisher", "license", "attribution",
    "verifiedOn", "redistribution", "instrument", "instrumentCode", "channels",
    "category", "collection", "sourceVerified", "serviceDatasetApproved", "notes",
})


def load_json(path):
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_keys)


def load_source_catalog(path):
    """Return independent full source objects; consumers validate their own fields."""
    document = load_json(path)
    if not isinstance(document, dict):
        raise ValueError("Expected a source catalog object")
    version = document.get("schemaVersion")
    if type(version) is not int or version not in (2, 3):
        raise ValueError("Source schemaVersion must be 2 or 3")
    sources = document.get("sources")
    if not isinstance(sources, list) or any(not isinstance(s, dict) for s in sources):
        raise ValueError("Expected a sources list of objects")
    if version == 2:
        if "sourceProfiles" in document or any("profile" in s for s in sources):
            raise ValueError("Source profiles require schemaVersion 3")
        return document

    profiles = document.get("sourceProfiles")
    if not isinstance(profiles, dict):
        raise ValueError("Expected a sourceProfiles object")
    for key, profile in profiles.items():
        if not isinstance(key, str) or not key.strip():
            raise ValueError("Expected a nonempty source profile name")
        if not isinstance(profile, dict) or set(profile) - PROFILE_FIELDS:
            raise ValueError(f"Invalid source profile fields: {key}")
    expanded = []
    for source in sources:
        common = {}
        if "profile" in source:
            reference = source["profile"]
            if not isinstance(reference, str) or not reference.strip() or reference not in profiles:
                raise ValueError(f"Unknown or invalid source profile: {reference!r}")
            common = profiles[reference]
        # Shallow precedence, deep ownership: null and whole nested objects win.
        expanded.append(deepcopy({**common, **{k: v for k, v in source.items() if k != "profile"}}))
    return {**{k: v for k, v in document.items() if k != "sourceProfiles"}, "sources": expanded}
