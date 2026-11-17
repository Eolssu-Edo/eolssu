"""스캔 결과의 공통 악보 연결 검사. 인식 품질은 별도 실제 모델 실행으로 검증한다."""
import io
import json
import os
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from omr_service import api


@pytest.mark.parametrize('code,message,expected', [
    (4, b'', True),
    (1, b'Traceback\nAssertionError: Jeonggan height is not in range: 161.5, [np.int32(309)]\n', True),
    (1, b'AssertionError: beta is not zero\n', False),
    (1, b'RuntimeError: CUDA out of memory\n', False),
    (137, b'Killed\n', False),
])
def test_only_known_geometry_failure_is_recoverable(code, message, expected):
    from types import SimpleNamespace
    from omr_service.jeongganbo_scan import recoverable_grid_failure
    assert recoverable_grid_failure(SimpleNamespace(returncode=code, stderr=message)) == expected


def test_scan_cell_keeps_supported_pitch_and_marks_unsupported_ornament():
    from omr_service.jeongganbo_scan import cell_text
    assert cell_text({'text':'청태:5','jg':':0 청태'}) == (':0 청태',None)
    assert cell_text({'text':'청태_흘림표:5','jg':':0 청태 흘림표'}) == (':0 청태 흘림표',None)
    assert cell_text({'text':'니나*:5','jg':':0 니나*'}) == ('','UNSUPPORTED_SYMBOL')
    assert cell_text({'text':'청태:2 니나*:5 청임:8'}) == (':2 청태 :8 청임','UNSUPPORTED_SYMBOL')
    assert cell_text({'text':'','jg':'','ink':0.1}) == ('','EMPTY_RECOGNITION')
    assert cell_text({'text':'','jg':'','ink':0}) == ('',None)


def test_text_probe_time_is_subtracted_before_scan(monkeypatch):
    from omr_service import jeongganbo_text, jeongganbo_scan
    clock=iter([10.,13.])
    monkeypatch.setattr(api,'monotonic',lambda:next(clock))
    def no_text(job):
        raise jeongganbo_text.NoTextLayer()
    monkeypatch.setattr(jeongganbo_text,'recognize',no_text)
    monkeypatch.setattr(jeongganbo_scan,'recognize',lambda job:job.time_budget_s)
    job=api.Job(None,'PDF',[],[],60)
    assert api.recognize_jeongganbo(job)==57


def test_tiny_image_without_grid_is_not_an_engine_error(tmp_path,monkeypatch):
    from omr_service import jeongganbo_scan as scan
    script=tmp_path/'empty.py';script.write_text('import sys;sys.exit(4)',encoding='utf-8')
    monkeypatch.setattr(scan,'READER',[sys.executable,str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR',str(tmp_path))
    buf=io.BytesIO();Image.new('RGB',(1,1),'white').save(buf,'PNG')
    api.app.dependency_overrides[api.get_fetcher]=lambda:lambda url,p,limit,deadline:p.write_bytes(buf.getvalue())
    try:
        out=TestClient(api.app).post('/v1/recognitions',json=dict(conversionId='tiny',notation='JEONGGANBO',jangdan='세마치',instrument='DANSO',files=[dict(url='https://local.invalid/tiny.png',contentType='image/png')])).json()
    finally:
        api.app.dependency_overrides.clear()
    assert out['failReason']=='NO_GRID_FOUND'


@pytest.mark.parametrize('instrument,expected', [
    ('DANSO', ['--yul', 'danso']),
    ('SOGEUM', ['--yul', 'sogeum']),
    ('JEONGAK_DAEGEUM', ['--yul', 'jeongak-daegeum']),
    ('SANJO_DAEGEUM', []),
    ('UNKNOWN_INSTRUMENT', []),
])
def test_reader_limits_pitch_candidates_by_instrument_on_every_size_retry(tmp_path,monkeypatch,instrument,expected):
    from omr_service import jeongganbo_scan as scan
    calls=tmp_path/'calls.jsonl'
    script=tmp_path/'reader.py'
    # 모든 크기에서 격자를 못 찾게 해 마지막 --grid-boxes 시도까지 인자를 남긴다.
    script.write_text(f'''import json,sys
with open({str(calls)!r},'a',encoding='utf-8') as f:
    f.write(json.dumps(sys.argv[1:])+'\\n')
sys.exit(4)
''',encoding='utf-8')
    monkeypatch.setattr(scan,'READER',[sys.executable,str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR',str(tmp_path))
    buf=io.BytesIO();Image.new('RGB',(300,400),'white').save(buf,'PNG')
    api.app.dependency_overrides[api.get_fetcher]=lambda:lambda url,p,limit,deadline:p.write_bytes(buf.getvalue())
    try:
        out=TestClient(api.app).post('/v1/recognitions',json=dict(conversionId='yul',notation='JEONGGANBO',jangdan='세마치',instrument=instrument,files=[dict(url='https://local.invalid/page.png',contentType='image/png')])).json()
    finally:
        api.app.dependency_overrides.clear()
    assert out['failReason']=='NO_GRID_FOUND'
    runs=[json.loads(line) for line in calls.read_text(encoding='utf-8').splitlines()]
    assert len(runs)==4 and '--grid-boxes' in runs[-1]
    for argv in runs:
        assert [a for i,a in enumerate(argv) if a=='--yul' or (i and argv[i-1]=='--yul')]==expected
        assert '--batch-size' in argv


@pytest.mark.skipif(not os.getenv('JGB_GENERATOR_DIR'),reason='Node 변환기 필요')
@pytest.mark.parametrize('geometry_assertion', [False, True])
@pytest.mark.parametrize('width,accepted,expected', [
    (1907, (2200, 2300), [84, 168, 84, 84]),
    (3000, (1950, 2050), [150, 300, 150, 150]),
])
def test_image_grid_size_retry_keeps_boxes_in_original_coordinates(tmp_path,monkeypatch,width,accepted,expected,geometry_assertion):
    from omr_service import jeongganbo_scan as scan
    script=tmp_path/'reader.py'
    script.write_text(f'''import json,sys
from pathlib import Path
from PIL import Image
with Image.open(sys.argv[1]) as image:
    if not {accepted[0]} <= image.width <= {accepted[1]}:
        if {geometry_assertion!r}:
            raise AssertionError('Jeonggan height is not in range: 161.5, [309, 279]')
        sys.exit(4)
    if {width} == 3000 and '--grid-boxes' not in sys.argv:
        sys.exit(4)
out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
cells=[dict(text='청태:5',conf=.2,ink=.1,x=100,y=200,w=100,h=100) for _ in range(8)]
(out/'cells.json').write_text(json.dumps(dict(beats=4,cells=cells)))
''',encoding='utf-8')
    monkeypatch.setattr(scan,'READER',[sys.executable,str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR',str(tmp_path))
    buf=io.BytesIO();Image.new('RGB',(width,2600),'white').save(buf,'PNG')
    api.app.dependency_overrides[api.get_fetcher]=lambda:lambda url,p,limit,deadline:p.write_bytes(buf.getvalue())
    try:
        out=TestClient(api.app).post('/v1/recognitions',json=dict(conversionId='resized-grid',notation='JEONGGANBO',jangdan='4박 기본 반주',instrument='SOGEUM',files=[dict(url='https://local.invalid/page.png',contentType='image/png')])).json()
    finally:
        api.app.dependency_overrides.clear()
    assert out['status']=='DONE',out
    assert len(out['score']['events'])==8
    assert out['issues'][0]['bbox']==expected
    assert out['issues'][0]['cellId']=='c0-j0'


@pytest.mark.skipif(not os.getenv('JGB_GENERATOR_DIR'),reason='Node 변환기 필요')
def test_image_scan_reaches_default_engine_and_preserves_issue_ids(tmp_path,monkeypatch):
    from omr_service import jeongganbo_scan as scan
    script=tmp_path/'reader.py'
    script.write_text('''import json,sys
from pathlib import Path
out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
cells=[dict(text=t,jg=j,conf=c,ink=.1,x=10,y=i*20,w=20,h=20,gak=0) for i,(t,j,c) in enumerate([
 ('청태:5',':0 청태',.99),('니나*:5',':0 니나*',.99),('청임:5',':0 청임',.2),
 ('미해독:5','',.99),('미해독:5','',.99),('미해독:5','',.99)])]
(out/'cells.json').write_text(json.dumps(dict(beats=3,cells=cells)))
''',encoding='utf-8')
    monkeypatch.setattr(scan,'READER',[sys.executable,str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR',str(tmp_path))
    buf=io.BytesIO();Image.new('RGB',(200,200),'white').save(buf,'PNG')
    api.app.dependency_overrides[api.get_fetcher]=lambda:lambda url,p,limit,deadline:p.write_bytes(buf.getvalue())
    try:
        out=TestClient(api.app).post('/v1/recognitions',json=dict(conversionId='scan-test',notation='JEONGGANBO',jangdan='세마치',instrument='SOGEUM',files=[dict(url='https://local.invalid/page.png',contentType='image/png')])).json()
    finally:
        api.app.dependency_overrides.clear()
    assert out['status']=='DONE',out
    assert out['engine']=='jeongganbo-omr'
    assert len(out['score']['columns'][0]['cells'])==3
    assert len(out['score']['columns'])==2
    assert any(i['cellId']=='c1-j2' for i in out['issues'])
    assert any(i['kind']=='UNREADABLE' and i['cellId']=='c0-j1' for i in out['issues'])
    assert any(i['kind']=='LOW_CONFIDENCE' and i['cellId']=='c0-j2' for i in out['issues'])
    assert all(i['page']==0 and i['bbox'] for i in out['issues'])


def test_grid_places_use_reader_numbers_and_fall_back_to_order():
    from omr_service.jeongganbo_scan import grid_places
    numbered = [dict(gak=g, jeonggan=j) for g, j in [(0, 0), (0, 1), (0, 2), (1, 0), (1, 1)]]
    assert grid_places(numbered, 3) == [(0, 0), (0, 1), (0, 2), (1, 0), (1, 1)]
    assert grid_places([dict(gak=0)] * 4, 3) == [(0, 0), (0, 1), (0, 2), (1, 0)]  # 옛 리더 출력
    assert grid_places([dict(gak=0, jeonggan=0), dict(gak=0, jeonggan=0)], 3) == [(0, 0), (0, 1)]
    assert grid_places([dict(gak=0, jeonggan=3)], 3) == [(0, 0)]


@pytest.mark.skipif(not os.getenv('JGB_GENERATOR_DIR'),reason='Node 변환기 필요')
def test_split_cell_is_flagged_where_the_reader_placed_it(tmp_path,monkeypatch):
    from omr_service import jeongganbo_scan as scan
    script=tmp_path/'reader.py'
    script.write_text('''import json,sys
from pathlib import Path
out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
cells=[dict(text='청태:5',conf=.99,ink=.1,x=10,y=i*20,w=20,h=20,gak=i//3,jeonggan=i%3) for i in range(6)]
cells[4]['split']=True
(out/'cells.json').write_text(json.dumps(dict(beats=3,cells=cells)))
''',encoding='utf-8')
    monkeypatch.setattr(scan,'READER',[sys.executable,str(script)])
    monkeypatch.setenv('JEONGGANBO_OMR_DIR',str(tmp_path))
    buf=io.BytesIO();Image.new('RGB',(200,200),'white').save(buf,'PNG')
    api.app.dependency_overrides[api.get_fetcher]=lambda:lambda url,p,limit,deadline:p.write_bytes(buf.getvalue())
    try:
        out=TestClient(api.app).post('/v1/recognitions',json=dict(conversionId='split',notation='JEONGGANBO',jangdan='세마치',instrument='SOGEUM',files=[dict(url='https://local.invalid/page.png',contentType='image/png')])).json()
    finally:
        api.app.dependency_overrides.clear()
    assert out['status']=='DONE',out
    assert [len(c['cells']) for c in out['score']['columns']]==[3,3]
    assert [(i['cellId'],i['detail']) for i in out['issues']]==[('c1-j1',{'confidence':.99,'reason':'GRID_SPLIT'})]

def _column_image(tmp_path, thick_after, *, columns=2, cells=12, h=40, w=30, thick=4):
    """정간 cells개가 세로로 쌓인 줄 columns개. thick_after 번째 정간 아래 선만 굵게 그린다(S15P31E102-1068)."""
    from PIL import ImageDraw
    image = Image.new('L', (columns * (w + 20) + 20, cells * h + 40), 255)
    draw = ImageDraw.Draw(image)
    boxes = []
    for col in range(columns):
        x = 20 + col * (w + 20)
        for j in range(cells + 1):
            y = 20 + j * h
            width = thick if j in thick_after else 1
            draw.rectangle([x, y - width // 2, x + w, y - width // 2 + width - 1], fill=0)
        boxes += [dict(x=x, y=20 + j * h + 1, w=w, h=h - 2) for j in range(cells)]
    path = tmp_path / 'grid.png'
    image.save(path)
    return path, boxes


def test_semachi_column_read_as_one_gak_is_split_at_thick_gak_lines(tmp_path):
    from omr_service.jeongganbo_scan import gak_size_from_lines
    # 아리랑 사진처럼 12정간 줄에 3정간마다 굵은 각 구분선이 있다. 세마치 한 각은 3정간(정간당 1박)이다.
    path, cells = _column_image(tmp_path, {3, 6, 9})
    assert gak_size_from_lines(path, cells, 12, '세마치') == 3


def test_gak_size_is_kept_without_clear_regular_thick_lines(tmp_path):
    from omr_service.jeongganbo_scan import gak_size_from_lines
    plain, cells = _column_image(tmp_path, set())
    assert gak_size_from_lines(plain, cells, 12, '세마치') == 12  # 굵은 선이 없으면 고치지 않는다
    irregular, cells = _column_image(tmp_path, {3, 7})
    assert gak_size_from_lines(irregular, cells, 12, '세마치') == 12  # 간격이 일정하지 않으면 고치지 않는다


def test_gak_size_matching_the_jangdan_is_not_reinterpreted(tmp_path):
    from omr_service.jeongganbo_scan import gak_size_from_lines
    # 굿거리 12정간 각은 이미 정간당 1/3박으로 맞으므로 줄 안의 굵은 선이 있어도 바꾸지 않는다.
    path, cells = _column_image(tmp_path, {3, 6, 9})
    assert gak_size_from_lines(path, cells, 12, '굿거리') == 12
