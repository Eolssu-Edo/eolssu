"""Trace outcomes must describe finished work, not a pending analysis."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch


class AiTraceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        route = SimpleNamespace(path='/v1/analyses', dependant=SimpleNamespace(call=None))
        self.api = SimpleNamespace(app=SimpleNamespace(routes=[route]), analyze=Mock(return_value={'score': 0}), log=Mock())
        spec = importlib.util.spec_from_file_location('qa_ai_trace_test', Path(__file__).with_name('qa_ai.py'))
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {'ai_service': SimpleNamespace(api=self.api),
                                      'fastapi': SimpleNamespace(HTTPException=RuntimeError)}):
            spec.loader.exec_module(self.module)
        self.module.gate = Path(temporary.name) / 'absent-fault-gate.json'
        self.observe = route.dependant.call
        self.request = SimpleNamespace(session_id=3, attempt=1)

    def events(self):
        return [json.loads(call.args[1]) for call in self.api.log.info.call_args_list]

    def test_start_is_unclassified_and_success_keeps_the_actual_result(self):
        fetch = object()
        result = self.observe(self.request, fetch)
        self.assertIs(result, self.api.analyze.return_value)
        self.api.analyze.assert_called_once_with(self.request, fetch)
        started, finished = self.events()
        self.assertEqual(started['kind'], 'analysis_started')
        self.assertNotIn('outcome', started)
        self.assertEqual((finished['kind'], finished['outcome']), ('analysis_finished', 'DONE'))
        self.assertEqual((finished['sessionId'], finished['attempt']), ('3', 1))

    def test_failure_is_reported_only_after_the_analysis_raises(self):
        self.api.analyze.side_effect = RuntimeError('analysis failed')
        with self.assertRaisesRegex(RuntimeError, 'analysis failed'):
            self.observe(self.request, object())
        started, finished = self.events()
        self.assertNotIn('outcome', started)
        self.assertEqual((finished['kind'], finished['outcome']), ('analysis_finished', 'FAILED'))


if __name__ == '__main__':
    unittest.main()
