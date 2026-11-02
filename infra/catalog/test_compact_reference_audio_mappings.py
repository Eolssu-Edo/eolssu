import hashlib
import json
import copy
from pathlib import Path
import unittest
from unittest.mock import patch

import test_import_reference_audio as fixtures
import link_reference_audio_pitches as basic
import link_reference_audio_roles as roles
import import_reference_audio as imports


class CompactMappingTests(unittest.TestCase):
    setUp = fixtures.ReferenceAudioImportTests.setUp
    manifest = fixtures.ReferenceAudioImportTests.manifest

    def mapping(self, row=None):
        manifest = self.manifest()
        document = dict(schemaVersion=2, datasetId='danso', basis='unchanged nominal source',
            manifestSha256=hashlib.sha256(manifest.read_bytes()).hexdigest(),
            mappings=[row or dict(labelId=self.asset['labelId'], yul='중', octave=0)])
        path = self.root/'mapping.json'
        path.write_text(json.dumps(document),encoding='utf-8')
        return path,manifest,document

    def test_schema2_reconstructs_identity_from_the_exact_pinned_manifest(self):
        path,manifest,_=self.mapping()
        mappings=basic.load_mappings(path,manifest)
        self.assertEqual(self.asset['assetId'],mappings[0]['assetId'])
        self.assertEqual(self.asset['sha256'],mappings[0]['sha256'])
        self.assertEqual('unchanged nominal source',mappings[0]['basis'])

    def test_bad_schema_pin_dataset_rows_and_targets_are_rejected_before_output(self):
        path,manifest,original=self.mapping()
        changes=[{'schemaVersion':1},{'schemaVersion':True},{'manifestSha256':None},{'manifestSha256':'0'*64},
                 {'datasetId':'unknown'},{'datasetId':'sogeum'},{'unexpected':True},{'basis':' '},{'mappings':[]}]
        bad_rows=[dict(labelId='missing',yul='중',octave=0),dict(labelId=self.asset['labelId'],yul='중',octave=True),
                  dict(labelId=self.asset['labelId'],yul='중',octave=3),dict(labelId=self.asset['labelId'],yul='?',octave=0),
                  dict(labelId=self.asset['labelId'],yul='중',octave=0,assetId=self.asset['assetId'])]
        changes += [{'mappings':[row]} for row in bad_rows]
        changes += [{'mappings':original['mappings']*2}]
        for index,change in enumerate(changes):
            path.write_text(json.dumps(dict(original,**change)),encoding='utf-8')
            output=self.root/f'bad-{index}.sql'
            with self.subTest(change=change):
                self.assertEqual(1,basic.main(['--manifest',str(manifest),'--mapping',str(path),'--output',str(output)]))
                self.assertFalse(output.exists())

    def test_duplicate_json_keys_at_file_and_target_levels_are_rejected(self):
        path,manifest,_=self.mapping()
        raw=path.read_text(encoding='utf-8')
        for changed in (raw.replace('"schemaVersion": 2','"schemaVersion": 2, "schemaVersion": 2'),
                        raw.replace('"octave": 0','"octave": 0, "octave": 1')):
            path.write_text(changed,encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'Duplicate JSON key'):basic.load_mappings(path,manifest)

    def test_changed_manifest_metadata_or_file_cannot_reuse_the_pin(self):
        path,manifest,_=self.mapping()
        original=manifest.read_bytes()
        document=json.loads(original)
        document['assets'][0]['metadata']['label']['evidence']={'changed':True}
        manifest.write_text(json.dumps(document),encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'Manifest SHA-256'):basic.load_mappings(path,manifest)
        manifest.write_bytes(original)
        (self.root/'clip.m4a').write_bytes(b'changed audio')
        with self.assertRaisesRegex(ValueError,'file integrity'):basic.load_mappings(path,manifest)

    def test_hash_and_parsing_use_one_manifest_read(self):
        _,manifest,document=self.mapping()
        original=manifest.read_bytes()
        # If a second read is introduced, the content no longer matches the checked snapshot.
        with patch.object(Path,'read_bytes',side_effect=[original,b'{}']) as reads:
            assets=imports.load_assets(manifest,expected_sha256=document['manifestSha256'])
        self.assertEqual(1,reads.call_count)
        self.assertEqual(self.asset['metadata'],assets[0]['metadata'])

    def test_role_shape_must_exactly_cover_source_sequence_and_gesture(self):
        self.asset['category']=self.asset['metadata']['label']['category']='시김새'
        label=self.asset['metadata']['label']
        anchor=dict(register='평취',yulmyeong='중',f0Hz=400.0,windowsSeconds=[[0.1,0.2]])
        label.update(startSeconds=0,endSeconds=1,evidence={'labelF0Hz':400.0,'sequenceDecision':{
            'orderedAnchors':[anchor,copy.deepcopy(anchor)],'labelAnchorIndex':1}})
        row=dict(labelId=self.asset['labelId'],label=dict(yul='중',octave=0),sequence=[dict(yul='중',octave=0)]*2)
        path,manifest,document=self.mapping(row)
        self.assertEqual(3,len(roles.load_mappings(path,manifest)[0]['relations']))
        for change in ({'sequence':[]},{'sequence':[row['label']]},{'sequence':row['sequence']+[row['label']]},
                       {'destination':row['label']},{'sequence':[row['label'],dict(yul='중',octave=1)]}):
            changed=copy.deepcopy(document);changed['mappings']=[dict(row,**change)]
            path.write_text(json.dumps(changed),encoding='utf-8')
            with self.subTest(change=change),self.assertRaises(ValueError):roles.load_mappings(path,manifest)
        del label['evidence']['sequenceDecision']
        label['gesture']={'lowerAnchor':anchor,'destination':copy.deepcopy(anchor),'repeatedRises':2}
        row=dict(labelId=self.asset['labelId'],label=dict(yul='중',octave=0),lowerAnchor=dict(yul='중',octave=0),destination=dict(yul='중',octave=0))
        path,manifest,document=self.mapping(row)
        self.assertEqual(['LABEL','LOWER_ANCHOR','DESTINATION'],[r['role'] for r in roles.load_mappings(path,manifest)[0]['relations']])
        for field in ('lowerAnchor','destination'):
            changed=copy.deepcopy(document);del changed['mappings'][0][field]
            path.write_text(json.dumps(changed),encoding='utf-8')
            with self.assertRaises(ValueError):roles.load_mappings(path,manifest)


if __name__=='__main__':
    unittest.main()
