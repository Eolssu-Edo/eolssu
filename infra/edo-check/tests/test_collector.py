from test_relay import load
import unittest
from unittest.mock import patch
import subprocess
import tempfile
from pathlib import Path

collector = load('collector')


class MetricTests(unittest.TestCase):
    def test_memory_subtracts_inactive_file_cache_without_negative_values(self):
        self.assertEqual(collector.resource_stats({'memory_stats': {'usage': 100, 'stats': {'inactive_file': 20}}})['memory'], 80)
        self.assertEqual(collector.resource_stats({'memory_stats': {'usage': 10, 'stats': {'cache': 100}}})['memory'], 0)

    def test_cpu_accounts_for_online_cores(self):
        stats = {'cpu_stats': {'cpu_usage': {'total_usage': 300}, 'system_cpu_usage': 1000, 'online_cpus': 4},
                 'precpu_stats': {'cpu_usage': {'total_usage': 100}, 'system_cpu_usage': 600}}
        self.assertEqual(collector.resource_stats(stats)['cpu'], 200)

    def test_shared_image_storage_is_not_sum_of_virtual_image_sizes(self):
        self.assertEqual(collector.image_disk_bytes({'LayersSize': 1000, 'Images': [{'Size': 600}, {'Size': 700}]}), 1000)

    def test_missing_stats_are_not_reported_as_zero_usage(self):
        metrics = collector.resource_stats({})
        self.assertFalse(metrics['memory_available'])
        self.assertFalse(metrics['cpu_available'])

    def test_current_failed_services_and_past_oom_logs_have_separate_findings(self):
        issues = []
        results = [subprocess.CompletedProcess([], 0, '', ''),
                   subprocess.CompletedProcess([], 0, 'oom-kill: first\nOut of memory: Killed process second\n', '')]
        with patch.object(collector, 'run', side_effect=results):
            failures, oom_lines = collector.system_events(issues)
        self.assertEqual((failures, oom_lines), (0, 2))
        self.assertEqual(issues, ['최근 24h OOM 매칭 로그 2줄 (과거 기록·주의)'])

    def test_unreadable_system_events_are_unknown_and_never_zero(self):
        issues = []
        results = [subprocess.CompletedProcess([], 1, '', 'access denied'),
                   subprocess.CompletedProcess([], 1, '', 'access denied')]
        with patch.object(collector, 'run', side_effect=results):
            self.assertEqual(collector.system_events(issues), ('확인 불가', '확인 불가'))
        self.assertTrue(all('확인 불가' in issue for issue in issues))

    def test_stopped_container_never_passes_using_old_health_result(self):
        data = {'item': {'Names': ['/demo'], 'State': 'exited'},
                'detail': {'State': {'Status': 'exited', 'Running': False, 'Health': {'Status': 'healthy'}}}, 'stats': {}}
        with patch.object(collector, 'snapshot', return_value=({}, [data], {}, [])), \
                patch.object(collector, 'edo1_host_health', return_value=[]), \
                patch.object(collector, 'prometheus_health', return_value=[]):
            _, rows, _, _ = collector.health_report()
        row = next(row for row in rows if row[0] == 'demo')
        self.assertNotEqual(row[2], '정상')
        self.assertIn('중지', row[3])

    def test_health_reports_only_the_selected_backend_as_required(self):
        blue = 'eolssu-runtime-backend-1'
        green = 'eolssu-runtime-backend-green-1'
        healthy = {'item': {'Names': ['/' + green], 'State': 'running'},
                   'detail': {'State': {'Running': True, 'Status': 'running', 'Health': {'Status': 'healthy'}}}}
        with tempfile.TemporaryDirectory() as folder:
            route = Path(folder) / 'active.conf'
            route.write_text('set $backend http://backend-green:8080;\n')
            with patch.object(collector, 'Path', return_value=route), \
                    patch.object(collector, 'edo1_host_health', return_value=[]), \
                    patch.object(collector, 'prometheus_health', return_value=[]), \
                    patch.object(collector, 'snapshot', side_effect=[({}, [healthy], {}, []), ({}, [], {}, [])]):
                _, running, _, facts = collector.health_report()
                _, missing, _, _ = collector.health_report()
        self.assertEqual(facts['backend_slot'], 'backend-green')
        self.assertEqual([(row[0], row[2]) for row in running if row[0] in (blue, green)], [(green, '정상')])
        self.assertEqual([(row[0], row[2]) for row in missing if row[0] in (blue, green)], [(green, '실패')])

    def test_invalid_route_reports_unknown_without_guessing_the_required_slot(self):
        with tempfile.TemporaryDirectory() as folder:
            route = Path(folder) / 'active.conf'
            route.write_text('invalid route')
            with patch.object(collector, 'Path', return_value=route), \
                    patch.object(collector, 'edo1_host_health', return_value=[]), \
                    patch.object(collector, 'prometheus_health', return_value=[]), \
                    patch.object(collector, 'snapshot', return_value=({}, [], {}, [])):
                _, rows, issues, facts = collector.health_report()
        self.assertIsNone(facts['backend_slot'])
        self.assertIn('활성 백엔드 슬롯 확인 불가', issues)
        self.assertFalse(any(row[0].startswith('eolssu-runtime-backend') for row in rows))

    def test_stats_timeout_preserves_inspect_state_and_reports_partial_measurement(self):
        detail = {'Name': '/demo', 'State': {'Status': 'running', 'Running': True}}
        def docker(path, **kwargs):
            if path == '/info': return {'Containers': 1}
            if path.startswith('/containers/json'): return [{'Id': 'a'*64, 'Names': ['/demo'], 'State': 'running'}]
            if path.endswith('/json'): return detail
            raise TimeoutError()
        with patch.object(collector, 'docker', side_effect=docker):
            _, values, _, issues = collector.snapshot()
        self.assertEqual(values[0]['detail'], detail)
        self.assertTrue(any('통계' in item for item in issues))

    def test_local_log_measures_primary_and_rotated_file_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            logdir = Path(folder)/'containers'/('a'*64)/'local-logs'
            logdir.mkdir(parents=True)
            (logdir/'container.log').write_bytes(b'x'*128)
            (logdir/'container.log.1.gz').write_bytes(b'x'*64)
            (logdir/'container.log.link').symlink_to('/etc/passwd')
            detail = {'Id': 'a'*64, 'HostConfig': {'LogConfig': {'Type': 'local'}}, 'LogPath': ''}
            value = collector.log_usage(detail, folder)
        self.assertIn('192.0B', value)
        self.assertIn('local', value)


if __name__ == '__main__':
    unittest.main()
