"""MALerLab 스캔 모델 → 검수가 필요한 공통 악보 초안. 텍스트·오선보와 독립 경로다."""
import json
import logging
import os
import re
import sys
import shutil
import tempfile
from fractions import Fraction
from pathlib import Path
from time import monotonic

from PIL import Image

from omr_service.jeongganbo_text import (SCORES, USUAL_BEATS, _last_line, _run,
                                        beats_per_jeonggan, lines_to_score, score_data)
from omr_service.staff_engine import AUDIVERIS_LOCK
from jeongganbo_tokens import jg_like
from hanbae_marks import image_marks
from omr_service.jeongganbo_tempo import attach_marks

READER = [sys.executable, '-X', 'utf8', str(SCORES / 'jeongganbo_omr.py')]
ENGINE_VERSION = 'e95045f-v1.0.0-r8'
# instruments.code → 리더 YUL_PRESETS 이름. 목록 근거: docs/knowledge/data/jeongganbo-omr-accuracy.md.
# 산조대금은 정악 대금 목록과 조율·음역 근거가 달라 제한하지 않는다(목록 없는 악기는 기존 결과와 같다).
YUL_PRESETS = {'DANSO': 'danso', 'SOGEUM': 'sogeum', 'JEONGAK_DAEGEUM': 'jeongak-daegeum'}
MIN_CONFIDENCE = .8  # 검수 기준이며, 보정된 정확도 확률이 아니다.
log = logging.getLogger('omr_service.api')
TOKEN = re.compile(rf'(?:{score_data.YUL}(?:_[^:\s]+)?|-|쉼표|쉼):(?:[0-9]|1[0-5])\Z')


def recoverable_grid_failure(run):
    """지원 리더의 크기 검출 실패만 재시도한다. 모델·환경 오류는 전파한다."""
    return run.returncode == 4 or (run.returncode == 1 and re.fullmatch(
        r'AssertionError: Jeonggan height is not in range: [0-9.]+, \[.*\]',
        _last_line(run.stderr)) is not None)


def cell_text(cell):
    text = cell.get('text', '')
    supported = [token for token in text.split() if TOKEN.fullmatch(token)]
    if len(supported) != len(text.split()):
        return jg_like(' '.join(supported)), 'UNSUPPORTED_SYMBOL'
    if not text and cell.get('ink', 1) > .005:
        return '', 'EMPTY_RECOGNITION'
    return jg_like(text), None


def grid_places(cells, beats):
    """칸마다 (쪽 안 각, 정간). 리더가 격자 자리로 매긴 gak·jeonggan을 쓰고, 없거나 겹치면 순번으로 나눈다."""
    places = [(cell.get('gak'), cell.get('jeonggan')) for cell in cells]
    if (all(isinstance(g, int) and isinstance(j, int) and g >= 0 and 0 <= j < beats for g, j in places)
            and len(set(places)) == len(places) and places == sorted(places)):
        return places
    return [(j // beats, j % beats) for j in range(len(cells))]


def recognize(job):
    from omr_service import api
    model_dir = os.environ.get('JEONGGANBO_OMR_DIR')
    if not model_dir:
        raise RuntimeError('JEONGGANBO_OMR_DIR is not set')
    deadline = monotonic() + job.time_budget_s
    if not AUDIVERIS_LOCK.acquire(timeout=max(0, deadline - monotonic())):
        raise TimeoutError()
    preset = YUL_PRESETS.get(job.request.instrument)
    yul_options = ['--yul', preset] if preset else []
    try:
        lines, issues, tempo_cues = [], [], []
        with tempfile.TemporaryDirectory(prefix='jeongganbo-scan-') as folder:
            work = Path(folder)
            for i, page in enumerate(job.pages):
                page_deadline = min(deadline, monotonic() + api.PAGE_TIME_S)
                report, scale = None, 1
                # 리더의 고정 칸 너비는 85~110px, 기준 쪽 높이는 3091px다.
                # 2600px 그림과 넓은 전자 정간도 같은 시간 예산 안에서 시도한다.
                scales = dict.fromkeys((1, .5, 3091 / page.height, 2 / 3))
                for scale in scales:
                    size = (max(1, round(page.width * scale)), max(1, round(page.height * scale)))
                    if size[0] * size[1] > api.MAX_PIXELS:
                        continue
                    out = work / f'page-{i}-{scale}'
                    image = page.path
                    if scale != 1:
                        image = work / f'scaled-{i}.png'
                        with Image.open(page.path) as source:
                            source.resize(size, Image.Resampling.LANCZOS).save(image)
                    grid_options = ['--grid-boxes'] if scale == 2 / 3 else []
                    run = _run(READER + [str(image), '0', str(out), '--image', '--omr-dir', model_dir,
                                        '--model-cache-dir', str(work), '--batch-size', '16',
                                        '--beats', '0', '--fill-grid', '--keep-empty-columns',
                                        '--title', 'page', *yul_options, *grid_options], page_deadline)
                    if recoverable_grid_failure(run):
                        log.info('jeongganbo-scan page=%d scale=%s grid retry: %s', i, scale, _last_line(run.stderr))
                        continue
                    if run.returncode:
                        log.info('jeongganbo-scan page=%d failed: %s', i, _last_line(run.stderr))
                        raise RuntimeError('jeongganbo scan reader failed')
                    report = json.loads((out / 'cells.json').read_text(encoding='utf-8'))
                    break
                whole = dict(eventId=None, cellId=None, page=i, bbox=[0, 0, page.width, page.height])
                if not report or not report['cells']:
                    issues.append(dict(kind='UNREADABLE', **whole, detail={'reason':'NO_GRID'}))
                    continue
                beats, cells = int(report['beats']), report['cells']
                first_gak = len(lines)
                places = grid_places(cells, beats)
                geometry = [cell | {'gak': gak} for cell, (gak, _) in zip(cells, places)]
                with Image.open(image) as source_image:
                    detected = image_marks(source_image, geometry)
                tempo_cues += [cue | {'gak': first_gak + cue['gak'], 'page': i,
                                      'bbox': [round(v / scale) for v in cue['bbox']]}
                               if 'gak' in cue else cue | {'page': i, 'bbox': [round(v / scale) for v in cue['bbox']]}
                               for cue in detected]
                grid = [[''] * beats for _ in range(places[-1][0] + 1)]
                del grid[-1][places[-1][1] + 1:]  # 마지막 각은 읽은 정간까지만 둔다
                for cell, (gak, jeonggan) in zip(cells, places):
                    text, reason = cell_text(cell)
                    grid[gak][jeonggan] = text
                    at = dict(eventId=None, cellId=f'c{first_gak + gak}-j{jeonggan}', page=i,
                              bbox=[round(cell[k] / scale) for k in ('x','y','w','h')])
                    if reason:
                        issues.append(dict(kind='UNREADABLE', **at,
                                           detail={'reason':reason,'text':cell.get('text','')}))
                    grid_reason = 'GRID_SPLIT' if cell.get('split') else 'GRID_FILLED' if cell.get('filled') else None
                    if cell.get('conf') is None or cell['conf'] < MIN_CONFIDENCE or grid_reason:
                        issues.append(dict(kind='LOW_CONFIDENCE', **at,
                                           detail={'confidence':cell.get('conf'),'reason':grid_reason or 'MODEL_CONFIDENCE'}))
                lines += [' | '.join(gak) for gak in grid]
                if len(grid[-1]) < beats:
                    issues.append(dict(kind='LOW_CONFIDENCE', **whole, detail={'reason':'PARTIAL_GAK'}))
                # 칸 위치는 JSON에 보존했으므로 무거운 쪽 그림은 다음 쪽 전에 지운다.
                shutil.rmtree(out)
        if not lines:
            raise api.RecognitionFailed('NO_GRID_FOUND')

        def bpj_of(doc):
            per_gak = int(doc['controls']['beats'])
            bpj = beats_per_jeonggan(job.request.jangdan, per_gak)
            if bpj not in USUAL_BEATS and not (job.request.jangdan == '세령산' and bpj == Fraction(3, 2)):
                issues.append(dict(kind='JANGDAN_MISMATCH', eventId=None, cellId=None, page=None, bbox=None,
                                   detail={'jangdan':job.request.jangdan,'jeongganPerGak':per_gak}))
            return bpj

        doc, score = lines_to_score(lines, job.request, job.request.jangdan, bpj_of, deadline,
                                    'jeongganbo-scan/' + ENGINE_VERSION, continue_explicit_ties=True)
        if not any(e['kind']=='NOTE' for e in score['events']):
            raise api.RecognitionFailed('NO_GRID_FOUND')
        extra, tempo_issues = attach_marks(score, tempo_cues)
        return dict(engine='jeongganbo-omr', engineVersion=ENGINE_VERSION, jgb=doc, score=score,
                    issues=issues + tempo_issues, **extra)
    finally:
        AUDIVERIS_LOCK.release()
