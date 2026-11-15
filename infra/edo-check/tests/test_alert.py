import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

from test_relay import load

alert = load('alert')
CRIT = ['API health', 'edo2 외부 HTTPS', '실패', 'HTTP 502', '—', '/api/actuator/health/readiness']
OK = ['API health', 'edo2 외부 HTTPS', '정상', 'HTTP 200 · JSON status=UP', '12ms', '/api/actuator/health/readiness']
KEY = 'edo1:external:API health:edo2 외부 HTTPS'


def external(rows):
    return {'edo1:external': (alert.table_signals('edo1:external', 'edo1', rows), True)}


class FakeRelay:
    """Stands in for relay.py: scripted rows per tick instead of SSH and HTTPS."""

    def __init__(self):
        self.external = [OK]
        self.health = {'edo1': [], 'edo2': []}
        self.server = {'edo1': [], 'edo2': []}
        self.fail = set()
        self.calls = []
        relay = self

        class App:
            def __init__(self, config):
                pass

            def collect(self, settings, target, mode):
                relay.calls.append(f'{target}:{mode}')
                if f'{target}:{mode}' in relay.fail:
                    raise TimeoutError()
                if mode == 'health':
                    return {'rows': relay.health[target]}
                return {'rows': [], 'issues': relay.server[target]}

        self.App = App

    def validate_config(self, config):
        pass

    def target_settings(self, config, target):
        return {'service_host': target + '.example'}

    def external_health(self, host, target='edo1'):
        self.calls.append(f'{target}:external')
        return self.external


class StateMachineTests(unittest.TestCase):
    def test_single_failure_is_not_alerted_and_two_in_a_row_is(self):
        state = {}
        self.assertEqual(alert.evaluate(state, external([CRIT]), 0), [])
        events = alert.evaluate(state, external([CRIT]), 60)
        self.assertEqual([(e['key'], e['previous'], e['level']) for e in events], [(KEY, 'ok', 'crit')])
        self.assertEqual(alert.evaluate(state, external([CRIT]), 120), [], 'no repeat while still failing')

    def test_blip_between_failures_resets_confirmation(self):
        state = {}
        for now, row in enumerate((CRIT, OK, CRIT)):
            self.assertEqual(alert.evaluate(state, external([row]), now * 60), [])

    def test_recovery_needs_two_ok_and_reports_outage_length(self):
        state = {}
        alert.evaluate(state, external([CRIT]), 0)
        alert.evaluate(state, external([CRIT]), 60)
        self.assertEqual(alert.evaluate(state, external([OK]), 600), [])
        events = alert.evaluate(state, external([OK]), 660)
        self.assertEqual([(e['previous'], e['level']) for e in events], [('crit', 'ok')])
        self.assertIn('장애 10분 뒤 복구', alert.render(events))

    def test_unknown_rows_neither_fail_nor_recover(self):
        state = {}
        alert.evaluate(state, external([CRIT]), 0)
        unknown = [CRIT[:2] + ['미측정', '확인 불가', '—', '—']]
        alert.evaluate(state, external(unknown), 60)
        self.assertEqual(len(alert.evaluate(state, external([CRIT]), 120)), 1, 'unknown did not reset the streak')

    def test_warning_escalates_to_failure(self):
        state = {}
        warn = CRIT[:2] + ['주의', '만료까지 3.0일', '—', 'host']
        for now in (0, 60):
            events = alert.evaluate(state, external([warn]), now)
        self.assertEqual(events[0]['level'], 'warn')
        alert.evaluate(state, external([CRIT]), 120)
        events = alert.evaluate(state, external([CRIT]), 180)
        self.assertEqual([(e['previous'], e['level']) for e in events], [('warn', 'crit')])
        self.assertIn('주의에서 변경', alert.render(events))

    def test_row_missing_from_complete_source_counts_as_recovered(self):
        state = {}
        disk = alert.server_signals('edo1:server', 'edo1', ['/ 공간 91%·inode 2% (주의·긴급 용량 점검)'])
        for now in (0, 60):
            events = alert.evaluate(state, {'edo1:server': (disk, True)}, now)
        self.assertEqual(events[0]['level'], 'warn')
        for now in (120, 180):
            events = alert.evaluate(state, {'edo1:server': ([], True)}, now)
        self.assertEqual([(e['label'], e['level']) for e in events], [('edo1 디스크 /', 'ok')])
        self.assertEqual(state['keys'], {}, 'recovered vanished keys are forgotten')

    def test_failed_collection_alerts_without_faking_recovery(self):
        state = {}
        row = ['MySQL 복제 감시', 'edo2 systemd', '실패', '복제 감시 실패', '—', 'journalctl']
        for now in (0, 60):
            alert.evaluate(state, {'edo2:health': (alert.table_signals('edo2:health', 'edo2', [row]), True)}, now)
        failed = [alert.signal('edo2:health:collect', 'edo2 상태 점검 수집', 'crit', 'SSH 실패', '확인')]
        alert.evaluate(state, {'edo2:health': (failed, False)}, 120)
        events = alert.evaluate(state, {'edo2:health': (failed, False)}, 180)
        self.assertEqual([e['key'] for e in events], ['edo2:health:collect'])
        self.assertEqual(state['keys']['edo2:health:MySQL 복제 감시:edo2 systemd']['alerted'], 'crit')


class SignalTests(unittest.TestCase):
    def test_disk_thresholds_and_failed_units(self):
        signals = alert.server_signals('edo2:server', 'edo2', [
            '/ 공간 84%·inode 1% (주의)', '/data 공간 85%·inode 1% (주의)',
            '/var 공간 70%·inode 96% (주의·긴급 용량 점검)', '실패 서비스 2개', '서버 RAM 사용률 80% 이상 (주의)'])
        self.assertEqual([(s['label'], s['level']) for s in signals],
                         [('edo2 디스크 /', 'ok'), ('edo2 디스크 /data', 'warn'),
                          ('edo2 디스크 /var', 'crit'), ('edo2 systemd 실패 서비스', 'warn')])

    def test_collector_status_mapping(self):
        rows = [[name, 'loc', status, 'e', '—', 'a'] for name, status in
                (('a', '실패'), ('b', '주의'), ('c', '정상'), ('d', '미검증'), ('e', '확인 불가'))]
        self.assertEqual([s['level'] for s in alert.table_signals('edo1:health', 'edo1', rows)],
                         ['crit', 'warn', 'ok', None, None])


class RenderTests(unittest.TestCase):
    def event(self, level='crit', previous='ok', **change):
        base = dict(key='k', label='edo1 API health', level=level, previous=previous,
                    evidence='HTTP 502', action='확인', started=0, at=60)
        return dict(base, **change)

    def test_masks_urls_emails_mentions_and_table_breakers(self):
        text = alert.render([self.event(evidence='https://x.test/hooks/abc a@b.com @all | x\ny `z`')])
        self.assertNotIn('hooks/abc', text)
        self.assertNotIn('a@b.com', text)
        self.assertIn('@​all', text)
        self.assertIn('\\| x y', text)
        self.assertNotIn('`z`', text)

    def test_times_do_not_become_emoji_shortcodes(self):
        text = alert.render([self.event('ok', 'crit', evidence='active/waiting · 다음 Sun 2026-11-15 07:00:00 UTC :smile:')])
        self.assertNotIn(':00:', text)
        self.assertNotIn(':smile:', text)
        self.assertIn('07:\u206000:00', text)

    def test_mention_only_for_new_failure(self):
        self.assertTrue(alert.render([self.event()], '@here').endswith('@here'))
        self.assertNotIn('@here', alert.render([self.event('ok', 'crit')], '@here'))
        self.assertNotIn('@here', alert.render([self.event('warn')], '@here'))

    def test_row_cap_and_heading(self):
        events = [self.event(label=f'edo1 대상 {i}') for i in range(25)] + [self.event('ok', 'warn')]
        text = alert.render(events)
        self.assertTrue(text.startswith('### ❌ 운영 감시 · 장애 25 · 복구 1'))
        self.assertIn('나머지 6건', text)

    def test_webhook_destination_is_pinned(self):
        self.assertEqual(alert.webhook_destination('https://meeting.ssafy.com/hooks/abc123'),
                         ('meeting.ssafy.com', '/hooks/abc123'))
        for url in ('http://meeting.ssafy.com/hooks/abc', 'https://meeting.ssafy.com.evil/hooks/abc',
                    'https://u@meeting.ssafy.com/hooks/abc', 'https://meeting.ssafy.com/hooks/abc?x=1',
                    'https://meeting.ssafy.com/hooks/commands/abc', 'https://meeting.ssafy.com:8443/hooks/abc'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                alert.webhook_destination(url)


class SendTests(unittest.TestCase):
    URL = 'https://meeting.ssafy.com/hooks/abc123'

    def relay(self, *, connect=None, status=200, request=None):
        class Connection:
            def __init__(self, host, port, **kwargs):
                self.host = host

            def connect(self):
                if connect:
                    raise connect

            def request(self, method, path, body, headers):
                Connection.sent = (method, path, json.loads(body))
                if request:
                    raise request

            def getresponse(self):
                return types.SimpleNamespace(status=status, read=lambda size: b'ok')

            def close(self):
                pass
        return types.SimpleNamespace(PublicHTTPS=Connection), Connection

    def test_posts_json_with_optional_channel(self):
        relay, connection = self.relay()
        alert.send(relay, self.URL, '본문', 'e102-alerts')
        self.assertEqual(connection.sent, ('POST', '/hooks/abc123',
                                           {'username': 'edo 감시', 'text': '본문', 'channel': 'e102-alerts'}))

    def test_classifies_certain_and_ambiguous_failures(self):
        for kwargs, expected in (({'connect': OSError()}, alert.NotDelivered),
                                 ({'status': 400}, alert.NotDelivered),
                                 ({'request': TimeoutError()}, TimeoutError)):
            relay, _ = self.relay(**kwargs)
            with self.subTest(kwargs=kwargs), self.assertRaises(expected):
                alert.send(relay, self.URL, 'x')


class RunTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.state_dir = Path(self.directory.name)
        self.relay = FakeRelay()
        self.sent = []
        self.patch = patch.object(alert, 'load_relay', return_value=self.relay)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.directory.cleanup()

    def tick(self, now, sender=None, **kwargs):
        self.relay.calls.clear()
        return alert.run({}, self.state_dir, sender or self.sent.append, now, **kwargs)

    def state(self):
        return json.loads((self.state_dir / 'state.json').read_text())

    def test_schedule_runs_slow_sources_only_when_due_or_suspected(self):
        self.tick(0)
        self.assertEqual(sorted(self.relay.calls),
                         ['edo1:external', 'edo1:health', 'edo1:server', 'edo2:health', 'edo2:server'])
        self.tick(60)
        self.assertEqual(self.relay.calls, ['edo1:external'])
        self.relay.health['edo2'] = [['MySQL 복제 감시', 'edo2 systemd', '실패', '복제 감시 실패', '—', 'x']]
        self.tick(300)
        self.assertIn('edo2:health', self.relay.calls)
        self.tick(360)
        self.assertIn('edo2:health', self.relay.calls, 'suspected change is re-checked on the next tick')
        self.assertEqual(len(self.sent), 1)
        self.assertIn('MySQL 복제 감시', self.sent[0])
        self.tick(420)
        self.assertNotIn('edo2:health', self.relay.calls)

    def test_flapping_value_gets_one_early_recheck_per_scheduled_run(self):
        self.tick(0)
        row = ['node 지표 수집', 'edo1 모니터링 HTTP', '주의', 'up · 최근 수집 120초 전', '—', 'x']
        self.relay.health['edo1'] = [row]
        self.tick(1800)
        self.relay.health['edo1'] = [row[:2] + ['실패'] + row[3:]]
        self.tick(1860)
        self.assertIn('edo1:health', self.relay.calls, 'one early re-check')
        self.relay.health['edo1'] = [row]
        self.tick(1920)
        self.assertNotIn('edo1:health', self.relay.calls, 'R2-probing collection is not repeated every minute')
        self.tick(3600)
        self.assertIn('edo1:health', self.relay.calls)

    def test_ssh_failure_is_its_own_alert(self):
        self.relay.fail.add('edo1:server')
        self.tick(0)
        self.tick(60)
        self.assertEqual(len(self.sent), 1)
        self.assertIn('edo1 서버 점검 수집', self.sent[0])

    def test_certain_non_delivery_is_retried_but_uncertain_is_not(self):
        self.relay.external = [CRIT]
        self.tick(0)

        def refuse(text):
            raise alert.NotDelivered('connect')
        result = self.tick(60, refuse)
        self.assertEqual(result['delivery'], 'failed')
        self.assertEqual(self.state()['keys'][KEY]['alerted'], 'ok')
        result = self.tick(120)
        self.assertEqual((result['delivery'], len(self.sent)), ('sent', 1))
        self.relay.external = [OK]
        self.tick(180)

        def ambiguous(text):
            raise TimeoutError()
        self.assertEqual(self.tick(240, ambiguous)['delivery'], 'uncertain')
        self.assertEqual(self.state()['keys'][KEY]['alerted'], 'ok')
        self.relay.external = [OK]
        self.assertEqual(self.tick(300)['events'], 0, 'ambiguous recovery post is not repeated')

    def test_dry_run_neither_sends_nor_writes_state(self):
        self.relay.external = [CRIT]
        result = self.tick(0, dry_run=True)
        self.assertEqual([s['key'] for s in result['non_ok']], [KEY])
        self.assertFalse((self.state_dir / 'state.json').exists())
        self.assertEqual(self.sent, [])


class UnitFileTests(unittest.TestCase):
    def test_service_runs_as_relay_user_with_credential_and_bounded_runtime(self):
        unit = (Path(__file__).parents[1] / 'edo-check-alert.service').read_text()
        for line in ('User=edo-check', 'LoadCredential=webhook-url:/etc/edo-check-alert/webhook-url',
                     'ExecStart=/usr/bin/python3 -I -B /opt/edo-check/alert.py /etc/edo-check/relay.json',
                     'StateDirectory=edo-check-alert', 'NoNewPrivileges=yes', 'TimeoutStartSec=420'):
            self.assertIn(line, unit)
        timer = (Path(__file__).parents[1] / 'edo-check-alert.timer').read_text()
        self.assertIn('OnUnitInactiveSec=1min', timer)


if __name__ == '__main__':
    unittest.main()
