"""Request-owned originals expire even when an inference thread stops making progress."""

import shutil
import tempfile
import threading
from contextlib import contextmanager
from pathlib import Path

RETENTION_SECONDS = 30 * 60


def _schedule(seconds, callback):
    timer = threading.Timer(seconds, callback)
    timer.daemon = True
    timer.start()
    return timer


def _remove(directory):
    try:
        shutil.rmtree(directory)
    except FileNotFoundError:
        pass
    except OSError:
        # Open handles may temporarily prevent deletion. Never retain silently.
        _schedule(1, lambda: _remove(directory))


@contextmanager
def temporary_sources():
    directory = Path(tempfile.mkdtemp(prefix="recognition-"))
    timer = _schedule(RETENTION_SECONDS, lambda: _remove(directory))
    try:
        yield directory
    finally:
        timer.cancel()
        _remove(directory)
