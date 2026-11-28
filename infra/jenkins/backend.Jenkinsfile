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
                        userRemoteConfigs: [[credentialsId: 'eolssu-github',
                            url: 'https://github.com/team-edo/eolssu.git',
                            refspec: '+refs/heads/develop:refs/remotes/origin/develop']])
                    env.GIT_COMMIT = revision.GIT_COMMIT
                    def previous = revision.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                    env.IOS_QA_BASE = previous?.matches('[0-9a-f]{40}') ? previous : ''
                    if (!env.GIT_COMMIT?.matches('[0-9a-f]{40}')) {
                        error('Git checkout commit SHA is missing or invalid')
                    }
                }
            }
        }

        // S15P31E102-1270: 웹 빌드 입력이 바뀐 develop 커밋은 웹 브라우저 테스트 전용 잡(배포 없음)에 넘긴다.
        // 백엔드 검사·배포 결과와 관계없이 웹 테스트를 돌리도록 백엔드 단계보다 먼저 예약한다.
        stage('Queue web tests') {
            steps {
                script {
                    def changed = sh(returnStatus: true, script: '''
                        base="$IOS_QA_BASE"
                        if [ -z "$base" ] || ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
                            base=$(git rev-parse --verify "${GIT_COMMIT}^1" 2>/dev/null) ||
                                base=$(git hash-object -t tree /dev/null)
                        fi
                        echo "Web test diff: $base .. $GIT_COMMIT"
                        git diff --quiet --no-ext-diff "$base" "$GIT_COMMIT" -- \
                            frontend/shared/src/commonMain/ frontend/shared/src/commonTest/ \
                            frontend/shared/src/webMain/ frontend/shared/src/webTest/ \
                            frontend/shared/src/jsMain/ frontend/shared/src/jsTest/ \
                            frontend/shared/src/wasmJsMain/ frontend/shared/src/wasmJsTest/ \
                            frontend/shared/build.gradle.kts frontend/build.gradle.kts \
                            frontend/settings.gradle.kts frontend/gradle.properties frontend/gradle/ frontend/gradlew \
                            frontend/kotlin-js-store/
                    ''')
                    if (changed == 1) {
                        build job: 'eolssu-web-tests', parameters: [string(name: 'REVISION', value: env.GIT_COMMIT)], wait: false
                    } else if (changed == 0) {
                        echo 'Web tests skipped: no web build inputs changed'
                    } else {
                        unstable("Web tests skipped: Git diff failed (exit ${changed})")
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
            steps {
                // Warning-only until the 2026-11-18 release freeze ends: a failure or timeout marks
                // the build UNSTABLE (alerted to Mattermost) without blocking deploy. S15P31E102-946
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    timeout(time: 10, unit: 'MINUTES') {
                        dir('backend') {
                            // Disposable loopback MySQL per run; never the runtime database.
                            sh 'python3 -B ../infra/jenkins/run-database-tests.py -- ./gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 databaseTest'
                        }
                    }
                }
            }
            post { always { junit testResults: 'backend/build/test-results/databaseTest/*.xml', allowEmptyResults: true } }
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

        stage('Queue Android QA APK') {
            steps {
                build job: 'eolssu-android-qa', parameters: [string(name: 'REVISION', value: env.GIT_COMMIT)], wait: false
            }
        }

        stage('Queue iOS QA IPA') {
            steps {
                script {
                    def changed = sh(returnStatus: true, script: '''
                        base="$IOS_QA_BASE"
                        if [ -z "$base" ] || ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
                            base=$(git rev-parse --verify "${GIT_COMMIT}^1" 2>/dev/null) ||
                                base=$(git hash-object -t tree /dev/null)
                        fi
                        echo "iOS QA diff: $base .. $GIT_COMMIT"
                        git diff --quiet --no-ext-diff "$base" "$GIT_COMMIT" -- \
                            frontend/iosApp/ \
                            frontend/shared/src/commonMain/ frontend/shared/src/iosMain/ \
                            frontend/shared/src/nativeMain/ frontend/shared/src/appleMain/ \
                            frontend/shared/src/iosArm64Main/ frontend/shared/src/iosSimulatorArm64Main/ \
                            frontend/shared/build.gradle.kts frontend/build.gradle.kts \
                            frontend/settings.gradle.kts frontend/gradle.properties frontend/gradle/ frontend/gradlew
                    ''')
                    if (changed == 1) {
                        build job: 'eolssu-ios-qa', parameters: [string(name: 'REVISION', value: env.GIT_COMMIT)], wait: false
                    } else if (changed == 0) {
                        echo 'iOS QA skipped: no iOS build inputs changed'
                    } else {
                        unstable("iOS QA skipped: Git diff failed (exit ${changed})")
                    }
                }
            }
        }

    }
}
