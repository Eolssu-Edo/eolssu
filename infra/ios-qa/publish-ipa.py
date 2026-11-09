#!/usr/bin/env python3
"""edo1: retain paid-team Ad Hoc IPAs and HTTPS OTA manifests (S15P31E102-444)."""
import argparse, datetime, fcntl, hashlib, html, json, os, pathlib, plistlib, re, shutil, tempfile, zipfile

BUNDLE_PREFIX = 'com.ssafy.eolssu.dev'
IDENT = re.compile(r'\d{8}T\d{6}Z-\d+-[a-f0-9]{8}')
MAX_SIZE = 300 * 1024 * 1024


def sha256(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def inspect_ipa(path):
    """Return app metadata. Rejects anything that is not a single signed Payload/<name>.app."""
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if any(n.startswith('/') or '..' in n.split('/') for n in names):
            raise ValueError('unsafe path in IPA')
        # iOS refuses an OTA install when anything else (e.g. __MACOSX/ from ditto) sits next to Payload/.
        if {n.split('/')[0] for n in names} != {'Payload'}:
            raise ValueError('IPA must contain only Payload/')
        apps = {n.split('/')[1] for n in names if n.startswith('Payload/') and n.count('/') >= 2}
        if len(apps) != 1 or not next(iter(apps)).endswith('.app'):
            raise ValueError('IPA must contain exactly one Payload/<name>.app')
        app = 'Payload/' + next(iter(apps)) + '/'
        info = plistlib.loads(archive.read(app + 'Info.plist'))
        try:
            profile = archive.read(app + 'embedded.mobileprovision')
        except KeyError:
            raise ValueError('IPA is not signed with a provisioning profile') from None
        if app + '_CodeSignature/CodeResources' not in names:
            raise ValueError('IPA is not code signed')
    # The profile is a CMS envelope around an XML plist. Only metadata is read; iOS verifies the signature.
    start, end = profile.find(b'<?xml'), profile.rfind(b'</plist>')
    if start < 0 or end < 0:
        raise ValueError('unreadable provisioning profile')
    provision = plistlib.loads(profile[start:end + len(b'</plist>')])
    bundle, version, build = info.get('CFBundleIdentifier', ''), info.get('CFBundleShortVersionString', ''), info.get('CFBundleVersion', '')
    if not (bundle == BUNDLE_PREFIX or bundle.startswith(BUNDLE_PREFIX + '.')) or not re.fullmatch(r'[A-Za-z0-9.-]{1,155}', bundle):
        raise ValueError('unexpected bundle identifier')
    if not re.fullmatch(r'\d+(\.\d+){0,2}', version) or not re.fullmatch(r'\d{1,18}', build):
        raise ValueError('unexpected app version')
    teams = provision.get('TeamIdentifier') or ['']
    if teams != ['WDA26H33JA'] or bundle != BUNDLE_PREFIX:
        raise ValueError('expected Eunseo paid team and release bundle ID')
    ent = provision.get('Entitlements', {})
    if ent.get('get-task-allow') is not False or not provision.get('ProvisionedDevices') or provision.get('ProvisionsAllDevices'):
        raise ValueError('expected Ad Hoc distribution profile')
    if 'Default' not in ent.get('com.apple.developer.applesignin', []):
        raise ValueError('missing Apple sign-in entitlement')
    expires = provision['ExpirationDate'].replace(tzinfo=datetime.timezone.utc)
    if expires <= datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1):
        raise ValueError('profile expires too soon')
    return dict(bundleId=bundle, version=version, bundleVersion=build, title=info.get('CFBundleDisplayName') or info.get('CFBundleName', ''),
                team=teams[0], profileExpiresAt=expires.strftime('%Y-%m-%dT%H:%M:%SZ'), devices=len(provision.get('ProvisionedDevices', [])))


def atomic_write(root, name, data):
    fd, temp = tempfile.mkstemp(prefix='.' + name + '-', dir=root)
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(data); out.flush(); os.fsync(out.fileno())
        os.chmod(temp, 0o644); os.replace(temp, root / name)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def manifest(meta, base_url):
    return plistlib.dumps({'items': [{
        'assets': [{'kind': 'software-package', 'url': f"{base_url}/{meta['id']}/app.ipa"}],
        'metadata': {'bundle-identifier': meta['bundleId'], 'bundle-version': meta['bundleVersion'],
                     'kind': 'software', 'title': f"{meta['title'] or '얼쑤'} {meta['commit'][:8]}"}}]})


def index_html(builds, base_url):
    ordered = sorted(builds, key=lambda v: v['builtAt'], reverse=True)
    compatible = [b for b in ordered if b.get('team') == 'WDA26H33JA' and b.get('bundleId') == BUNDLE_PREFIX]
    rows = []
    for i, b in enumerate(compatible):
        link = html.escape(f"itms-services://?action=download-manifest&url={base_url}/{b['id']}/manifest.plist")
        label = '최신 빌드 설치' if i == 0 else '이전 빌드 설치'
        rows.append(f'<li><a href="{link}">{label} · #{b["build"]} · {html.escape(b["commit"][:8])}</a> '
                    f'<small>빌드 시각 {html.escape(b["builtAt"])} · 서명 만료 {html.escape(b["profileExpiresAt"])}</small></li>')
    legacy = len(ordered) - len(compatible)
    notice = f'<p>이전 무료 계정 빌드 {legacy}개는 Safari 설치를 지원하지 않아 설치 링크를 숨겼습니다.</p>' if legacy else ''
    return ('<!doctype html><html lang="ko"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">'
            '<title>얼쑤 개발 iOS QA</title><style>body{font:17px system-ui;margin:24px;line-height:1.6}'
            'li{margin:20px 0}a{display:inline-block;padding:12px;background:#e9f2ff;border-radius:8px}'
            'small{display:block}li:first-child a{font-size:21px;font-weight:700}</style></head><body><h1>얼쑤 개발 iOS QA 빌드</h1>'
            '<p>등록된 iPhone의 Safari에서 맨 위 최신 빌드를 설치하세요. Mac 연결 없이 Wi-Fi 또는 셀룰러로 설치할 수 있습니다. '
            'Ad Hoc 배포 프로파일에 등록된 기기만 설치할 수 있습니다.</p>'
            f"<ul>{''.join(rows)}</ul>{notice}</body></html>\n").encode()


def publish(root, ipa, revision, build, base_url, keep=5, now=None):
    if not re.fullmatch(r'[a-f0-9]{40}', revision) or not 0 < build < 10**10 or not 0 < keep <= 50:
        raise ValueError('invalid revision/build/keep')
    if not re.fullmatch(r'https://[A-Za-z0-9.-]+(/[A-Za-z0-9._-]+)*', base_url):
        raise ValueError('invalid base URL')
    ipa = pathlib.Path(ipa)
    size = ipa.stat().st_size
    if not 0 < size <= MAX_SIZE:
        raise ValueError('invalid IPA size')
    info = inspect_ipa(ipa)
    digest = sha256(ipa)
    now = (now or datetime.datetime.now(datetime.timezone.utc)).replace(microsecond=0)
    root = pathlib.Path(root); root.mkdir(parents=True, exist_ok=True)
    with (root / '.publish.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        indexfile = root / 'index.json'
        index = json.loads(indexfile.read_text()) if indexfile.exists() else {'builds': []}
        same = next((v for v in index['builds'] if v['commit'] == revision and v['build'] == build), None)
        if same:
            if same['sha256'] != digest:
                raise ValueError('immutable build conflict')
            return same
        ident = now.strftime('%Y%m%dT%H%M%SZ') + f'-{build}-{revision[:8]}'
        meta = dict(id=ident, builtAt=now.strftime('%Y-%m-%dT%H:%M:%SZ'), commit=revision, build=build,
                    sha256=digest, size=size, **info)
        dest = root / ident
        dest.mkdir(exist_ok=False)
        shutil.copyfile(ipa, dest / 'app.ipa'); os.chmod(dest / 'app.ipa', 0o644)
        atomic_write(dest, 'manifest.plist', manifest(meta, base_url))
        atomic_write(dest, 'build.json', (json.dumps(meta, indent=2, ensure_ascii=False) + '\n').encode())
        builds = sorted(index['builds'] + [meta], key=lambda v: v['builtAt'])
        kept, removed = builds[-keep:], builds[:-keep]
        index['builds'] = kept
        atomic_write(root, 'index.json', (json.dumps(index, indent=2, ensure_ascii=False) + '\n').encode())
        atomic_write(root, 'index.html', index_html(kept, base_url))
        # Remove only directories this tool created; anything else in the folder is left alone.
        for old in removed:
            path = root / old['id']
            if IDENT.fullmatch(old['id']) and path.is_dir() and not path.is_symlink():
                shutil.rmtree(path)
        return meta


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--root', required=True); p.add_argument('--ipa', required=True)
    p.add_argument('--revision', required=True); p.add_argument('--build', required=True, type=int)
    p.add_argument('--base-url', required=True); p.add_argument('--keep', type=int, default=5)
    a = p.parse_args()
    print(json.dumps(publish(a.root, a.ipa, a.revision, a.build, a.base_url, a.keep), ensure_ascii=False))
