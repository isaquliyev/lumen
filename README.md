# Lumen

A personal, sideloaded Android music player (Kotlin + Jetpack Compose).
The full spec is in `mobile-app-plan.md`, and build progress is in `PROGRESS.md`.

## Build

Run Gradle with Android Studio's bundled JDK (system Java 11 is too old):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug        # debug build
./gradlew assembleRelease      # release build (R8, signed with the release key)
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew connectedDebugAndroidTest   # Room tests on the phone (uninstalls the app afterwards)
```

The release key is in `keystore/lumen-release.jks`, and its passwords are in `keystore.properties`.
Both are gitignored, so **keep a copy somewhere safe**: an app signed with a different key can only be
installed after uninstalling, and then the library has to be restored from `Lumen/data`.
Without `keystore.properties`, release builds fall back to the debug key.

## Install on the phone

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" install -r app/build/outputs/apk/release/app-release.apk
"$ADB" shell am start -n com.lumen.player/.MainActivity
```

## Where the data lives

Everything is in `/storage/emulated/0/Lumen/` (audio, covers, artist photos, lyrics, playlist covers,
recap exports). The database is backed up automatically to `Lumen/data/lumen.db`
(previous copy: `lumen-prev.db`). After a reinstall, onboarding offers to restore from it.
