# InstaWrap — Instagram web wrapper for Android 17

A minimal native shell around the Instagram mobile web site (`https://www.instagram.com/`).
No API access, no scraping: it renders instagram.com in a WebView and adds the native
bits the browser tab doesn't give you.

## What it does
- Loads instagram.com in a full-screen WebView with a Chrome-on-Android user agent
- Persistent cookies, so you stay logged in between launches
- Hardware **Back** walks the web history, then leaves the app
- **Pull to refresh**
- **Photo/video uploads** through the system file picker
- **Camera / microphone** permission bridging for stories and reels capture
- **Downloads** go through Android's DownloadManager
- Links off instagram.com open in the user's browser
- `https://instagram.com/...` links from other apps open here
- Adaptive icon, light/dark aware

## Build
```
export ANDROID_HOME=/path/to/android-sdk   # needs platform 37.0 + build-tools 37.0.0
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

## Targeting
`compileSdk`/`targetSdk` 37.0 — Android 17. `minSdk` 24 (Android 7.0).

## Signing
The release build is signed with the local **debug** key so the APK installs without
extra setup. Before publishing anywhere, replace `signingConfig signingConfigs.debug`
in `app/build.gradle` with your own release keystore.

## Installing
Enable "install unknown apps" for your file manager, then open the APK, or:
```
adb install -r app-release.apk
```
