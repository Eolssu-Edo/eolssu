#!/usr/bin/env bash
# S15P31E102-444: paid-team Ad Hoc archive/export, independent of iPhone connectivity.
set -euo pipefail
# Prevent idle sleep only for this build; do not change system power settings.
/usr/bin/caffeinate -i -w $$ &
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
# Epoch seconds prevent downgrade of CFBundleVersion when migrating Jenkins jobs.
version=$(date -u +%s)
/usr/bin/python3 - "$out/DevelopmentInfo.plist" <<'PYINFO'
import plistlib,sys
with open('frontend/iosApp/iosApp/Info.plist','rb') as f: info=plistlib.load(f)
info['CFBundleDisplayName']='얼쑤 개발'
with open(sys.argv[1],'wb') as f: plistlib.dump(info,f)
PYINFO
xcodebuild -project frontend/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath "${IOS_DERIVED_DATA:-$HOME/jenkins-agent/ios-derived}" \
  -archivePath "$archive" -allowProvisioningUpdates \
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
  -exportOptionsPlist "$out/export-options.plist" -allowProvisioningUpdates
ipas=("$out"/export/*.ipa)
[[ ${#ipas[@]} -eq 1 && -f "${ipas[0]}" ]]
cp "${ipas[0]}" "$out/app.ipa"
/usr/bin/python3 "$(dirname "$0")/verify-ipa.py" "$out/app.ipa" "$IOS_TEAM_ID" "${IOS_DEVICE_ID:?registered iPhone UDID required}"
