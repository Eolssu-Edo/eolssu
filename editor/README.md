# 블로그 작성자 확인과 글 편집

공개 글에서 **글 수정**을 열고 GitHub로 로그인하면 본인이 작성한 글의 제목과 Markdown 본문을 수정할 수 있습니다. **글 삭제**를 누르면 글 제목을 확인한 뒤 삭제할 수 있습니다. `/edit/`에서는 등록된 블로그 작성자인지 확인할 수 있습니다. 댓글을 남길 때 쓰는 GitHub 계정으로 로그인할 수 있습니다. 작성자 확인에 쓰는 별도 OAuth 앱은 이메일 읽기 권한만 요청합니다.

## 운영 설정

GitHub OAuth 앱의 콜백은 공개 블로그의 `/edit/` 주소입니다. 서버에 `BLOG_ORIGIN`, `EDITOR_GITHUB_CLIENT_ID`, `EDITOR_GITHUB_CLIENT_SECRET_FILE`, `EDITOR_GITHUB_AUTHOR_EMAILS_FILE`을 설정합니다. 이메일 파일은 `{"작성자 이름":"author@example.org"}` 형식입니다. `edo1`의 `/opt/eolssu/blog-editor/credentials`에 있는 비밀키와 이메일 파일은 읽기 전용으로 컨테이너에 마운트합니다. 파일은 root 소유, 그룹 `65532`, 모드 `0440`입니다. 로컬에서는 Git이 무시하는 `editor/.author-emails.local.json`을 사용할 수 있습니다.

`edo1`의 `eolssu-blog-editor-writable` 컨테이너는 런타임 브리지의 `172.18.0.1:18121`에만 바인딩됩니다. TLS 프록시는 `/blog-editor/config`, `/blog-editor/session`, `/blog-editor/post`를 컨테이너의 `/config`, `/session`, `/post`로 전달합니다. `/session`과 `/post`는 각각 요청 크기를 8KB와 270KB로 제한하고 IP별 분당 10회 제한을 공유합니다. HTTPS 초기화 도구로 `nginx.tls.conf`를 재생성했다면 프록시 경로와 호출 제한을 다시 적용해야 합니다.

Hugo의 `editorAPI`는 HTTPS API 주소입니다. 이 값을 비우면 로그인 버튼이 사라집니다. 로그인 전용으로 되돌릴 때는 프록시에서 `/blog-editor/post` 경로를 제거하고 쓰기 토큰 없이 컨테이너를 실행합니다. Hugo `editorWrites`를 `false`로 설정하면 글 상세의 편집 링크와 저장 양식이 숨겨집니다.

```sh
python -m pip install -r editor/requirements.txt
python -m unittest discover -s editor/tests -v
gunicorn --bind 127.0.0.1:8120 --timeout 45 'editor.app:create_app()'
```

## 저장 권한

`EDITOR_GITLAB_WRITE_TOKEN_FILE`은 `blog` 브랜치에 직접 커밋할 수 있는 GitLab 프로젝트 토큰 파일입니다. `EDITOR_SESSION_SECRET_FILE`은 32자 이상 무작위 세션 키 파일입니다. 두 파일은 `edo1`에서 기존 OAuth 자격 증명과 같은 권한으로 읽기 전용 마운트합니다. 개발 환경에서는 각각 `_FILE`을 뺀 환경변수에 값을 직접 설정할 수 있습니다.

편집 세션은 GitHub가 인증한 이메일과 원본 글의 `author`가 일치할 때만 발급됩니다. 세션은 글 하나에 한정되고 15분 뒤 만료됩니다. 서버는 저장 직전 작성자와 원본 버전을 다시 확인합니다. 제목과 일반 Markdown 본문만 갱신합니다. 수정 커밋은 `blog`에 직접 반영되며, Jenkins가 공개 사이트를 다시 빌드합니다. 이미 저장한 수정은 Git 이력에서 해당 커밋을 되돌릴 수 있습니다.

삭제 요청도 같은 세션과 원본 버전을 사용합니다. 서버는 작성자를 다시 확인하고 GitLab에 마지막 파일 커밋을 함께 넘깁니다. 삭제 커밋이 반영된 뒤 자동 빌드가 끝나면 공개 목록과 검색에서 글이 빠집니다. 복구할 때는 삭제 커밋을 되돌립니다. **글 삭제** 버튼은 삭제를 지원하는 편집 API가 응답할 때 표시됩니다.

## 주간 조회수

추천 글은 한국 날짜로 오늘과 이전 6일의 글별 조회수를 합산합니다. 조회수가 같거나 집계가 없는 자리에는 최신 글을 우선합니다. 홈은 집계 서버가 비어 있거나 응답할 수 없어도 Hugo가 생성한 최신 글 3편을 추천 영역에 표시합니다.

공개 글 상세를 브라우저에서 열면 표준 글 경로 하나를 `POST /views`로 전송합니다. `GET /views`는 글 경로별 합계를 반환합니다. 집계에는 날짜·글 경로·조회 횟수만 저장하며, 새 조회를 저장할 때 기간 밖의 데이터를 지웁니다.

운영에서는 기존 편집 서버 이미지를 이 변경으로 다시 빌드합니다. 컨테이너 UID/GID `65532`가 쓸 수 있는 영속 디렉터리를 `/var/lib/blog-views`에 마운트하고 `BLOG_VIEWS_DB=/var/lib/blog-views/views.sqlite3`를 설정합니다. 이 설정이 없으면 조회수 API가 비활성화됩니다. 기존 편집 자격 증명과 라우트 설정은 그대로 사용합니다.

TLS 프록시의 `http` 블록에 `limit_req_zone $binary_remote_addr zone=blog_views:1m rate=30r/m;`을 추가합니다. 기존 블로그 편집 경로와 별도로 공개 주소를 연결합니다.

```nginx
location = /blog-editor/views {
    client_max_body_size 256;
    limit_req zone=blog_views burst=10 nodelay;
    limit_req_status 429;
    access_log off;
    proxy_pass http://172.18.0.1:18121/views;
}
```

설정 검사와 재적용 후 공개 블로그에서 글 하나를 열고, 홈에서 `GET /blog-editor/views` 응답에 해당 경로의 조회수가 포함되는지 확인합니다. 신규 프록시 경로는 Hugo Pages 자동 배포에 포함되지 않습니다.
