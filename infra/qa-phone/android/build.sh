#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the installed Android SDK}"
source_dir=$(cd -- "$(dirname -- "$0")" && pwd)
output=${1:?Output directory is required}
mkdir -p -- "$output"
output=$(cd -- "$output" && pwd)
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/classes" "$work/dex"
javac --release 8 -d "$work/classes" "$source_dir/BrightScreen.java" "$source_dir/UnicodeText.java"
"$ANDROID_HOME/build-tools/37.0.0/d8" --min-api 26 --lib "$ANDROID_HOME/platforms/android-37.0/android.jar" --output "$work/dex" "$work/classes"/com/ssafy/qa/*.class
jar --create --file "$output/bright-screen.jar" -C "$work/dex" classes.dex
python3 - "$source_dir/BrightScreen.java" "$output" <<'PY'
import hashlib, json, pathlib, sys
source=pathlib.Path(sys.argv[1]); output=pathlib.Path(sys.argv[2])
text_source=source.with_name('UnicodeText.java')
metadata={'textSourceSha256':hashlib.sha256(text_source.read_bytes()).hexdigest(),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sha256':hashlib.sha256((output/'bright-screen.jar').read_bytes()).hexdigest(),'leaseMs':15000,'verifiedApi':31}
(output/'bright-screen.json').write_text(json.dumps(metadata,indent=2)+'\n')
print(json.dumps(metadata))
PY
