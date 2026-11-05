#!/usr/bin/env python3
"""Publish a successfully built APK; preserve immutable archives outside Jenkins retention."""
import argparse, datetime, fcntl, hashlib, json, os, pathlib, re, shutil, subprocess, tempfile

def extract_merge_request(message):
    matches=set(re.findall(r'^See merge request [A-Za-z0-9_./-]+!([1-9][0-9]*)\s*$', message, re.MULTILINE))
    return int(next(iter(matches))) if len(matches)==1 else None

def revision_merge_request(revision):
    if not re.fullmatch(r'[a-f0-9]{40}', revision): return None
    try:
        message=subprocess.check_output(['git','show','-s','--format=%B',revision],text=True,stderr=subprocess.DEVNULL,timeout=5)
        return extract_merge_request(message)
    except (OSError,subprocess.SubprocessError): return None

def save_index(root,index):
    fd,temp=tempfile.mkstemp(prefix='.index-',dir=root)
    try:
        with os.fdopen(fd,'w') as out: json.dump(index,out,indent=2);out.flush();os.fsync(out.fileno())
        os.chmod(temp,0o644);os.replace(temp,root/'index.json')
    finally:
        if os.path.exists(temp):os.unlink(temp)

def publish(root, apk, revision, build, aapt, merge_request=None):
    if not re.fullmatch(r'[a-f0-9]{40}', revision) or not 0 < build < 10**10:
        raise ValueError('invalid revision/build')
    if merge_request is not None and (type(merge_request) is not int or not 0<merge_request<=2147483647): raise ValueError('invalid MR number')
    apk = pathlib.Path(apk)
    badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
    info = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not info or info[1] != 'com.ssafy.eolssu.qa' or not re.fullmatch(r'[A-Za-z0-9._+-]{1,100}', info[3]):
        raise ValueError('unexpected APK package/version')
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    root = pathlib.Path(root); root.mkdir(parents=True, exist_ok=True)
    with (root / '.publish.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        indexfile = root / 'index.json'
        index = json.loads(indexfile.read_text()) if indexfile.exists() else {'builds': []}
        mapping=index.get('mergeRequests',{})
        if merge_request is not None and mapping.get(revision,merge_request)!=merge_request: raise ValueError('MR metadata conflict')
        with apk.open('rb') as source: digest = hashlib.file_digest(source, 'sha256').hexdigest()
        size = apk.stat().st_size
        if not 0 < size <= 300 * 1024 * 1024: raise ValueError('invalid APK size')
        same = next((v for v in index['builds'] if v['commit'] == revision and v['build'] == build), None)
        if same:
            if same['sha256'] != digest: raise ValueError('immutable build conflict')
            if merge_request is not None and revision not in mapping:
                index['mergeRequests']={**mapping,revision:merge_request};save_index(root,index)
            return same
        ident = now.strftime('%Y%m%dT%H%M%SZ') + f'-{build}-{revision[:8]}'
        meta = dict(id=ident, builtAt=now.strftime('%Y-%m-%dT%H:%M:%SZ'), commit=revision, build=build,
                    package=info[1], versionCode=int(info[2]), versionName=info[3], sha256=digest, size=size)
        dest = root / ident; dest.mkdir(exist_ok=False)
        shutil.copyfile(apk, dest / 'app.apk'); os.chmod(dest / 'app.apk', 0o644)
        (dest / 'build.json').write_text(json.dumps(meta, indent=2) + '\n')
        index['builds'].append(meta)
        if merge_request is not None:index['mergeRequests']={**mapping,revision:merge_request}
        save_index(root,index)
        return meta

if __name__ == '__main__':
    p=argparse.ArgumentParser(); p.add_argument('--root',required=True);p.add_argument('--apk',required=True)
    p.add_argument('--revision',required=True);p.add_argument('--build',required=True,type=int);p.add_argument('--aapt',required=True)
    p.add_argument('--merge-request',type=int)
    a=p.parse_args(); mr=a.merge_request if a.merge_request is not None else revision_merge_request(a.revision)
    print(json.dumps(publish(a.root,a.apk,a.revision,a.build,a.aapt,mr)))
