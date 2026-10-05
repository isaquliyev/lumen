# Progress

## P0 · Bootstrap (2026-10-05)

**Done**
- Removed the generated Flutter scaffold. The app is native Kotlin + Compose, as the spec says.
- Gradle wrapper 9.8.0, generated with the cached Gradle 9.3.1 running on the Studio JBR (Java 25).
- Version catalog `gradle/libs.versions.toml` with the latest stable versions (checked 2026-10-05):
  AGP 9.4.1 (Studio 2026.2.1 supports up to 9.5), Kotlin 2.4.20, KSP 2.3.12, Hilt 2.60.1,
  Compose BOM 2026.09.00, Room 2.8.5, Media3 1.11.1, youtubedl-android 0.18.1 (pinned).
- AGP 9 built-in Kotlin: the app module applies only `kotlin.plugin.compose`. `kotlin-android`
  is declared `apply false` at the root just to pin KGP 2.4.20.
- Minimal Hilt + Compose app: `com.lumen.player`, "Lumen" on ink, edge-to-edge, adaptive vector icon.

**Verified**
- `./gradlew assembleDebug` succeeds.
- Installed and launched on the POCO F3 (Android 13, HyperOS 1.0); screenshot `shots/p0-launch.png`
  shows "Lumen". `adb shell input tap` works without a SecurityException.

**Open issues / deviations**
- `compileSdk` is **37**, not 36: core-ktx 1.19.1 and other current AndroidX libraries require it.
  `targetSdk` stays 36.
- AGP warns that `android:extractNativeLibs` should not be in the manifest (`useLegacyPackaging`
  already sets it). Kept for now as the spec asks; check the merged manifest in P3 and remove it if
  it's redundant.
- The release build uses debug signing until the release keystore is made in P12.
- Use `MSYS_NO_PATHCONV=1` in Git Bash for adb commands with `/sdcard/...` paths.
