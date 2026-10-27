// 기술 블로그 배포 파이프라인
// blog 브랜치를 1분마다 polling해서 변경이 있으면 Hugo로 빌드하고, 결과물만 GitHub Pages 저장소에 push
// blog 브랜치는 develop과 기록을 공유하지 않는 블로그 전용 브랜치다. 서비스 코드 잡(develop)과 서로 영향을 주지 않는다.
// Jenkins가 loopback에서만 수신하므로 GitLab webhook 대신 SCM polling을 쓴다.
// 필요: 호스트 에이전트의 Docker·git / Credentials 'eolssu-gitlab'(GitLab 읽기), 'github-blog-token'(Secret text)
pipeline {
    agent any

    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 10, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        pollSCM('* * * * *')
    }

    environment {
        HUGO_IMAGE = 'ghcr.io/gohugoio/hugo:v0.145.0'
        GH_REPO    = 'team-edo/team-edo.github.io'
        GH_BRANCH  = 'main'
        SITE_URL   = 'https://team-edo.github.io/'
        PUBLIC_DIR = 'blog-build/public'
    }

    stages {
        stage('Checkout blog') {
            steps {
                script {
                    deleteDir()
                    // 소스는 site/에, 빌드 결과물은 blog-build/에 둔다
                    dir('site') {
                        def revision = checkout scmGit(
                            branches: [[name: 'refs/heads/blog']],
                            userRemoteConfigs: [[credentialsId: 'eolssu-gitlab',
                                url: 'https://lab.ssafy.com/s15-final/S15P31E102.git',
                                refspec: '+refs/heads/blog:refs/remotes/origin/blog']])
                        env.GIT_COMMIT = revision.GIT_COMMIT
                    }
                    if (!env.GIT_COMMIT?.matches('[0-9a-f]{40}')) {
                        error('Git checkout commit SHA is missing or invalid')
                    }
                }
            }
        }

        stage('Build') {
            steps {
                // 소스는 읽기 전용으로 연결하고 결과물은 workspace의 별도 폴더에 쓴다 (외부 네트워크 불필요)
                sh '''
                    mkdir -p blog-build
                    docker run --rm --network none \
                        --user "$(id -u):$(id -g)" \
                        --env HUGO_CACHEDIR=/tmp/hugo_cache \
                        --mount "type=bind,src=$WORKSPACE/site,dst=/project,readonly" \
                        --mount "type=bind,src=$WORKSPACE/blog-build,dst=/out" \
                        --entrypoint hugo "$HUGO_IMAGE" \
                        --source /project --destination /out/public --noBuildLock \
                        --minify --cleanDestinationDir --baseURL "$SITE_URL"
                    test -f "$PUBLIC_DIR/index.html"
                '''
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([string(credentialsId: 'github-blog-token', variable: 'GH_TOKEN')]) {
                    sh 'bash site/scripts/deploy.sh'
                }
            }
        }
    }
}
