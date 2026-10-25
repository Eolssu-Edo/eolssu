"""Rejected text columns must not become outside musical marks."""
import unittest

import numpy as np
from PIL import Image, ImageDraw

from playback.jeonggan_margins import margin_components


class MarginRegionTests(unittest.TestCase):
    def fixture(self):
        image = Image.new('RGB', (240, 140), 'white')
        ImageDraw.Draw(image).rectangle((80, 54, 94, 73), fill='black')
        layout = {'unit_height_pixels': 120, 'cells': [
            {'id': 'j1', 'bbox': [100, 0, 100, 120], 'rows': [
                {'id': 'r1', 'bbox': [130, 55, 20, 30]}]}]}
        return np.asarray(image), layout

    def test_rejected_non_rhythmic_text_is_excluded(self):
        pixels, layout = self.fixture()
        layout['ignored_regions'] = [{'bbox': [70, 0, 30, 120],
            'reason': 'non-rhythmic header/footer geometry'}]
        self.assertEqual(margin_components(pixels, layout, 40), [])

    def test_blank_margin_region_can_contain_outside_notation(self):
        pixels, layout = self.fixture()
        layout['ignored_regions'] = [{'bbox': [70, 0, 30, 120],
            'reason': 'column has only blank/marginal ink'}]
        marks = margin_components(pixels, layout, 40)
        self.assertEqual([mark['bbox'] for mark in marks], [[80, 54, 15, 20]])

    def test_other_music_cell_interior_is_excluded(self):
        pixels, layout = self.fixture()
        layout['cells'].append({'id': 'j2', 'bbox': [0, 0, 100, 120], 'rows': []})
        self.assertEqual(margin_components(pixels, layout, 40), [])


if __name__ == '__main__':
    unittest.main()
