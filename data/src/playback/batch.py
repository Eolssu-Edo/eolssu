"""Prepare source-preserving Danso auditions from a reviewed local catalog.

python -m data.src.playback.batch --catalog catalog.json --assets assets.json --out output
The catalog carries per-edition evidence and decisions; this module does not search
for tempo, identify a traditional mode, transpose notes or suppress failed symbols.
"""
import argparse
from copy import deepcopy
import csv
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import time
from .__main__ import dump, run
from .jgb import NOTE, SYMBOLS, YUL, adaptive_breath
from .score import require
from .audio import FORMATS, add_audio_arguments, resolve_encoder


def prepare_jgb(source, tempo, corrections):
    document=deepcopy(source)
    controls=document['controls']
    bpm=tempo['jeonggan_bpm']
    require(isinstance(bpm,(int,float)) and not isinstance(bpm,bool) and 1<=bpm<=800,
            '정간 BPM 범위를 확인하세요')
    changes=[]
    def assign(obj,key,value,path):
        previous=obj.get(key)
        if previous!=value:
            changes.append({'field':path,'before':previous,'after':value})
            obj[key]=value
    if document['v']==1:
        require(document.get('ornInstrument') in (None,'all','danso'),'원본의 악기가 단소 분류와 다릅니다')
        assign(document,'ornInstrument','danso','ornInstrument')
        parts=[document]
    else:
        parts=document['parts']
        for i,part in enumerate(parts):
            require(part.get('instrument') in (None,'all','danso'),'원본의 악기가 단소 분류와 다릅니다')
            assign(part,'instrument','danso',f'parts[{i}].instrument')
    seen=set()
    for correction in corrections:
        if 'field' in correction:
            field=correction['field']
            allowed=[['controls','gakBeats'],['melody']] if document['v']==1 else [
                ['controls','gakBeats'],*[['parts',i,'melody'] for i in range(len(parts))]]
            require(field in allowed,'원보 교정 필드는 melody 또는 gakBeats만 허용합니다')
            key=tuple(field)
            require(key not in seen,'같은 필드의 교정은 하나의 before/after로 합치세요')
            seen.add(key)
            obj=document
            for segment in field[:-1]:obj=obj[segment]
            require(obj.get(field[-1])==correction['before'],'교정 기록과 원본 필드가 다릅니다')
            assign(obj,field[-1],correction['after'],str(field))
            continue
        key=(correction.get('part',1),correction['gak'],correction['cell'])
        require(key not in seen,'같은 원본 칸의 교정은 하나의 before/after로 합치세요')
        seen.add(key)
        part=parts[key[0]-1]
        lines=part['melody'].splitlines(keepends=True)
        line=lines[key[1]-1]
        ending=re.search(r'(\r\n|\r|\n)$',line)
        ending=ending[0] if ending else ''
        cells=(line[:-len(ending)] if ending else line).split('|')
        require(cells[key[2]-1]==correction['before_cell'],'교정 기록과 원본 정간이 다릅니다')
        cells[key[2]-1]=correction['after_cell']
        lines[key[1]-1]='|'.join(cells)+ending
        assign(part,'melody',''.join(lines),f'part[{key[0]}].melody')
    assign(controls,'tempoBpm',bpm,'controls.tempoBpm')
    assign(document,'tempoBpmUserSet',True,'tempoBpmUserSet')
    assign(controls,'playJanggu',False,'controls.playJanggu')
    assign(controls,'playSigimsae',True,'controls.playSigimsae')
    melody='\n'.join(p['melody'] for p in parts)
    symbols=re.findall(r'\{([^}@]+)(?:@[^}]+)?\}',melody)
    relative=any(name in SYMBOLS for name in symbols)
    clean=re.sub(r'\{[^}]+\}','',melody)
    observed=sorted({YUL.index(match[2]) for match in NOTE.finditer(clean)})
    inferred=False
    if relative and controls.get('joPreset') in (None,'','all') and observed==[0,2,5,7,10]:
        assign(controls,'joPreset','jung-pyeong','controls.joPreset')
        inferred=True
    document['playback']={
        'schema':'danso-preparation/v1','tempo':deepcopy(tempo),'metadata_changes':changes,
        'source_corrections':deepcopy(corrections),'observed_scale':observed if inferred else None,
        'scale_policy':'Exact five written pitch classes for relative-step playback; not a traditional mode classification.' if inferred else 'Use source declaration; no partial-scale inference.',
        'instrument_basis':'Explicit Danso catalog category; no automatic pitch transposition.',
    }
    return document,changes


def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def audition_html(records):
    cards=[]
    units={'dotted_quarter':'점4분음표','quarter_note':'4분음표',
           'eighth_note':'8분음표','half_note':'2분음표','jeonggan':'정간'}
    for record in records:
        for output in record.get('outputs',[]):
            tempo=record['tempo'];name=html.escape(record['title'])
            unit=units.get(tempo['beat_unit'],tempo['beat_unit'])
            text=f"{unit} {tempo.get('bpm',tempo.get('jeonggan_bpm'))}회/분"
            if tempo.get('jeonggan_bpm') and unit!='정간':text+=f" · {tempo['jeonggan_bpm']}정간/분"
            text+=f" · {output['seconds']:.1f}초 · {output['notes']}음"
            evidence=html.escape(tempo.get('label',tempo.get('evidence_type','')))
            audio_file=html.escape(output['audio_file'])
            cards.append(f'<section data-search="{name}"><h2>{name}</h2><p>{html.escape(record.get("edition",""))} · 성부 {output["part"]}</p><p>{html.escape(text)}<br>{evidence}</p><audio controls preload="none" src="{audio_file}"></audio><p><a href="{audio_file}" download>음원</a> · <a href="{output["midi"]}">MIDI</a> · <a href="{record["prepared"]}">빠르기·근거 포함 JSON</a> · <a href="{output["report"]}">변환 기록</a></p></section>')
    failures=''.join(f'<tr><td>{html.escape(r["title"])}</td><td>{html.escape(r.get("error",""))}</td></tr>' for r in records if r['status']!='generated')
    count=sum(len(r.get('outputs',[])) for r in records)
    items=len({r['title'] for r in records if r['status']=='generated'})
    return '''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>단소 악보 미리듣기 — 표현·빠르기 반영</title><style>body{background:#f5f5ef;color:#233d32;font:16px/1.65 'Malgun Gothic',sans-serif;margin:0}main{max-width:1120px;margin:auto;padding:30px}h1{font-size:30px}h2{font-size:20px;margin:0}p{overflow-wrap:anywhere}input{font:inherit;padding:12px;border:1px solid #b8cabc;border-radius:8px;width:min(90%,480px)}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px;margin-top:22px}section{border:1px solid #cfdbd0;border-radius:12px;background:white;padding:20px}section p{font-size:13px;color:#526a5c}audio{width:100%}a{color:#11694b}.notice{padding:18px;background:#e6ede3;border-radius:10px}td{padding:8px;border-bottom:1px solid #bbb}summary{cursor:pointer;padding:20px 0}</style><main><h1>단소 악보 미리듣기</h1>'''+f'<p>{items}개 목록 항목 · {count}개 음원 · {sum(r["status"]!="generated" for r in records)}개 파일 보류</p>'+'''<p class="notice">숨표·농음·흘림을 적용한 합성 미리듣기입니다. 공식 수치가 없는 빠르기는 승인된 연습용 선택값으로 표시합니다. 청성곡은 원보에 수록된 단소 오선보 2줄입니다. 원본과 전사·표현의 전문가 검수는 별도입니다.</p><p><a href="tempo-decisions.json">전체 빠르기 JSON</a> · <a href="inventory.csv">처리 목록</a> · <a href="manifest.json">전체 재현 기록</a></p><input id="search" aria-label="곡 검색" placeholder="곡 검색"><div class="cards">'''+''.join(cards)+'</div><details><summary>보류 상세</summary><table>'+failures+'</table></details><footer><p>이 2차 저작물은 서울예술과학센터에서 개발한 국악 가상악기 음원 라이브러리를 사용하였습니다. (서울예술과학센터: catsnu.com, 문의: catantsnu@gmail.com)</p></footer></main><script>const ps=[...document.querySelectorAll("audio")];ps.forEach(p=>p.addEventListener("play",()=>ps.forEach(q=>{if(q!==p)q.pause()})));document.querySelector("#search").addEventListener("input",e=>document.querySelectorAll("section").forEach(s=>s.hidden=!s.dataset.search.includes(e.target.value)));</script></html>'


def run_batch(catalog_path,assets,out,*,audio_format='m4a',ffmpeg=None,ffprobe=None):
    require(audio_format in FORMATS,'오디오 출력 형식은 m4a 또는 wav여야 합니다')
    catalog_path=Path(catalog_path).resolve();out=Path(out).resolve()
    catalog=json.loads(catalog_path.read_text(encoding='utf-8-sig'))
    inputs=[catalog_path,Path(assets).resolve()]
    for item in catalog['items']:
        inputs.append((catalog_path.parent/item['input']).resolve())
        if item.get('transcription_map'):
            inputs.append((catalog_path.parent/item['transcription_map']).resolve())
    require(not any(path.is_relative_to(out) for path in inputs), '출력 폴더 안의 원본 입력을 덮어쓸 수 없습니다')
    other_format='wav' if audio_format=='m4a' else 'm4a'
    require(not any((out/'tracks').glob(f'*/danso.{other_format}')),
            '출력 폴더에 이전 형식의 음원이 있습니다. 새 --out 폴더를 사용하세요')
    encoder=resolve_encoder(ffmpeg,ffprobe) if audio_format=='m4a' else None
    out.mkdir(parents=True,exist_ok=True)
    records=[];started=time.monotonic()
    for i,item in enumerate(catalog['items'],1):
        record=deepcopy(item);record['id']=f'{i:03d}';record['outputs']=[]
        path=(catalog_path.parent/item['input']).resolve()
        try:
            payload=path.read_bytes()
            record['original_sha256']=hashlib.sha256(payload).hexdigest()
            original=out/'originals'/f'{i:03d}.json';original.parent.mkdir(exist_ok=True);original.write_bytes(payload)
            source=json.loads(payload.decode('utf-8-sig'))
            require(isinstance(source,dict), '악보 JSON의 최상위 값은 객체여야 합니다')
            is_jgb='v' in source
            if is_jgb:
                require(all(not c.get('source_sha256') or c['source_sha256']==record['original_sha256']
                            for c in item.get('corrections',[])), '교정 대상의 원본 SHA256이 다릅니다')
                prepared,changes=prepare_jgb(source,item['tempo'],item.get('corrections',[]))
                parts=item.get('parts',list(range(1,len(prepared.get('parts',[{}]))+1)))
            else:
                prepared=deepcopy(source);changes=[];parts=[None]
                prepared['quarter_bpm']=item['tempo']['quarter_bpm']
                prepared['source']['tempo_decision']=item['tempo']
                for note in prepared['notes']:
                    if note.get('breath_seconds'):
                        before={k:note.get(k) for k in ('breath_seconds','release_seconds')}
                        note.update(adaptive_breath(note['duration'],prepared['quarter_bpm']))
                        changes.append({'note':note['id'],'breath_before':before,'breath_after':{k:note[k] for k in ('breath_seconds','release_seconds')}})
                prepared['assumptions']=[a for a in prepared.get('assumptions',[]) if '120ms' not in a]
                prepared['assumptions'].append('Breath+release bounded to half the preceding note; this is a documented audition policy.')
            record['metadata_changes']=changes
            folder=out/'prepared';folder.mkdir(exist_ok=True)
            target=folder/f'{i:03d}{".jgb" if is_jgb else ".score"}.json'
            dump(target,prepared);record['prepared']=target.relative_to(out).as_posix()
            for part in parts:
                track=out/'tracks'/f'{i:03d}-p{part or 1}'
                result=run(target,assets,track,['danso'],part=part,audio_format=audio_format,encoder=encoder)
                result['catalog_source']={k:v for k,v in record.items() if k!='outputs'}
                result['original_input_sha256']=record['original_sha256']
                result['prepared_input_sha256']=digest(target)
                if item.get('transcription_map'):
                    source_map=(catalog_path.parent/item['transcription_map']).resolve()
                    shutil.copyfile(source_map,track/'transcription-map.json')
                    result['transcription_map_sha256']=digest(source_map)
                dump(track/'manifest.json',result)
                a=result['results'][0]
                record['outputs'].append({'part':part or 1,'notes':a['notes'],'seconds':a['seconds'],
                    'audio_file':(track/a['audio']['file']).relative_to(out).as_posix(),
                    'midi':(track/'danso.mid').relative_to(out).as_posix(),
                    'report':(track/'manifest.json').relative_to(out).as_posix(),'audio':a['audio']})
            record['status']='generated'
        except (OSError,ValueError,IndexError,KeyError) as exc:
            record.update(status='blocked',error=str(exc))
        print(record['id'],record['status'],record['title'],record.get('error',''),flush=True)
        records.append(record);dump(out/'progress.json',records)
    dump(out/'manifest.json',{'schema':'danso-batch/v2','audio_format':audio_format,'catalog_sha256':digest(catalog_path),
                              'catalog_metadata':{k:v for k,v in catalog.items() if k!='items'},
                              'batch_source_sha256':digest(__file__),
                              'elapsed_seconds':time.monotonic()-started,'records':records})
    dump(out/'tempo-decisions.json',[{'id':r['id'],'title':r['title'],'input':r['input'],**r['tempo']} for r in records])
    with (out/'inventory.csv').open('w',encoding='utf-8-sig',newline='') as file:
        writer=csv.writer(file);writer.writerow(['ID','곡','입력','상태','정간 BPM','기준 BPM','기준 단위','근거 유형','오류'])
        for r in records:writer.writerow([r['id'],r['title'],r['input'],r['status'],r['tempo'].get('jeonggan_bpm'),r['tempo'].get('bpm'),r['tempo']['beat_unit'],r['tempo']['evidence_type'],r.get('error','')])
    (out/'index.html').write_text(audition_html(records),encoding='utf-8')
    return records


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog',required=True);parser.add_argument('--assets',required=True);parser.add_argument('--out',required=True)
    add_audio_arguments(parser)
    args=parser.parse_args()
    try:
        run_batch(args.catalog,args.assets,args.out,audio_format=args.audio_format,ffmpeg=args.ffmpeg,ffprobe=args.ffprobe)
    except ValueError as exc:
        parser.error(str(exc))
