pipeline {
    agent any

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        pollSCM('* * * * *')
    }

    stages {
        stage('Checkout develop') {
            steps {
                script {
                    deleteDir()
                    def revision = checkout scmGit(
                        branches: [[name: 'refs/heads/develop']],
                        userRemoteConfigs: [[credentialsId: 'eolssu-gitlab',
                            url: 'https://lab.ssafy.com/s15-final/S15P31E102.git',
                            refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    if (!env.GIT_COMMIT?.matches('[0-9a-f]{40}')) {
                        error('Git checkout commit SHA is missing or invalid')
                    }
                }
            }
        }

        stage('Test and package') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon clean build'
                    sh './gradlew --no-daemon mediaTest'
                }
            }
            post {
                always {
                    junit testResults: 'backend/build/test-results/test/*.xml, backend/build/test-results/mediaTest/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Validate runtime') {
            steps {
                sh '''
                    docker compose --env-file /opt/eolssu/config/ci.env.example \
                        -f /opt/eolssu/config/compose.yml config --quiet
                    docker run --rm --network none \
                        --mount type=bind,src=/opt/eolssu/config/nginx.conf,dst=/etc/nginx/conf.d/default.conf,readonly \
                        nginx:1.30.6-alpine nginx -t
                '''
            }
        }

        stage('Build image') {
            steps {
                sh '''
                    docker build --file /opt/eolssu/config/backend.Dockerfile \
                        --label "org.opencontainers.image.revision=${GIT_COMMIT}" \
                        --tag "eolssu-backend:ci-${GIT_COMMIT}" backend
                '''
            }
        }

        stage('Archive package') {
            steps {
                archiveArtifacts artifacts: 'backend/build/libs/*.jar', excludes: '**/*-plain.jar', fingerprint: true
            }
        }

        stage('Verify candidate and deploy backend') {
            steps {
                sh '''
                    sudo -n /opt/eolssu/bin/deploy-backend "${GIT_COMMIT}" > deployment.jsonl
                    cat deployment.jsonl
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'deployment.jsonl', allowEmptyArchive: true
                }
            }
        }

        stage('Test and deploy matching frontend') {
            steps {
                build job: 'eolssu-frontend', wait: false,
                    parameters: [string(name: 'REVISION', value: env.GIT_COMMIT)]
            }
        }
    }
}
