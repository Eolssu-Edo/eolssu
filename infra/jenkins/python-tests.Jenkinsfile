// S15P31E102-945: Python tests for infra·catalog·data·ai, chosen by the paths changed on develop.
// Runs after each finished eolssu-backend build (success, unstable or failure) instead of polling, so a
// develop change starts the backend deployment first on the single shared executor. A backend build queued
// while this job runs still waits for it. A weekly timer run checks every group against dependency drift.
// It checks out develop again, diffs against the last successful run, and only reads the repository.
pipeline {
    agent any

    parameters {
        booleanParam(name: 'FULL_RUN', defaultValue: false, description: '변경 경로와 관계없이 모든 Python 테스트 묶음을 실행')
    }

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    triggers {
        upstream(upstreamProjects: 'eolssu-backend', threshold: hudson.model.Result.FAILURE)
        cron('H 4 * * 1')
    }

    environment {
        UV_LINK_MODE = 'copy'
        UV_PYTHON_PREFERENCE = 'only-managed'
        PYTHONDONTWRITEBYTECODE = '1'
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
                    if (!env.GIT_COMMIT?.matches('[0-9a-f]{40}')) {
                        error('Git checkout commit SHA is missing or invalid')
                    }
                }
            }
        }

        stage('Tool check') {
            steps {
                sh 'python3 --version && uv --version'
            }
        }

        stage('Plan test groups') {
            steps {
                script {
                    def previous = currentBuild.previousSuccessfulBuild
                    env.PYTHON_TEST_BASE = previous?.buildVariables?.GIT_COMMIT ?: ''
                    def timer = !currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause').isEmpty()
                    def all = (params.FULL_RUN || timer) ? '--all' : ''
                    env.PYTHON_TEST_GROUPS = sh(returnStdout: true, script: """
                        python3 -B infra/jenkins/python-tests.py plan --base '${env.PYTHON_TEST_BASE}' --head '${env.GIT_COMMIT}' ${all}
                    """).trim()
                    currentBuild.description = env.GIT_COMMIT.substring(0, 12) + ': ' + (env.PYTHON_TEST_GROUPS ?: 'no Python changes')
                }
            }
        }

        stage('infra tests') {
            when { expression { (',' + env.PYTHON_TEST_GROUPS + ',').contains(',infra,') } }
            steps {
                sh 'python3 -B infra/jenkins/python-tests.py run infra --work "$WORKSPACE/.python-tests"'
            }
        }

        stage('catalog tests') {
            when { expression { (',' + env.PYTHON_TEST_GROUPS + ',').contains(',catalog,') } }
            steps {
                sh 'python3 -B infra/jenkins/python-tests.py run catalog --work "$WORKSPACE/.python-tests"'
            }
        }

        stage('data tests') {
            when { expression { (',' + env.PYTHON_TEST_GROUPS + ',').contains(',data,') } }
            steps {
                sh 'python3 -B infra/jenkins/python-tests.py run data --work "$WORKSPACE/.python-tests"'
            }
            post { always { junit testResults: '.python-tests/results/data.xml', allowEmptyResults: true } }
        }

        stage('ai tests') {
            when { expression { (',' + env.PYTHON_TEST_GROUPS + ',').contains(',ai,') } }
            steps {
                sh 'python3 -B infra/jenkins/python-tests.py run ai --work "$WORKSPACE/.python-tests"'
            }
            post { always { junit testResults: '.python-tests/results/ai.xml', allowEmptyResults: true } }
        }

        // Known environment-dependent failures: shown as an unstable stage, never failing the build.
        stage('Quarantined tests') {
            when { expression { (',' + env.PYTHON_TEST_GROUPS + ',').contains(',ai,') || (',' + env.PYTHON_TEST_GROUPS + ',').contains(',data,') } }
            steps {
                script {
                    for (group in ['data', 'ai']) {
                        if ((',' + env.PYTHON_TEST_GROUPS + ',').contains(',' + group + ',')) {
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                sh "python3 -B infra/jenkins/python-tests.py run ${group} --quarantine --work \"\$WORKSPACE/.python-tests\""
                            }
                        }
                    }
                }
            }
        }
    }
}
