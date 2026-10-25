import unittest
from playback.evaluate import compare, summarize, expanded_events


class EvaluationTests(unittest.TestCase):
    def score(self,notes):
        return {'context':{'pitch_mode':'sounding','notation_octave_shift':0},
                'quarter_bpm':60,'notes':[{'pitch':p,'duration':d} for p,d in notes]}

    def test_insertion_is_not_counted_as_all_following_pitch_errors(self):
        ref=self.score([(70,'3/2'),(73,'1'),(70,'1/4'),(68,'1/4')])
        pred=self.score([(71,'2'),(70,'1.5'),(73,'1'),(70,'1/4'),(68,'1/2')])
        result=compare(ref,pred)
        self.assertEqual(result['pitch_matches'],4)
        self.assertEqual(result['pitch_and_duration_matches'],3)
        self.assertEqual(result['insertions'],1)
        self.assertEqual(result['pitch_edit_distance'],1)
        self.assertEqual(result['joint_edit_distance'],2)

    def test_empty_extraction_is_zero_matches_not_success(self):
        result=compare(self.score([(82,'1')]),self.score([]))
        self.assertEqual(result['pitch_matches'],0)
        self.assertEqual(result['deletions'],1)
        self.assertEqual(result['pitch_error_rate'],1)

    def test_repeated_pitch_with_added_note_preserves_exact_rhythm_matches(self):
        result=compare(self.score([(70,'1'),(70,'2')]),self.score([(70,'1'),(70,'2'),(70,'3')]))
        self.assertEqual(result['pitch_and_duration_matches'],2)
        self.assertEqual(result['joint_edit_distance'],1)

    def test_same_ornament_count_does_not_hide_wrong_pitch_or_parent(self):
        ref=self.score([(70,'1'),(73,'1')]);pred=self.score([(70,'1'),(73,'1')])
        ref['notes'][0]['ornament']={'kind':'upper-return','pitch':73}
        pred['notes'][1]['ornament']={'kind':'upper-return','pitch':75}
        metrics=compare(ref,pred)
        self.assertEqual(metrics['reference_ornament_count'],1)
        self.assertEqual(metrics['recognized_ornament_count'],1)
        self.assertEqual(metrics['ornament_matches'],0)
        self.assertEqual(metrics['ornament_missed_or_wrong'],1)
        pred['notes'][0]['ornament']=ref['notes'][0]['ornament'].copy()
        self.assertEqual(compare(ref,pred)['ornament_matches'],1)

    def test_failed_photo_remains_in_micro_average_denominator(self):
        ref=self.score([(70,'1'),(73,'1')])
        reports=[{'status':'recognized','metrics':compare(ref,ref)},
                 {'status':'failed','metrics':compare(ref,self.score([]))}]
        result=summarize(reports)
        self.assertEqual(result['failed_images'],1)
        self.assertEqual(result['reference_main_events'],4)
        self.assertEqual(result['pitch_recall'],.5)
        self.assertEqual(result['pitch_precision'],1)

    def test_repeated_pitch_insertion_does_not_shift_ornament_to_wrong_parent(self):
        ref=self.score([(70,'1'),(70,'2')])
        pred=self.score([(70,'1'),(70,'2'),(70,'3')])
        ref['notes'][0]['ornament']={'kind':'upper-return','pitch':73}
        pred['notes'][1]['ornament']={'kind':'upper-return','pitch':73}
        result=compare(ref,pred)
        self.assertEqual(result['pitch_and_duration_matches'],2)
        self.assertEqual(result['ornament_matches'],0)

    def test_rests_are_reported_separately_from_sounding_pitch_accuracy(self):
        ref=self.score([(70,'1')]);ref['notes'].append({'rest':True,'duration':'1'})
        pred=self.score([(71,'1')]);pred['notes'].append({'rest':True,'duration':'1'})
        metrics=compare(ref,pred)
        self.assertEqual(metrics['pitch_matches'],1)
        self.assertEqual(metrics['sounding_pitch_matches'],0)
        self.assertEqual(metrics['reference_sounding_notes'],1)
        self.assertEqual(metrics['rest_and_duration_matches'],1)

    def test_explicit_notes_and_native_ornament_have_same_expanded_timeline(self):
        ref=self.score([(70,'1')])
        ref['notes'][0]['ornament']={'kind':'upper-return','pitch':73,'duration':'1/16'}
        pred=self.score([(70,'1/16'),(73,'1/16'),(70,'7/8')])
        metrics=compare(ref,pred)
        self.assertEqual(metrics['reference_expanded_events'],3)
        self.assertEqual(metrics['expanded_timed_matches'],3)
        # Representation metrics still reveal that the attached object is absent.
        self.assertEqual(metrics['ornament_matches'],0)

    def test_rest_shift_does_not_pass_exact_timed_comparison(self):
        ref=self.score([(70,'1'),(73,'1')])
        pred=self.score([(70,'1'),(73,'1')])
        pred['notes'].insert(1,{'rest':True,'duration':'1/2'})
        metrics=compare(ref,pred)
        self.assertEqual(metrics['expanded_pitch_and_duration_matches'],2)
        self.assertEqual(metrics['expanded_timed_matches'],1)

    def test_ties_merge_but_repeated_notes_remain_distinct(self):
        tied=self.score([(70,'1'),(70,'1')])
        tied['notes'][0]['tie']='start';tied['notes'][1]['tie']='stop'
        ref=self.score([(70,'2')])
        self.assertEqual(compare(ref,tied)['expanded_timed_matches'],1)
        self.assertEqual(compare(ref,self.score([(70,'1'),(70,'1')]))['expanded_timed_matches'],0)

    def test_grace_expansion_and_failed_candidate_denominator(self):
        ref=self.score([(70,'1')])
        ref['notes'][0]['ornament']={'kind':'grace-before','pitch':73,'duration':'1/16'}
        self.assertEqual(expanded_events(ref),[(73,'0','1/16'),(70,'1/16','15/16')])
        metric=compare(ref,self.score([]))
        totals=summarize([{'status':'failed','metrics':metric}])
        self.assertEqual(totals['reference_expanded_events'],2)
        self.assertEqual(totals['expanded_timed_recall'],0)
