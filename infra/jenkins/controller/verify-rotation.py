#!/usr/bin/env python3
"""Capture and compare the Jenkins controller settings used during log rotation."""

import argparse
import copy
import json
import re
import sys


HOME = "/var/lib/jenkins"
IMAGE_VOLUME = "/var/jenkins_home"
ROTATION = {"max-size": "10m", "max-file": "3"}
MISSING = object()


def load(path):
    with open(path, encoding="utf-8") as file:
        return json.load(file)


def capture(inspected, security_options):
    [container] = inspected
    if not container["State"]["Running"]:
        raise ValueError("controller is not running")
    config = container["Config"]
    host = container["HostConfig"]
    if not isinstance(security_options, list) or not all(isinstance(v, str) for v in security_options):
        raise ValueError("Docker security options unavailable")
    logging = host["LogConfig"]
    if logging["Type"] != "json-file" or any(
        key not in ROTATION or not isinstance(value, str) or not re.fullmatch(r"[0-9]+[kKmMgG]?", value)
        for key, value in logging["Config"].items()
    ):
        raise ValueError("unexpected Docker log driver or options")
    mounts = {}
    for mount in container["Mounts"]:
        target = mount["Destination"]
        if target in mounts:
            raise ValueError("duplicate Docker mount destination")
        mounts[target] = {
            key: mount.get(key) for key in ("Type", "Name", "Source", "Driver", "Mode", "RW", "Propagation")
        }
    return {
        "container_id": container["Id"],
        "image": {"id": container["Image"], "reference": config["Image"]},
        "user": config["User"],
        "jenkins_home": HOME if f"JENKINS_HOME={HOME}" in config["Env"] else None,
        "mounts": mounts,
        "declared_default_volume": IMAGE_VOLUME in (config["Volumes"] or {}),
        "ports": host["PortBindings"],
        "live_ports": container["NetworkSettings"]["Ports"],
        "network_mode": host["NetworkMode"],
        "networks": {
            name: network["NetworkID"] for name, network in container["NetworkSettings"]["Networks"].items()
        },
        "restart": host["RestartPolicy"],
        "stop_timeout": config["StopTimeout"],
        "security": {key: host[key] for key in ("SecurityOpt", "Privileged", "CapAdd", "CapDrop", "ReadonlyRootfs")},
        "selinux": any(option.partition(",")[0] == "name=selinux" for option in security_options),
        "logging": logging,
    }


def differences(before, after, path=()):
    if isinstance(before, dict) and isinstance(after, dict):
        for key in sorted(before.keys() | after.keys()):
            yield from differences(before.get(key, MISSING), after.get(key, MISSING), (*path, key))
    elif before != after:
        yield path, before, after


def change(path, before, after):
    return {
        "field": list(path),
        "before": {"missing": True} if before is MISSING else before,
        "after": {"missing": True} if after is MISSING else after,
    }


def inherited_volume_mode(before, after):
    # Compose can pass the same image-created volume back as an explicit mount.
    old_mount = before["mounts"].get(IMAGE_VOLUME, {})
    new_mount = after["mounts"].get(IMAGE_VOLUME, {})
    old_home = before["mounts"].get(HOME, {})
    new_home = after["mounts"].get(HOME, {})
    return (
        before["selinux"] is False
        and after["selinux"] is False
        and before["jenkins_home"] == after["jenkins_home"] == HOME
        and old_home == new_home
        and old_home.get("Type") == "bind"
        and before["declared_default_volume"] is True
        and after["declared_default_volume"] is True
        and old_mount.get("Type") == new_mount.get("Type") == "volume"
        and old_mount.get("Driver") == "local"
        and re.fullmatch(r"[0-9a-f]{64}", old_mount.get("Name") or "") is not None
        and old_mount.get("Mode") == ""
        and new_mount.get("Mode") == "z"
        and {**old_mount, "Mode": "z"} == new_mount
    )


def compare(before, after, phase):
    expected = copy.deepcopy(before)
    if phase == "apply":
        expected["logging"] = {"Type": "json-file", "Config": ROTATION}
    expected["container_id"] = after["container_id"]
    observed = [change(*diff) for diff in differences(before, after)]
    unexpected = []
    for path, left, right in differences(expected, after):
        if path == ("mounts", IMAGE_VOLUME, "Mode") and inherited_volume_mode(before, after):
            continue
        diff = change(path, left, right)
        unexpected.append({"field": diff["field"], "expected": diff["before"], "actual": diff["after"]})
    if phase == "apply" and before["container_id"] == after["container_id"]:
        unexpected.append({"field": ["container_id"], "problem": "controller was not recreated"})
    return {"phase": phase, "observed": observed, "unexpected": unexpected}


def compare_compose(before, after):
    expected = copy.deepcopy(before)
    logging = expected["services"]["controller"].get("logging", MISSING)
    expected["services"]["controller"]["logging"] = {"driver": "json-file", "options": ROTATION}
    actual = after["services"]["controller"].get("logging", MISSING)
    log_path = ("services", "controller", "logging")
    unexpected = [
        {"field": list(path)} for path, _, _ in differences(expected, after)
    ]
    if logging == actual:
        unexpected.append({"field": list(log_path), "problem": "logging did not change"})
    # Rendered Compose can contain secrets. Record changed paths, not their values.
    observed = [{"field": list(path)} for path, _, _ in differences(before, after)]
    return {"phase": "compose", "observed": observed, "unexpected": unexpected}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    snapshot = commands.add_parser("capture", help="read a Docker inspect array from stdin")
    snapshot.add_argument("--security-options", required=True, help="Docker info SecurityOptions JSON")
    compare_cmd = commands.add_parser("compare", help="compare captured runtime settings")
    compare_cmd.add_argument("phase", choices=("apply", "rollback"))
    compare_cmd.add_argument("before")
    compare_cmd.add_argument("after")
    compose_cmd = commands.add_parser("compose", help="verify the rendered candidate changes only logging")
    compose_cmd.add_argument("before")
    compose_cmd.add_argument("after")
    args = parser.parse_args()
    try:
        if args.command == "capture":
            result = capture(json.load(sys.stdin), json.loads(args.security_options))
        elif args.command == "compose":
            result = compare_compose(load(args.before), load(args.after))
        else:
            result = compare(load(args.before), load(args.after), args.phase)
    except (AttributeError, KeyError, TypeError, ValueError, OSError):
        print("Invalid or incomplete input for Jenkins rotation verification", file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 1 if result.get("unexpected") else 0


if __name__ == "__main__":
    sys.exit(main())
