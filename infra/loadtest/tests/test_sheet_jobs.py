"""Checks the QA driver's evidence and rejection boundaries, not product behavior."""
import hashlib
import copy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import shutil
import subprocess
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
import urllib.error

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import sheet_jobs


class ReplyClient:
    def __init__(self, replies): self.replies=iter(replies)
    def call(self,*args,**kwargs): return next(self.replies)


class SheetJourneyEvidenceTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'),'FFmpeg/ffprobe required for real media boundary check')
    def test_media_decodes_real_aac_and_rejects_non_audio_and_wrong_duration(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'valid.m4a'; journal=sheet_jobs.Journal(Path(directory)/'events.json')
            subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i','sine=frequency=440:duration=0.1','-c:a','aac',str(target)],check=True,capture_output=True,timeout=15)
            sheet_jobs.verify_media({'durationMs':100},target,journal,0)
            self.assertEqual(journal.events[0]['step'],'media-decoded')
            with self.assertRaises(sheet_jobs.JourneyFailure):
                sheet_jobs.verify_media({'durationMs':1000},target,journal,0)
            target.write_bytes(b'non-audio bytes with a matching declared hash')
            with self.assertRaises(subprocess.CalledProcessError):
                sheet_jobs.verify_media({'durationMs':100},target,journal,0)

    def test_score_requires_matching_identity_version_and_nonempty_result(self):
        common={'schemaVersion':1,'score':{'scoreVersion':2},'columns':[{'cells':[{'units':[]}]}],'events':[{}]}
        valid={'sheetId':'7','scoreVersion':'2','scoreData':common}
        self.assertEqual(sheet_jobs.verify_score(valid,'7'),'2')
        self.assertEqual(sheet_jobs.verify_score({**valid,'scoreData':None,'draft':{'score':common}},'7'),'2')
        for changes in ({'sheetId':'8'},{'scoreVersion':'1'},{'scoreData':{}},
                        {'scoreData':{**common,'columns':[]}}, {'scoreVersion':'0'},
                        {'scoreData':{**common,'columns':'not decoded columns'}},
                        {'scoreData':{**common,'events':[]}},
                        {'scoreData':{**common,'columns':[{'cells':'broken'}]}}):
            with self.subTest(changes=changes),self.assertRaises(sheet_jobs.JourneyFailure):
                sheet_jobs.verify_score({**valid,**changes},'7')

    def test_audio_requires_current_version_and_bound_performance_hashes(self):
        valid={'sheetId':'7','scoreVersion':'2','currentScoreVersion':'2','outdated':False,
               'audio':{'sha256':'a'*64,'performance':{'sourceCommonSha256':'b'*64,
                        'timeline':{'sourceSha256':'b'*64,'audioSha256':'a'*64,'scoreVersion':2}}}}
        self.assertEqual(sheet_jobs.verify_audio(valid,'7','2'),valid['audio'])
        changes=[(('sheetId',),'8'),(('outdated',),True),(('scoreVersion',),'1'),
                 (('currentScoreVersion',),'3'),(('audio','sha256'),'c'*64),
                 (('audio','performance','sourceCommonSha256'),'invalid'),
                 (('audio','performance','timeline','sourceSha256'),'c'*64),
                 (('audio','performance','timeline','scoreVersion'),1)]
        for path,value in changes:
            result=copy.deepcopy(valid); target=result
            for key in path[:-1]: target=target[key]
            target[path[-1]]=value
            with self.subTest(path=path),self.assertRaises(sheet_jobs.JourneyFailure):
                sheet_jobs.verify_audio(result,'7','2')

    def test_redirect_does_not_escape_local_http_boundary(self):
        paths=[]
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                paths.append(self.path)
                self.send_response(302); self.send_header('Location','/unexpected'); self.end_headers()
            def log_message(self,*args): pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
        try:
            base=f'http://127.0.0.1:{server.server_port}'
            client=sheet_jobs.Client(base,sheet_jobs.Recorder(),opener=sheet_jobs.LOCAL_HTTP)
            self.assertEqual(client.call('redirect','GET','/api')[0],302)
            with tempfile.TemporaryDirectory() as directory,self.assertRaises(urllib.error.HTTPError):
                sheet_jobs.verify_download({'url':base+'/audio','sha256':'a'*64,'byteSize':1,'durationMs':1},Path(directory)/'audio',sheet_jobs.Journal(Path(directory)/'events.json'),0)
            self.assertEqual(paths,['/api','/audio'])
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_unknown_state_and_http_failure_are_not_terminal_success(self):
        with tempfile.TemporaryDirectory() as directory:
            journal=sheet_jobs.Journal(Path(directory)/'events.json')
            for reply in [(503,{'result':{}}),(200,{'result':{'status':'READY'}})]:
                with self.subTest(reply=reply),self.assertRaises(sheet_jobs.JourneyFailure):
                    sheet_jobs.wait_terminal(ReplyClient([reply]),journal,0,'/audio',time.monotonic()+1)
            failed=sheet_jobs.wait_terminal(ReplyClient([(200,{'result':{'status':'FAILED','failureCode':'TIMEOUT'}})]),journal,0,'/audio',time.monotonic()+1)
            self.assertEqual(failed['status'],'FAILED')

    def test_expected_rejection_is_distinct_from_unexpected_success(self):
        with tempfile.TemporaryDirectory() as directory:
            journal=sheet_jobs.Journal(Path(directory)/'events.json')
            reply={'result':{'errorCode':'AUDIO_IN_PROGRESS','url':'secret-url'}}
            sheet_jobs.checked(ReplyClient([(409,reply)]),journal,0,'duplicate','POST','/audio',[409])
            with self.assertRaises(sheet_jobs.JourneyFailure):
                sheet_jobs.checked(ReplyClient([(202,{'result':{}})]),journal,0,'duplicate','POST','/audio',[409])
            journal.save()
            saved=journal.target.read_text()
            self.assertIn('AUDIO_IN_PROGRESS',saved)
            self.assertNotIn('secret-url',saved)

    def test_download_requires_bytes_and_hash_and_preserves_evidence(self):
        data=b'fixed audio fixture'
        reply={'status':200,'content_type':'audio/mp4'}
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(reply['status']); self.send_header('Content-Type',reply['content_type']); self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
            def log_message(self,*args): pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                target=Path(directory)/'audio.m4a'; journal=sheet_jobs.Journal(Path(directory)/'events.json')
                audio={'url':f'http://127.0.0.1:{server.server_port}/audio?secret=not-recorded','byteSize':len(data),'sha256':hashlib.sha256(data).hexdigest(),'durationMs':1234}
                for changes in ({'sha256':'0'*64},{'byteSize':len(data)+1},{'byteSize':len(data)-1},{'durationMs':-1}):
                    with self.subTest(changes=changes),self.assertRaises(sheet_jobs.JourneyFailure):
                        sheet_jobs.verify_download({**audio,**changes},target,journal,0)
                for change in ({'status':206},{'content_type':'text/plain'}):
                    reply.update(change)
                    with self.subTest(change=change),self.assertRaises(sheet_jobs.JourneyFailure):
                        sheet_jobs.verify_download(audio,target,journal,0)
                    reply.update(status=200,content_type='audio/mp4')
                self.assertEqual(journal.events,[])
                sheet_jobs.verify_download(audio,target,journal,0)
                self.assertEqual(target.read_bytes(),data)
                self.assertEqual(journal.events[0]['sha256'],audio['sha256'])
                self.assertNotIn('not-recorded',json.dumps(journal.events))
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_malformed_accepted_json_is_an_ambiguous_failure(self):
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                self.send_response(202); self.end_headers(); self.wfile.write(b'{broken')
            def log_message(self,*args): pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                recorder=sheet_jobs.Recorder(); client=sheet_jobs.Client(f'http://127.0.0.1:{server.server_port}',recorder)
                journal=sheet_jobs.Journal(Path(directory)/'events.json')
                with self.assertRaises(sheet_jobs.JourneyFailure):
                    sheet_jobs.checked(client,journal,0,'submit','POST','/submit',[202])
                self.assertIn('HTTP 202 incomplete: JSONDecodeError',recorder.samples[0][3])
                self.assertEqual(journal.events[0]['http'],0)
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_import_multipart_preserves_the_original_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'score.pdf'; path.write_bytes(b'%PDF-1.7\x00\xff\r\nfixture')
            body,kind=sheet_jobs.import_body(path,{'notation':'STAFF','title':'fixture'})
            self.assertIn(b'name="metadata"',body)
            self.assertIn(b'name="files"',body)
            self.assertIn(path.read_bytes(),body)
            self.assertTrue(body.endswith(("--"+kind.split('boundary=')[1]+"--\r\n").encode()))


if __name__=='__main__': unittest.main()
