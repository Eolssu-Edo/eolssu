#!/usr/bin/env python3
"""Check the identity, version, and distribution profile of an exported store IPA."""

import plistlib
import re
import subprocess
import sys
import tempfile
import zipfile


def verify(path, team, version, build):
    with zipfile.ZipFile(path) as ipa:
        apps = [name for name in ipa.namelist() if re.fullmatch(r'Payload/[^/]+\.app/Info\.plist', name)]
        if len(apps) != 1:
            raise ValueError('IPA must contain exactly one app')
        info = plistlib.loads(ipa.read(apps[0]))
        raw = ipa.read(apps[0].removesuffix('Info.plist') + 'embedded.mobileprovision')
    with tempfile.NamedTemporaryFile() as profile_file:
        profile_file.write(raw)
        profile_file.flush()
        profile = plistlib.loads(subprocess.check_output(
            ['security', 'cms', '-D', '-i', profile_file.name], stderr=subprocess.DEVNULL))
    if (info.get('CFBundleIdentifier') != 'com.ssafy.eolssu'
            or info.get('CFBundleShortVersionString') != version
            or info.get('CFBundleVersion') != build
            or profile.get('TeamIdentifier') != [team]
            or 'ProvisionedDevices' in profile
            or profile.get('ProvisionsAllDevices')
            or profile.get('Entitlements', {}).get('get-task-allow') is not False):
        raise ValueError('Unexpected app identity, version, build, or App Store profile')
    print('App Store IPA identity, version and profile verified')


if __name__ == '__main__':
    verify(*sys.argv[1:])
