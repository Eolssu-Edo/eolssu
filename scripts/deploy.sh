#!/usr/bin/env bash
# Hugo 빌드 결과물(blog/public)만 GitHub Pages 저장소에 push
# 필요 환경 변수:
#   GH_TOKEN   GitHub 토큰 (Pages 저장소 Contents 쓰기 권한만)
#   GH_REPO    예: team-edo/team-edo.github.io
#   GH_BRANCH  배포 브랜치 (기본 main)
#   PUBLIC_DIR 빌드 결과물 경로 (기본 blog/public)
set -euo pipefail

# No argument keeps the existing one-shot entrypoint. Named phases use the
# private workspace supplied by the caller, which also cleans it up.
PHASE="${1:-all}"
GH_BRANCH="${GH_BRANCH:-main}"
PUBLIC_DIR="${PUBLIC_DIR:-blog/public}"
if [ "$PHASE" = all ]; then
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT
else
  WORK="${PAGES_WORK_DIR:?PAGES_WORK_DIR is required for named phases}"
fi

prepare() {
  : "${GH_TOKEN:?GH_TOKEN is required}"
  : "${GH_REPO:?GH_REPO is required}"
  local remote="https://x-access-token:${GH_TOKEN}@github.com/${GH_REPO}.git"
  mkdir -p "$WORK"
  chmod 700 "$WORK"
  if git ls-remote --exit-code --heads "$remote" "$GH_BRANCH" >/dev/null 2>&1; then
    git clone --quiet --depth 1 --branch "$GH_BRANCH" "$remote" "$WORK"
    # Do not retain credentials between phases.
    git -C "$WORK" remote remove origin
  else
    git init --quiet "$WORK"
    git -C "$WORK" checkout --quiet -b "$GH_BRANCH"
  fi
}

sync_files() {
  test -f "$PUBLIC_DIR/index.html"
  test -d "$WORK/.git"
  find "$WORK" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
  cp -a "$PUBLIC_DIR"/. "$WORK/"
}

commit_files() {
  git -C "$WORK" config user.name "edo-blog-bot"
  git -C "$WORK" config user.email "edo-blog-bot@users.noreply.github.com"
  git -C "$WORK" add -A
  if git -C "$WORK" diff --cached --quiet; then
    echo "바뀐 내용이 없어서 배포하지 않아요."
    return
  fi
  git -C "$WORK" commit --quiet -m "deploy: $(date -u +%Y-%m-%dT%H:%MZ)"
  touch "$WORK/.git/publish-needed"
}

push_files() {
  if [ ! -f "$WORK/.git/publish-needed" ]; then
    echo "No changed content to publish"
    return
  fi
  : "${GH_TOKEN:?GH_TOKEN is required}"
  : "${GH_REPO:?GH_REPO is required}"
  git -C "$WORK" push --quiet "https://x-access-token:${GH_TOKEN}@github.com/${GH_REPO}.git" "HEAD:${GH_BRANCH}"
  rm "$WORK/.git/publish-needed"
  echo "배포 완료: https://github.com/${GH_REPO}"
}

case "$PHASE" in
  all) prepare; sync_files; commit_files; push_files ;;
  prepare) prepare ;;
  sync) sync_files ;;
  commit) commit_files ;;
  push) push_files ;;
  *) echo "Unknown deploy phase" >&2; exit 2 ;;
esac
