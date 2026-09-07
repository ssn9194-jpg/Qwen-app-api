#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
VERSION=9.5.0
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/ai-studio-wrapper/gradle-$VERSION"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
DIST="$CACHE/gradle-$VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE"
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 "$URL" -o "$ZIP";
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL";
  else echo "Gradle is not installed and curl/wget is unavailable." >&2; exit 1; fi
  rm -rf "$DIST" "$CACHE/tmp"
  mkdir -p "$CACHE/tmp"
  unzip -q "$ZIP" -d "$CACHE/tmp"
  mv "$CACHE/tmp/gradle-$VERSION" "$DIST"
  rm -rf "$CACHE/tmp"
fi
exec "$DIST/bin/gradle" "$@"
