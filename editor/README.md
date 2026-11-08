# 블로그 작성자 확인과 글 편집

현재 운영 환경은 `/edit/`에서 등록된 블로그 작성자인지 확인합니다. 댓글을 남길 때 쓰는 GitHub 계정으로 로그인할 수 있습니다. 작성자 확인에 쓰는 별도 OAuth 앱은 이메일 읽기 권한만 요청합니다.

## 로그인 전용 운영

GitHub OAuth 앱의 콜백은 공개 블로그의 `/edit/` 주소입니다. 서버에 `BLOG_ORIGIN`, `EDITOR_GITHUB_CLIENT_ID`, `EDITOR_GITHUB_CLIENT_SECRET_FILE`, `EDITOR_GITHUB_AUTHOR_EMAILS_FILE`을 설정합니다. 이메일 파일은 `{"작성자 이름":"author@example.org"}` 형식입니다. `edo1`의 `/opt/eolssu/blog-editor/credentials`에 있는 비밀키와 이메일 파일은 읽기 전용으로 컨테이너에 마운트합니다. 파일은 root 소유, 그룹 `65532`, 모드 `0440`입니다. 로컬에서는 Git이 무시하는 `editor/.author-emails.local.json`을 사용할 수 있습니다.

`edo1`의 `eolssu-blog-editor` 컨테이너는 런타임 브리지의 `172.18.0.1:18120`에만 바인딩됩니다. TLS 프록시는 `/blog-editor/config`와 `/blog-editor/session`을 각각 `/config`, `/session`으로 전달합니다. `/session` 요청은 크기 8KB, IP당 분당 10회, 순간 허용량 5회로 제한합니다. HTTPS 초기화 도구로 `nginx.tls.conf`를 재생성했다면 두 프록시 경로와 호출 제한을 다시 적용해야 합니다.

Hugo의 `editorAPI`는 HTTPS API 주소입니다. 이 값을 비우면 로그인 버튼이 사라집니다. `editorWrites`가 `false`이면 글 상세의 편집 링크와 저장 양식이 숨겨집니다. 운영 서버에서 `EDITOR_GITLAB_WRITE_TOKEN`을 비워 두면 글을 읽거나 쓰는 GitLab 요청과 편집 세션 발급이 비활성화됩니다.

```sh
python -m pip install -r editor/requirements.txt
python -m unittest discover -s editor/tests -v
gunicorn --bind 127.0.0.1:8120 --timeout 45 'editor.app:create_app()'
```

## 저장을 활성화할 경우

`blog` 브랜치에 직접 커밋할 수 있는 전용 계정의 `EDITOR_GITLAB_WRITE_TOKEN`과 32자 이상 무작위 `EDITOR_SESSION_SECRET`을 서버에 설정해야 합니다. HTTPS 프록시에 `/blog-editor/post`를 연결하고 Hugo `editorWrites`를 `true`로 설정해야 합니다.

편집 세션은 GitHub가 인증한 이메일과 원본 글의 `author`가 일치할 때만 발급됩니다. 세션은 글 하나에 한정되고 15분 뒤 만료됩니다. 서버는 저장 직전 작성자와 원본 버전을 다시 확인합니다. 제목과 일반 Markdown 본문만 갱신합니다. 수정 커밋은 `blog`에 직접 반영되며, Jenkins가 공개 사이트를 다시 빌드합니다. 이미 저장한 수정은 Git 이력에서 해당 커밋을 되돌릴 수 있습니다.
