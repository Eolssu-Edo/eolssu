#!/bin/sh
set -eu
case "${SSH_ORIGINAL_COMMAND-}" in
    server|containers|health)
        exec sudo -n /usr/bin/timeout --signal=TERM --kill-after=5s 80s /usr/bin/python3 -I /opt/edo-check/collector.py "$SSH_ORIGINAL_COMMAND" edo2
        ;;
    *)
        printf '%s\n' 'unsupported inspection mode' >&2
        exit 64
        ;;
esac
