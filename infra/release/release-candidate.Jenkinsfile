// 초안(S15P31E102-916): 최종 발표 RC 검증 전용 잡. Jenkins에 등록하지 않았다.
// 배포·태그·main 변경 단계는 넣지 않는다. 등록은 팀 승인 뒤 기존 잡 등록 절차를 따른다.
pipeline {
    agent any

    parameters {
        string(name: 'RC_SHA', defaultValue: '', description: '검증할 develop 전체 커밋 SHA')
        string(name: 'RELEASE_TAG', defaultValue: '', description: '노트에 표시할 태그 이름(예: v1.0.0). 태그를 만들지 않는다')
    }

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Clean workspace') {
            steps {
                deleteDir()
            }
        }

        stage('Checkout release candidate') {
            steps {
                script {
                    if (!params.RC_SHA?.matches('[0-9a-f]{40}')) {
                        error('RC_SHA must be a full lowercase commit SHA')
                    }
                    if (params.RELEASE_TAG && !params.RELEASE_TAG.matches('v[0-9]+\\.[0-9]+\\.[0-9]+(-rc\\.[0-9]+)?')) {
                        error('RELEASE_TAG must look like v1.2.3 or v1.2.3-rc.1')
                    }
                    def revision = checkout scmGit(
                        branches: [[name: params.RC_SHA]],
                        userRemoteConfigs: [[credentialsId: 'eolssu-github',
                            url: 'https://github.com/team-edo/eolssu.git',
                            refspec: '+refs/heads/develop:refs/remotes/origin/develop +refs/heads/main:refs/remotes/origin/main +refs/tags/*:refs/tags/*']])
                    if (revision.GIT_COMMIT != params.RC_SHA) {
                        error('Checked out revision does not match RC_SHA')
                    }
                }
            }
        }

        stage('Release candidate shape') {
            steps {
                sh 'python3 -B infra/release/release-candidate.py check "$RC_SHA"'
            }
        }

        stage('Release notes') {
            steps {
                sh '''
                set -- notes "$RC_SHA"
                if [ -n "$RELEASE_TAG" ]; then set -- "$@" --tag "$RELEASE_TAG"; fi
                python3 -B infra/release/release-candidate.py "$@" > release-notes.md
                python3 -B infra/release/release-candidate.py "$@" --format json > release-notes.json
                '''
            }
            post { always { archiveArtifacts artifacts: 'release-notes.*', allowEmptyArchive: true } }
        }

        stage('Release tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/release/tests -v'
            }
        }

        stage('Deploy tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -v'
            }
        }

        stage('Backend unit tests') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 clean test'
                }
            }
            post { always { junit testResults: 'backend/build/test-results/test/*.xml', allowEmptyResults: false } }
        }

        stage('Recording media tests') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 mediaTest'
                }
            }
            post { always { junit testResults: 'backend/build/test-results/mediaTest/*.xml', allowEmptyResults: false } }
        }
    }

    post {
        always {
            script {
                currentBuild.description = 'RC ' + (params.RC_SHA ?: 'missing').take(12) + (params.RELEASE_TAG ? ' ' + params.RELEASE_TAG : '')
            }
        }
    }
}
