# Developer notes

Notes for working on Speech Split. The project's front page is [README.md](README.md).

## Project layout

| Folder | What it is |
|---|---|
| `app/src/main/java/no/srrlsm/speechsplit/core` | App logic, timer, history, translations. Plain Kotlin, shared by all versions. |
| `app/src/main/java/no/srrlsm/speechsplit/ui` | All screens (Compose). Shared by Android and Windows. |
| `app/src/main/java/no/srrlsm/speechsplit/jvm` | Save format (JSON), shared by Android and Windows. |
| `app/src/main/java/no/srrlsm/speechsplit/platform` | Android only: storage, vibration, watch, Do Not Disturb. |
| `desktop/` | Windows (and Mac) app: window, keyboard shortcuts, storage. |
| `docs/` | The download page (GitHub Pages), with `privacy.html` (privacy and terms). |
| `docs/app/` | The web version (for iPad and other browsers). Plain HTML/JS, works offline. Texts are in `strings.js`, document import in `import.js`. |
| `.github/workflows/` | Builds the Windows installer automatically on each release. |

## Version number

Change `speechSplitVersion` in `gradle.properties`. For Android, also raise `versionCode` in `app/build.gradle.kts` by 1.
For the web version, change `APP_VERSION` in `docs/app/app.js` and `VERSION` in `docs/app/sw.js` (so browsers pick up the new files).

The text you write in the GitHub release is shown as patch notes in the apps, the web version and on the website.

## Making a release

1. On GitHub: **Releases → Draft a new release**, tag e.g. `v2.2`, press **Publish**.
2. About 10 minutes later, `SpeechSplit-Setup.msi` is attached to the release automatically.
3. Build the signed Android APK in Android Studio, rename it to `SpeechSplit.apk`, and drag it into the release.

The download page always links to the newest release.

## Run on your own computer

```
./gradlew :desktop:run          # desktop app
./gradlew :desktop:screenshots  # renders the screens to desktop/build/screenshots
./gradlew :app:assembleDebug    # Android debug APK
./gradlew :app:testDebugUnitTest  # unit tests
```

The web version needs no build step: open `docs/app/index.html` through any local web server, e.g. `python3 -m http.server` in the `docs` folder.
