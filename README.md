# Speech Split

A speech timer that splits your talk into parts, so you always know if you're on time.
For **Windows** and **Android**. Free, no ads, no account.

**Download:** see the project's GitHub Pages site, or the latest release under *Releases*.

## Project layout

| Folder | What it is |
|---|---|
| `app/src/main/java/no/srrlsm/speechsplit/core` | App logic, timer, history, translations. Plain Kotlin, shared by all versions. |
| `app/src/main/java/no/srrlsm/speechsplit/ui` | All screens (Compose). Shared by Android and Windows. |
| `app/src/main/java/no/srrlsm/speechsplit/jvm` | Save format (JSON), shared by Android and Windows. |
| `app/src/main/java/no/srrlsm/speechsplit/platform` | Android only: storage, vibration, watch, Do Not Disturb. |
| `desktop/` | Windows (and Mac) app: window, keyboard shortcuts, storage. |
| `docs/` | The download page (GitHub Pages). |
| `.github/workflows/` | Builds the Windows installer automatically on each release. |

## Version number

Change `speechSplitVersion` in `gradle.properties`. For Android, also raise `versionCode` in `app/build.gradle.kts` by 1.

## Making a release

1. On GitHub: **Releases → Draft a new release**, tag e.g. `v2.1`, press **Publish**.
2. About 10 minutes later, `SpeechSplit-Setup.msi` is attached to the release automatically.
3. Build the signed Android APK in Android Studio, rename it to `SpeechSplit.apk`, and drag it into the release.

The download page always links to the newest release.

## Run on your own computer

```
./gradlew :desktop:run          # desktop app
./gradlew :desktop:screenshots  # renders the screens to desktop/build/screenshots
./gradlew :app:assembleDebug    # Android debug APK
```
