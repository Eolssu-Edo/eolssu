import unittest

from test_relay import relay


class ContainerReadinessTests(unittest.TestCase):
    def render(self, cases, *, readiness_kinds=None):
        rows = []
        facts = []
        for name, docker_state, readiness in cases:
            rows.append([name, docker_state, '0.1%/미설정', '100B/미설정', '1B/미설정 · 로그 2B',
                         '없음', '1시간/0회', '0/아니오', '1/없음', '1B/1B · IO 1B/1B', 'image'])
            fact = {'name': name, 'ram_bytes': 100, 'host_ram': 1000,
                    'ram_limit': 0, 'cpu_percent': 0.1, 'readiness': readiness}
            if readiness_kinds and name in readiness_kinds:
                fact['readiness_kind'] = readiness_kinds[name]
            facts.append(fact)
        result = {'mode': 'containers', 'target': 'edo1', 'columns': ['열'] * 11, 'rows': rows, 'facts': facts}
        return relay.build_report(result, 'req').replace('\u2060', '').replace('\u00a0', ' ')

    def test_reports_common_verdicts_and_evidence_without_duplicate_unverified_findings(self):
        report = self.render([
            ('eolssu-monitoring-grafana-1', 'running/미설정', ['정상', 'HTTP 200 · database=ok', '4ms']),
            ('hey-edo1-n8n-1', 'running/미설정', ['실패', 'HTTP 503', '8ms']),
            ('eolssu-monitoring-node-exporter-1', 'running/미설정', ['주의', '최근 수집 95초 전', '3ms']),
            ('hey-edo1-tunnel-1', 'running/미설정', ['확인 불가', '응답을 읽지 못함', '—']),
            ('eolssu-runtime-ai-1', 'running/healthy', ['미검증', 'TCP 연결만 확인', '—']),
            ('legacy', 'running/미설정', None),
        ])
        self.assertIn('장애 1', report)
        self.assertIn('주의 1', report)
        self.assertIn('미측정 1', report)
        self.assertIn('미검증 2', report)
        self.assertIn('| 모니터링(Grafana) | ✅ **실행 · 검증 완료** · HTTP 200 · database=ok · 4ms |', report)
        self.assertIn('| 리뷰 워크플로(n8n) | ❌ **장애** · HTTP 503 · 8ms |', report)
        self.assertIn('| 서버 지표(node-exporter) | ⚠️ **실행 · 준비 상태 주의** · 최근 수집 95초 전 · 3ms |', report)
        self.assertIn('| 리뷰 봇 터널 | 🚫 **실행 · 준비 확인 불가** · 응답을 읽지 못함 |', report)
        self.assertIn('| 연주 분석 AI | ❓ **실행 · 준비 미검증** · TCP 연결만 확인 |', report)
        self.assertIn('| legacy | ❓ **실행 · 준비 미검증** |', report)

    def test_shared_success_cannot_hide_docker_failures_or_promote_a_result_without_evidence(self):
        positive = ['정상', '오래된 성공 근거', '2ms']
        report = self.render([
            ('stopped', 'exited/healthy', positive),
            ('unhealthy', 'running/unhealthy', positive),
            ('inspect-missing', 'running/확인 불가', positive),
            ('no-evidence', 'running/미설정', ['정상', '', '2ms']),
        ])
        self.assertIn('장애 2', report)
        self.assertIn('미측정 1', report)
        self.assertIn('미검증 1', report)
        self.assertNotIn('✅', report)
        self.assertNotIn('오래된 성공 근거', report)

    def test_ai_reports_http_communication_only_when_shared_check_passes(self):
        ai = 'eolssu-runtime-ai-1'
        evidence = 'POST /v1/analyses · HTTP 422 (입력 검증 응답)'
        positive = ['정상', evidence, '2ms']
        readiness_kinds = {ai: 'http_communication'}
        report = self.render([(ai, 'running/healthy', positive)], readiness_kinds=readiness_kinds)
        self.assertIn(f'| 연주 분석 AI | ✅ **실행 · 통신 정상** · {evidence} · 2ms |', report)
        self.assertIn('미검증 0', report.splitlines()[1])
        self.assertNotIn('검증 완료', report)
        self.assertNotIn('통신 정상', self.render([(ai, 'running/healthy', positive)]))

        for docker_state, readiness in (
            ('exited/healthy', positive),
            ('running/unhealthy', positive),
            ('running/healthy', ['실패', 'POST /v1/analyses HTTP 503', '2ms']),
        ):
            with self.subTest(docker_state=docker_state, readiness=readiness):
                report = self.render([(ai, docker_state, readiness)], readiness_kinds=readiness_kinds)
                self.assertIn('장애 1', report.splitlines()[1])
                self.assertIn('| 연주 분석 AI | ❌ **장애**', report)
                self.assertNotIn('통신 정상', report)
