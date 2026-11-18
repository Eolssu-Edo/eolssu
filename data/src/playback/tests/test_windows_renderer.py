"""Real Windows launch/cleanup checks; no renderer assets or external service."""
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from data.src.playback._windows_renderer import run_renderer


@unittest.skipUnless(os.name == 'nt', 'Windows process/job handles')
class WindowsRendererTests(unittest.TestCase):
    def _handle_count(self):
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.GetCurrentProcess.restype = wintypes.HANDLE
        kernel.GetProcessHandleCount.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
        count = wintypes.DWORD()
        self.assertTrue(kernel.GetProcessHandleCount(kernel.GetCurrentProcess(), ctypes.byref(count)))
        return count.value

    def test_preserves_interpreter_environment_and_quoted_unicode_arguments(self):
        with tempfile.TemporaryDirectory() as fixture:
            output = Path(fixture) / '실행 정보.json'
            arguments = ['공백 포함', 'quote"inside', 'trailing\\']
            script = '''import ctypes, json, pathlib, sys
executable = ctypes.create_unicode_buffer(32768)
ctypes.windll.kernel32.GetModuleFileNameW(None, executable, len(executable))
pathlib.Path(sys.argv[1]).write_text(json.dumps([sys.executable, sys.prefix, sys.base_prefix,
    executable.value, sys.argv[2:]]), encoding='utf-8')
'''
            self.assertEqual(run_renderer([sys.executable, '-c', script, str(output), *arguments], 5), 0)
            executable = ctypes.create_unicode_buffer(32768)
            ctypes.windll.kernel32.GetModuleFileNameW(None, executable, len(executable))
            self.assertEqual(json.loads(output.read_text(encoding='utf-8')),
                             [sys.executable, sys.prefix, sys.base_prefix, executable.value, arguments])

    def test_success_failure_timeout_and_launch_error_release_handles(self):
        # Warm interpreter/ctypes state before measuring native handles.
        self.assertEqual(run_renderer([sys.executable, '-c', 'pass'], 5), 0)
        before = self._handle_count()
        with tempfile.TemporaryDirectory() as fixture:
            for _ in range(4):
                self.assertEqual(run_renderer([sys.executable, '-c', 'pass'], 5), 0)
                self.assertEqual(run_renderer([sys.executable, '-c', 'raise SystemExit(7)'], 5), 7)
                with self.assertRaises(subprocess.TimeoutExpired):
                    run_renderer([sys.executable, '-c', 'import threading; threading.Event().wait()'], .2)
                with self.assertRaises(OSError):
                    run_renderer([str(Path(fixture) / 'missing.exe')], 5)
        self.assertEqual(self._handle_count(), before)
        self.assertEqual(run_renderer([sys.executable, '-c', 'pass'], 5), 0)


if __name__ == '__main__':
    unittest.main()
