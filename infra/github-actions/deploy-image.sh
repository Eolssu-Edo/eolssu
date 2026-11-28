#!/usr/bin/env bash
# Install under /opt/eolssu/bin and run as the operator on the production Linux VM.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo 'Usage: deploy-github-image SERVICE COMMIT SHA256_DIGEST' >&2
  exit 2
fi
service=$1
revision=$2
digest=$3
[[ "$revision" =~ ^[0-9a-f]{40}$ && "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] ||
  { echo 'Expected a full commit SHA and sha256 digest' >&2; exit 2; }
case "$service" in
  backend|frontend|ai) deploy=("/opt/eolssu/bin/deploy-$service") ;;
  omr|playback) deploy=(/opt/eolssu/bin/deploy-service-image "$service") ;;
  *) echo 'Unknown service' >&2; exit 2 ;;
esac
platform=linux/arm64
if [[ "$service" == omr ]]; then
  platform=linux/amd64
fi

# Serialize importing and tagging candidates. The deployment tools acquire their own shared lock.
exec 9>/run/lock/eolssu-ghcr-deploy.lock
flock -w 120 9
remote="ghcr.io/team-edo/eolssu-$service@$digest"
candidate="eolssu-$service:ci-$revision"
docker pull --platform "$platform" "$remote"
image=$(docker image inspect --format '{{.Id}}' "$remote")
label=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image")
arch=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image")
[[ "$revision" == "$label" && "$arch" == "$platform" ]] ||
  { echo 'Image revision or architecture does not match the release' >&2; exit 1; }
existing=$(docker image inspect --format '{{.Id}}' "$candidate" 2>/dev/null || true)
[[ -z "$existing" || "$existing" == "$image" ]] ||
  { echo 'The commit already has a different local candidate image' >&2; exit 1; }
docker tag "$image" "$candidate"
export DOCKER_DEFAULT_PLATFORM="$platform"
"${deploy[@]}" "$revision"
