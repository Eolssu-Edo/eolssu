#!/usr/bin/env bash
set -euo pipefail
version=v1.3.2
case "$(uname -s):$(uname -m)" in
  Darwin:arm64)
    platform=darwin-arm64
    expected=e2020b073c44f692685a24d6abc378817eb81ffaaf49fd0531ef8565f767f2f5
    ;;
  Linux:x86_64)
    platform=linux-amd64
    expected=cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10
    ;;
  *) echo 'No age binary configured for this runner' >&2; exit 1 ;;
esac
destination=${1:?installation directory required}
mkdir -p "$destination"
archive="$destination/age.tar.gz"
curl --fail --silent --show-error --location --retry 2 --max-time 60 \
  "https://github.com/FiloSottile/age/releases/download/$version/age-$version-$platform.tar.gz" -o "$archive"
printf '%s  %s\n' "$expected" "$archive" | shasum -a 256 -c -
tar -xzf "$archive" -C "$destination" age/age age/age-keygen
