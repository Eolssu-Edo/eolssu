#!/usr/bin/env python3
"""Select CI suites for the exact checked-out commit and write GitHub job outputs."""

import json
import os
from pathlib import Path
import runpy


PYTHON_TESTS = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'jenkins/python-tests.py'))
CI_INPUTS = ('.github/', 'infra/github-actions/')
BACKEND_INPUTS = ('backend/', 'infra/jenkins/', 'infra/runtime/', 'infra/scripts/',
                  'docker-compose.dev.yml', 'docker-compose.prod.yml')
FRONTEND_BUILD = ('frontend/build.gradle.kts', 'frontend/settings.gradle.kts',
                  'frontend/gradle.properties', 'frontend/gradle/', 'frontend/gradlew',
                  'frontend/shared/build.gradle.kts', 'frontend/shared/src/common')
WEB_INPUTS = (*FRONTEND_BUILD, 'frontend/webApp/', 'frontend/kotlin-js-store/',
              'frontend/shared/src/web', 'frontend/shared/src/js', 'frontend/shared/src/wasmJs')
ANDROID_INPUTS = (*FRONTEND_BUILD, 'frontend/androidApp/', 'frontend/shared/src/android',
                  'infra/qa-phone/')


def select(paths):
    full = paths is None or any(path.startswith(CI_INPUTS) for path in paths)
    suites = {'backend': BACKEND_INPUTS, 'web': WEB_INPUTS, 'android': ANDROID_INPUTS}
    selected = {name: full or any(path.startswith(prefixes) for path in paths)
                for name, prefixes in suites.items()}
    selected['python'] = list(PYTHON_TESTS['GROUPS']) if full else PYTHON_TESTS['select'](paths)[0]
    return selected


def main():
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text(encoding='utf-8'))
    event_name = os.environ['GITHUB_EVENT_NAME']
    base = {'pull_request': lambda: event['pull_request']['base']['sha'],
            'push': lambda: event['before']}.get(event_name, lambda: '')()
    paths = PYTHON_TESTS['changed_paths'](base, os.environ['GITHUB_SHA'])
    selected = select(paths)
    # Preserve the weekly full Python check without rebuilding all application targets.
    if event_name == 'schedule':
        selected.update(backend=False, web=False, android=False)
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        for name, value in selected.items():
            print(f'{name}={json.dumps(value, separators=(",", ":"))}', file=output)


if __name__ == '__main__':
    main()
