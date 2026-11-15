from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('retention', Path(__file__).parents[1] / 'retention.py')
retention = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retention)

NOW = datetime(2026, 11, 15, 5, 0, tzinfo=timezone.utc)


def sha(number):
    return f'{number:040x}'


@contextmanager
def no_lock():
    yield


class Fixture:
    def __init__(self, folder, count=30):
        self.root = Path(folder)
        self.releases = self.root / 'releases'
        self.releases.mkdir()
        for number in range(1, count + 1):
            release = self.releases / sha(number)
            release.mkdir()
            (release / 'app.jar').write_bytes(b'x' * number)
            # Release n was created n days before NOW: number 1 is the newest.
            stamp = (NOW - timedelta(days=number)).timestamp()
            os.utime(release, (stamp, stamp))
        self.deploy(active=1, previous=2, web=3, previous_web=4)

    def deploy(self, active, previous, web, previous_web, status='deployed'):
        (self.root / '.env').write_text('DB_PASSWORD=fixture-secret\n'
                                        f'BACKEND_JAR={self.releases / sha(active) / "app.jar"}\n'
                                        f'WEB_DIST_DIR={self.releases / sha(web) / "web"}\n')
        (self.root / 'deployment.json').write_text(json.dumps({
            'status': status, 'sha': sha(active), 'jar': str(self.releases / sha(active) / 'app.jar'),
            'previous_jar': str(self.releases / sha(previous) / 'app.jar')}))
        (self.root / 'frontend-deployment.json').write_text(json.dumps({
            'status': 'deployed', 'sha': sha(web), 'web': str(self.releases / sha(web) / 'web'),
            'previous_web': str(self.releases / sha(previous_web) / 'web')}))


def image(repository, number, days_old, image_id=None):
    return {'repository': repository, 'sha': sha(number), 'id': image_id or f'sha256:{repository}-{number}',
            'created': NOW - timedelta(days=days_old)}


class RetentionTests(unittest.TestCase):
    def test_keep_set_combines_protected_newest_count_and_age(self):
        items = [(sha(n), NOW - timedelta(days=n)) for n in range(1, 31)]
        kept = retention.keep_set(items, {sha(30)}, NOW, keep_count=3, keep_age=timedelta(days=5))
        # newest 3 (1-3), younger than 5 days (1-4), protected (30)
        self.assertEqual(kept, {sha(1), sha(2), sha(3), sha(4), sha(30)})

    def test_protects_active_previous_and_mounted_releases(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            mounted = str(fixture.releases / sha(25) / 'web')
            protected = retention.protected_shas(fixture.root, mounts=['/var/lib/docker/volumes/x', mounted])
            self.assertEqual(protected, {sha(1), sha(2), sha(3), sha(4), sha(25)})

    def test_refuses_while_a_deployment_is_in_progress(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            fixture.deploy(1, 2, 3, 4, status='deploying')
            with self.assertRaisesRegex(retention.RetentionError, 'not in the deployed state'):
                retention.protected_shas(fixture.root)

    def test_refuses_release_reference_outside_managed_root(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            (fixture.root / '.env').write_text('BACKEND_JAR=/tmp/app.jar\nWEB_DIST_DIR=/tmp/web\n')
            with self.assertRaisesRegex(retention.RetentionError, 'outside managed root'):
                retention.protected_shas(fixture.root)

    def test_refuses_duplicate_release_keys(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            text = (fixture.root / '.env').read_text()
            (fixture.root / '.env').write_text(text + text.splitlines()[1] + '\n')
            with self.assertRaisesRegex(retention.RetentionError, 'BACKEND_JAR must occur exactly once'):
                retention.protected_shas(fixture.root)

    def test_plan_keeps_images_for_kept_releases_and_in_use_images(self):
        releases = [(sha(n), NOW - timedelta(days=n)) for n in range(1, 31)]
        images = [image('eolssu-backend', n, n) for n in range(1, 31)]
        images.append(image('eolssu-backend', 40, 40, image_id='sha256:running'))
        images.append(image('unrelated', 41, 41))
        decided = retention.plan(releases, images, {sha(29)}, NOW, in_use={'sha256:running'},
                                 keep_count=5, keep_age=timedelta(days=3))
        self.assertEqual(decided['releases'], sorted(sha(n) for n in range(6, 31) if n != 29))
        removed = {item['sha'] for item in decided['images']}
        self.assertEqual(removed, {sha(n) for n in range(6, 31) if n != 29})
        self.assertNotIn(sha(40), removed)
        self.assertNotIn(sha(41), removed)

    def test_plan_includes_ai_images_but_never_the_running_one(self):
        images = [image('eolssu-ai', n, n) for n in range(1, 8)]
        images.append(image('eolssu-ai', 50, 50, image_id='sha256:ai-running'))
        decided = retention.plan([], images, set(), NOW, in_use={'sha256:ai-running'},
                                 keep_count=2, keep_age=timedelta(days=1))
        removed = {item['sha'] for item in decided['images'] if item['repository'] == 'eolssu-ai'}
        self.assertEqual(removed, {sha(n) for n in range(3, 8)})

    def test_plan_keeps_only_newest_service_images_but_never_the_running_one(self):
        # n days old: the running image 9 is the oldest, 1~3 are the newest three.
        images = [image('eolssu-omr', n, n) for n in range(1, 8)]
        images.append(image('eolssu-omr', 9, 9, image_id='sha256:omr-running'))
        images += [image('eolssu-playback', n, n) for n in range(10, 12)]
        # A backend release or a recent date does not keep these: their rollback uses the image ID and :rollback.
        decided = retention.plan([(sha(5), NOW)], images, {sha(6)}, NOW, in_use={'sha256:omr-running'},
                                 keep_count=20, keep_age=timedelta(days=365))
        removed = {(item['repository'], item['sha']) for item in decided['images']}
        self.assertEqual(removed, {('eolssu-omr', sha(n)) for n in range(4, 8)})

    def test_dry_run_changes_nothing_and_reports_plan(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            calls = []
            report = retention.execute(fixture.root, apply=False, now=NOW, lock=no_lock,
                                       images_source=lambda: [image('eolssu-backend', n, n) for n in range(1, 31)],
                                       containers=lambda: (set(), []), docker=lambda *a, **k: calls.append(a),
                                       free=lambda: 100)
            self.assertEqual(report['mode'], 'dry-run')
            self.assertEqual(len(report['releases_to_remove']), 30 - 20)
            self.assertEqual(report['release_bytes_to_remove'], sum(range(21, 31)))
            self.assertEqual(len(list(fixture.releases.iterdir())), 30)
            self.assertEqual(calls, [])
            self.assertNotIn('fixture-secret', json.dumps(report))

    def test_apply_removes_only_planned_items_and_prunes_old_cache(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            (fixture.releases / 'bootstrap').mkdir()
            calls, frees = [], iter([100, 200])
            def docker(*args, **kwargs):
                calls.append(args)
                if args[-1] == 'eolssu-backend:ci-' + sha(30):
                    raise retention.RetentionError('command failed: docker image rm')
            report = retention.execute(fixture.root, apply=True, now=NOW, lock=no_lock,
                                       images_source=lambda: [image('eolssu-backend', n, n) for n in range(1, 31)],
                                       containers=lambda: (set(), []), docker=docker, free=lambda: next(frees))
            remaining = sorted(path.name for path in fixture.releases.iterdir())
            self.assertEqual(remaining, sorted([sha(n) for n in range(1, 21)] + ['bootstrap']))
            removed_tags = [call[-1] for call in calls if call[:3] == ('docker', 'image', 'rm')]
            self.assertEqual(sorted(removed_tags), sorted(f'eolssu-backend:ci-{sha(n)}' for n in range(21, 31)))
            self.assertIn(('docker', 'builder', 'prune', '--force', '--filter', 'until=72h'), calls)
            self.assertEqual(report['image_failures'], ['eolssu-backend:ci-' + sha(30)])
            self.assertEqual((report['free_bytes_before'], report['free_bytes_after']), (100, 200))

    def test_apply_holds_lock_only_while_removing_releases(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            events = []
            @contextmanager
            def tracked():
                events.append('lock')
                yield
                events.append('unlock')
            def docker(*args, **kwargs):
                events.append('docker')
            original = retention.remove_release
            retention.remove_release = lambda root, value: (events.append('release'), original(root, value))
            try:
                retention.execute(fixture.root, apply=True, now=NOW, lock=tracked,
                                  images_source=lambda: [image('eolssu-backend', 30, 30)],
                                  containers=lambda: (set(), []), docker=docker, free=lambda: 1)
            finally:
                retention.remove_release = original
            self.assertEqual(events[0], 'lock')
            unlock = events.index('unlock')
            self.assertTrue(all(event == 'release' for event in events[1:unlock]))
            self.assertTrue(all(event == 'docker' for event in events[unlock + 1:]))

    def test_skips_without_lock_or_docker_when_free_space_is_enough(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            calls = []
            @contextmanager
            def forbidden():
                raise AssertionError('lock must not be taken')
                yield
            report = retention.execute(fixture.root, apply=True, now=NOW, lock=forbidden,
                                       images_source=lambda: calls.append('images') or [],
                                       containers=lambda: calls.append('containers') or (set(), []),
                                       docker=lambda *a, **k: calls.append(a), free=lambda: 10 * 2 ** 30,
                                       below_free=10 * 2 ** 30)
            self.assertEqual(report['skipped'], 'enough free space')
            self.assertEqual(report['image_failures'], [])
            self.assertEqual(calls, [])
            self.assertEqual(len(list(fixture.releases.iterdir())), 30)

    def test_cleans_when_free_space_is_below_threshold(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder)
            frees = iter([10 * 2 ** 30 - 1, 20 * 2 ** 30])
            report = retention.execute(fixture.root, apply=True, now=NOW, lock=no_lock,
                                       images_source=lambda: [], containers=lambda: (set(), []),
                                       docker=lambda *a, **k: None, free=lambda: next(frees),
                                       below_free=10 * 2 ** 30)
            self.assertNotIn('skipped', report)
            self.assertEqual(len(list(fixture.releases.iterdir())), 20)
            self.assertEqual(report['free_bytes_after'], 20 * 2 ** 30)

    def test_remove_release_rejects_symlink_target(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder, count=1)
            outside = Path(folder) / 'outside'
            outside.mkdir()
            (fixture.releases / sha(99)).symlink_to(outside)
            with self.assertRaisesRegex(retention.RetentionError, 'unexpected release target'):
                retention.remove_release(fixture.root, sha(99))
            self.assertTrue(outside.exists())

    def test_lists_only_plain_sha_directories(self):
        with tempfile.TemporaryDirectory() as folder:
            fixture = Fixture(folder, count=2)
            (fixture.releases / 'bootstrap').mkdir()
            (fixture.releases / sha(50)).symlink_to(fixture.releases / sha(1))
            names = sorted(name for name, _ in retention.list_releases(fixture.root))
            self.assertEqual(names, [sha(1), sha(2)])

    def test_parses_docker_created_at(self):
        self.assertEqual(retention.parse_created('2026-11-14 22:10:01 +0900 KST'),
                         datetime(2026, 11, 14, 13, 10, 1, tzinfo=timezone.utc))


if __name__ == '__main__':
    unittest.main()
