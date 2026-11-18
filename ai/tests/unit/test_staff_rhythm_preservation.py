"""Preserve source onsets, bar boundaries and numeric tempi in real score conversion."""
from fractions import Fraction
from types import SimpleNamespace
import pytest
import io
import zipfile

from omr_service import staff_engine, staff_musicxml
from test_staff_musicxml import xml, measure, note
from test_staff_tempo import tempo


def recognize(monkeypatch, document, jangdan=None, issues=None):
    monkeypatch.setattr(staff_engine, '_read_pages', lambda *_: ([document], issues or []))
    request = SimpleNamespace(instrument='DANSO', hwang_pitch=60,
                              conversion_id='rhythm-883', jangdan=jangdan)
    return staff_engine.recognize(SimpleNamespace(request=request, time_budget_s=20))


def quarter_events(score):
    music = score['music']
    beat = {'QUARTER': Fraction(1), 'DOTTED_QUARTER': Fraction(3, 2)}[music['bpmUnit']]
    return [(e['kind'], Fraction(e['startTick'], music['ticksPerBeat']) * beat,
             Fraction(e['durationTick'], music['ticksPerBeat']) * beat) for e in score['events']]


def test_cross_bar_tie_keeps_one_note_and_links_the_new_column(monkeypatch):
    result = recognize(monkeypatch, xml(
        measure(note('C4', 16, '<tie type="start"/>'), time=(4, 4)),
        measure(note('C4', 16, '<tie type="stop"/>'))))
    score = result['score']
    assert quarter_events(score) == [('NOTE', 0, 8)]
    first = score['events'][0]['id']
    assert score['columns'][1]['cells'][0]['units'] == [
        {'row': 0, 'col': 0, 'symbol': '-', 'eventId': first}]
    assert len(result['sourcePitches']) == 1


def test_mixed_meter_keeps_each_bar_and_numeric_tempo_location(monkeypatch):
    document = xml(
        measure(tempo('120'), note('C4', 12), time=(3, 4)),
        measure(tempo('60 molto accel.'), note('D4', 8), time=(2, 4)),
        measure(tempo('120'), note('E4', 12), time=(3, 4)))
    conversion = staff_musicxml.convert(document, instrument='DANSO', hwang_pitch=60)
    assert conversion.source_meters == ['3/4', '2/4', '3/4']
    assert conversion.bpm_unit == 'QUARTER'
    result = recognize(monkeypatch, document)
    score = result['score']
    assert [len(c['cells']) for c in score['columns']] == [3, 2, 3]
    assert quarter_events(score) == [('NOTE', 0, 3), ('NOTE', 3, 2), ('NOTE', 5, 3)]
    assert result['jgb']['controls']['gakBeats'] == '2:2'
    assert result['bpm'] == 120
    assert result['expressions']['performancePlan']['tempos'] == [
        {'gak': 2, 'cell': 1, 'start_bpm': 60, 'bpm': 120, 'end_gak': 3, 'end_cell': 1},
        {'gak': 3, 'cell': 1, 'bpm': 120}]


def test_mixed_beat_units_share_exact_eighth_cells_without_new_onsets(monkeypatch):
    result = recognize(monkeypatch, xml(
        measure(note('C4', 12), time=(3, 4)),
        measure(note('D4', 12), time=(6, 8)),
        measure(note('E4', 10), time=(5, 8))))
    score = result['score']
    assert [len(c['cells']) for c in score['columns']] == [6, 6, 5]
    assert quarter_events(score) == [('NOTE', 0, 3), ('NOTE', 3, 3), ('NOTE', 6, Fraction(5, 2))]
    assert score['music']['ticksPerJeonggan'] * 2 == score['music']['ticksPerBeat']


def test_same_pitch_without_tie_is_a_new_onset_in_each_bar(monkeypatch):
    result = recognize(monkeypatch, xml(
        measure(note('C4', 12), time=(3, 4)), measure(note('C4', 8), time=(2, 4))))
    assert quarter_events(result['score']) == [('NOTE', 0, 3), ('NOTE', 3, 2)]


def test_last_partial_bar_is_preserved_without_added_rest(monkeypatch):
    result = recognize(monkeypatch, xml(
        measure(note('C4', 16), time=(4, 4)), measure(note('D4', 2))))
    assert quarter_events(result['score']) == [('NOTE', 0, 4), ('NOTE', 4, Fraction(1, 2))]
    assert [len(c['cells']) for c in result['score']['columns']] == [8, 1]


@pytest.mark.parametrize('requested,expected', [(None, ''), ('4박 기본 반주', '4박 기본 반주')])
def test_empty_source_bar_requires_review_without_inventing_a_rest(monkeypatch, requested, expected):
    result = recognize(monkeypatch, xml(
        measure(note('C4', 16), time=(4, 4)), measure(), measure(note('D4', 16))), jangdan=requested)
    assert quarter_events(result['score']) == [('NOTE', 0, 4), ('NOTE', 4, 4)]
    assert result['score']['music']['jangdan'] == expected
    reviews = [i for i in result['issues'] if i['detail'].get('reason') == 'EMPTY_MEASURE_REVIEW_REQUIRED']
    assert len(reviews) == 1 and reviews[0]['detail']['measure'] == 2


@pytest.mark.parametrize('beats,unit,duration,width,bpm_unit', [
    (2, 4, 8, 2, 'QUARTER'), (3, 4, 12, 3, 'QUARTER'),
    (6, 8, 12, 2, 'DOTTED_QUARTER'), (5, 8, 10, 5, 'QUARTER'),
    (7, 8, 14, 7, 'QUARTER')])
def test_unsupported_meter_keeps_timing_without_a_traditional_fallback(
        monkeypatch, beats, unit, duration, width, bpm_unit):
    result = recognize(monkeypatch, xml(measure(note('C4', duration), time=(beats, unit))))
    assert result['score']['music']['jangdan'] == ''
    assert result['score']['music']['bpmUnit'] == bpm_unit
    assert [len(c['cells']) for c in result['score']['columns']] == [width]
    assert quarter_events(result['score']) == [('NOTE', 0, Fraction(duration, 4))]
    assert not any(i['kind'].startswith('JANGDAN_') for i in result['issues'])


def test_low_confidence_time_signature_keeps_its_page_and_source_bounds():
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
        archive.writestr('sheet#1/sheet#1.xml', '''<sheet>
          <time-whole time-rational="6/8" grade="0.547" staff="1">
            <bounds x="592" y="450" w="35" h="90"/></time-whole>
          <time-whole time-rational="4/4" grade="0.95" staff="2">
            <bounds x="500" y="700" w="32" h="40"/></time-whole></sheet>''')
    issues = staff_engine.time_signature_reviews(stream.getvalue(), page=2)
    assert len(issues) == 1
    assert issues[0]['kind'] == 'LOW_CONFIDENCE'
    assert issues[0]['page'] == 2 and issues[0]['bbox'] == [592, 450, 35, 90]
    assert issues[0]['detail'] == {'reason': 'TIME_SIGNATURE_REVIEW_REQUIRED',
                                    'timeSignature': '6/8', 'grade': 0.547, 'staff': '1'}


def test_repeated_underfull_bars_require_cell_review_without_changing_durations(monkeypatch):
    result = recognize(monkeypatch, xml(
        measure(note('C4', 2), time=(6, 8)),
        measure(note('D4', 10)), measure(note('E4', 10)), measure(note('F4', 10))))
    score = result['score']
    assert quarter_events(score) == [('REST', 0, Fraction(5, 2)), ('NOTE', Fraction(5, 2), Fraction(1, 2)),
                                     ('NOTE', 3, Fraction(5, 2)), ('NOTE', Fraction(11, 2), Fraction(5, 2)),
                                     ('NOTE', 8, Fraction(5, 2))]
    reviews = [i for i in result['issues'] if i['detail'].get('reason') == 'REPEATED_UNDERFULL_MEASURE']
    assert {i['cellId'] for i in reviews} == {'c1-j0', 'c2-j0'}
    assert all(i['detail']['expectedQuarters'] == '3' and i['detail']['actualQuarters'] == '5/2'
               for i in reviews)


@pytest.mark.parametrize('requested,expected', [(None, ''), ('세마치', '세마치')])
def test_uncertain_meter_suppresses_auto_accompaniment_but_keeps_explicit_choice(monkeypatch, requested, expected):
    review = {**staff_musicxml._issue('LOW_CONFIDENCE', {'reason': 'TIME_SIGNATURE_REVIEW_REQUIRED'}),
              'page': 0, 'bbox': [10, 20, 30, 40]}
    result = recognize(monkeypatch, xml(measure(tempo('80'), note('C4', 18), time=(9, 8))),
                       jangdan=requested, issues=[review])
    assert result['score']['music']['jangdan'] == expected
    assert quarter_events(result['score']) == [('NOTE', 0, Fraction(9, 2))]
    assert result['issues'][0] == review


def test_opening_tempo_is_the_base_bpm_and_not_a_section_tempo(monkeypatch):
    # S15P31E102-1083: 곡 첫머리 숫자 빠르기는 기본 BPM이며 구간 목록에 남기지 않는다.
    result = recognize(monkeypatch, xml(
        measure(tempo('60'), note('C4', 16), time=(4, 4)),
        measure(tempo('90'), note('D4', 16)),
        measure(tempo('60'), note('E4', 16))))
    assert result['bpm'] == 60
    assert result['expressions']['performancePlan']['tempos'] == [
        {'gak': 2, 'cell': 1, 'bpm': 90}, {'gak': 3, 'cell': 1, 'bpm': 60}]


def test_pickup_opening_tempo_is_the_base_bpm(monkeypatch):
    result = recognize(monkeypatch, xml(
        measure(tempo('72'), note('C4', 4), time=(4, 4)),
        measure(note('D4', 16))))
    assert result['bpm'] == 72 and 'expressions' not in result


def test_opening_ramp_keeps_its_explicit_start(monkeypatch):
    words = '<direction><direction-type><words>MOLTO ACCEL.</words></direction-type></direction>'
    result = recognize(monkeypatch, xml(
        measure(words, tempo('80'), note('C4', 16), time=(4, 4)),
        measure(tempo('160'), note('D4', 16))))
    assert result['bpm'] == 80
    assert result['expressions']['performancePlan']['tempos'] == [
        {'gak': 1, 'cell': 1, 'start_bpm': 80, 'bpm': 160, 'end_gak': 2, 'end_cell': 1},
        {'gak': 2, 'cell': 1, 'bpm': 160}]


def test_opening_tempo_stays_in_the_plan_when_it_cannot_be_the_base_bpm(monkeypatch):
    # 6/8의 4분음표100은 점4분 66.67이라 기본 BPM이 될 수 없다. 기존 TEMPO_BASE_NOT_INTEGER 계약을 따른다.
    result = recognize(monkeypatch, xml(measure(tempo('100'), note('C4', 24), time=(6, 8))))
    assert 'bpm' not in result
    assert result['expressions']['performancePlan']['tempos'][0]['gak'] == 1
    assert any(i['detail'].get('reason') == 'TEMPO_BASE_NOT_INTEGER' for i in result['issues'])
