from evaluation.dtw_full_performance import candidate_estimate, tempos, full_frames, frames_for, load_notes, blocks_later_inputs


def test_candidate_estimates_scale_with_actual_input_dimensions():
    assert candidate_estimate('reference', 100, 200) == 480_000
    assert candidate_estimate('A', 8000, 8000) < candidate_estimate('reference', 8000, 8000)
    assert candidate_estimate('B0.5', 8000, 8000) < candidate_estimate('B2.0', 8000, 8000)


def test_tempos_include_target_middle_and_default_without_duplicates():
    assert tempos(96) == [40, 68, 96]
    assert tempos(66) == [40, 53, 66]
    assert tempos(40) == [40]


def test_whole_score_includes_final_rest():
    data, notes = load_notes(171)
    assert frames_for(notes, 40) == 12_450
    assert full_frames(data, notes, 40) == 12_600


def test_failed_run_blocks_later_larger_inputs_but_budget_skip_does_not():
    assert all(blocks_later_inputs(x) for x in ('ERROR', 'TIMEOUT', 'FAILED', 'PREPARE_ERROR'))
    assert not blocks_later_inputs('SKIPPED_BUDGET')
    assert not blocks_later_inputs('DONE')


def test_runner_stops_failed_candidate_without_stopping_others(tmp_path, monkeypatch):
    import json
    import sys
    from types import SimpleNamespace
    import evaluation.dtw_full_performance as benchmark

    (tmp_path / 'ai').mkdir()
    (tmp_path / 'ai/uv.lock').write_text('lock', encoding='utf-8')
    note = SimpleNamespace(start_beat=0, beats=1)
    data = {'music': {'ticksPerBeat': 1}, 'events': [{'startTick': 0, 'durationTick': 1}]}
    monkeypatch.setattr(benchmark, 'ROOT', tmp_path)
    monkeypatch.setattr(benchmark, 'load_notes', lambda page: (data, [note]))

    def fake_run(command, timeout):
        if '--guard-check' in command:
            return SimpleNamespace(returncode=0, stdout='LIMIT_OK', stderr='')
        if '--check-rss' in command:
            return SimpleNamespace(returncode=0, stdout='{"rssLimit": 1, "peakRss": 1}', stderr='')
        if '-c' in command:
            return None
        if '--prepare' in command:
            return SimpleNamespace(returncode=0, stdout='{"audioHash": "test"}', stderr='')
        method, mode = command[6], command[7]
        if (method, mode) == ('A', 'dtw'):
            return SimpleNamespace(returncode=1, stdout='', stderr='test error')
        return SimpleNamespace(returncode=0, stderr='', stdout=json.dumps(
            {'status': 'DONE', 'seconds': 0.1, 'peakRssBytes': 100}))

    monkeypatch.setattr(benchmark, 'run_process', fake_run)
    out = tmp_path / 'result.json'
    monkeypatch.setattr(sys, 'argv', ['benchmark', '--pages', '171', '--bpm', '96', '--repeat', '2', '--out', str(out)])
    benchmark.main()
    rows = json.loads(out.read_text(encoding='utf-8'))['runs']
    a = [r['status'] for r in rows if r['method'] == 'A' and r['mode'] == 'dtw']
    ref = [r['status'] for r in rows if r['method'] == 'reference' and r['mode'] == 'dtw']
    assert a == ['ERROR', 'SKIPPED_PRIOR_FAILURE']
    assert ref == ['DONE', 'DONE']
