"""공통 악보·재생 시간표·모범 음원을 앞 N각에서 자른다 (S15P31E102-1227).

기존 곡의 앞부분을 새 곡으로 적재할 때 쓴다. 음 길이·시간표의 측정 시각은 바꾸지 않고 잘라 내기만 한다.
각 경계를 넘어 이어지는 음이 있으면 자르지 않고 멈춘다.
"""
from copy import deepcopy

import numpy as np

from ..playback.audio import write_audio, FS
from ..playback.score import require


def gak_end_tick(score, gaks):
    """앞 gaks각이 끝나는 tick. 정간 하나는 ticksPerJeonggan tick이다."""
    require(0 < gaks <= len(score['columns']), 'gak count out of range')
    return sum(len(column['cells']) for column in score['columns'][:gaks]) * score['music']['ticksPerJeonggan']


def cut_score(score, gaks, *, source_id, title):
    """앞 gaks각의 열·이벤트만 남긴 새 악보. sourceId·제목만 바꾸고 가사·검수 상태는 그대로 둔다."""
    end = gak_end_tick(score, gaks)
    cut = deepcopy(score)
    cut['events'] = [e for e in cut['events'] if e['startTick'] < end]
    require(all(e['startTick'] + e['durationTick'] <= end for e in cut['events']), 'an event crosses the cut')
    cut['columns'] = cut['columns'][:gaks]
    cut['score'].update(sourceId=source_id, title=title)
    cut['provenance']['sourceId'] = source_id
    return cut


def cut_performance(performance, end_tick, *, source_sha256, audio_sha256, source_id):
    """end_tick 앞 segment만 남긴 시간표. 측정 시각은 그대로이고 길이는 마지막 segment 끝이다."""
    cut = deepcopy(performance)
    cut['segments'] = [s for s in cut['segments'] if s['startTick'] < end_tick]
    require(cut['segments'] and all(s['endTick'] <= end_tick + 1e-9 for s in cut['segments']), 'a segment crosses the cut')
    cut.update(sourceSha256=source_sha256, audioSha256=audio_sha256, sourceId=source_id,
               durationMs=cut['segments'][-1]['endMs'])
    return cut


def decode(path, encoder):
    """M4A를 FS·mono float PCM으로. 디코더가 edit list의 앞 여백(priming)을 뺀다."""
    from ..playback.audio import command
    payload = command([encoder['ffmpeg'], '-hide_banner', '-loglevel', 'error', '-nostdin', '-i', str(path),
                       '-map', '0:a:0', '-ac', '1', '-ar', str(FS), '-f', 'f32le', '-c:a', 'pcm_f32le', 'pipe:1'])
    return np.frombuffer(payload, dtype='<f4')


def cut_audio(source, target, duration_ms, encoder, *, credit=None):
    """원본을 PCM으로 풀어 duration_ms에서 자르고 같은 설정(AAC-LC, ipod)으로 다시 쓴다."""
    pcm = decode(source, encoder)
    frames = round(duration_ms * FS / 1000)
    require(0 < frames <= len(pcm), 'cut is longer than the audio')
    return write_audio(pcm[:frames].copy(), target, encoder, credit=credit)


def main(argv=None):
    """운영에서 받은 악보·시간표·음원을 앞 N각에서 잘라 --out에 source.common.json·performance.common.json·sogeum.m4a로 쓴다."""
    import argparse
    import hashlib
    import json
    from pathlib import Path

    from ..playback.audio import resolve_encoder
    from ..playback.common_score import validate_common
    from ..scores.rebind_performance import validate_performance

    parser = argparse.ArgumentParser()
    for name in ('--score', '--performance', '--audio', '--source-id', '--title', '--out'):
        parser.add_argument(name, required=True)
    parser.add_argument('--gaks', type=int, required=True)
    args = parser.parse_args(argv)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=False)
    score = cut_score(json.loads(Path(args.score).read_text(encoding='utf-8')), args.gaks,
                      source_id=args.source_id, title=args.title)
    validate_common(score, allow_sigimsae=True)
    source_bytes = json.dumps(score, ensure_ascii=False, indent=1).encode('utf-8')
    (out / 'source.common.json').write_bytes(source_bytes)
    source_sha = hashlib.sha256(source_bytes).hexdigest()
    performance = json.loads(Path(args.performance).read_text(encoding='utf-8'))
    end_tick = gak_end_tick(score, args.gaks)
    timed = cut_performance(performance, end_tick, source_sha256=source_sha, audio_sha256='0' * 64,
                            source_id=args.source_id)
    audio = cut_audio(args.audio, out / 'sogeum.m4a', timed['durationMs'], resolve_encoder())
    timed['audioSha256'] = audio['sha256']
    validate_performance(score, timed, source_sha256=source_sha, audio_sha256=audio['sha256'])
    (out / 'performance.common.json').write_text(json.dumps(timed, ensure_ascii=False, indent=1), encoding='utf-8')
    report = dict(endTick=end_tick, events=len(score['events']),
                  notes=sum(e['kind'] == 'NOTE' for e in score['events']), durationMs=timed['durationMs'],
                  sourceSha256=source_sha, audio=audio)
    (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
