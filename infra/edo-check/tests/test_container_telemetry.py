from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from test_relay import load

collector = load('collector')
relay = load('relay')


def container(*, name='demo', config=None, mounts=(), io=None):
    container_id = 'a' * 64
    return {
        'item': {'Names': ['/' + name], 'Id': container_id, 'SizeRw': 0},
        'detail': {
            'Id': container_id,
            'Name': '/' + name,
            'State': {'Status': 'running', 'Running': True, 'Pid': 42,
                      'StartedAt': '2026-11-10T01:00:00Z', 'Health': {'Status': 'healthy'}},
            'HostConfig': {'Memory': 1000, 'LogConfig': config or {'Type': 'none'}},
            'Config': {'Image': 'fixture:1'},
            'Mounts': list(mounts),
        },
        'stats': {'networks': {'eth0': {'rx_bytes': 11, 'tx_bytes': 22}},
                  'blkio_stats': {'io_service_bytes_recursive': io},
                  'memory_stats': {'usage': 100},
                  'cpu_stats': {'system_cpu_usage': 1000}},
    }


def collect(value, target):
    with patch.object(collector, 'snapshot', return_value=({}, [value], {}, [])):
        columns, rows, issues, facts = collector.container_report(target)
    return {'mode': 'containers', 'target': target, 'columns': columns,
            'rows': rows, 'issues': issues, 'facts': facts}


class ContainerTelemetryTests(unittest.TestCase):
    def test_syslog_counts_only_approved_local_daily_files_and_labels_shared_api(self):
        value = container(config={'Type': 'syslog', 'Config': {
            'tag': 'eolssu-backend', 'syslog-address': 'tcp://127.0.0.1:1514'}}, io=[])
        with tempfile.TemporaryDirectory() as directory:
            logs = Path(directory).resolve()
            (logs / 'eolssu-backend.2026-11-09.log').write_bytes(b'x' * 64)
            (logs / 'eolssu-backend.2026-11-10.log').write_bytes(b'x' * 128)
            (logs / 'eolssu-omr.2026-11-09.log').write_bytes(b'x' * 256)
            (logs / 'eolssu-backend.invalid.log').write_bytes(b'x' * 512)
            (logs / 'eolssu-backend.2026-11-11.log').symlink_to(logs / 'eolssu-omr.2026-11-09.log')
            with patch.object(collector, 'EOLSSU_SYSLOG_DIR', logs, create=True):
                edo1 = relay.build_report(collect(value, 'edo1'), 'test')
                edo2 = relay.build_report(collect(value, 'edo2'), 'test')
                value['detail']['HostConfig']['LogConfig']['Config']['syslog-address'] = 'tcp://other:1514'
                foreign = relay.build_report(collect(value, 'edo1'), 'test')
        self.assertIn('192.0B (syslog, API 공용 로그)', edo1)
        self.assertIn('미측정 (syslog 저장소)', edo2)
        self.assertIn('미측정 (syslog 저장소)', foreign)

    def test_only_observer_root_bind_is_skipped_and_jenkins_failure_explains_cause(self):
        mounts = [{'Type': 'bind', 'Source': '/', 'Destination': '/host', 'RW': False},
                  {'Type': 'bind', 'Source': '/var/lib/jenkins', 'Destination': '/var/lib/jenkins', 'RW': True}]
        value = container(name='eolssu-monitoring-node-exporter-1', mounts=mounts, io=[])
        with patch.object(collector, 'run', side_effect=subprocess.TimeoutExpired([], 12)) as run, \
                patch.object(collector, 'jenkins_home_identity', return_value=[1, 2]):
            report = collect(value, 'edo1')
        self.assertEqual(run.call_count, 1)
        self.assertEqual(run.call_args.args[0][-1], '/var/lib/jenkins')
        self.assertIn('호스트 관측용', report['rows'][0][5])
        self.assertIn('경로당 12초 한도', report['rows'][0][5])
        value['detail']['Name'] = '/other'
        with patch.object(collector, 'run', return_value=subprocess.CompletedProcess([], 1, '', 'denied')) as run, \
                patch.object(collector, 'jenkins_home_identity', return_value=[1, 2]):
            report = collect(value, 'edo1')
        self.assertEqual(run.call_count, 2)
        self.assertIn('저장소 읽기 실패', report['rows'][0][5])
        self.assertNotIn('12초', relay.build_report(report, 'test'))

    def test_cgroup_empty_counters_and_unavailable_are_distinct_from_network(self):
        value = container()
        with tempfile.TemporaryDirectory() as directory:
            proc = Path(directory) / 'proc'
            cgroups = Path(directory) / 'cgroup'
            path = '/system.slice/docker-' + value['detail']['Id'] + '.scope'
            membership = proc / '42' / 'cgroup'
            membership.parent.mkdir(parents=True)
            membership.write_text('0::' + path + '\n')
            io_stat = cgroups / path.lstrip('/') / 'io.stat'
            io_stat.parent.mkdir(parents=True)
            with patch.object(collector, 'PROC_ROOT', proc, create=True), \
                    patch.object(collector, 'CGROUP_ROOT', cgroups, create=True):
                io_stat.write_text('')
                empty = relay.build_report(collect(value, 'edo2'), 'empty')
                io_stat.write_text('8:1 rbytes=100 wbytes=200 rios=2\n8:2 rbytes=10 wbytes=20 wios=2\n')
                numeric = relay.build_report(collect(value, 'edo2'), 'numeric')
                io_stat.write_text('8:1 rbytes=invalid wbytes=200\n')
                malformed = relay.build_report(collect(value, 'edo2'), 'malformed')
                io_stat.write_text('8:1 rbytes=100 wbytes=200\n')
                membership.write_text('0::' + path.replace('a' * 64, 'b' * 64) + '\n')
                mismatch = relay.build_report(collect(value, 'edo2'), 'mismatch')
        self.assertIn('11.0B/22.0B · IO 기록 없음', empty)
        self.assertIn('미측정 0', empty)
        self.assertIn('11.0B/22.0B · IO 110.0B/220.0B', numeric)
        for report in (malformed, mismatch):
            self.assertIn('누적 블록 I/O', report)
            self.assertNotIn('누적 NET/IO', report)
            self.assertIn('11.0B/22.0B', report)
