"""곡 하나의 적재 묶음(release)과 적재 모델을 만든다 (S15P31E102-1227).

입력은 같은 폴더의 source.common.json·performance.common.json·<악기>.m4a(score_prefix 출력)다.
출력 폴더:
  release/release.json, release/cases/<case>/{source.common.json, performance.common.json, <악기>.m4a}
  model.json  infra/catalog/load_model_audio.run·publish_model_audio.run이 받는 모델(행 1개, 참조 추가 없음)
R2 키는 model-audio/drafts/<release.json SHA-256>/... 이고 V15 CHECK(세 자리 case 번호)를 따른다.
"""
import argparse
import hashlib
import json
import math
import re
import shutil
from pathlib import Path

from ..playback.score import require
from ..scores.rebind_performance import validate_performance
from .model_audio_difficulty import classify_difficulty

CASE_ID = re.compile(r'^(sogeum|danso|jeongak-daegeum|sanjo-daegeum)-[0-9]{3}-p[0-9]+$')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build_release(source_dir, out, *, case_id, duration_ms, task, derived_from, rights, review):
    """source_dir의 세 파일을 묶고 행 하나짜리 적재 모델을 돌려준다. out은 새 폴더여야 한다."""
    match = CASE_ID.match(case_id)
    require(match is not None, 'case id must match the V15 object key rule')
    instrument = match.group(1)
    source_dir, out = Path(source_dir), Path(out)
    names = ('source.common.json', 'performance.common.json', f'{instrument}.m4a')
    data = {name: (source_dir / name).read_bytes() for name in names}
    source = json.loads(data['source.common.json'])
    performance = json.loads(data['performance.common.json'])
    source_sha, audio_sha = sha(data['source.common.json']), sha(data[names[2]])
    validate_performance(source, performance, source_sha256=source_sha, audio_sha256=audio_sha)
    # DB duration_ms는 정수 ms라 1ms 미만 반올림 차이는 받는다(음원 15000.023ms → 15000, 시간표 15000.012ms).
    require(type(duration_ms) is int and duration_ms >= math.floor(performance['durationMs']), 'audio is shorter than the timetable')
    files = [dict(path=f'cases/{case_id}/{name}', sha256=sha(data[name]), bytes=len(data[name])) for name in names]
    release = dict(schema='eolssu-single-song-release/v1', task=task, caseId=case_id, sourceId=source['score']['sourceId'],
                   files=files)
    release_bytes = (json.dumps(release, ensure_ascii=False, indent=1) + '\n').encode('utf-8')
    checksum = sha(release_bytes)
    (out / 'release' / 'cases' / case_id).mkdir(parents=True)
    for name in names:
        (out / 'release' / 'cases' / case_id / name).write_bytes(data[name])
    (out / 'release' / 'release.json').write_bytes(release_bytes)
    bpm = performance['bpm']
    require(float(bpm).is_integer() and performance['bpmUnit'] == source['music']['bpmUnit'], 'unsupported tempo')
    difficulty = classify_difficulty(source, bpm)
    prefix = f'model-audio/drafts/{checksum}/cases/{case_id}/'
    notes = sum(e['kind'] == 'NOTE' for e in source['events'])
    row = dict(source=source['score']['sourceId'], title=source['score']['title'], description=None,
               instrument_code=source['score']['instrument'], jangdan_name=None, difficulty=difficulty['level'],
               default_bpm=int(bpm), score_json=source, score_version=source['score']['scoreVersion'], note_count=notes,
               bpm_unit=performance['bpmUnit'], object_key=prefix + names[2], performance_object_key=prefix + names[1],
               source_common_sha256=source_sha,
               generation_metadata=dict(task=task, manifestSha256=checksum, caseId=case_id, sourceCommonSha256=source_sha,
                                        rights=rights, review=review, difficulty=difficulty, derivedFrom=derived_from),
               duration_ms=duration_ms, start_offset_ms=0, content_type='audio/mp4', sha256=audio_sha)
    model = dict(manifestSha256=checksum, difficultySha256=sha(json.dumps(difficulty, sort_keys=True).encode()),
                 referencePlanSha256=sha(b'{"jangdans":[],"pitches":[],"instruments":[]}'),
                 references=dict(jangdans=[], pitches=[], instruments=[]), rows=[row])
    (out / 'model.json').write_text(json.dumps(model, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return model


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-dir', required=True, help='score_prefix 출력 폴더')
    parser.add_argument('--original-audio-row', required=True, help='fetch_song_source의 model-audios.json(원곡 행)')
    parser.add_argument('--case-id', required=True)
    parser.add_argument('--task', required=True)
    parser.add_argument('--cut-end-tick', type=int, required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args(argv)
    out = Path(args.out)
    require(not out.exists(), 'output folder must be new')
    report = json.loads((Path(args.source_dir) / 'report.json').read_text(encoding='utf-8'))
    audio_name = CASE_ID.match(args.case_id).group(1) + '.m4a' if CASE_ID.match(args.case_id) else ''
    require(report['endTick'] == args.cut_end_tick and audio_name and
            report['audio']['sha256'] == sha((Path(args.source_dir) / audio_name).read_bytes()),
            'report.json does not describe these cut files')
    (original,) = json.loads(Path(args.original_audio_row).read_text(encoding='utf-8'))
    meta = original['generation_metadata']
    derived = dict(songId=original['song_id'], scoreVersion=original['score_version'], audioSha256=original['sha256'],
                   sourceCommonSha256=original['source_common_sha256'], cutEndTick=args.cut_end_tick)
    try:
        model = build_release(args.source_dir, out, case_id=args.case_id,
                              duration_ms=round(report['audio']['container_seconds'] * 1000), task=args.task,
                              derived_from=derived, rights=meta['rights'], review=meta['review'])
    except BaseException:
        shutil.rmtree(out, ignore_errors=True)
        raise
    row = model['rows'][0]
    print(json.dumps(dict(manifestSha256=model['manifestSha256'], source=row['source'], title=row['title'],
                          difficulty=row['difficulty'], noteCount=row['note_count'], durationMs=row['duration_ms'],
                          objectKey=row['object_key']), ensure_ascii=False))


if __name__ == '__main__':
    main()
