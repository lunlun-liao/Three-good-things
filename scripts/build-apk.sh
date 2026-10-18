#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
signing_dir="${THREE_GOOD_THINGS_SIGNING_DIR:-$repo_dir/../three-good-things-signing}"
if [[ ! -f "$signing_dir/release.p12" || ! -f "$signing_dir/password" ]]; then
  echo 'Signing key missing. Restore the original key for updates; only for a first build, run scripts/create-signing-key.sh.' >&2
  exit 1
fi
npm run check
npm test
./android/gradlew -p android --no-daemon --console=plain :app:assembleRelease :app:lintRelease
build_tools="$ANDROID_HOME/build-tools/35.0.0"
unsigned_apk="$repo_dir/android/app/build/outputs/apk/release/app-release-unsigned.apk"
"$build_tools/zipalign" -c -P 16 4 "$unsigned_apk"
mkdir -p downloads
"$build_tools/apksigner" sign --ks "$signing_dir/release.p12" --ks-key-alias three-good-things \
  --ks-pass "file:$signing_dir/password" \
  --out downloads/three-good-things-1.0.0.apk "$unsigned_apk"
"$build_tools/apksigner" verify --verbose --print-certs downloads/three-good-things-1.0.0.apk
(cd downloads; sha256sum three-good-things-1.0.0.apk > three-good-things-1.0.0.apk.sha256)
echo 'Built and verified downloads/three-good-things-1.0.0.apk. Keep the signing material private.'
