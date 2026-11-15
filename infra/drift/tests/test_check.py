import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

HERE = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('drift_check', HERE / 'check.py')
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


def job_xml(script):
    escaped = script.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    return (f'<flow-definition><definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition">'
            f'<script>{escaped}</script></definition></flow-definition>').encode()


class FakeServer:
    """Answers the two read-only commands check.py sends: sha256sum and cat."""

    def __init__(self, files):
        self.files = files
        self.commands = []

    def __call__(self, argv, capture_output, timeout):
        command = argv[-1]
        self.commands.append(command)
        if command.startswith('sudo -n sha256sum'):
            out = ''.join(f'{hashlib.sha256(d).hexdigest()}  {p}\n' for p, d in self.files.items() if p in command)
            return SimpleNamespace(returncode=0, stdout=out.encode(), stderr=b'')
        path = command.split('-- ', 1)[1].strip("'")
        if path in self.files:
            return SimpleNamespace(returncode=0, stdout=self.files[path], stderr=b'')
        return SimpleNamespace(returncode=1, stdout=b'', stderr=b'No such file')


class CheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'a.py').write_bytes(b'print(1)\n')
        (self.root / 'site.template').write_bytes(b'server_name __TLS_DOMAIN__;\n')
        (self.root / 'job.Jenkinsfile').write_bytes(b'pipeline { agent any }\n')

    def tearDown(self):
        self.tmp.cleanup()

    def run_check(self, entries, files):
        server = FakeServer(files)
        rows = check.check({'hosts': {'edo1': entries}}, run=server, root=self.root)
        return {r[1]: r[3] for r in rows}, server

    def test_match_drift_and_missing(self):
        statuses, server = self.run_check(
            [{'path': '/opt/a.py', 'source': 'a.py'}, {'path': '/opt/b.py', 'source': 'a.py'},
             {'path': '/opt/c.py', 'source': 'a.py'}],
            {'/opt/a.py': b'print(1)\n', '/opt/b.py': b'print(2)\n'})
        self.assertEqual(statuses, {'/opt/a.py': 'match', '/opt/b.py': 'drift', '/opt/c.py': 'missing'})
        self.assertTrue(all(c.startswith(('sudo -n sha256sum', 'sudo -n cat')) for c in server.commands))

    def test_rendered_template_and_text_compare(self):
        statuses, _ = self.run_check(
            [{'path': '/etc/site.conf', 'source': 'site.template', 'render': {'__TLS_DOMAIN__': 'example.test'}},
             {'path': '/opt/crlf.py', 'source': 'a.py', 'compare': 'text'}],
            {'/etc/site.conf': b'server_name example.test;\n', '/opt/crlf.py': b'print(1)\r\n\n'})
        self.assertEqual(statuses, {'/etc/site.conf': 'match', '/opt/crlf.py': 'match'})

    def test_inline_jenkins_job(self):
        files = {f'{check.JOBS}/web/config.xml': job_xml('pipeline { agent any }\r\n'),
                 f'{check.JOBS}/old/config.xml': job_xml('pipeline { agent { label "x" } }')}
        statuses, _ = self.run_check(
            [{'job': 'web', 'source': 'job.Jenkinsfile'}, {'job': 'old', 'source': 'job.Jenkinsfile'},
             {'job': 'gone', 'source': 'job.Jenkinsfile'}], files)
        self.assertEqual(statuses, {'jenkins:web': 'match', 'jenkins:old': 'drift', 'jenkins:gone': 'missing'})

    def test_scm_job_without_inline_script_is_missing(self):
        xml = b'<flow-definition><definition class="CpsScmFlowDefinition"><scriptPath>Jenkinsfile</scriptPath></definition></flow-definition>'
        statuses, _ = self.run_check([{'job': 'blog', 'source': 'job.Jenkinsfile'}],
                                     {f'{check.JOBS}/blog/config.xml': xml})
        self.assertEqual(statuses, {'jenkins:blog': 'missing'})

    def test_manifest_sources_exist(self):
        manifest = json.loads((HERE / 'manifest.json').read_text())
        repo = HERE.parents[1]
        for host, entries in manifest['hosts'].items():
            for e in entries:
                self.assertTrue((repo / e['source']).is_file(), f"{host}: {e['source']}")
                self.assertTrue(('path' in e) != ('job' in e), e)


if __name__ == '__main__':
    unittest.main()
