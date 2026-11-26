import importlib.util,json,pathlib,tempfile,unittest
SPEC=importlib.util.spec_from_file_location('mr_sync',pathlib.Path(__file__).parents[1]/'sync-mr-details.py')
syncer=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(syncer)
def pr(number,title,login='qa-user',name=None):
 return {'number':number,'title':title,'author':{'login':login,'name':name} if login else None}
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
    return [pr(328,'새 PR 제목',name='실제 작성자')]
   self.assertEqual(syncer.sync(root,'fixture-token',fetch),1)
   result=json.loads((root/'index.json').read_text());self.assertEqual(calls,[([328],'fixture-token')])
   self.assertEqual(len(result['builds']),2);self.assertEqual(result['mergeRequests']['b'*40],329)
   self.assertEqual(result['mergeRequestDetails']['328'],{'authorName':'실제 작성자','title':'새 PR 제목'})
   self.assertEqual(result['mergeRequestDetails']['12'],original['mergeRequestDetails']['12'])
 def test_author_falls_back_to_login_then_deleted_account(self):
  with tempfile.TemporaryDirectory(prefix='qa1290-author-') as temp:
   root=pathlib.Path(temp);value=self.index(root);value['mergeRequests']={'a'*40:1,'b'*40:2}
   (root/'index.json').write_text(json.dumps(value),encoding='utf-8')
   self.assertEqual(syncer.sync(root,'fixture',lambda ids,token:[pr(1,'로그인만',login='eunseo',name=''),pr(2,'삭제된 계정',login=None)]),2)
   details=json.loads((root/'index.json').read_text())['mergeRequestDetails']
   self.assertEqual(details['1'],{'authorName':'eunseo','title':'로그인만'});self.assertEqual(details['2']['authorName'],'ghost')
 def test_mr_metadata_is_requested_in_batches_of_at_most_ten(self):
  with tempfile.TemporaryDirectory(prefix='qa624-mr-batch-') as temp:
   root=pathlib.Path(temp);value=self.index(root)
   value['mergeRequests']={f'{iid:040x}':iid for iid in range(1,24)}
   (root/'index.json').write_text(json.dumps(value),encoding='utf-8')
   calls=[]
   def fetch(ids,token):
    calls.append(ids)
    return [pr(iid,f'PR {iid}') for iid in ids]
   self.assertEqual(syncer.sync(root,'fixture',fetch),23)
   self.assertEqual(calls,[list(range(1,11)),list(range(11,21)),list(range(21,24))])
   self.assertEqual(len(json.loads((root/'index.json').read_text())['mergeRequestDetails']),23)
 def test_invalid_missing_and_failed_responses_leave_index_untouched(self):
  for response in [[],[pr(329,'wrong PR')],[pr(328,'x',name='a\nb')],[None]]:
   with self.subTest(response=response),tempfile.TemporaryDirectory(prefix='qa498-mr-bad-') as temp:
    root=pathlib.Path(temp);self.index(root);before=(root/'index.json').read_bytes()
    with self.assertRaises(Exception):syncer.sync(root,'fixture',lambda ids,token:response)
    self.assertEqual((root/'index.json').read_bytes(),before)
  with tempfile.TemporaryDirectory(prefix='qa498-mr-fail-') as temp:
   root=pathlib.Path(temp);self.index(root);before=(root/'index.json').read_bytes()
   def failure(ids,token):raise OSError('offline')
   with self.assertRaises(OSError):syncer.sync(root,'fixture',failure)
   self.assertEqual((root/'index.json').read_bytes(),before)
 def test_transport_posts_github_graphql_with_bearer_header_and_rejects_redirects(self):
  import http.server,threading,urllib.error
  seen=[]
  class Handler(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_POST(self):
    body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
    seen.append((self.path,self.headers.get('Authorization'),self.headers.get('User-Agent'),body['query']))
    if self.path.startswith('/redirect'):
     self.send_response(307);self.send_header('Location','/stolen');self.end_headers();return
    payload={'data':{'repository':{'p328':pr(328,'제목')}}}
    if self.path.startswith('/errors'):payload={'data':{'repository':{'p328':None}},'errors':[{'type':'NOT_FOUND'}]}
    self.send_response(200);self.end_headers();self.wfile.write(json.dumps(payload).encode())
  with http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler) as server:
   thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
   try:
    base='http://127.0.0.1:'+str(server.server_port)
    self.assertEqual(syncer.fetch_batch([328],'fixture-secret',base+'/graphql'),[pr(328,'제목')])
    path,auth,agent,query=seen[0]
    self.assertEqual(auth,'Bearer fixture-secret');self.assertNotIn('fixture-secret',path+query);self.assertTrue(agent)
    self.assertIn('repository(owner:"team-edo",name:"eolssu")',query);self.assertIn('p328:pullRequest(number:328)',query)
    with self.assertRaises(ValueError):syncer.fetch_batch([328],'fixture-secret',base+'/errors')
    with self.assertRaises(urllib.error.HTTPError):syncer.fetch_batch([328],'fixture-secret',base+'/redirect')
    self.assertEqual(len(seen),3)
   finally:server.shutdown();thread.join()
 def test_default_endpoint_is_github_graphql(self):
  self.assertEqual(syncer.API,'https://api.github.com/graphql')
if __name__=='__main__':unittest.main()
