"""Local HTTP contract checks; the stub does not measure recognition quality."""
import base64
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

from playback.recognition import RecognitionError, recognize


CONTEXT = {"pitch_mode": "sounding", "notation_octave_shift": 0,
           "tuning_a_hz": 440, "style": "정악", "tuning_description": "A440 test"}
CANDIDATE = {"title": "한 음", "quarter_bpm": 60,
             "notes": [{"id": "n1", "pitch": 75, "duration": "1"}],
             "regions": [{"note_id": "n1", "bbox": [.1, .2, .2, .1]}],
             "warnings": []}
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aAbEAAAAASUVORK5CYII=")


class RecognitionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                cls.request_path = self.path
                cls.request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                self.send_response(cls.status)
                self.send_header("Content-Type", "application/json")
                if cls.location:
                    self.send_header("Location", cls.location)
                self.end_headers()
                self.wfile.write(cls.body)

            def log_message(self, *args):
                pass

        cls.server = HTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base_url = "http://127.0.0.1:" + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.image = Path(self.temp.name) / "score.png"
        self.image.write_bytes(PNG)
        type(self).status, type(self).location = 200, None
        self.respond(deepcopy(CANDIDATE))

    def respond(self, candidate):
        type(self).body = json.dumps({"model": "vision-test", "message": {
            "role": "assistant", "content": json.dumps(candidate, ensure_ascii=False)}}).encode()

    def run_recognizer(self, notation="staff", context=None, **options):
        return recognize(self.image, notation, deepcopy(context or CONTEXT),
                         model="vision-test", base_url=self.base_url, **options)

    def test_staff_image_is_sent_and_result_is_always_an_unreviewed_draft(self):
        result = self.run_recognizer()
        self.assertEqual(self.request_path, "/api/chat")
        self.assertEqual(base64.b64decode(self.request["messages"][-1]["images"][0]), PNG)
        self.assertFalse(self.request["stream"])
        self.assertEqual(self.request["format"]["type"], "object")
        self.assertEqual(self.request["options"]["temperature"], 0)
        self.assertEqual(result["score"]["context"], CONTEXT)
        self.assertEqual(result["score"]["notes"][0]["pitch"], 75)
        self.assertEqual(result["score"]["source"]["review_status"], "machine-transcribed-unverified")
        self.assertEqual(len(result["score"]["source"]["image_sha256"]), 64)
        self.assertEqual(result["regions"], CANDIDATE["regions"])
        self.assertTrue(result["warnings"])

    def test_jeonggan_uses_explicit_reference_and_register(self):
        context = {**CONTEXT, "pitch_mode": "yul", "huangjong_midi": 63,
                   "reading_direction": "top-to-bottom-right-to-left"}
        candidate = deepcopy(CANDIDATE)
        candidate["notes"][0]["pitch"] = {"yul": "黃", "register": 1}
        self.respond(candidate)
        result = self.run_recognizer("jeonggan", context)
        self.assertEqual(result["score"]["context"]["huangjong_midi"], 63)
        self.assertNotIn("reading_direction", result["score"]["context"])
        prompt = self.request["messages"][-1]["content"]
        self.assertIn("top-to-bottom-right-to-left", prompt)
        self.assertIn("register", prompt)

    def test_missing_yul_reference_fails_before_request(self):
        with self.assertRaisesRegex(RecognitionError, "huangjong"):
            self.run_recognizer("jeonggan", {**CONTEXT, "pitch_mode": "yul"})

    def test_missing_model_or_external_endpoint_is_rejected(self):
        for endpoint in ("https://127.0.0.1:11434", "http://example.com", "http://127.0.0.1/remote",
                         "http://user:secret@127.0.0.1", "http://127.0.0.1?x=1"):
            with self.subTest(endpoint=endpoint), self.assertRaises(RecognitionError):
                recognize(self.image, "staff", CONTEXT, model="vision-test", base_url=endpoint)
        with self.assertRaisesRegex(RecognitionError, "model"):
            recognize(self.image, "staff", CONTEXT, model="")

    def test_remote_redirect_is_never_followed(self):
        type(self).status, type(self).location = 302, "https://example.com"
        with self.assertRaisesRegex(RecognitionError, "302"):
            self.run_recognizer()

    def test_unavailable_provider_is_reported_without_fallback_notes(self):
        with patch("playback.recognition.build_opener") as opener:
            opener.return_value.open.side_effect = OSError("connection refused")
            with self.assertRaisesRegex(RecognitionError, "unavailable"):
                self.run_recognizer()

    def test_provider_error_status_and_invalid_envelope(self):
        type(self).status = 500
        with self.assertRaisesRegex(RecognitionError, "500"):
            self.run_recognizer()
        type(self).status, type(self).body = 200, b'{"message":{}}'
        with self.assertRaisesRegex(RecognitionError, "content"):
            self.run_recognizer()

    def test_response_size_is_bounded(self):
        with patch("playback.recognition.MAX_RESPONSE_BYTES", 100):
            with self.assertRaisesRegex(RecognitionError, "large"):
                self.run_recognizer()

    def test_malformed_candidate_does_not_become_a_score(self):
        for update in ({"extra": True}, {"quarter_bpm": float("nan")}, {"notes": "notes"}):
            with self.subTest(update=update):
                candidate = {**deepcopy(CANDIDATE), **update}
                self.respond(candidate)
                with self.assertRaises(RecognitionError):
                    self.run_recognizer()

    def test_regions_must_cover_known_ids_and_stay_in_image(self):
        for region in ({"note_id": "unknown", "bbox": [0, 0, 1, 1]},
                       {"note_id": "n1", "bbox": [.9, 0, .5, 1]},
                       {"note_id": "n1", "bbox": [0, 0, 0, 1]}):
            candidate = deepcopy(CANDIDATE)
            candidate["regions"] = [region]
            self.respond(candidate)
            with self.assertRaises(RecognitionError):
                self.run_recognizer()

    def test_unknown_glyph_can_yield_no_notes_but_must_warn(self):
        candidate = {**deepcopy(CANDIDATE), "notes": [], "regions": [],
                     "warnings": ["Unreadable symbol at top-right; transcription incomplete"]}
        self.respond(candidate)
        self.assertEqual(self.run_recognizer()["score"]["notes"], [])
        candidate["warnings"] = []
        self.respond(candidate)
        with self.assertRaisesRegex(RecognitionError, "warning"):
            self.run_recognizer()

    def test_duration_and_note_id_errors_are_rejected(self):
        for update in ({"duration": "0"}, {"duration": "x"}, {"id": ""}, {"pitch": 999}, {"unknown": True}):
            candidate = deepcopy(CANDIDATE)
            candidate["notes"][0].update(update)
            self.respond(candidate)
            with self.assertRaises(RecognitionError):
                self.run_recognizer()


if __name__ == "__main__":
    unittest.main()
