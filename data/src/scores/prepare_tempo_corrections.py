"""Prepare Task728 replacements from a current DB snapshot; never writes remotely.

The decision file carries the song-specific evidence. Input expression files must
belong to the current score, including earlier notation repairs. Selection is by
source identity and version, never by title. Run with -m from the repository root.
"""
import argparse
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys

from data.src.playback.common_score import validate_common, convert_common_score


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode('utf-8')


def sha(body):
    return hashlib.sha256(body).hexdigest()


def prepare(before, decision, expressions):
    if before['id'] in {56, 132, 133, 134}:
        raise ValueError('Airplane is excluded by the user')
    old = before['score']
    if (before['source'], before['version']) != (decision['sourceId'], decision['version']):
        raise ValueError('source/version drift')
    instrument = old['score']['instrument'].lower().replace('_', '-')
    if (before['source'] != old['score']['sourceId'] or before['version'] != old['score']['scoreVersion'] or
            instrument not in ('danso', 'sogeum', 'jeongak-daegeum', 'sanjo-daegeum') or
            re.fullmatch('mr203-fixed-' + re.escape(instrument) + r'-[0-9]{3}-p[0-9]+', before['source']) is None):
        raise ValueError('unsupported source catalog identity')
    target = deepcopy(old)
    target['score']['scoreVersion'] += 1
    if 'jangdan' in decision:
        target['music']['jangdan'] = decision['jangdan']
    if 'ticksPerBeat' in decision:
        if decision['targetBpm'] * decision['ticksPerBeat'] != before['bpm'] * old['music']['ticksPerBeat']:
            raise ValueError('notation correction changed actual speed')
        target['music']['ticksPerBeat'] = decision['ticksPerBeat']
    validate_common(target, allow_sigimsae=True)
    body = encoded(target)
    expr = deepcopy(expressions)
    expr['sourceSha256'] = sha(body)
    # Existing compiler checks every expression slot against the target events.
    convert_common_score(target, bpm=decision['targetBpm'], instrument=instrument,
                         input_sha256=sha(body), notation_policy='educational-preview/v1', expressions=expr)
    return target, body, expr, instrument


def build_release(snapshot, records, bundle):
    """Package only verified renderer outputs and before-images, without upload."""
    songs = {s['id']: s for s in snapshot['songs']}
    audios = {(a['songId'], a['version']): a for a in snapshot['audios']}
    if bundle.exists():
        raise ValueError('use a new release bundle directory')
    bundle.mkdir(parents=True)
    entries = []
    for record in records:
        sid = record['songId']
        before = songs[sid]
        old_audio = audios[(sid, before['version'])]
        output = Path(record['output'])
        manifest = read(output / 'manifest.json')
        source = (output / 'source.common.json').read_bytes()
        if source != Path(record['sourceFile']).read_bytes() or manifest['input_sha256'] != sha(source):
            raise ValueError(f'{sid}: stale renderer source')
        expression_hash = sha(Path(record['expressionFile']).read_bytes())
        if manifest.get('configuration_sha256', {}).get('expressions.common.json') != expression_hash:
            raise ValueError(f'{sid}: stale renderer expressions; render again')
        media = manifest['results'][0]['audio']
        if media['format'] != 'm4a' or media['codec'] != 'aac' or media['channels'] != 1:
            raise ValueError('unexpected rendered format')
        perf = read(output / 'performance.common.json')
        if perf['bpm'] != record['bpm']:
            raise ValueError('stale rendered BPM')
        entry = dict(songId=sid, beforeSong=before, beforeAudio=old_audio, targetBpm=record['bpm'])
        case_id = before['score']['score']['sourceId'].removeprefix('mr203-fixed-')
        for field, name in [('scoreFile', 'source.common.json'), ('audioFile', media['file']),
                            ('performanceFile', 'performance.common.json')]:
            payload = (output / name).read_bytes()
            dest = bundle / 'cases' / case_id / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(payload)
            entry[field] = dict(path=f'cases/{case_id}/{name}', sha256=sha(payload), bytes=len(payload))
        if entry['audioFile']['sha256'] != media['sha256'] or perf['audioSha256'] != media['sha256']:
            raise ValueError('audio digest mismatch')
        metadata = deepcopy(old_audio['generation_metadata'])
        metadata.update(task='S15P31E102-728', previousScoreVersion=before['version'],
                        previousAudioSha256=old_audio['sha256'], sourceCommonSha256=sha(source),
                        tempoCorrection=record['decision'],
                        rendererManifestSha256=sha((output / 'manifest.json').read_bytes()))
        if 'difficulty' in metadata:
            metadata['difficulty']['inheritedFromScoreVersion'] = before['version']
        entry['audioMetadata'] = dict(source_common_sha256=sha(source), bpm=record['bpm'],
            bpm_unit=before['score']['music']['bpmUnit'], duration_ms=math.ceil(media['container_seconds'] * 1000),
            start_offset_ms=0, content_type='audio/mp4', sha256=media['sha256'],
            status=old_audio['status'], generation_metadata=metadata)
        if 'ticksPerBeat' in record['decision']:
            entry['beatNotationCorrection'] = dict(basis=record['decision']['basis'])
        entries.append(entry)
    (bundle / 'release.json').write_bytes(encoded(dict(task='S15P31E102-728',
        schema='eolssu-score-tempo-release/v1', entries=entries)))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--snapshot', type=Path, required=True)
    p.add_argument('--decisions', type=Path, required=True)
    p.add_argument('--expressions', type=Path, required=True,
                   help='JSON mapping song ID to current expression companion path')
    p.add_argument('--assets', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--song-id', action='append', type=int)
    p.add_argument('--render', action='store_true')
    p.add_argument('--bundle', type=Path, help='Package rendered outputs into a new immutable release folder')
    args = p.parse_args()
    snapshot, decisions, paths = read(args.snapshot), read(args.decisions), read(args.expressions)
    songs = {s['id']: s for s in snapshot['songs']}
    selected = [d for d in decisions['corrections'] if not args.song_id or d['songId'] in args.song_id]
    if not selected or (args.song_id and set(args.song_id) != {d['songId'] for d in selected}):
        raise ValueError('unknown or empty selection')
    records = []
    for decision in selected:
        sid = decision['songId']
        target, body, expr, instrument = prepare(songs[sid], decision, read(paths[str(sid)]))
        folder = args.out / str(sid)
        folder.mkdir(parents=True, exist_ok=True)
        source_path, expr_path = folder / 'common.json', folder / 'expressions.json'
        source_path.write_bytes(body)
        expr_path.write_bytes(encoded(expr))
        output = folder / 'audio'
        command = [sys.executable, '-m', 'data.src.playback', '--score', str(source_path),
                   '--common-bpm', str(decision['targetBpm']), '--common-expressions', str(expr_path),
                   '--notation-policy', 'educational-preview/v1', '--instrument', instrument,
                   '--assets', str(args.assets), '--out', str(output)]
        if args.render:
            run = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            (folder / 'render.log').write_bytes(run.stdout)
            if run.returncode:
                raise RuntimeError(f'{sid}: rendering failed; see {folder / "render.log"}')
        record = dict(songId=sid, sourceFile=str(source_path.resolve()),
                      expressionFile=str(expr_path.resolve()), output=str(output.resolve()),
                      sourceSha256=sha(body), bpm=decision['targetBpm'], instrument=instrument,
                      scoreVersion=target['score']['scoreVersion'], decision=decision, rendered=args.render)
        (folder / 'preparation.json').write_bytes(encoded(record))
        records.append(record)
        print(f'{sid}: ' + ('rendered' if args.render else 'prepared'), flush=True)
    (args.out / 'preparation.json').write_bytes(encoded(dict(task=decisions['task'], songs=records)))
    if args.bundle:
        build_release(snapshot, records, args.bundle)


if __name__ == '__main__':
    main()
