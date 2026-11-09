#!/usr/bin/env python3
"""Check exact published HTML before inserting immutable term versions. Default is read-only."""
import argparse
from datetime import datetime, timezone, timedelta
import hashlib
import json
import re
from pathlib import Path
import subprocess
import urllib.request

VERSION = '2026-11-03'
BASE = 'https://k15e102.p.ssafy.io'
ROWS = [
    ('AGE_OVER_14', '만 14세 이상 확인', 'age', True, 10),
    ('SERVICE_TERMS', '서비스 이용약관', 'service', True, 20),
    ('PRIVACY_COLLECTION', '개인정보 수집·이용 동의', 'collection', True, 30),
    ('MICROPHONE_NOTICE', '접근권한 안내', 'permissions', False, 40),
]
DOCUMENTS = ('age', 'service', 'collection', 'permissions', 'privacy')
RESOURCE = Path(__file__).resolve().parents[2] / 'backend/src/main/resources/legal' / VERSION


def canonical(body):
    return body.decode('utf-8').replace('\r\n', '\n').encode('utf-8')


def verify_document(expected, received, content_type, final_url, requested_url):
    if final_url != requested_url or not content_type.lower().startswith('text/html'):
        raise ValueError('document redirect or wrong content type')
    if hashlib.sha256(canonical(expected)).digest() != hashlib.sha256(canonical(received)).digest():
        raise ValueError('published document differs from reviewed source')


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def build_insert(effective):
    values = []
    for code, title, document, required, seq in ROWS:
        url = BASE + '/api/terms/documents/' + VERSION + '/' + document
        fields = [quote(code), quote(VERSION), quote(title), quote(url), 'TRUE' if required else 'FALSE', str(seq), quote(effective)]
        values.append('(' + ','.join(fields) + ')')
    return 'INSERT INTO terms (code,version,title,content_url,is_required,seq,effective_at) VALUES\n' + ',\n'.join(values) + ';'


def verify_catalog(items):
    if not isinstance(items, list) or len(items) != len(ROWS):
        raise ValueError('published terms catalog is incomplete')
    ids = set()
    for item, (code, title, document, required, _) in zip(items, ROWS):
        if (item.get('code') != code or item.get('title') != title or
                item.get('version') != VERSION or item.get('required') is not required or
                item.get('contentUrl') != BASE + '/api/terms/documents/' + VERSION + '/' + document or
                not re.fullmatch(r'[1-9][0-9]*', str(item.get('termId', '')))):
            raise ValueError('published terms metadata differs from reviewed source')
        ids.add(item['termId'])
    if len(ids) != len(ROWS):
        raise ValueError('published term IDs are duplicated')


def verify_live_catalog():
    url = BASE + '/api/terms'
    with urllib.request.urlopen(url, timeout=15) as response:
        if response.url != url or response.headers.get_content_type() != 'application/json':
            raise ValueError('terms API redirected or returned a non-JSON response')
        body = json.load(response)
    if body.get('isSuccess') is not True:
        raise ValueError('terms API did not succeed')
    verify_catalog(body.get('result', {}).get('items'))
    print('live_terms_metadata_verified=True')


def mysql(sql):
    # The MySQL container can retain its bootstrap database name after a rename.
    deployer = Path('/opt/eolssu/bin/deploy-backend')
    container = 'eolssu-runtime-backend-1'
    if deployer.is_file():
        container = subprocess.run([str(deployer), '--active-container'], capture_output=True, text=True,
                                   timeout=15, check=True).stdout.strip()
    inspected = subprocess.run(['docker', 'inspect', container], capture_output=True, timeout=15, check=True)
    environment = dict(item.split('=', 1) for item in json.loads(inspected.stdout)[0]['Config']['Env'] if '=' in item)
    matched = re.fullmatch(r'jdbc:mysql://[^/]+/([A-Za-z0-9_]+)(?:\?.*)?', environment.get('DB_URL', ''))
    if not matched:
        raise ValueError('active backend database could not be resolved')
    database = matched.group(1)
    # The password stays in the MySQL container's environment, never in host arguments or output.
    command = ['docker', 'exec', '-i', 'eolssu-runtime-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 --user=root --database=' + database + ' --batch --raw --skip-column-names']
    result = subprocess.run(command, input=sql.encode(), capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError('terms database operation failed')
    return result.stdout.decode().strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify-live', action='store_true', help='read-only check of registered API metadata, including Korean titles')
    parser.add_argument('--effective-at', help='required UTC ISO time, e.g. YYYY-MM-DDTHH:MM:SSZ')
    args = parser.parse_args()
    if args.apply and not args.effective_at:
        parser.error('--apply requires --effective-at in UTC')
    for document in DOCUMENTS:
        url = BASE + '/api/terms/documents/' + VERSION + '/' + document
        with urllib.request.urlopen(url, timeout=15) as response:
            verify_document((RESOURCE / (document + '.html')).read_bytes(), response.read(), response.headers.get('Content-Type', ''), response.url, url)
    # Also ensure the Play account-deletion link is live without login.
    url = BASE + '/api/terms/documents/account-deletion'
    with urllib.request.urlopen(url, timeout=15) as response:
        verify_document((RESOURCE.parent / 'account-deletion.html').read_bytes(), response.read(), response.headers.get('Content-Type', ''), response.url, url)
    print('published_html_verified=True')
    if not args.apply:
        if args.verify_live:
            verify_live_catalog()
        return
    effective = datetime.strptime(args.effective_at, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
    if (effective + timedelta(hours=9)).date().isoformat() != VERSION:
        raise ValueError('effective KST date differs from reviewed document date; prepare a new version')
    if effective > datetime.now(timezone.utc):
        raise ValueError('future effective time would leave signup terms unavailable')
    # Fail closed rather than overwrite a version that may already have agreements.
    existing = mysql('SELECT COUNT(*) FROM terms WHERE version=' + quote(VERSION) + ';')
    if existing != '0':
        raise ValueError('term version already exists; inspect it instead of recreating or updating')
    insert = build_insert(effective.strftime('%Y-%m-%d %H:%M:%S'))
    mysql('START TRANSACTION;\n' + insert + '\nCOMMIT;')
    rows = mysql('SELECT code,version,is_required,seq FROM terms WHERE version=' + quote(VERSION) + ' ORDER BY seq;')
    if len(rows.splitlines()) != len(ROWS):
        raise RuntimeError('terms read-back incomplete; preserve inserted rows and inspect')
    print('terms_registered_and_read_back=True')
    verify_live_catalog()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Terms publication stopped (' + type(error).__name__ + '); do not retry writes without reading existing rows.')
        raise SystemExit(1)
