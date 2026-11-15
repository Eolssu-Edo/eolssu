"""omr·playback 이미지 교체·대기열 대기·되돌리기 계약을 Docker·DB 대역으로 검사한다(S15P31E102-951)."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deploy_service', Path(__file__).parents[1] / 'deploy-service-image.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)

SHA = 'a' * 40
OLD = 'sha256:' + '1' * 64
NEW = 'sha256:' + '2' * 64
SOURCE = 'f' * 64
NOW = '2026-11-15 09:00:00.123456'
PROTECTED = {'backend': 'b', 'frontend': 'f', 'mysql': 'm', 'volumes': ['data']}


def fake(base):
    class Fake(base):
        """태그·실행 중 이미지·대기열만 흉내 낸다. 상태 저장·대기·되돌리기 판단은 실제 코드다."""

        def __init__(self, root):
            super().__init__(root)
            self.tags = {self.runtime_tag: OLD, self.candidate_image(SHA): NEW}
            self.labels = {NEW: {deploy.ai.REVISION_LABEL: SHA, self.source_label: SOURCE}}
            self.running = OLD
            self.activations = []
            self.commands = []
            self.fail_activate_for = set()
            self.pending = []
            self.failed = 0
            self.contract = self.contract_code
            self.queries = []

        def image_id(self, reference):
            if reference in (OLD, NEW):
                return reference
            if reference not in self.tags:
                raise deploy.DeployError('missing image')
            return self.tags[reference]

        def image_labels(self, reference):
            return self.labels.get(reference, {})

        def running_image(self):
            return self.running

        def tag(self, image_id, reference):
            self.tags[reference] = image_id

        def activate(self):
            selected = self.tags[self.runtime_tag]
            self.activations.append(selected)
            if selected in self.fail_activate_for:
                raise deploy.DeployError('compose failed')
            self.running = selected

        def health(self):
            return {'status': self.healthy_status}

        def contract_status(self):
            return self.contract

        def warm_up(self):
            self.commands.append(('tools', self.running))

        def protected(self):
            return dict(PROTECTED)

        def blocked_by_other_deployments(self):
            pass

        def query(self, sql):
            self.queries.append(sql)
            if sql == 'SELECT UTC_TIMESTAMP(6)':
                return NOW
            if sql == self.pending_sql:
                return str(self.pending.pop(0) if self.pending else 0)
            return str(self.failed)

    return Fake


class Recorder:
    """docker 명령 인자만 기록하는 run 대역."""

    def __init__(self):
        self.calls = []

    def __call__(self, *args, **kwargs):
        self.calls.append(args)
        return ''


class ServiceDeployTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        order = patch.object(deploy.support, 'check_release_order')
        order.start()
        self.addCleanup(order.stop)

    def make(self, name):
        return fake(deploy.DEPLOYERS[name])(self.root)

    def journal(self, deployer):
        return json.loads(deployer.state.read_text())

    def test_each_service_has_its_own_tags_journal_and_contract(self):
        omr, playback = self.make('omr'), self.make('playback')
        self.assertEqual((omr.runtime_tag, omr.rollback_tag, omr.state.name),
                         ('eolssu-omr:runtime', 'eolssu-omr:rollback', 'omr-deployment.json'))
        self.assertEqual((playback.runtime_tag, playback.state.name), ('eolssu-playback:runtime', 'playback-deployment.json'))
        self.assertEqual(omr.candidate_image(SHA), 'eolssu-omr:ci-' + SHA)
        self.assertEqual((omr.contract_path, omr.contract_code), ('/v1/recognitions', '422'))
        self.assertEqual((playback.contract_path, playback.contract_code), ('/v1/render', '400'))
        self.assertIn('sheet_conversions', omr.pending_sql)
        self.assertIn('sheet_audios', playback.pending_sql)
        # ai의 journal·태그는 건드리지 않는다.
        self.assertNotEqual(omr.state.name, deploy.ai.AiDeployer.state_file)

    def test_success_moves_runtime_tag_records_rollback_and_switch_failures(self):
        for name in ('omr', 'playback'):
            d = self.make(name)
            d.failed = 1
            d.deploy(SHA)
            self.assertEqual((d.tags[d.runtime_tag], d.tags[d.rollback_tag], d.running), (NEW, OLD, NEW))
            saved = self.journal(d)
            self.assertEqual((saved['status'], saved['sha'], saved['previous_image'], saved['source']),
                             ('deployed', SHA, OLD, SOURCE))
            self.assertEqual((saved['switched_at'], saved['failed_during_switch']), (NOW, 1))
            self.assertIn(f"finished_at >= '{NOW}'", d.queries[-1])
            self.assertEqual(d.state.stat().st_mode & 0o777, 0o600)

    def test_pending_jobs_stop_before_any_change(self):
        d = self.make('omr')
        d.pending = [2]
        with self.assertRaisesRegex(deploy.Busy, '2 omr job'):
            d.deploy(SHA)
        self.assertEqual((d.tags[d.runtime_tag], d.activations), (OLD, []))
        self.assertNotIn(d.rollback_tag, d.tags)
        self.assertFalse(d.state.exists())

    def test_failed_recreate_restores_previous_image(self):
        d = self.make('playback')
        d.fail_activate_for = {NEW}
        with self.assertRaisesRegex(deploy.DeployError, 'playback deployment failed; previous image restored'):
            d.deploy(SHA)
        self.assertEqual(d.activations, [NEW, OLD])
        self.assertEqual((d.tags[d.runtime_tag], d.running), (OLD, OLD))
        self.assertEqual(self.journal(d)['status'], 'rolled_back')

    def test_wrong_contract_or_failed_tool_check_rolls_back(self):
        d = self.make('omr')
        d.contract = '000'
        with self.assertRaises(deploy.DeployError):
            d.deploy(SHA)
        # 이전 이미지도 백엔드에서 닿지 않으면 되돌리기 실패로 남겨 사람이 보게 한다.
        self.assertEqual(self.journal(d)['status'], 'rollback_failed')
        d = fake(deploy.OmrDeployer)(self.root / 'x')
        (self.root / 'x').mkdir()

        def broken():
            raise deploy.DeployError('command failed: docker')
        d.warm_up = broken
        with self.assertRaises(deploy.DeployError):
            d.deploy(SHA)
        self.assertEqual((d.running, self.journal(d)['status']), (OLD, 'rolled_back'))

    def test_identical_running_image_is_verified_without_waiting_or_recreation(self):
        d = self.make('playback')
        d.deploy(SHA)
        d.activations.clear()
        d.pending = [3]
        d.deploy(SHA)
        self.assertEqual(d.activations, [])

    def test_playback_compose_uses_its_profile_and_omr_checks_tools_as_runtime_user(self):
        recorder = Recorder()
        with patch.object(deploy.ai, 'run', recorder):
            deploy.PlaybackDeployer(self.root).activate()
            deploy.OmrDeployer(self.root).activate()
        playback, omr = recorder.calls
        self.assertEqual(playback[playback.index('--profile') + 1], 'playback')
        self.assertEqual(playback[-1], 'playback')
        self.assertNotIn('--profile', omr)
        self.assertEqual(omr[-1], 'omr')
        recorder = Recorder()
        with patch.object(deploy, 'run', recorder):
            deploy.OmrDeployer(self.root).warm_up()
        self.assertEqual(recorder.calls[0][:3], ('docker', 'exec', 'eolssu-runtime-omr-1'))
        self.assertIn('$AUDIVERIS_CMD', recorder.calls[0][-1])

    def test_unexpected_database_answers_are_refused(self):
        d = self.make('omr')
        d.query = lambda sql: 'x'
        with self.assertRaisesRegex(deploy.DeployError, 'cannot count'):
            d.pending_jobs()
        with self.assertRaisesRegex(deploy.DeployError, 'database time'):
            d.database_now()
        with self.assertRaisesRegex(deploy.DeployError, 'database time'):
            d.failed_since("x' OR 1=1 --")


class WaitTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.lock = self.root / 'deploy.lock'
        self.clock = [0.0]
        order = patch.object(deploy.support, 'check_release_order')
        order.start()
        self.addCleanup(order.stop)
        locks = patch.object(deploy.support, 'acquire_lock', lambda lock: self.acquired.append(lock.name))
        locks.start()
        self.addCleanup(locks.stop)
        self.acquired = []

    def sleep(self, seconds):
        self.clock[0] += seconds

    def run_wait(self, d, seconds=100):
        deploy.deploy_when_idle(d, SHA, lock_path=self.lock, seconds=seconds, poll=10, sleep=self.sleep,
                                clock=lambda: self.clock[0])

    def test_waits_outside_the_lock_until_the_queue_is_empty(self):
        d = fake(deploy.OmrDeployer)(self.root)
        d.pending = [2, 1, 0, 0]
        self.run_wait(d)
        self.assertEqual(self.clock[0], 20)
        self.assertEqual(len(self.acquired), 1)
        self.assertEqual(self.journal_status(d), 'deployed')

    def test_job_arriving_after_the_wait_releases_the_lock_and_retries(self):
        d = fake(deploy.PlaybackDeployer)(self.root)
        # 첫 확인은 비었지만 lock 안 확인에서 1건, 다음 시도에서 비었다.
        d.pending = [0, 1, 0]
        with patch('builtins.print') as printed:
            self.run_wait(d)
        self.assertEqual(len(self.acquired), 2)
        events = [json.loads(call.args[0]) for call in printed.call_args_list]
        self.assertIn('jobs_pending', [event.get('event') for event in events])
        # Jenkins 단계 표시가 실패로 바뀌지 않게, 대기열 때문에 멈춘 시도에는 phase_failed가 없다.
        self.assertNotIn('phase_failed', [event.get('event') for event in events])
        self.assertEqual(self.journal_status(d), 'deployed')

    def test_queue_that_never_empties_fails_without_changing_anything(self):
        d = fake(deploy.OmrDeployer)(self.root)
        d.pending = [1] * 100
        with self.assertRaisesRegex(deploy.DeployError, 'did not finish in time'):
            self.run_wait(d, seconds=30)
        self.assertEqual((self.acquired, d.activations), ([], []))
        self.assertFalse(d.state.exists())

    def journal_status(self, d):
        return json.loads(d.state.read_text())['status']


class AiPathTests(unittest.TestCase):
    def test_ai_deployer_keeps_its_tags_journal_and_phase_names(self):
        ai = deploy.ai.AiDeployer(Path('/opt/eolssu'))
        self.assertEqual((ai.service, ai.runtime_tag, ai.rollback_tag, ai.container, ai.state.name),
                         ('ai', 'eolssu-ai:runtime', 'eolssu-ai:rollback', 'eolssu-runtime-ai-1', 'ai-deployment.json'))
        self.assertEqual((ai.contract_path, ai.contract_code, ai.healthy_status), ('/v1/analyses', '422', 'ok'))
        self.assertEqual(ai.compose_options, ())
        self.assertEqual(ai.candidate_image(SHA), 'eolssu-ai:ci-' + SHA)


if __name__ == '__main__':
    unittest.main()
