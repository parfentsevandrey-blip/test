#!/bin/sh
# Runs the tests on the device (an emulator in CI) and then takes the screenshots that GlassShotsTest wrote
# to /data/local/tmp/themesh-shots (the folder survives uninstalling the app), whatever the result of the tests was.
# Usage (from svoi/android): sh tools/emulator-run.sh [extra gradle arguments]
rm -rf emulator-shots
./gradlew --no-daemon connectedDebugAndroidTest "$@"
status=$?
adb pull /data/local/tmp/themesh-shots emulator-shots >/dev/null 2>&1 || echo "no screenshots were written"
ls -la emulator-shots 2>/dev/null || true
exit $status
