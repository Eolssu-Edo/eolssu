import copy
import json
import os
from pathlib import Path
import unittest

import test_import_reference_audio as fixtures
import link_reference_audio_roles as roles
import test_import_reference_audio_mysql as mysql_fixture
from test_pitch_mapping_data import annotations, ROOT


class RoleMappingTests(unittest.TestCase):
    setUp = fixtures.ReferenceAudioImportTests.setUp
    manifest = fixtures.ReferenceAudioImportTests.manifest

    def mapping_file(self):
        self.asset['category'] = self.asset['metadata']['label']['category'] = '시김새'
        row = {k:self.asset[k] for k in roles.IDENTITY}
        row.update(metadataSha256=roles.metadata_digest(self.asset['metadata']),relations=[dict(
            role='LABEL',anchorIndex=0,evidencePath='/label',register='평취',yulmyeong='중',yul='중',octave=0)])
        path=self.root/'roles.json'
        path.write_text(json.dumps(dict(schemaVersion=1,basis='source nominal label',mappings=[row]),ensure_ascii=False),encoding='utf-8')
        return path

    def test_explicit_mapping_rejects_changed_evidence_incomplete_roles_and_wrong_identity(self):
        path=self.mapping_file()
        assets=fixtures.module.load_assets(self.manifest())
        self.assertEqual(1,len(roles.load_mappings(path,assets)))
        original=json.loads(path.read_text(encoding='utf-8'))
        for change in ('metadata','missing','path','identity','duplicate'):
            doc=copy.deepcopy(original);row=doc['mappings'][0]
            if change=='metadata':row['metadataSha256']='0'*64
            if change=='missing':row['relations']=[]
            if change=='path':row['relations'][0]['evidencePath']='/label/gesture/destination'
            if change=='identity':row['sha256']='0'*64
            if change=='duplicate':doc['mappings'].append(copy.deepcopy(row))
            path.write_text(json.dumps(doc),encoding='utf-8')
            with self.subTest(change=change),self.assertRaises(ValueError):roles.load_mappings(path,assets)

    def test_repeated_anchors_and_label_selection_are_preserved(self):
        import link_reference_audio_roles as roles
        self.asset['category'] = self.asset['metadata']['label']['category'] = '시김새'
        label = self.asset['metadata']['label']
        anchor = {'register': '평취', 'yulmyeong': '중', 'f0Hz': 400.0, 'windowsSeconds': [[0.1, 0.2]]}
        label.update(startSeconds=0, endSeconds=1, evidence={'labelF0Hz': 400.0,
            'sequenceDecision': {'orderedAnchors': [anchor, copy.deepcopy(anchor)], 'labelAnchorIndex': 1}})
        anchors = roles.evidence_nodes(label)
        self.assertEqual([('LABEL', 0), ('SEQUENCE', 0), ('SEQUENCE', 1)], [(r, i) for r, i, _, _ in anchors])
        label['evidence']['sequenceDecision']['labelAnchorIndex'] = 2
        with self.assertRaises(ValueError):
            roles.evidence_nodes(label)

    def test_same_source_label_cannot_point_to_different_pitches_across_roles(self):
        path=self.mapping_file();label=self.asset['metadata']['label']
        label.update(startSeconds=0,endSeconds=1,evidence={'labelF0Hz':400.0,'sequenceDecision':{
            'orderedAnchors':[{'register':'평취','yulmyeong':'중','f0Hz':400.0,'windowsSeconds':[[0.1,0.2]]}],
            'labelAnchorIndex':0}})
        document=json.loads(path.read_text(encoding='utf-8'));row=document['mappings'][0]
        row['metadataSha256']=roles.metadata_digest(self.asset['metadata'])
        row['relations'].append(dict(role='SEQUENCE',anchorIndex=0,evidencePath='/label/evidence/sequenceDecision/orderedAnchors/0',
            register='평취',yulmyeong='중',yul='중',octave=1))
        path.write_text(json.dumps(document),encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'conflicting role targets'):
            roles.load_mappings(path,fixtures.module.load_assets(self.manifest()))

    def test_curated_207_assets_and_315_roles_match_committed_annotations(self):
        from collections import Counter
        counts=Counter();ids=set()
        for path in (ROOT/'infra/catalog/pitch-mappings').glob('*-sigimsae-v1.json'):
            doc=json.loads(path.read_text(encoding='utf-8'))
            dataset=doc['mappings'][0]['datasetId'];source=annotations(dataset)
            self.assertEqual({k for k,(_,g,_) in source.items() if g['category']=='시김새'}, {r['labelId'] for r in doc['mappings']})
            for mapping in doc['mappings']:
                row,group,_=source[mapping['labelId']];ev=group.get('evidence',{}).get(row['id'],{})
                ids.add(mapping['assetId']);counts.update(r['role'] for r in mapping['relations'])
                expected=[('LABEL',0,row)]
                expected.extend(('SEQUENCE',i,a) for i,a in enumerate(ev.get('sequenceDecision',{}).get('orderedAnchors',[])))
                if 'gesture' in ev:
                    expected.extend([('LOWER_ANCHOR',0,ev['gesture']['lowerAnchor']),('DESTINATION',0,row)])
                self.assertEqual(len(expected),len(mapping['relations']))
                for relation,(role,index,node) in zip(mapping['relations'],expected):
                    self.assertEqual((role,index,node['register'],node['yulmyeong']),tuple(relation[k] for k in ('role','anchorIndex','register','yulmyeong')))
                    y=node['yulmyeong'];register=node['register']
                    if y=='중청임':target=('임',2)
                    elif dataset in ('jeongak-daegeum','sanjo-daegeum'):target=(y,(-1 if y in ('임','남','무') else 0)+{'저취':0,'평취':1,'역취':2}[register])
                    else:target=(y,(0 if y in (('중','임','무') if dataset=='danso' else ('임','남','무')) else 1)+{'평취':0,'역취':1}[register])
                    self.assertEqual(target,(relation['yul'],relation['octave']))
        self.assertEqual(207,len(ids))
        self.assertEqual(Counter(LABEL=207,SEQUENCE=88,LOWER_ANCHOR=10,DESTINATION=10),counts)


@unittest.skipUnless(os.environ.get('REFERENCE_AUDIO_TEST_MYSQL_CONTAINER'),'Disposable MySQL not selected')
class RoleMySqlTests(unittest.TestCase):
    run_sql = mysql_fixture.ReferenceAudioMySqlTests.__dict__['run_sql']
    another_asset = mysql_fixture.ReferenceAudioMySqlTests.another_asset

    @classmethod
    def setUpClass(cls):
        mysql_fixture.ReferenceAudioMySqlTests.setUpClass.__func__(cls)

    def setUp(self):
        self.run_sql('DROP TABLE IF EXISTS reference_audio_pitch_relations;')
        mysql_fixture.ReferenceAudioMySqlTests.setUp(self)
        self.asset['category']=self.asset['metadata']['label']['category']='시김새'

    def mapping(self,asset=None):
        asset=asset or self.asset
        return {**{k:asset[k] for k in roles.IDENTITY},'metadata':asset['metadata'],'basis':'selected label; inferred',
            'relations':[dict(role='LABEL',anchorIndex=0,yul='중',octave=0,evidencePath='/label')]}

    def snapshot(self):
        return self.run_sql('SELECT * FROM reference_audio_assets ORDER BY asset_id; SELECT * FROM reference_audio_pitch_relations ORDER BY asset_id,role,anchor_index;').stdout

    def test_exact_rerun_and_plain_import_preserve_all_rows(self):
        self.run_sql(fixtures.module.render_sql([self.asset]))
        sql=roles.render_sql([self.mapping()]);self.run_sql(sql)
        before=self.snapshot();self.run_sql(sql);self.run_sql(fixtures.module.render_sql([self.asset]))
        self.assertEqual(before,self.snapshot())
        self.assertEqual('1\t71',self.run_sql('SELECT COUNT(*),MIN(pitch_id) FROM reference_audio_pitch_relations;').stdout.strip())

    def test_evidence_change_missing_target_and_existing_conflict_reject_whole_batch_even_force(self):
        added=self.another_asset();self.run_sql(fixtures.module.render_sql([self.asset,added]))
        original=self.mapping()
        for failure in ('metadata','target','identity','basis'):
            if failure=='basis':self.run_sql(roles.render_sql([original]))
            before=self.snapshot();bad=copy.deepcopy(original)
            if failure=='metadata':bad['metadata']['label']['evidence']={'changed':True}
            if failure=='target':bad['relations'][0]['octave']=2
            if failure=='identity':bad['sha256']='0'*64
            if failure=='basis':bad['basis']='changed basis'
            for force in (False,True):
                result=self.run_sql(roles.render_sql([bad,self.mapping(added)]),force=force,check=False)
                self.assertIn('Check constraint',result.stderr)
                self.assertEqual(before,self.snapshot())

    def test_partial_or_extra_existing_roles_are_not_silently_completed(self):
        self.run_sql(fixtures.module.render_sql([self.asset]));mapping=self.mapping()
        self.run_sql(roles.render_sql([mapping]));before=self.snapshot()
        mapping['relations'].append(dict(role='SEQUENCE',anchorIndex=0,yul='중',octave=0,evidencePath='/label/evidence/sequenceDecision/orderedAnchors/0'))
        self.assertNotEqual(0,self.run_sql(roles.render_sql([mapping]),check=False).returncode)
        self.assertEqual(before,self.snapshot())
        self.run_sql('DELETE FROM reference_audio_pitch_relations;');self.run_sql(roles.render_sql([mapping]));before=self.snapshot()
        self.assertNotEqual(0,self.run_sql(roles.render_sql([self.mapping()]),check=False).returncode)
        self.assertEqual(before,self.snapshot())

    def test_role_index_path_and_pitch_foreign_keys_are_enforced(self):
        self.run_sql(fixtures.module.render_sql([self.asset]));self.run_sql(roles.render_sql([self.mapping()]))
        for update in ("pitch_id=999","role='invented'","anchor_index=1","evidence_path='/wrong'","mapping_basis=' '"):
            self.assertNotEqual(0,self.run_sql('UPDATE reference_audio_pitch_relations SET '+update,check=False).returncode)


if __name__ == '__main__':
    unittest.main()
