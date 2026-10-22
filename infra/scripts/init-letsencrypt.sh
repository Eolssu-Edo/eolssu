#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ "${1:-}" = --help ] || [ "${1:-}" = -h ]; then
    exec python3 "$script_dir/init-letsencrypt.py" --help
fi
if [ "$(id -u)" -ne 0 ]; then
    exec sudo -- "$script_dir/init-letsencrypt.sh" "$@"
fi
exec python3 -I "$script_dir/init-letsencrypt.py" "$@"
