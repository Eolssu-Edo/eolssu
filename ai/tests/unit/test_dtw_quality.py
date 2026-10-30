import numpy as np
import sys

from ai_service.pipelines import scoring
from evaluation.dtw_quality import ROOT, compare, note_groups, synth
from evaluation.error_detection import score_notes


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
