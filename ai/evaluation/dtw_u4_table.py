"""U4 원본 측정 JSON에서 반복별 결과 표(CSV)를 만든다.

실행: uv run python -m evaluation.dtw_u4_table ../data/processed/dtw-286/u4-reviewed.json --out ../docs/knowledge/ai/dtw-u4-reviewed-results.csv
첫 측정(`u4-full-rest.json`)처럼 processWallSeconds가 없는 원본은 프로세스 전체 시간·음원 준비 열 없이 만든다.
"""
import argparse
import csv
import io
import json
import statistics
from pathlib import Path

from evaluation.dtw_performance import METHODS

SONG_ORDER = (173, 171, 172, 174)  # 아리랑·도라지타령·밀양아리랑·늴리리야
REPEATS = 5


def _cell(value):
    if value is None:
        return ''
    if isinstance(value, bool):
        return int(value)
    return value


def _median(values):
    return statistics.median(values) if values else None


def build_rows(report):
    """곡·BPM·입력 방식·정렬 방식마다 한 행. 중앙값·범위·최대 RSS는 DONE 반복만으로 계산한다."""
    reviewed = 'preparations' in report
    preparations = {(p['page'], p['bpm']): p for p in report.get('preparations', [])}
    groups = {}
    for run in report['runs']:
        groups.setdefault((run['page'], run['bpm'], run['mode'], run['method']), []).append(run)
    header = ['source_page', 'bpm', 'mode', 'method', 'attempts', 'statuses']
    header += ['reasons'] if reviewed else []
    header += ['expected_frames', 'detected_frames_or_estimate', 'estimated_bytes', 'audio_sha256', 'f0_sha256',
               'rms_sha256', 'median_seconds', 'min_seconds', 'max_seconds']
    header += ['median_process_wall_seconds', 'min_process_wall_seconds', 'max_process_wall_seconds'] if reviewed else []
    header += ['peak_rss_max_bytes', 'rss_limit_near']
    header += ['preparation_status', 'preparation_wall_seconds'] if reviewed else []
    header += [f'seconds_{i}' for i in range(1, REPEATS + 1)]
    header += [f'process_wall_seconds_{i}' for i in range(1, REPEATS + 1)] if reviewed else []
    header += [f'{name}_{i}' for name in ('peak_rss_bytes', 'score', 'path_sha256') for i in range(1, REPEATS + 1)]
    rows = [header]
    pages = [p for p in SONG_ORDER if any(k[0] == p for k in groups)]
    for page in pages:
        for bpm in sorted({k[1] for k in groups if k[0] == page}):
            for mode in ('dtw', 'm4a'):
                for method in METHODS:
                    runs = sorted(groups.get((page, bpm, mode, method), []), key=lambda r: r['repeat'])
                    if not runs:
                        continue
                    done = [r for r in runs if r['status'] == 'DONE']
                    first = lambda key: next((r[key] for r in runs if r.get(key) is not None), None)
                    detected = first('detectedFrames')
                    row = [page, bpm, mode, method, len(runs), '|'.join(r['status'] for r in runs)]
                    row += ['|'.join(r.get('reason') or '' for r in runs)] if reviewed else []
                    row += [first('expectedFrames'), detected if detected is not None else first('detectedFramesEstimate'),
                            first('estimatedBytes'), first('audioHash'), first('inputHash'), first('rmsHash'),
                            _median([r['seconds'] for r in done]), min((r['seconds'] for r in done), default=None),
                            max((r['seconds'] for r in done), default=None)]
                    if reviewed:
                        walls = [r['processWallSeconds'] for r in done]
                        row += [_median(walls), min(walls, default=None), max(walls, default=None)]
                    row += [max((r['peakRssBytes'] for r in done), default=None),
                            int(any(r.get('rssMayBeLimited') for r in runs))]
                    if reviewed:
                        prep = preparations.get((page, bpm)) if mode == 'm4a' else None
                        row += [prep and prep['status'], prep and prep['processWallSeconds']]
                    by_repeat = {r['repeat']: r for r in runs}
                    pick = lambda key: [by_repeat.get(i, {}).get(key) for i in range(1, REPEATS + 1)]
                    done_only = lambda key: [r.get(key) if r['status'] == 'DONE' else None
                                             for r in (by_repeat.get(i, {'status': None}) for i in range(1, REPEATS + 1))]
                    row += done_only('seconds')
                    row += pick('processWallSeconds') if reviewed else []
                    row += done_only('peakRssBytes') + done_only('scoreTotal') + done_only('pathHash')
                    rows.append([_cell(v) for v in row])
    return rows


def to_csv(rows):
    buffer = io.StringIO()
    csv.writer(buffer, lineterminator='\n').writerows(rows)
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('report', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    rows = build_rows(json.loads(args.report.read_text(encoding='utf-8')))
    args.out.write_text(to_csv(rows), encoding='utf-8', newline='')
    print(f'{args.out}: {len(rows) - 1} rows')


if __name__ == '__main__':
    main()
