#!/usr/bin/env bash
set -euo pipefail

out="$PWD/mobile-out"
work="$RUNNER_TEMP/eolssu-ios"
archive="$work/eolssu.xcarchive"
mkdir -p "$work" "$out"
[[ "$IOS_TEAM_ID" =~ ^[A-Z0-9]{10}$ && "$ASC_KEY_ID" =~ ^[A-Z0-9]{10}$ &&
   "$ASC_ISSUER_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || { echo 'Invalid Apple signing identifiers' >&2; exit 1; }
: "${ASC_PRIVATE_KEY:?App Store Connect key required}"
umask 077
key_file=$(mktemp "$RUNNER_TEMP/asc-key.XXXXXX")
trap 'rm -f "$key_file"' EXIT
printf '%s\n' "$ASC_PRIVATE_KEY" > "$key_file"
unset ASC_PRIVATE_KEY
auth=(-authenticationKeyPath "$key_file" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")

export_archive() {
  local destination=$1
  /usr/bin/python3 - "$work/export-$destination.plist" "$IOS_TEAM_ID" "$method" "$destination" <<'PY'
import plistlib
import sys
path, team, method, destination = sys.argv[1:]
options = dict(method=method, destination=destination, teamID=team,
               signingStyle='automatic', manageAppVersionAndBuildNumber=False)
if method == 'app-store-connect':
    options['uploadSymbols'] = True
else:
    options['thinning'] = '<none>'
with open(path, 'wb') as output:
    plistlib.dump(options, output)
PY
  xcodebuild -exportArchive -archivePath "$archive" -exportPath "$work/$destination" \
    -exportOptionsPlist "$work/export-$destination.plist" -allowProvisioningUpdates "${auth[@]}"
}


if [[ "${1:-}" == upload ]]; then
  [[ "$MODE" == ios-release && -d "$archive" && "$(cat "$out/revision.txt")" == "$REVISION" ]]
  method=app-store-connect
  export_archive upload
  exit 0
fi

case "${MODE:?iOS build mode required}" in
  ios-qa)
    : "${IOS_DEVICE_ID:?Registered QA device ID required}"
    method=release-testing
    bundle=com.ssafy.eolssu.dev
    version=$(date -u +%s)
    name=eolssu-qa-"${REVISION:0:8}"-b"$version"
    /usr/bin/python3 - "$work/DevelopmentInfo.plist" <<'PY'
import plistlib
import sys
with open('frontend/iosApp/iosApp/Info.plist', 'rb') as source:
    info = plistlib.load(source)
info['CFBundleDisplayName'] = '얼쑤 개발'
with open(sys.argv[1], 'wb') as output:
    plistlib.dump(info, output)
PY
    overrides=("INFOPLIST_FILE=$work/DevelopmentInfo.plist")
    ;;
  ios-release)
    [[ "$VERSION_CODE" =~ ^[1-9][0-9]{0,8}$ && "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
      { echo 'Supply a positive iOS build number and X.Y.Z marketing version' >&2; exit 1; }
    method=app-store-connect
    bundle=com.ssafy.eolssu
    version=$VERSION_CODE
    name=eolssu-"$VERSION_NAME"-b"$version"
    overrides=("MARKETING_VERSION=$VERSION_NAME")
    ;;
  *) echo 'Unknown iOS build mode' >&2; exit 1 ;;
esac

xcodebuild -project frontend/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath "$work/DerivedData" -archivePath "$archive" \
  -allowProvisioningUpdates "${auth[@]}" "TEAM_ID=$IOS_TEAM_ID" "PRODUCT_BUNDLE_IDENTIFIER=$bundle" \
  "CURRENT_PROJECT_VERSION=$version" "${overrides[@]}" archive

export_archive export
ipas=("$work"/export/*.ipa)
[[ ${#ipas[@]} -eq 1 && -f "${ipas[0]}" ]]
cp "${ipas[0]}" "$out/$name.ipa"
if [[ "$MODE" == ios-qa ]]; then
  /usr/bin/python3 infra/ios-qa/verify-ipa.py "$out/$name.ipa" "$IOS_TEAM_ID" "$IOS_DEVICE_ID"
else
  /usr/bin/python3 "$CI_TOOLS/verify-appstore-ipa.py" "$out/$name.ipa" "$IOS_TEAM_ID" "$VERSION_NAME" "$version"
  if [[ -d "$archive/dSYMs" ]]; then
    ditto -c -k --keepParent "$archive/dSYMs" "$out/$name-dSYMs.zip"
  fi
fi
printf '%s\n' "$REVISION" > "$out/revision.txt"
(cd "$out" && shasum -a 256 "$name.ipa" > "$name.ipa.sha256")
