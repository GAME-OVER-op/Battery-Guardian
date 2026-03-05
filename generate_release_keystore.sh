#!/usr/bin/env sh
set -e

# Generates a simple release keystore at project root: release.jks
# Alias: release

if [ -f "./release.jks" ]; then
  echo "release.jks already exists. Abort."
  exit 1
fi

keytool -genkeypair \
  -keystore release.jks \
  -storetype JKS \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias release

echo ""
echo "Done. Now put passwords into ~/.gradle/gradle.properties:"
echo "BG_STORE_PASSWORD=..."
echo "BG_KEY_PASSWORD=..."
