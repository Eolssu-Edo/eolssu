"""Play 업로드 도구의 edits 순서·실패 시 정리·토큰 서명 계약을 HTTP 대역으로 검사한다(S15P31E102-1254)."""
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import urllib.parse

spec = importlib.util.spec_from_file_location('play_upload', Path(__file__).parents[1] / 'play-upload.py')
play_upload = importlib.util.module_from_spec(spec)
spec.loader.exec_module(play_upload)

PACKAGE = 'com.ssafy.eolssu'
BASE = f'/androidpublisher/v3/applications/{PACKAGE}/edits'
AAB = b'fake-aab-bytes'
INFO = {'type': 'service_account', 'client_email': 'ci@example.iam.gserviceaccount.com',
        'private_key': 'PEM', 'token_uri': 'https://oauth2.googleapis.com/token'}


class FakePlay:
    """HTTP 대역: 요청을 기록하고 경로별로 정해 둔 응답이나 오류를 돌려준다."""

    def __init__(self, version_code=11, bundles=(10, 9), commit_errors=()):
        self.calls = []
        self.version_code = version_code
        self.bundle_codes = bundles
        self.commit_errors = list(commit_errors)
        self.fail_on = None

    def __call__(self, method, url, headers, body, timeout):
        parts = urllib.parse.urlsplit(url)
        path, query = parts.path, urllib.parse.parse_qs(parts.query)
        self.calls.append((method, path, query, body))
        if self.fail_on and self.fail_on in (method + ' ' + path):
            raise play_upload.PlayError(f'{method} {path} -> HTTP 500: boom')
        if path == '/token':
            return 200, {}, json.dumps({'access_token': 'tok'}).encode()
        if method == 'POST' and path == BASE:
            return 200, {}, b'{"id": "e1"}'
        if method == 'DELETE':
            return 204, {}, b''
        if path == BASE + '/e1/tracks':
            return 200, {}, json.dumps({'tracks': [
                {'track': 'production', 'releases': [{'status': 'completed', 'name': '10 (1.3.0)', 'versionCodes': ['10']}]},
                {'track': 'internal'}]}).encode()
        if method == 'GET' and path == BASE + '/e1/bundles':
            return 200, {}, json.dumps({'bundles': [{'versionCode': c} for c in self.bundle_codes]}).encode()
        if method == 'POST' and path.startswith('/upload/'):
            assert headers['X-Upload-Content-Length'] == str(len(AAB))
            return 200, {'Location': 'https://androidpublisher.googleapis.com/upload/session/1'}, b''
        if method == 'PUT' and path == '/upload/session/1':
            return 200, {}, json.dumps({'versionCode': self.version_code,
                                        'sha256': hashlib.sha256(body).hexdigest()}).encode()
        if method == 'PUT' and path.startswith(BASE + '/e1/tracks/'):
            return 200, {}, body
        if method == 'POST' and path.endswith(':commit'):
            if self.commit_errors:
                raise play_upload.PlayError(self.commit_errors.pop(0))
            return 200, {}, b'{"id": "e1"}'
        if method == 'POST' and path.endswith(':validate'):
            return 200, {}, b'{"id": "e1"}'
        raise AssertionError(f'unexpected {method} {path}')

    def names(self):
        return [m + ' ' + p.replace(BASE, '') + ('?' + urllib.parse.urlencode(q, doseq=True) if q else '')
                for m, p, q, _ in self.calls if p != '/token']

    def track_body(self):
        return next(json.loads(b) for m, p, _, b in self.calls if m == 'PUT' and p.startswith(BASE))


def login(fake):
    return play_upload.Play.login(PACKAGE, INFO, send=fake, signer=lambda m, k: b'sig', now=1000)


class UploadTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.dir)
        self.aab = self.dir / 'eolssu-1.4.0-vc11.aab'
        self.aab.write_bytes(AAB)
        self.log = []

    def run_upload(self, fake, **overrides):
        args = dict(track='production', status='draft', notes='버그를 고쳤습니다.')
        args.update(overrides)
        return play_upload.upload(login(fake), self.aab, 11, '1.4.0', args['track'], args['status'],
                                  args['notes'], args.get('validate_only', False), log=self.log.append)

    def test_draft_upload_commits_one_release_with_korean_notes(self):
        fake = FakePlay()
        self.assertEqual(self.run_upload(fake), 'committed')
        self.assertEqual(fake.names(), ['POST ', 'POST /upload/e1/bundles?uploadType=resumable', 'PUT /upload/session/1',
                                        'PUT /e1/tracks/production', 'POST /e1:commit'])
        self.assertEqual(fake.track_body(), {'track': 'production', 'releases': [{
            'name': '11 (1.4.0)', 'versionCodes': ['11'], 'status': 'draft',
            'releaseNotes': [{'language': 'ko-KR', 'text': '버그를 고쳤습니다.'}]}]})

    def test_empty_notes_are_left_out(self):
        fake = FakePlay()
        self.run_upload(fake, track='internal', notes='')
        self.assertNotIn('releaseNotes', fake.track_body()['releases'][0])

    def test_validate_only_deletes_edit_without_commit(self):
        fake = FakePlay()
        self.assertEqual(self.run_upload(fake, validate_only=True), 'validated')
        self.assertEqual(fake.names()[-2:], ['POST /e1:validate', 'DELETE /e1'])
        self.assertNotIn('POST /e1:commit', fake.names())

    def test_version_code_mismatch_deletes_edit_before_touching_track(self):
        fake = FakePlay(version_code=10)
        with self.assertRaisesRegex(play_upload.PlayError, 'expected 11'):
            self.run_upload(fake)
        self.assertEqual(fake.names()[-1], 'DELETE /e1')
        self.assertFalse(any('tracks' in n for n in fake.names()))

    def test_failed_track_update_deletes_edit(self):
        fake = FakePlay()
        fake.fail_on = 'PUT ' + BASE + '/e1/tracks'
        with self.assertRaises(play_upload.PlayError):
            self.run_upload(fake)
        self.assertEqual(fake.names()[-1], 'DELETE /e1')

    def test_pending_console_changes_commit_without_sending_for_review(self):
        fake = FakePlay(commit_errors=['HTTP 400: Changes cannot be sent for review automatically. '
                                       'Please set the query parameter changesNotSentForReview to true.'])
        self.assertEqual(self.run_upload(fake, status='completed'), 'committed-not-sent-for-review')
        self.assertEqual(fake.names()[-2:], ['POST /e1:commit', 'POST /e1:commit?changesNotSentForReview=true'])
        self.assertNotIn('DELETE /e1', fake.names())

    def test_other_commit_errors_delete_edit(self):
        fake = FakePlay(commit_errors=['HTTP 403: The caller does not have permission'])
        with self.assertRaisesRegex(play_upload.PlayError, 'permission'):
            self.run_upload(fake)
        self.assertEqual(fake.names()[-1], 'DELETE /e1')


class CheckTest(unittest.TestCase):
    def test_reports_tracks_and_highest_code_then_deletes_edit(self):
        fake, log = FakePlay(bundles=(9, 10)), []
        self.assertEqual(play_upload.check(login(fake), 11, log=log.append), 10)
        self.assertIn('  production: completed 10 (1.3.0) codes=10', log)
        self.assertIn('  internal: no releases', log)
        self.assertEqual(fake.names()[-1], 'DELETE /e1')

    def test_rejects_version_code_not_above_play(self):
        fake = FakePlay(bundles=(10,))
        with self.assertRaisesRegex(play_upload.PlayError, 'greater than 10'):
            play_upload.check(login(fake), 10, log=lambda _: None)
        self.assertEqual(fake.names()[-1], 'DELETE /e1')


class InputTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.dir)

    def test_notes_over_play_limit_are_rejected(self):
        notes = self.dir / 'notes.txt'
        notes.write_text('가' * 501, encoding='utf-8')
        with self.assertRaisesRegex(play_upload.PlayError, '501 characters'):
            play_upload.read_notes(notes)
        notes.write_bytes(' 첫 줄\r\n둘째 줄 \n'.encode('utf-8'))
        self.assertEqual(play_upload.read_notes(notes), '첫 줄\n둘째 줄')

    def test_non_service_account_file_is_rejected_without_echoing_it(self):
        path = self.dir / 'key.json'
        path.write_text(json.dumps({'type': 'authorized_user', 'private_key': 'SECRET'}), encoding='utf-8')
        with self.assertRaises(play_upload.PlayError) as caught:
            play_upload.load_service_account(path)
        self.assertNotIn('SECRET', str(caught.exception))

    def test_login_posts_jwt_bearer_assertion(self):
        fake = FakePlay()
        self.assertEqual(login(fake).token, 'tok')
        form = urllib.parse.parse_qs(fake.calls[0][3].decode())
        self.assertEqual(form['grant_type'], ['urn:ietf:params:oauth:grant-type:jwt-bearer'])
        header, claims, signature = form['assertion'][0].split('.')
        decode = lambda part: json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))
        self.assertEqual(decode(header), {'alg': 'RS256', 'typ': 'JWT'})
        self.assertEqual(decode(claims), {'iss': INFO['client_email'], 'scope': play_upload.SCOPE,
                                          'aud': INFO['token_uri'], 'iat': 1000, 'exp': 4600})
        self.assertEqual(signature, 'c2ln')


@unittest.skipUnless(shutil.which('openssl'), 'openssl CLI not installed')
class OpensslSignTest(unittest.TestCase):
    def test_signature_verifies_with_public_key(self):
        directory = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, directory)
        key, public, message = directory / 'k.pem', directory / 'p.pem', directory / 'm'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048',
                        '-out', str(key)], check=True, capture_output=True)
        subprocess.run(['openssl', 'pkey', '-in', str(key), '-pubout', '-out', str(public)],
                       check=True, capture_output=True)
        message.write_bytes(b'header.claims')
        signature = directory / 's'
        signature.write_bytes(play_upload.sign_rs256(b'header.claims', key.read_text()))
        verified = subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(public), '-signature',
                                   str(signature), str(message)], capture_output=True)
        self.assertEqual(verified.returncode, 0, verified.stderr)


if __name__ == '__main__':
    unittest.main()
