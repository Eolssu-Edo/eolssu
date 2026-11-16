"""ai 이미지 교체·검증·되돌리기 계약을 Docker 대역으로 검사한다(S15P31E102-244)."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deploy_ai', Path(__file__).parents[1] / 'deploy-ai.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)

SHA = 'a' * 40
OLD = 'sha256:' + '1' * 64
NEW = 'sha256:' + '2' * 64
SOURCE = 'f' * 64
PROTECTED = {'backend': 'b', 'frontend': 'f', 'mysql': 'm', 'volumes': ['data']}


class FakeDocker(deploy.AiDeployer):
    """태그·실행 중 이미지만 흉내 낸다. 상태 저장·순서·되돌리기 판단은 실제 코드다."""

    def __init__(self, root):
        super().__init__(root)
        self.tags = {deploy.RUNTIME_TAG: OLD, 'eolssu-ai:ci-' + SHA: NEW}
        self.labels = {NEW: {deploy.REVISION_LABEL: SHA, deploy.SOURCE_LABEL: SOURCE}}
        self.running = OLD
        self.activations = []
        self.fail_activate_for = set()
        self.fail_warm = False
        self.contract = '422'
        self.protected_value = dict(PROTECTED)

    def image_id(self, reference):
        if reference in self.labels or reference in (OLD, NEW):
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
        selected = self.tags[deploy.RUNTIME_TAG]
        self.activations.append(selected)
        if selected in self.fail_activate_for:
            raise deploy.DeployError('compose failed')
        self.running = selected

    def health(self):
        return {'status': 'ok', 'modelVersion': 'pyin-9'}

    def contract_status(self):
        return self.contract

    def warm_up(self):
        if self.fail_warm and self.running == NEW:
            raise deploy.DeployError('warmup failed')
        return {'status': 'ok'}

    def protected(self):
        return dict(self.protected_value)

    def blocked_by_other_deployments(self):
        pass


class AiDeployTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.d = FakeDocker(Path(self.tmp.name))
        order = patch.object(deploy.support, 'check_release_order')
        self.order = order.start()
        self.addCleanup(order.stop)

    def journal(self):
        return json.loads(self.d.state.read_text())

    def test_success_moves_runtime_tag_and_keeps_previous_for_rollback(self):
        self.d.deploy(SHA)
        self.assertEqual(self.d.tags[deploy.RUNTIME_TAG], NEW)
        self.assertEqual(self.d.tags[deploy.ROLLBACK_TAG], OLD)
        self.assertEqual(self.d.running, NEW)
        saved = self.journal()
        self.assertEqual((saved['status'], saved['sha'], saved['image_id'], saved['previous_image'], saved['source']),
                         ('deployed', SHA, NEW, OLD, SOURCE))
        self.assertEqual(self.d.state.stat().st_mode & 0o777, 0o600)

    def test_recreate_failure_restores_previous_image(self):
        self.d.fail_activate_for = {NEW}
        with self.assertRaisesRegex(deploy.DeployError, 'previous image restored'):
            self.d.deploy(SHA)
        self.assertEqual(self.d.activations, [NEW, OLD])
        self.assertEqual((self.d.tags[deploy.RUNTIME_TAG], self.d.running), (OLD, OLD))
        self.assertEqual(self.journal()['status'], 'rolled_back')

    def test_failed_warm_up_or_unreachable_contract_rolls_back(self):
        self.d.fail_warm = True
        with self.assertRaises(deploy.DeployError):
            self.d.deploy(SHA)
        self.assertEqual(self.d.running, OLD)
        self.d.fail_warm = False
        self.d.contract = '000'
        with self.assertRaises(deploy.DeployError):
            self.d.deploy(SHA)
        # 이전 이미지도 백엔드에서 닿지 않으면 되돌리기 실패로 남겨 사람이 보게 한다.
        self.assertEqual(self.journal()['status'], 'rollback_failed')

    def test_interrupted_deployment_is_recovered_before_next_attempt(self):
        self.d.save({'status': 'deploying', 'sha': SHA, 'image_id': NEW, 'previous_image': OLD,
                     'previous_sha': '', 'protected': PROTECTED})
        self.d.tags[deploy.RUNTIME_TAG] = NEW
        self.d.running = NEW
        self.d.recover()
        self.assertEqual((self.d.tags[deploy.RUNTIME_TAG], self.d.running), (OLD, OLD))
        self.assertEqual(self.journal()['status'], 'rolled_back')

    def test_failed_rollback_can_be_resumed(self):
        self.d.fail_activate_for = {NEW, OLD}
        with self.assertRaises(deploy.DeployError):
            self.d.deploy(SHA)
        self.assertEqual(self.journal()['status'], 'rollback_failed')
        self.d.fail_activate_for = set()
        self.d.recover()
        self.assertEqual(self.journal()['status'], 'rolled_back')
        self.assertEqual(self.d.running, OLD)

    def test_same_running_image_is_verified_without_recreation(self):
        self.d.deploy(SHA)
        self.d.activations.clear()
        self.d.deploy(SHA)
        self.assertEqual(self.d.activations, [])

    def test_label_mismatch_is_rejected_before_any_tag(self):
        self.d.labels[NEW] = {deploy.REVISION_LABEL: 'b' * 40, deploy.SOURCE_LABEL: SOURCE}
        with self.assertRaisesRegex(deploy.DeployError, 'labels'):
            self.d.deploy(SHA)
        self.assertEqual(self.d.tags[deploy.RUNTIME_TAG], OLD)
        self.assertFalse(self.d.state.exists())

    def test_runtime_tag_out_of_sync_with_container_is_refused(self):
        self.d.tags[deploy.RUNTIME_TAG] = 'sha256:' + '3' * 64
        with self.assertRaisesRegex(deploy.DeployError, 'does not match the running container'):
            self.d.deploy(SHA)
        self.assertEqual(self.d.activations, [])
        self.assertFalse(self.d.state.exists())

    def test_protected_service_change_rolls_back(self):
        original = self.d.activate

        def activate():
            original()
            if self.d.running == NEW:
                self.d.protected_value = {**PROTECTED, 'mysql': 'replaced'}
        self.d.activate = activate
        with self.assertRaises(deploy.DeployError):
            self.d.deploy(SHA)
        self.assertEqual(self.d.running, OLD)

    def test_order_check_uses_last_deployed_or_restored_sha(self):
        self.d.deploy(SHA)
        self.order.reset_mock()
        self.d.check_order('b' * 40)
        self.order.assert_called_once_with('b' * 40, SHA)
        self.d.state.write_text(json.dumps({'status': 'rolled_back', 'sha': 'c' * 40, 'previous_sha': SHA}))
        self.order.reset_mock()
        self.d.check_order('b' * 40)
        self.order.assert_called_once_with('b' * 40, SHA)

    def test_wrong_sha_rejected_before_external_effect(self):
        for value in ['develop', '../escape', 'a' * 39, 'a' * 40 + ';id']:
            with self.assertRaises(deploy.DeployError):
                self.d.deploy(value)
        self.assertEqual(self.d.activations, [])


class OtherDeploymentTests(unittest.TestCase):
    def test_backend_drain_blocks_ai_deployment(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'deployment.json').write_text(json.dumps({'status': 'deployed', 'retire_slot': 'backend'}))
            with self.assertRaisesRegex(deploy.DeployError, 'drain the backend'):
                deploy.AiDeployer(root).blocked_by_other_deployments()


if __name__ == '__main__':
    unittest.main()
