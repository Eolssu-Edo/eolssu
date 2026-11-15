#!/usr/bin/python3
"""Read-only Jenkins observer; short, evidence-based Mattermost failure notices."""
import argparse
import base64
import fcntl
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

# 웹 수동 배포 잡(eolssu-frontend-web-수동배포용)처럼 한글이 든 잡 이름도 대상이다.
JOB = re.compile(r'eolssu-[a-z0-9가-힣-]+')


def job_path(job):
    """Jenkins URL path for a top-level job; non-ASCII names must be percent-encoded."""
    return 'job/' + urllib.parse.quote(job, safe='-') + '/'
ERROR = re.compile(r'(?i)(^e: |\berror:|^FAILURE:|Execution failed for task|^\s*Caused by:\s*\S|\bexception\b|\bFAILED\b|No profiles|Unable to find|Could not resolve|No space left|Check database migrations)')


def redact(text):
    text = str(text)
    text = re.sub(r'(?:file://)?/[^\s:]*?/workspace/[^/\s]+/', '', text)
    text = re.sub(r'\x1b\[[0-9;]*[a-zA-Z]', '', text)
    text = re.sub(r'https?://[^\s<>]+', '[URL 생략]', text)
    text = re.sub(r'(?i)(authorization\s*[:=]\s*)(?:Bearer|Basic)?\s*\S+', r'\1[MASKED]', text)
    text = re.sub(r'(?i)((?:[\w.-]*(?:password|passwd|secret|token|api[_-]?key|access[_-]?key|credential)[\w.-]*)\s*[=:]\s*)[^\s,;]+', r'\1[MASKED]', text)
    text = re.sub(r'''(?i)(["']?[\w.-]*(?:password|passwd|secret|token|api[_-]?key|access[_-]?key|credential|authorization)[\w.-]*["']?\s*[=:]\s*)("[^"]*"|'[^']*'|[^\s,;]+)''', r'\1[MASKED]', text)
    text = re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '[EMAIL]', text)
    text = re.sub(r'\b(?:glpat-|gh[pousr]_|sk-)[A-Za-z0-9_-]+', '[MASKED]', text)
    text = re.sub(r'-----BEGIN [^-]*PRIVATE KEY-----.*', '[PRIVATE KEY 생략]', text, flags=re.S)
    return text.replace('@', '@\u200b').replace('```', "'''")


def cell(value):
    return redact(value).replace('|', '\\|').replace('\n', ' ')[:240]


def excerpt(log):
    lines = [(i, redact(line).strip()) for i, line in enumerate(str(log).splitlines(), 1) if ERROR.search(line)]
    # Keep a specific diagnostic ahead of generic exit-code and build-failed footers.
    lines.sort(key=lambda pair: bool(re.search(r'(?i)(script returned exit code|BUILD FAILED|^FAILURE:)', pair[1])))
    selected = sorted(lines[:3])
    return '\n'.join(f'L{i}: {line[:160]}' for i, line in selected)[:300] or '선별 가능한 오류 줄 없음 · 전체 로그에서 원인 확인'


def scope_console(log, stage):
    marker = '[Pipeline] { (' + stage + ')'
    lines = log.splitlines()
    starts = [i for i,line in enumerate(lines) if line.strip() == marker]
    if not starts:
        return log
    start = starts[-1]
    end = next((i for i in range(start+1,len(lines)) if lines[i].startswith('[Pipeline] { (')),len(lines))
    return '\n'.join(lines[start+1:end])


def diagnose(log, stage):
    if re.search(r'(?i)(Timeout has been exceeded|Cancelling nested steps due to timeout)', log):
        return 'Jenkins 실행 제한시간 초과', '실패 단계의 마지막 진행 시각·에이전트 연결·명령 정체 원인을 확인한 뒤 동일 단계 재검증; 원인 확인 없이 제한시간만 늘리지 않음'
    if re.search(r'(?i)(No profiles|provisioning profile|requires a development team|code.?sign|signing certificate)', log):
        return 'iOS 서명·프로파일 설정 오류', 'Mac의 해당 앱 Bundle ID·Team·인증서·프로파일을 맞추고 서명 빌드만 재실행'
    if re.search(r'(?i)(Check database migrations|Flyway|migration.*(?:failed|mismatch)|duplicate.*migration)', log):
        return 'DB 마이그레이션 검사 실패', '실패 빌드의 deployment.jsonl과 운영 Flyway 이력을 비교해 중복 버전·checksum·호환성을 수정한 뒤 백엔드 재실행; 임의 repair 금지'
    if re.search(r'(?i)(Unresolved reference|Compilation error|^e: |\berror:.*(?:cannot find|unresolved))', log, re.M):
        return '소스 컴파일 오류', '핵심 로그의 파일·행에서 누락 참조·import·의존성을 수정하고 실패한 컴파일 단계부터 재검증'
    if re.search(r'(?i)(There were failing tests|tests? (?:failed|failures)|AssertionError|FAILED.*Test)', log):
        return '자동 테스트 실패', '해당 빌드 Test Result에서 실패 테스트명·기대값/실제값을 확인해 수정 후 같은 테스트를 재실행'
    if re.search(r'(?i)(No space left on device|ENOSPC|disk quota)', log):
        return '빌드 디스크 공간 부족', '실패 에이전트의 df -h·df -i와 workspace/캐시 용량을 확인하고 보존 정책에 맞게 정리 후 재실행'
    if re.search(r'(?i)(Could not resolve|Could not GET|Connection refused|timed out|Could not connect)', log):
        return '외부 의존성·연결 실패', '실패 에이전트에서 로그의 대상 연결·DNS·저장소 인증을 확인한 후 같은 단계 재실행'
    return '원인 미확정 · 실패 로그 추가 확인 필요', f'Jenkins의 {stage or "전체 빌드"} 로그에서 최초 오류와 직전 명령을 확인한 뒤 원인을 수정하고 재실행'


def render(data):
    reason, action = diagnose(data.get('log', ''), data.get('stage', ''))
    job, number = data['job'], int(data['number'])
    assert JOB.fullmatch(job) and number > 0
    path = f'http://127.0.0.1:18080/{job_path(job)}{number}/'
    sha = data.get('sha', '')
    revision = f'`{sha[:10]}`' if re.fullmatch('[a-f0-9]{40}', sha) else '미확인'
    authors = list(dict.fromkeys(data.get('authors', [])))
    who = ', '.join(authors[:3]) + (f' 외 {len(authors)-3}명' if len(authors)>3 else '') or 'checkout/변경 기록 없음'
    scope = data.get('log_scope', '전체 Console Output')
    stage_id = data.get('stage_id', '')
    log_url = path + (f'execution/node/{stage_id}/log' if str(stage_id).isdigit() else 'console')
    title = 'Jenkins 시간초과' if data.get('result') == 'ABORTED' else 'Jenkins 실패' if data.get('result') == 'FAILURE' else 'Jenkins 불안정'
    replay = ' · 과거 실패 알림 연결 검증' if data.get('replay') else ''
    return (f'### ❌ {title} · {job} #{number}{replay}\n\n'
            '| 항목 | 내용 |\n| --- | --- |\n'
            f'| 실행 / 변경 작성자 | {cell(data.get("trigger", "실행 계기 미확인"))} / {cell(who)} |\n'
            f'| 실패 대상 | {cell(data.get("stage", "단계 미확인"))} · 커밋 {revision} |\n'
            f'| 확인한 원인 | {cell(reason)} |\n| 조치 | {cell(action)} |\n\n'
            f'**핵심 로그 · {cell(scope)}**\n```text\n{excerpt(data.get("log", ""))}\n```\n'
            f'[관련 로그]({log_url}) · [전체 로그]({path}console) · [빌드·테스트 결과]({path})\n'
            '로그 링크는 기존 Jenkins SSH 터널(127.0.0.1:18080) 연결 후 열 수 있습니다. 변경 작성자가 실패 원인 제공자라는 뜻은 아닙니다.')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Jenkins:
    def __init__(self, auth):
        self.auth = base64.b64encode(auth.strip().encode()).decode()
        self.http = urllib.request.build_opener(NoRedirect)

    def get(self, path, raw=False):
        req = urllib.request.Request('http://127.0.0.1:8080/' + path, headers={'Authorization': 'Basic ' + self.auth})
        with self.http.open(req, timeout=20) as response:
            body = response.read(8*1024*1024+1)
        if len(body) > 8*1024*1024:
            raise ValueError('Jenkins response too large')
        return body.decode(errors='replace') if raw else json.loads(body)

    def jobs(self):
        jobs = self.get('api/json?tree=jobs[name,builds[number,result,building,timestamp,duration]{0,100}]')['jobs']
        return {j['name']:j.get('builds', []) for j in jobs if JOB.fullmatch(j['name'])}

    def detail(self, job, number):
        assert JOB.fullmatch(job) and int(number)>0
        path = f'{job_path(job)}{int(number)}/'
        tree = 'result,actions[causes[userName,shortDescription,upstreamProject,upstreamBuild],lastBuiltRevision[SHA1]],changeSets[items[author[fullName]]]'
        data = self.get(path+'api/json?tree='+tree)
        causes = [c for a in data.get('actions',[]) for c in a.get('causes',[])]
        trigger = '; '.join(('수동 '+c['userName'] if c.get('userName') else
                              '상위 '+c['upstreamProject']+' #'+str(c['upstreamBuild']) if c.get('upstreamProject') else
                              c.get('shortDescription','자동/미확인')) for c in causes) or '자동/실행 계기 미확인'
        authors = [c.get('author',{}).get('fullName','미확인') for group in data.get('changeSets',[]) for c in group.get('items',[])]
        sha = next((a['lastBuiltRevision']['SHA1'] for a in data.get('actions',[]) if a.get('lastBuiltRevision',{}).get('SHA1')), '')
        result = dict(job=job,number=number,result=data['result'],trigger=trigger,authors=authors,sha=sha,stage='파이프라인 시작 전/단계 미확인',stage_id='',log_scope='전체 Console Output')
        try:
            stages = self.get(path+'wfapi/describe').get('stages',[])
            failed = [s for s in stages if s.get('status') in ('FAILED','UNSTABLE','ABORTED')]
            if failed:
                stage = min(failed,key=lambda s:s.get('startTimeMillis',0))
                result.update(stage=stage['name'],stage_id=str(stage['id']),log_scope=stage['name']+' 단계 로그')
                assert str(stage['id']).isdigit()
                result['log'] = self.get(path+'execution/node/'+str(stage['id'])+'/wfapi/log')['text']
        except (OSError, KeyError, ValueError, TypeError):
            pass
        if 'log' not in result:
            try:
                full = self.get(path+'consoleText',raw=True)
                result['log'] = scope_console(full,result['stage'])
                result.update(stage_id='',log_scope=result['stage']+' · Console Output 발췌' if result['log'] != full else '전체 Console Output')
            except (OSError, ValueError, TypeError):
                result.update(log='',stage_id='',log_scope='로그 조회 실패 · Jenkins에서 직접 확인')
        return result


def save(path, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp = path.with_suffix('.tmp')
    fd = os.open(temp, os.O_WRONLY|os.O_CREAT|os.O_TRUNC, 0o600)
    with os.fdopen(fd,'w') as out:json.dump(data,out)
    os.replace(temp,path)


def poll(api, path, sender):
    state = json.loads(path.read_text()) if path.exists() else {'jobs':{},'deliveries':{}}
    initial = not path.exists()
    # The state file is rewritten on every poll, so its mtime is when the previous poll finished.
    last_poll_ms = None if initial else path.stat().st_mtime * 1000
    for job, builds in api.jobs().items():
        if job not in state['jobs']:
            if initial:
                floor = max([b['number'] for b in builds] or [0])
            else:
                # A job seen for the first time may be brand new or may only now match JOB (e.g. a
                # Korean name). Skip builds that had already finished before the previous poll so
                # its history is not re-announced; builds finishing later still alert.
                floor = max([b['number'] for b in builds if not b.get('building') and 'timestamp' in b
                             and b['timestamp'] + b.get('duration', 0) <= last_poll_ms] or [0])
            # Running builds stay tracked even when an older-numbered one is still running.
            state['jobs'][job] = {'floor':floor, 'pending':[b['number'] for b in builds if b.get('building')]}
        cursor = state['jobs'][job]
        selected = sorted([b for b in builds if b['number']>cursor['floor'] or b['number'] in cursor['pending']],key=lambda b:b['number'])
        for build in selected:
            number=build['number'];key=f'{job}#{number}'
            cursor['floor']=max(cursor['floor'],number)
            if number not in cursor['pending']:cursor['pending'].append(number)
            if build.get('building') or build.get('result') is None:continue
            if build['result'] in ('FAILURE','UNSTABLE','ABORTED') and key not in state['deliveries']:
                try:data=api.detail(job,number)
                except Exception:
                    save(path,state);print(json.dumps({'event':'jenkins_notify','state':'detail_failed','build':key}));continue
                if build['result']=='ABORTED' and not re.search(r'(?i)(Timeout has been exceeded|Cancelling nested steps due to timeout)',data.get('log','')):
                    cursor['pending'].remove(number);save(path,state);continue
                # Persist the intent before a network write; never auto-repeat an ambiguous send.
                state['deliveries'][key]='sending';save(path,state)
                try:
                    sender(data);state['deliveries'][key]='sent'
                except Exception:state['deliveries'][key]='uncertain'
                print(json.dumps({'event':'jenkins_notify','state':state['deliveries'][key],'build':key}))
            cursor['pending'].remove(number)
            save(path,state)
    for key,value in list(state['deliveries'].items()):
        if value=='sending':state['deliveries'][key]='uncertain'
    save(path,state)
    return state


def webhook_sender(url):
    parsed=urllib.parse.urlsplit(url)
    if parsed.scheme!='https' or parsed.hostname!='meeting.ssafy.com' or parsed.port not in (None,443) or parsed.username or parsed.password or parsed.query or parsed.fragment or not re.fullmatch('/hooks/[a-z0-9]+',parsed.path):
        raise ValueError('Invalid Mattermost destination')
    http=urllib.request.build_opener(NoRedirect)
    def send(data):
        payload=json.dumps({'username':'Jenkins','channel':'e102-cicd','text':render(data)},ensure_ascii=False).encode()
        request=urllib.request.Request(url,data=payload,headers={'Content-Type':'application/json'},method='POST')
        with http.open(request,timeout=10) as response:
            if response.status!=200:raise ValueError('Mattermost rejected delivery')
    return send


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--preview');parser.add_argument('--send-replay')
    args=parser.parse_args()
    credentials=Path(os.environ.get('CREDENTIALS_DIRECTORY','/etc/eolssu-jenkins-notify'))
    api=Jenkins((credentials/'jenkins-auth').read_text())
    state_dir=Path(os.environ.get('STATE_DIRECTORY','/var/lib/eolssu-jenkins-notify'))
    state_dir.mkdir(parents=True,exist_ok=True)
    with (state_dir/'lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.preview or args.send_replay:
            selector=args.preview or args.send_replay;job,number=selector.rsplit('#',1)
            data=api.detail(job,int(number));data['replay']=True
            if args.preview:print(render(data))
            else:
                path=state_dir/'state.json';state=json.loads(path.read_text())
                if selector in state['deliveries']:raise ValueError('Delivery already recorded')
                state['deliveries'][selector]='sending';save(path,state)
                try:webhook_sender((credentials/'webhook-url').read_text().strip())(data);state['deliveries'][selector]='sent'
                except Exception:state['deliveries'][selector]='uncertain';raise
                finally:save(path,state)
                print(json.dumps({'state':state['deliveries'][selector],'build':selector}))
        else:poll(api,state_dir/'state.json',webhook_sender((credentials/'webhook-url').read_text().strip()))


if __name__=='__main__':
    try:main()
    except Exception as error:
        print(json.dumps({'event':'jenkins_notify_error','type':type(error).__name__}));raise SystemExit(1)
