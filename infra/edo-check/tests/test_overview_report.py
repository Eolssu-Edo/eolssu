import unittest
from unittest.mock import patch

from test_relay import relay


class OverviewReportTests(unittest.TestCase):
    def render(self, results, target='edo1'):
        return relay.build_overview(target, results, 'req', '2026-11-10 16:45:10 KST', 18.5)

    def test_groups_all_three_modes_and_keeps_measurements_and_status(self):
        health = {'mode': 'health', 'columns': list(range(6)), 'rows': [
            ['API health', 'edo2 외부 HTTPS', '정상', 'HTTP 200 · JSON status=UP', '10ms', '—'],
            ['SSH', 'edo1 systemd', '정상', 'active/running', '—', '—'],
            ['DB 백업 예약', 'edo1 systemd', '주의', '타이머 지연', '—', '타이머 확인'],
        ]}
        server = {'mode': 'server', 'columns': list(range(10)),
                  'rows': [['Ubuntu', '12.3%', '8GiB', '0B', '/ 20%', '엔진 정상',
                            '2GiB', '10GB / 5GB / 2GB / 1GB', '10GB', '0개']]}
        containers = {'mode': 'containers', 'columns': list(range(11)),
                      'rows': [['eolssu-replication-proxy-579', 'running/healthy', '1.0%/미설정',
                                '64MiB/128MiB', '1MiB · 로그 2MiB', 'bind /data: 5GiB',
                                '1시간/0회', '0/아니오', '92/13306', '1B/2B · IO 3B/4B', 'test:1']],
                      'facts': [{'name': 'eolssu-replication-proxy-579', 'cpu_percent': 1, 'ram_bytes': 67108864,
                                 'ram_limit': 134217728, 'readiness_kind': 'tcp_listener',
                                 'readiness': ['정상', '로컬 수신 확인 · 복제 통신은 /edo2-health에서 확인', '1ms']}]}
        messages = self.render({'health': health, 'server': server, 'containers': containers})
        self.assertEqual(len(messages), 1)
        message = messages[0].replace('\u2060', '').replace('\u00a0', ' ')
        self.assertEqual(message.splitlines()[0], '### edo1')
        for heading in ('서비스·통신', '호스트·모니터링', '백업·복제', '서버 자원', '서버 저장공간',
                        '컨테이너 상태', '컨테이너 자원', '데이터 저장공간', '실행·포트', '통신량·이미지'):
            self.assertIn(f'**{heading}**', message)
        for data in ('JSON status=UP', 'active/running', '타이머 지연', '12.3%', '2GiB', '로컬 수신 확인',
                     'bind /data', '5GiB', '92/13306', '1B/2B · IO 3B/4B', 'test:1', '/edo2의 백업·복제 표'):
            self.assertIn(data, message)
        self.assertIn('| 상태 | 0 | ⚠️ **1** | 0 | 0 |', message)
        self.assertNotIn('/edo2-health', message)

    def test_missing_and_invalid_results_leave_the_valid_mode_visible(self):
        health = {'mode': 'health', 'columns': list(range(4)),
                  'rows': [['중계 HTTPS', 'edo2 자체 HTTPS', '정상', 'HTTP 200']]}
        bad_server = {'mode': 'server', 'columns': [], 'rows': []}
        message, = self.render({'health': health, 'server': bad_server}, target='edo2')
        self.assertIn('HTTP 200', message)
        self.assertIn('서버 수집 실패', message)
        self.assertIn('컨테이너 수집 실패', message)
        self.assertNotIn('✅ 정상', message.splitlines()[1])

    def test_continuations_repeat_table_columns_and_mark_unpublished_rows(self):
        health = {'mode': 'health', 'columns': list(range(4)),
                  'rows': [[f'대상{i:02}', 'Docker', '정상', '근거' * 90] for i in range(40)]}
        with patch.object(relay, 'REPORT_LIMIT', 1300):
            messages = self.render({'health': health})
        self.assertEqual(len(messages), 5)
        self.assertTrue(all('요청 `req`' in part and len(part) <= 1300 for part in messages))
        continuation = next(part for part in messages[1:] if '**서비스·통신 (계속)**' in part)
        self.assertIn('| **대상** | **판정** | **검사 근거 · 소요** | **필요한 조치** |', continuation)
        self.assertIn('출력 제한', messages[-1])
