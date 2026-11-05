pipeline {
    agent any
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters { string(name: 'REVISION', defaultValue: '', description: '검증된 develop 전체 커밋 SHA') }
    environment { ANDROID_HOME = '/opt/eolssu/android-sdk' }
    stages {
        stage('Checkout develop revision') {
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) { error('Full develop SHA required') }
                    def revision = checkout scmGit(branches: [[name: params.REVISION]], userRemoteConfigs: [[credentialsId: 'eolssu-gitlab', url: 'https://lab.ssafy.com/s15-final/S15P31E102.git', refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) { error('Revision mismatch') }
                    sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                }
            }
        }
        stage('Build tested APK and preserve archive') {
            steps {
                sh 'bash /opt/eolssu/config/build-apk.sh'
                archiveArtifacts artifacts: 'frontend/androidApp/build/outputs/apk/debug/*.apk', fingerprint: true
            }
            post { always { junit testResults: 'frontend/shared/build/test-results/testAndroidHostTest/TEST-*.xml', allowEmptyResults: true } }
        }
    }
}
