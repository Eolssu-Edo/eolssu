import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import Mock, patch

from test_relay import load, relay
collector = load('collector')


class CurrentRuntimeTests(unittest.TestCase):
    def test_missing_ai_and_monitoring_are_detected_only_on_edo1(self):
        expected = {'eolssu-runtime-ai-1', 'eolssu-monitoring-grafana-1',
                    'eolssu-monitoring-prometheus-1', 'eolssu-monitoring-node-exporter-1'}
        self.assertTrue(expected <= collector.REQUIRED_BY_TARGET['edo1'])
        self.assertFalse(collector.REQUIRED_BY_TARGET['edo2'])

    def test_prometheus_checks_ready_and_each_required_scrape(self):
        now = datetime.now(timezone.utc)
        active = [{'labels': {'job': job}, 'health': 'up', 'lastScrape': now.isoformat()}
                  for job in ('node', 'eolssu-backend')]
        responses = [Mock(status=200, read=Mock(return_value=b'Prometheus Server is Ready.\n')),
                     Mock(status=200, read=Mock(return_value=json.dumps({'status':'success','data':{'activeTargets':active}}).encode()))]
        connection = Mock()
        connection.getresponse.side_effect = responses
        with patch.object(collector.http.client, 'HTTPConnection', return_value=connection):
            rows = collector.prometheus_health()
        self.assertTrue(all(row[2] == '정상' for row in rows))
        self.assertEqual(len(rows), 3)
        paths = [call.args[1] for call in connection.request.call_args_list]
        self.assertEqual(paths, ['/-/ready', '/api/v1/targets'])
        active[0]['lastScrape'] = (now - timedelta(minutes=5)).isoformat()
        self.assertEqual(collector.scrape_state(active, 'node')[0], '주의')
        active[0]['health'] = 'down'
        self.assertEqual(collector.scrape_state(active, 'node')[0], '실패')
        self.assertEqual(collector.scrape_state([], 'node')[0], '실패')
        active[0]['health'] = 'up'; active[0]['lastScrape'] = 'bad'
        self.assertEqual(collector.scrape_state(active, 'node')[0], '미측정')

    def test_prometheus_bad_response_never_passes(self):
        for code, body in ((503, b'not ready'), (200, b'<html>ok</html>'),
                           (200, b'{"status":"success","data":{}}')):
            connection = Mock()
            connection.getresponse.return_value = Mock(status=code, read=Mock(return_value=body))
            with self.subTest(code=code, body=body), patch.object(collector.http.client, 'HTTPConnection', return_value=connection):
                rows = collector.prometheus_health()
            self.assertFalse(any(row[2] == '정상' for row in rows))

    def test_backup_freshness_and_restore_failure_are_not_systemd_success(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); store = root/'store'; keys = root/'keys'; state = root/'state'
            state.mkdir()
            for kind in collector.BACKUP_KINDS:
                directory = keys/'controller'/kind if kind == 'controller-key' else store/kind
                directory.mkdir(parents=True)
                path = directory/'20261029T000000Z-aaaaaaaaaaaa.json'
                age = 2 if kind == 'db' else 1
                path.write_text(json.dumps({'snapshot_at': (datetime.now(timezone.utc)-timedelta(hours=age)).isoformat()}))
                path.with_suffix('.cms').write_bytes(b'encrypted-fixture')
            (state/'restore-last.json').write_text(json.dumps({'status':'failed','finished_at':datetime.now(timezone.utc).isoformat(),'cleanup_errors':[]}))
            rows = collector.backup_health(store, keys, state)
            self.assertEqual(rows[0][2], '주의')
            self.assertTrue(all(row[2] == '정상' for row in rows[1:5]))
            self.assertEqual(rows[-1][2], '실패')
            text = relay.build_report({'target':'edo2','mode':'health','columns':list('abcdef'),'rows':rows}, 'backup')
            self.assertIn('실패', text)
            self.assertNotIn('encrypted-fixture', text)
            self.assertIn('edo1 해당 백업', text)
            self.assertNotIn('/edo1-', text)

    def test_ai_tcp_success_is_not_analysis_readiness(self):
        data = {'item': {'Names':['/eolssu-runtime-ai-1'], 'State':'running'},
                'detail': {'State': {'Status':'running','Running':True,'Health':{'Status':'healthy'}}}}
        with patch.object(collector, 'snapshot', return_value=({},[data],{},[])), \
             patch.object(collector, 'edo1_host_health', return_value=[]), \
             patch.object(collector, 'prometheus_health', return_value=[]), \
             patch.object(collector.http.client, 'HTTPConnection', return_value=Mock(request=Mock(side_effect=OSError))):
            rows = collector.health_report()[1]
        row = next(row for row in rows if row[0] == 'eolssu-runtime-ai-1')
        self.assertEqual(row[2], '미검증')
        self.assertIn('TCP', row[3])
        self.assertIn('분석', row[3])
