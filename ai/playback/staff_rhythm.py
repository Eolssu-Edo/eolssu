"""Recover explicit tuplet and ornament symbols from one Audiveris staff."""
from copy import deepcopy
from fractions import Fraction
import hashlib
import io
import json
from pathlib import Path
import time
import xml.etree.ElementTree as ET
import zipfile

import numpy as np
from PIL import Image

from .recognition import RecognitionError, _chat, _json, _require, inference_options
from .score import PPQN
from .staff_metadata import _graph


TUPLET_PROMPT = ('Read the printed tuplet numeral beside the small curved bracket in this musical passage. '
                 'Report the visible tuplet number (2 to 9), or 0 if none is visible. Do not count notes to '
                 'guess the number. Ignore ordinary long slurs. Mark uncertain if not readable.')
TUPLET_SCHEMA = {'type':'object','additionalProperties':False,'properties':{
    'number':{'type':'integer','minimum':0,'maximum':9},'uncertain':{'type':'boolean'}},'required':['number','uncertain']}
ORNAMENT_SCHEMA = {'type':'object','additionalProperties':False,'properties':{
    'ornament':{'type':'string','enum':['upper_mordent','lower_mordent','none','unknown']},
    'uncertain':{'type':'boolean'}},'required':['ornament','uncertain']}


def _durations(root):
    result=[]; divisions=None
    for measure in root.findall('part/measure'):
        for element in measure:
            if element.tag == 'attributes' and element.findtext('divisions') is not None:
                divisions=Fraction(element.findtext('divisions'))
            elif element.tag == 'note' and element.find('grace') is None:
                _require(divisions is not None and divisions > 0 and element.findtext('duration') is not None,
                         'Missing divisions or duration for rhythm correction')
                result.append((element,Fraction(element.findtext('duration'))/divisions))
    return result


def apply_tuplet(root, indices, actual):
    """Apply one explicit, uniform tuplet; preserve unrelated time exactly."""
    _require(type(actual) is int and actual in (3,5,6), 'Unsupported simple tuplet number')
    _require(not root.findall('.//offset') and not root.findall('.//backup') and not root.findall('.//forward'),
             'Timed directions or polyphonic offsets require explicit rhythm handling')
    _require(len(indices)==actual and indices==list(range(indices[0],indices[0]+actual)), 'Tuplet span/count mismatch')
    result=deepcopy(root); items=_durations(result)
    _require(indices[0]>=0 and indices[-1]<len(items), 'Tuplet outside note sequence')
    selected=[items[i] for i in indices]
    _require(len({duration for _,duration in selected})==1 and all(
        note.find('time-modification') is None and note.find('rest') is None and note.find('chord') is None
        and not note.findall('dot') for note,_ in selected), 'Tuplet already encoded or nonuniform')
    normal=2**(actual.bit_length()-1)
    for index,(note,duration) in enumerate(items):
        if index in indices:
            duration*=Fraction(normal,actual)
            modification=ET.SubElement(note,'time-modification')
            ET.SubElement(modification,'actual-notes').text=str(actual)
            ET.SubElement(modification,'normal-notes').text=str(normal)
        ticks=duration*PPQN
        _require(ticks.denominator==1 and ticks>0, 'Unrepresentable corrected duration')
        note.find('duration').text=str(ticks.numerator)
    for divisions in result.findall('.//attributes/divisions'):divisions.text=str(PPQN)
    return result


def _bounds(element):
    return {k:int(v) for k,v in element.find('bounds').attrib.items()}


def _mask(glyph):
    table=glyph.find('run-table');w,h=int(table.get('width')),int(table.get('height'))
    _require(table.get('orientation')=='VERTICAL' and 0<w<=512 and 0<h<=512, 'Unsupported glyph raster')
    columns=table.findall('runs');_require(len(columns)==w,'Invalid glyph columns')
    result=np.zeros((h,w),dtype=bool)
    for x,column in enumerate(columns):
        y=0
        for i,length in enumerate(map(int,(column.text or '').split())):
            _require(length>=0 and y+length<=h,'Invalid glyph runs')
            if i%2==0:result[y:y+length,x]=True
            y+=length
    return result


def _aligned_heads(root,graph,excluded):
    _require(len(graph.findall('.//staff'))==1 and len(root.findall('part'))==1,
             'Rhythm enrichment supports one staff/system only')
    _require(not root.findall('.//backup') and not root.findall('.//forward'), 'Polyphonic rhythm unsupported')
    heads=sorted((h for h in graph.findall('.//inters/head') if h.get('id') not in excluded),key=lambda h:_bounds(h)['x'])
    notes=[note for note,_ in _durations(root)]
    _require(len(heads)==len(notes) and len(notes)<=1000 and all(n.find('pitch') is not None and n.find('chord') is None for n in notes),
             'Graph/XML heads are not a bijective monophonic sequence')
    il=int(graph.find('scale/interline').get('main'));_require(4<=il<=150,'Unsupported staff scale')
    index=0
    for measure in root.findall('part/measure'):
        offsets=[]
        for note in measure.findall('note'):
            if note.find('grace') is not None:continue
            offsets.append(float(note.get('default-x'))-_bounds(heads[index])['x']*10/il);index+=1
        _require(not offsets or max(offsets)-min(offsets)<=2,'Graph/XML note coordinates disagree')
    return heads,il


def _orphans(graph,heads,il):
    used={e.get('glyph') for e in graph.findall('.//inters/*') if e.get('glyph')}
    words=[_bounds(e) for e in graph.findall('.//inters/word')]
    start=min(_bounds(h)['x'] for h in heads);end=max(_bounds(h)['x']+_bounds(h)['w'] for h in heads)
    result=[]
    for glyph in graph.findall('.//glyph'):
        if glyph.get('id') in used or glyph.get('groups')!='SYMBOL':continue
        table=glyph.find('run-table');x,y,w,h=[int(v) for v in (glyph.get('left'),glyph.get('top'),table.get('width'),table.get('height'))]
        if not (start<=x<=end and .2*il<=w<=5*il and .2*il<=h<=5*il):continue
        if any(b['x']<=x and x+w<=b['x']+b['w'] and b['y']<=y and y+h<=b['y']+b['h'] for b in words):continue
        result.append({'glyph':glyph,'id':glyph.get('id'),'box':[x,y,x+w,y+h],'w':w,'h':h})
    return result


def _stem_x(graph,head):
    edges=[e for e in graph.findall('.//relation') if e.get('source')==head.get('id') and e.find('head-stem') is not None]
    if len(edges)!=1:return None
    stem=graph.find('.//inters/stem[@id="'+edges[0].get('target')+'"]')
    if stem is None:return None
    median=stem.find('median/p1')
    return float(median.get('x')) if median is not None else _bounds(stem)['x']


def _grace_geometry(glyph,graph,root,il):
    mask=_mask(glyph);h,w=mask.shape;stem=int(np.argmax(mask.sum(axis=0)))
    _require(mask[:,stem].sum()>=.65*h,'No long grace stem')
    low=mask.copy();low[:int(h*.6)]=False;low[:,max(0,stem-1):]=False
    high=mask.copy();high[int(h*.4):]=False;high[:,:min(w,stem+2)]=False
    def density(part):
        yy,xx=np.where(part)
        return part.sum()/((yy.max()-yy.min()+1)*(xx.max()-xx.min()+1)) if len(xx) else 0
    # A filled notehead is a compact blob; the opposite flag has a sparse curve.
    low_density,high_density=density(low),density(high)
    if low_density>=.55 and low_density-high_density>=.1 and low.sum()>=.08*w*h:body=low;direction='up'
    elif high_density>=.55 and high_density-low_density>=.1 and high.sum()>=.08*w*h:body=high;direction='down'
    else:raise RecognitionError('Ambiguous grace notehead/stem geometry')
    center=float(np.where(body)[0].mean())+int(glyph.get('top'))
    clefs=root.findall('.//attributes/clef');keys=root.findall('.//attributes/key/fifths')
    _require(len(clefs)==1 and len(keys)<=1,'Changing clef/key for grace unsupported')
    clef=clefs[0];reference={'G':(4,4),'F':(3,3),'C':(4,0)}
    _require(clef.findtext('sign') in reference,'Unsupported grace clef')
    octave,step=reference[clef.findtext('sign')];line=int(clef.findtext('line'))
    ys=[float(e.find('point').get('y')) for e in graph.findall('.//staff/lines/line')]
    offset=(max(ys)-(line-1)*il-center)/(il/2)
    _require(abs(offset-round(offset))<=.3,'Grace notehead between staff positions')
    diatonic=7*octave+step+round(offset);octave,step=divmod(diatonic,7)
    octave+=int(clef.findtext('clef-octave-change','0'));letter='CDEFGAB'[step]
    fifths=int(keys[0].text) if keys else 0;order='BEADGCF' if fifths<0 else 'FCGDAEB'
    alter=(-1 if fifths<0 else 1) if letter in order[:abs(fifths)] else 0
    yy,xx=np.where(body)
    return {'step':letter,'octave':octave,'alter':alter,'head_y':center,'stem_direction':direction,
            'head_width':int(xx.max()-xx.min()+1),'head_height':int(yy.max()-yy.min()+1),
            'head_density':density(body),'flag_density':high_density if direction=='up' else low_density,
            'opposite_flag_pixels':int(high.sum() if direction=='up' else low.sum()),
            'glyph_pixels':w*h,'stem_coverage':float(mask[:,stem].sum()/h)}


def _grace_accidental(root,parent,glyph,geometry,graph,orphans,il):
    """Carry explicit same-measure accidentals; reject an unbound nearby sign."""
    geometry=dict(geometry);x=glyph['box'][0];head_y=geometry['head_y']
    boxes=[_bounds(e) for e in graph.findall('.//inters/alter')]
    boxes += [{'x':g['box'][0],'y':g['box'][1],'w':g['w'],'h':g['h']} for g in orphans if g['id']!=glyph['id']]
    for b in boxes:
        if (0<=x-(b['x']+b['w'])<=2*il and b['y']<=head_y+il and b['y']+b['h']>=head_y-il
                and .2*il<=b['w']<=2*il and .8*il<=b['h']<=4*il):
            raise RecognitionError('Unbound accidental-shaped glyph beside grace note')
    target=_durations(root)[parent][0]
    measure=next(m for m in root.findall('part/measure') if target in list(m))
    for note in measure.findall('note'):
        if note is target:break
        pitch=note.find('pitch')
        if (pitch is not None and note.find('accidental') is not None and
                pitch.findtext('step')==geometry['step'] and pitch.findtext('octave')==str(geometry['octave'])):
            value=Fraction(pitch.findtext('alter','0'))
            _require(value.denominator==1 and -2<=value<=2,'Unsupported grace accidental carry')
            geometry['alter']=int(value)
            geometry['accidental_basis']='explicit same-measure carry'
    return geometry


def _small_flagged_grace(geometry, heads):
    median_w=float(np.median([_bounds(n)['w'] for n in heads]))
    median_h=float(np.median([_bounds(n)['h'] for n in heads]))
    return (.35*median_w<=geometry['head_width']<=.8*median_w and
            geometry['head_height']<=.9*median_h and
            geometry['opposite_flag_pixels']>=.04*geometry['glyph_pixels'])


def enrich_rhythm(root,image_path,omr_path,context,*,model,base_url='http://127.0.0.1:11434',trace_path=None,excluded_head_ids=()):
    """Bounded local symbol reading, with geometric associations and raw evidence."""
    result=deepcopy(root)
    report={'corrections':[],'warnings':[],'responses':[],'candidates':[],'model':model,
            'scope':'single staff/system, explicit uniform 3/5/6 tuplets, short upper mordents and single grace notes',
            'ornament_pitch_classes':context.get('ornament_pitch_classes'),
            'ornament_duration_policy':'1/16 quarter borrowed from the main note; preview assumption'}
    options={**inference_options(model),'temperature':0,'num_ctx':4096,'num_batch':128,'num_predict':160}
    report['options']=options;started=time.monotonic();calls=0
    def read(image,box,name,prompt,schema,scale=2):
        nonlocal calls
        _require(calls<24 and time.monotonic()-started<90,'Rhythm recognition budget exceeded')
        box=[max(0,int(box[0])),max(0,int(box[1])),min(image.width,int(box[2])),min(image.height,int(box[3]))]
        _require(box[0]<box[2] and box[1]<box[3] and (box[2]-box[0])*(box[3]-box[1])<=200_000,'Invalid rhythm crop')
        crop=image.crop(box);crop=crop.resize((crop.width*scale,crop.height*scale))
        stream=io.BytesIO();crop.save(stream,format='PNG');data=stream.getvalue()
        entry={'name':name,'bbox_pixels_xyxy':box,'scale':scale,'sha256':hashlib.sha256(data).hexdigest(),'prompt':prompt,'schema':schema}
        if trace_path:
            path=Path(trace_path).with_name(Path(trace_path).stem+'-'+name+'.png');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);entry['path']=str(path)
        remaining=90-(time.monotonic()-started);_require(remaining>=.1,'Rhythm recognition budget exceeded')
        calls+=1;raw=_chat(data,prompt,schema,model=model,base_url=base_url,timeout=min(30,remaining),options=options)
        entry['raw_response']=raw;report['responses'].append(entry)
        _require(raw.get('done_reason')!='length','Truncated rhythm response')
        value=_json(raw['message']['content'])
        _require(isinstance(value,dict) and set(value)==set(schema['required']) and type(value.get('uncertain')) is bool,'Invalid rhythm response')
        for key,spec in schema['properties'].items():
            if spec['type']=='boolean':_require(type(value[key]) is bool,'Invalid boolean')
            elif spec['type']=='integer':_require(type(value[key]) is int and spec['minimum']<=value[key]<=spec['maximum'],'Invalid numeral')
            elif 'enum' in spec:_require(value[key] in spec['enum'],'Invalid ornament symbol')
        return value
    try:
        graph=_graph(omr_path);heads,il=_aligned_heads(result,graph,set(excluded_head_ids));orphans=_orphans(graph,heads,il)
        ys=[float(e.find('point').get('y')) for e in graph.findall('.//staff/lines/line')]
        stems=[_stem_x(graph,h) for h in heads]
        with Image.open(image_path) as image:
            picture=graph.find('picture');_require(image.size==(int(picture.get('width')),int(picture.get('height'))),'OMR image dimensions mismatch')
            # A visible small arc plus nearby unclassified numeral bounds a possible tuplet.
            for arc in graph.findall('.//inters/slur'):
                b=_bounds(arc)
                if not (1.5*il<=b['w']<=12*il and .25*il<=b['h']<=4*il):continue
                curve=arc.find('curve');left,right=sorted((float(curve.get('x1')),float(curve.get('x2'))))
                indices=[i for i,x in enumerate(stems) if x is not None and left-.5*il<=x<=right+.5*il]
                if len(indices) not in (3,5,6) or indices!=list(range(indices[0],indices[-1]+1)):continue
                symbols=[g for g in orphans if g['w']<=1.2*il and .5*il<=g['h']<=2*il and
                         abs((g['box'][0]+g['box'][2])/2-(left+right)/2)<.35*(right-left) and
                         b['y']-il<=g['box'][1]<=b['y']+b['h']+il]
                if len(symbols)!=1:continue
                durations=_durations(result)
                if len({durations[i][1] for i in indices})!=1 or any(durations[i][0].find('time-modification') is not None for i in indices):continue
                box=[b['x']-2*il,min(min(ys)-il,b['y']-il),b['x']+b['w']+2*il,max(max(ys)+2*il,b['y']+b['h']+il)]
                value=read(image,box,'tuplet-'+arc.get('id'),TUPLET_PROMPT,TUPLET_SCHEMA)
                evidence={'kind':'tuplet','arc_id':arc.get('id'),'glyph_id':symbols[0]['id'],'note_indices':indices,'read':value}
                report['candidates'].append(evidence)
                if value['uncertain'] or value['number']!=len(indices):
                    report['warnings'].append('잇단 숫자와 기하학적 음표 범위가 일치하지 않아 해당 음가를 유지했습니다.');continue
                result=apply_tuplet(result,indices,value['number'])
                report['corrections'].append({**evidence,'normal_notes':2**(value['number'].bit_length()-1)})
            for glyph in orphans:
                x,y,x2,y2=glyph['box'];w,h=glyph['w'],glyph['h'];center=(x+x2)/2
                centers=[_bounds(n)['x']+_bounds(n)['w']/2 for n in heads]
                parent=min(range(len(heads)),key=lambda i:abs(centers[i]-center));nb=_bounds(heads[parent])
                # Short, horizontal symbols above a uniquely aligned main note.
                if .6*il<=w<=2*il and .3*il<=h<=1.2*il and w/h>=1.2 and abs(centers[parent]-center)<=.75*il and il<=nb['y']-y2<=8*il:
                    if sum(abs(cx-center)<=.75*il for cx in centers)!=1:continue
                    box=[max(0,x-2*il),max(0,y-2*il),x2+3*il,max(y2+2*il,nb['y']+nb['h']+il)]
                    target=[(x-box[0])*3,(y-box[1])*3,w*3,h*3]
                    prompt=('Read the musical symbol in target bbox [x,y,width,height] '+str(target)+
                            '. Identify an upper mordent (short zigzag without a crossing vertical stroke), '
                            'lower mordent (zigzag with vertical stroke), or none. Other accents/slurs are none. '
                            'Do not infer an absent symbol from the tune.')
                    value=read(image,box,'ornament-'+glyph['id'],prompt,ORNAMENT_SCHEMA,3)
                    evidence={'kind':'upper-return','glyph_id':glyph['id'],'note_index':parent,'read':value};report['candidates'].append(evidence)
                    if value['uncertain'] or value['ornament']!='upper_mordent':continue
                    note=_durations(result)[parent][0]
                    if note.find('./notations/ornaments') is not None:continue
                    notations=note.find('notations')
                    if notations is None:notations=ET.SubElement(note,'notations')
                    ET.SubElement(ET.SubElement(notations,'ornaments'),'inverted-mordent')
                    report['corrections'].append(evidence)
                elif .6*il<=w<=2.5*il and 1.5*il<=h<=4*il and min(ys)-il<=y<=max(ys)+il:
                    try:geometry=_grace_geometry(glyph['glyph'],graph,result,il)
                    except (RecognitionError,ValueError):continue
                    if not _small_flagged_grace(geometry,heads):continue
                    following=[i for i,n in enumerate(heads) if _bounds(n)['x']>=x2 and _bounds(n)['x']-x2<=4*il]
                    if not following:continue
                    parent=following[0]
                    try:geometry=_grace_accidental(result,parent,glyph,geometry,graph,orphans,il)
                    except RecognitionError as error:
                        report['warnings'].append('앞꾸밈음 임시표를 확인하지 못해 생략했습니다: '+str(error));continue
                    evidence={'kind':'grace-before','glyph_id':glyph['id'],'note_index':parent,
                              'provider':'glyph_geometry','bbox_pixels_xyxy':glyph['box'],'geometry':geometry,
                              'basis':'compact smaller head, long stem, opposite sparse flag, staff-position fit and following main note'}
                    report['candidates'].append(evidence)
                    if trace_path:
                        path=Path(trace_path).with_name(Path(trace_path).stem+'-grace-'+glyph['id']+'.png')
                        path.parent.mkdir(parents=True,exist_ok=True);image.crop(glyph['box']).save(path);evidence['path']=str(path)
                    note=_durations(result)[parent][0];measure=next(m for m in result.findall('part/measure') if note in list(m));position=list(measure).index(note)
                    if position and list(measure)[position-1].tag=='note' and list(measure)[position-1].find('grace') is not None:continue
                    grace=ET.Element('note');ET.SubElement(grace,'grace');pitch=ET.SubElement(grace,'pitch')
                    for tag in ('step','alter','octave'):ET.SubElement(pitch,tag).text=str(geometry[tag])
                    ET.SubElement(grace,'type').text='eighth';ET.SubElement(grace,'stem').text=geometry['stem_direction']
                    if note.findtext('voice') is not None:ET.SubElement(grace,'voice').text=note.findtext('voice')
                    measure.insert(position,grace);report['corrections'].append(evidence)
    except (OSError,ValueError,KeyError,TypeError,AttributeError,ET.ParseError,zipfile.BadZipFile) as error:
        report['warnings'].append('추가 리듬·장식음 인식을 중단했습니다. 확인된 보정만 유지합니다: '+str(error))
    report['elapsed_seconds']=round(time.monotonic()-started,3)
    if trace_path:
        path=Path(trace_path);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return result,report
