// S15P31E102-1256: App Store release: develop revision -> Eunseo Mac -> Release archive -> App Store IPA (-> optional App Store Connect upload).
// Signing and upload use a team App Store Connect API key (Admin role, cloud signing) from Jenkins Credentials, only inside withCredentials:
//   eolssu-asc-api-key (Secret file, AuthKey_<KEY_ID>.p8), eolssu-asc-key-id (Secret text), eolssu-asc-issuer-id (Secret text).
pipeline {
    agent none
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 75, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters {
        string(name: 'REVISION', defaultValue: '', description: 'develop 전체 커밋 SHA')
        string(name: 'MARKETING_VERSION', defaultValue: '', description: '사용자에게 보이는 버전 CFBundleShortVersionString (예: 1.0.3)')
        string(name: 'CURRENT_PROJECT_VERSION', defaultValue: '', description: '빌드 번호 CFBundleVersion. App Store Connect에 올린 마지막 빌드보다 큰 정수 (1.0.2는 8)')
        booleanParam(name: 'UPLOAD_TO_APP_STORE_CONNECT', defaultValue: false, description: '체크하면 IPA를 만든 뒤 App Store Connect(TestFlight)에 업로드합니다. 심사 제출은 하지 않습니다.')
    }
    stages {
        stage('Build App Store IPA on Eunseo Mac') {
            agent { label 'ios-eunseo' }
            // S15P31E102-908: a sleeping Mac drops the agent channel mid-build; rerun once it reconnects.
            options { retry(count: 2, conditions: [agent(), nonresumable()]) }
            environment { IOS_TEAM_ID = 'WDA26H33JA' }
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) { error('Full develop SHA required') }
                    if (!params.MARKETING_VERSION?.matches('[0-9]+\\.[0-9]+\\.[0-9]+')) { error('MARKETING_VERSION must look like 1.0.3') }
                    if (!params.CURRENT_PROJECT_VERSION?.matches('[1-9][0-9]{0,8}')) { error('Positive integer CURRENT_PROJECT_VERSION required') }
                    def revision = checkout scmGit(branches: [[name: params.REVISION]], userRemoteConfigs: [[credentialsId: 'eolssu-github', url: 'https://github.com/team-edo/eolssu.git', refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) { error('Revision mismatch') }
                    sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.MARKETING_VERSION} (${params.CURRENT_PROJECT_VERSION})"
                }
                withCredentials([
                    file(credentialsId: 'eolssu-asc-api-key', variable: 'ASC_KEY_FILE'),
                    string(credentialsId: 'eolssu-asc-key-id', variable: 'ASC_KEY_ID'),
                    string(credentialsId: 'eolssu-asc-issuer-id', variable: 'ASC_ISSUER_ID')
                ]) {
                sh '''#!/usr/bin/env bash
set -euo pipefail
/usr/bin/caffeinate -i -w $$ &
derived="$HOME/jenkins-agent/ios-release-derived"
out="$PWD/release-out"
# A build cut off by an agent disconnect keeps running on the Mac; stop it before reusing its paths.
for pid in $(pgrep -f "xcodebuild .*($derived|$out/)" || true); do
  echo "Stopping leftover release xcodebuild $pid"
  kill -TERM "$pid" 2>/dev/null || true
done
[[ "$(git rev-parse HEAD)" == "$GIT_COMMIT" ]]
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"
asc_key_id=$(printf '%s' "$ASC_KEY_ID" | tr -d '[:space:]')
asc_issuer=$(printf '%s' "$ASC_ISSUER_ID" | tr -d '[:space:]')
[[ -s "$ASC_KEY_FILE" && "$asc_key_id" =~ ^[A-Z0-9]{10}$ && "$asc_issuer" =~ ^[0-9a-f-]{36}$ ]] || { echo "Invalid App Store Connect API key credentials (eolssu-asc-*)" >&2; exit 1; }
auth=(-authenticationKeyPath "$ASC_KEY_FILE" -authenticationKeyID "$asc_key_id" -authenticationKeyIssuerID "$asc_issuer")
echo "Signing with App Store Connect API key from Jenkins Credentials"
rm -rf "$out" && mkdir -p "$out"
archive="$out/eolssu.xcarchive"
xcodebuild -project frontend/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath "$derived" \
  -archivePath "$archive" -allowProvisioningUpdates "${auth[@]}" \
  "TEAM_ID=$IOS_TEAM_ID" PRODUCT_BUNDLE_IDENTIFIER=com.ssafy.eolssu \
  "MARKETING_VERSION=$MARKETING_VERSION" "CURRENT_PROJECT_VERSION=$CURRENT_PROJECT_VERSION" archive
for destination in export upload; do
  /usr/bin/python3 - "$out/export-$destination.plist" "$IOS_TEAM_ID" "$destination" <<'PY'
import plistlib,sys
with open(sys.argv[1], 'wb') as f:
    plistlib.dump(dict(method='app-store-connect', destination=sys.argv[3], teamID=sys.argv[2],
        signingStyle='automatic', manageAppVersionAndBuildNumber=False, uploadSymbols=True), f)
PY
done
xcodebuild -exportArchive -archivePath "$archive" -exportPath "$out/export" \
  -exportOptionsPlist "$out/export-export.plist" -allowProvisioningUpdates "${auth[@]}"
ipas=("$out"/export/*.ipa)
[[ ${#ipas[@]} -eq 1 && -f "${ipas[0]}" ]]
name="eolssu-$MARKETING_VERSION-b$CURRENT_PROJECT_VERSION"
cp "${ipas[0]}" "$out/$name.ipa"
/usr/bin/python3 - "$out/$name.ipa" "$IOS_TEAM_ID" "$MARKETING_VERSION" "$CURRENT_PROJECT_VERSION" <<'PY'
import plistlib,re,sys,zipfile
ipa,team,version,build=sys.argv[1:]
with zipfile.ZipFile(ipa) as z:
    app=next(n.rsplit('/',1)[0] for n in z.namelist() if re.fullmatch(r'Payload/[^/]+\\.app/Info\\.plist',n))
    info=plistlib.loads(z.read(app+'/Info.plist'))
    raw=z.read(app+'/embedded.mobileprovision')
profile=plistlib.loads(raw[raw.index(b'<?xml'):raw.index(b'</plist>')+8])
checks={'bundle id':info.get('CFBundleIdentifier')=='com.ssafy.eolssu',
        'version':info.get('CFBundleShortVersionString')==version,
        'build':info.get('CFBundleVersion')==build,
        'team':team in profile.get('TeamIdentifier',[]),
        'App Store profile (no device list)':'ProvisionedDevices' not in profile and not profile.get('ProvisionsAllDevices'),
        'distribution (not debug)':profile.get('Entitlements',{}).get('get-task-allow') is False}
for k,v in checks.items(): print(('ok  ' if v else 'FAIL')+' '+k)
sys.exit(0 if all(checks.values()) else 1)
PY
if [[ -d "$archive/dSYMs" ]]; then ditto -c -k --keepParent "$archive/dSYMs" "$out/$name-dSYMs.zip"; fi
printf '%s\\n' "$GIT_COMMIT" > "$out/revision.txt"
(cd "$out" && shasum -a 256 "$name.ipa" | tee "$name.ipa.sha256")
'''
                }
                archiveArtifacts artifacts: 'release-out/*.ipa, release-out/*.sha256, release-out/*-dSYMs.zip, release-out/revision.txt', fingerprint: true, allowEmptyArchive: false
            }
        }
        stage('Upload to App Store Connect') {
            when {
                beforeAgent true
                expression { params.UPLOAD_TO_APP_STORE_CONNECT }
            }
            // Same exclusive single-executor Mac and job workspace, so the archive from the previous stage is still there.
            // No retry: a second upload of the same build number is rejected by App Store Connect.
            agent { label 'ios-eunseo' }
            steps {
                withCredentials([
                    file(credentialsId: 'eolssu-asc-api-key', variable: 'ASC_KEY_FILE'),
                    string(credentialsId: 'eolssu-asc-key-id', variable: 'ASC_KEY_ID'),
                    string(credentialsId: 'eolssu-asc-issuer-id', variable: 'ASC_ISSUER_ID')
                ]) {
                sh '''#!/usr/bin/env bash
set -euo pipefail
out="$PWD/release-out"
[[ -d "$out/eolssu.xcarchive" && "$(cat "$out/revision.txt")" == "$REVISION" ]] || { echo "Archive for $REVISION not found in workspace; rerun the job" >&2; exit 1; }
asc_key_id=$(printf '%s' "$ASC_KEY_ID" | tr -d '[:space:]')
asc_issuer=$(printf '%s' "$ASC_ISSUER_ID" | tr -d '[:space:]')
xcodebuild -exportArchive -archivePath "$out/eolssu.xcarchive" -exportPath "$out/upload" \
  -exportOptionsPlist "$out/export-upload.plist" -allowProvisioningUpdates \
  -authenticationKeyPath "$ASC_KEY_FILE" -authenticationKeyID "$asc_key_id" -authenticationKeyIssuerID "$asc_issuer"
'''
                }
                script { currentBuild.description = "App Store Connect 업로드 완료: ${params.MARKETING_VERSION} (${params.CURRENT_PROJECT_VERSION}). 처리 후 TestFlight·심사 제출은 App Store Connect에서 진행" }
            }
        }
    }
}
