#!/usr/bin/env python3
"""Upload a signed AAB to a Google Play track with the Play Developer API (S15P31E102-1254).

Standard library and the openssl CLI only: the service-account token request is signed with
`openssl dgst -sha256 -sign`, so the Jenkins agent needs no pip packages or Jenkins plugin.

check:  get a token, open an edit, print every track's releases, fail if --version-code is not
        greater than every bundle already in Play, then delete the edit.
upload: open an edit, upload the bundle, set one release on --track with --status, then commit
        (or only validate with --validate-only). Any failure before the commit deletes the edit.

The service-account key is read from --credentials and never printed or kept after signing.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request


API = 'https://androidpublisher.googleapis.com'
SCOPE = 'https://www.googleapis.com/auth/androidpublisher'
TRACKS = ('internal', 'production')
STATUSES = ('draft', 'completed')
# What a person does in Play Console after a normal commit. A draft internal release goes out
# only after someone starts its rollout; a draft production release must be sent for review.
NEXT_STEP = {
    ('internal', 'draft'): 'draft internal release is in Play Console; start its rollout there to reach testers',
    ('internal', 'completed'): 'internal release rolled out to testers',
    ('production', 'draft'): 'draft production release is in Play Console; send it for review there',
    ('production', 'completed'): 'production release sent for review',
}
NOTES_LANGUAGE = 'ko-KR'
NOTES_LIMIT = 500  # Play Console limit per language
UPLOAD_TIMEOUT = 900


class PlayError(Exception):
    pass


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=')


def sign_rs256(message, private_key_pem):
    """RS256 signature of message. The key exists only in a private temp file during the call."""
    with tempfile.TemporaryDirectory(prefix='play-key-') as directory:
        key = Path(directory) / 'key.pem'
        fd = os.open(key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8') as handle:
            handle.write(private_key_pem)
        result = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(key)],
                                input=message, capture_output=True, check=False)
    if result.returncode != 0 or not result.stdout:
        raise PlayError('openssl could not sign the token request: '
                        + result.stderr.decode('utf-8', 'replace').strip()[:200])
    return result.stdout


def load_service_account(path):
    try:
        info = json.loads(Path(path).read_text(encoding='utf-8'))
    except (OSError, ValueError) as error:
        raise PlayError(f'cannot read the service account file: {type(error).__name__}') from None
    if not isinstance(info, dict) or info.get('type') != 'service_account':
        raise PlayError('credentials file is not a service account JSON key')
    for field in ('client_email', 'private_key', 'token_uri'):
        if not info.get(field):
            raise PlayError(f'service account JSON key lacks {field}')
    return info


def jwt_assertion(info, now, signer=sign_rs256):
    header = {'alg': 'RS256', 'typ': 'JWT'}
    claims = {'iss': info['client_email'], 'scope': SCOPE, 'aud': info['token_uri'],
              'iat': now, 'exp': now + 3600}
    signing_input = b'.'.join(b64url(json.dumps(part, separators=(',', ':')).encode('utf-8'))
                              for part in (header, claims))
    return (signing_input + b'.' + b64url(signer(signing_input, info['private_key']))).decode('ascii')


def urllib_send(method, url, headers, body, timeout):
    """One HTTP exchange. Returns (status, headers, body); Google error bodies become PlayError."""
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode('utf-8', 'replace')
        try:
            parsed = json.loads(detail)
            message = parsed.get('error_description') or parsed['error']['message']
        except (ValueError, KeyError, TypeError, AttributeError):
            message = detail.strip()[:300]
        raise PlayError(f'{method} {urllib.parse.urlsplit(url).path} -> HTTP {error.code}: {message}') from None
    except urllib.error.URLError as error:
        raise PlayError(f'{method} {urllib.parse.urlsplit(url).path} failed: {error.reason}') from None


class Play:
    """Minimal androidpublisher v3 edits client."""

    def __init__(self, package, token, send=urllib_send):
        self.package = package
        self.token = token
        self.send = send

    @classmethod
    def login(cls, package, info, send=urllib_send, signer=sign_rs256, now=None):
        assertion = jwt_assertion(info, int(now if now is not None else time.time()), signer)
        body = urllib.parse.urlencode({'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                                       'assertion': assertion}).encode('ascii')
        _, _, raw = send('POST', info['token_uri'], {'Content-Type': 'application/x-www-form-urlencoded'},
                         body, 60)
        token = json.loads(raw).get('access_token')
        if not token:
            raise PlayError('token response has no access_token')
        return cls(package, token, send)

    def _url(self, path, upload=False):
        prefix = '/upload' if upload else ''
        return f'{API}{prefix}/androidpublisher/v3/applications/{self.package}/{path}'

    def _call(self, method, path, payload=None, query=None, timeout=60):
        url = self._url(path)
        if query:
            url += '?' + urllib.parse.urlencode(query)
        headers = {'Authorization': 'Bearer ' + self.token}
        body = None
        if payload is not None:
            headers['Content-Type'] = 'application/json; charset=utf-8'
            body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        _, _, raw = self.send(method, url, headers, body, timeout)
        return json.loads(raw) if raw else {}

    def insert_edit(self):
        return self._call('POST', 'edits', {})['id']

    def delete_edit(self, edit):
        self._call('DELETE', f'edits/{edit}')

    def tracks(self, edit):
        return self._call('GET', f'edits/{edit}/tracks').get('tracks', [])

    def bundles(self, edit):
        return self._call('GET', f'edits/{edit}/bundles').get('bundles', [])

    def upload_bundle(self, edit, data):
        # Single-chunk resumable upload: AABs are tens of MB, above the simple-upload comfort size.
        headers = {'Authorization': 'Bearer ' + self.token, 'Content-Type': 'application/json; charset=utf-8',
                   'X-Upload-Content-Type': 'application/octet-stream',
                   'X-Upload-Content-Length': str(len(data))}
        url = self._url(f'edits/{edit}/bundles', upload=True) + '?uploadType=resumable'
        _, response_headers, _ = self.send('POST', url, headers, b'', 60)
        location = {k.lower(): v for k, v in response_headers.items()}.get('location')
        if not location:
            raise PlayError('resumable upload session has no Location header')
        _, _, raw = self.send('PUT', location, {'Authorization': 'Bearer ' + self.token,
                                                'Content-Type': 'application/octet-stream'},
                              data, UPLOAD_TIMEOUT)
        return json.loads(raw)

    def update_track(self, edit, track, release):
        return self._call('PUT', f'edits/{edit}/tracks/{track}', {'track': track, 'releases': [release]})

    def validate(self, edit):
        self._call('POST', f'edits/{edit}:validate')

    def commit(self, edit, not_sent_for_review=False):
        query = {'changesNotSentForReview': 'true'} if not_sent_for_review else None
        self._call('POST', f'edits/{edit}:commit', query=query)


def read_notes(path):
    if not path:
        return ''
    notes = Path(path).read_text(encoding='utf-8').replace('\r\n', '\n').strip()
    if len(notes) > NOTES_LIMIT:
        raise PlayError(f'release notes are {len(notes)} characters; Play allows {NOTES_LIMIT}')
    return notes


def describe_tracks(tracks):
    lines = []
    for track in sorted(tracks, key=lambda t: t.get('track', '')):
        releases = track.get('releases') or []
        summary = '; '.join(f"{r.get('status')} {r.get('name', '')} codes={','.join(r.get('versionCodes', []))}"
                            for r in releases) or 'no releases'
        lines.append(f"{track.get('track')}: {summary}")
    return lines


def check(play, version_code=None, log=print):
    edit = play.insert_edit()
    try:
        for line in describe_tracks(play.tracks(edit)):
            log('  ' + line)
        codes = [int(b['versionCode']) for b in play.bundles(edit) if 'versionCode' in b]
        highest = max(codes, default=0)
        log(f'highest uploaded versionCode: {highest}')
        if version_code is not None and version_code <= highest:
            raise PlayError(f'VERSION_CODE {version_code} must be greater than {highest}, the highest in Play')
    finally:
        play.delete_edit(edit)
    return highest


def upload(play, aab, version_code, version_name, track, status, notes, validate_only=False, log=print):
    if track not in TRACKS or status not in STATUSES:
        raise PlayError(f'track must be one of {TRACKS} and status one of {STATUSES}')
    data = Path(aab).read_bytes()
    local_sha256 = hashlib.sha256(data).hexdigest()
    edit = play.insert_edit()
    finished = False
    try:
        bundle = play.upload_bundle(edit, data)
        if int(bundle.get('versionCode', -1)) != version_code:
            raise PlayError(f"Play read versionCode {bundle.get('versionCode')} from the bundle, expected {version_code}")
        if bundle.get('sha256') and bundle['sha256'] != local_sha256:
            raise PlayError('Play reports a different bundle sha256 than the local file')
        log(f'uploaded bundle versionCode={version_code} sha256={local_sha256}')
        release = {'name': f'{version_code} ({version_name})', 'versionCodes': [str(version_code)], 'status': status}
        if notes:
            release['releaseNotes'] = [{'language': NOTES_LANGUAGE, 'text': notes}]
        play.update_track(edit, track, release)
        log(f"set {track} release '{release['name']}' status={status} notes={'yes' if notes else 'no'}")
        if validate_only:
            play.validate(edit)
            log('validated; edit deleted without commit (nothing changed in Play)')
            return 'validated'
        try:
            play.commit(edit)
            outcome = 'committed'
        except PlayError as error:
            if 'changesNotSentForReview' not in str(error):
                raise
            # Play Console holds other changes not yet sent for review; committing would otherwise fail.
            play.commit(edit, not_sent_for_review=True)
            outcome = 'committed-not-sent-for-review'
        finished = True
    finally:
        if not finished:
            try:
                play.delete_edit(edit)
            except PlayError as error:
                log(f'warning: could not delete edit: {error}')
    if outcome == 'committed':
        log('committed: ' + NEXT_STEP[track, status])
    else:
        log('committed without sending for review: open Play Console > Publishing overview and send the changes')
    return outcome


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    sub = parser.add_subparsers(dest='command', required=True)
    for name in ('check', 'upload'):
        command = sub.add_parser(name)
        command.add_argument('--credentials', required=True, help='service account JSON key file')
        command.add_argument('--package', required=True)
        command.add_argument('--version-code', type=int, required=(name == 'upload'))
    up = sub.choices['upload']
    up.add_argument('--aab', required=True)
    up.add_argument('--version-name', required=True)
    up.add_argument('--track', choices=TRACKS, required=True)
    up.add_argument('--status', choices=STATUSES, required=True)
    up.add_argument('--notes-file', help=f'{NOTES_LANGUAGE} release notes, UTF-8, up to {NOTES_LIMIT} characters')
    up.add_argument('--validate-only', action='store_true')
    args = parser.parse_args(argv)
    try:
        notes = read_notes(args.notes_file) if args.command == 'upload' else ''
        play = Play.login(args.package, load_service_account(args.credentials))
        if args.command == 'check':
            print(f'Google Play {args.package}:')
            check(play, args.version_code)
        else:
            upload(play, args.aab, args.version_code, args.version_name, args.track, args.status, notes,
                   args.validate_only)
    except PlayError as error:
        print(f'play-upload: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
