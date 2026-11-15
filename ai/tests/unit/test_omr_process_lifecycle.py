"""Subprocess completion must release the request's descendant processes."""
from concurrent.futures import ThreadPoolExecutor
import json
import os
import socket
import sys
import threading
from time import monotonic

import pytest

from omr_service.jeongganbo_text import _run


@pytest.mark.skipif(os.name != 'posix', reason='Linux OMR process groups')
@pytest.mark.parametrize('mode', ['exit', 'timeout'])
def test_renderer_exit_or_timeout_stops_descendants_and_allows_next_command(tmp_path, mode):
    marker, release = tmp_path / 'ready.json', tmp_path / 'release'
    descendant = '''import json, pathlib, socket, sys
listener = socket.socket()
listener.bind(('127.0.0.1', 0))
listener.listen()
marker = pathlib.Path(sys.argv[1])
pending = marker.with_suffix('.tmp')
pending.write_text(json.dumps({'port': listener.getsockname()[1]}))
pending.replace(marker)
while True:
    connection, _ = listener.accept()
    with connection:
        if connection.recv(1) == b'X': break
        connection.sendall(b'alive')
'''
    parent = f'''import pathlib, subprocess, sys, threading
subprocess.Popen([sys.executable, '-c', {descendant!r}, {str(marker)!r}],
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
while not pathlib.Path({str(release)!r}).exists(): threading.Event().wait(.005)
if {mode!r} == 'exit': raise SystemExit(7)
threading.Event().wait()
'''
    port = None
    try:
        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(_run, [sys.executable, '-c', parent], monotonic() + 1)
            deadline = monotonic() + .8
            while not marker.exists() and monotonic() < deadline:
                threading.Event().wait(.005)
            assert marker.exists(), 'descendant readiness was not observed'
            port = json.loads(marker.read_text())['port']
            with socket.create_connection(('127.0.0.1', port), timeout=.2) as observer:
                observer.sendall(b'?')
                assert observer.recv(5) == b'alive'
            release.touch()
            if mode == 'timeout':
                with pytest.raises(TimeoutError):
                    future.result(timeout=2)
            else:
                assert future.result(timeout=2).returncode == 7
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=.2) as observer:
                    observer.sendall(b'?')
                    alive = observer.recv(5) == b'alive'
            except OSError:
                alive = False
            assert not alive, 'descendant survived the completed command'
        recovered = _run([sys.executable, '-c', 'import sys; print("ok"); print("note", file=sys.stderr)'], monotonic() + 2)
        assert (recovered.returncode, recovered.stdout, recovered.stderr) == (0, b'ok\n', b'note\n')
    finally:
        release.touch()
        if port is not None:
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=.2) as observer:
                    observer.sendall(b'X')
            except OSError:
                pass
