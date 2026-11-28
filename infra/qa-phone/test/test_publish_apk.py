import importlib.util, json, pathlib, tempfile, unittest
SPEC=importlib.util.spec_from_file_location('publisher',pathlib.Path(__file__).parents[1]/'publish-apk.py')
publisher=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(publisher)
class PublishTest(unittest.TestCase):
    def test_idempotent_archive_and_conflict_preserve_index(self):
        with tempfile.TemporaryDirectory(prefix='qa312-publish-') as temp:
            root=pathlib.Path(temp);apk=root/'fixture.apk';apk.write_bytes(b'isolated fixture')
            aapt=root/'fixture-aapt';aapt.write_text("#!/bin/sh\necho \"package: name='com.ssafy.eolssu.qa' versionCode='12' versionName='qa-12'\"\n");aapt.chmod(0o755)
            archive=root/'archive'
            first=publisher.publish(archive,apk,'a'*40,12,str(aapt))
            second=publisher.publish(archive,apk,'a'*40,12,str(aapt))
            self.assertEqual(first,second);self.assertEqual(len(json.loads((archive/'index.json').read_text())['builds']),1)
            apk.write_bytes(b'changed same build')
            with self.assertRaisesRegex(ValueError,'immutable'):publisher.publish(archive,apk,'a'*40,12,str(aapt))
            self.assertEqual(len(json.loads((archive/'index.json').read_text())['builds']),1)
            self.assertEqual((archive/first['id']/'app.apk').read_bytes(),b'isolated fixture')
    def test_invalid_revision_is_rejected_before_external_command(self):
        with self.assertRaises(ValueError):publisher.publish('/unused','/unused','../invalid',1,'/unused')
    def test_merge_request_metadata_preserves_existing_archive(self):
        with tempfile.TemporaryDirectory(prefix='qa290-mr-') as temp:
            root=pathlib.Path(temp);apk=root/'fixture.apk';apk.write_bytes(b'fixture')
            aapt=root/'aapt';aapt.write_text("#!/bin/sh\necho \"package: name='com.ssafy.eolssu.qa' versionCode='12' versionName='qa-12'\"\n");aapt.chmod(0o755)
            archive=root/'archive';first=publisher.publish(archive,apk,'a'*40,12,str(aapt))
            again=publisher.publish(archive,apk,'a'*40,12,str(aapt),191)
            self.assertEqual(first,again);self.assertEqual(json.loads((archive/'index.json').read_text())['mergeRequests']['a'*40],191)
            with self.assertRaisesRegex(ValueError,'MR metadata conflict'):publisher.publish(archive,apk,'a'*40,12,str(aapt),192)
            self.assertEqual((archive/first['id']/'app.apk').read_bytes(),b'fixture')
    def test_pull_request_is_extracted_only_from_unambiguous_github_merge_commit(self):
        self.assertEqual(publisher.extract_merge_request('Merge pull request #12 from team-edo/feat(fe)/S15P31E102-1-화면\n\nfeat(fe): 화면 (S15P31E102-1)\n'),12)
        self.assertIsNone(publisher.extract_merge_request('Closes S15P31E102-331\nSee merge request s15-final/S15P31E102!191\n'))
        self.assertIsNone(publisher.extract_merge_request('Jenkins build #6'))
        self.assertIsNone(publisher.extract_merge_request('fix: 설명\n\nMerge pull request #3 from team-edo/x'))
        self.assertIsNone(publisher.extract_merge_request('Merge pull request #1 from a/b\nMerge pull request #2 from a/c'))
        with self.assertRaisesRegex(ValueError,'invalid MR'):publisher.publish('/unused','/unused','a'*40,1,'/unused',0)
if __name__=='__main__':unittest.main()
