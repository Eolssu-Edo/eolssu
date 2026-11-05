#!/usr/bin/env python3
"""Package the tested executable JAR and verify the bytes stored in the image."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


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
        container = run(['docker', 'create', '--network', 'none', image],
                        check=True, text=True, capture_output=True).stdout.strip()
        if not re.fullmatch('[0-9a-f]{64}', container):
            raise ValueError('Docker did not return a verification container ID')
        try:
            extracted = root / 'image.jar'
            run(['docker', 'cp', container + ':/app/app.jar', str(extracted)], check=True)
            if digest(extracted) != checksum:
                raise ValueError('Image JAR does not match the tested artifact')
        finally:
            run(['docker', 'rm', container], check=True)
    return {'image': image, 'jar': jars[0].name, 'jar_sha256': checksum}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('revision')
    parser.add_argument('--libs', type=Path, default=Path('backend/build/libs'))
    parser.add_argument('--dockerfile', type=Path,
                        default=Path(__file__).with_name('backend-artifact.Dockerfile'))
    args = parser.parse_args()
    try:
        result = build_image(args.revision, args.libs, args.dockerfile)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, str(error) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
