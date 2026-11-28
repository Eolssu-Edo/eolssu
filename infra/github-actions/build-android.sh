#!/usr/bin/env bash
set -euo pipefail

out="$PWD/mobile-out"
mkdir -p "$out"
case "${MODE:?Android build mode required}" in
  android-qa)
    : "${ANDROID_KEYSTORE:?Existing QA debug keystore required}"
    mkdir -p "$HOME/.android"
    umask 077
    trap 'rm -f "$HOME/.android/debug.keystore"' EXIT
    printf '%s' "$ANDROID_KEYSTORE" | base64 --decode > "$HOME/.android/debug.keystore"
    unset ANDROID_KEYSTORE
    version=$(date -u +%s)
    name="qa-$(date -u +%Y%m%d.%H%M%S)-${REVISION:0:8}"
    (cd frontend && ./gradlew --no-daemon --no-configuration-cache \
      -I ../infra/qa-phone/qa-android.init.gradle :shared:testAndroidHostTest :androidApp:assembleDebug \
      "-Peolssu.versionCode=$version" "-Peolssu.versionName=$name")
    filename=eolssu-"$name".apk
    cp frontend/androidApp/build/outputs/apk/debug/androidApp-debug.apk "$out/$filename"
    "$ANDROID_HOME/build-tools/37.0.0/apksigner" verify "$out/$filename"
    ;;
  android-release)
    [[ "$VERSION_CODE" =~ ^[1-9][0-9]{0,8}$ && "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
      { echo 'Supply a positive Android version code and X.Y.Z version name' >&2; exit 1; }
    : "${ANDROID_KEYSTORE:?Android upload keystore required}" "${EOLSSU_STORE_PASSWORD:?Upload keystore password required}"
    umask 077
    export EOLSSU_STORE_FILE="$RUNNER_TEMP/android-upload.keystore"
    printf '%s' "$ANDROID_KEYSTORE" | base64 --decode > "$EOLSSU_STORE_FILE"
    unset ANDROID_KEYSTORE
    trap 'rm -f "$EOLSSU_STORE_FILE"' EXIT
    EOLSSU_KEY_ALIAS=$(keytool -list -keystore "$EOLSSU_STORE_FILE" -storepass:env EOLSSU_STORE_PASSWORD |
      awk -F, '/PrivateKeyEntry/ && !seen++ {print $1}')
    : "${EOLSSU_KEY_ALIAS:?No private key in upload keystore}"
    export EOLSSU_KEY_ALIAS EOLSSU_KEY_PASSWORD="$EOLSSU_STORE_PASSWORD"
    (cd frontend && ./gradlew --no-daemon --no-configuration-cache --max-workers=2 \
      :shared:testAndroidHostTest :androidApp:lintRelease :androidApp:bundleRelease \
      "-Peolssu.versionCode=$VERSION_CODE" "-Peolssu.versionName=$VERSION_NAME")
    filename=eolssu-"$VERSION_NAME"-vc"$VERSION_CODE".aab
    cp frontend/androidApp/build/outputs/bundle/release/androidApp-release.aab "$out/$filename"
    keytool -printcert -jarfile "$out/$filename"
    ;;
  *) echo 'Unknown Android build mode' >&2; exit 1 ;;
esac
printf '%s\n' "$REVISION" > "$out/revision.txt"
(cd "$out" && sha256sum "$filename" > "$filename.sha256")
