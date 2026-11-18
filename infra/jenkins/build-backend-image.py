#!/usr/bin/env python3
"""Package the tested executable JAR and verify the bytes stored in the image."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import signal
import subprocess
import tempfile
import uuid


def digest(path):
    checksum = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            checksum.update(chunk)
    return checksum.hexdigest()


def build_image(revision, libs, dockerfile, *, run=subprocess.run):
    if not re.fullmatch('[0-9a-f]{40}', revision):
        raise ValueError('A full lowercase commit SHA is required')
    jars = [path for path in libs.glob('*.jar') if not path.name.endswith('-plain.jar')]
    if len(jars) != 1 or jars[0].is_symlink() or not jars[0].is_file():
        raise ValueError('Exactly one regular executable JAR is required')
    if not dockerfile.is_file():
        raise ValueError('Artifact Dockerfile is missing')

    image = 'eolssu-backend:ci-' + revision
    with tempfile.TemporaryDirectory(prefix='eolssu-backend-image-') as directory:
        root = Path(directory)
        context = root / 'context'
        context.mkdir()
        artifact = context / 'app.jar'
        shutil.copyfile(jars[0], artifact)
        checksum = digest(artifact)
        run(['docker', 'build', '--file', str(dockerfile.resolve()),
             '--label', 'org.opencontainers.image.revision=' + revision,
             '--label', 'com.eolssu.artifact.sha256=' + checksum,
             '--tag', image, str(context)], check=True)
        # Allocate the name before creation so an interrupted create can also be cleaned up.
        container = 'eolssu-ci-verify-' + uuid.uuid4().hex
        created = False
        try:
            run(['docker', 'create', '--name', container, '--network', 'none',
                 '--label', 'com.eolssu.ci.verification=backend-jar', image],
                check=True, stdout=subprocess.DEVNULL)
            created = True
            extracted = root / 'image.jar'
            run(['docker', 'cp', container + ':/app/app.jar', str(extracted)], check=True)
            if digest(extracted) != checksum:
                raise ValueError('Image JAR does not match the tested artifact')
        finally:
            # Defer a first SIGTERM arriving during removal, including for its child process.
            previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM})
            try:
                run(['docker', 'rm', container], check=created, capture_output=not created, timeout=10)
            finally:
                signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
    return {'image': image, 'jar': jars[0].name, 'jar_sha256': checksum}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('revision')
    parser.add_argument('--libs', type=Path, default=Path('backend/build/libs'))
    parser.add_argument('--dockerfile', type=Path,
                        default=Path(__file__).with_name('backend-artifact.Dockerfile'))
    args = parser.parse_args()

    def terminate(signum, _frame):
        signal.signal(signum, signal.SIG_IGN)
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, terminate)
    try:
        result = build_image(args.revision, args.libs, args.dockerfile)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, str(error) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
