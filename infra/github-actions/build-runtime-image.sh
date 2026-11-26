#!/usr/bin/env bash
set -euo pipefail
: "${REVISION:?develop commit required}"
service=${1:?runtime service required}
[[ "$(git rev-parse HEAD)" == "$REVISION" ]]
case "$service" in
  backend)
    (cd backend && ./gradlew --no-daemon bootJar)
    python3 -B infra/jenkins/build-backend-image.py "$REVISION" --dockerfile infra/jenkins/backend-artifact.Dockerfile
    ;;
  backend-runtime)
    docker build --file infra/runtime/backend-runtime.Dockerfile \
      --label "org.opencontainers.image.revision=$REVISION" \
      --tag "eolssu-backend-runtime:ci-$REVISION" infra/runtime
    docker run --rm --network none --entrypoint ffprobe "eolssu-backend-runtime:ci-$REVISION" -version
    ;;
  frontend)
    (cd frontend && ./gradlew --no-daemon :webApp:wasmJsBrowserDistribution)
    docker build --file infra/jenkins/frontend.Dockerfile \
      --label "org.opencontainers.image.revision=$REVISION" \
      --tag "eolssu-frontend:ci-$REVISION" frontend/webApp/build/dist/wasmJs/productionExecutable
    docker run --rm --network none "eolssu-frontend:ci-$REVISION" sh -ec \
      'test -s /usr/share/nginx/html/index.html && nginx -t'
    ;;
  ai)
    for stage in build test smoke; do
      python3 -B infra/jenkins/build-ai-image.py "$stage" "$REVISION"
    done
    ;;
  omr|playback)
    for stage in build test; do
      python3 -B infra/jenkins/build-service-image.py "$service" "$stage" "$REVISION"
    done
    if [[ "$service" == playback ]]; then
      # The health and invalid-request probes don't render audio. Production assets stay on the server.
      python3 -B "$CI_TOOLS/playback-fixtures.py" "$RUNNER_TEMP"
      python3 -B infra/jenkins/build-service-image.py "$service" smoke "$REVISION" \
        --assets "$RUNNER_TEMP/playback-assets" --janggu "$RUNNER_TEMP/playback-janggu"
    else
      python3 -B infra/jenkins/build-service-image.py "$service" smoke "$REVISION"
    fi
    ;;
  *) echo "Unknown runtime service: $service" >&2; exit 1 ;;
esac
