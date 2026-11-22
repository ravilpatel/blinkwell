#!/usr/bin/env bash
# ==============================================================================
# BlinkWell — Zero-Friction Local USB Debugging, Testing & Device Runner
# ==============================================================================
# Builds the APK locally with Gradle, runs automated tests, installs the APK
# directly to your USB-connected Android phone, auto-grants permissions,
# launches the app, and streams live telemetry / logcat logs in real-time.
# (No GitHub Actions or remote CI required!)
# ==============================================================================

set -euo pipefail

# Resolve actual script path even if executed via symlink
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
SCRIPT_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
PROJECT_ROOT="$(cd -P "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"

# Default configuration
DO_BUILD=true
DO_TEST=false
DO_CLEAN=false
BUILD_TYPE="debug"
LOCAL_APK=""
TARGET_DEVICE=""
EPHEMERAL=false
LOGS=true
LOG_FILTER=""

# ------------------------------------------------------------------------------
# 0. Environment Resolution (Java & Android SDK)
# ------------------------------------------------------------------------------
# Auto-detect JAVA_HOME if not already exported
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME:-}/bin/java" ]]; then
  if [[ -x "$HOME/.local/share/jdk-17/bin/java" ]]; then
    export JAVA_HOME="$HOME/.local/share/jdk-17"
  elif [[ -x "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" ]]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
  elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(readlink -f "$(command -v java)")"
    export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
  fi
fi

if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME/bin" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Auto-detect ANDROID_HOME / ANDROID_SDK_ROOT
if [[ -z "${ANDROID_HOME:-}" || ! -d "${ANDROID_HOME:-}" ]]; then
  if [[ -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [[ -d "$HOME/.local/share/android-sdk" ]]; then
    export ANDROID_HOME="$HOME/.local/share/android-sdk"
  elif [[ -d "$HOME/.local/share/blinkwell-tools/android-sdk" ]]; then
    export ANDROID_HOME="$HOME/.local/share/blinkwell-tools/android-sdk"
  fi
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  # Add platform-tools (adb) and cmdline-tools to PATH
  [[ -d "$ANDROID_HOME/platform-tools" ]] && export PATH="$ANDROID_HOME/platform-tools:$PATH"
  [[ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]] && export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
  # Locate build-tools
  LATEST_BT="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -n "$LATEST_BT" && -d "$LATEST_BT" ]]; then
    export PATH="$LATEST_BT:$PATH"
  fi
fi

# ------------------------------------------------------------------------------
# 1. Parse Arguments
# ------------------------------------------------------------------------------
show_help() {
  echo "=================================================================="
  echo "  BlinkWell — Local USB Debugging, Testing & Device Runner"
  echo "=================================================================="
  echo "Usage: ./run-device.sh [OPTIONS]"
  echo ""
  echo "Build & Test Options:"
  echo "  -t, --test           Run unit tests (./gradlew testDebugUnitTest) before build"
  echo "      --no-build       Skip compiling APK (use existing built APK)"
  echo "  -c, --clean          Run ./gradlew clean before building"
  echo "  -r, --release        Build and test the Release APK instead of Debug"
  echo "      --apk <path>     Use a specific pre-built APK instead of compiling"
  echo ""
  echo "Device & USB Options:"
  echo "  -s, --serial <id>    Target specific ADB device serial"
  echo "  -e, --ephemeral      Ephemeral mode: auto-uninstalls the app upon exit (Ctrl+C)"
  echo "      --no-logs        Skip live logcat streaming after launch"
  echo "      --log <filter>   Custom logcat filter specification"
  echo "  -h, --help           Show this help message"
  echo ""
  echo "Examples:"
  echo "  ./run-device.sh                      # Full local build + USB install + run + logs"
  echo "  ./run-device.sh --test               # Run unit tests, then build + install + run"
  echo "  ./run-device.sh --no-build           # Instant install existing APK to USB device"
  echo "  ./run-device.sh -e                   # Test app and auto-clean phone on Ctrl+C"
  echo "=================================================================="
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --test|-t)
      DO_TEST=true
      shift
      ;;
    --no-build)
      DO_BUILD=false
      shift
      ;;
    --clean|-c)
      DO_CLEAN=true
      shift
      ;;
    --release|-r)
      BUILD_TYPE="release"
      shift
      ;;
    --apk)
      LOCAL_APK="$2"
      DO_BUILD=false
      shift 2
      ;;
    --serial|-s|--device)
      TARGET_DEVICE="$2"
      shift 2
      ;;
    --ephemeral|-e)
      EPHEMERAL=true
      shift
      ;;
    --no-logs)
      LOGS=false
      shift
      ;;
    --log)
      LOG_FILTER="$2"
      shift 2
      ;;
    --help|-h)
      show_help
      exit 0
      ;;
    *)
      if [[ -f "$1" && "$1" == *.apk ]]; then
        LOCAL_APK="$1"
        DO_BUILD=false
        shift
      else
        echo "Unknown option: $1"
        echo "Run with --help for usage."
        exit 1
      fi
      ;;
  esac
done

echo "=================================================================="
echo "  BlinkWell — Local USB Debugging & Device Runner"
echo "=================================================================="

# ------------------------------------------------------------------------------
# 2. Ensure ADB is Available
# ------------------------------------------------------------------------------
ADB_BIN=""
if command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB_BIN="$ANDROID_HOME/platform-tools/adb"
elif [[ -x "$HOME/.local/share/blinkwell-tools/platform-tools/adb" ]]; then
  ADB_BIN="$HOME/.local/share/blinkwell-tools/platform-tools/adb"
else
  echo "[+] ADB not found on system. Bootstrapping official Google platform-tools..."
  TOOLS_DIR="$HOME/.local/share/blinkwell-tools"
  mkdir -p "$TOOLS_DIR"
  TMP_ZIP="/tmp/platform-tools.zip"
  PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip"

  if command -v curl >/dev/null 2>&1; then
    curl -L --progress-bar -o "$TMP_ZIP" "$PLATFORM_TOOLS_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "$TMP_ZIP" "$PLATFORM_TOOLS_URL"
  else
    python3 -c "import urllib.request; urllib.request.urlretrieve('$PLATFORM_TOOLS_URL', '$TMP_ZIP')"
  fi

  unzip -q -o "$TMP_ZIP" -d "$TOOLS_DIR"
  rm -f "$TMP_ZIP"
  ADB_BIN="$TOOLS_DIR/platform-tools/adb"
  chmod +x "$ADB_BIN"
  echo "[✓] ADB successfully installed locally to $ADB_BIN"
fi

# Ensure adb server is running
"$ADB_BIN" start-server >/dev/null 2>&1

# ------------------------------------------------------------------------------
# 3. Optional: Run Local Unit Tests
# ------------------------------------------------------------------------------
if $DO_TEST; then
  echo ""
  echo "[*] Running local unit tests (testDebugUnitTest)..."
  (
    cd "$ANDROID_DIR"
    ./gradlew testDebugUnitTest --info
  )
  echo "[✓] All unit tests PASSED!"
fi

# ------------------------------------------------------------------------------
# 4. Local Build (Assemble APK)
# ------------------------------------------------------------------------------
TARGET_APK=""
if [[ -n "$LOCAL_APK" ]]; then
  if [[ ! -f "$LOCAL_APK" ]]; then
    echo "[-] Error: Specified APK does not exist: $LOCAL_APK"
    exit 1
  fi
  TARGET_APK="$LOCAL_APK"
  echo "[*] Using specified APK: $TARGET_APK"
elif $DO_BUILD; then
  echo ""
  if $DO_CLEAN; then
    echo "[*] Cleaning previous build artifacts..."
    (cd "$ANDROID_DIR" && ./gradlew clean)
  fi

  GRADLE_TASK="assembleDebug"
  APK_SUBDIR="debug"
  if [[ "$BUILD_TYPE" == "release" ]]; then
    GRADLE_TASK="assembleRelease"
    APK_SUBDIR="release"
  fi

  echo "[*] Compiling APK locally with Gradle ($GRADLE_TASK)..."
  (
    cd "$ANDROID_DIR"
    ./gradlew "$GRADLE_TASK"
  )

  # Locate output APK
  SEARCH_DIR="$ANDROID_DIR/app/build/outputs/apk/$APK_SUBDIR"
  TARGET_APK="$(find "$SEARCH_DIR" -name "*.apk" -type f | head -n 1 || true)"
  if [[ -z "$TARGET_APK" || ! -f "$TARGET_APK" ]]; then
    echo "[-] Error: Build completed but could not find APK in $SEARCH_DIR"
    exit 1
  fi
  echo "[✓] Build successful: $TARGET_APK ($(du -h "$TARGET_APK" | cut -f1))"
else
  # Use existing built APK
  SEARCH_DIR="$ANDROID_DIR/app/build/outputs/apk"
  TARGET_APK="$(find "$SEARCH_DIR" -name "*.apk" -type f | sort -r | head -n 1 || true)"
  if [[ -z "$TARGET_APK" || ! -f "$TARGET_APK" ]]; then
    echo "[-] Error: No compiled APK found. Run without --no-build to compile first."
    exit 1
  fi
  echo "[*] Using existing built APK: $TARGET_APK ($(du -h "$TARGET_APK" | cut -f1))"
fi

# ------------------------------------------------------------------------------
# 5. Extract Package & Main Activity Information
# ------------------------------------------------------------------------------
PACKAGE_NAME=""
MAIN_ACTIVITY="com.mitalipurohit.blinkwell.ui.MainActivity"

# Attempt extraction via aapt or aapt2 if available
AAPT_BIN="$(command -v aapt 2>/dev/null || true)"
if [[ -z "$AAPT_BIN" && -n "${ANDROID_HOME:-}" ]]; then
  AAPT_BIN="$(find "$ANDROID_HOME/build-tools" -name "aapt" 2>/dev/null | sort -V | tail -n 1 || true)"
fi

if [[ -n "$AAPT_BIN" && -x "$AAPT_BIN" ]]; then
  PKG_EXTRACTED="$("$AAPT_BIN" dump badging "$TARGET_APK" 2>/dev/null | awk -F"'" '/package: name=/{print $2}' || true)"
  if [[ -n "$PKG_EXTRACTED" ]]; then
    PACKAGE_NAME="$PKG_EXTRACTED"
  fi
  ACT_EXTRACTED="$("$AAPT_BIN" dump badging "$TARGET_APK" 2>/dev/null | awk -F"'" '/launchable-activity: name=/{print $2}' || true)"
  if [[ -n "$ACT_EXTRACTED" ]]; then
    MAIN_ACTIVITY="$ACT_EXTRACTED"
  fi
fi

# Fallback determination
if [[ -z "$PACKAGE_NAME" ]]; then
  if [[ "$TARGET_APK" == *"-debug.apk" || "$TARGET_APK" == *"debug"* ]]; then
    PACKAGE_NAME="com.mitalipurohit.blinkwell.debug"
  else
    PACKAGE_NAME="com.mitalipurohit.blinkwell"
  fi
fi

echo "[*] Target Package: $PACKAGE_NAME"
echo "[*] Main Activity:  $MAIN_ACTIVITY"

# ------------------------------------------------------------------------------
# 6. Detect Connected USB Device
# ------------------------------------------------------------------------------
echo ""
echo "[*] Detecting connected Android phone over USB..."

get_devices() {
  "$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1}'
}

get_unauthorized() {
  "$ADB_BIN" devices | awk 'NR>1 && $2=="unauthorized" {print $1}'
}

# If user specified a device serial, verify it
if [[ -n "$TARGET_DEVICE" ]]; then
  DEVICE_ID="$TARGET_DEVICE"
else
  DEVICES=($(get_devices))
  if [[ ${#DEVICES[@]} -eq 1 ]]; then
    DEVICE_ID="${DEVICES[0]}"
  elif [[ ${#DEVICES[@]} -gt 1 ]]; then
    echo "[!] Multiple authorized devices detected:"
    for idx in "${!DEVICES[@]}"; do
      DEV="${DEVICES[$idx]}"
      MODEL_INFO="$("$ADB_BIN" -s "$DEV" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo 'Unknown')"
      echo "    [$((idx+1))] $DEV ($MODEL_INFO)"
    done
    DEVICE_ID="${DEVICES[0]}"
    echo "[*] Defaulting to primary device: $DEVICE_ID (use -s <serial> to choose another)"
  else
    DEVICE_ID=""
  fi
fi

if [[ -z "$DEVICE_ID" ]]; then
  echo ""
  echo "⚠️  No authorized Android device detected over USB."
  echo "------------------------------------------------------------------"
  echo "Please check the following on your Android mobile device:"
  echo "  1. Connect your phone to your computer using a USB cable."
  echo "  2. Enable Developer Options:"
  echo "     Settings > About Phone > Tap 'Build number' 7 times."
  echo "  3. Enable USB Debugging:"
  echo "     Settings > System / Developer options > USB Debugging (Toggle ON)."
  echo "  4. Look at your phone screen: if prompted with 'Allow USB debugging?',"
  echo "     check 'Always allow from this computer' and tap 'Allow'."
  echo "------------------------------------------------------------------"
  echo "[*] Waiting for USB device connection (Press Ctrl+C to cancel)..."

  while [[ -z "$DEVICE_ID" ]]; do
    sleep 2
    UNAUTH="$(get_unauthorized | head -n 1)"
    if [[ -n "$UNAUTH" ]]; then
      echo "👉 Phone detected ($UNAUTH) but UNAUTHORIZED."
      echo "   Please unlock your phone screen and tap 'Allow' for USB Debugging!"
    fi
    DEVICES=($(get_devices))
    if [[ ${#DEVICES[@]} -gt 0 ]]; then
      DEVICE_ID="${DEVICES[0]}"
    fi
  done
fi

MODEL="$("$ADB_BIN" -s "$DEVICE_ID" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo 'Android Device')"
ANDROID_VER="$("$ADB_BIN" -s "$DEVICE_ID" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo 'Unknown')"
echo "[✓] Connected Device: $MODEL (Android $ANDROID_VER, Serial: $DEVICE_ID)"

# ------------------------------------------------------------------------------
# 7. Stream & Install Directly to Phone via USB
# ------------------------------------------------------------------------------
echo ""
echo "[*] Streaming APK to $MODEL via USB..."
# -r: Replace existing application (preserve app preferences)
# -d: Allow version code downgrade
# -g: Grant all runtime permissions (CAMERA, POST_NOTIFICATIONS) automatically!
"$ADB_BIN" -s "$DEVICE_ID" install -r -d -g "$TARGET_APK"
echo "[✓] Successfully installed on $MODEL!"

# ------------------------------------------------------------------------------
# 8. Launch App on Phone Screen
# ------------------------------------------------------------------------------
echo "[*] Launching $PACKAGE_NAME on device..."
"$ADB_BIN" -s "$DEVICE_ID" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" >/dev/null
echo "[🚀] BlinkWell is now running on your phone!"

# ------------------------------------------------------------------------------
# 9. Ephemeral Mode Cleanup Handler (if enabled)
# ------------------------------------------------------------------------------
cleanup() {
  if $EPHEMERAL; then
    echo ""
    echo "[*] Ephemeral mode: Uninstalling $PACKAGE_NAME from device..."
    "$ADB_BIN" -s "$DEVICE_ID" uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
    echo "[✓] App cleanly uninstalled from phone. Zero leftover footprint."
  fi
  exit 0
}
trap cleanup INT TERM EXIT

# ------------------------------------------------------------------------------
# 10. Live Telemetry & Logcat Streaming
# ------------------------------------------------------------------------------
if $LOGS; then
  echo ""
  echo "------------------------------------------------------------------"
  echo "  Live BlinkWell Device Telemetry (Press Ctrl+C to exit)"
  echo "------------------------------------------------------------------"
  # Clear log buffer before streaming
  "$ADB_BIN" -s "$DEVICE_ID" logcat -c

  if [[ -n "$LOG_FILTER" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" logcat -v time $LOG_FILTER
  else
    # Follow BlinkWell & Camera & MLKit logs and crashes
    "$ADB_BIN" -s "$DEVICE_ID" logcat -v time \
      BlinkMonitorService:V \
      BlinkDetector:V \
      BlinkWell:V \
      MLKit:V \
      CameraX:I \
      ActivityManager:I \
      AndroidRuntime:E \
      *:S
  fi
fi
