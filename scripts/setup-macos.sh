#!/usr/bin/env bash
#
# One-shot, user-level toolchain setup for building Boys-Dairy on macOS.
#
#   * Temurin JDK 17   -> ~/Library/Java/JavaVirtualMachines/temurin-17.jdk
#   * Android SDK      -> $ANDROID_HOME (default ~/Library/Android/sdk)
#   * local.properties -> <repo>/local.properties (sdk.dir=...)
#
# No sudo and no Homebrew (this Intel Mac gets no prebuilt bottles). Idempotent:
# every step is skipped when its result is already in place.
#
# Run `scripts/setup-macos.sh --help` for usage.

set -euo pipefail

JDK_FEATURE_VERSION=17
SDK_PLATFORM="android-35"
SDK_BUILD_TOOLS="35.0.0"
AVD_NAME="boysdairy"
AVD_DEVICE="pixel_6"
CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-mac-15641748_latest.zip}"
# Optional: set to the published SHA-256 of the command-line tools zip to verify it.
CMDLINE_TOOLS_SHA256="${CMDLINE_TOOLS_SHA256:-}"

WITH_EMULATOR=0
ACCEPT_LICENSES=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
  cat <<USAGE
Usage: scripts/setup-macos.sh [options]

Installs (user-level, no sudo, no Homebrew) everything needed to build the app:
Temurin JDK ${JDK_FEATURE_VERSION}, Android command-line tools, platform-tools,
platforms;${SDK_PLATFORM}, build-tools;${SDK_BUILD_TOOLS}, and writes local.properties.

Options:
  --with-emulator     Also install the emulator + a google_apis system image and
                      create the AVD "${AVD_NAME}" (${AVD_DEVICE}, 720x1560 @ 320dpi)
  --accept-licenses   Accept the Android SDK licenses on your behalf
                      (pipes "yes" into sdkmanager). Without this flag the
                      license prompts are shown interactively.
  -h, --help          Show this help

Environment (optional overrides):
  ANDROID_HOME / ANDROID_SDK_ROOT   SDK location (default: \$HOME/Library/Android/sdk)
  ANDROID_AVD_HOME                  AVD location (default: \$HOME/.android/avd)
  CMDLINE_TOOLS_URL                 command-line tools zip URL
  CMDLINE_TOOLS_SHA256              expected SHA-256 of that zip (verified when set)
USAGE
}

log() { printf '==> %s\n' "$*"; }
note() { printf '    %s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-emulator) WITH_EMULATOR=1; shift ;;
    --accept-licenses) ACCEPT_LICENSES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown option: $1" ;;
  esac
done

# --- Preconditions ---------------------------------------------------------

[[ "$(uname -s)" == "Darwin" ]] || die "this script only supports macOS"
for tool in curl tar unzip shasum plutil; do
  command -v "$tool" >/dev/null 2>&1 || die "required tool not found: $tool"
done

case "$(uname -m)" in
  x86_64) ADOPTIUM_ARCH="x64"; SYSIMG_ABI="x86_64" ;;
  arm64|aarch64) ADOPTIUM_ARCH="aarch64"; SYSIMG_ABI="arm64-v8a" ;;
  *) die "unsupported architecture: $(uname -m)" ;;
esac

JDK_DIR="$HOME/Library/Java/JavaVirtualMachines/temurin-${JDK_FEATURE_VERSION}.jdk"
JDK_HOME="$JDK_DIR/Contents/Home"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/boysdairy-setup.XXXXXX")"
trap 'rm -rf "$WORK_DIR"' EXIT

# --- Helpers ---------------------------------------------------------------

download() { # URL DEST
  curl -fL --retry 3 --progress-bar -o "$2" "$1"
}

sha256_of() { shasum -a 256 "$1" | awk '{ print tolower($1) }'; }

# json_get FILE KEYPATH -- value at a dotted key path (array indexes allowed), via macOS plutil.
json_get() {
  plutil -extract "$2" raw -o - "$1"
}

# set_kv FILE KEY VALUE SEP -- replace "KEY<ws>=<ws>..." or append "KEY<SEP>VALUE".
set_kv() {
  local file="$1" key="$2" value="$3" sep="$4" tmp
  tmp="$(mktemp "$WORK_DIR/kv.XXXXXX")"
  awk -v k="$key" -v v="$value" -v s="$sep" '
    { line = $0; sub(/[ \t]*=.*/, "", line); sub(/^[ \t]+/, "", line)
      if (line == k) { if (!found) print k s v; found = 1 } else print }
    END { if (!found) print k s v }' "$file" > "$tmp"
  cat "$tmp" > "$file"
  rm -f "$tmp"
}

# sdkm ARGS... -- run sdkmanager, auto-answering prompts only with --accept-licenses.
sdkm() {
  if [[ "$ACCEPT_LICENSES" -eq 1 ]]; then
    { yes || true; } | "$SDKMANAGER" --sdk_root="$SDK" "$@"
  else
    "$SDKMANAGER" --sdk_root="$SDK" "$@"
  fi
}

# sdk_pkg_installed PACKAGE -- true if the sdkmanager package is already on disk.
sdk_pkg_installed() {
  [[ -f "$SDK/${1//;//}/package.xml" ]]
}

# --- 1. JDK ----------------------------------------------------------------

install_jdk() {
  log "Temurin JDK ${JDK_FEATURE_VERSION} (${ADOPTIUM_ARCH})"
  if [[ -x "$JDK_HOME/bin/java" ]]; then
    note "already installed: $JDK_DIR"
    return 0
  fi
  if [[ -e "$JDK_DIR" ]]; then
    die "$JDK_DIR exists but has no usable java; remove it and re-run"
  fi

  local api="https://api.adoptium.net/v3/assets/latest/${JDK_FEATURE_VERSION}/hotspot?architecture=${ADOPTIUM_ARCH}&image_type=jdk&os=mac&vendor=eclipse"
  local meta="$WORK_DIR/adoptium.json" url name expected archive actual extract top
  note "querying $api"
  curl -fsSL --retry 3 -o "$meta" "$api" || die "could not query the Adoptium API"
  url="$(json_get "$meta" 0.binary.package.link)" || die "unexpected Adoptium API response (link)"
  name="$(json_get "$meta" 0.binary.package.name)" || die "unexpected Adoptium API response (name)"
  expected="$(json_get "$meta" 0.binary.package.checksum | tr 'A-F' 'a-f')" || die "unexpected Adoptium API response (checksum)"
  [[ -n "$url" && -n "$name" && -n "$expected" ]] || die "incomplete Adoptium API response"

  archive="$WORK_DIR/$name"
  note "downloading $name"
  download "$url" "$archive"

  actual="$(sha256_of "$archive")"
  if [[ "$actual" != "$expected" ]]; then
    die "SHA-256 mismatch for $name (expected $expected, got $actual)"
  fi
  note "sha256 verified: $actual"

  extract="$WORK_DIR/jdk-extract"
  mkdir -p "$extract"
  tar -xzf "$archive" -C "$extract"
  # The tarball holds a single top-level directory (jdk-17.x.y+z) with Contents/Home inside.
  top="$(find "$extract" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  [[ -n "$top" && -x "$top/Contents/Home/bin/java" ]] || die "unexpected JDK archive layout"

  mkdir -p "$(dirname "$JDK_DIR")"
  mv "$top" "$JDK_DIR"
  note "installed to $JDK_DIR"
}

# --- 2. Android command-line tools -----------------------------------------

install_cmdline_tools() {
  log "Android command-line tools"
  if [[ -x "$SDKMANAGER" ]]; then
    note "already installed: $SDK/cmdline-tools/latest"
    return 0
  fi

  local zip="$WORK_DIR/cmdline-tools.zip" actual extract
  note "downloading $CMDLINE_TOOLS_URL"
  download "$CMDLINE_TOOLS_URL" "$zip"

  if [[ -n "$CMDLINE_TOOLS_SHA256" ]]; then
    actual="$(sha256_of "$zip")"
    if [[ "$actual" != "$(printf '%s' "$CMDLINE_TOOLS_SHA256" | tr 'A-F' 'a-f')" ]]; then
      die "SHA-256 mismatch for the command-line tools (expected $CMDLINE_TOOLS_SHA256, got $actual)"
    fi
    note "sha256 verified: $actual"
  else
    note "sha256 not checked (downloaded over HTTPS from dl.google.com; set CMDLINE_TOOLS_SHA256 to verify)"
  fi

  extract="$WORK_DIR/cmdline-extract"
  mkdir -p "$extract"
  unzip -q "$zip" -d "$extract"
  [[ -x "$extract/cmdline-tools/bin/sdkmanager" ]] || die "unexpected command-line tools archive layout"

  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$extract/cmdline-tools" "$SDK/cmdline-tools/latest"
  note "installed to $SDK/cmdline-tools/latest"
}

# --- 3. SDK licenses + packages --------------------------------------------

install_sdk_packages() {
  local packages=("platform-tools" "platforms;${SDK_PLATFORM}" "build-tools;${SDK_BUILD_TOOLS}")
  if [[ "$WITH_EMULATOR" -eq 1 ]]; then
    packages+=("emulator" "system-images;${SDK_PLATFORM};google_apis;${SYSIMG_ABI}")
  fi

  log "Android SDK packages"
  local missing=() pkg
  for pkg in "${packages[@]}"; do
    if sdk_pkg_installed "$pkg"; then
      note "already installed: $pkg"
    else
      missing+=("$pkg")
    fi
  done
  if [[ "${#missing[@]}" -eq 0 ]]; then
    return 0
  fi

  log "Android SDK licenses"
  if [[ "$ACCEPT_LICENSES" -eq 1 ]]; then
    note "--accept-licenses given: accepting the Android SDK licenses on your behalf"
    note "(terms: https://developer.android.com/studio/terms)"
    sdkm --licenses >/dev/null
  else
    note "The Android SDK licenses must be accepted before packages can be installed."
    note "Re-run with --accept-licenses to accept them automatically; otherwise you will"
    note "be asked below (nothing is accepted without your answer)."
    if [[ ! -t 0 ]]; then
      die "no terminal for the license prompts; run interactively or pass --accept-licenses"
    fi
    sdkm --licenses
  fi

  log "Installing: ${missing[*]}"
  sdkm --install "${missing[@]}"
}

# --- 4. AVD ----------------------------------------------------------------

create_avd() {
  [[ "$WITH_EMULATOR" -eq 1 ]] || return 0
  log "AVD ${AVD_NAME}"
  if [[ -f "$AVD_HOME/$AVD_NAME.ini" ]]; then
    note "already exists: $AVD_HOME/$AVD_NAME.ini"
    return 0
  fi

  # "no" answers avdmanager's "create a custom hardware profile?" question.
  printf 'no\n' | "$AVDMANAGER" create avd \
    -n "$AVD_NAME" \
    -k "system-images;${SDK_PLATFORM};google_apis;${SYSIMG_ABI}" \
    -d "$AVD_DEVICE"

  local config="$AVD_HOME/$AVD_NAME.avd/config.ini"
  [[ -f "$config" ]] || die "avdmanager did not create $config"
  set_kv "$config" hw.lcd.width 720 =
  set_kv "$config" hw.lcd.height 1560 =
  set_kv "$config" hw.lcd.density 320 =
  set_kv "$config" hw.keyboard yes =
  set_kv "$config" hw.ramSize 2048 =
  note "created ${AVD_NAME} (720x1560, 320dpi, 2048 MB RAM)"
}

# --- 5. local.properties ---------------------------------------------------

write_local_properties() {
  local file="$REPO_ROOT/local.properties"
  log "local.properties"
  if [[ -f "$file" ]]; then
    note "already exists: $file (left untouched)"
    return 0
  fi
  printf 'sdk.dir=%s\n' "$SDK" > "$file"
  note "wrote $file"
}

# --- Run -------------------------------------------------------------------

mkdir -p "$SDK"
install_jdk
export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
install_cmdline_tools
install_sdk_packages
create_avd
write_local_properties

cat <<DONE

Setup finished. Add this to your shell profile (e.g. ~/.zshrc) so gradle and
other tools find the toolchain:

  export JAVA_HOME="$JDK_HOME"
  export ANDROID_HOME="$SDK"
  export PATH="\$PATH:\$ANDROID_HOME/platform-tools"

Next:
  scripts/emulator.sh --install    # boot the emulator, install and launch the app
  ./gradlew testDebugUnitTest      # run the unit tests
DONE
