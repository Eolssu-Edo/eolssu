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

        stage('Checkout verified develop revision') {
            steps {
                script {
                    if (!params.REVISION?.matches('[0-9a-f]{40}')) {
                        error('A full develop commit SHA is required')
                    }
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

        stage('Backend deploy tool tests') {
            steps {
                sh '''
                python3 -B -m unittest discover -s infra/jenkins/tests -p test_deploy_backend.py -v
                '''
            }
        }

        stage('Frontend deploy tool tests') {
            steps {
                sh '''
                python3 -B -m unittest discover -s infra/jenkins/tests -p test_deploy_frontend.py -v
                '''
            }
        }

        stage('Certificate initialization tests') {
            steps {
                sh '''
                RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -p test_init_letsencrypt.py -v
                '''
            }
        }

        stage('Nginx health routing tests') {
            steps {
                sh '''
                RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -p test_nginx_actuator.py -v
                '''
            }
        }

        stage('HTTPS redirect tests') {
            steps {
                sh '''
                RUN_NGINX_TESTS=1 python3 -B -m unittest discover -s infra/scripts/tests -p test_nginx_redirect.py -v
                '''
            }
        }

        stage('Compile Wasm app') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:compileKotlinWasmJs :webApp:compileKotlinWasmJs'
                }
            }
        }

        stage('Compile Wasm tests') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:compileTestKotlinWasmJs'
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

        stage('Compile JavaScript') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:compileKotlinJs'
                }
            }
        }

        stage('Compile JavaScript tests') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:compileTestKotlinJs'
                }
            }
        }

        stage('JavaScript browser tests') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :shared:jsTest'
                }
            }
            post { always { junit testResults: 'frontend/shared/build/test-results/jsBrowserTest/TEST-*.xml', allowEmptyResults: false } }
        }

        stage('Compile production Wasm') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :webApp:compileProductionExecutableKotlinWasmJs'
                }
            }
        }

        stage('Optimize production Wasm') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :webApp:compileProductionExecutableKotlinWasmJsOptimize'
                }
            }
        }

        stage('Bundle production web') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :webApp:wasmJsBrowserProductionWebpack'
                }
            }
        }

        stage('Assemble web distribution') {
            steps {
                dir('frontend') {
                    sh './gradlew --daemon -Dorg.gradle.daemon.idletimeout=60000 :webApp:wasmJsBrowserDistribution'
                }
            }
        }

        stage('Build frontend image') {
            steps {
                sh '''
                docker build --file /opt/eolssu/config/frontend.Dockerfile --label "org.opencontainers.image.revision=${GIT_COMMIT}" --tag "eolssu-frontend:ci-${GIT_COMMIT}" frontend/webApp/build/dist/wasmJs/productionExecutable
                '''
            }
        }

        stage('Archive web distribution') {
            steps {
                archiveArtifacts artifacts: 'frontend/webApp/build/dist/wasmJs/productionExecutable/**', fingerprint: true
            }
        }

        stage('Deploy frontend transaction') {
            steps {
                script {
                    def deployment = load '/opt/eolssu/config/deployment-stages.groovy'
                    deployment('frontend-deployment.jsonl') {
                        sh(returnStatus: true, script: 'sudo -n /opt/eolssu/bin/deploy-frontend "$GIT_COMMIT" > frontend-deployment.jsonl 2>&1')
                    }
                }
            }
            post { always { archiveArtifacts artifacts: 'frontend-deployment.jsonl', allowEmptyArchive: true } }
        }

    }
}
