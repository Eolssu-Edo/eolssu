from datetime import datetime, timedelta, timezone
import json
import subprocess
import unittest
from unittest.mock import patch

from test_relay import load

collector = load('collector')
relay = load('relay')


def before(minutes):
    return (datetime.now(timezone.utc) - timedelta(minutes=minutes)).isoformat()


class Edo1DependencyTests(unittest.TestCase):
    def rows(self, *, storage_age=10, outside_age=1, write_status='ok', timer='waiting',
             outside_ok=True, read_detail='range_ok', sheet_detail='put_signed_get_delete_ok'):
        now = datetime.now(timezone.utc)
        timer_stdout = (f'LoadState=loaded\nActiveState=active\nSubState={timer}\nUnitFileState=enabled\n')
        read_result = {'status': 'ok' if read_detail == 'range_ok' else 'failed', 'detail': read_detail, 'elapsed_ms': 20}
        sheet_result = {'status': 'ok' if sheet_detail == 'put_signed_get_delete_ok' else 'failed',
                        'detail': sheet_detail, 'elapsed_ms': 42}
        attempt = before(4)
        write = {'last_attempt_at': attempt, 'status': write_status,
                 'detail': 'put_get_delete_ok' if write_status == 'ok' else 'write_read',
                 'last_success_at': before(3.98 if write_status == 'ok' else 12)}
        outside = {'captured_at': before(outside_age),
                   'relay': {'ok': outside_ok, 'detail': 'HTTP 200 · 중계 JSON 정상' if outside_ok else 'HTTP 503'},
                   'terms': {'ok': True, 'detail': 'HTTP 200 · 약관 목록 JSON 정상'}}

        def run(arguments, **kwargs):
            data = timer_stdout if arguments[0] == 'systemctl' else json.dumps(
                sheet_result if arguments[-1] == 'sheet' else read_result)
            return subprocess.CompletedProcess(arguments, 0, data, '')

        def record(path):
            return outside if path.name == 'latest.json' else write

        with patch.object(collector, 'run', side_effect=run), \
                patch.object(collector, 'read_record', side_effect=record), \
                patch.object(collector, 'fresh_storage_measurement',
                             return_value=({}, now - timedelta(minutes=storage_age))):
            return collector.edo1_dependency_rows()

    def test_running_timer_and_valid_checks_are_normal(self):
        rows = self.rows(timer='running')
        self.assertEqual({row[0]: row[2] for row in rows},
                         {'R2 읽기': '정상', 'R2 쓰기': '정상', '악보 음원 R2': '정상', '저장공간 자동 측정': '정상', '독립 HTTPS': '정상'})
        self.assertTrue(any('Jenkins' in row[3] and 'Docker' in row[3] for row in rows))

    def test_stale_and_failed_results_remain_visible_with_their_own_actions(self):
        rows = self.rows(storage_age=46, outside_age=8, write_status='failed', read_detail='r2_http_403')
        self.assertEqual({row[0]: row[2] for row in rows},
                         {'R2 읽기': '실패', 'R2 쓰기': '실패', '악보 음원 R2': '정상', '저장공간 자동 측정': '주의', '독립 HTTPS': '주의'})
        report = relay.build_report({'target': 'edo1', 'mode': 'health', 'columns': ['a'] * 6, 'rows': rows}, 'test')
        self.assertIn('R2 HTTP 403', report)
        self.assertIn('최근 성공', report)
        self.assertIn('journalctl -u edo-check-outside.service', report)

    def test_independent_https_failure_does_not_hide_the_other_endpoint(self):
        row = next(row for row in self.rows(outside_ok=False) if row[0] == '독립 HTTPS')
        self.assertEqual(row[2], '실패')
        self.assertIn('HTTP 503', row[3])
        self.assertIn('약관 목록 JSON 정상', row[3])


    def test_sheet_storage_report_names_failed_steps_and_suppresses_private_details(self):
        row = next(row for row in self.rows(sheet_detail='sheet_download_delete') if row[0] == '악보 음원 R2')
        self.assertEqual(row[2], '실패')
        self.assertIn('서명 다운로드', row[3])
        self.assertIn('삭제 확인 실패', row[3])

        private = 'https://example.invalid/?X-Amz-Signature=private'
        rows = self.rows(sheet_detail=private)
        row = next(row for row in rows if row[0] == '악보 음원 R2')
        self.assertEqual(row[2], '미측정')
        report = relay.build_report({'target': 'edo1', 'mode': 'health', 'columns': ['a'] * 6, 'rows': rows}, 'test')
        self.assertNotIn(private, report)
        self.assertNotIn('X-Amz-Signature', report)
