# Lumen

A personal, sideloaded Android music player (Kotlin + Jetpack Compose).
The full spec is in `mobile-app-plan.md`, and build progress is in `PROGRESS.md`.

## Build

Run Gradle with Android Studio's bundled JDK (system Java 11 is too old):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

## Install on the phone

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.lumen.player/.MainActivity
```
