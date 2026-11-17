"""정간보 스캔 격자 평가(evaluation/jeongganbo_grid_eval.py)의 각·정간 지표를 모델 없이 검사한다 (S15P31E102-1077)."""
from evaluation.jeongganbo_grid_eval import geometry

TRUTH = ['임', '남', '-', '청황', '', '청태']  # 3정간 각 둘


def cells(ids, texts):
    return [dict(cellId=i, jg=t, issues=[]) for i, t in zip(ids, texts)]


def test_a_perfect_reading_matches_every_address_and_boundary():
    got = geometry(cells(['c0-j0', 'c0-j1', 'c0-j2', 'c1-j0', 'c1-j1', 'c1-j2'],
                         [':0 임', ':0 남', ':0 -', ':0 청황', '', ':0 청태']), TRUTH, 3)
    assert (got['content'], got['address'], got['gaks'], got['truthGaks']) == (6, 6, 2, 2)
    assert (got['boundaryRecall'], got['extraBoundaries'], got['failed']) == (1.0, 0, False)


def test_one_missing_cell_numbered_in_reading_order_shifts_the_next_boundary():
    # 칸 하나(남)를 놓친 채 순번으로 3칸씩 끊으면 둘째 각의 시작이 청황이 아니라 공백 칸이 된다.
    got = geometry(cells(['c0-j0', 'c0-j1', 'c0-j2', 'c1-j0', 'c1-j1'],
                         [':0 임', ':0 -', ':0 청황', '', ':0 청태']), TRUTH, 3)
    assert got['content'] == 5
    assert (got['boundaryRecall'], got['extraBoundaries']) == (0.5, 1)
    assert got['address'] == 1


def test_one_missing_cell_numbered_by_grid_address_keeps_every_boundary():
    got = geometry(cells(['c0-j0', 'c0-j2', 'c1-j0', 'c1-j1', 'c1-j2'],
                         [':0 임', ':0 -', ':0 청황', '', ':0 청태']), TRUTH, 3)
    assert (got['boundaryRecall'], got['extraBoundaries'], got['address']) == (1.0, 0, 5)


def test_a_column_read_as_one_long_gak_misses_the_inner_boundaries():
    got = geometry(cells([f'c0-j{j}' for j in range(6)], [':0 임', ':0 남', ':0 -', ':0 청황', '', ':0 청태']), TRUTH, 3)
    assert (got['gaks'], got['boundaryRecall'], got['address']) == (1, 0.5, 3)


def test_flags_and_failures_are_counted():
    flagged = cells(['c0-j0'], [':0 임'])
    flagged[0]['issues'] = [dict(kind='LOW_CONFIDENCE', reason='MODEL_CONFIDENCE')]
    assert geometry(flagged, TRUTH, 3)['flagged'] == 1
    empty = geometry([], TRUTH, 3)
    assert (empty['failed'], empty['boundaryRecall'], empty['content']) == (True, 0.0, 0)
