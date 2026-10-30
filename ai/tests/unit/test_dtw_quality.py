import numpy as np
import sys
from unittest.mock import patch

from ai_service.pipelines import scoring
from evaluation.dtw_quality import ROOT, compare, note_groups, synth, summarize_cases, load_recording_case
from evaluation.error_detection import score_notes
from evaluation.dtw_candidates import NoPath


def test_same_f0_preserves_note_ids_and_checkpoint_result():
    notes = [scoring.Note(0, 1, idx=4), scoring.Note(2, 1, idx=8)]
    f0 = np.r_[np.full(50, scoring.HWANG_HZ), np.full(50, scoring.HWANG_HZ * 2 ** (2 / 12))]
    result = compare(f0, notes, 120)
    assert result['reference']['score']['status'] == 'DONE'
    assert [n['idx'] for n in result['reference']['score']['notes']] == [4, 8]
    assert result['A']['score'] == result['reference']['score']
    assert result['A']['path'] == result['reference']['path']


def test_budget_skip_and_no_sound_are_not_note_failures():
    notes = [scoring.Note(0, 1, idx=3)]
    result = compare(np.full(50, scoring.HWANG_HZ), notes, 120, budget=1)
    assert result['reference']['status'] == 'SKIPPED_BUDGET'
    assert result['reference']['score'] is None
    result = compare(np.full(50, np.nan), notes, 120)
    assert all(x['status'] == 'FAILED' and x['score']['fail_reason'] == 'NO_SOUND' for x in result.values())


def test_synthetic_repeated_note_dip_is_inside_new_note():
    notes = [scoring.Note(0, 1, idx=0, start_beat=0),
             scoring.Note(0, 1, idx=1, start_beat=4 + 1 / 3)]
    _, rms = synth(notes, 66, 'normal')
    first_frame = int(np.ceil((4 + 1 / 3) * 60 / 66 / scoring.HOP_S))
    assert rms[first_frame] == 0.1


def test_note_groups_cover_every_id_once():
    notes = [scoring.Note(0, 1, idx=i, start_beat=i) for i in range(91)]
    groups = list(note_groups(notes, 31))
    assert list(map(len, groups)) == [31, 31, 29]
    assert [n.idx for group in groups for n in group] == list(range(91))


def test_normal_score_excerpts_preserve_ids_and_verdicts():
    sys.path.insert(0, str(ROOT / 'data/src/scores'))
    try:
        from score_data import convert_answer
        for page in (171, 172, 173, 174):
            notes = score_notes(convert_answer(f'ngc-standard-2022-p{page}'), 0, 10000)
            notes = [n for n in notes if n.start_beat - notes[0].start_beat + n.beats <= 8]
            bpm = 66 if page == 174 else 96
            f0, rms = synth(notes, bpm, 'normal')
            result = compare(f0, notes, bpm, rms)
            for item in result.values():
                assert item['status'] == 'DONE'
                assert [n['idx'] for n in item['score']['notes']] == [n.idx for n in notes]
                assert all(n['verdict'] == 'CORRECT' for n in item['score']['notes'])
    finally:
        sys.path.pop(0)


def test_synthetic_silence_and_score_rest_have_zero_rms():
    notes = [scoring.Note(0, 1, idx=0, start_beat=0),
             scoring.Note(0, 1, idx=1, start_beat=2),
             scoring.Note(0, 1, idx=2, start_beat=3)]
    f0, rms = synth(notes, 120, 'silence')
    assert np.all(rms[np.isnan(f0)] == 0)
    assert np.any(rms[~np.isnan(f0)] > 0)


def test_candidate_failure_is_separate_and_other_candidates_run():
    notes = [scoring.Note(0, 1, idx=0)]
    with patch('evaluation.dtw_quality.checkpoint_path', side_effect=ValueError('broken')):
        result = compare(np.full(50, scoring.HWANG_HZ), notes, 120)
    assert result['A']['status'] == 'ERROR'
    assert 'ValueError' in result['A']['reason']
    assert result['A']['score'] is None
    assert result['reference']['status'] == result['B0.5']['status'] == 'DONE'


def test_bad_recording_is_recorded_without_note_results(tmp_path):
    result = load_recording_case(tmp_path / 'missing.json')
    assert result['results']['reference']['status'] == 'INPUT_ERROR'
    assert result['results']['reference']['score'] is None
    assert 'FileNotFoundError' in result['results']['reference']['reason']


def test_summary_records_band_membership_changed_notes_and_exclusions():
    score = {'notes': [{'idx': 7, 'verdict': 'CORRECT'}], 'total': 100}
    changed = {'notes': [{'idx': 7, 'verdict': 'MISSED'}], 'total': 0}
    reference = {'status': 'DONE', 'score': score, 'path': [[2, 2], [1, 1], [0, 0]]}
    candidate = {'status': 'DONE', 'score': changed, 'path': [[2, 2], [1, 0], [0, 0]]}
    skipped = {'status': 'SKIPPED_BUDGET', 'score': None, 'path': None}
    report = summarize_cases([{'recordingId': 'one', 'results': {'reference': reference, 'B0.5': candidate}},
                              {'recordingId': 'two', 'results': {'reference': reference, 'B0.5': skipped}}])
    b = report['B0.5']
    assert b['comparisons'][0]['referenceInsideBand'] is True
    assert b['comparisons'][0]['changedNotes'] == [{'idx': 7, 'reference': 'CORRECT', 'candidate': 'MISSED'}]
    assert b['comparisons'][0]['changedMeasurements'][0]['idx'] == 7
    assert b['excluded'][0]['caseId'] == 'two'
    assert b['excluded'][0]['candidateStatus'] == 'SKIPPED_BUDGET'


def test_repeated_recording_id_is_excluded():
    from types import SimpleNamespace
    label = {'recordingId': 'duplicate'}
    with patch('evaluation.dtw_quality.load_trials', return_value=([label], [SimpleNamespace(label=label)])):
        case = load_recording_case(ROOT / 'duplicate.json', {'duplicate'})
    assert case['results']['reference']['status'] == 'INPUT_ERROR'
    assert 'duplicate' in case['results']['reference']['reason']


def test_missing_path_and_note_id_mismatch_are_excluded_separately():
    notes = [scoring.Note(0, 1, idx=0)]
    with patch('evaluation.dtw_quality.band_path', side_effect=NoPath('no route')):
        result = compare(np.full(50, scoring.HWANG_HZ), notes, 120)
    assert result['B0.5']['status'] == 'NO_PATH'
    assert result['B0.5']['score'] is None
    reference = result['reference']
    mismatched = dict(reference, score=dict(reference['score'], notes=[{'idx': 99, 'verdict': 'CORRECT'}]))
    report = summarize_cases([{'recordingId': 'missing-path', 'results': result},
                              {'recordingId': 'different-id', 'results': {'reference': reference, 'A': mismatched}}])
    assert report['B0.5']['excluded'][0]['candidateStatus'] == 'NO_PATH'
    assert report['A']['excluded'][0]['referenceStatus'] == 'NOTE_ID_MISMATCH'


def test_reference_path_outside_band_is_reported():
    score = {'notes': [{'idx': 1, 'verdict': 'CORRECT'}], 'total': 100}
    reference = {'status': 'DONE', 'score': score, 'path': [[2, 102], [1, 0], [0, 0]]}
    candidate = {'status': 'DONE', 'score': score, 'path': [[2, 102], [1, 51], [0, 0]]}
    result = summarize_cases([{'recordingId': 'outside', 'results': {'reference': reference, 'B0.5': candidate}}])
    assert result['B0.5']['comparisons'][0]['referenceInsideBand'] is False
    assert result['B0.5']['comparisons'][0]['pathChanged'] is True
