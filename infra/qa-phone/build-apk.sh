#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?EDO1 Android SDK path is required}"
: "${GIT_COMMIT:?verified full revision required}"
: "${BUILD_NUMBER:?Jenkins build number required}"
[[ "$GIT_COMMIT" =~ ^[a-f0-9]{40}$ && "$BUILD_NUMBER" =~ ^[0-9]+$ ]]
[[ "$(git rev-parse HEAD)" == "$GIT_COMMIT" ]]
# Use the existing debug identity in Jenkins HOME on every build. Never auto-uninstall on the phone.
export ANDROID_SDK_ROOT="$ANDROID_HOME"
version_code=$(date -u +%s)
version_name="qa-$(date -u +%Y%m%d.%H%M%S)-${GIT_COMMIT:0:8}"
cd frontend
./gradlew --no-daemon -I /opt/eolssu/config/qa-android.init.gradle :shared:testAndroidHostTest :androidApp:assembleDebug \
  "-Peolssu.versionCode=${version_code}" "-Peolssu.versionName=${version_name}"
cd ..
python3 /opt/eolssu/config/publish-apk.py --root /opt/eolssu/apks \
  --apk frontend/androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --revision "$GIT_COMMIT" --build "$BUILD_NUMBER" --aapt "$ANDROID_HOME/build-tools/37.0.0/aapt"
