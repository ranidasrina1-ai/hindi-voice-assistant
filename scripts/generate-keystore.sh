#!/usr/bin/env bash
set -euo pipefail

# Run this once in Termux:
#   pkg install openssl
#   bash scripts/generate-keystore.sh
#
# Then add the four GitHub Actions secrets from secrets.env.
# Keep release.p12, secrets.env and the password backed up safely.

cd "$(dirname "$0")/.."

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl not found. Run: pkg install openssl"
    exit 1
fi

ALIAS="voiceassistant"
if [ -f keystore/release.p12 ] && [ "${FORCE:-0}" != "1" ]; then
    echo "keystore/release.p12 already exists."
    echo "Back it up before rotating. To overwrite deliberately: FORCE=1 bash scripts/generate-keystore.sh"
    exit 1
fi
STORE_PASSWORD="${KEYSTORE_PASSWORD:-$(openssl rand -hex 24)}"
PASSWORD_FILE="keystore-password.txt"

umask 077
mkdir -p keystore

openssl req -x509 -newkey rsa:2048 -sha256 -days 10000 -nodes \
    -keyout keystore/key.pem \
    -out keystore/cert.pem \
    -subj "/CN=Hindi Voice Assistant" \
    -addext "basicConstraints=critical,CA:FALSE" \
    -addext "keyUsage=critical,digitalSignature"

openssl pkcs12 -export \
    -inkey keystore/key.pem \
    -in keystore/cert.pem \
    -name "$ALIAS" \
    -out keystore/release.p12 \
    -passout "pass:$STORE_PASSWORD"

rm -f keystore/key.pem keystore/cert.pem
base64 keystore/release.p12 | tr -d '\n' > keystore/release.p12.b64

cat > secrets.env <<EOF
KEYSTORE_PASSWORD=$STORE_PASSWORD
KEY_ALIAS=$ALIAS
KEY_PASSWORD=$STORE_PASSWORD
EOF
cp secrets.env "$PASSWORD_FILE"

echo
echo "Created:"
echo "  keystore/release.p12       (offline backup)"
echo "  keystore/release.p12.b64   (paste into KEYSTORE_BASE64 secret)"
echo "  secrets.env                (do not commit)"
echo "  $PASSWORD_FILE             (offline backup)"
echo
echo "GitHub repo -> Settings -> Secrets and variables -> Actions:"
echo "  KEYSTORE_BASE64   = contents of keystore/release.p12.b64"
echo "  KEYSTORE_PASSWORD = value from secrets.env"
echo "  KEY_ALIAS         = $ALIAS"
echo "  KEY_PASSWORD      = same as KEYSTORE_PASSWORD"
