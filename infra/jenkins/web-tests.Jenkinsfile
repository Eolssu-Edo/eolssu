// S15P31E102-1270: develop 커밋의 웹(JS·Wasm) 브라우저 테스트만 돌린다. 이미지·배포는 하지 않는다.
// 백엔드 잡이 웹 빌드 입력이 바뀐 develop 커밋마다 REVISION을 넘겨 예약한다. 운영 배포는 수동 웹 잡(frontend.Jenkinsfile)이 맡는다.
pipeline {
    agent { label 'edo1-web' }

    options {
        skipDefaultCheckout()
        // 더 새 develop 커밋은 이전 커밋의 변경을 모두 담으므로, 대기 중이거나 도는 이전 빌드를 새 빌드로 바꾼다.
        disableConcurrentBuilds(abortPrevious: true)
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        string(name: 'REVISION', defaultValue: '', description: '백엔드 잡이 넘긴 develop 전체 커밋 SHA')
    }

    environment {
        CHROME_BIN = '/usr/bin/google-chrome'
    }

    stages {
        stage('Clean workspace') {
            steps {
                script {
                    if (fileExists('.git')) {
                        def prepared = sh(returnStatus: true,
                            script: '/opt/eolssu/config/prepare-frontend-workspace.sh')
                        if (prepared == 2) {
                            deleteDir()
                        } else if (prepared != 0) {
                            error("Frontend workspace preparation failed (exit ${prepared})")
                        }
                    } else {
                        deleteDir()
                    }
                }
            }
        }

        stage('Checkout develop revision') {
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) {
                        error('A full develop commit SHA is required')
                    }
                    def revision = checkout scmGit(
                        branches: [[name: params.REVISION]],
                        userRemoteConfigs: [[credentialsId: 'eolssu-github',
                            url: 'https://github.com/team-edo/eolssu.git',
                            refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) {
                        error('Web test checkout revision mismatch')
                    }
                    sh 'git merge-base --is-ancestor "$GIT_COMMIT" origin/develop'
                    currentBuild.description = env.GIT_COMMIT.take(9)
                }
            }
        }

        stage('Wasm browser tests') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:wasmJsTest'
                }
            }
            post { always { junit testResults: 'frontend/shared/build/test-results/wasmJsBrowserTest/TEST-*.xml', allowEmptyResults: false } }
        }

        stage('JavaScript browser tests') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:jsTest'
                }
            }
            post { always { junit testResults: 'frontend/shared/build/test-results/jsBrowserTest/TEST-*.xml', allowEmptyResults: false } }
        }
    }
}
