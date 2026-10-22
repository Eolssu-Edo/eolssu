pipeline {
    agent any

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    parameters {
        string(name: 'REVISION', defaultValue: '', description: '상위 develop 잡에서 검증한 전체 SHA')
    }

    environment {
        CHROME_BIN = '/usr/bin/google-chrome'
    }

    stages {
        stage('Checkout verified develop revision') {
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) {
                        error('A full develop commit SHA is required')
                    }
                    deleteDir()
                    def revision = checkout scmGit(
                        branches: [[name: params.REVISION]],
                        userRemoteConfigs: [[credentialsId: 'eolssu-gitlab',
                            url: 'https://lab.ssafy.com/s15-final/S15P31E102.git',
                            refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (env.GIT_COMMIT != params.REVISION) {
                        error('Frontend checkout does not match the verified backend revision')
                    }
                }
            }
        }

        stage('Validate deployment tools') {
            steps {
                sh '''
                    python3 -B -m unittest discover -s infra/jenkins/tests -v
                    RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -v
                '''
            }
        }

        stage('Test and package web') {
            steps {
                dir('frontend') {
                    sh './gradlew --no-daemon :shared:wasmJsTest :shared:jsTest :webApp:wasmJsBrowserDistribution'
                }
            }
            post {
                always {
                    junit testResults: 'frontend/shared/build/test-results/*/TEST-*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Build verified web image') {
            steps {
                sh '''
                    docker build --file /opt/eolssu/config/frontend.Dockerfile \
                        --label "org.opencontainers.image.revision=${GIT_COMMIT}" \
                        --tag "eolssu-frontend:ci-${GIT_COMMIT}" \
                        frontend/webApp/build/dist/wasmJs/productionExecutable
                '''
                archiveArtifacts artifacts: 'frontend/webApp/build/dist/wasmJs/productionExecutable/**', fingerprint: true
            }
        }

        stage('Verify candidate and deploy frontend') {
            steps {
                sh '''
                    sudo -n /opt/eolssu/bin/deploy-frontend "${GIT_COMMIT}" > frontend-deployment.jsonl
                    cat frontend-deployment.jsonl
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'frontend-deployment.jsonl', allowEmptyArchive: true
                }
            }
        }
    }
}
