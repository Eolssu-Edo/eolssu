#!/usr/bin/env bash
# Restore source while retaining Git-ignored Gradle caches and task outputs.
set -euo pipefail

workspace=$(pwd -P)
if [[ ! -e .git ]]; then
    echo 'Run workspace preparation from the repository root' >&2
    exit 1
fi
repository=$(git rev-parse --show-toplevel)
repository=$(cd "$repository" && pwd -P)
if [[ "$workspace" != "$repository" ]]; then
    echo 'Refusing to prepare a directory other than the repository root' >&2
    exit 1
fi

git reset --hard HEAD
git clean -fd
