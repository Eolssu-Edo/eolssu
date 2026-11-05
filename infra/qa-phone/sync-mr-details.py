#!/usr/bin/env python3
"""Read only this project's MR metadata; never rewrite immutable APK files."""
import argparse
import fcntl
import json
import os
import pathlib
import re
import tempfile
import unicodedata
import urllib.parse
import urllib.request

API = 'https://lab.ssafy.com/api/v4/projects/s15-final%2FS15P31E102/merge_requests'
MAX_BYTES = 4 * 1024 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Do not send a credential to a redirect destination.


def fetch_batch(ids, token, api_base=API):
    query = urllib.parse.urlencode([('scope', 'all'), ('per_page', '100')] + [('iids[]', str(i)) for i in ids])
    request = urllib.request.Request(api_base + '?' + query, headers={'PRIVATE-TOKEN': token, 'Accept': 'application/json'})
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=15) as response:
        body = response.read(MAX_BYTES + 1)
    if len(body) > MAX_BYTES:
        raise ValueError('oversized MR response')
    return json.loads(body)


def read_index(root):
    body = (root / 'index.json').read_bytes()
    if len(body) > MAX_BYTES:
        raise ValueError('oversized archive index')
    value = json.loads(body)
    mapping = value.get('mergeRequests', {})
    if not isinstance(mapping, dict) or len(mapping) > 10000:
        raise ValueError('invalid MR mapping')
    for commit, iid in mapping.items():
        if not re.fullmatch('[a-f0-9]{40}', commit) or type(iid) is not int or not 0 < iid <= 2147483647:
            raise ValueError('invalid MR identity')
    return value


def details_for(rows, ids):
    if not isinstance(rows, list) or len(rows) > 100:
        raise ValueError('invalid MR response')
    details = {}
    for row in rows:
        iid = row.get('iid')
        if type(iid) is not int or iid not in ids or str(iid) in details:
            raise ValueError('unexpected MR identity')
        values = {'authorName': row.get('author', {}).get('name'), 'title': row.get('title')}
        for key, limit in [('authorName', 128), ('title', 512)]:
            text = values[key]
            if not isinstance(text, str) or not text.strip() or len(text) > limit or any(unicodedata.category(c).startswith('C') for c in text):
                raise ValueError('invalid MR text')
            values[key] = text.strip()
        details[str(iid)] = values
    if len(details) != len(ids):
        raise ValueError('missing MR information')
    return details


def sync(root, token, fetch=fetch_batch):
    root = pathlib.Path(root)
    ids = sorted(set(read_index(root).get('mergeRequests', {}).values()))
    incoming = {}
    # Network calls must not hold the publisher's archive lock.
    for offset in range(0, len(ids), 100):
        batch = ids[offset:offset + 100]
        incoming.update(details_for(fetch(batch, token), batch))
    if not incoming:
        return 0
    with (root / '.publish.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        current = read_index(root)  # Preserve APKs published during the request.
        merged = {**current.get('mergeRequestDetails', {}), **incoming}
        if current.get('mergeRequestDetails') != merged:
            current['mergeRequestDetails'] = merged
            fd, temporary = tempfile.mkstemp(prefix='.mr-index-', dir=root)
            try:
                with os.fdopen(fd, 'w', encoding='utf-8') as out:
                    json.dump(current, out, indent=2, ensure_ascii=False)
                    out.flush()
                    os.fsync(out.fileno())
                os.chmod(temporary, 0o644)
                os.replace(temporary, root / 'index.json')
            finally:
                if os.path.exists(temporary):
                    os.unlink(temporary)
    return len(incoming)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', required=True)
    parser.add_argument('--token-file', required=True)
    args = parser.parse_args()
    try:
        token = pathlib.Path(args.token_file).read_text().strip()
        if not re.fullmatch(r'\S{20,256}', token):
            raise ValueError('invalid credential')
        count = sync(args.root, token)
        print(f'MR metadata synchronized: {count}')
    except Exception:
        # No raw exception/request/header/body: they may contain a secret.
        parser.exit(1, 'MR metadata sync failed: verify read API permission, expiry and archive access\n')
