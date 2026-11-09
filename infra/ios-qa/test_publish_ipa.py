import datetime, importlib.util, pathlib, plistlib, tempfile, unittest, zipfile
spec=importlib.util.spec_from_file_location('publisher',pathlib.Path(__file__).with_name('publish-ipa.py'))
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)

class PublishTest(unittest.TestCase):
    def ipa(self, root, *, team='WDA26H33JA', debug=False, devices=True, apple=True, expired=False):
        path=pathlib.Path(root)/'fixture.ipa'
        profile=dict(TeamIdentifier=[team], ExpirationDate=datetime.datetime.now()+datetime.timedelta(days=-1 if expired else 30),
            Entitlements={'get-task-allow':debug,'com.apple.developer.applesignin':['Default'] if apple else []})
        if devices: profile['ProvisionedDevices']=['registered-device']
        with zipfile.ZipFile(path,'w') as z:
            base='Payload/eolssu.app/'
            z.writestr(base+'Info.plist',plistlib.dumps(dict(CFBundleIdentifier='com.ssafy.eolssu.dev',CFBundleShortVersionString='1.0',CFBundleVersion='1794180000')))
            z.writestr(base+'embedded.mobileprovision',b'CMS'+plistlib.dumps(profile))
            z.writestr(base+'_CodeSignature/CodeResources',b'fixture')
        return path

    def test_rejects_profiles_that_cannot_support_our_ota(self):
        for kwargs in [dict(team='B7Q6Q2H7TV'),dict(debug=True),dict(devices=False),dict(apple=False),dict(expired=True)]:
            with self.subTest(kwargs=kwargs), tempfile.TemporaryDirectory() as d:
                with self.assertRaises(ValueError):p.inspect_ipa(self.ipa(d,**kwargs))

    def test_ota_page_hides_free_team_links_and_labels_latest(self):
        meta=dict(id='latest',build=57,commit='b'*40,builtAt='2026-11-09',profileExpiresAt='2027-11-08',team='WDA26H33JA',bundleId='com.ssafy.eolssu.dev')
        legacy=dict(meta,id='legacy',build=4,builtAt='2026-11-04',team='B7Q6Q2H7TV')
        page=p.index_html([legacy,meta],'https://example.test/ipas').decode()
        self.assertIn('최신 빌드 설치 · #57',page)
        self.assertIn('/latest/manifest.plist',page)
        self.assertNotIn('/legacy/manifest.plist',page)

    def test_manifest_build_version_and_immutable_retry(self):
        with tempfile.TemporaryDirectory() as d:
            ipa=self.ipa(d);root=pathlib.Path(d)/'published'
            args=(root,ipa,'a'*40,56,'https://example.test/ipas')
            first=p.publish(*args);second=p.publish(*args)
            self.assertEqual(first,second)
            manifest=plistlib.loads((root/first['id']/'manifest.plist').read_bytes())
            self.assertEqual('1794180000',manifest['items'][0]['metadata']['bundle-version'])
            self.assertEqual(1,len(__import__('json').loads((root/'index.json').read_text())['builds']))
            with zipfile.ZipFile(ipa,'a') as z:z.writestr('Payload/eolssu.app/changed',b'changed')
            with self.assertRaisesRegex(ValueError,'immutable'):p.publish(*args)

if __name__=='__main__':unittest.main()
