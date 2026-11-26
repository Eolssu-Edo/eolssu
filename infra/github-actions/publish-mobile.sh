#!/usr/bin/env bash
set -euo pipefail
: "${GHCR_MOBILE_USER:?Private mobile package account required}"
: "${GHCR_MOBILE_TOKEN:?Private mobile package credential required}"
registry=ghcr.io
package="$registry/team-edo/eolssu-mobile-$MODE"
target="$package:$REVISION-$GITHUB_RUN_NUMBER-$GITHUB_RUN_ATTEMPT"
auth=$(mktemp -d)
trap 'rm -rf "$auth"' EXIT
config="$auth/config.json"
printf '%s' "$GHCR_MOBILE_TOKEN" | oras login --registry-config "$config" "$registry" \
  -u "$GHCR_MOBILE_USER" --password-stdin
unset GHCR_MOBILE_TOKEN
cd mobile-out
oras push --registry-config "$config" --artifact-type application/vnd.eolssu.mobile.v1 \
  --annotation "org.opencontainers.image.revision=$REVISION" "$target" ./*
digest=$(oras manifest fetch --registry-config "$config" --descriptor "$target" | jq -er .digest)
[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
printf 'Commit: %s\n\nPrivate package: %s@%s\n' "$REVISION" "$package" "$digest" | tee -a "$GITHUB_STEP_SUMMARY"
