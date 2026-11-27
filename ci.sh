#!/bin/sh
# ember: image=docker.io/library/eclipse-temurin:21.0.13.1_2-jdk-noble@sha256:02414d70acea327645d412e9794da6e3e2bec41d8c56c283fda875afb5cc2211 vcpus=4 memory=4096
# Superscan's CI: the debug build .github/workflows/build.yml runs on a push or
# pull request. Signed release builds stay on GitHub's v* tag workflow.
# Radicle CI runs this file in an ember microVM (factory docs/radicle-ci.md)
# with the commit's tree as the working directory; locally it is `sh ci.sh`
# with a JDK 21 and either ANDROID_HOME set or curl and unzip to fetch the SDK
# into a temporary directory.
# Everything goes to stderr: CI keeps stdout as artifacts.
set -eu
exec >&2

# Android command-line tools 23.0, pinned; sdkmanager then checks every SDK
# package against Google's repository manifest.
clt_url=https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
clt_sha256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
packages="platforms;android-37.0 build-tools;37.0.0"

if [ -z "${ANDROID_HOME:-}" ]; then
  if ! command -v unzip >/dev/null || ! command -v curl >/dev/null; then
    # The CI image (Ubuntu, as root) lacks unzip; elsewhere, ask for them.
    if [ "$(id -u)" -ne 0 ] || ! command -v apt-get >/dev/null; then
      echo "ci.sh needs curl and unzip, or ANDROID_HOME pointing at an SDK" >&2
      exit 1
    fi
    export DEBIAN_FRONTEND=noninteractive
    apt-get -qq update >/dev/null
    apt-get -qq install -y --no-install-recommends curl unzip ca-certificates >/dev/null
  fi
  sdk=$(mktemp -d)
  ANDROID_HOME=$sdk/android-sdk
  export ANDROID_HOME
  echo "Android SDK: fetching command-line tools into $ANDROID_HOME"
  curl -fsSL --retry 3 -o "$sdk/cmdline-tools.zip" "$clt_url"
  echo "$clt_sha256  $sdk/cmdline-tools.zip" | sha256sum -c -
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$sdk/cmdline-tools.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm "$sdk/cmdline-tools.zip"
fi
sdkmanager=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
# yes exits on SIGPIPE once sdkmanager stops reading; that is not a failure.
(yes || true) | "$sdkmanager" --licenses >/dev/null
# shellcheck disable=SC2086 # packages is a space-separated list on purpose
"$sdkmanager" $packages >/dev/null

# In-process Kotlin compilation keeps the build to one JVM in a 4 GiB VM.
./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process assembleDebug
