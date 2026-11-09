#!/usr/bin/env python3
"""Check exported Ad Hoc profile before publishing; never print registered device IDs."""
import datetime, plistlib, subprocess, sys, tempfile, zipfile

def verify(path, team, device):
    with zipfile.ZipFile(path) as z:
        infos = [n for n in z.namelist() if n.startswith('Payload/') and n.count('/') == 2 and n.endswith('/Info.plist')]
        if len(infos) != 1:
            raise ValueError('IPA must have one app')
        app = infos[0][:-len('Info.plist')]
        info = plistlib.loads(z.read(infos[0]))
        with tempfile.NamedTemporaryFile() as f:
            f.write(z.read(app + 'embedded.mobileprovision')); f.flush()
            profile = plistlib.loads(subprocess.check_output(['security', 'cms', '-D', '-i', f.name], stderr=subprocess.DEVNULL))
    ent = profile['Entitlements']
    if profile.get('TeamIdentifier') != [team] or info['CFBundleIdentifier'] != 'com.ssafy.eolssu.dev':
        raise ValueError('Unexpected team or bundle ID')
    if ent.get('get-task-allow') is not False or profile.get('ProvisionsAllDevices') or device not in profile.get('ProvisionedDevices', []):
        raise ValueError('Expected Ad Hoc profile containing the registered iPhone')
    if ent.get('application-identifier') != team + '.com.ssafy.eolssu.dev' or 'Default' not in ent.get('com.apple.developer.applesignin', []):
        raise ValueError('Missing application identity or Sign in with Apple capability')
    if profile['ExpirationDate'] < datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None) + datetime.timedelta(days=1):
        raise ValueError('Provisioning profile expires too soon')
    print('Ad Hoc profile verified: team, bundle, Apple sign-in, device membership and expiry')

if __name__ == '__main__':
    verify(*sys.argv[1:])
