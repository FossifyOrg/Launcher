#!/usr/bin/env bash
# Build the LAUNCHPAD Companion App (separate APK for the parent's phone).
#
# The companion lives in companion/ as a SEPARATE Gradle project — applying the
# Kotlin Android plugin in two modules of the same Gradle build fails with
# "kotlin extension already registered" (Kotlin 2.x + AGP 9.x quirk). So this
# script uses the launcher's wrapper but points it at the companion's settings.
#
# Output: companion/app/build/outputs/apk/debug/app-debug.apk
#
# Usage:
#   ./build-companion.sh             # debug APK
#   ./build-companion.sh release     # release APK

set -e
TASK="${1:-assembleDebug}"
case "$TASK" in
  release) GRADLE_TASK="assembleRelease" ;;
  debug)   GRADLE_TASK="assembleDebug" ;;
  *)       GRADLE_TASK="$TASK" ;;
esac

cd "$(dirname "$0")"
echo "🛠  Building companion APK (task: :app:$GRADLE_TASK)..."
./gradlew -p companion ":app:$GRADLE_TASK" --no-daemon
APK="companion/app/build/outputs/apk/${GRADLE_TASK#assemble}/app-${GRADLE_TASK#assemble}.apk"
APK_LC=$(echo "$APK" | tr '[:upper:]' '[:lower:]')
if [ -f "$APK_LC" ]; then
  echo "✅ APK: $APK_LC ($(du -h "$APK_LC" | cut -f1))"
else
  find companion/app/build/outputs/apk -name "*.apk" -print 2>/dev/null || echo "❌ APK not found"
fi
