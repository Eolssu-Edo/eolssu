#!/usr/bin/env python3
"""Explicit, isolated Gradle runtime check; never uses the normal daemon home."""
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import tempfile
import time


def verify(gradle):
    with tempfile.TemporaryDirectory(prefix='gradle-reuse510-') as directory:
        root = Path(directory)
        env = dict(os.environ, GRADLE_USER_HOME=str(root / 'gradle-home'))
        (root / 'settings.gradle').write_text("rootProject.name = 'daemon-fixture'\n")
        (root / 'gradle.properties').write_text('org.gradle.jvmargs=-Xmx256m\n')
        (root / 'build.gradle').write_text("""
tasks.register('reportPid') {
    doLast { println('GRADLE_PID=' + ProcessHandle.current().pid()) }
}
tasks.register('copyInput', Copy) {
    from('input.txt')
    into('output')
}
""")
        common = [str(gradle), '--console=plain', '--configuration-cache',
                  'reportPid', 'copyInput']
        samples = []
        try:
            for index, mode in enumerate(['warmup', 'no-daemon', 'daemon',
                                          'no-daemon', 'daemon', 'no-daemon', 'daemon']):
                expected = 'changed input ' + str(index)
                (root / 'input.txt').write_text(expected)
                options = ['--daemon', '-Dorg.gradle.daemon.idletimeout=60000'] if mode == 'daemon' else ['--no-daemon']
                start = time.monotonic()
                result = subprocess.run(common + options, cwd=root, env=env,
                                        check=True, capture_output=True, text=True, timeout=90)
                elapsed = time.monotonic() - start
                pid = int(re.search(r'GRADLE_PID=(\d+)', result.stdout).group(1))
                if (root / 'output/input.txt').read_text() != expected:
                    raise RuntimeError('Changed input was not reflected in the output')
                if mode != 'warmup':
                    sample = {'mode': mode, 'seconds': round(elapsed, 3), 'pid': pid,
                              'changed_input_copied': True,
                              'configuration_cache_reused': 'Reusing configuration cache.' in result.stdout}
                    samples.append(sample)
                    print(json.dumps(sample), flush=True)
            old = [s for s in samples if s['mode'] == 'no-daemon']
            new = [s for s in samples if s['mode'] == 'daemon']
            if len({s['pid'] for s in old}) != 3 or len({s['pid'] for s in new}) != 1:
                raise RuntimeError('Expected separate old JVMs and one reused daemon JVM')
            return {'gradle': str(gradle), 'samples': samples,
                    'no_daemon_median_s': statistics.median(s['seconds'] for s in old),
                    'daemon_median_s': statistics.median(s['seconds'] for s in new),
                    'scope': 'Small fixture; not an application CI timing comparison.'}
        finally:
            # This home belongs only to the fixture, so other jobs are unaffected.
            subprocess.run([str(gradle), '--stop'], cwd=root, env=env, check=True,
                           capture_output=True, text=True, timeout=30)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('gradle', type=Path)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    result = verify(args.gradle.resolve())
    args.receipt.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
