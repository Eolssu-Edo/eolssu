import json
import subprocess
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock, patch

from test_relay import load, relay

collector = load('collector')
BLOG_LOGIN = 'eolssu-blog-editor'
BLOG_WRITABLE = 'eolssu-blog-editor-writable'
MYSQL_PROXY = 'eolssu-replication-proxy-579'
PHPMYADMIN = 'eolssu-mysql-admin-phpmyadmin-1'
AI = 'eolssu-runtime-ai-1'
TUNNEL = 'hey-edo1-tunnel-1'
GRAFANA = 'eolssu-monitoring-grafana-1'
N8N = 'hey-edo1-n8n-1'
NODE_EXPORTER = 'eolssu-monitoring-node-exporter-1'
ORIGIN = 'https://team-edo.github.io'


def container(name, *, binding=None, network=None, health=None):
    state = {'Status': 'running', 'Running': True, 'StartedAt': '2026-11-10T00:00:00Z'}
    if health:
        state['Health'] = {'Status': health}
    ports = {binding[0]: [{'HostIp': binding[1], 'HostPort': str(binding[2])}]} if binding else {}
    networks = {network[0]: {'IPAddress': network[1]}} if network else {}
    return {'item': {'Names': ['/' + name], 'State': 'running', 'SizeRw': 0},
            'detail': {'Name': '/' + name, 'State': state, 'HostConfig': {'LogConfig': {'Type': 'none'}, 'Memory': 1024},
                       'Config': {'Image': 'fixture'}, 'NetworkSettings': {'Ports': ports, 'Networks': networks}},
            'stats': {}}


class Edo1ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.containers = [
            container(BLOG_LOGIN, binding=('8000/tcp', '172.18.0.1', 18120)),
            container(BLOG_WRITABLE, binding=('8000/tcp', '172.18.0.1', 18121)),
            container(MYSQL_PROXY, binding=('3306/tcp', '127.0.0.1', 13306)),
            container(PHPMYADMIN, binding=('80/tcp', '127.0.0.1', 8081)),
            container(AI, network=('eolssu-runtime_default', '172.18.0.15'), health='healthy'),
            container(TUNNEL, network=('hey-edo1_control', '172.23.0.4')),
            container(GRAFANA, binding=('3000/tcp', '127.0.0.1', 3000)),
            container(N8N, binding=('5678/tcp', '127.0.0.1', 5688)),
        ]
        blog = {'authorize': 'https://github.com/login/oauth/authorize', 'clientId': 'hidden-client-id',
                'redirectUri': ORIGIN + '/edit/'}
        self.responses = {
            18120: (200, 'application/json', blog),
            18121: (200, 'application/json', {**blog, 'canBatch': True}),
            8081: (200, 'text/html; charset=utf-8',
                   b'<form id="login_form"><input name="pma_username"><input name="pma_password"></form>'),
            8000: (422, 'application/json', {'errors': [
                {'loc': ['body', key], 'type': 'missing'} for key in ('audioUrl', 'notes')]}),
            20241: (200, '', {'status': 200, 'readyConnections': 4}),
            3000: (200, 'application/json', {'database': 'ok'}),
            5688: (200, 'application/json', {'status': 'ok'}),
        }
        self.requests = {}
        self.ss = subprocess.CompletedProcess([], 0, 'LISTEN 0 4096 127.0.0.1:13306 0.0.0.0:*\n', '')

    def connection(self, host, port, timeout):
        status, content_type, payload = self.responses[port]
        if isinstance(payload, Exception):
            return Mock(request=Mock(side_effect=payload))
        if not isinstance(payload, bytes):
            payload = json.dumps(payload).encode()
        reply = Mock(status=status, read=Mock(return_value=payload))
        reply.getheader.side_effect = lambda key, default=None: {
            'Content-Type': content_type, 'Access-Control-Allow-Origin': ORIGIN}.get(key, default)
        client = Mock(getresponse=Mock(return_value=reply))
        self.requests[port] = client
        return client

    def collect(self, mode='health'):
        with patch.object(collector, 'snapshot', return_value=({}, self.containers, {}, [])), \
             patch.object(collector, 'required_containers', return_value=set()), \
             patch.object(collector, 'edo1_host_health', return_value=[]), \
             patch.object(collector, 'prometheus_health', return_value=[]), \
             patch.object(collector.http.client, 'HTTPConnection', side_effect=self.connection), \
             patch.object(collector, 'run', return_value=self.ss):
            result = collector.main(mode, 'edo1')
        self.assertFalse(result['issues'], result)
        return json.loads(json.dumps(result))

    def test_actual_routes_supply_same_evidence_in_health_and_container_facts(self):
        health = self.collect()
        detailed = self.collect('containers')
        rows = {row[0]: row for row in health['rows']}
        facts = {fact['name']: fact for fact in detailed['facts']}
        expected = {BLOG_LOGIN: '정상', BLOG_WRITABLE: '정상', MYSQL_PROXY: '정상',
                    PHPMYADMIN: '정상', AI: '정상', TUNNEL: '정상', GRAFANA: '정상', N8N: '정상'}
        for name, status in expected.items():
            with self.subTest(name=name):
                self.assertEqual(rows[name][2], status)
                self.assertEqual(rows[name][2:4], facts[name]['readiness'][:2])
        ai_evidence = 'POST /v1/analyses · HTTP 422 (입력 검증 응답)'
        self.assertEqual(rows[AI][3], ai_evidence)
        self.assertEqual(facts[AI]['readiness_kind'], 'http_communication')
        self.assertEqual(len(facts[AI]['readiness']), 3)
        self.assertEqual(facts[MYSQL_PROXY]['readiness_kind'], 'tcp_listener')
        self.assertIn('복제 통신은 /edo2-health', rows[MYSQL_PROXY][3])
        self.assertEqual(self.requests[8000].request.call_args.args[:2], ('POST', '/v1/analyses'))
        self.assertEqual(self.requests[8000].request.call_args.kwargs['body'], b'{}')
        self.assertEqual(self.requests[18120].request.call_args.kwargs['headers']['Origin'], ORIGIN)
        report = relay.build_report(health, 'fixture').replace('\u2060', '')
        self.assertEqual(report.count('**검증 완료**'), 6)
        self.assertIn('| eolssu-blog-editor-writable | ✅\u00a0**검증 완료** | GET /config HTTP 200', report)
        self.assertIn('| 모니터링(Grafana) | ✅\u00a0**검증 완료** | GET /api/health HTTP 200', report)
        self.assertIn('장애 0 · 주의 0', report)
        self.assertIn('미검증 0', report)
        self.assertIn('**로컬 수신 정상**', report)
        self.assertIn('| 연주 분석 AI | ✅\u00a0**통신 정상** | ' + ai_evidence, report)
        self.assertNotIn('**복제 통신 정상**', report)
        self.assertNotIn('hidden-client-id', report)

    def test_container_report_uses_the_node_scrape_without_rechecking_prometheus_readiness(self):
        self.containers = [container(NODE_EXPORTER)]
        active_target = {'labels': {'job': 'node'}, 'health': 'up',
                         'lastScrape': datetime.now(timezone.utc).isoformat()}
        targets = {'status': 'success', 'data': {'activeTargets': [active_target]}}
        for response, expected in ((targets, '정상'), (b'invalid json', '확인 불가')):
            with self.subTest(expected=expected):
                self.responses[3001] = (200, 'application/json', response)
                with patch.object(collector, 'snapshot', return_value=({}, self.containers, {}, [])), \
                     patch.object(collector.http.client, 'HTTPConnection', side_effect=self.connection) as http:
                    report = collector.main('containers', 'edo1')
                readiness = report['facts'][0]['readiness']
                self.assertIsNotNone(readiness)
                self.assertEqual(readiness[0], expected)
                self.assertIn('Prometheus', readiness[1])
                http.assert_called_once()
                self.requests[3001].request.assert_called_once_with('GET', '/api/v1/targets')

    def test_invalid_responses_fail_and_unavailable_addresses_remain_visible(self):
        self.responses[18120] = (200, 'text/html', b'<html>wrong app</html>')
        self.responses[18121] = (200, 'application/json', {'canBatch': False})
        self.responses[8081] = (200, 'text/html', b'<html>not the login form</html>')
        self.responses[8000] = (422, 'application/json', {'errors': []})
        self.responses[20241] = (503, '', {'status': 503, 'readyConnections': 0})
        self.responses[3000] = (200, 'application/json', {'database': 'error'})
        self.responses[5688] = (503, 'application/json', {'status': 'error'})
        self.ss = subprocess.CompletedProcess([], 0, '', '')
        health = self.collect()
        rows = {row[0]: row for row in health['rows']}
        report = relay.build_report(health, 'fixture').replace('\u2060', '')
        self.assertNotIn('검증 완료', report)
        self.assertNotIn('통신 정상', report)
        self.assertIn('**장애 8**', report)
        self.assertNotIn('로컬 수신 정상', report)
        for name in (BLOG_LOGIN, BLOG_WRITABLE, PHPMYADMIN, AI, TUNNEL, GRAFANA, N8N):
            self.assertEqual(rows[name][2], '실패', rows[name])
        self.assertEqual(rows[MYSQL_PROXY][2], '실패')
        self.assertIn('0', rows[TUNNEL][3])

    def test_docker_failure_and_wrong_route_do_not_send_probes(self):
        self.containers[0]['detail']['State'].update(Status='exited', Running=False)
        self.containers[1]['detail']['NetworkSettings']['Ports'] = {}
        self.containers[4]['detail']['State']['Health'] = {'Status': 'unhealthy'}
        self.containers[5]['detail']['NetworkSettings']['Networks']['hey-edo1_control']['IPAddress'] = '8.8.8.8'
        rows = {row[0]: row for row in self.collect()['rows']}
        self.assertEqual([rows[name][2] for name in (BLOG_LOGIN, BLOG_WRITABLE, AI, TUNNEL)],
                         ['실패', '확인 불가', '실패', '확인 불가'])
        self.assertFalse({18120, 18121, 8000, 20241} & self.requests.keys())

    def test_transport_failure_and_unreadable_body_are_distinguished(self):
        self.responses[18120] = (200, 'application/json', TimeoutError())
        self.responses[18121] = (200, 'application/json', PermissionError())
        self.responses[8081] = (200, 'text/html', b'x' * 32769)
        self.responses[20241] = (503, '', b'x' * 4097)
        rows = {row[0]: row for row in self.collect()['rows']}
        self.assertEqual([rows[name][2] for name in (BLOG_LOGIN, BLOG_WRITABLE, PHPMYADMIN, TUNNEL)],
                         ['실패', '확인 불가', '확인 불가', '실패'])

        self.responses[20241] = (200, '', ConnectionRefusedError())
        rows = {row[0]: row for row in self.collect()['rows']}
        self.assertEqual(rows[TUNNEL][2], '확인 불가')

    def test_ai_http_failure_and_docker_stop_cannot_report_communication_success(self):
        for reply, status in (
            ((500, 'application/json', {}), '실패'),
            ((422, 'application/json', TimeoutError()), '실패'),
            ((422, 'application/json', PermissionError()), '확인 불가'),
        ):
            with self.subTest(reply=reply):
                self.responses[8000] = reply
                health = self.collect()
                ai_row = next(row for row in health['rows'] if row[0] == AI)
                self.assertEqual(ai_row[2], status)
                self.assertNotIn('통신 정상', relay.build_report(health, 'fixture').replace('\u2060', ''))

        self.requests.clear()
        self.containers[4]['detail']['State'].update(Status='exited', Running=False)
        health = self.collect()
        ai_row = next(row for row in health['rows'] if row[0] == AI)
        self.assertEqual(ai_row[2], '실패')
        self.assertNotIn(8000, self.requests)
        self.assertNotIn('통신 정상', relay.build_report(health, 'fixture').replace('\u2060', ''))

    def test_proxy_socket_query_failure_and_container_failure_are_distinct(self):
        self.ss = subprocess.CompletedProcess([], 1, '', 'fixture error')
        rows = {row[0]: row for row in self.collect()['rows']}
        self.assertEqual(rows[MYSQL_PROXY][2], '확인 불가')

        proxy = next(item for item in self.containers if item['detail']['Name'] == '/' + MYSQL_PROXY)
        proxy['detail']['NetworkSettings']['Ports'] = {}
        rows = {row[0]: row for row in self.collect()['rows']}
        self.assertEqual(rows[MYSQL_PROXY][2], '실패')
        self.assertIn('포트 매핑', rows[MYSQL_PROXY][3])

        proxy['detail']['State'].update(Status='exited', Running=False)
        rows = {row[0]: row for row in self.collect()['rows']}
        self.assertEqual(rows[MYSQL_PROXY][2], '실패')
        self.assertIn('exited', rows[MYSQL_PROXY][3])
