"""확인 필요 칸 측정(evaluation/jeongganbo_scan_review.py)의 정답 정렬·판정·집계를 모델 없이 검사한다."""
from evaluation.jeongganbo_scan_review import align, by_threshold, labelled, pitch_rows, tally, token_count, truth_cells


def test_truth_cells_reads_both_answer_layouts_in_reading_order():
    assert truth_cells({'parts': [{'melody': '청임|청임 - 청태\n청태{풀어내림표} - 청임|\n'}]}) == \
        ['청임', '청임 - 청태', '청태{풀어내림표} - 청임', '']
    assert truth_cells({'melody': '청태 | - - 청황 | 무<'}) == ['청태', '- - 청황', '무<']


def test_pitch_rows_ignores_ornaments_and_breath_and_treats_blank_as_hold():
    assert pitch_rows('청태{농음표} - 청임<') == pitch_rows('청태 - 청임') == [['청태'], ['-'], ['청임']]
    assert pitch_rows('') == pitch_rows('-') == []
    assert pitch_rows('쉼') == [['쉼표']]
    assert pitch_rows('청황청태') != pitch_rows('청황 청태')  # 반칸 둘과 두 줄은 칸 모양이 다르다


def test_align_keeps_order_and_reports_extra_and_missing_cells():
    a, b, c, x = [['a']], [['b']], [['c']], [['x']]
    assert align([a, b, c], [a, b, c]) == [(0, 0), (1, 1), (2, 2)]
    assert align([x, x, a, b], [a, b]) == [(0, None), (1, None), (2, 0), (3, 1)]
    assert align([a, c], [a, b, c]) == [(0, 0), (None, 1), (1, 2)]
    assert align([a, x, c], [a, b, c]) == [(0, 0), (1, 1), (2, 2)]  # 다른 칸은 짝을 지어 틀린 칸으로 센다


def test_token_count_counts_pitch_position_and_ornament_tokens():
    assert token_count('청태:5') == 2
    assert token_count('청태_흘림표:5 -:8') == 5
    assert token_count('') == 1


def song(cells, truth, instrument='DANSO'):
    return dict(answer='ngc-silgi-2016-test', instrument=instrument, truth=truth, cells=[
        dict(cellId=f'c0-j{i}', text=t, jg=jg, conf=conf, filled=None, ink=.1, issues=issues)
        for i, (t, jg, conf, issues) in enumerate(cells)])


def test_labelled_marks_wrong_extra_and_missing_cells():
    rows, missing = labelled(song([
        ('쉼표:5', ':0 쉼표', .99, []),              # 정답에 없는 칸(가사·장단 열)
        ('청임:5', ':0 청임', .9, []),
        ('청태_흘림표:5', ':0 청태 흘림표', .3, [dict(kind='LOW_CONFIDENCE', reason='MODEL_CONFIDENCE')]),
        ('청황:5', ':0 청황', .95, []),
    ], ['청임', '청태{풀어내림표}', '중청황', '청무']))
    assert [(r['extra'], r['wrong']) for r in rows] == [(True, True), (False, False), (False, False), (False, True)]
    assert missing == 1


def test_tally_counts_missed_errors_and_review_burden_per_rule():
    rows, _ = labelled(song([
        ('청임:5', ':0 청임', .9, []),
        ('청태:5', ':0 청태', .5, [dict(kind='LOW_CONFIDENCE', reason='MODEL_CONFIDENCE')]),
        ('청황:5', ':0 청황', .95, []),
        ('니나*:5', '', .99, [dict(kind='UNREADABLE', reason='UNSUPPORTED_SYMBOL')]),
    ], ['청임', '청태', '중청황', '청무']))
    low = tally(rows, by_threshold(.8))
    assert (low['wrong'], low['flagged'], low['caught'], low['missed'], low['false_alarm']) == (2, 2, 1, 1, 1)
    pairs = tally(rows, by_threshold(.8, pairs=('황', '태')))
    assert (pairs['flagged'], pairs['missed']) == (3, 0)
    # 소금도 청황·중청황이 모두 후보다. 율명 후보 목록이 없는 악기는 옥타브 쌍 표시를 하지 않는다.
    no_preset, _ = labelled(song([('청황:5', ':0 청황', .95, [])], ['중청황'], instrument='SANJO_DAEGEUM'))
    assert tally(no_preset, by_threshold(.8, pairs=('황', '태')))['flagged'] == 0
