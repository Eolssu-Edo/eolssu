import importlib.util,json,pathlib,tempfile,unittest
SPEC=importlib.util.spec_from_file_location('mr_sync',pathlib.Path(__file__).parents[1]/'sync-mr-details.py')
syncer=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(syncer)
class MetadataTest(unittest.TestCase):
 def index(self,root):
  value={'builds':[{'id':'original'}],'mergeRequests':{'a'*40:328},'mergeRequestDetails':{'12':{'authorName':'기존','title':'보존'}}}
  (root/'index.json').write_text(json.dumps(value),encoding='utf-8');return value
 def test_actual_author_title_and_concurrent_archive_are_preserved(self):
  with tempfile.TemporaryDirectory(prefix='qa498-mr-test-') as temp:
   root=pathlib.Path(temp);original=self.index(root);calls=[]
   def fetch(ids,token):
    calls.append((ids,token));current=json.loads((root/'index.json').read_text());current['builds'].append({'id':'published-during-fetch'});current['mergeRequests']['b'*40]=329
    (root/'index.json').write_text(json.dumps(current),encoding='utf-8')
    return [{'iid':328,'title':'새 MR 제목','author':{'name':'실제 작성자'}}]
   self.assertEqual(syncer.sync(root,'fixture-token',fetch),1)
   result=json.loads((root/'index.json').read_text());self.assertEqual(calls,[([328],'fixture-token')])
   self.assertEqual(len(result['builds']),2);self.assertEqual(result['mergeRequests']['b'*40],329)
   self.assertEqual(result['mergeRequestDetails']['328'],{'authorName':'실제 작성자','title':'새 MR 제목'})
   self.assertEqual(result['mergeRequestDetails']['12'],original['mergeRequestDetails']['12'])
 def test_invalid_missing_and_failed_responses_leave_index_untouched(self):
  for response in [[],[{'iid':329,'title':'wrong MR','author':{'name':'a'}}],[{'iid':328,'title':'x','author':{'name':'a\nb'}}]]:
   with self.subTest(response=response),tempfile.TemporaryDirectory(prefix='qa498-mr-bad-') as temp:
    root=pathlib.Path(temp);self.index(root);before=(root/'index.json').read_bytes()
    with self.assertRaises(Exception):syncer.sync(root,'fixture',lambda ids,token:response)
    self.assertEqual((root/'index.json').read_bytes(),before)
  with tempfile.TemporaryDirectory(prefix='qa498-mr-fail-') as temp:
   root=pathlib.Path(temp);self.index(root);before=(root/'index.json').read_bytes()
   def failure(ids,token):raise OSError('offline')
   with self.assertRaises(OSError):syncer.sync(root,'fixture',failure)
   self.assertEqual((root/'index.json').read_bytes(),before)
 def test_transport_uses_read_header_without_token_in_url_and_rejects_redirects(self):
  import http.server,threading,urllib.error
  seen=[]
  class Handler(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_GET(self):
    seen.append((self.path,self.headers.get('PRIVATE-TOKEN')))
    if self.path.startswith('/redirect'):
     self.send_response(302);self.send_header('Location','/stolen');self.end_headers();return
    self.send_response(200);self.end_headers();self.wfile.write(b'[]')
  with http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler) as server:
   thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
   try:
    base='http://127.0.0.1:'+str(server.server_port)
    self.assertEqual(syncer.fetch_batch([328],'fixture-secret',base+'/mrs'),[])
    self.assertEqual(seen[0][1],'fixture-secret');self.assertNotIn('fixture-secret',seen[0][0]);self.assertIn('iids%5B%5D=328',seen[0][0])
    with self.assertRaises(urllib.error.HTTPError):syncer.fetch_batch([328],'fixture-secret',base+'/redirect')
    self.assertEqual(len(seen),2)
   finally:server.shutdown();thread.join()
if __name__=='__main__':unittest.main()
