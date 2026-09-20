#!/data/data/com.termux/files/usr/bin/bash
set -e
: "${ANDROID_HOME:=$HOME/android-sdk}"
export ANDROID_HOME
AAPT2=$(find "$ANDROID_HOME/build-tools" -type f -name aapt2 | sort -V | tail -1)
[ -n "$AAPT2" ] || { echo 'aapt2 غير موجود'; exit 1; }
echo "Using aapt2: $AAPT2"
# AGP 8.7.x requires Gradle 8.9. Use local gradle only if compatible; otherwise wrapper is preferred.
if [ -x ./gradlew ]; then ./gradlew -Pandroid.aapt2FromMavenOverride="$AAPT2" :app:assembleDebug
else gradle -Pandroid.aapt2FromMavenOverride="$AAPT2" :app:assembleDebug
fi
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
