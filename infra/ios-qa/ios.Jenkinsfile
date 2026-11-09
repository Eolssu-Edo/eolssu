// S15P31E102-444: develop -> Eunseo Mac -> paid-team Ad Hoc -> HTTPS OTA.
pipeline {
    agent none
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters { string(name: 'REVISION', defaultValue: '', description: '검증된 develop 전체 커밋 SHA') }
    stages {
        stage('Build Ad Hoc IPA on Eunseo Mac') {
            agent { label 'ios-eunseo' }
            environment {
                IOS_TEAM_ID = 'WDA26H33JA'
                IOS_DEVICE_ID = '00008140-0004446002D2801C'
            }
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) { error('Full develop SHA required') }
                    def revision = checkout scmGit(branches: [[name: params.REVISION]], userRemoteConfigs: [[credentialsId: 'eolssu-gitlab', url: 'https://lab.ssafy.com/s15-final/S15P31E102.git', refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) { error('Revision mismatch') }
                    sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                }
                // Managed scripts allow rebuilding develop revisions predating this task.
                sh 'bash "$HOME/jenkins-agent/build-ipa.sh"'
                stash name: 'ipa', includes: 'build/ipa/app.ipa'
            }
        }
        stage('Publish Ad Hoc IPA') {
            agent { label 'edo1' }
            steps {
                deleteDir()
                unstash 'ipa'
                sh 'python3 /opt/eolssu/config/publish-ipa.py --root /opt/eolssu/ipas --ipa build/ipa/app.ipa --revision "$REVISION" --build "$BUILD_NUMBER" --base-url https://k15e102.p.ssafy.io/ipas'
                script { currentBuild.description = 'Safari 설치: /ipas/ (등록 iPhone, Ad Hoc)' }
            }
        }
    }
}
