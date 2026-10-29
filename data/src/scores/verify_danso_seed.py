"""Read-only V5/V6 literal/score consistency audit; no production DB connection.

Run: uv run --no-project --with jsonschema python data/src/scores/verify_danso_seed.py
SQLite is only an isolated literal reader for the current INSERT-only migrations,
not a substitute for MySQL/Flyway deployment validation or human score approval.
"""
import sys,json,hashlib,sqlite3,re,importlib.metadata
from pathlib import Path
from fractions import Fraction
root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[3]; data=root/'data';sys.path.insert(0,str(data/'src/scores'))
import score_data as c
import jsonschema
seed=root/'backend/src/main/resources/db/migration/V6__seed_danso_songs.sql'
sql=seed.read_text();db=sqlite3.connect(':memory:');db.row_factory=sqlite3.Row
for table,columns in re.findall(r'INSERT INTO (\w+) \(([^)]+)\)',sql):
 db.execute(f'CREATE TABLE {table} ({columns})')
db.executescript(sql)
refs={name:json.loads((data/'reference'/f'{name}.json').read_text())['rows'] for name in ['songs','jangdans','instruments']}
schema=json.loads(c.SCHEMA_PATH.read_text()); rows=[]
catalog={x['id']: x for x in json.loads((data/'catalog/collected-scores/sources.json').read_text())['sources']}
for raw in db.execute('SELECT * FROM songs ORDER BY id'):
 s=dict(raw);sid=s['source'];ref=next(r for r in refs['songs'] if r['source']==sid);a=c.convert_answer(sid);actual=json.loads(s['score_json'])
 jsonschema.validate(actual,schema);assert not c.check(actual);assert a==actual,(sid,'generated != seed')
 folder=data/'annotations/scores'/sid;jgb=next(folder.glob('*.jgb.json'));review=next(folder.glob('*.review.json'));rv=json.loads(review.read_text());doc=json.loads(jgb.read_text());melody=c.melody_of(doc)
 assert actual['provenance']['jgbSha256']==hashlib.sha256(jgb.read_bytes()).hexdigest()
 assert actual['score']['sourceId']==actual['provenance']['sourceId']==sid==rv['sourceId']
 assert rv['sourceHashes'][sid]==catalog[sid]['sha256']
 for f in ['title','difficulty','default_bpm','description','is_scorable']: assert s[f]==ref[f],(sid,f)
 assert s['status']=='PUBLISHED' and ref['status']=='DRAFT'
 assert s['score_version']==actual['score']['scoreVersion']==1 and s['umulsai_text'] is None
 jang=dict(db.execute('SELECT * FROM jangdans WHERE id=?',(s['jangdan_id'],)).fetchone());jr=next(j for j in refs['jangdans'] if j['name']==jang['name'])
 assert jang['name']==ref['jangdan']==rv['sourceValues']['jangdan']==actual['music']['jangdan']
 for k in ['default_bpm','bpm_unit','source']:assert jang[k]==jr[k]
 assert json.loads(jang['pattern'])==jr['pattern']
 events=actual['events'];notes=[e for e in events if e['kind']=='NOTE'];rests=[e for e in events if e['kind']=='REST']
 assert len(notes)==s['note_count'];assert len({e['id'] for e in events})==len(events)
 cells=[cell for col in actual['columns'] for cell in col['cells']];assert len(cells)==rv['total']==rv['confirmed']
 # Independent source scan: emitted NOTE order must follow every explicit pitch token; ties/blanks create no note.
 tokens=re.findall(r'(?:하하배|하배|중청|배|청)?[황대태협고중유임이남무응]',melody)
 pitches=[(re.match(r'(하하배|하배|중청|배|청)?(.)',t).group(2),{'':0,'청':1,'중청':2,'배':-1,'하배':-2,'하하배':-3}[re.match(r'(하하배|하배|중청|배|청)?(.)',t).group(1) or '']) for t in tokens]
 assert pitches==[(e['yul'],e['octave']) for e in notes]
 assert sum(e['breathAfter'] for e in events)==melody.count('<')
 assert all(not e['sigimsae'] for e in events) and not re.search(r'[{}()\[\]]',melody)
 assert all(e['durationTick']>0 for e in events)
 total=sum(e['durationTick'] for e in events);assert Fraction(total,actual['music']['ticksPerBeat'])==len(cells)*Fraction(rv['sourceValues']['beatsPerJeonggan'])
 assert all(e['startTick']+e['durationTick']==events[i+1]['startTick'] for i,e in enumerate(events[:-1]))
 fixture=data/'tests/fixtures/scores'/f'{sid}.score.json'
 if fixture.exists():assert c.dumps(actual).encode()==fixture.read_bytes()
 rows.append(dict(id=s['id'],title=s['title'],source=sid,notes=len(notes),rests=len(rests),cells=len(cells),columns=len(actual['columns']),ties=melody.count('-'),blankCells=sum(not x.strip() for line in melody.splitlines() for x in line.split('|')),breaths=melody.count('<'),sigimsae=0,totalTicks=total,ticksPerBeat=actual['music']['ticksPerBeat'],beats=str(Fraction(total,actual['music']['ticksPerBeat'])),jgbSha256=hashlib.sha256(jgb.read_bytes()).hexdigest(),scoreSha256=hashlib.sha256(c.dumps(actual).encode()).hexdigest(),reviewSha256=hashlib.sha256(review.read_bytes()).hexdigest(),reviewBy=rv['answerApproval']['by'],reviewOn=rv['answerApproval']['on']))
inst=dict(db.execute('select * from instruments').fetchone());ir=refs['instruments'][0]
assert inst['code']==ir['code'] and inst['name']==ir['name']
assert inst['pitch_tolerance_cent']==ir['pitch_tolerance_cent'] and inst['is_active']==ir['is_active']
v5=(seed.parent/'V5__seed_danso_pitches.sql').read_text()
for table,columns in re.findall(r'INSERT INTO (\w+) \(([^)]+)\)',v5): db.execute(f'CREATE TABLE {table} ({columns})')
db.executescript(v5)
for key,id_key in [('range_low','range_low_pitch_id'),('range_high','range_high_pitch_id')]:
 pitch=dict(db.execute('SELECT * FROM pitches WHERE id=?',(inst[id_key],)).fetchone())
 assert (pitch['yul'],pitch['octave'])==(ir[key]['yul'],ir[key]['octave'])
out={'seedSha256':hashlib.sha256(seed.read_bytes()).hexdigest(),'converterSha256':hashlib.sha256((data/'src/scores/score_data.py').read_bytes()).hexdigest(),'jsonschema':importlib.metadata.version('jsonschema'),'songs':rows,'checks':'all assertions passed'}
print(json.dumps(out,ensure_ascii=False,indent=2))
