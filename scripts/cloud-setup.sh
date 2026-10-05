#!/bin/bash
# Installs the Android SDK for Claude Code *cloud* sessions (claude.ai/code).
# Runs from the SessionStart hook in .claude/settings.json; does nothing on your PC.
# Idempotent: skips everything that is already installed.
#
# The cloud environment must allow the host dl.google.com (Android SDK + Google Maven).
# See docs/CLAUDE_CODE.md.

if [ "$CLAUDE_CODE_REMOTE" != "true" ]; then exit 0; fi

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
REPO="${CLAUDE_PROJECT_DIR:-$(pwd)}"

if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/36.0.0" ]; then
  echo "Installing Android SDK into $SDK …"
  mkdir -p "$SDK/cmdline-tools"
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    curl -sSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
      && unzip -q -o /tmp/clt.zip -d "$SDK/cmdline-tools" \
      && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest" || { echo "SDK download failed (is dl.google.com allowed?)"; exit 0; }
  fi
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
      "platforms;android-36" "build-tools;36.0.0" "platform-tools" > /tmp/sdkmanager.log 2>&1 || true
fi

# Gradle finds the SDK through local.properties (gitignored)
echo "sdk.dir=$SDK" > "$REPO/local.properties"
chmod +x "$REPO/gradlew" 2>/dev/null || true
echo "Android SDK ready: $SDK"
exit 0
