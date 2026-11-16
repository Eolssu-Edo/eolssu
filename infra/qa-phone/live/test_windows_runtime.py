"""Device isolation checks without starting Docker or contacting Android."""
from pathlib import Path
import threading
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from live_observer import Observer
from windows_runtime import Runtime, Observation
from scenarios import ScenarioFailure


class DeviceBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.config = {'runId': 'test936', 'identity': 'PHONE936', 'serial': '192.0.2.1:40000', 'adb': 'adb',
                       'devices': {'PHONE936': {'serial': 'PHONE936', 'device': {'name': 'phone'}},
                                   'TABLET936': {'serial': 'TABLET936', 'device': {'name': 'tablet'}}}}
        self.runtime = Runtime(self.config, Path(self.temporary.name) / 'runtime')

    def test_adb_selects_registered_identity_instead_of_first_connected_device(self):
        def adb(args, **_kwargs):
            if args[-1] == 'devices':
                return 'List of devices attached\nTABLET936\tdevice\nPHONE936\tdevice\n'
            return args[args.index('-s') + 1]
        with patch('windows_runtime.execute', side_effect=adb):
            self.assertEqual(self.runtime.device_serial(), 'PHONE936')

    def test_two_registered_devices_start_with_independent_owners_and_proxy_ports(self):
        self.runtime.state = 'ready'
        with patch('windows_runtime.threading.Thread'):
            self.runtime.command({'action': 'start', 'sessionId': 'b' * 36}, 'PHONE936')
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'TABLET936')
        phone, tablet = self.runtime.observations['PHONE936'], self.runtime.observations['TABLET936']
        self.assertNotEqual(phone.config['proxyPort'], tablet.config['proxyPort'])
        self.assertEqual((phone.controller, tablet.controller), ('b' * 36, 'a' * 36))
        self.assertEqual((phone.config['identity'], tablet.config['identity']), ('PHONE936', 'TABLET936'))
        self.assertEqual(self.config['identity'], 'PHONE936')
        with self.assertRaises(PermissionError):
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'UNKNOWN936')

    def test_stopping_one_observer_keeps_the_other_and_server_checks_exclusive(self):
        self.runtime.state = 'ready'
        with patch('windows_runtime.threading.Thread'):
            for identity, caller in [('PHONE936', 'a'), ('TABLET936', 'b')]:
                self.runtime.command({'action': 'start', 'sessionId': caller * 36}, identity)
                self.runtime.observations[identity].state = 'observing'
        with self.assertRaises(PermissionError):
            self.runtime.command({'action': 'stop', 'sessionId': 'b' * 36}, 'PHONE936')
        self.runtime.command({'action': 'stop', 'sessionId': 'a' * 36}, 'PHONE936')
        self.assertEqual(self.runtime.mode, 'observe')
        self.assertEqual(self.runtime.observations['TABLET936'].state, 'observing')
        with self.assertRaises(BlockingIOError):
            self.runtime.command({'action': 'scenario', 'scenario': 'feedback', 'sessionId': 'b' * 36}, 'TABLET936')

    def test_observer_events_and_history_stay_with_their_device(self):
        self.runtime.state = 'ready'
        with patch('windows_runtime.threading.Thread'):
            for identity, caller in [('PHONE936', 'a'), ('TABLET936', 'b')]:
                self.runtime.command({'action': 'start', 'sessionId': caller * 36}, identity)
                context = self.runtime.observations[identity]
                context.observer = SimpleNamespace(status=lambda identity=identity: {'events': [{'requestId': identity}]})
                context.state = 'observing'
        self.runtime.history = [{'id': 'phone-run', 'deviceIdentity': 'PHONE936'}, {'id': 'tablet-run', 'deviceIdentity': 'TABLET936'}]
        self.assertEqual(self.runtime.status('PHONE936')['events'], [{'requestId': 'PHONE936'}])
        self.assertEqual(self.runtime.status('TABLET936')['events'], [{'requestId': 'TABLET936'}])
        self.assertEqual(self.runtime.status('TABLET936')['history'], [{'id': 'tablet-run', 'deviceIdentity': 'TABLET936'}])

    def test_device_start_cannot_overlap_a_server_run(self):
        self.runtime.state, self.runtime.mode = 'running', 'run'
        with self.assertRaises(BlockingIOError):
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'PHONE936')
        self.assertFalse(self.runtime.observations)

    def test_app_launch_reserves_the_shared_boundary_until_the_device_command_finishes(self):
        self.runtime.state = 'ready'
        entered, release = threading.Event(), threading.Event()
        def adb(*args, **_kwargs):
            if 'dumpsys' in args:
                return 'versionCode=1'
            entered.set()
            release.wait(2)
            return 'Starting: Intent'
        results = []
        with patch.object(Observation, 'adb', side_effect=adb):
            worker = threading.Thread(target=lambda: results.append(self.runtime.command({'action': 'launch', 'sessionId': 'a' * 36}, 'PHONE936')))
            worker.start()
            try:
                self.assertTrue(entered.wait(1))
                with self.assertRaises(BlockingIOError):
                    self.runtime.command({'action': 'scenario', 'scenario': 'feedback', 'sessionId': 'b' * 36}, 'TABLET936')
                self.assertEqual(self.runtime.status()['mode'], 'launch')
            finally:
                release.set()
                worker.join(2)
        self.assertEqual(results, [{'started': True}])
        self.assertFalse(self.runtime.launches)

    def test_late_stop_or_observer_finally_cannot_erase_a_running_server_check(self):
        self.runtime.state = 'ready'
        with patch('windows_runtime.threading.Thread'):
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'PHONE936')
            context = self.runtime.observations['PHONE936']
            context.state = 'observing'
            self.runtime.command({'action': 'stop', 'sessionId': 'a' * 36}, 'PHONE936')
            self.runtime.command({'action': 'scenario', 'scenario': 'feedback', 'sessionId': 'a' * 36}, 'PHONE936')
            self.runtime.command({'action': 'stop', 'sessionId': 'a' * 36}, 'PHONE936')
            self.runtime.sync_observations()
        self.assertEqual((self.runtime.mode, self.runtime.state), ('scenario', 'running'))
        with self.assertRaises(BlockingIOError):
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'TABLET936')

    def test_unstarted_default_device_never_reports_another_devices_observation(self):
        self.runtime.state = 'ready'
        with patch('windows_runtime.threading.Thread'):
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'TABLET936')
        actual = self.runtime.status('PHONE936')
        self.assertFalse(actual['observingSelected'])
        self.assertEqual(actual['state'], 'ready')
        self.assertEqual(actual['events'], [])

    def test_stopping_startup_keeps_environment_exclusive_until_worker_finishes(self):
        self.runtime.state = 'ready'
        with patch('windows_runtime.threading.Thread'):
            self.runtime.command({'action': 'start', 'sessionId': 'a' * 36}, 'PHONE936')
        self.runtime.command({'action': 'stop', 'sessionId': 'a' * 36}, 'PHONE936')
        self.assertEqual(self.runtime.observations['PHONE936'].state, 'stopping')
        with self.assertRaises(BlockingIOError):
            self.runtime.command({'action': 'run', 'size': 6, 'sessionId': 'a' * 36}, 'PHONE936')

    def test_new_load_never_relabels_another_devices_result_or_old_audit_as_current(self):
        self.runtime.state = 'ready'
        self.runtime.load_identity = 'PHONE936'
        self.runtime.load = {'status': 'pass', 'total': 6, 'success': 6, 'results': [{'sessionId': 'old'}]}
        self.runtime.audit = SimpleNamespace(snapshot=lambda: [{'requestId': 'old-audit'}])
        with patch('windows_runtime.threading.Thread'):
            self.runtime.command({'action': 'run', 'size': 150, 'sessionId': 'b' * 36}, 'TABLET936')
        actual = self.runtime.status('TABLET936')['load']
        self.assertEqual((actual['status'], actual['total'], actual['success'], actual['results']), ('running', 150, 0, []))
        self.assertNotIn('logs', actual)
        self.assertIsNone(self.runtime.status('PHONE936')['load'])

    def test_existing_stack_is_adopted_without_recreating_database_or_credentials(self):
        (self.runtime.work / 'credentials.json').write_text('[{"email":"qa936-0001@eolssu.test","password":"existing-test-only"}]', encoding='utf8')
        (self.runtime.work / 'backend.env').write_text('SERVER_PORT=18181\n', encoding='utf8')
        calls = []
        def docker(*args):
            calls.append(args)
            if args[0] == 'ps':
                return self.runtime.name('db')
            if args[0] == 'network':
                return self.runtime.owner
            return 'true'
        with patch.object(self.runtime, 'docker', side_effect=docker), patch.object(self.runtime, 'verify_container'), patch.object(self.runtime, 'query', return_value='eolssu_qa'), patch.object(self.runtime, 'capture_log'), patch('windows_runtime.threading.Timer'):
            self.assertTrue(self.runtime.adopt_stack())
        self.assertTrue(self.runtime.stack_ready)
        self.assertEqual(len(self.runtime.credentials), 1)
        self.assertFalse(any(args[0] in {'run', 'rm'} for args in calls))
        self.assertTrue((self.runtime.work / 'credentials.json').exists())

    def test_failed_adoption_and_retry_never_delete_the_existing_database_or_recordings(self):
        credentials = self.runtime.work / 'credentials.json'
        credentials.write_text('preserve-existing-data', encoding='utf8')
        with patch.object(self.runtime, 'docker', side_effect=[self.runtime.name('db'), 'false']), patch.object(self.runtime, 'verify_container'):
            with self.assertRaises(RuntimeError):
                self.runtime.adopt_stack()
        self.assertTrue(self.runtime.preserve_existing)
        with patch.object(self.runtime, 'docker') as docker:
            self.runtime.close()
            docker.assert_not_called()
        self.assertTrue(credentials.exists())
        with patch.object(self.runtime, 'prepare'), patch.object(self.runtime, 'close') as close, patch.object(self.runtime, 'save_history'):
            self.runtime.run_command({'action': 'run', 'size': 6})
            close.assert_not_called()
        self.assertTrue(credentials.exists())

    def test_phone_events_do_not_appear_in_tablet_observation(self):
        self.runtime.observer = SimpleNamespace(status=lambda: {'device': {'name': 'phone'}, 'events': [{'requestId': 'phone-event'}]})
        self.assertEqual(self.runtime.status('TABLET936')['events'], [])
        self.assertEqual(self.runtime.status('TABLET936')['device']['name'], 'tablet')
        self.assertEqual(self.runtime.status('PHONE936')['events'][0]['requestId'], 'phone-event')

    def test_windows_crlf_package_version_is_read(self):
        observer = Observer()
        observer._command = lambda *_args, **_kwargs: 'Packages:\r\n    versionName=qa936-20261115-qa283-live\r\n'
        observer._read_package_version()
        self.assertEqual(observer.status()['device']['appVersion'], 'qa936-20261115-qa283-live')

    def test_normal_qa_apk_is_not_started_against_an_unverified_api(self):
        with patch.object(self.runtime, 'adb', return_value='    versionName=qa-20261115.080230\r\n'):
            with self.assertRaises(ScenarioFailure):
                self.runtime.observe()
        self.assertIsNone(self.runtime.observer)

    def test_resource_lease_does_not_interrupt_an_active_observation(self):
        self.runtime.mode = 'observe'
        self.runtime.state = 'observing'
        self.runtime.last_use = 0
        with patch('windows_runtime.threading.Timer') as timer, patch.object(self.runtime, 'close') as close:
            self.runtime.expire()
            close.assert_not_called()
            timer.return_value.start.assert_called_once()
        self.assertEqual(self.runtime.state, 'observing')

    def test_new_scenario_cannot_display_the_previous_scenarios_pass(self):
        self.runtime.state = 'ready'
        progress = self.runtime.work / 'scenario-progress.json'
        progress.write_text('{"scenario":"feedback","status":"pass","steps":[],"result":{"old":true}}', encoding='utf8')
        with patch('windows_runtime.threading.Thread'):
            self.runtime.command({'action': 'scenario', 'scenario': 'duplicate', 'sessionId': 'a' * 36}, 'PHONE936')
        actual = self.runtime.status('PHONE936')['scenario']
        self.assertEqual((actual['name'], actual['status'], actual['checks']), ('duplicate', 'running', []))
        self.assertNotIn('result', actual)
        self.assertFalse(progress.exists())


if __name__ == '__main__':
    unittest.main()
