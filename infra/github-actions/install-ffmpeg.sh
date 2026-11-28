#!/usr/bin/env bash
set -euo pipefail
if ! command -v ffmpeg >/dev/null || ! command -v ffprobe >/dev/null; then
  sudo apt-get update -qq
  sudo apt-get install -y --no-install-recommends ffmpeg
fi
