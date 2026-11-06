"""File lifetime checks; no inference models or real waiting."""
from omr_service import temporary_sources as sources


def test_completion_and_failure_remove_originals(monkeypatch):
    timers = []
    class Timer:
        def cancel(self):
            self.cancelled = True
    def schedule(seconds, callback):
        timer = Timer()
        timers.append((seconds, callback, timer))
        return timer
    monkeypatch.setattr(sources, "_schedule", schedule)
    for fail in (False, True):
        try:
            with sources.temporary_sources() as directory:
                (directory / "original.png").write_bytes(b"private")
                if fail:
                    raise RuntimeError("engine failed")
        except RuntimeError:
            pass
        assert not directory.exists()
        assert timers[-1][2].cancelled


def test_watchdog_removes_original_while_engine_is_still_running(monkeypatch):
    calls = []
    class Timer:
        def cancel(self):
            pass
    monkeypatch.setattr(sources, "_schedule", lambda seconds, callback: calls.append((seconds, callback)) or Timer())
    with sources.temporary_sources() as directory:
        (directory / "original.pdf").write_bytes(b"private")
        assert calls[0][0] == 1800
        calls[0][1]()
        assert not directory.exists()


def test_failed_cleanup_is_retried_without_logging_paths(monkeypatch, tmp_path):
    calls = []
    monkeypatch.setattr(sources, "_schedule", lambda seconds, callback: calls.append((seconds, callback)))
    original = sources.shutil.rmtree
    monkeypatch.setattr(sources.shutil, "rmtree", lambda path: (_ for _ in ()).throw(PermissionError()))
    directory = tmp_path / "owned"
    directory.mkdir()
    sources._remove(directory)
    assert calls[0][0] == 1
    monkeypatch.setattr(sources.shutil, "rmtree", original)
    calls[0][1]()
    assert not directory.exists()
