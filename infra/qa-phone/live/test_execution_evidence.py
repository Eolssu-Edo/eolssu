import asyncio
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

import httpx
from execution_evidence import ExecutionEvidence


class ExecutionEvidenceTest(unittest.TestCase):
    def test_only_this_run_http_server_ai_and_queried_db_records_are_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'runtime').mkdir()
            backend = root / 'runtime' / 'backend.log'
            backend.write_text('old execution\n', encoding='utf8')
            runtime = SimpleNamespace(work=root)
            audit = ExecutionEvidence(runtime)
            request = httpx.Request('POST', 'http://127.0.0.1:18181/api/practice-sessions/12/recording',
                                    headers={'Authorization': 'Bearer never', 'Cookie': 'never'}, json={'password': 'never'})
            asyncio.run(audit.request(request))
            response = httpx.Response(202, request=request, headers={'Set-Cookie': 'never'})
            asyncio.run(audit.response(response))
            rid = request.headers['X-QA-Request-Id']
            line = {'kind': 'request_received', 'requestId': rid, 'password': 'never', 'target': 'recording_upload'}
            backend.write_text('old execution\nQA_EVENT_JSON=' + json.dumps(line) + '\n'
                               'QA_EVENT_JSON={"kind":"request_received","requestId":"00000000-0000-4000-8000-000000000000"}\n', encoding='utf8')
            (root / 'runtime' / 'ai-18201.log').write_text('analysis session=12 attempt=1 result=DONE ms=70\nanalysis session=99 attempt=1 result=DONE ms=20\n', encoding='utf8')
            audit.database({'sessions': {'12': {'status': 'DONE', 'attempts': 1, 'evaluations': 1, 'notes': 8},
                                         '99': {'status': 'DONE', 'evaluations': 1}}})
            logs = audit.snapshot()
            self.assertTrue(any(row['source'] == 'test' and row.get('status') == 202 for row in logs))
            self.assertTrue(any(row['source'] == 'backend' and row.get('requestId') == rid for row in logs))
            self.assertTrue(any(row['source'] == 'ai' and row.get('sessionId') == '12' for row in logs))
            self.assertTrue(any(row['source'] == 'db' and row.get('evaluations') == 1 for row in logs))
            self.assertFalse(any(row.get('sessionId') == '99' for row in logs))
            self.assertNotIn('never', json.dumps(logs))
            self.assertEqual(audit.snapshot(), logs)


if __name__ == '__main__':
    unittest.main()
