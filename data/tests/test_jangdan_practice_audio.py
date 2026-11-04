from fractions import Fraction
import unittest

from data.src.audio.jangdan_practice_audio import atempo_factors, gutgeori_roll_frames


class PracticeAudioTest(unittest.TestCase):
    def test_roll_spans_follow_rounded_song_bpm_instead_of_catalog_bpm(self):
        # A gudgeori roll occupies one quarter note at the song's QUARTER BPM.
        self.assertEqual([44847, 33494, 26727, 24275, 22235],
                         [gutgeori_roll_frames(bpm) for bpm in (59, 79, 99, 109, 119)])
        with self.assertRaises(ValueError):
            gutgeori_roll_frames(66)

    def test_tempo_chain_does_not_use_sample_skipping_factors_above_two(self):
        factors = atempo_factors(Fraction(47, 10))
        product = Fraction(1)
        for factor in factors:
            self.assertGreaterEqual(factor, Fraction(1, 2))
            self.assertLessEqual(factor, 2)
            product *= factor
        self.assertEqual(Fraction(47, 10), product)
        self.assertEqual([Fraction(1)], atempo_factors(Fraction(1)))


if __name__ == '__main__':
    unittest.main()
