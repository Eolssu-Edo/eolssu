import unittest
from unittest.mock import patch

from test_relay import load

collector = load('collector')


class ServerDiskTests(unittest.TestCase):
    def server_snapshot(self, storage):
        paths = []
        files = {'/proc/meminfo': 'MemTotal: 1024 kB\nMemAvailable: 512 kB\nSwapTotal: 0 kB\nSwapFree: 0 kB',
                 '/etc/os-release': 'PRETTY_NAME="Linux"', '/proc/uptime': '120 200'}
        container_id = 'a' * 64

        def docker(path, **kwargs):
            paths.append(path)
            if path == '/info':
                return {'ServerVersion': '29.9.2', 'Containers': 1, 'MemTotal': 1024 * 1024}
            if path.startswith('/containers/json?'):
                return [{'Id': container_id, 'Names': ['/demo'], 'State': 'running'}]
            if path == f'/containers/{container_id}/json':
                return {'State': {'Running': True}}
            if path == f'/containers/{container_id}/stats?stream=false':
                return {'memory_stats': {'usage': 1024}}
            if path == '/system/df':
                if isinstance(storage, Exception):
                    raise storage
                return storage
            self.fail(f'Unexpected Docker path {path}')

        with patch.object(collector.Path, 'read_text', lambda path: files[str(path)]), \
                patch.object(collector, 'docker', side_effect=docker), \
                patch.object(collector, 'cpu_sample', return_value=1), \
                patch.object(collector, 'host_disks', return_value='sample'), \
                patch.object(collector, 'system_events', return_value=(0, 0)), \
                patch.object(collector, 'run', side_effect=AssertionError('duplicate docker system df')) as run:
            _, rows, issues, _ = collector.server_report('edo2')
        run.assert_not_called()
        self.assertEqual([path for path in paths if path.startswith('/system/df')], ['/system/df'])
        self.assertNotIn('/containers/json?all=1&size=1', paths)
        self.assertIn('정상 · 29.9.2 · 실행 1/전체 1', rows[0][5])
        self.assertEqual(rows[0][6], '1.0KiB / 인식 1.0MiB')
        return rows[0], issues

    def test_server_reads_disk_categories_and_reclaimable_from_one_engine_request(self):
        row, issues = self.server_snapshot({
            'ImageUsage': {'TotalSize': 4096, 'Reclaimable': 512},
            'ContainerUsage': {'TotalSize': 2048},
            'VolumeUsage': {'TotalSize': 1024, 'Reclaimable': 128},
            'BuildCacheUsage': {},
            'LayersSize': 1,  # API 1.52 can include legacy keys beside the summaries.
        })
        self.assertEqual(row[7], '4.0KiB / 2.0KiB / 1.0KiB / 0.0B')
        self.assertEqual(row[8], '이미지 512.0B · 쓰기 0.0B · 볼륨 128.0B · 캐시 0.0B')
        self.assertEqual(issues, [])

    def test_disk_timeout_preserves_already_collected_engine_and_ram(self):
        row, issues = self.server_snapshot(TimeoutError())
        self.assertEqual(row[7:9], ['확인 불가', '확인 불가'])
        self.assertEqual(issues, ['Docker 저장공간 수집 실패·시간 초과'])

    def test_missing_category_preserves_other_disk_measurements(self):
        row, issues = self.server_snapshot({'ImageUsage': {'TotalSize': 4096},
                                            'ContainerUsage': {'TotalSize': -1, 'Reclaimable': 'invalid'},
                                            'BuildCacheUsage': {}})
        self.assertEqual(row[7], '4.0KiB / 확인 불가 / 확인 불가 / 0.0B')
        self.assertEqual(row[8], '이미지 0.0B · 쓰기 확인 불가 · 볼륨 확인 불가 · 캐시 0.0B')
        self.assertEqual(issues, ['Docker 저장공간 일부 수집 실패'])

    def test_container_volume_report_reads_only_volume_usage_details(self):
        paths = []
        container_id = 'a' * 64

        def docker(path, **kwargs):
            paths.append(path)
            if path == '/info':
                return {'DockerRootDir': '/var/lib/docker'}
            if path == '/containers/json?all=1&size=1':
                return [{'Id': container_id, 'Names': ['/demo'], 'State': 'exited', 'SizeRw': 12}]
            if path == f'/containers/{container_id}/json':
                return {'State': {'Running': False, 'Status': 'exited', 'StartedAt': '2026-01-01T00:00:00Z'},
                        'HostConfig': {'LogConfig': {'Type': 'none'}}, 'Config': {},
                        'Mounts': [{'Type': 'volume', 'Name': 'db', 'Destination': '/data'}]}
            if path == '/system/df?type=volume&verbose=1':
                return {'VolumeUsage': {'TotalSize': 4096, 'Items': [
                    {'Name': 'db', 'UsageData': {'Size': 4096, 'RefCount': 2}}]}}
            self.fail(f'Unexpected Docker path {path}')

        with patch.object(collector, 'docker', side_effect=docker):
            _, rows, _, _ = collector.container_report('edo2')
        self.assertEqual([path for path in paths if path.startswith('/system/df')], ['/system/df?type=volume&verbose=1'])
        self.assertEqual(rows[0][5], '볼륨 db: 4.0KiB (공유)')
