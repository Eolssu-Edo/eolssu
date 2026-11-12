"""The structural JGB intermediate must never replace original sounding pitches."""
from omr_service.staff_engine import restore_source_pitches


def test_outside_notation_retains_midi_and_marks_only_its_origin_slot():
    score = {"music": {}, "events": [{"id": "c0-j0-r0-u0", "kind": "NOTE", "yul": "황", "octave": 2}],
             "columns": [{"cells": [{"units": [{"eventId": "c0-j0-r0-u0", "symbol": "중청황"},
                                                    {"eventId": "c0-j0-r0-u0", "symbol": "-"}]}]}]}
    restore_source_pitches(score, [{"eventId": "c0-j0-r0-u0", "midi": 99}], None)
    assert (score['events'][0]['yul'], score['events'][0]['octave']) == ('황', 3)
    assert [u['symbol'] for u in score['columns'][0]['cells'][0]['units']] == ['', '-']
    assert score['music']['hwangHz'] == 440 * 2 ** ((63 - 69) / 12)
