// eolssu-ai: develop의 ai 실행 입력이 바뀌면 이미지 빌드 → 이미지 안 pytest → 운영과 같은 조건의 실행 확인 → 배포(S15P31E102-244).
// 잡 이름이 eolssu-로 시작하므로 실패는 기존 Jenkins 실패 알림(S15P31E102-509)이 Mattermost로 보낸다.
pipeline {
    // 전용 슬롯(executor 1, EXCLUSIVE). 백엔드 빌드와 서로 기다리지 않는다. 배포는 공유 lock으로 한 번에 하나다.
    agent { label 'edo1-ai' }

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
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
                    if (!env.GIT_COMMIT?.matches('[0-9a-f]{40}')) {
                        error('Git checkout commit SHA is missing or invalid')
                    }
                }
            }
        }

        stage('AI image tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_build_ai_image.py -v'
            }
        }

        stage('AI deploy tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_deploy_ai.py -v'
            }
        }

        stage('Plan ai image') {
            steps {
                script {
                    sh 'python3 -B infra/jenkins/build-ai-image.py plan "$GIT_COMMIT" > ai-plan.json'
                    env.AI_ACTION = sh(returnStdout: true,
                        script: 'python3 -B -c \'import json; print(json.load(open("ai-plan.json"))["action"])\'').trim()
                    if (env.AI_ACTION == 'skip') {
                        currentBuild.description = 'ai 실행 입력 변경 없음: 빌드·배포 생략'
                    }
                }
            }
            post { always { archiveArtifacts artifacts: 'ai-plan.json', allowEmptyArchive: true } }
        }

        stage('Build ai image') {
            when { expression { env.AI_ACTION == 'deploy' } }
            steps {
                sh 'python3 -B infra/jenkins/build-ai-image.py build "$GIT_COMMIT"'
            }
        }

        stage('AI tests in image') {
            when { expression { env.AI_ACTION == 'deploy' } }
            steps {
                sh 'python3 -B infra/jenkins/build-ai-image.py test "$GIT_COMMIT" --junit ai-test-results/junit.xml'
            }
            post { always { junit testResults: 'ai-test-results/junit.xml', allowEmptyResults: true } }
        }

        stage('Runtime smoke check') {
            when { expression { env.AI_ACTION == 'deploy' } }
            steps {
                sh 'python3 -B infra/jenkins/build-ai-image.py smoke "$GIT_COMMIT"'
            }
        }

        stage('Deploy ai transaction') {
            when { expression { env.AI_ACTION == 'deploy' } }
            steps {
                script {
                    def deployment = load '/opt/eolssu/config/deployment-stages.groovy'
                    deployment('ai-deployment.jsonl') {
                        sh(returnStatus: true, script: 'sudo -n /opt/eolssu/bin/deploy-ai "$GIT_COMMIT" > ai-deployment.jsonl 2>&1')
                    }
                }
            }
            post { always { archiveArtifacts artifacts: 'ai-deployment.jsonl', allowEmptyArchive: true } }
        }
    }
}
