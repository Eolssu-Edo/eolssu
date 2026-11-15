import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import json
import copy

spec = importlib.util.spec_from_file_location('publish', Path(__file__).with_name('publish.py'))
publish = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publish)


class PublishTests(unittest.TestCase):
    def test_live_catalog_rejects_missing_required_wrong_versions_and_corrupted_titles(self):
        items = [dict(termId=str(index), code=code, title=title, required=required,
                      version=publish.VERSION, contentUrl=publish.BASE + '/api/terms/documents/' + publish.VERSION + '/' + document)
                 for index, (code, title, document, required, _) in enumerate(publish.ROWS, 1)]
        publish.verify_catalog(items)
        for broken in ([], items[:-1]):
            with self.assertRaises(ValueError):
                publish.verify_catalog(broken)
        for field, value in [('title', 'corrupted-title'), ('required', False), ('version', 'old'), ('contentUrl', 'https://example.test/other')]:
            broken = copy.deepcopy(items)
            broken[0][field] = value
            with self.assertRaises(ValueError):
                publish.verify_catalog(broken)
        broken = copy.deepcopy(items)
        broken[1]['termId'] = broken[0]['termId']
        with self.assertRaises(ValueError):
            publish.verify_catalog(broken)

    def test_registration_targets_active_backend_database_after_database_rename(self):
        inspected = [{'Config': {'Env': ['DB_URL=jdbc:mysql://mysql:3306/eolssu?connectTimeout=2000']}}]
        with patch.object(publish.subprocess, 'run', side_effect=[
            SimpleNamespace(stdout=json.dumps(inspected).encode()),
            SimpleNamespace(returncode=0, stdout=b'0\n'),
        ]) as call:
            self.assertEqual(publish.mysql('SELECT COUNT(*) FROM terms;'), '0')
            self.assertIn('--database=eolssu ', call.call_args.args[0][-1])
            self.assertIn('--default-character-set=utf8mb4 ', call.call_args.args[0][-1])
            self.assertNotIn('$MYSQL_DATABASE', call.call_args.args[0][-1])

    def test_spa_redirect_and_changed_policy_cannot_pass_preflight(self):
        html = '<html lang="ko">얼쑤 약관</html>'.encode()
        publish.verify_document(html, html, 'text/html;charset=UTF-8', 'https://example.test/privacy', 'https://example.test/privacy')
        for body, content_type, url in [(b'<html>SPA</html>', 'text/html', 'https://example.test/privacy'),
                (html, 'application/json', 'https://example.test/privacy'),
                (html, 'text/html', 'https://example.test/login')]:
            with self.assertRaises(ValueError):
                publish.verify_document(html, body, content_type, url, 'https://example.test/privacy')

    def test_required_age_and_membership_terms_do_not_make_device_permissions_required(self):
        required = {code for code, _, _, required, _ in publish.ROWS if required}
        self.assertEqual(required, {'AGE_OVER_14', 'SERVICE_TERMS', 'PRIVACY_COLLECTION'})
        self.assertEqual(len({code for code, *_ in publish.ROWS}), 4)
        sql = publish.build_insert('2026-11-16 00:00:00')
        self.assertIn('is_required,seq,effective_at', sql)
        self.assertNotIn('UPDATE ', sql)
        for _, _, document, _, _ in publish.ROWS:
            self.assertTrue((publish.RESOURCE / (document + '.html')).is_file())


if __name__ == '__main__':
    unittest.main()
