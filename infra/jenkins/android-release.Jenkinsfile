pipeline {
    agent { label 'edo1-host' }
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters {
        string(name: 'REVISION', defaultValue: '', description: 'develop 전체 커밋 SHA')
        string(name: 'VERSION_CODE', defaultValue: '', description: 'Play versionCode (이전 업로드보다 큰 정수)')
        string(name: 'VERSION_NAME', defaultValue: '', description: '사용자에게 보이는 버전 (예: 1.3.0)')
    }
    environment { ANDROID_HOME = '/opt/eolssu/android-sdk' }
    stages {
        stage('Checkout develop revision') {
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) { error('Full develop SHA required') }
                    if (!params.VERSION_CODE?.matches('[1-9][0-9]{0,8}')) { error('Positive integer VERSION_CODE required') }
                    if (!params.VERSION_NAME?.matches('[0-9]+\\.[0-9]+\\.[0-9]+')) { error('VERSION_NAME must look like 1.3.0') }
                    def revision = checkout scmGit(branches: [[name: params.REVISION]], userRemoteConfigs: [[credentialsId: 'eolssu-gitlab', url: 'https://lab.ssafy.com/s15-final/S15P31E102.git', refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) { error('Revision mismatch') }
                    sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.VERSION_NAME} (${params.VERSION_CODE})"
                }
            }
        }
        stage('Test, lint and build signed AAB') {
            steps {
                withCredentials([
                    file(credentialsId: 'eolssu-android-upload-keystore', variable: 'EOLSSU_STORE_FILE'),
                    string(credentialsId: 'eolssu-android-upload-password', variable: 'EOLSSU_STORE_PASSWORD')
                ]) {
                    sh '''#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SDK_ROOT="$ANDROID_HOME"
EOLSSU_KEY_ALIAS=$(keytool -list -keystore "$EOLSSU_STORE_FILE" -storepass:env EOLSSU_STORE_PASSWORD | awk -F, '/PrivateKeyEntry/ {print $1; exit}')
[[ -n "$EOLSSU_KEY_ALIAS" ]] || { echo 'No private key entry in upload keystore' >&2; exit 1; }
export EOLSSU_KEY_ALIAS EOLSSU_KEY_PASSWORD="$EOLSSU_STORE_PASSWORD"
cd frontend
./gradlew --no-daemon --no-configuration-cache --max-workers=2 \
  :shared:testAndroidHostTest :androidApp:lintRelease :androidApp:bundleRelease \
  "-Peolssu.versionCode=$VERSION_CODE" "-Peolssu.versionName=$VERSION_NAME"
cd ..
out="eolssu-$VERSION_NAME-vc$VERSION_CODE.aab"
rm -rf release-out && mkdir release-out
cp frontend/androidApp/build/outputs/bundle/release/androidApp-release.aab "release-out/$out"
echo "Upload certificate:"
keytool -printcert -jarfile "release-out/$out" | grep -E 'Owner:|SHA256:'
(cd release-out && sha256sum "$out" | tee "$out.sha256")
'''
                }
                archiveArtifacts artifacts: 'release-out/*', fingerprint: true
            }
            post { always { junit testResults: 'frontend/shared/build/test-results/testAndroidHostTest/TEST-*.xml', allowEmptyResults: true } }
        }
    }
}
