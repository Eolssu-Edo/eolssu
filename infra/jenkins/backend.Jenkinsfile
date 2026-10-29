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
        stage('Clean workspace') {
            steps {
                deleteDir()
            }
        }

        stage('Checkout develop') {
            steps {
                script {
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

        stage('Clean backend outputs') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon clean'
                }
            }
        }

        stage('Compile backend') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon compileKotlin'
                }
            }
        }

        stage('Compile backend tests') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon compileTestKotlin'
                }
            }
        }

        stage('Backend unit tests') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon test'
                }
            }
            post { always { junit testResults: 'backend/build/test-results/test/*.xml', allowEmptyResults: false } }
        }

        stage('Recording media tests') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon mediaTest'
                }
            }
            post { always { junit testResults: 'backend/build/test-results/mediaTest/*.xml', allowEmptyResults: false } }
        }

        stage('Package executable JAR') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon bootJar'
                }
            }
        }

        stage('Backend build checks') {
            steps {
                dir('backend') {
                    sh './gradlew --no-daemon build'
                }
            }
        }

        stage('Validate runtime Compose') {
            steps {
                sh '''
                docker compose --env-file /opt/eolssu/config/ci.env.example -f /opt/eolssu/config/compose.yml config --quiet
                '''
            }
        }

        stage('Validate runtime Nginx') {
            steps {
                sh '''
                docker run --rm --network none --mount type=bind,src=/opt/eolssu/config/nginx.conf,dst=/etc/nginx/conf.d/default.conf,readonly nginx:1.30.6-alpine nginx -t
                '''
            }
        }

        stage('Build backend image') {
            steps {
                sh '''
                docker build --file /opt/eolssu/config/backend.Dockerfile --label "org.opencontainers.image.revision=${GIT_COMMIT}" --tag "eolssu-backend:ci-${GIT_COMMIT}" backend
                '''
            }
        }

        stage('Archive executable JAR') {
            steps {
                archiveArtifacts artifacts: 'backend/build/libs/*.jar', excludes: '**/*-plain.jar', fingerprint: true
            }
        }

        stage('Deploy backend transaction') {
            steps {
                script {
                    def deployment = load '/opt/eolssu/config/deployment-stages.groovy'
                    deployment('deployment.jsonl') {
                        sh(returnStatus: true, script: 'sudo -n /opt/eolssu/bin/deploy-backend "$GIT_COMMIT" > deployment.jsonl 2>&1')
                    }
                }
            }
            post { always { archiveArtifacts artifacts: 'deployment.jsonl', allowEmptyArchive: true } }
        }

        stage('Queue matching frontend') {
            steps {
                build job: 'eolssu-frontend', wait: false,
                    parameters: [string(name: 'REVISION', value: env.GIT_COMMIT)]
            }
        }

    }
}
