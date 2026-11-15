pipeline {
    agent any

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        // 25 minutes: the database test stage adds about 3 minutes and must not push deploy past the limit.
        timeout(time: 25, unit: 'MINUTES')
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

        stage('Backend image tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_build_backend_image.py -v'
            }
        }

        stage('Backend deploy tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_deploy_backend.py -v'
            }
        }

        stage('Database wrapper tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_run_database_tests.py -v'
            }
        }

        stage('Certificate initialization tests') {
            steps {
                sh 'RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -p test_init_letsencrypt.py -v'
            }
        }

        stage('Nginx health routing tests') {
            steps {
                sh 'RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -p test_nginx_actuator.py -v'
            }
        }

        stage('HTTPS redirect tests') {
            steps {
                sh 'RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -p test_nginx_redirect.py -v'
            }
        }

        stage('Clean backend outputs') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 clean'
                }
            }
        }

        stage('Compile backend') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 compileKotlin'
                }
            }
        }

        stage('Compile backend tests') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 compileTestKotlin'
                }
            }
        }

        stage('Backend unit tests') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 test'
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

        stage('Backend database tests') {
            options { timeout(time: 10, unit: 'MINUTES') }
            steps {
                dir('backend') {
                    // Disposable loopback MySQL per run; never the runtime database.
                    sh 'python3 -B ../infra/jenkins/run-database-tests.py -- ./gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 databaseTest'
                }
            }
            post { always { junit testResults: 'backend/build/test-results/databaseTest/*.xml', allowEmptyResults: false } }
        }

        stage('Package executable JAR') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 bootJar'
                }
            }
        }

        stage('Backend build checks') {
            steps {
                dir('backend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 build'
                }
            }
        }

        stage('Validate runtime Compose') {
            steps {
                sh '''
                docker compose --env-file /opt/eolssu/config/ci.env.example -f /opt/eolssu/config/compose.yml --profile backend-slots config --quiet
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
                python3 -B infra/jenkins/build-backend-image.py "$GIT_COMMIT" --dockerfile /opt/eolssu/config/backend-artifact.Dockerfile
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

    }
}
