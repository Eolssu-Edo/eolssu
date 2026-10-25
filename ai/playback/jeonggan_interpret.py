"""Lower explicitly recognized notation into the existing reviewed-score draft.

Rules are from the fixed Danso notation legends, not a score's reference notes.
Unmeasured grace timing remains a declared preview policy.
"""
from fractions import Fraction

from .recognition import _require,RecognitionError
from .jeonggan_page import RELATIVE, REGISTERS, SCALE

ORNAMENTS = {
    'grace_upper1': ([1,0], 'grace'), 'grace_upper2': ([2,0], 'grace'),
    'grace_lower1': ([-1,0], 'grace'), 'grace_lower2': ([-2,0], 'grace'),
    'turn_upper': ([0,1,0], 'turn'), 'turn_lower': ([0,-1,0], 'turn'),
    'lower_upper': ([-1,1,0], 'grace'), 'around_four': ([0,-1,1,0], 'turn'),
    'triplet_upper': ([0,1,0], 'equal'), 'triplet_lower': ([0,-1,0], 'equal'),
    # The I legend prints the middle (higher) note as its anchor.
    'long_upper': ([-1,0,-1], 'long'), 'long_lower': ([0,-1,0], 'long'),
    'lower2_upper1': ([-2,1,0], 'grace'),
}


def shift(pitch, offset):
    _require(pitch['yul'] in SCALE, 'Relative symbol requires a five-tone anchor')
    register,index=divmod(SCALE.index(pitch['yul'])+5*pitch['register']+offset,5)
    return {'yul':SCALE[index],'register':register}


def _decorate(events, token):
    """Attached secondary signs decorate the final return of a composite."""
    offsets,mode=ORNAMENTS[token]
    pitch,duration=events[-1]
    _require(pitch is not None,'A rest cannot carry a pitch ornament')
    if mode=='equal':
        durations=[duration/len(offsets)]*len(offsets)
    elif mode=='long':
        durations=[duration/4,duration/2,duration/4]
    else:
        grace=min(Fraction(1,16),duration/(2*len(offsets)))
        durations=[grace]*(len(offsets)-1)+[duration-grace*(len(offsets)-1)]
    return events[:-1]+[(shift(pitch,o),d) for o,d in zip(offsets,durations)]


def _groups(atoms):
    groups=[];pending=None
    for atom in atoms:
        token=atom['token'];kind=atom['kind']
        _require(token!='unknown',f"{atom['id']}: unknown glyph requires review")
        if kind=='duration':
            if token=='duration_long':
                _require(pending is None,'Duplicate unattached duration mark')
                pending=atom
            else:
                _require(token=='duration_half' and groups and groups[-1]['factor']==1,
                         'Half-duration mark has no unmodified preceding symbol')
                groups[-1]['factor']=Fraction(1,2);groups[-1]['modifiers'].append(atom)
        elif kind in ('ornament','expression'):
            _require(groups,f"{atom['id']}: attached mark has no main symbol")
            groups[-1]['attachments'].append(atom)
        elif kind=='tuplet':
            continue
        else:
            _require(kind in ('pitch','relative','rest','repeat','sustain'), 'Unsupported main symbol kind')
            groups.append({'main':atom,'factor':Fraction(3,2) if pending else Fraction(1),
                           'modifiers':[pending] if pending else [],'attachments':[]})
            pending=None
    _require(pending is None,'Long-duration mark has no following symbol')
    _require(groups,'Time row has no main symbol')
    _require(sum(g['factor'] for g in groups)==len(groups),'Explicit duration factors do not fill the time row')
    return groups


def expand_symbols(layout,cell_quarters):
    notes=[];regions=[];notices=[];width,height=layout['image_size']
    for cell in layout['cells']:
        rows=cell['rows']
        if not rows:
            _require(notes and not notes[-1].get('rest'),'Blank cell needs a preceding sounding note')
            notes[-1]['duration']=str(Fraction(notes[-1]['duration'])+cell_quarters*cell['units'])
            notices.append(f"{cell['id']}: 빈 정간을 앞 음의 연장으로 해석했습니다.")
            continue
        _require(1<=len(rows)<=3,f"{cell['id']}: ambiguous row count")
        _require(all(abs(r['relative_y']-(i+.5)/len(rows))<=.22 for i,r in enumerate(rows)),
                 f"{cell['id']}: ambiguous row positions")
        for row in rows:
            try:groups=_groups(row['atoms'])
            except RecognitionError as exc:raise RecognitionError(f"{row['id']}: {exc}") from exc
            row_duration=cell_quarters*cell['units']/len(rows)
            _require(row.get('tuple_count') is None or all(g['main']['token']!='sustain' for g in groups),
                     f"{row['id']}: Tuplet group containing a sustain needs review")
            generated=[]
            for group in groups:
                atom=group['main'];token=atom['token'];slot=row_duration/len(groups)*group['factor']
                if token=='sustain':
                    _require(not group['attachments'],f"{row['id']}: Sustain with attached mark requires review")
                    if generated:
                        p,d,a,marks=generated[-1]
                        _require(p is not None,f"{row['id']}: Sustain requires a preceding sounding note")
                        generated[-1]=(p,d+slot,a,marks)
                    else:
                        _require(notes and not notes[-1].get('rest'),f"{row['id']}: Sustain requires a preceding sounding note")
                        notes[-1]['duration']=str(Fraction(notes[-1]['duration'])+slot)
                    continue
                if atom['kind']=='pitch':
                    register,yul=token.split(':');pitches=[{'yul':yul,'register':REGISTERS[register]}]
                elif token=='rest':
                    pitches=[None]
                else:
                    prior=generated[-1][0] if generated else (notes[-1].get('pitch') if notes else None)
                    _require(prior is not None,f"{row['id']}: Relative/repeat symbol requires a preceding sounding pitch")
                    pitches=[dict(prior)] if token=='repeat' else [shift(prior,n) for n in RELATIVE[token]]
                events=[(p,slot/len(pitches)) for p in pitches]
                for attachment in group['attachments']:
                    sign=attachment['token']
                    if sign in ORNAMENTS:
                        events=_decorate(events,sign)
                    else:
                        notices.append(f"{attachment['id']}: {sign} 표현은 위치를 보존했으나 미리듣기에 적용하지 않았습니다.")
                for pitch,duration in events:
                    generated.append((pitch,duration,atom,group['attachments']))
            if row.get('tuple_count') is not None:
                _require(len(generated)==row['tuple_count'],f"{row['id']}: Tuplet marker count disagrees with expanded notes")
                _require(all(g['factor']==1 for g in groups),f"{row['id']}: Tuplet plus length modifier needs review")
                generated=[(p,row_duration/len(generated),a,marks) for p,d,a,marks in generated]
            for index,(pitch,duration,atom,marks) in enumerate(generated,1):
                nid=f"{row['id']}-n{index}"
                note={'id':nid,'duration':str(duration)}
                note.update({'rest':True} if pitch is None else {'pitch':pitch})
                if marks and pitch is not None:
                    note['slur']=atom['id']
                notes.append(note)
                x,y,w,h=atom['bbox'];regions.append({'note_id':nid,'bbox':[x/width,y/height,w/width,h/height]})
            row['expansion']={'groups':[{'main_id':g['main']['id'],'factor':str(g['factor']),
                                        'attachments':[a['token'] for a in g['attachments']]} for g in groups],
                              'event_count':len(generated),'duration':str(row_duration)}
    return notes,regions,notices
