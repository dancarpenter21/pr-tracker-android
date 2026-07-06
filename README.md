# PR Tracker For Android

![PR Background Landscape](PR-bckgnd-landscape.png)

Offline Android app for tracking weightlifting PRs, estimated 1RM, bodyweight,
Wilks score, major-lift totals, and progress graphs.

## Stack

- Kotlin + Jetpack Compose for Android UI.
- Room over local SQLite for all app data.
- Rust business-logic core packaged as `libprtracker_core.so`.
- Manual JNI bridge through `com.prtracker.core.PrCore`.
- Google Drive app-data backup and restore.
- No ads.

## WSL Setup

Java and Rust must already be installed. To install or repair the Android SDK,
NDK, Rust Android targets, `cargo-ndk`, and `local.properties`, run:

```sh
./scripts/setup_android_sdk.sh
```

The script uses `ANDROID_HOME` if set, otherwise `/opt/android-sdk`.
It installs Android platform API 36 because API 37 is not available from the
stable SDK package channel.

## Build

```sh
cargo test --manifest-path core/Cargo.toml
./gradlew test
./gradlew assembleDebug
```

If this checkout does not yet have a Gradle wrapper, create it with a local
Gradle install:

```sh
gradle wrapper --gradle-version 9.4.1
```

Install a debug build on a connected device or Windows-side emulator:

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Google Drive Backup Setup

The app uses Google Drive's private app data folder and requests only the
`https://www.googleapis.com/auth/drive.appdata` scope.

Before Drive sign-in works on a device:

1. Create or select a Google Cloud project.
2. Enable the Google Drive API.
3. Configure the OAuth consent screen.
4. Create an Android OAuth client for package `com.prtracker`.
5. Add the SHA-1 fingerprint for the debug or release signing key you are
   installing with.

The app does not need `google-services.json` for this direct Drive REST
integration.

For the debug APK, get the SHA-1 with:

```sh
keytool -list -v \
  -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android \
  -keypass android
```

If Google sign-in shows `DEVELOPER_ERROR` or stays in a Drive authorization
pending state, the Android OAuth client package name or SHA-1 does not match
the installed APK.
