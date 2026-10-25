"""Geometry and interpretation contract tests; stub OCR is not model accuracy."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image, ImageDraw

from playback.jeonggan import recognize_jeonggan
from playback.recognition import RecognitionError
from playback.score import resolve_pitch


CONTEXT = {"pitch_mode": "yul", "notation_octave_shift": 0, "huangjong_midi": 63,
           "tuning_a_hz": 440, "style": "정악", "tuning_description": "A440 test"}


def reply(token, glyph="test", uncertain=False, done_reason="stop"):
    return {"message": {"content": json.dumps({"token": token, "glyph": glyph,
                                               "uncertain": uncertain}, ensure_ascii=False)},
            "done_reason": done_reason}


class JeongganTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.image = Path(self.temp.name) / "grid.png"
        self.trace = Path(self.temp.name) / "trace.json"

    def grid(self, columns=1, rows=2, subdivisions=None, faint=False):
        image = Image.new("RGB", (columns*120+17, rows*180+17), "white")
        draw = ImageDraw.Draw(image)
        color = (210,210,210) if faint else "black"
        for x in range(columns+1):
            draw.line((8+x*120,8,8+x*120,8+rows*180), fill=color, width=2)
        for y in range(rows+1):
            draw.line((8,8+y*180,8+columns*120,8+y*180), fill=color, width=2)
        for col in range(columns):
            for row in range(rows):
                count = (subdivisions or {}).get((col,row),1)
                for slot in range(count):
                    cy = 8+row*180+round((slot+.5)*180/count)
                    draw.rectangle((53+col*120,cy-9,83+col*120,cy+9),fill="black")
        image.save(self.image)

    def recognize(self, **kwargs):
        return recognize_jeonggan(self.image, deepcopy(CONTEXT), model="vision-test",
                                 trace_path=self.trace, **kwargs)

    def test_pitch_sustain_and_scale_ornament_expand_with_original_regions(self):
        self.grid(subdivisions={(0,1):3})
        responses=[reply("high:林", "淋"), reply("high:無", "漁"), reply("sustain", "－"), reply("h", "h")]
        with patch("playback.jeonggan._chat", side_effect=responses) as chat:
            result=self.recognize()
        notes=result["score"]["notes"]
        self.assertEqual([resolve_pitch(n["pitch"],CONTEXT) for n in notes],[82,85,82,80])
        self.assertEqual([n["duration"] for n in notes],["3/2","1","1/4","1/4"])
        self.assertEqual(len(result["regions"]),4)
        self.assertEqual(result["score"]["source"]["review_status"],"machine-transcribed-unverified")
        self.assertEqual(result["score"]["quarter_bpm"],60)
        self.assertEqual(result["score"]["source"]["model"],"vision-test")
        self.assertEqual(len(result["score"]["source"]["prompt_sha256"]),64)
        self.assertIn("BPM60", " ".join(result["score"]["assumptions"]))
        self.assertEqual(chat.call_count,4)
        self.assertTrue(all(call.kwargs["timeout"]<=120 for call in chat.call_args_list))
        trace=json.loads(self.trace.read_text(encoding="utf-8"))
        self.assertEqual(trace["glyphs"][1]["response"]["message"],responses[1]["message"])
        self.assertIn("漁",json.dumps(result["warnings"],ensure_ascii=False))

    def test_multiple_columns_read_right_to_left_top_to_bottom(self):
        self.grid(columns=2,rows=2)
        with patch("playback.jeonggan._chat",side_effect=[reply("base:"+y) for y in "黃太仲林"]):
            result=self.recognize()
        boxes=[r["bbox"] for r in result["regions"]]
        self.assertGreater(boxes[0][0],boxes[2][0])
        self.assertLess(boxes[0][1],boxes[1][1])
        self.assertEqual([n["pitch"]["yul"] for n in result["score"]["notes"]],list("黃太仲林"))

    def test_uncertain_and_unknown_are_errors_with_saved_provider_evidence(self):
        self.grid(rows=1)
        for response in (reply("unknown"),reply("base:黃",uncertain=True)):
            with self.subTest(response=response), patch("playback.jeonggan._chat",return_value=response):
                with self.assertRaisesRegex(RecognitionError,"Unknown|uncertain"):
                    self.recognize()
                saved=json.loads(self.trace.read_text(encoding="utf-8"))
                self.assertEqual(saved["glyphs"][0]["response"],response)
                self.assertIn("error",saved)

    def test_truncated_response_is_never_accepted(self):
        self.grid(rows=1)
        with patch("playback.jeonggan._chat",return_value=reply("base:黃",done_reason="length")):
            with self.assertRaisesRegex(RecognitionError,"truncated"):
                self.recognize()

    def test_duplicate_keys_and_unknown_token_are_rejected(self):
        self.grid(rows=1)
        bad={"message":{"content":'{"token":"base:黃","token":"h","glyph":"h","uncertain":false}'}}
        for response in (bad,reply("base:garbage")):
            with self.subTest(response=response),patch("playback.jeonggan._chat",return_value=response):
                with self.assertRaises(RecognitionError):
                    self.recognize()

    def test_blank_faint_and_partial_grids_fail_before_model_calls(self):
        with patch("playback.jeonggan._chat") as chat:
            Image.new("RGB",(100,100),"white").save(self.image)
            with self.assertRaises(RecognitionError): self.recognize()
            self.grid(faint=True)
            with self.assertRaises(RecognitionError): self.recognize()
            self.grid()
            with Image.open(self.image) as image: image.crop((25,0,image.width,image.height)).save(self.image)
            with self.assertRaises(RecognitionError): self.recognize()
            chat.assert_not_called()

    def test_more_than_64_glyphs_fails_before_inference(self):
        # The legacy crop path stays bounded; larger grids now use the page path.
        from playback.jeonggan import _segments
        import numpy as np
        self.grid(columns=9,rows=8)
        with patch("playback.jeonggan._chat") as chat:
            with self.assertRaisesRegex(RecognitionError,"64"):
                with Image.open(self.image) as image: _segments(np.asarray(image))
            chat.assert_not_called()

    def test_explicit_cell_length_and_tempo_are_preserved(self):
        self.grid(rows=1)
        context={**CONTEXT,"cell_quarters":"1","quarter_bpm":80}
        with patch("playback.jeonggan._chat",return_value=reply("base:黃")):
            result=recognize_jeonggan(self.image,context,model="vision-test")
        self.assertEqual(result["score"]["notes"][0]["duration"],"1")
        self.assertEqual(result["score"]["quarter_bpm"],80)
        self.assertNotIn("cell_quarters",result["score"]["context"])

    def test_conflicting_reading_direction_fails_before_model_call(self):
        self.grid(rows=1)
        with patch("playback.jeonggan._chat") as chat:
            with self.assertRaisesRegex(RecognitionError,"reading_direction"):
                recognize_jeonggan(self.image,{**CONTEXT,"reading_direction":"left-to-right"},model="vision-test")
            chat.assert_not_called()

    def test_service_direction_and_fractional_cell_length(self):
        from playback.recognition import recognize
        self.grid(rows=1)
        context={**CONTEXT,'reading_direction':'top-to-bottom,right-to-left','cell_quarters':'3/2'}
        with patch('playback.jeonggan._chat',return_value=reply('base:黃')):
            result=recognize(self.image,'jeonggan',context,model='vision-test',structured=True)
        self.assertEqual(result['score']['notes'][0]['duration'],'3/2')

    def test_total_time_budget_stops_before_next_request(self):
        self.grid(rows=1)
        with patch("playback.jeonggan.time.monotonic",side_effect=[0,121]), patch("playback.jeonggan._chat") as chat:
            with self.assertRaisesRegex(RecognitionError,"120-second"):
                self.recognize()
            chat.assert_not_called()

    def test_h_crosses_register_by_scale_degree_not_semitone(self):
        self.grid(rows=2)
        with patch("playback.jeonggan._chat",side_effect=[reply("base:黃"),reply("h")]):
            result=self.recognize()
        self.assertEqual([resolve_pitch(n["pitch"],CONTEXT) for n in result["score"]["notes"]],[63,61,58])

    def test_sustain_without_previous_pitch_and_unsupported_h_scale_fail(self):
        self.grid(rows=1)
        with patch("playback.jeonggan._chat",return_value=reply("sustain")):
            with self.assertRaisesRegex(RecognitionError,"preceding"):
                self.recognize()
        self.grid(rows=2)
        with patch("playback.jeonggan._chat",side_effect=[reply("base:大"),reply("h")]):
            with self.assertRaisesRegex(RecognitionError,"five-tone"):
                self.recognize()


if __name__=="__main__":
    unittest.main()
