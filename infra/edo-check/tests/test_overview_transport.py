import json
import unittest
from unittest.mock import Mock, call, patch

from test_relay import relay


class OverviewTransportTests(unittest.TestCase):
    def setUp(self):
        self.settings = {
            target: {'ssh_target': f'{target}-check@example.test',
                     'known_hosts': f'/test/{target}-hosts', 'identity_file': f'/test/{target}-key',
                     'service_host': f'{target}.example.test'}
            for target in ('edo1', 'edo2')
        }
        self.config = {'tokens': {'/edo1': 'one' * 10, '/edo2': 'two' * 10},
                       'targets': self.settings, 'channel_ids': ['cicd'], 'team_id': 'team',
                       'mattermost_origin': 'https://meeting.ssafy.com'}
        self.form = {'command': '/edo1', 'token': self.config['tokens']['/edo1'],
                     'channel_id': 'cicd', 'team_id': 'team', 'user_id': 'u', 'text': '',
                     'response_url': 'https://meeting.ssafy.com/hooks/commands/' + 'a' * 26}

    def test_config_and_authorization_bind_overview_to_each_host(self):
        relay.validate_config(self.config)
        for target in ('edo1', 'edo2'):
            command = '/' + target
            request = relay.authorize(dict(self.form, command=command, token=self.config['tokens'][command]),
                                      self.config)
            self.assertEqual((request['target'], request['mode']), (target, 'overview'))
        for invalid in ({'command': '/edo2'}, {'command': '/edo1-health'}, {'mode': 'health'},
                        {'target': 'edo2'}, {'text': 'server'}, {'channel_id': 'other'}, {'team_id': 'other'}):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                relay.authorize(dict(self.form, **invalid), self.config)

    def test_config_allows_moving_one_host_to_overview_without_token_reuse(self):
        tokens = {f'/edo2-{mode}': mode * 10 for mode in ('health', 'server', 'containers')}
        tokens.update({'/edo1': 'one' * 10, '/edo1-server': 'existing-server' * 2})
        relay.validate_config(dict(self.config, tokens=tokens))
        for invalid in (dict(tokens, **{'/edo1-server': tokens['/edo1']}),
                        {key: value for key, value in tokens.items() if key != '/edo2-health'}):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                relay.validate_config(dict(self.config, tokens=invalid))

    def test_active_host_rejects_overlap_and_releases_on_delivery_failure(self):
        self.config['tokens']['/edo1-server'] = 'legacy' * 5
        app = relay.App(self.config)
        app.pool.shutdown()
        app.pool = Mock()
        second = dict(self.form, response_url=self.form['response_url'].replace('a' * 26, 'b' * 26))
        with patch.object(relay.time, 'monotonic', return_value=1000) as now:
            self.assertIn('점검을 시작', app.submit(self.form))
            now.return_value = 1011
            self.assertIn('실행 중', app.submit(second))
            self.assertIn('실행 중', app.submit(dict(second, command='/edo1-server', token='legacy' * 5)))
            self.assertIn('점검을 시작', app.submit(dict(second, command='/edo2', token='two' * 10)))
            first_request = app.pool.submit.call_args_list[0].args[1]
            with patch.object(app, 'collect', side_effect=OSError), patch.object(relay, 'external_health', return_value=[]), \
                    patch.object(relay, 'build_overview', return_value=['보고서'], create=True), \
                    patch.object(relay, 'post_callback', side_effect=OSError), patch.object(relay.logging, 'error'):
                app.run(first_request, 'request')
            third = dict(self.form, response_url=self.form['response_url'].replace('a' * 26, 'c' * 26))
            self.assertIn('점검을 시작', app.submit(third))
        self.assertEqual(app.pool.submit.call_count, 3)

    def test_partial_collection_keeps_external_probe_and_posts_every_part_once(self):
        app = relay.App(self.config)
        app.pool.shutdown()
        self.assertTrue(app.slots.acquire(blocking=False))
        containers = {'target': 'edo2', 'mode': 'containers', 'columns': ['서비스'], 'rows': [['demo']]}
        foreign_health = {'target': 'edo1', 'mode': 'health', 'columns': ['대상', '위치', '판정', '근거'],
                          'rows': [['unexpected', 'Docker', '정상', 'healthy']]}
        external = ['중계 HTTPS', 'edo2 자체 HTTPS', '정상', 'HTTP 200', '2ms', '/healthz']
        posts = ['상태', '서버', '컨테이너']
        request = dict(self.form, target='edo2', mode='overview')
        with patch.object(relay.subprocess, 'run', side_effect=[
                Mock(stdout=json.dumps(foreign_health).encode()),
                relay.subprocess.TimeoutExpired('ssh', 90),
                Mock(stdout=json.dumps(containers).encode()),
        ]) as ssh, patch.object(relay, 'external_health', return_value=[external]) as probe, \
                patch.object(relay, 'build_overview', return_value=posts, create=True) as render, \
                patch.object(relay, 'post_callback', side_effect=[None, OSError(), None]) as callback, \
                patch.object(relay.logging, 'error'):
            app.run(request, 'request')
        probe.assert_called_once_with('edo2.example.test', target='edo2')
        commands = [invocation.args[0] for invocation in ssh.call_args_list]
        self.assertEqual([command[-1] for command in commands], ['health', 'server', 'containers'])
        self.assertTrue(all(command[-2] == 'edo2-check@example.test' for command in commands))
        self.assertTrue(all('/test/edo2-key' in command for command in commands))
        target, results, request_id, _, _ = render.call_args.args
        self.assertEqual((target, request_id), ('edo2', 'request'))
        self.assertEqual(results['containers'], containers)
        self.assertNotIn('server', results)
        self.assertEqual(results['health']['rows'][-1], external)
        self.assertEqual(results['health']['rows'][0][2], '미측정')
        self.assertEqual(callback.call_args_list, [call(request['response_url'], post) for post in posts])


if __name__ == '__main__':
    unittest.main()
