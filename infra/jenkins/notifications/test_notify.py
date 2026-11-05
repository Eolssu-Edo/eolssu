import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location('notify', Path(__file__).with_name('notify.py'))
n = importlib.util.module_from_spec(spec)
spec.loader.exec_module(n)

class NotificationTests(unittest.TestCase):
    def sample(self):
        return {'job':'eolssu-ios-qa','number':24,'result':'FAILURE','stage':'Build signed IPA on Mac',
                'trigger':'상위 eolssu-backend #259','authors':['wdg0434'], 'sha':'a'*40,
                'log':'error: No profiles for bundle were found\nERROR: script returned exit code 65\nAuthorization: Bearer abcdefSECRET\npassword=helloSECRET\nhttps://meeting.ssafy.com/hooks/secretHOOK'}

    def test_short_log_and_message_explain_identity_target_evidence_action(self):
        data=self.sample(); text=n.render(data)
        for word in ('eolssu-ios-qa','#24','wdg0434','상위 eolssu-backend','Build signed IPA','서명','프로파일','전체 로그'):
            self.assertIn(word,text)
        log=n.excerpt(data['log'])
        self.assertLessEqual(len(log),300);self.assertLessEqual(len(log.splitlines()),3)
        self.assertNotIn('abcdefSECRET',text);self.assertNotIn('helloSECRET',text);self.assertNotIn('secretHOOK',text)
        self.assertIn('127.0.0.1:18080',text)

    def test_json_secrets_tokens_and_signed_urls_are_redacted(self):
        text=n.excerpt('ERROR: {"password":"hidden value", "access_token":"hiddenToken", "authorization":"Bearer hiddenAuth"} user=user@example.com https://store/object?signature=hiddenSigned')
        for value in ('hidden value','hiddenToken','hiddenAuth','user@example.com','hiddenSigned'):
            self.assertNotIn(value,text)
        self.assertIn('[MASKED]',text)

    def test_many_long_errors_still_fit_three_lines_and_300_characters(self):
        log='\n'.join('ERROR: '+str(i)+' '+('very-long-diagnostic '*30) for i in range(10))
        short=n.excerpt(log)
        self.assertLessEqual(len(short),300)
        self.assertLessEqual(len(short.splitlines()),3)
        self.assertIn('ERROR: 0',short)

    def test_unknown_reason_is_not_guessed_and_mentions_are_inert(self):
        text=n.render(dict(self.sample(),log='build failed',authors=['@channel']))
        self.assertIn('미확정',text); self.assertNotIn('@channel',text)
        self.assertNotIn('서명 설정 실패',text)

    def test_reason_and_error_lines_are_not_displaced_by_generic_footer(self):
        log='e: file:///workspace/App.kt:12:3 Unresolved reference foo\n'+('info\n'*50)+'ERROR: script returned exit code 1'
        reason,action=n.diagnose(log,'Compile Wasm app')
        self.assertIn('컴파일',reason);self.assertIn('App.kt',n.excerpt(log));self.assertIn('참조',action)
        self.assertIn('마이그레이션',n.diagnose('ERROR: Deployment phase failed: Check database migrations','Deploy')[0])

    def test_expected_mock_errors_from_earlier_stages_do_not_become_the_cause(self):
        log='ERROR: Deployment phase failed: Check database migrations\n[Pipeline] { (JavaScript browser tests)\n841 tests completed, 1 failed\nThere were failing tests\nERROR: script returned exit code 1'
        scoped=n.scope_console(log,'JavaScript browser tests')
        self.assertNotIn('migrations',scoped)
        self.assertIn('테스트',n.diagnose(scoped,'JavaScript browser tests')[0])

    def test_log_lookup_failure_still_returns_a_failure_notice(self):
        api=n.Jenkins('test:credential')
        api.get=Mock(side_effect=[{'result':'FAILURE','actions':[],'changeSets':[]}, OSError(), ValueError()])
        data=api.detail('eolssu-ios-qa',24)
        self.assertIn('로그 조회 실패',n.render(data))
        self.assertIn('원인 미확정',n.render(data))

    def test_initial_seed_keeps_running_and_new_failures_and_sends_once(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'state.json';sent=[]
            builds=[{'number':1,'building':False,'result':'FAILURE'},{'number':2,'building':True,'result':None}]
            api=Mock();api.jobs.return_value={'eolssu-ios-qa':builds};api.detail.side_effect=lambda job,num:dict(self.sample(),number=num)
            sender=lambda data:sent.append(data)
            n.poll(api,path,sender);self.assertEqual(sent,[])
            api.jobs.return_value={'eolssu-ios-qa':[dict(builds[1],building=False,result='FAILURE'), {'number':3,'building':False,'result':'SUCCESS'}]}
            n.poll(api,path,sender);n.poll(api,path,sender)
            self.assertEqual(len(sent),1);self.assertEqual(sent[0]['number'],2)

    def test_manual_abort_is_silent_but_timeout_alerts(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'state.json';api=Mock();api.jobs.return_value={'eolssu-ios-qa':[]};sent=[]
            n.poll(api,path,sent.append)
            api.jobs.return_value={'eolssu-ios-qa':[{'number':4,'result':'ABORTED','building':False}]}
            api.detail.return_value=dict(self.sample(),result='ABORTED',log='Aborted by operator')
            n.poll(api,path,sent.append);self.assertFalse(sent)
            api.jobs.return_value={'eolssu-ios-qa':[{'number':5,'result':'ABORTED','building':False}]}
            api.detail.return_value=dict(self.sample(),number=5,result='ABORTED',log='Cancelling nested steps due to timeout')
            n.poll(api,path,sent.append);self.assertEqual(len(sent),1)
            self.assertIn('시간초과',n.render(sent[0]))

    def test_older_concurrent_build_finishing_late_is_not_lost(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'state.json';api=Mock();sent=[]
            api.jobs.return_value={'eolssu-ios-qa':[]};n.poll(api,path,sent.append)
            api.detail.side_effect=lambda job,num:dict(self.sample(),number=num)
            older={'number':7,'result':None,'building':True}
            newer={'number':8,'result':'FAILURE','building':False}
            api.jobs.return_value={'eolssu-ios-qa':[newer,older]};n.poll(api,path,sent.append)
            api.jobs.return_value={'eolssu-ios-qa':[newer,dict(older,result='FAILURE',building=False)]}
            n.poll(api,path,sent.append);n.poll(api,path,sent.append)
            self.assertEqual([item['number'] for item in sent],[8,7])

    def test_ambiguous_send_is_durable_and_not_repeated(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'state.json'
            api=Mock();api.jobs.return_value={'eolssu-ios-qa':[]};n.poll(api,path,Mock())
            api.jobs.return_value={'eolssu-ios-qa':[{'number':5,'building':False,'result':'FAILURE'}]}
            api.detail.return_value=self.sample()
            sender=Mock(side_effect=TimeoutError())
            n.poll(api,path,sender);n.poll(api,path,sender)
            self.assertEqual(sender.call_count,1)
            self.assertEqual(json.loads(path.read_text())['deliveries']['eolssu-ios-qa#5'],'uncertain')

if __name__=='__main__':unittest.main()
