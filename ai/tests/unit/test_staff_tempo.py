"""Printed numeric tempi use the existing cell-located performance plan."""
from omr_service.staff_musicxml import convert
from test_staff_musicxml import xml, measure, note


def tempo(value, unit='quarter', dots='', words='', offset=''):
    return (f'<direction><direction-type><metronome><beat-unit>{unit}</beat-unit>{dots}'
            f'<per-minute>{value}</per-minute></metronome></direction-type>{offset}'
            f'<sound tempo="{value.split()[0]}"/>{words}</direction>')


def read(*measures):
    return convert(xml(*measures), instrument='DANSO', hwang_pitch=60)


def bar(*directions):
    return measure(*directions, note('C4',16), time=(4,4))


def test_numeric_steps_keep_positions_and_sound_duplicate_is_not_a_second_cue():
    out=read(bar(tempo('160')),bar(tempo('80')),bar(tempo('160')))
    assert out.quarter_bpm == 160
    assert out.tempos == [{'gak':1,'cell':1,'bpm':160}, {'gak':2,'cell':1,'bpm':80},
                          {'gak':3,'cell':1,'bpm':160}]


def test_accelerando_uses_numeric_start_and_next_numeric_target_with_existing_ramp():
    out=read(bar(tempo('160')),bar(tempo('80 molto accel. _')),bar(),bar(tempo('160')))
    assert out.tempos == [{'gak':1,'cell':1,'bpm':160},
                          {'gak':2,'cell':1,'start_bpm':80,'bpm':160,'end_gak':4,'end_cell':1},
                          {'gak':4,'cell':1,'bpm':160}]


def test_dotted_metronome_and_compound_meter_are_converted_to_jeonggan_bpm():
    # MusicXML sound is quarter/min; printed dotted quarter 80 means quarter120.
    mark=tempo('80',dots='<beat-unit-dot/>').replace('tempo="80"','tempo="120"')
    out=read(measure(mark,note('C4',24),time=(6,8)))
    assert out.quarter_bpm == 120
    assert out.tempos == [{'gak':1,'cell':1,'bpm':80}]


def test_pickup_padding_and_repeat_use_the_same_timeline_as_the_notes():
    out=read(measure(tempo('120'),note('C4',4),time=(4,4)),
             measure(tempo('60'),note('D4',16),barline='<barline><repeat direction="forward"/></barline>'),
             measure(note('E4',16),barline='<barline><repeat direction="backward"/></barline>'))
    assert [(x['gak'],x['cell'],x['bpm']) for x in out.tempos] == [(1,1,120),(2,1,60),(4,1,60)]


def test_fractional_cell_tempo_is_reported_without_snapping_to_a_different_note():
    out=read(bar(tempo('80',offset='<offset>2</offset>')))
    assert out.tempos == [] and out.quarter_bpm is None
    assert any(x['detail']['reason']=='TEMPO_OFF_GRID' for x in out.issues)


def test_accel_without_target_is_reported_and_numeric_step_is_preserved():
    out=read(bar(tempo('80 molto accel. _')))
    assert out.tempos == [{'gak':1,'cell':1,'bpm':80}]
    assert any(x['detail']['reason']=='TEMPO_RAMP_UNRESOLVED' for x in out.issues)


def test_accel_cannot_jump_over_an_unrepresentable_numeric_target():
    out=read(bar(tempo('80 molto accel. _')),bar(tempo('120',offset='<offset>2</offset>')),bar(tempo('160')))
    assert out.tempos == [{'gak':1,'cell':1,'bpm':80},{'gak':3,'cell':1,'bpm':160}]
    assert any(x['detail']['reason']=='TEMPO_RAMP_UNRESOLVED' for x in out.issues)


def test_separate_accel_text_before_metronome_at_the_same_position_is_combined():
    words='<direction><direction-type><words>MOLTO ACCEL.</words></direction-type></direction>'
    out=read(bar(words,tempo('80')),bar(tempo('160')))
    assert out.tempos[0] == {'gak':1,'cell':1,'start_bpm':80,'bpm':160,'end_gak':2,'end_cell':1}
