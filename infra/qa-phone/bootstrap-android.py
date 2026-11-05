#!/usr/bin/env python3
"""EDO1 operator bootstrap. Run as root after accepting the Android SDK license."""
import hashlib, os, pathlib, pwd, subprocess, urllib.request, zipfile
import io
ROOT=pathlib.Path('/opt/eolssu/android-sdk')
URL='https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
SHA='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
if os.environ.get('ACCEPT_ANDROID_SDK_LICENSE')!='1': raise SystemExit('Set ACCEPT_ANDROID_SDK_LICENSE=1 after accepting https://developer.android.com/studio#terms')
ROOT.mkdir(parents=True,exist_ok=True)
manager=ROOT/'cmdline-tools/latest/bin/sdkmanager'
if not manager.exists():
    data=urllib.request.urlopen(URL,timeout=120).read()
    if hashlib.sha256(data).hexdigest()!=SHA: raise SystemExit('command line tools checksum mismatch')
    target=ROOT/'cmdline-tools/latest';target.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for info in z.infolist():
            rel=pathlib.PurePosixPath(info.filename)
            if not rel.parts or rel.parts[0]!='cmdline-tools' or '..' in rel.parts: raise SystemExit('unexpected zip entry')
            out=target.joinpath(*rel.parts[1:])
            if info.is_dir(): out.mkdir(parents=True,exist_ok=True)
            else:
                out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(z.read(info))
                os.chmod(out, 0o755 if rel.parts[1]=='bin' else 0o644)
# SDK licenses and package install are explicit provisioning, not part of each CI run.
subprocess.run([str(manager),'--sdk_root='+str(ROOT),'--licenses'],input='y\n'*100,text=True,check=True)
subprocess.run([str(manager),'--sdk_root='+str(ROOT),'platforms;android-37.0','build-tools;37.0.0','platform-tools'],check=True)
who=pwd.getpwnam('jenkins')
for base,dirs,files in os.walk(ROOT):
    os.chown(base,who.pw_uid,who.pw_gid)
    for name in files: os.chown(os.path.join(base,name),who.pw_uid,who.pw_gid)
archive=pathlib.Path('/opt/eolssu/apks');archive.mkdir(exist_ok=True);os.chown(archive,who.pw_uid,who.pw_gid)
if not (archive/'index.json').exists():
    (archive/'index.json').write_text('{"builds": []}\n');os.chown(archive/'index.json',who.pw_uid,who.pw_gid)
print('EDO1 Android SDK and APK archive ready')
