# 작성자용 글 편집

공개 글에서 **글 수정**을 열고 GitHub로 로그인하면 제목과 Markdown 본문을 편집할 수 있습니다. 저장은 GitLab `blog` 브랜치의 해당 파일만 갱신합니다. 기존 Jenkins가 새 커밋을 감지해 공개 사이트를 다시 빌드합니다.

편집 서버는 로그인한 계정에서 GitHub가 인증한 이메일을 원본 글의 `author`와 대조합니다. 댓글을 남길 때 쓰는 GitHub 계정으로 로그인할 수 있습니다. 편집은 별도의 OAuth 앱으로 이메일 읽기 권한만 요청합니다. 이메일은 서버에서 권한을 확인하는 데 쓰며 공개 페이지나 편집 세션에 포함하지 않습니다. 매핑에 없는 작성자와 팀 명의 글은 편집할 수 없습니다. 편집 세션은 글 하나에 한정되고 15분 뒤 만료됩니다. 서버는 저장 직전 작성자를 다시 확인하며, 그사이 원본이 바뀌면 새 글을 덮어쓰지 않습니다. 작성자, 분류, 주소 등 나머지 머리말은 보존합니다. 일반 Markdown을 편집할 수 있으며 Hugo shortcode는 저장할 수 없습니다.

## 준비와 실행

1. GitHub에 편집 전용 OAuth 앱을 등록합니다. Authorization callback URL은 공개 블로그의 `/edit/` 주소입니다. 로그인은 `user:email` scope만 사용합니다.
2. GitLab 프로젝트 전용 서비스 계정에 `blog` 브랜치의 직접 커밋 권한을 부여합니다. 토큰은 편집 서버에만 둡니다. 프로젝트나 브랜치의 기존 쓰기 권한 정책을 유지하면서 전용 계정을 허용할 수 있는지 확인합니다.
3. 환경변수로 `BLOG_ORIGIN`, `EDITOR_GITHUB_CLIENT_ID`, `EDITOR_GITHUB_CLIENT_SECRET`, `EDITOR_GITLAB_WRITE_TOKEN`, `EDITOR_SESSION_SECRET`, `EDITOR_GITHUB_AUTHOR_EMAILS_FILE`을 설정합니다. `BLOG_ORIGIN`은 공개 블로그의 정확한 origin입니다. 세션 비밀값은 최소 32자의 무작위 값입니다. 이메일 파일은 `{"작성자 이름":"author@example.org"}` 형식의 비공개 JSON이며 서비스 계정만 읽을 수 있게 설정합니다. 로컬에서는 Git이 무시하는 `editor/.author-emails.local.json`을 사용할 수 있습니다.
4. Python 3.11 이상으로 HTTPS 프록시 뒤에서 편집 서버를 실행합니다. 프록시에서 요청 크기와 호출 빈도를 제한합니다.
5. Hugo 빌드에 `HUGO_PARAMS_EDITORAPI`로 HTTPS 편집 API 주소를 전달합니다. 예를 들어 `https://editor.example.org/`를 지정합니다. 비워 두면 글 상세에서 편집 링크가 숨겨집니다.

```sh
python -m pip install -r editor/requirements.txt
python -m unittest discover -s editor/tests -v
gunicorn --bind 127.0.0.1:8120 --timeout 45 'editor.app:create_app()'
```

공개를 끄려면 편집 서버의 쓰기 토큰을 폐기하고 Hugo 편집 API 주소를 비운 뒤 다시 배포합니다. 이미 저장한 수정은 Git 이력에서 해당 커밋을 되돌릴 수 있습니다.
