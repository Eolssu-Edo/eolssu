from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from test_relay import load


collector = load('collector')
JENKINS = 'eolssu-jenkins-controller-1'
JENKINS_HOME = '/var/lib/jenkins'
DOCKER = {key: {'TotalSize': 1024, 'Reclaimable': 128}
          for key in ('ImageUsage', 'ContainerUsage', 'VolumeUsage', 'BuildCacheUsage')}


class StorageSnapshotTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.snapshot = Path(directory.name) / 'edo1.json'
        path = patch.object(collector, 'STORAGE_SNAPSHOT_PATH', self.snapshot, create=True)
        path.start()
        self.addCleanup(path.stop)
        identity = patch.object(collector, 'jenkins_home_identity', return_value=[1, 2])
        identity.start()
        self.addCleanup(identity.stop)

    def save(self, *, age=0):
        timestamp = (datetime.now(timezone.utc) - timedelta(minutes=age)).isoformat()
        data = {'docker': {'captured_at': timestamp, 'used': [1024] * 4, 'reclaimable': [128] * 4},
                'jenkins': {'captured_at': timestamp, 'bytes': 2048, 'identity': [1, 2]}}
        self.snapshot.write_text(json.dumps(data))
        return data

    def test_recent_measurements_avoid_expensive_live_scans(self):
        self.save(age=10)
        mounts = [{'Type': 'bind', 'Source': JENKINS_HOME, 'Destination': JENKINS_HOME}]
        with patch.object(collector, 'docker') as docker, patch.object(collector, 'run') as run:
            issues = []
            used, reclaimable = collector.disk_usage_summary(issues, target='edo1')
            jenkins = collector.mounted_storage(mounts, {}, name=JENKINS, target='edo1')
        docker.assert_not_called()
        run.assert_not_called()
        self.assertEqual(issues, [])
        self.assertEqual(used, '1.0KiB / 1.0KiB / 1.0KiB / 1.0KiB')
        self.assertIn('실측 `', reclaimable)
        self.assertIn('2.0KiB · 실측 `', jenkins)

    def test_stale_snapshot_runs_bounded_live_measurements(self):
        self.save(age=46)
        mounts = [{'Type': 'bind', 'Source': JENKINS_HOME, 'Destination': JENKINS_HOME}]
        with patch.object(collector, 'docker', return_value=DOCKER) as docker, \
                patch.object(collector, 'allocated_bind_bytes', return_value=4096, create=True) as measure:
            _, reclaimable = collector.disk_usage_summary([], target='edo1')
            jenkins = collector.mounted_storage(mounts, {}, name=JENKINS, target='edo1')
        docker.assert_called_once_with('/system/df', timeout=40)
        measure.assert_called_once_with(JENKINS_HOME, timeout=30)
        self.assertNotIn('실측', reclaimable)
        self.assertEqual(jenkins, 'bind /var/lib/jenkins: 4.0KiB')

    def test_other_host_and_mount_do_not_reuse_edo1_values(self):
        self.save()
        mounts = [{'Type': 'bind', 'Source': JENKINS_HOME, 'Destination': '/other'}]
        with patch.object(collector, 'docker', return_value=DOCKER) as docker, \
                patch.object(collector, 'allocated_bind_bytes', return_value=8192, create=True) as measure:
            _, reclaimable = collector.disk_usage_summary([], target='edo2')
            jenkins = collector.mounted_storage(mounts, {}, name=JENKINS, target='edo1')
        docker.assert_called_once_with('/system/df', timeout=20)
        measure.assert_called_once_with(JENKINS_HOME, timeout=12)
        self.assertNotIn('실측', reclaimable)
        self.assertEqual(jenkins, 'bind /other: 8.0KiB')

    def test_each_refresh_failure_preserves_its_previous_measurement(self):
        old = self.save(age=10)
        with patch.object(collector, 'docker', side_effect=TimeoutError), \
                patch.object(collector, 'allocated_bind_bytes', return_value=4096, create=True):
            self.assertEqual(collector.refresh_storage_snapshot(), ['Docker 저장공간'])
        after = json.loads(self.snapshot.read_text())
        self.assertEqual(after['docker'], old['docker'])
        self.assertEqual(after['jenkins']['bytes'], 4096)
        self.assertEqual(self.snapshot.stat().st_mode & 0o777, 0o600)
        with patch.object(collector, 'docker', return_value=DOCKER), \
                patch.object(collector, 'allocated_bind_bytes', side_effect=OSError, create=True):
            self.assertEqual(collector.refresh_storage_snapshot(), ['Jenkins 데이터 용량'])
        latest = json.loads(self.snapshot.read_text())
        self.assertNotEqual(latest['docker']['captured_at'], old['docker']['captured_at'])
        self.assertEqual(latest['jenkins'], after['jenkins'])

    def test_future_or_invalid_samples_do_not_bypass_live_collection(self):
        data = self.save(age=-10)
        with patch.object(collector, 'docker', return_value=DOCKER) as docker:
            collector.disk_usage_summary([], target='edo1')
        docker.assert_called_once()

        data = self.save()
        data['docker']['used'] = [0, 0, False, 0]
        self.snapshot.write_text(json.dumps(data))
        with patch.object(collector, 'docker', return_value=DOCKER) as docker:
            collector.disk_usage_summary([], target='edo1')
        docker.assert_called_once()

    def test_replaced_jenkins_directory_does_not_reuse_its_previous_size(self):
        self.save()
        with patch.object(collector, 'jenkins_home_identity', return_value=[1, 3]):
            self.assertIsNone(collector.fresh_storage_measurement('jenkins'))
