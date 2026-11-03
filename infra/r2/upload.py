#!/usr/bin/env python3
"""기존 R2 설정으로 파일·폴더 업로드. --dry-run은 네트워크 없이 미리보기."""
import argparse
import base64
import hashlib
import logging
import mimetypes
import os
from pathlib import Path
import shlex
import stat
import sys
from urllib.parse import urlsplit

PREFIX = 'RECORDING_STORAGE_'
FIELDS = ('ENDPOINT', 'BUCKET', 'ACCESS_KEY_ID', 'SECRET_ACCESS_KEY', 'REGION')
MAX_BYTES = 5 * 1024 ** 3  # S3 single PUT limit; no multipart overwrite workaround.


def read_settings(env_file=None):
    values = os.environ if env_file is None else {}
    if env_file is not None:
        for number, line in enumerate(Path(env_file).read_text(encoding='utf-8-sig').splitlines(), 1):
            key, separator, raw = line.strip().removeprefix('export ').partition('=')
            if not separator or key.strip() not in {PREFIX + name for name in FIELDS}:
                continue
            try:
                words = shlex.split(raw, comments=True, posix=True)
                if len(words) > 1:
                    raise ValueError()
            except ValueError:
                raise ValueError(f'환경파일 {number}행: 값에 공백이 있으면 따옴표로 감싸세요.') from None
            values[key.strip()] = words[0] if words else ''
    result = {name: values.get(PREFIX + name, '') for name in FIELDS}
    missing = [PREFIX + name for name in FIELDS[:-1] if not result[name]]
    if missing:
        raise ValueError('설정 누락: ' + ', '.join(missing))
    result['REGION'] = result['REGION'] or 'auto'
    endpoint = urlsplit(result['ENDPOINT'])
    if (endpoint.scheme != 'https' or not endpoint.hostname or endpoint.username or
            endpoint.password or endpoint.query or endpoint.fragment or endpoint.path not in ('', '/')):
        raise ValueError('ENDPOINT는 경로·인증정보 없는 HTTPS S3 API 주소여야 합니다.')
    if '/' in result['BUCKET'] or '\\' in result['BUCKET']:
        raise ValueError('BUCKET에는 경로가 아닌 버킷 이름만 지정하세요.')
    return result


def checked_key(key):
    if (not key or '\\' in key or any(ord(char) < 32 or ord(char) == 127 for char in key)
            or any(part in ('', '.', '..') for part in key.split('/'))
            or len(key.encode('utf-8')) > 1024):
        raise ValueError('목적지는 1024바이트 이내의 상대 객체 키/접두사여야 합니다.')
    if key.split('/')[0] == 'recordings':
        raise ValueError('recordings/는 사용자 녹음 보관·삭제 정책이 적용되는 경로입니다. 개발 자료는 다른 경로를 쓰세요.')
    return key


def checked_file(path):
    if any(part == '.git' or part == '.env' or part.startswith('.env.') for part in path.parts):
        raise ValueError('.git 및 .env 파일·폴더는 업로드할 수 없습니다.')
    mode = path.lstat().st_mode
    if stat.S_ISLNK(mode) or not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
        raise ValueError('심볼릭 링크 및 일반 파일·폴더가 아닌 입력은 업로드할 수 없습니다.')
    if stat.S_ISREG(mode) and path.stat().st_size > MAX_BYTES:
        raise ValueError('5GiB 초과 파일은 지원하지 않습니다. 분할 업로드가 필요합니다.')


def inventory(source, destination):
    source = Path(source)
    checked_file(source)
    base = checked_key(destination.rstrip('/'))
    if source.is_file():
        return [(source, checked_key(base + '/' + source.name) if destination.endswith('/') else base)]
    files = []
    for directory, dirs, names in os.walk(source, onerror=lambda error: _walk_error(error)):
        for name in sorted(dirs + names):
            path = Path(directory) / name
            checked_file(path)
            if path.is_file():
                files.append((path, checked_key(base + '/' + path.relative_to(source).as_posix())))
    if not files:
        raise ValueError('업로드할 파일이 없습니다. 빈 폴더 객체는 만들지 않습니다.')
    return sorted(files, key=lambda item: item[1])


def _walk_error(error):
    raise error


def content_type(path):
    return 'audio/mp4' if path.suffix.lower() == '.m4a' else (mimetypes.guess_type(path.name)[0] or 'application/octet-stream')


def upload(client, bucket, files, overwrite=False, output=None):
    output = output or sys.stdout
    uploaded = skipped = 0
    for path, key in files:
        with path.open('rb') as body:
            sha, md5 = hashlib.sha256(), hashlib.md5()
            for chunk in iter(lambda: body.read(1024 * 1024), b''):
                sha.update(chunk)
                md5.update(chunk)
            body.seek(0)
            request = dict(Bucket=bucket, Key=key, Body=body, ContentType=content_type(path),
                           ContentLength=os.fstat(body.fileno()).st_size,
                           ContentMD5=base64.b64encode(md5.digest()).decode('ascii'),
                           Metadata={'sha256': sha.hexdigest()})
            if not overwrite:
                request['IfNoneMatch'] = '*'
            try:
                client.put_object(**request)
            except Exception as error:
                code = str(getattr(error, 'response', {}).get('Error', {}).get('Code', ''))
                if not overwrite and code in ('PreconditionFailed', '412'):
                    skipped += 1
                    print(f'SKIP existing s3://{bucket}/{key}', file=output)
                    continue
                # SDK exceptions may contain signed request details; report no raw exception.
                raise RuntimeError(f'업로드 실패: s3://{bucket}/{key}. 이전 성공 파일은 남습니다. 설정·권한·연결을 확인하세요.') from None
        uploaded += 1
        print(f'UPLOADED s3://{bucket}/{key}', file=output)
    print(f'완료: 업로드 {uploaded}, 기존 객체 건너뜀 {skipped}', file=output)
    return uploaded, skipped


def make_client(settings):
    try:
        import boto3
        from botocore.config import Config
    except ImportError:
        raise ValueError('실제 업로드에는 boto3가 필요합니다: python -m pip install -r infra/r2/requirements.txt') from None
    logging.getLogger('botocore').setLevel(logging.CRITICAL)
    client = boto3.client('s3', endpoint_url=settings['ENDPOINT'],
                          aws_access_key_id=settings['ACCESS_KEY_ID'],
                          aws_secret_access_key=settings['SECRET_ACCESS_KEY'],
                          region_name=settings['REGION'],
                          config=Config(signature_version='s3v4', s3={'addressing_style': 'path'},
                                        request_checksum_calculation='when_required',
                                        connect_timeout=10, read_timeout=120, retries={'max_attempts': 2}))
    if 'IfNoneMatch' not in client.meta.service_model.operation_model('PutObject').input_shape.members:
        raise ValueError('조건부 업로드를 지원하는 boto3로 업데이트하세요: python -m pip install -r infra/r2/requirements.txt')
    return client


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='로컬 파일 또는 폴더')
    parser.add_argument('destination', help='파일: 객체 키(끝이 /면 파일명 추가), 폴더: 대상 접두사')
    parser.add_argument('--env-file', type=Path, help='기존 RECORDING_STORAGE_* 환경파일. 미지정 시 프로세스 환경변수 사용')
    parser.add_argument('--dry-run', action='store_true', help='설정·파일 검사와 목록 표시만 실행; 네트워크 연결 없음')
    parser.add_argument('--overwrite', action='store_true', help='같은 객체 키의 기존 파일 교체 허용')
    args = parser.parse_args(argv)
    try:
        settings = read_settings(args.env_file)
        files = inventory(args.source, args.destination)
        if args.dry_run:
            for path, key in files:
                print(f'DRY-RUN {path.stat().st_size} bytes -> s3://{settings["BUCKET"]}/{key}')
            print(f'미리보기: {len(files)}개; 기존 객체 교체 {"허용" if args.overwrite else "안 함"}; 원격 존재 여부는 조회하지 않음')
        else:
            client = make_client(settings)
            try:
                upload(client, settings['BUCKET'], files, overwrite=args.overwrite)
            finally:
                client.close()
        return 0
    except (ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    except Exception:
        print('실행 실패: 입력 파일·환경파일·SDK 설정을 확인하세요. 비밀값 보호를 위해 상세 오류는 출력하지 않습니다.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
