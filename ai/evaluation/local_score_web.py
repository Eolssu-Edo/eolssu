"""로컬 녹음의 기준 점수를 확인하는 PC 전용 화면."""

from dataclasses import asdict
from pathlib import Path
import tempfile
import threading
from urllib.parse import urlsplit

from fastapi import FastAPI, Request
from fastapi.responses import FileResponse, JSONResponse
from starlette.concurrency import run_in_threadpool

from ai_service.api import MAX_DOWNLOAD_BYTES, RecordingTooLong
from ai_service.pipelines.audio import DecodeError
from ai_service.pipelines.scoring import HWANG_HZ
from evaluation.local_score import AnalysisDurationExceeded, _SCORES, prepare_recording, score_recording
from evaluation.score_experiment import Experiment, score_experiment

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
_HTML = Path(__file__).with_name('local_score_web.html')
_LOCK = threading.Lock()


@app.middleware('http')
async def local_only(request, call_next):
    host = request.headers.get('host', '')
    try:
        hostname = urlsplit('http://' + host).hostname
    except ValueError:
        hostname = None
    if hostname != '127.0.0.1' or (request.method != 'GET' and request.headers.get('origin') != 'http://' + host):
        return JSONResponse({'error': '로컬 화면에서만 사용할 수 있습니다.'}, status_code=403)
    response = await call_next(request)
    response.headers['Cache-Control'] = 'no-store'
    return response


@app.get('/')
def home():
    return FileResponse(_HTML, media_type='text/html; charset=utf-8')


@app.post('/score')
@app.post('/compare')
async def analyze(request: Request):
    if request.headers.get('content-type', '').split(';')[0] != 'application/octet-stream':
        return JSONResponse({'error': '녹음 파일을 선택해 주세요.'}, status_code=400)
    try:
        source = request.query_params['score_id']
        bpm = float(request.query_params['bpm'])
        start = float(request.query_params['start'])
        if source not in _SCORES:
            raise ValueError
        if request.url.path == '/compare':
            values = request.query_params
            spec = Experiment(pitch_cent=float(values['pitch_cent']), duration_ratio=float(values['duration_ratio']),
                              onset_spread=float(values['onset_spread']), pitch_penalty=float(values['pitch_penalty']),
                              timing_penalty=float(values['timing_penalty']), large_error_exponent=float(values['large_error_exponent']),
                              weights=(float(values['weight_pitch']), float(values['weight_timing']), float(values['weight_duration'])))
            spec.validate()
    except (KeyError, ValueError):
        return JSONResponse({'error': '악보와 BPM, 시작 위치를 확인해 주세요.'}, status_code=400)
    if not _LOCK.acquire(blocking=False):
        return JSONResponse({'error': '이전 분석이 끝난 뒤 다시 시도해 주세요.'}, status_code=409)
    try:
        with tempfile.TemporaryDirectory(prefix='eolssu-local-score-') as directory:
            audio = Path(directory) / 'recording.m4a'
            size = 0
            with audio.open('wb') as output:
                async for chunk in request.stream():
                    size += len(chunk)
                    if size > MAX_DOWNLOAD_BYTES:
                        return JSONResponse({'error': '녹음 파일이 25MiB를 넘습니다.'}, status_code=413)
                    output.write(chunk)
            if not size:
                return JSONResponse({'error': '녹음 파일이 비어 있습니다.'}, status_code=400)
            try:
                if request.url.path == '/compare':
                    def compare():
                        f0, notes, rms = prepare_recording(audio, source, bpm, start)
                        return score_experiment(f0, notes, bpm, HWANG_HZ, spec, rms)
                    experiment = await run_in_threadpool(compare)
                    return JSONResponse({'result': asdict(experiment['result']),
                                         'onset_tolerance_ms': experiment['onset_tolerance_ms']})
                result = await run_in_threadpool(score_recording, audio, source, bpm, start)
            except ValueError as exc:
                return JSONResponse({'error': str(exc)}, status_code=400)
            except DecodeError:
                return JSONResponse({'error': '녹음 파일을 읽을 수 없습니다.'}, status_code=400)
            except AnalysisDurationExceeded as exc:
                return JSONResponse({'error': str(exc)}, status_code=400)
            except RecordingTooLong:
                return JSONResponse({'error': '녹음과 악보가 분석 메모리 한도를 넘습니다.'}, status_code=400)
            except FileNotFoundError:
                return JSONResponse({'error': '녹음 분석 도구를 찾을 수 없습니다. ffmpeg와 ffprobe 설치를 확인해 주세요.'}, status_code=500)
            except TimeoutError:
                return JSONResponse({'error': '녹음 분석 시간이 초과됐습니다.'}, status_code=500)
            except RuntimeError:
                return JSONResponse({'error': '실제 채점과 실험 측정값이 일치하지 않아 비교를 중단했습니다.'}, status_code=500)
            return JSONResponse(asdict(result))
    except OSError:
        return JSONResponse({'error': '임시 파일을 읽거나 쓸 수 없습니다. 저장 공간을 확인해 주세요.'}, status_code=500)
    finally:
        _LOCK.release()


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='127.0.0.1', port=8765, log_level='warning')
