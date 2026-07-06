#!/usr/bin/env bash
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-"/opt/android-sdk"}"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

mkdir -p "$SDK_DIR/cmdline-tools"

if ! command -v sdkmanager >/dev/null 2>&1; then
  tmp_zip="$(mktemp /tmp/android-commandlinetools.XXXXXX.zip)"
  curl -L "$CMDLINE_URL" -o "$tmp_zip"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  unzip -q "$tmp_zip" -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK_DIR"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0" \
  "ndk;29.0.14033849"

rustup target add aarch64-linux-android x86_64-linux-android
if ! command -v cargo-ndk >/dev/null 2>&1; then
  cargo install cargo-ndk
fi

cat > local.properties <<EOF
sdk.dir=$SDK_DIR
EOF

echo "Android SDK is ready at $SDK_DIR"
echo "Add this to your shell profile if needed:"
echo "export ANDROID_HOME=$SDK_DIR"
echo 'export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"'
