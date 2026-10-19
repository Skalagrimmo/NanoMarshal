#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export PATH="$HOME/go/bin:$PATH"

if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
  echo "Missing Android API 35 platform at $ANDROID_HOME/platforms/android-35" >&2
  echo "Install platforms;android-35 with sdkmanager, then retry." >&2
  exit 2
fi
if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "Missing Android NDK at $ANDROID_NDK_HOME" >&2
  echo "Edit this script or set the correct path before retrying." >&2
  exit 2
fi

cd "$ROOT_DIR/gocore"
go get -tool golang.org/x/mobile/cmd/gobind@latest
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
"$HOME/go/bin/gomobile" init

mkdir -p "$ROOT_DIR/app/libs"
"$HOME/go/bin/gomobile" bind \
  -target=android/arm,android/arm64 \
  -androidapi 24 \
  -javapkg com.example.nanomarshal \
  -o "$ROOT_DIR/app/libs/nanomarshalcore.aar" \
  .

echo "Built $ROOT_DIR/app/libs/nanomarshalcore.aar"
