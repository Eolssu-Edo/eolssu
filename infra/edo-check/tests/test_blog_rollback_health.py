import unittest
from unittest.mock import Mock, patch

from test_relay import load, relay

collector = load('collector')
EDITOR = 'eolssu-blog-editor-writable'


def exited_container(name, **state_overrides):
    return {
        'item': {'Names': ['/' + name], 'State': 'exited'},
        'detail': {
            'Name': '/' + name,
            'State': {'Status': 'exited', 'Running': False, 'ExitCode': 0,
                      'OOMKilled': False, 'Error': '', **state_overrides},
        },
    }


class BlogRollbackHealthTests(unittest.TestCase):
    def health_report(self, containers, target='edo1'):
        connection = Mock()
        connection.getresponse.return_value = Mock(
            status=200, read=Mock(return_value=b'{"database":"ok","status":"ok"}'))
        with patch.object(collector, 'snapshot', return_value=({}, containers, {}, [])), \
             patch.object(collector, 'required_containers', return_value=set()), \
             patch.object(collector, 'edo1_host_health', return_value=[]), \
             patch.object(collector, 'prometheus_health', return_value=[]), \
             patch.object(collector, 'edo2_host_health', return_value=[]), \
             patch.object(collector, 'backup_health', return_value=[]), \
             patch.object(collector.http.client, 'HTTPConnection', return_value=connection):
            return collector.health_report(target)

    def test_edo1_report_excludes_rollback_copies_but_reports_live_editor_stopped(self):
        backups = [f'{EDITOR}-{suffix}' for suffix in
                   ('before-504fa817', 'rollback-a542ceaf', 'rollback-b134848e', 'pre-317108c7')]
        containers = [exited_container(name) for name in [*backups, EDITOR]]
        columns, rows, issues, facts = self.health_report(containers)
        report = relay.build_report({'target': 'edo1', 'mode': 'health', 'columns': columns,
                                     'rows': rows, 'issues': issues, 'facts': facts}, 'fixture')
        self.assertIn('**장애 1**', report)
        self.assertIn(f'{EDITOR}: 중지·일시정지: exited', report)
        for name in backups:
            self.assertNotIn(name, report)

    def test_abnormal_or_unconfirmed_stops_remain_visible(self):
        cases = [
            ('edo1', exited_container(f'{EDITOR}-rollback-deadbeef', ExitCode=1), '실패'),
            ('edo1', exited_container(f'{EDITOR}-rollback-deadbeef', OOMKilled=True), '실패'),
            ('edo1', exited_container(f'{EDITOR}-rollback-deadbeef', Error='runtime error'), '실패'),
            ('edo1', exited_container(f'{EDITOR}-rollback-deadbeef', Status='paused'), '실패'),
            ('edo1', exited_container(f'{EDITOR}-rollback-deadbeef', Status='running', Running=True), '미검증'),
            ('edo1', exited_container(f'{EDITOR}-rollback-deadbeef-other'), '실패'),
            ('edo2', exited_container(f'{EDITOR}-rollback-deadbeef'), '실패'),
        ]
        unavailable = exited_container(f'{EDITOR}-rollback-deadbeef')
        unavailable.update(detail={}, unavailable=True)
        cases.append(('edo1', unavailable, '확인 불가'))
        for target, container, expected in cases:
            name = container['item']['Names'][0].lstrip('/')
            with self.subTest(target=target, name=name, detail=container['detail']):
                _, rows, _, _ = self.health_report([container], target)
                self.assertEqual(next(row for row in rows if row[0] == name)[2], expected)
