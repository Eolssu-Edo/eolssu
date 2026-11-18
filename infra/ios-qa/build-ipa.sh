#!/usr/bin/env bash
# S15P31E102-444: paid-team Ad Hoc archive/export, independent of iPhone connectivity.
set -euo pipefail
# Prevent idle sleep only for this build; do not change system power settings.
/usr/bin/caffeinate -i -w $$ &
# S15P31E102-908: a build cut off by an agent disconnect keeps running and writes the same workspace.
# The node has one executor, so any other copy of this script or its xcodebuild is a leftover.
derived="${IOS_DERIVED_DATA:-$HOME/jenkins-agent/ios-derived}"
stop_tree() {
  local child
  for child in $(pgrep -P "$1"); do stop_tree "$child"; done
  kill -TERM "$1" 2>/dev/null || true
}
leftovers=$( { pgrep -f "jenkins-agent/build-ipa[.]sh" || true; pgrep -f "xcodebuild .*($derived|$PWD/build/ipa/)" || true; } | sort -u)
for pid in $leftovers; do
  [[ "$pid" == "$$" ]] && continue
  kill -0 "$pid" 2>/dev/null || continue
  echo "Stopping leftover iOS build process $pid"
  stop_tree "$pid"
done
: "${GIT_COMMIT:?full revision required}"
: "${BUILD_NUMBER:?Jenkins build number required}"
: "${IOS_TEAM_ID:?paid Apple team required}"
[[ "$GIT_COMMIT" =~ ^[a-f0-9]{40}$ && "$BUILD_NUMBER" =~ ^[0-9]+$ && "$IOS_TEAM_ID" =~ ^[A-Z0-9]{10}$ ]]
[[ "$(git rev-parse HEAD)" == "$GIT_COMMIT" ]]
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"
out="$PWD/build/ipa"
mkdir -p "$out"
rm -f "$out/app.ipa"
archive="$out/eolssu.xcarchive"
# S15P31E102-757: sign with an App Store Connect API key so builds survive a lost Xcode account session.
# The key stays on this Mac: one AuthKey_<KEY_ID>.p8 plus an issuer-id file, never in the repository or logs.
asc_dir="${IOS_ASC_KEY_DIR:-$HOME/jenkins-agent/asc}"
auth=()
asc_keys=("$asc_dir"/AuthKey_*.p8)
if [[ -f "${asc_keys[0]}" ]]; then
  [[ ${#asc_keys[@]} -eq 1 && -f "$asc_dir/issuer-id" ]] || { echo "ASC key folder needs exactly one AuthKey_<KEY_ID>.p8 and issuer-id" >&2; exit 1; }
  asc_key_id=$(basename "${asc_keys[0]}" .p8)
  asc_key_id=${asc_key_id#AuthKey_}
  asc_issuer=$(tr -d '[:space:]' < "$asc_dir/issuer-id")
  [[ "$asc_key_id" =~ ^[A-Z0-9]{10}$ && "$asc_issuer" =~ ^[0-9a-f-]{36}$ ]] || { echo "Invalid ASC key ID or issuer ID" >&2; exit 1; }
  auth=(-authenticationKeyPath "${asc_keys[0]}" -authenticationKeyID "$asc_key_id" -authenticationKeyIssuerID "$asc_issuer")
  echo "Signing with App Store Connect API key"
else
  echo "warning: no App Store Connect API key in $asc_dir; falling back to the Xcode account session" >&2
fi
# Epoch seconds prevent downgrade of CFBundleVersion when migrating Jenkins jobs.
version=$(date -u +%s)
/usr/bin/python3 - "$out/DevelopmentInfo.plist" <<'PYINFO'
import plistlib,sys
with open('frontend/iosApp/iosApp/Info.plist','rb') as f: info=plistlib.load(f)
info['CFBundleDisplayName']='얼쑤 개발'
with open(sys.argv[1],'wb') as f: plistlib.dump(info,f)
PYINFO
xcodebuild -project frontend/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath "$derived" \
  -archivePath "$archive" -allowProvisioningUpdates ${auth[@]+"${auth[@]}"} \
  "TEAM_ID=$IOS_TEAM_ID" PRODUCT_BUNDLE_IDENTIFIER=com.ssafy.eolssu.dev \
  "INFOPLIST_FILE=$out/DevelopmentInfo.plist" \
  "CURRENT_PROJECT_VERSION=$version" archive
/usr/bin/python3 - "$out/export-options.plist" "$IOS_TEAM_ID" <<'PY'
import plistlib,sys
with open(sys.argv[1], 'wb') as f:
    plistlib.dump(dict(method='release-testing', destination='export', teamID=sys.argv[2],
        signingStyle='automatic', manageAppVersionAndBuildNumber=False, thinning='<none>'), f)
PY
xcodebuild -exportArchive -archivePath "$archive" -exportPath "$out/export" \
  -exportOptionsPlist "$out/export-options.plist" -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}
ipas=("$out"/export/*.ipa)
[[ ${#ipas[@]} -eq 1 && -f "${ipas[0]}" ]]
cp "${ipas[0]}" "$out/app.ipa"
/usr/bin/python3 "$(dirname "$0")/verify-ipa.py" "$out/app.ipa" "$IOS_TEAM_ID" "${IOS_DEVICE_ID:?registered iPhone UDID required}"
