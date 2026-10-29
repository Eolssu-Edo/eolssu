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

# Exit 2 asks the Pipeline to bootstrap a clone that never checked out a commit.
if ! git rev-parse --verify 'HEAD^{commit}' >/dev/null 2>&1; then
    echo 'No valid HEAD commit; workspace bootstrap required' >&2
    exit 2
fi

git reset --hard HEAD
git clean -fd
