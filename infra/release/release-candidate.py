#!/usr/bin/env python3
"""Check a release candidate (RC) and write its notes without changing Git or servers.

Commands:
  check REV   Validate that REV is a full develop SHA that main can fast-forward to.
  notes REV   Print release notes (markdown or JSON): versions, merged work, deployed
              revisions and the tag/main commands a person runs after approval.

The tool only reads Git objects. It never fetches, tags, pushes or deploys.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

SHA = re.compile('[0-9a-f]{40}')
TAG = re.compile(r'v\d+\.\d+\.\d+(-rc\.\d+)?')
BRANCH = r"(?P<type>[a-z]+)\((?P<part>[a-z]+)\)/(?P<key>S15P31E102-\d+)-(?P<title>.*)"
# GitLab merges before the 2026-11-26 move, GitHub pull request merges after it.
MERGES = (re.compile(r"Merge branch '" + BRANCH + r"' into 'develop'"),
          re.compile(r"Merge pull request #\d+ from [^/\s]+/" + BRANCH))
COMPONENTS = ('backend', 'web', 'ai', 'omr', 'playback')
# Each source is read from the candidate commit. A missing file is reported, not guessed.
VERSION_SOURCES = (
    ('backend', 'backend/build.gradle.kts', re.compile(r'^version = "([^"]+)"', re.M)),
    ('ai', 'ai/pyproject.toml', re.compile(r'^version = "([^"]+)"', re.M)),
    ('android.versionName', 'frontend/androidApp/build.gradle.kts',
     re.compile(r'"eolssu\.versionName"\)\.orElse\("([^"]+)"\)')),
    ('android.versionCode', 'frontend/androidApp/build.gradle.kts',
     re.compile(r'"eolssu\.versionCode"\)\.orElse\("([^"]+)"\)')),
    ('ios.MARKETING_VERSION', 'frontend/iosApp/Configuration/Config.xcconfig',
     re.compile(r'^MARKETING_VERSION=(\S+)', re.M)),
    ('ios.CURRENT_PROJECT_VERSION', 'frontend/iosApp/Configuration/Config.xcconfig',
     re.compile(r'^CURRENT_PROJECT_VERSION=(\S+)', re.M)),
)
# Marketing versions shown to users; build numbers and server artifacts are listed only.
USER_FACING = ('android.versionName', 'ios.MARKETING_VERSION')


class ReleaseError(Exception):
    pass


class Git:
    def __init__(self, repo):
        self.repo = Path(repo)

    def run(self, *args, check=True):
        result = subprocess.run(('git', '-C', str(self.repo), *args), capture_output=True, text=True, timeout=60)
        if check and result.returncode != 0:
            raise ReleaseError('git ' + args[0] + ' failed: ' + result.stderr.strip())
        return result

    def resolve(self, ref):
        result = self.run('rev-parse', '--verify', '--quiet', ref + '^{commit}', check=False)
        return result.stdout.strip() if result.returncode == 0 else None

    def is_ancestor(self, older, newer):
        result = self.run('merge-base', '--is-ancestor', older, newer, check=False)
        if result.returncode not in (0, 1):
            raise ReleaseError('cannot compare commits: ' + result.stderr.strip())
        return result.returncode == 0

    def show(self, rev, path):
        result = self.run('show', rev + ':' + path, check=False)
        return result.stdout if result.returncode == 0 else None

    def count(self, since, rev):
        return int(self.run('rev-list', '--count', since + '..' + rev).stdout.strip())

    def first_parent_subjects(self, since, rev):
        output = self.run('log', '--first-parent', '--format=%H%x09%s', since + '..' + rev).stdout
        return [tuple(line.split('\t', 1)) for line in output.splitlines() if '\t' in line]

    def latest_tag(self, rev):
        tags = self.run('tag', '--merged', rev, '--sort=-creatordate', '--list', 'v*').stdout.split()
        return next((tag for tag in tags if TAG.fullmatch(tag)), None)


def check(git, rev, remote='origin'):
    """Return facts about the candidate; raise ReleaseError on a blocking problem."""
    if not SHA.fullmatch(rev or ''):
        raise ReleaseError('a full lowercase 40-character commit SHA is required')
    if git.resolve(rev) != rev:
        raise ReleaseError('candidate commit is not present locally; fetch first')
    develop = git.resolve('refs/remotes/' + remote + '/develop')
    main = git.resolve('refs/remotes/' + remote + '/main')
    if not develop or not main:
        raise ReleaseError(remote + '/develop and ' + remote + '/main must be fetched')
    # Deploy tools accept only SHAs in develop history (deploy-backend.py check_release_order).
    if not git.is_ancestor(rev, develop):
        raise ReleaseError('candidate is not in ' + remote + '/develop history')
    if not git.is_ancestor(main, rev):
        raise ReleaseError(remote + '/main cannot fast-forward to the candidate; main has diverged')
    return {'revision': rev, 'develop': develop, 'main': main,
            'behind_develop': git.count(rev, develop), 'main_behind': git.count(main, rev)}


def versions(git, rev):
    found, missing = {}, []
    for name, path, pattern in VERSION_SOURCES:
        text = git.show(rev, path)
        match = pattern.search(text) if text is not None else None
        if match:
            found[name] = match.group(1)
        else:
            missing.append(name + ' (' + path + ')')
    return found, missing


def version_warnings(found, expected=None):
    shown = {found[name] for name in USER_FACING if name in found}
    warnings = []
    if len(shown) > 1:
        warnings.append('app marketing versions differ: ' +
                        ', '.join(name + '=' + found[name] for name in USER_FACING if name in found))
    if expected:
        for name in USER_FACING:
            if found.get(name) != expected:
                warnings.append(name + ' is ' + str(found.get(name)) + ', expected ' + expected)
    if found.get('backend', '').endswith('-SNAPSHOT'):
        warnings.append('backend version is a SNAPSHOT: ' + found['backend'])
    return warnings


def merged_work(git, since, rev):
    items, other = [], []
    for sha, subject in git.first_parent_subjects(since, rev):
        match = next((m for m in (pattern.fullmatch(subject) for pattern in MERGES) if m), None)
        if match:
            items.append({'sha': sha, 'key': match['key'], 'type': match['type'], 'part': match['part'],
                          'title': match['title'].replace('-', ' ')})
        else:
            other.append({'sha': sha, 'subject': subject})
    return items, other


def deployed_status(git, rev, deployed):
    """Compare running revisions (read by a person from servers) with the candidate."""
    rows = []
    for component in COMPONENTS:
        value = deployed.get(component)
        if value is None:
            rows.append({'component': component, 'deployed': None, 'state': 'unknown'})
            continue
        resolved = git.resolve(value) if SHA.fullmatch(value) else None
        if not resolved:
            state = 'not-found'
        elif resolved == rev:
            state = 'same'
        elif git.is_ancestor(resolved, rev):
            state = 'older'
        elif git.is_ancestor(rev, resolved):
            state = 'newer'
        else:
            state = 'diverged'
        rows.append({'component': component, 'deployed': value, 'state': state})
    return rows


def parse_deployed(values):
    deployed = {}
    for value in values or ():
        component, sep, sha = value.partition('=')
        if not sep or component not in COMPONENTS:
            raise ReleaseError('use --deployed COMPONENT=SHA with COMPONENT in ' + ', '.join(COMPONENTS))
        if component in deployed:
            raise ReleaseError('duplicate --deployed ' + component)
        deployed[component] = sha.strip()
    return deployed


def build_notes(git, rev, *, tag=None, since=None, deployed=None, expected=None, remote='origin'):
    facts = check(git, rev, remote)
    if tag is not None and not TAG.fullmatch(tag):
        raise ReleaseError('tag must look like v1.2.3 or v1.2.3-rc.1')
    since_ref = since or git.latest_tag(rev) or facts['main']
    base = git.resolve(since_ref)
    if not base or not git.is_ancestor(base, rev):
        raise ReleaseError('since must be an ancestor of the candidate')
    found, missing = versions(git, rev)
    items, other = merged_work(git, base, rev)
    notes = {
        'check': facts, 'since': base, 'since_ref': since_ref, 'tag': tag, 'versions': found,
        'missing_versions': missing,
        'warnings': version_warnings(found, expected), 'merged': items, 'other_commits': other,
        'deployed': deployed_status(git, rev, deployed or {}),
    }
    notes['warnings'] += ['deployed ' + row['component'] + ' is ' + row['state']
                          for row in notes['deployed'] if row['state'] not in ('same', 'unknown')]
    notes['commands'] = commands(rev, tag, remote)
    return notes


def commands(rev, tag, remote):
    # Printed for a person to run after team approval; never executed here.
    lines = ['git fetch ' + remote, 'python3 -B infra/release/release-candidate.py check ' + rev]
    if tag:
        lines += ["git tag -a " + tag + " " + rev + " -m 'Release " + tag + "'",
                  'git push ' + remote + ' refs/tags/' + tag]
    lines += ['# main 반영은 팀이 정한 방식(fast-forward MR 등)으로 ' + rev[:12] + '까지 옮긴다']
    return lines


def markdown(notes):
    facts = notes['check']
    out = ['# 릴리스 후보 ' + (notes['tag'] or facts['revision'][:12]), '',
           '- 후보 SHA: `' + facts['revision'] + '`',
           '- origin/develop: `' + facts['develop'] + '` (후보보다 ' + str(facts['behind_develop']) + '커밋 앞)',
           '- origin/main: `' + facts['main'] + '` (후보까지 ' + str(facts['main_behind']) + '커밋, fast-forward 가능)',
           '- 비교 기준: `' + notes['since_ref'] + '` (`' + notes['since'] + '`)', '', '## 버전', '', '| 항목 | 값 |', '| --- | --- |']
    out += ['| ' + name + ' | `' + value + '` |' for name, value in notes['versions'].items()]
    out += ['| ' + item + ' | 찾지 못함 |' for item in notes['missing_versions']]
    out += ['', '## 배포 중인 revision', '', '| 구성 | 배포 SHA | 후보 대비 |', '| --- | --- | --- |']
    out += ['| ' + row['component'] + ' | ' + ('`' + row['deployed'] + '`' if row['deployed'] else '미입력') +
            ' | ' + row['state'] + ' |' for row in notes['deployed']]
    out += ['', '## 경고', '']
    out += ['- ' + warning for warning in notes['warnings']] or ['- 없음']
    out += ['', '## 병합된 작업 (' + str(len(notes['merged'])) + '건)', '']
    for part in sorted({item['part'] for item in notes['merged']}):
        out += ['### ' + part, '']
        out += ['- ' + item['key'] + ' ' + item['type'] + ': ' + item['title']
                for item in notes['merged'] if item['part'] == part]
        out.append('')
    if notes['other_commits']:
        out += ['### 형식 밖 커밋', '']
        out += ['- `' + item['sha'][:12] + '` ' + item['subject'] for item in notes['other_commits']]
        out.append('')
    out += ['## 승인 후 사람이 실행할 명령', '', '```sh'] + notes['commands'] + ['```', '']
    return '\n'.join(out)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--repo', default='.')
    parser.add_argument('--remote', default='origin')
    commands_parser = parser.add_subparsers(dest='command', required=True)
    check_parser = commands_parser.add_parser('check')
    check_parser.add_argument('revision')
    notes_parser = commands_parser.add_parser('notes')
    notes_parser.add_argument('revision')
    notes_parser.add_argument('--tag')
    notes_parser.add_argument('--since')
    notes_parser.add_argument('--expect-version')
    notes_parser.add_argument('--deployed', action='append', metavar='COMPONENT=SHA')
    notes_parser.add_argument('--format', choices=('markdown', 'json'), default='markdown')
    notes_parser.add_argument('--strict', action='store_true', help='exit 2 when there are warnings')
    args = parser.parse_args(argv)
    git = Git(args.repo)
    try:
        if args.command == 'check':
            print(json.dumps(check(git, args.revision, args.remote), sort_keys=True))
            return 0
        notes = build_notes(git, args.revision, tag=args.tag, since=args.since,
                            deployed=parse_deployed(args.deployed), expected=args.expect_version,
                            remote=args.remote)
    except ReleaseError as error:
        print('release-candidate: ' + str(error), file=sys.stderr)
        return 1
    print(json.dumps(notes, ensure_ascii=False, indent=2, sort_keys=True) if args.format == 'json' else markdown(notes))
    return 2 if args.strict and notes['warnings'] else 0


if __name__ == '__main__':
    sys.exit(main())
