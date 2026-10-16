#!/usr/bin/env bash
#
# Start the local Android emulator (AVD "boysdairy") WITH a window on macOS,
# docked on the right side of the screen, wait for it to finish booting and
# optionally install + launch the debug build of the app.
#
# Run `scripts/emulator.sh --help` for usage.

set -euo pipefail

APP_ID="com.rookierc.boysdairy"
MAIN_ACTIVITY=".MainActivity"
BOOT_TIMEOUT_SECONDS=300

AVD_NAME="boysdairy"
DO_INSTALL=0
HEADLESS=0
POSITION=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

_tmp="${TMPDIR:-/tmp}"
LOG_FILE="${_tmp%/}/boysdairy-emulator.log"

usage() {
  cat <<USAGE
Usage: scripts/emulator.sh [options]

Starts the Android emulator with a window (positioned on the right side of the
main screen), waits for boot and optionally installs + launches the debug app.

Options:
  --avd NAME     AVD to start (default: boysdairy)
  --install      After boot: ./gradlew installDebug, then launch
                 ${APP_ID}/${MAIN_ACTIVITY}
  --headless     Start without a window (-no-window)
  --no-position  Do not touch window.x / window.y / window.scale of the AVD
  -h, --help     Show this help

Environment (optional overrides):
  ANDROID_HOME / ANDROID_SDK_ROOT   default: \$HOME/Library/Android/sdk
  JAVA_HOME                         default: \$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
  ANDROID_AVD_HOME                  default: \$HOME/.android/avd

If the AVD or the SDK is missing, run scripts/setup-macos.sh --with-emulator.
Emulator output is logged to: ${LOG_FILE}
USAGE
}

log() { printf '[emulator] %s\n' "$*"; }
warn() { printf '[emulator] warning: %s\n' "$*" >&2; }
die() { printf '[emulator] error: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --avd)
      [[ $# -ge 2 && -n "$2" ]] || die "--avd needs a name"
      AVD_NAME="$2"
      shift 2
      ;;
    --avd=*) AVD_NAME="${1#--avd=}"; shift ;;
    --install) DO_INSTALL=1; shift ;;
    --headless) HEADLESS=1; shift ;;
    --no-position) POSITION=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown option: $1" ;;
  esac
done

# --- Environment -----------------------------------------------------------

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
JDK_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"

[[ -x "$EMULATOR" ]] || die "emulator not found at $EMULATOR (run scripts/setup-macos.sh --with-emulator)"
[[ -x "$ADB" ]] || die "adb not found at $ADB (run scripts/setup-macos.sh)"

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
if [[ -d "$JDK_HOME" ]]; then
  export JAVA_HOME="$JDK_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
elif [[ "$DO_INSTALL" -eq 1 ]]; then
  warn "JAVA_HOME ($JDK_HOME) does not exist; gradle will use whatever java is on PATH"
fi

# --- Helpers ---------------------------------------------------------------

# Serial of the first emulator known to adb (prints nothing if there is none).
emulator_serial() {
  "$ADB" devices 2>/dev/null | awk '/^emulator-[0-9]+/ { print $1; exit }'
}

# set_ini_key FILE KEY VALUE -- replace "KEY = ..." or append it (format "key = value").
set_ini_key() {
  local file="$1" key="$2" value="$3" tmp
  [[ -f "$file" ]] || : > "$file"
  tmp="$(mktemp "${_tmp%/}/boysdairy-ini.XXXXXX")"
  awk -v k="$key" -v v="$value" '
    { line = $0; sub(/[ \t]*=.*/, "", line); sub(/^[ \t]+/, "", line)
      if (line == k) { if (!found) print k " = " v; found = 1 } else print }
    END { if (!found) print k " = " v }' "$file" > "$tmp"
  cat "$tmp" > "$file"
  rm -f "$tmp"
}

avd_dir() {
  local ini="$AVD_HOME/$AVD_NAME.ini" p=""
  if [[ -f "$ini" ]]; then
    p="$(sed -n 's/^path=//p' "$ini" | head -n 1)"
  fi
  [[ -n "$p" ]] || p="$AVD_HOME/$AVD_NAME.avd"
  printf '%s\n' "$p"
}

position_window() {
  local dir ini bounds width x
  dir="$(avd_dir)"
  if [[ ! -d "$dir" ]]; then
    warn "AVD directory $dir not found; skipping window positioning"
    return 0
  fi
  if [[ "$(uname -s)" != "Darwin" ]] || ! command -v osascript >/dev/null 2>&1; then
    warn "osascript unavailable; skipping window positioning"
    return 0
  fi
  # Finder reports the desktop bounds as "0, 0, W, H".
  bounds="$(osascript -e 'tell application "Finder" to get bounds of window of desktop' 2>/dev/null || true)"
  width="$(printf '%s' "$bounds" | awk -F', *' 'NF >= 4 { print $3 }')"
  if ! [[ "$width" =~ ^[0-9]+$ ]]; then
    warn "could not read the screen width from Finder (got '${bounds}'); skipping window positioning"
    return 0
  fi
  x=$((width - 430))
  if (( x < 0 )); then x=0; fi
  ini="$dir/emulator-user.ini"
  set_ini_key "$ini" "window.x" "$x"
  set_ini_key "$ini" "window.y" "25"
  set_ini_key "$ini" "window.scale" "0.53"
  log "window position set to x=$x y=25 scale=0.53 (screen width ${width})"
}

wait_for_boot() {
  local pid="$1" deadline serial="" boot=""
  deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
  log "waiting for the emulator to boot (timeout ${BOOT_TIMEOUT_SECONDS}s)..."
  while :; do
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      printf '[emulator] error: the emulator process exited before boot completed. Last log lines (%s):\n' "$LOG_FILE" >&2
      tail -n 25 "$LOG_FILE" >&2 || true
      exit 1
    fi
    if (( SECONDS >= deadline )); then
      die "timed out after ${BOOT_TIMEOUT_SECONDS}s waiting for sys.boot_completed (see $LOG_FILE)"
    fi
    if [[ -z "$serial" ]]; then
      serial="$(emulator_serial || true)"
    fi
    if [[ -n "$serial" ]]; then
      boot="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [[ "$boot" == "1" ]]; then
        break
      fi
    fi
    sleep 2
  done
  # The package manager can lag a little behind sys.boot_completed.
  until "$ADB" -s "$serial" shell pm path android >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      die "package manager did not become ready in time"
    fi
    sleep 1
  done
  log "boot completed on $serial"
}

# --- Start the emulator ----------------------------------------------------

EMU_PID=""
if [[ -n "$(emulator_serial || true)" ]]; then
  log "an emulator is already running ($(emulator_serial)); not starting a new one"
else
  if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
    die "AVD '$AVD_NAME' does not exist. Create it with: scripts/setup-macos.sh --with-emulator"
  fi

  if [[ "$HEADLESS" -eq 0 && "$POSITION" -eq 1 ]]; then
    position_window
  fi

  emu_args=(-avd "$AVD_NAME" -no-audio -no-boot-anim -gpu auto)
  if [[ "$HEADLESS" -eq 1 ]]; then
    emu_args+=(-no-window)
  fi

  log "starting: emulator ${emu_args[*]}"
  log "log file: $LOG_FILE"
  nohup "$EMULATOR" "${emu_args[@]}" </dev/null >"$LOG_FILE" 2>&1 &
  EMU_PID=$!
  disown "$EMU_PID" 2>/dev/null || true
fi

wait_for_boot "$EMU_PID"

# --- Install + launch ------------------------------------------------------

if [[ "$DO_INSTALL" -eq 1 ]]; then
  SERIAL="$(emulator_serial)"
  # AGP's install task honours ANDROID_SERIAL, so a connected phone is left alone.
  export ANDROID_SERIAL="$SERIAL"
  log "installing the debug build (./gradlew installDebug)..."
  (cd "$REPO_ROOT" && ./gradlew installDebug)
  log "launching $APP_ID/$MAIN_ACTIVITY"
  "$ADB" -s "$SERIAL" shell am start -n "$APP_ID/$MAIN_ACTIVITY"
fi

log "done"
