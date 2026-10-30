import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('publish', Path(__file__).with_name('publish.py'))
publish = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publish)


class PublishTests(unittest.TestCase):
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
        sql = publish.build_insert('2026-10-30 00:00:00')
        self.assertIn('is_required,seq,effective_at', sql)
        self.assertNotIn('UPDATE ', sql)
        for _, _, document, _, _ in publish.ROWS:
            self.assertTrue((publish.RESOURCE / (document + '.html')).is_file())


if __name__ == '__main__':
    unittest.main()
