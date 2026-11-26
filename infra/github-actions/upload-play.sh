#!/usr/bin/env bash
set -euo pipefail
: "${PLAY_CREDENTIALS:?Google Play service account required}"
case "$PLAY_TARGET" in
  internal) track=internal; status=draft ;;
  production-draft) track=production; status=draft ;;
  production-completed) track=production; status=completed ;;
  *) echo 'Select a Google Play upload target' >&2; exit 1 ;;
esac
notes=$(printf '%s' "$PLAY_NOTES" | python3 -c 'import sys; print(sys.stdin.read().strip())')
[[ ${#notes} -le 500 ]] || { echo 'Google Play notes exceed 500 characters' >&2; exit 1; }
umask 077
credentials=$(mktemp "$RUNNER_TEMP/play-service-account.XXXXXX")
trap 'rm -f "$credentials"' EXIT
printf '%s' "$PLAY_CREDENTIALS" > "$credentials"
unset PLAY_CREDENTIALS
printf '%s' "$notes" > "$RUNNER_TEMP/play-notes-ko.txt"
command=(python3 -B infra/jenkins/play-upload.py)
auth=(--credentials "$credentials" --package com.ssafy.eolssu --version-code "$VERSION_CODE")
"${command[@]}" check "${auth[@]}"
"${command[@]}" upload "${auth[@]}" --aab "mobile-out/eolssu-$VERSION_NAME-vc$VERSION_CODE.aab" \
  --version-name "$VERSION_NAME" --track "$track" --status "$status" --notes-file "$RUNNER_TEMP/play-notes-ko.txt"
