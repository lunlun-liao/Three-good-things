#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
signing_dir="${THREE_GOOD_THINGS_SIGNING_DIR:-$repo_dir/../three-good-things-signing}"
mkdir -p "$signing_dir"
signing_dir="$(cd "$signing_dir" && pwd)"
case "$signing_dir/" in "$repo_dir/"*) echo 'Signing keys must be outside the repository.' >&2; exit 1;; esac
if [[ -e "$signing_dir/release.p12" || -e "$signing_dir/password" ]]; then
  echo 'Signing material already exists. Preserve it for future updates; not overwriting.' >&2
  exit 1
fi
umask 077
python3 - "$signing_dir/password" <<'PY'
import secrets, sys
from pathlib import Path
Path(sys.argv[1]).write_text(secrets.token_urlsafe(48))
PY
keytool -genkeypair -noprompt -keystore "$signing_dir/release.p12" -storetype PKCS12 \
  -storepass:file "$signing_dir/password" -keypass:file "$signing_dir/password" \
  -alias three-good-things -keyalg RSA -keysize 3072 -validity 10000 \
  -dname 'CN=Three Good Things, OU=Android App'
echo 'Signing key created outside the repository. Back up the key AND password securely; do not commit them.'
