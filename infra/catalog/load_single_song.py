#!/usr/bin/env python3
"""곡 하나짜리 release를 R2에 올리고 DRAFT로 적재·게시한다 (S15P31E102-1227). 기본은 읽기만 한다.

data/src/catalog/single_song_release.py가 만든 release 폴더와 model.json을 받는다. 107곡 묶음 검사
(stage_model_audio.validate_bundle)를 쓰지 않는 대신, 모델의 키·해시·악보가 release 파일과 같은지 여기서 검사하고
DB 쓰기는 기존 load_model_audio.run·publish_model_audio.run을 그대로 쓴다(곡 135 선례).
  python3 load_single_song.py upload  --release R --model M --env-file /opt/eolssu/.env --receipt X [--apply]
  python3 load_single_song.py load    --release R --model M --upload-receipt U --receipt X [--apply]
  python3 load_single_song.py publish --release R --model M --upload-receipt U --receipt X [--apply --application-ready]
load·publish는 같은 객체가 모두 업로드·재조회 확인된 upload 영수증(--apply로 만든 것)이 있어야 실행한다.
"""
import argparse
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import sys

import load_model_audio as loader
import publish_model_audio as publisher
from stage_model_audio import checked_bytes, create_client, parse_json, require, sha, upload_objects


def verify(release, model):
    """model.json이 release 폴더의 파일과 정확히 묶였는지. 묶음 목록(objects)을 돌려준다."""
    release = Path(release).absolute()
    raw = (release / 'release.json').read_bytes()
    manifest = parse_json(raw)
    checksum = sha(raw)
    require(manifest.get('schema') == 'eolssu-single-song-release/v1' and model['manifestSha256'] == checksum,
            'Model is not bound to this release')
    require(len(model['rows']) == 1 and model['references'] == {'jangdans': [], 'pitches': [], 'instruments': []},
            'Single-song model expected without new references')
    row = model['rows'][0]
    prefix = f"model-audio/drafts/{checksum}/"
    case = manifest.get('caseId')
    objects = [dict(item, key=prefix + item['path']) for item in manifest['files']]
    by_name = {item['path'].rsplit('/', 1)[1]: item for item in objects}
    require(len(objects) == 3 and len(by_name) == 3 and all(item['path'].startswith(f'cases/{case}/') for item in objects),
            'Release must hold exactly the three case files')
    for item in objects:
        checked_bytes(release, item)
    audio = next(item for name, item in by_name.items() if name.endswith('.m4a'))
    source = parse_json(checked_bytes(release, by_name['source.common.json']))
    performance = parse_json(checked_bytes(release, by_name['performance.common.json']))
    require(row['object_key'] == audio['key'] and row['sha256'] == audio['sha256'] and
            row['performance_object_key'] == by_name['performance.common.json']['key'] and
            row['source_common_sha256'] == by_name['source.common.json']['sha256'] and
            row['score_json'] == source and row['source'] == source['score']['sourceId'] == manifest['sourceId'] and
            row['generation_metadata']['manifestSha256'] == checksum, 'Model row differs from release files')
    require(row['title'] == source['score']['title'] and row['instrument_code'] == source['score']['instrument'] and
            row['score_version'] == source['score']['scoreVersion'] and
            row['note_count'] == sum(e['kind'] == 'NOTE' for e in source['events']) and
            row['default_bpm'] == performance['bpm'] and row['bpm_unit'] == performance['bpmUnit'] == source['music']['bpmUnit'] and
            row['duration_ms'] >= math.floor(performance['durationMs']), 'Model row metadata differs from release files')
    marker = dict(path='release.json', key=prefix + 'release.json', sha256=checksum, bytes=len(raw))
    return objects + [marker]  # 마지막이 표식: 모든 파일이 검증된 뒤에만 보인다.


def check_upload(receipt, objects, checksum):
    """upload --apply 영수증이 이 release의 모든 객체를 같은 키·해시로 올리고 다시 읽어 확인했는지."""
    require(receipt.get('status') == 'uploaded' and receipt.get('phase') == 'complete' and
            receipt.get('manifestSha256') == checksum, 'Upload receipt is not complete for this release')
    uploaded = receipt.get('objects')
    require(isinstance(uploaded, list) and len(uploaded) == len(objects), 'Upload receipt object count mismatch')
    for expected, actual in zip(objects, uploaded):
        require(all(actual.get(k) == expected[k] for k in ('path', 'key', 'sha256', 'bytes')) and
                actual.get('status') in ('new', 'reused', 'reused-race'), 'Uploaded object was not byte-verified')


def write_receipt(path, release, step, body):
    """기존 영수증을 덮어쓰지 않고, 실패해도 마지막 상태를 남긴다(stage_model_audio.stage와 같은 방식)."""
    require(not Path(path).absolute().is_relative_to(Path(release).absolute()), 'Receipt must stay outside the release')
    report = {'status': 'inventory', 'step': step}
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        try:
            body(report)
        except BaseException as error:
            report['errorType'] = type(error).__name__
            raise
        finally:
            report['checkedAtUtc'] = datetime.now(timezone.utc).isoformat()
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('step', choices=('upload', 'load', 'publish'))
    for name in ('release', 'model', 'receipt'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--upload-receipt', type=Path, help='load·publish: upload --apply가 남긴 영수증')
    parser.add_argument('--env-file', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--application-ready', action='store_true')
    args = parser.parse_args(argv)
    try:
        model = parse_json(args.model.read_bytes())
        objects = verify(args.release, model)
        if args.step == 'upload':
            def body(report):
                report.update(status='validated-dry-run', manifestSha256=model['manifestSha256'],
                              objects=[dict(item, status='planned') for item in objects])
                if args.apply:
                    require(args.env_file is not None, '--env-file is required to upload')
                    client, bucket = create_client(args.env_file)
                    upload_objects(args.release.absolute(), client, bucket, report, workers=2)
                    report['status'] = 'uploaded'
            report = write_receipt(args.receipt, args.release, 'upload', body)
        else:
            require(args.upload_receipt is not None, '--upload-receipt is required to load or publish')
            check_upload(parse_json(args.upload_receipt.read_bytes()), objects, model['manifestSha256'])
            if args.step == 'load':
                report = loader.run(model, args.receipt, apply=args.apply)
            else:
                report = publisher.run(model, args.receipt, action='publish', from_status='DRAFT', apply=args.apply,
                                       application_ready=args.application_ready)
    except Exception as error:
        print(f'{args.step} stopped ({type(error).__name__}: {error}); inspect the receipt and current rows before retrying',
              file=sys.stderr)
        return 1
    print(json.dumps({key: report.get(key) for key in ('status', 'manifestSha256', 'new', 'reused', 'changed', 'wouldInsert', 'songIds')}, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
