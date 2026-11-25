// S15P31E102-444: develop -> Eunseo Mac -> paid-team Ad Hoc -> HTTPS OTA.
// S15P31E102-1258: bound the wait for an offline Mac; only the newest queued revision runs.
def MAC_LABEL = 'ios-eunseo'
def MAC_WAIT_MINUTES = 15

pipeline {
    agent none
    options {
        skipDefaultCheckout()
        // A newer develop revision replaces the running or waiting one instead of queueing behind it.
        disableConcurrentBuilds(abortPrevious: true)
        // Safety net only: Mac wait and build have their own limits below.
        timeout(time: 4, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    parameters { string(name: 'REVISION', defaultValue: '', description: '검증된 develop 전체 커밋 SHA') }
    stages {
        stage('Build Ad Hoc IPA on Eunseo Mac') {
            steps {
                script {
                    if (!(params.REVISION ==~ /[0-9a-f]{40}/)) { error('Full develop SHA required') }
                    // S15P31E102-908: a sleeping Mac drops the agent channel mid-build; rerun once it reconnects.
                    retry(count: 2, conditions: [agent(), nonresumable()]) {
                        def onMac = false
                        parallel(
                            failFast: true,
                            'Build on Mac': {
                                node(MAC_LABEL) {
                                    onMac = true
                                    // The build limit starts only after the Mac executor is assigned.
                                    timeout(time: 45, unit: 'MINUTES') {
                                        withEnv(['IOS_TEAM_ID=WDA26H33JA', 'IOS_DEVICE_ID=00008140-0004446002D2801C']) {
                                            def revision = checkout scmGit(branches: [[name: params.REVISION]], userRemoteConfigs: [[credentialsId: 'eolssu-gitlab', url: 'https://lab.ssafy.com/s15-final/S15P31E102.git', refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                                            env.GIT_COMMIT = revision.GIT_COMMIT
                                            if (env.GIT_COMMIT != params.REVISION) { error('Revision mismatch') }
                                            sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                                            // Managed scripts allow rebuilding develop revisions predating this task.
                                            sh 'bash "$HOME/jenkins-agent/build-ipa.sh"'
                                            stash name: 'ipa', includes: 'build/ipa/app.ipa'
                                        }
                                    }
                                }
                            },
                            'Wait for Mac': {
                                long deadline = System.currentTimeMillis() + MAC_WAIT_MINUTES * 60000L
                                waitUntil(initialRecurrencePeriod: 15000, quiet: true) {
                                    if (onMac) { return true }
                                    if (System.currentTimeMillis() > deadline) {
                                        error("Mac agent (label ${MAC_LABEL}) did not become available within ${MAC_WAIT_MINUTES} min. " +
                                            'Check that the Mac is awake, on AC power with the lid open, and that com.eolssu.jenkins-agent is running; ' +
                                            'then rerun with the same REVISION.')
                                    }
                                    return false
                                }
                            }
                        )
                    }
                }
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
