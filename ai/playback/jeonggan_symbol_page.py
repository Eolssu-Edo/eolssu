"""Full-page isolated glyph recognition with a fixed operator-provided atlas."""
from copy import deepcopy
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import time

import cv2
import numpy as np

from .recognition import RecognitionError,REVIEW_WARNING,_candidate,_require,_chat
from .jeonggan_layout import segment_page
from .jeonggan_margins import margin_components
from .jeonggan_symbols import LegendAtlas,split_row,classify_glyph,LITERAL_PROMPT,LITERAL_SCHEMA,OPTIONS,_response,duration_dot
from .jeonggan_interpret import expand_symbols

MAX_ATOMS=512
TOTAL_TIMEOUT=300


def recognize_symbols(pixels,image_hash,context,score_context,cell_quarters,*,model,base_url,trace_path,started,atlas_path):
    from .jeonggan import _glyph_png
    atlas=LegendAtlas(atlas_path)
    trace={'schema':'jeonggan-symbol-trace/v1','image_sha256':image_hash,'model':model,
           'atlas_sha256':atlas.sha256,'atlas_manifest_sha256':atlas.manifest_sha256,
           'atlas_files':[{'file':e['file'],'sha256':e['file_sha256']} for e in atlas.entries],
           'context':context,'options':dict(OPTIONS),'symbols':[],'domain_adaptation':True,
           'algorithm':'jeonggan-symbols/v1'}
    def save():
        trace['elapsed_seconds']=time.monotonic()-started
        if trace_path is not None:
            try:Path(trace_path).write_text(json.dumps(trace,ensure_ascii=False,indent=2),encoding='utf-8')
            except OSError as exc:raise RecognitionError('Cannot save local symbol trace') from exc
    def remaining():
        value=TOTAL_TIMEOUT-(time.monotonic()-started)
        _require(value>.1,'Jeonggan symbol page exceeded 300-second budget')
        return value
    try:
        layout=segment_page(pixels);trace['layout']=layout;trace['image_size']=layout['image_size']
        heights=[r['bbox'][3] for c in layout['cells'] for r in c['rows'] if r['bbox'][3]>6]
        _require(heights,'No glyphs found in selected grid')
        typical=float(np.percentile(heights,80));trace['typical_height']=typical
        cache={};calls=0
        for cell in layout['cells']:
            for ri,row in enumerate(cell['rows'],1):
                row['id']=f"{cell['id']}-r{ri}";row['atoms']=[];row['tuple_count']=None
                boxes=split_row(pixels,row['bbox'],typical_height=typical)
                for ai,bbox in enumerate(boxes,1):
                    _require(len(trace['symbols'])<MAX_ATOMS,'Page exceeds 512 isolated glyphs')
                    role='main'
                    if row['atoms']:
                        previous=row['atoms'][-1];px,py,pw,ph=previous['bbox']
                        if previous['kind'] not in ('duration','sustain'):
                            role='after-main'
                            gap=bbox[0]-(px+pw)
                            if ((gap<=typical*.35 and bbox[3]<=typical*.75) or
                                    (gap<=typical*.8 and bbox[2]<=typical*.50 and bbox[3]<=typical*.85)):
                                role='attached'
                    role_reason='geometry-and-parent-spacing'
                    if ai<len(boxes):
                        nx,ny,nw,nh=boxes[ai]
                        next_ink=cv2.cvtColor(pixels[ny:ny+nh,nx:nx+nw],cv2.COLOR_RGB2GRAY)<170
                        if duration_dot(next_ink,typical):
                            role='main';role_reason='following-half-duration-dot'
                    digest=hashlib.sha256(_glyph_png(pixels,bbox)).hexdigest()+str(role)
                    if digest in cache:
                        result,evidence=deepcopy(cache[digest]);evidence['exact_crop_cache']=True;evidence['bbox']=bbox
                    else:
                        result,evidence=classify_glyph(pixels,bbox,typical_height=typical,atlas=atlas,
                                                      model=model,base_url=base_url,timeout=remaining(),role=role)
                        cache[digest]=deepcopy((result,evidence))
                        calls+=int('response' in evidence)+int('body_ocr' in evidence)+int('prefix_comparison' in evidence)
                    evidence['role']=role;evidence['role_reason']=role_reason
                    atom={'id':f"{row['id']}-a{ai}",'bbox':bbox,**result};row['atoms'].append(atom)
                    trace['symbols'].append({'id':atom['id'],'cell_id':cell['id'],'row_id':row['id'],
                                             'classification':result,**evidence});save()
        rows={r['id']:r for c in layout['cells'] for r in c['rows']}
        trace['margins']=margin_components(pixels,layout,typical)
        _require(len(trace['margins'])<=128,'Too many ambiguous grid-margin marks')
        for index,mark in enumerate(trace['margins'],1):
            _require(not mark.get('ambiguous_row'),'Margin symbol is equidistant from two time rows')
            row=rows[mark['row_id']];x,y,w,h=mark['bbox'];mark['classification']={'token':'unknown','kind':'unknown','glyph':'?'}
            if mark['side']=='left' and w<=max(3,typical*.20) and typical*.45<=h<=typical*1.6:
                atom={'id':f"{row['id']}-m{index}",'bbox':mark['bbox'],'token':'duration_long','kind':'duration','glyph':'|'}
                row['atoms'].insert(0,atom);mark['classification']={k:atom[k] for k in ('token','kind','glyph')}
                mark['provider']='outside-left-vertical-stroke-geometry'
            elif .45<w/h<1.6 and typical*.4<=h<=typical*1.5:
                reply=_chat(_glyph_png(pixels,mark['bbox']),LITERAL_PROMPT,LITERAL_SCHEMA,model=model,
                            base_url=base_url,timeout=remaining(),options=OPTIONS);calls+=1
                mark['response']=reply;glyph=_response(reply,['glyph'])['glyph']
                if glyph in ('五','三'):
                    count={'五':5,'三':3}[glyph]
                    _require(row['tuple_count'] is None,'Multiple tuplet markers on one row')
                    row['tuple_count']=count;mark['classification']={'token':f'tuplet:{count}','kind':'tuplet','glyph':glyph}
            save()
        unknown=[s for s in trace['symbols'] if s['classification']['token']=='unknown']
        trace['coverage']={'cells':len(layout['cells']),'rows':len(rows),'atoms':len(trace['symbols']),
                           'unknown_atoms':len(unknown),'model_calls':calls,
                           'blank_cells':sum(not c['rows'] for c in layout['cells']),
                           'unrecognized_margin_marks':sum(m['classification']['token']=='unknown' for m in trace['margins'])}
        _require(not unknown,f'{len(unknown)} unknown isolated glyphs require review')
        _require(not trace['coverage']['unrecognized_margin_marks'],'Unrecognized rhythm-bearing margin marks require review')
        notes,regions,notices=expand_symbols(layout,cell_quarters)
        assumptions=[REVIEW_WARNING,'고정 교재 범례의 글자/기호와 원본 이미지 획을 대조한 개발용 인식 초안입니다.',
                     f'정간 1칸={cell_quarters} quarter, 위→아래/오른쪽→왼쪽으로 읽고 행을 균등 분할했습니다.',
                     '삼수변 0/1/2개는 범례 본체와 삼수변을 분리해 대조했습니다. 합성 두 삼수변 범례는 일반 표기 규칙입니다.',
                     '덧길이/반길이는 3/2·1/2, 명시 잇단음은 전체 행의 음을 균등 분할합니다.',
                     '수치가 없는 앞꾸밈은 각 1/16 quarter(짧은 슬롯에서는 축소)를 사용하는 미리듣기 정책입니다.',
                     '연속 장식은 앞 복합기호의 마지막 귀환음에 붙였습니다. 미표기 실연 시가·음높이를 보충하지 않았습니다.']
        if 'cell_quarters' not in context:assumptions.append('정간 길이 미지정: 기본 3/2 quarter 가정입니다.')
        if 'quarter_bpm' not in context:assumptions.append('빠르기 미지정: 기본 quarter BPM60 가정입니다.')
        if trace['coverage']['unrecognized_margin_marks']:
            notices.append('일부 격자 바깥 기호가 미확인입니다. trace 위치와 원문을 대조하세요.')
        value=_candidate({'title':'정간보 전체 페이지 기호 인식 초안','quarter_bpm':context.get('quarter_bpm',60),
                          'notes':notes,'regions':regions,'warnings':assumptions+notices},score_context)
        remaining();trace.update({'status':'draft-created','event_count':len(notes)});save()
        provider={'name':'ollama-jeonggan-symbols','model':model,'notation':'jeonggan','elapsed_seconds':trace['elapsed_seconds'],
                  'atlas_sha256':atlas.sha256,'recognition_quality':'unverified','domain_adaptation':True,'coverage':trace['coverage']}
        return {'score':{'schema':'gugak-reviewed-score/v1','title':value['title'],'context':score_context,
                         'quarter_bpm':value['quarter_bpm'],'notes':notes,'assumptions':assumptions,
                         'source':{'image_sha256':image_hash,'review_status':'machine-transcribed-unverified',
                                   'edition':f'Uploaded Jeonggan image {image_hash[:12]}',
                                   'model':model,'provider':provider['name'],'atlas_sha256':atlas.sha256,
                                   'scope':'Literal glyphs and fixed legend rules; unmeasured timing is preview policy'}},
                'regions':regions,'warnings':assumptions+notices,'provider':provider}
    except RecognitionError as exc:
        trace.update({'status':'failed','error':str(exc)});save();raise
