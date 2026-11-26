#!/usr/bin/env bash
set -euo pipefail
: "${REVISION:?develop commit required}"
[[ "$REVISION" =~ ^[0-9a-f]{40}$ ]] || { echo 'Use a full lowercase commit SHA' >&2; exit 1; }
git merge-base --is-ancestor "$REVISION" "$GITHUB_SHA" ||
  { echo 'Revision is outside the selected develop history' >&2; exit 1; }

if [[ "${1:-}" == require-ci ]]; then
  result=$(gh run list --workflow ci.yml --commit "$REVISION" --branch develop --limit 50 \
    --json event,conclusion --jq '[.[] | select(.event == "push" or .event == "workflow_dispatch")][0].conclusion // ""')
  [[ "$result" == success ]] ||
    { echo 'The latest CI run for this exact develop commit must succeed' >&2; exit 1; }
fi
