"""Run real project OMR on local, isolated sample images and retain engine evidence.

Run inside the existing OMR image with the recorded source tree mounted read-only.
No backend writes or public endpoints. Inputs are trusted local manifest paths.
"""
import argparse
from collections import Counter
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import shutil
import time
import traceback


def probe(inputs, output):
    from PIL import Image
    from omr_service import api, staff_engine, jeongganbo_scan
    if output.exists() and any(output.iterdir()):
        raise ValueError('output must be a new or empty evidence directory')
    output.mkdir(parents=True, exist_ok=True)
    summary = []
    original_run = staff_engine._run
    original_scan_run = jeongganbo_scan._run
    for item in inputs:
        sid = item['id']
        dest = output / sid
        dest.mkdir(parents=True, exist_ok=True)
        calls = []
        reader_calls = []

        def capture(command, deadline):
            began = time.monotonic()
            run = original_run(command, deadline)
            if '-output' in command and '-transcribe' in command:
                folder = Path(command[command.index('-output') + 1])
                call = dest / f'audiveris-{len(calls)}'
                call.mkdir(exist_ok=True)
                for path in folder.rglob('*'):
                    if path.suffix in ('.mxl', '.musicxml', '.omr'):
                        shutil.copy2(path, call / path.name)
                (call / 'stdout.txt').write_bytes(run.stdout)
                (call / 'stderr.txt').write_bytes(run.stderr)
                calls.append(dict(returncode=run.returncode, seconds=round(time.monotonic()-began, 3)))
            elif '--omr-dir' in command:
                call=dest / f'jeonggan-reader-{len(reader_calls)}'
                call.mkdir(exist_ok=True)
                (call/'stdout.txt').write_bytes(run.stdout)
                (call/'stderr.txt').write_bytes(run.stderr)
                reader_calls.append(dict(returncode=run.returncode,seconds=round(time.monotonic()-began,3)))
            return run

        staff_engine._run = capture
        jeongganbo_scan._run = capture
        image = Path(item['imagePath'])
        with Image.open(image) as im:
            width, height = im.size
        request = api.RecognitionIn(conversionId='meter883-' + sid, notation=item.get('notation','STAFF'),
                                    files=[dict(url='https://isolated.invalid/input.png',contentType='image/png')],
                                    instrument='sogeum', jangdan=item.get('jangdan'))
        page = api.Page(image, width, height)
        files = [Path(item['pdfPath'])] if item.get('pdfPath') else [image]
        job = api.Job(request, 'PDF' if item.get('pdfPath') else 'IMAGE', files, [page], 60)
        began = time.monotonic()
        row = dict(id=sid, inputSha256=hashlib.sha256(image.read_bytes()).hexdigest())
        try:
            result = (staff_engine.recognize(job) if request.notation == 'STAFF' else api.recognize_jeongganbo(job))
            (dest / 'engine.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
            response = api._done(request, result, [page])
            (dest / 'response.json').write_text(json.dumps(response, ensure_ascii=False, indent=2), encoding='utf-8')
            music = result['score']['music']
            row.update(status='DONE', music=music,
                       columnSizes=[len(x['cells']) for x in result['score']['columns']],
                       events=len(result['score']['events']),
                       issues=dict(Counter(x['kind'] for x in result['issues'])))
        except Exception as error:
            row.update(status='FAILED', error=type(error).__name__, detail=str(error))
            (dest / 'error.txt').write_text(traceback.format_exc(), encoding='utf-8')
        finally:
            staff_engine._run = original_run
            jeongganbo_scan._run = original_scan_run
        row.update(seconds=round(time.monotonic()-began,3), audiveris=calls, readers=reader_calls)
        summary.append(row)
        (output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(row, ensure_ascii=False), flush=True)
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--ids', nargs='+', help='Optional fixed sample IDs; keep a first replay small')
    args = parser.parse_args()
    inputs=json.loads(args.inputs.read_text(encoding='utf-8'))
    if args.ids:
        if set(args.ids)-{x['id'] for x in inputs}:
            parser.error('unknown sample ID')
        inputs=[x for x in inputs if x['id'] in args.ids]
    if not inputs:
        parser.error('empty input set')
    results=probe(inputs, args.output)
    raise SystemExit(0 if all(row['status']=='DONE' for row in results) else 1)
