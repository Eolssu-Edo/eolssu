#!/usr/bin/env bash
# Hugo 빌드 결과물(blog/public)만 GitHub Pages 저장소에 push
# 필요 환경 변수:
#   GH_TOKEN   GitHub 토큰 (Pages 저장소 Contents 쓰기 권한만)
#   GH_REPO    예: team-ido/team-ido.github.io
#   GH_BRANCH  배포 브랜치 (기본 main)
#   PUBLIC_DIR 빌드 결과물 경로 (기본 blog/public)
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN이 없어요}"
: "${GH_REPO:?GH_REPO가 없어요}"
GH_BRANCH="${GH_BRANCH:-main}"
PUBLIC_DIR="${PUBLIC_DIR:-blog/public}"

if [ ! -f "$PUBLIC_DIR/index.html" ]; then
  echo "빌드 결과물이 없어요: $PUBLIC_DIR/index.html" >&2
  exit 1
fi

REMOTE="https://x-access-token:${GH_TOKEN}@github.com/${GH_REPO}.git"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 첫 배포라 브랜치가 없으면 새로 만듦
if git ls-remote --exit-code --heads "$REMOTE" "$GH_BRANCH" >/dev/null 2>&1; then
  git clone --quiet --depth 1 --branch "$GH_BRANCH" "$REMOTE" "$WORK"
else
  git init --quiet "$WORK"
  git -C "$WORK" checkout --quiet -b "$GH_BRANCH"
  git -C "$WORK" remote add origin "$REMOTE"
fi

# 이전 결과물을 지우고 새 결과물로 교체 (.git은 유지)
find "$WORK" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -a "$PUBLIC_DIR"/. "$WORK"/

cd "$WORK"
git config user.name "ido-blog-bot"
git config user.email "ido-blog-bot@users.noreply.github.com"
git add -A

if git diff --cached --quiet; then
  echo "바뀐 내용이 없어서 배포하지 않아요."
  exit 0
fi

# 커밋 메시지에는 GitLab 커밋 정보를 넣지 않음 (공개 저장소)
git commit --quiet -m "deploy: $(date -u +%Y-%m-%dT%H:%MZ)"
git push --quiet origin "HEAD:${GH_BRANCH}"
echo "배포 완료: https://github.com/${GH_REPO}"
