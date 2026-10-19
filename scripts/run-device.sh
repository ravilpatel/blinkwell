#!/usr/bin/env bash
# ==============================================================================
# BlinkWell — Zero-Friction USB Device Runner & CI Deployer
# ==============================================================================
# Fetches the latest compiled APK directly from GitHub Actions CI and streams
# it straight to your USB-connected Android phone without manual downloading,
# unzipping, or clicking phone installation dialogs.
# ==============================================================================

set -euo pipefail

PACKAGE_NAME="com.mitalipurohit.blinkwell"
MAIN_ACTIVITY="com.mitalipurohit.blinkwell.ui.MainActivity"
WORKFLOW_NAME="android-build.yml"
ARTIFACT_NAME="blinkwell-debug-apk"
TOOLS_DIR="$HOME/.local/share/blinkwell-tools"
PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip"
CACHE_DIR="/tmp/blinkwell-ci-cache"

WATCH_CI=false
EPHEMERAL=false
LOGS=true
LOCAL_APK=""

# --- Parse Arguments ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    --watch|-w)
      WATCH_CI=true
      shift
      ;;
    --ephemeral|-e)
      EPHEMERAL=true
      shift
      ;;
    --no-logs)
      LOGS=false
      shift
      ;;
    --apk)
      LOCAL_APK="$2"
      shift 2
      ;;
    --help|-h)
      echo "Usage: ./run-device.sh [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  -w, --watch        Wait for any currently in-progress CI build to finish"
      echo "  -e, --ephemeral    Ephemeral mode: auto-uninstalls the app when testing ends (Ctrl+C)"
      echo "      --no-logs      Skip live logcat streaming after launch"
      echo "      --apk <file>   Use a local APK instead of downloading from GitHub Actions"
      echo "  -h, --help         Show this help message"
      exit 0
      ;;
    *)
      if [[ -f "$1" && "$1" == *.apk ]]; then
        LOCAL_APK="$1"
        shift
      else
        echo "Unknown option: $1"
        echo "Run with --help for usage."
        exit 1
      fi
      ;;
  esac
done

echo "=========================================================="
echo "  BlinkWell — USB Direct Runner (Powered by GitHub Actions)"
echo "=========================================================="

# ------------------------------------------------------------------------------
# 1. Ensure ADB is Available (Self-Bootstrapping, Zero Root Required)
# ------------------------------------------------------------------------------
ADB_BIN=""
if command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
elif [[ -x "$TOOLS_DIR/platform-tools/adb" ]]; then
  ADB_BIN="$TOOLS_DIR/platform-tools/adb"
else
  echo "[+] ADB not found on system. Bootstrapping official Google platform-tools..."
  mkdir -p "$TOOLS_DIR"
  TMP_ZIP="/tmp/platform-tools.zip"
  
  if command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "$TMP_ZIP" "$PLATFORM_TOOLS_URL"
  elif command -v curl >/dev/null 2>&1; then
    curl -L --progress-bar -o "$TMP_ZIP" "$PLATFORM_TOOLS_URL"
  else
    python3 -c "import urllib.request; urllib.request.urlretrieve('$PLATFORM_TOOLS_URL', '$TMP_ZIP')"
  fi

  unzip -q -o "$TMP_ZIP" -d "$TOOLS_DIR"
  rm -f "$TMP_ZIP"
  ADB_BIN="$TOOLS_DIR/platform-tools/adb"
  chmod +x "$ADB_BIN"
  echo "[✓] ADB successfully installed locally to $ADB_BIN (no root required)"
fi

# ------------------------------------------------------------------------------
# 2. Check Android Device via USB
# ------------------------------------------------------------------------------
echo ""
echo "[*] Detecting connected Android phone over USB..."

check_device() {
  "$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1}' | head -n 1
}

DEVICE_ID="$(check_device)"

if [[ -z "$DEVICE_ID" ]]; then
  echo ""
  echo "⚠️  No authorized Android device detected over USB."
  echo "----------------------------------------------------------"
  echo "Please check the following on your Android mobile:"
  echo "  1. Connect your phone to your computer using a USB cable."
  echo "  2. Enable Developer Options:"
  echo "     Settings > About Phone > Tap 'Build number' 7 times."
  echo "  3. Enable USB Debugging:"
  echo "     Settings > System / Developer options > USB Debugging (ON)."
  echo "  4. Look at your phone screen: if prompted with 'Allow USB debugging?',"
  echo "     check 'Always allow' and tap 'Allow'."
  echo "----------------------------------------------------------"
  echo "[*] Waiting for USB device connection (Press Ctrl+C to cancel)..."
  
  while [[ -z "$DEVICE_ID" ]]; do
    sleep 2
    # Check if device is in unauthorized state
    UNAUTH="$("$ADB_BIN" devices | awk 'NR>1 && $2=="unauthorized" {print $1}')"
    if [[ -n "$UNAUTH" ]]; then
      echo "👉 Phone detected ($UNAUTH) but UNAUTHORIZED. Please tap 'Allow' on your phone screen!"
    fi
    DEVICE_ID="$(check_device)"
  done
fi

MODEL="$("$ADB_BIN" -s "$DEVICE_ID" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo 'Android Device')"
ANDROID_VER="$("$ADB_BIN" -s "$DEVICE_ID" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo 'Unknown')"
echo "[✓] Connected Device: $MODEL (Android $ANDROID_VER, Serial: $DEVICE_ID)"

# ------------------------------------------------------------------------------
# 3. Retrieve APK (GitHub Actions CI or Local)
# ------------------------------------------------------------------------------
TARGET_APK=""

if [[ -n "$LOCAL_APK" ]]; then
  echo "[*] Using provided local APK: $LOCAL_APK"
  TARGET_APK="$LOCAL_APK"
else
  echo ""
  echo "[*] Querying GitHub Actions for latest compiled build..."
  mkdir -p "$CACHE_DIR"

  if ! command -v gh >/dev/null 2>&1; then
    echo "[-] Error: 'gh' (GitHub CLI) is not installed."
    echo "    Please install GitHub CLI or pass an APK directly: ./run-device.sh --apk <path>"
    exit 1
  fi

  if $WATCH_CI; then
    echo "[*] Checking for active in-progress CI runs..."
    IN_PROGRESS_ID="$(gh run list --workflow="$WORKFLOW_NAME" --json databaseId,status -q '.[] | select(.status=="in_progress") | .databaseId' | head -n 1 || true)"
    if [[ -n "$IN_PROGRESS_ID" ]]; then
      echo "[*] Build #$IN_PROGRESS_ID is currently in progress. Watching build until completion..."
      gh run watch "$IN_PROGRESS_ID"
    fi
  fi

  LATEST_SUCCESS_RUN="$(gh run list --workflow="$WORKFLOW_NAME" --status=success --limit 1 --json databaseId,headSha,createdAt -q '.[0]')"
  
  if [[ -z "$LATEST_SUCCESS_RUN" || "$LATEST_SUCCESS_RUN" == "null" ]]; then
    echo "[-] No successful GitHub Actions build found for workflow '$WORKFLOW_NAME'."
    echo "    Ensure a build has run and succeeded on GitHub."
    exit 1
  fi

  RUN_ID="$(echo "$LATEST_SUCCESS_RUN" | python3 -c "import sys, json; print(json.load(sys.stdin)['databaseId'])")"
  RUN_SHA="$(echo "$LATEST_SUCCESS_RUN" | python3 -c "import sys, json; print(json.load(sys.stdin)['headSha'][:7])")"

  echo "[✓] Found latest successful CI Run #$RUN_ID (commit: $RUN_SHA)"
  echo "[*] Downloading build artifact '$ARTIFACT_NAME' directly over GitHub API..."
  
  RUN_CACHE_DIR="$CACHE_DIR/run-$RUN_ID"
  rm -rf "$RUN_CACHE_DIR"
  mkdir -p "$RUN_CACHE_DIR"
  
  gh run download "$RUN_ID" -n "$ARTIFACT_NAME" --dir "$RUN_CACHE_DIR"
  
  DOWNLOADED_APK="$(find "$RUN_CACHE_DIR" -name "*.apk" | head -n 1)"
  if [[ -z "$DOWNLOADED_APK" || ! -f "$DOWNLOADED_APK" ]]; then
    echo "[-] Failed to locate .apk inside downloaded artifact."
    exit 1
  fi
  TARGET_APK="$DOWNLOADED_APK"
  echo "[✓] Artifact ready: $(basename "$TARGET_APK") ($(du -h "$TARGET_APK" | cut -f1))"
fi

# ------------------------------------------------------------------------------
# 4. Stream & Install Directly to Phone via USB
# ------------------------------------------------------------------------------
echo ""
echo "[*] Streaming APK to $MODEL via USB..."
# -r: Replace existing application (keeps app preferences)
# -d: Allow version code downgrade
# -g: Grant all runtime permissions (CAMERA, NOTIFICATIONS) automatically!
"$ADB_BIN" -s "$DEVICE_ID" install -r -d -g "$TARGET_APK"
echo "[✓] Successfully installed and configured on $MODEL!"

# ------------------------------------------------------------------------------
# 5. Launch App Immediately on Phone Screen
# ------------------------------------------------------------------------------
echo "[*] Launching BlinkWell on device..."
"$ADB_BIN" -s "$DEVICE_ID" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" >/dev/null
echo "[🚀] BlinkWell is now running on your phone!"

# ------------------------------------------------------------------------------
# 6. Ephemeral Mode Cleanup Handler (if enabled)
# ------------------------------------------------------------------------------
cleanup() {
  if $EPHEMERAL; then
    echo ""
    echo "[*] Ephemeral mode: Uninstalling $PACKAGE_NAME from device..."
    "$ADB_BIN" -s "$DEVICE_ID" uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
    echo "[✓] App cleanly removed from phone. Zero leftover footprint."
  fi
  exit 0
}
trap cleanup INT TERM EXIT

# ------------------------------------------------------------------------------
# 7. Live Telemetry & Logcat Streaming (Optional)
# ------------------------------------------------------------------------------
if $LOGS; then
  echo ""
  echo "----------------------------------------------------------"
  echo "  Live BlinkWell Device Telemetry (Press Ctrl+C to exit)"
  echo "----------------------------------------------------------"
  # Clear log buffer and follow BlinkWell & Camera logs
  "$ADB_BIN" -s "$DEVICE_ID" logcat -c
  "$ADB_BIN" -s "$DEVICE_ID" logcat -v time \
    BlinkMonitorService:V \
    BlinkWell:V \
    MLKit:V \
    CameraX:I \
    ActivityManager:I \
    AndroidRuntime:E \
    *:S
fi

