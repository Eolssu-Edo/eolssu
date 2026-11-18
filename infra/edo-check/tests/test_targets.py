import json
import subprocess
import unittest
from unittest.mock import MagicMock, Mock, patch

from test_relay import relay, load

collector = load('collector')


class TargetTests(unittest.TestCase):
    def test_runtime_image_column_keeps_tags_and_compacts_digests_without_changing_source(self):
        digest = 'a660310e39ade10631f774bacd5219de767dfd08947ea5c32a739b5e5bb382c1'
        cases = [
            ('jenkins/jenkins:2.580.1-jdk21@sha256:' + digest, 'jenkins/jenkins:2.580.1-jdk21'),
            ('registry:5000/team/demo:v1@sha256:' + digest, 'registry:5000/team/demo:v1'),
            ('registry:5000/team/demo@sha256:' + digest, 'registry:5000/team/demo@sha256:a660310e39ad…'),
            ('sha256:' + digest, 'sha256:a660310e39ad…'),
            ('nginx:alpine', 'nginx:alpine'),
        ]
        for target in ('edo1', 'edo2'):
            for original, expected in cases:
                with self.subTest(target=target, image=original):
                    report = {'target':target, 'mode':'containers', 'columns':list(range(11)),
                              'rows':[['eolssu-jenkins-controller-1','running/healthy','0%/미설정',
                                       '100B/미설정','1B/미설정 · 로그 2B','없음','1시간/0회',
                                       '0/아니오','1/없음','1B/1B · IO 1B/1B',original]],
                              'facts':[{'name':'eolssu-jenkins-controller-1','ram_bytes':100,
                                        'host_ram':1000,'ram_limit':0,'cpu_percent':0}]}
                    text = relay.build_report(report, 'image-test').replace('\u200b', '')
                    runtime = text.split('**실행 세부 정보**')[1]
                    self.assertIn(' | ' + expected + ' |', runtime)
                    self.assertNotIn(digest, runtime)
                    self.assertEqual(report['rows'][0][10], original)

    def test_command_token_binds_target_and_rejects_client_target_override(self):
        config = {'tokens': {'/edo1-server': 'one', '/edo2-server': 'two'},
                  'channel_ids': ['cicd'], 'mattermost_origin': 'https://meeting.ssafy.com'}
        form = {'command': '/edo2-server', 'token': 'two', 'channel_id': 'cicd', 'user_id': 'u',
                'text': '', 'response_url': 'https://meeting.ssafy.com/hooks/commands/'+'a'*26}
        request = relay.authorize(form, config)
        self.assertEqual((request['target'], request['mode']), ('edo2', 'server'))
        for change in ({'token':'one'}, {'target':'edo1'}, {'mode':'health'}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                relay.authorize(dict(form, **change), config)

    def test_edo2_snapshot_never_requires_edo1_containers(self):
        def docker(path, **kwargs):
            return [] if path.startswith('/containers/json') else {}
        with patch.object(collector, 'docker', side_effect=docker):
            self.assertEqual(collector.snapshot(target='edo2')[3], [])
            issues = collector.snapshot(target='edo1')[3]
            self.assertEqual(len(issues), 8)
            self.assertIn('eolssu-replication-proxy-579 필수 컨테이너 누락', issues)

    def test_empty_container_report_identifies_edo2_without_fabricating_measurements(self):
        report = {'target':'edo2','mode':'containers','columns':[str(i) for i in range(11)],'rows':[], 'facts':[]}
        text = relay.build_report(report, 'req')
        self.assertEqual(text.splitlines()[0], '### edo2 컨테이너')
        self.assertIn('등록된 컨테이너 0개', text)
        self.assertNotIn('/edo1-', text)

    def test_container_mount_name_cannot_replace_the_host_in_title_or_commands(self):
        for target in ('edo1', 'edo2'):
            for mount in ('없음', 'bind /data: 10B'):
                with self.subTest(target=target, mount=mount):
                    report = {'target':target,'mode':'containers','columns':[str(i) for i in range(11)],
                              'rows':[['demo','running/healthy','0.1%/미설정','100B/미설정','1B/미설정 · 로그 2B',
                                       mount,'1시간/0회','0/아니오','1/없음','1B/1B · IO 1B/1B','image']],
                              'facts':[{'name':'demo','ram_bytes':100,'host_ram':1000,'ram_limit':0,'cpu_percent':0.1}]}
                    text = relay.build_report(report, 'req')
                    self.assertEqual(text.splitlines()[0], f'### {target} 컨테이너')
                    self.assertIn(f'/{target}-health', text)

    def test_edo2_https_checks_relay_json_and_not_application_api(self):
        connection = Mock()
        connection.getresponse.return_value = Mock(status=200, read=Mock(return_value=b'{"status":"ok"}'))
        with patch.object(relay.http.client, 'HTTPSConnection', return_value=connection), \
                patch.object(relay.socket, 'create_connection', side_effect=OSError):
            rows = relay.external_health('15.165.144.7', target='edo2')
        connection.request.assert_called_once_with('GET', '/healthz')
        self.assertEqual(rows[0][2], '정상')
        self.assertIn('자체 HTTPS', rows[0][1])

    def test_oneshot_inactive_success_and_no_history_are_different(self):
        base = 'LoadState=loaded\nActiveState=inactive\nSubState=dead\nResult=success\nExecMainStatus=0\n'
        for started, expected in (('Mon 2026-10-26 08:30:00 UTC', '정상'), ('', '미검증')):
            response = subprocess.CompletedProcess([], 0, base+'ExecMainStartTimestamp='+started+'\n', '')
            with self.subTest(started=started), patch.object(collector, 'run', return_value=response):
                self.assertEqual(collector.systemd_health('example.service', 'oneshot')[0], expected)
        with patch.object(collector, 'run', return_value=response):
            self.assertEqual(collector.systemd_health('example.service', 'service')[0], '실패')

    def test_expected_docker_failure_is_never_an_empty_container_success(self):
        with patch.object(collector, 'snapshot', side_effect=OSError('unavailable')):
            report = collector.main('containers', 'edo2')
        text = relay.build_report(report, 'req')
        self.assertIn('미측정', text)
        self.assertNotIn('등록된 컨테이너 0개', text)

    def test_systemd_times_remain_readable_in_mattermost_tables(self):
        report = {'target':'edo2', 'mode':'health', 'columns':list('abcdef'),
                  'rows':[['백업 상태', 'edo2 systemd', '정상',
                           '최근 시작 Mon 2026-10-26 09:00:00 UTC · 결과 success · 종료 0', '—', '—'],
                          ['갱신 예약', 'edo2 systemd', '정상',
                           'active/waiting · 다음 Mon 2026-10-26 20:51:00 UTC', '—', '—']]}
        text = relay.build_report(report, 'req')
        self.assertIn('`09:00:00`', text)
        self.assertIn('`20:51:00`', text)

    def test_short_lived_ip_certificate_has_a_different_warning_threshold(self):
        connection = Mock()
        connection.getresponse.return_value = Mock(status=200, read=Mock(return_value=b'{"status":"ok"}'))
        raw, secured, context = MagicMock(), MagicMock(), Mock()
        secured.__enter__.return_value.getpeercert.return_value = {'notAfter':'fixture'}
        context.wrap_socket.return_value = secured
        with patch.object(relay.http.client, 'HTTPSConnection', return_value=connection), \
                patch.object(relay.socket, 'create_connection', return_value=raw), \
                patch.object(relay.ssl, 'create_default_context', return_value=context), \
                patch.object(relay.ssl, 'cert_time_to_seconds', return_value=relay.time.time()+2*86400):
            self.assertEqual(relay.external_health('15.165.144.7', 'edo2')[-1][2], '정상')
            self.assertEqual(relay.external_health('service.example', 'edo1')[-1][2], '주의')

    def test_wrong_target_result_is_rejected_and_uses_only_target_ssh_settings(self):
        settings = {'ssh_target':'edo2-check@127.0.0.1','known_hosts':'/test/edo2-hosts','identity_file':'/test/edo2-key'}
        app = relay.App({'targets': {'edo2':settings}})
        app.pool.shutdown()
        app.slots.acquire()
        response = {'target':'edo1','mode':'server','columns':['대상','값'],'rows':[['wrong','value']]}
        with patch.object(relay.subprocess, 'run', return_value=Mock(stdout=json.dumps(response).encode())) as ssh, \
                patch.object(relay, 'post_callback') as callback, patch.object(relay.logging, 'error'):
            app.run({'target':'edo2','mode':'server','response_url':'https://meeting.ssafy.com/hooks/commands/'+'a'*26}, 'mismatch')
        self.assertIn('edo2-check@127.0.0.1', ssh.call_args.args[0])
        self.assertIn('/test/edo2-key', ssh.call_args.args[0])
        self.assertIn('edo2 점검 실패', callback.call_args.args[1])
        self.assertNotIn('wrong', callback.call_args.args[1])

    def test_rate_limit_separates_targets_and_keeps_same_target_throttled(self):
        app = relay.App({'tokens':{'/edo1-server':'one','/edo2-server':'two'},
                         'channel_ids':['cicd'],'mattermost_origin':'https://meeting.ssafy.com'})
        app.pool.shutdown()
        app.pool = Mock()
        form = {'command':'/edo1-server','token':'one','channel_id':'cicd','user_id':'u','text':'',
                'response_url':'https://meeting.ssafy.com/hooks/commands/'+'a'*26}
        self.assertIn('edo1', app.submit(form))
        self.assertIn('edo2', app.submit(dict(form, command='/edo2-server', token='two',
                                            response_url='https://meeting.ssafy.com/hooks/commands/'+'b'*26)))
        self.assertIn('10초', app.submit(dict(form,response_url='https://meeting.ssafy.com/hooks/commands/'+'c'*26)))
        self.assertEqual(app.pool.submit.call_count, 2)

    def test_startup_accepts_legacy_group_and_rejects_partial_or_reused_tokens(self):
        settings = {'ssh_target':'test@example','identity_file':'/test/key','known_hosts':'/test/hosts','service_host':'example.com'}
        config = dict(settings, tokens={f'/edo1-{mode}':mode*20 for mode in ('server','containers','health')})
        relay.validate_config(config)
        config['targets'] = {'edo1':settings,'edo2':settings}
        config['tokens'].update({f'/edo2-{mode}':('two'+mode)*20 for mode in ('server','containers','health')})
        relay.validate_config(config)
        for changed in (dict(config,tokens={**config['tokens'],'/edo2-health':config['tokens']['/edo1-health']}),
                        dict(config,tokens={key:value for key,value in config['tokens'].items() if key!='/edo2-health'}),
                        dict(config,targets={'edo1':settings})):
            with self.assertRaises(ValueError):
                relay.validate_config(changed)


if __name__ == '__main__':
    unittest.main()
