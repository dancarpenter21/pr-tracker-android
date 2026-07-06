# PR Tracker For Android

Offline Android app for tracking weightlifting PRs, estimated 1RM, bodyweight,
Wilks score, major-lift totals, and progress graphs.

## Stack

- Kotlin + Jetpack Compose for Android UI.
- Room over local SQLite for all app data.
- Rust business-logic core packaged as `libprtracker_core.so`.
- Manual JNI bridge through `com.prtracker.core.PrCore`.
- No network/runtime permissions and no ads.

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
