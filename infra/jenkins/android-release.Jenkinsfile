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
        choice(name: 'PLAY_TRACK', choices: ['production', 'internal', 'none'], description: 'AAB를 올릴 Google Play 트랙. none이면 AAB만 만들고 올리지 않습니다.')
        choice(name: 'PLAY_STATUS', choices: ['draft', 'completed'], description: 'draft: 초안으로 올리고 Play Console에서 사람이 검토 전송합니다. completed: 올리면서 바로 검토 전송합니다(승인되면 100% 출시).')
        text(name: 'RELEASE_NOTES_KO', defaultValue: '', description: 'ko-KR 출시 노트(500자 이하). 비우면 노트 없이 올립니다.')
    }
    environment {
        ANDROID_HOME = '/opt/eolssu/android-sdk'
        PLAY_PACKAGE = 'com.ssafy.eolssu'
    }
    stages {
        stage('Checkout develop revision') {
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) { error('Full develop SHA required') }
                    if (!params.VERSION_CODE?.matches('[1-9][0-9]{0,8}')) { error('Positive integer VERSION_CODE required') }
                    if (!params.VERSION_NAME?.matches('[0-9]+\\.[0-9]+\\.[0-9]+')) { error('VERSION_NAME must look like 1.3.0') }
                    if ((params.RELEASE_NOTES_KO ?: '').trim().length() > 500) { error('RELEASE_NOTES_KO must be 500 characters or fewer') }
                    def revision = checkout scmGit(branches: [[name: params.REVISION]], userRemoteConfigs: [[credentialsId: 'eolssu-github', url: 'https://github.com/team-edo/eolssu.git', refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) { error('Revision mismatch') }
                    sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.VERSION_NAME} (${params.VERSION_CODE})"
                    if (params.PLAY_TRACK != 'none') {
                        currentBuild.displayName += " ${params.PLAY_TRACK}/${params.PLAY_STATUS}"
                        if (!fileExists('infra/jenkins/play-upload.py')) { error('REVISION predates infra/jenkins/play-upload.py; use PLAY_TRACK=none') }
                    }
                }
            }
        }
        stage('Check Google Play access') {
            when { expression { params.PLAY_TRACK != 'none' } }
            steps {
                // Fail before the long build when the key, the Play permission or VERSION_CODE is wrong.
                withCredentials([file(credentialsId: 'eolssu-play-service-account', variable: 'PLAY_SERVICE_ACCOUNT_JSON')]) {
                    sh 'python3 -B infra/jenkins/play-upload.py check --credentials "$PLAY_SERVICE_ACCOUNT_JSON" --package "$PLAY_PACKAGE" --version-code "$VERSION_CODE"'
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
        stage('Upload to Google Play') {
            when { expression { params.PLAY_TRACK != 'none' } }
            steps {
                writeFile file: 'play-notes-ko.txt', text: params.RELEASE_NOTES_KO ?: '', encoding: 'UTF-8'
                withCredentials([file(credentialsId: 'eolssu-play-service-account', variable: 'PLAY_SERVICE_ACCOUNT_JSON')]) {
                    // No retry: a committed upload would only fail again on the used versionCode.
                    sh '''#!/usr/bin/env bash
set -euo pipefail
python3 -B infra/jenkins/play-upload.py upload --credentials "$PLAY_SERVICE_ACCOUNT_JSON" --package "$PLAY_PACKAGE" \
  --aab "release-out/eolssu-$VERSION_NAME-vc$VERSION_CODE.aab" --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --track "$PLAY_TRACK" --status "$PLAY_STATUS" --notes-file play-notes-ko.txt
'''
                }
            }
        }
    }
}
