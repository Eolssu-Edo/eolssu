"""Independent bounded MusicXML reference reader for meter investigations.

Does not import the product converter. Durations/onsets are exact quarter-note
fractions. Compare a named voice and bounded opening, before repeats/voltas.
Full repeat navigation and engraving/expressive playback are outside this reader.
"""
from fractions import Fraction as F
import io
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as E
import zipfile


def root(data):
    if data[:2] == b'PK':
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            container=E.fromstring(archive.read('META-INF/container.xml'))
            path=next(x.attrib['full-path'] for x in container.iter() if x.tag.endswith('rootfile'))
            return E.fromstring(archive.read(path))
    return E.fromstring(data)


def opening(data, voice, measures=2):
    part=root(data).find('part')
    assert part is not None
    division, base, transpose = F(1), F(0), 0
    result=[]
    for m in part.findall('measure')[:measures]:
        cursor, end, previous = F(0), F(0), F(0)
        by_time={}
        for el in m:
            if el.tag=='attributes':
                if el.findtext('divisions'):division=F(el.findtext('divisions'))
                if el.find('transpose') is not None:
                    transpose=int(el.findtext('transpose/chromatic','0'))+12*int(el.findtext('transpose/octave-change','0'))
            elif el.tag in ('backup','forward'):
                cursor+=(1 if el.tag=='forward' else -1)*F(el.findtext('duration'))/division
            elif el.tag=='note' and el.find('grace') is None:
                dur=F(el.findtext('duration','0'))/division
                start=previous if el.find('chord') is not None else cursor
                if el.find('chord') is None: previous=start;cursor+=dur
                end=max(end,cursor,start+dur)
                if (el.findtext('voice') or '1')!=voice:continue
                pitch=el.find('pitch')
                if pitch is None:continue
                midi=12*(int(pitch.findtext('octave'))+1)+dict(C=0,D=2,E=4,F=5,G=7,A=9,B=11)[pitch.findtext('step')]+int(pitch.findtext('alter','0'))+transpose
                note=(midi,base+start,dur,el.find("tie[@type='stop']") is not None)
                if start not in by_time or midi>by_time[start][0]:by_time[start]=note
        for midi,start,dur,tie_stop in sorted(by_time.values(),key=lambda n:n[1]):
            if tie_stop and result and result[-1][0]==midi and result[-1][1]+result[-1][2]==start:
                before=result[-1];result[-1]=(midi,before[1],before[2]+dur)
            else:result.append((midi,start,dur))
        base+=end
    return result,base


def common_notes(score):
    unit=F(3,2) if score['music']['bpmUnit']=='DOTTED_QUARTER' else F(1)
    ticks=score['music']['ticksPerBeat']
    return [(63+'황대태협고중유임이남무응'.index(e['yul'])+12*e['octave'],F(e['startTick'],ticks)*unit,F(e['durationTick'],ticks)*unit)
            for e in score['events'] if e['kind']=='NOTE']


def explicit_lilypond_voltas(text):
    """Only the published Kesh/Butterfly subset: explicit octaves/lengths, two repeats.

    This is not a general LilyPond interpreter. Refuse unsupported note syntax
    rather than treating omissions as a valid reference.
    """
    main=text.split('main = {',1)[1].split('\\score',1)[0]
    groups=re.findall(r'\\repeat volta 2\s*\{([^}]+)\}',main,re.S)
    if not groups:
        raise ValueError('expected simple repeat volta 2 blocks')
    notes=[]
    cursor=F(0)
    token=r"\b([a-g])(is|es)?([',]+)(\d+)(\.)?"
    for group in groups:
        clean=re.sub(r'\\bar\s+"[^"]*"','',group)
        if re.sub(token,'',clean).strip():
            raise ValueError('unsupported reference LilyPond syntax')
        for _ in range(2):
            for step,alter,octave,duration,dot in re.findall(token,group):
                midi=48+dict(c=0,d=2,e=4,f=5,g=7,a=9,b=11)[step]
                midi+=(1 if alter=='is' else -1 if alter=='es' else 0)+12*(octave.count("'")-octave.count(','))
                length=F(4,int(duration))*(F(3,2) if dot else 1)
                notes.append((midi,cursor,length))
                cursor+=length
    return notes,cursor


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raw',type=Path,required=True)
    parser.add_argument('--actual',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    result=[]
    for sid in ['au-clair','greensleeves','kesh','butterfly']:
        if sid in ('au-clair','greensleeves'):
            path=args.raw/(sid+'.publisher.mxl')
            data=path.read_bytes()
            if root(data).findall('.//repeat'):
                raise ValueError('repeat navigation is outside bounded XML reader')
            expected,extent=opening(data,'5',measures=1000)
            _,first=opening(data,'5',measures=1)
            meter=F(4 if sid=='au-clair' else 3)
            pad=(meter-first%meter)%meter
            expected=[(m,s+pad,d) for m,s,d in expected]
            scope='full publisher MusicXML, melody voice 5; explicit leading pad'
        else:
            path=args.raw/(sid+'.ly')
            data=path.read_bytes()
            expected,extent=explicit_lilypond_voltas(data.decode('utf-8'))
            pad=F(0)
            scope='full published LilyPond, explicit notes and volta twice'
        score=json.loads((args.actual/sid/'engine.json').read_text(encoding='utf-8'))['score']
        actual=common_notes(score)
        changes=[dict(op=op,expectedStart=i,expectedEnd=j,actualStart=k,actualEnd=l)
                 for op,i,j,k,l in difflib.SequenceMatcher(a=expected,b=actual,autojunk=False).get_opcodes() if op!='equal']
        result.append(dict(id=sid,scope=scope,sourceSha256=hashlib.sha256(data).hexdigest(),
                           exactMatch=expected==actual,expectedNotes=len(expected),actualNotes=len(actual),
                           expectedMusicQuarters=str(extent),importPadQuarters=str(pad),differences=changes))
    args.out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))
    return 0 if all(x['exactMatch'] for x in result) else 1


if __name__=='__main__':
    raise SystemExit(main())
