// eolssu-omr·eolssu-playback: develop의 해당 이미지 입력이 바뀌면 이미지 빌드 → 이미지 안 테스트 → 운영과 같은 조건의
// 실행 확인 → 진행 중 작업이 끝난 뒤 배포(S15P31E102-951). 두 잡이 이 파일 하나를 쓰고, 서비스는 잡 이름에서 정한다.
// 잡 이름이 eolssu-로 시작하므로 실패는 기존 Jenkins 실패 알림(S15P31E102-509)이 Mattermost로 보낸다.
pipeline {
    // ai 잡과 같은 전용 슬롯(executor 1, EXCLUSIVE). 이미지 빌드를 한 번에 하나만 돌려 4코어 운영 서버의 CPU·메모리를
    // 지키고, 백엔드 빌드(edo1-host)와는 서로 기다리지 않는다. 배포는 공유 lock으로 한 번에 하나다.
    agent { label 'edo1-ai' }

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        // omr 빌드(최대 60분)와 진행 중 작업 대기(최대 20분)를 담는다.
        timeout(time: 120, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        pollSCM('* * * * *')
    }

    stages {
        stage('Select service') {
            steps {
                script {
                    env.SERVICE = env.JOB_NAME.replaceFirst('^eolssu-', '')
                    if (!(env.SERVICE in ['omr', 'playback'])) {
                        error('Job name must be eolssu-omr or eolssu-playback')
                    }
                }
            }
        }

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

        stage('Image tool tests') {
            steps {
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_build_service_image.py -v'
                sh 'python3 -B -m unittest discover -s infra/jenkins/tests -p test_deploy_service_image.py -v'
            }
        }

        stage('Plan image') {
            steps {
                script {
                    sh 'python3 -B infra/jenkins/build-service-image.py "$SERVICE" plan "$GIT_COMMIT" > service-plan.json'
                    env.IMAGE_ACTION = sh(returnStdout: true,
                        script: 'python3 -B -c \'import json; print(json.load(open("service-plan.json"))["action"])\'').trim()
                    if (env.IMAGE_ACTION == 'skip') {
                        currentBuild.description = env.SERVICE + ' 이미지 입력 변경 없음: 빌드·배포 생략'
                    }
                }
            }
            post { always { archiveArtifacts artifacts: 'service-plan.json', allowEmptyArchive: true } }
        }

        stage('Build image') {
            when { expression { env.IMAGE_ACTION == 'deploy' } }
            steps {
                sh 'python3 -B infra/jenkins/build-service-image.py "$SERVICE" build "$GIT_COMMIT"'
            }
        }

        stage('Tests in image') {
            when { expression { env.IMAGE_ACTION == 'deploy' } }
            steps {
                sh 'python3 -B infra/jenkins/build-service-image.py "$SERVICE" test "$GIT_COMMIT" --junit service-test-results/junit.xml'
            }
            post { always { junit testResults: 'service-test-results/junit.xml', allowEmptyResults: true } }
        }

        stage('Runtime smoke check') {
            when { expression { env.IMAGE_ACTION == 'deploy' } }
            steps {
                sh 'python3 -B infra/jenkins/build-service-image.py "$SERVICE" smoke "$GIT_COMMIT"'
            }
        }

        stage('Deploy transaction') {
            when { expression { env.IMAGE_ACTION == 'deploy' } }
            steps {
                script {
                    def deployment = load '/opt/eolssu/config/deployment-stages.groovy'
                    deployment('service-deployment.jsonl') {
                        sh(returnStatus: true, script: 'sudo -n /opt/eolssu/bin/deploy-service-image "$SERVICE" "$GIT_COMMIT" > service-deployment.jsonl 2>&1')
                    }
                }
            }
            post { always { archiveArtifacts artifacts: 'service-deployment.jsonl', allowEmptyArchive: true } }
        }
    }
}
