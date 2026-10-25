import unittest
from fractions import Fraction
from playback.jeonggan_interpret import expand_symbols
from playback.recognition import RecognitionError


def page(*tokens, tuple_count=None):
    atoms=[]
    for i,(kind,token) in enumerate(tokens):
        atoms.append({'id':f'a{i}','bbox':[10+i*10,10,8,8],'kind':kind,'token':token,'glyph':token})
    return {'image_size':[200,200],'cells':[{'id':'j1','units':1,'rows':[
        {'id':'j1-r1','relative_y':.5,'bbox':[10,10,100,20],'atoms':atoms,'tuple_count':tuple_count}]}]}


class InterpretTests(unittest.TestCase):
    def test_long_and_half_lengths_fill_row_in_three_to_one_ratio(self):
        layout=page(('duration','duration_long'),('pitch','high:黃'),('relative','upper1'),('duration','duration_half'))
        notes,_,_=expand_symbols(layout,Fraction(2))
        self.assertEqual([n['duration'] for n in notes],['3/2','1/2'])
        self.assertEqual(notes[1]['pitch'],{'yul':'太','register':1})

    def test_incomplete_length_pair_fails(self):
        with self.assertRaises(RecognitionError):
            expand_symbols(page(('duration','duration_long'),('pitch','high:黃')),Fraction(1))

    def test_i_anchor_is_middle_pitch_and_secondary_turn_decorates_final_return(self):
        notes,_,_=expand_symbols(page(('pitch','high:無'),('ornament','long_upper'),('ornament','turn_upper')),Fraction(1))
        self.assertEqual([n['pitch']['yul'] for n in notes],list('林無林無林'))
        self.assertEqual(sum(Fraction(n['duration']) for n in notes),1)
        self.assertEqual(notes[1]['duration'],'1/2')

    def test_h_and_triplet_group_respects_explicit_five_marker(self):
        layout=page(('pitch','high:黃'))
        second=page(('relative','h'),('pitch','high:黃'),('ornament','triplet_upper'),tuple_count=5)['cells'][0]
        second['id']='j2';second['rows'][0]['id']='j2-r1';layout['cells'].append(second)
        notes,_,_=expand_symbols(layout,Fraction(1))
        self.assertEqual([n['duration'] for n in notes[1:]],['1/5']*5)

    def test_unknown_never_omitted(self):
        with self.assertRaises(RecognitionError):
            expand_symbols(page(('unknown','unknown')),Fraction(1))

    def test_sustain_extends_same_row_note_not_previous_cell(self):
        layout=page(('pitch','base:太'))
        layout['cells'].append(page(('pitch','base:黃'),('sustain','sustain'))['cells'][0])
        notes,_,_=expand_symbols(layout,Fraction(1))
        self.assertEqual([n['duration'] for n in notes],['1','1'])

    def test_turn_graces_precede_long_final_anchor(self):
        notes,_,_=expand_symbols(page(('pitch','base:黃'),('ornament','turn_upper')),Fraction(1))
        self.assertEqual([n['duration'] for n in notes],['1/16','1/16','7/8'])

    def test_sustain_with_tuplet_rejected_before_double_counting(self):
        layout=page(('pitch','high:仲'))
        layout['cells'].append(page(('sustain','sustain'),('relative','h'),('pitch','high:仲'),
                                    ('ornament','triplet_upper'),tuple_count=5)['cells'][0])
        with self.assertRaises(RecognitionError):expand_symbols(layout,Fraction(1))


if __name__=='__main__': unittest.main()
