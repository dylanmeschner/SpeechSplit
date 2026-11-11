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
| `docs/app/` | The web version (for iPad and other browsers). Plain HTML/JS, works offline. Texts are in `strings.js` and `strings3.js`. |
| `docs/meetings/` | The weekly meeting programs for meeting mode, one file per language (`no.json`, `en.json`, `de.json`). |
| `tools/` | `workbook_to_json.py` (meeting workbook PDF to program file) and `strings3.py` (texts added in 3.0). |
| `.github/workflows/` | Builds the Windows installer automatically on each release. |

## Version number

Change `speechSplitVersion` in `gradle.properties`. For Android, also raise `versionCode` in `app/build.gradle.kts` by 1.
For the web version, change `APP_VERSION` in `docs/app/app.js` and `VERSION` in `docs/app/sw.js` (so browsers pick up the new files).

The text you write in the GitHub release is shown as patch notes in the apps, the web version and on the website.
Write one section per language, each starting with a marker line (invisible on GitHub); each place shows the user's
language, falling back to English:

```
<!-- en -->
### New
- ...
---
<!-- no -->
### Nytt
- ...
---
<!-- de -->
### Neu
- ...
```

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

## Speech files (.speechsplit)

"Export" writes a `.speechsplit` file (plain JSON), in one of three kinds:

- one speech: `title` and `segments` (`title`, `target` in seconds),
- one speech with history: the same plus ids and `history` (practice runs),
- the whole library: `"kind": "library"`, `speeches` (archive included) and `history`.

Every version opens it with Import → Open a file. A library file asks: add what's missing, or replace everything.
File type: `application/vnd.speechsplit+json`. Built in `JsonCodec` (apps) and `exportFile` in `docs/app/app.js` (web).

## Freeze log (Windows/desktop)

If the window stops responding for more than 2 seconds, `FreezeWatch.kt` writes what the app was doing to
`%APPDATA%\Speech Split\freeze-log.txt`. The feedback email mentions the file when it exists.

## Texts added in 3.0

The texts that came with 3.0 (meeting mode, folders, summary, introduction...) live in one place for all
three languages: `tools/strings3.py`. Change them there and run `python3 tools/strings3.py`; it writes
`docs/app/strings3.js` (web) and `core/Strings3.kt` (apps). Older texts are still in `Strings.kt` and `strings.js`.

## Sub-parts, spare time and the second screen (3.1)

- **Sub-parts**: on the meeting screen a part can be split into sub-parts (or filled from a saved speech). They're kept
  per week and part (`subpartsKey` in `core/Meetings.kt`), and `expandSubparts` swaps them in when the meeting starts,
  so the timer itself is unchanged. Web: `data.subparts` and `expandSubparts` in `docs/app/app.js`.
- **Spare time (buffer)** of a speech is `SpeechPlan.bufferSeconds` (`"buffer"` in the files). How a run is judged
  is in one place: `PracticeRun.diffSeconds` (web: `runDiff`).
- **Second screen** (desktop only): `SecondScreenWindow` in `desktop/.../Main.kt` shows `ui/SecondScreen.kt` on the
  other monitor while the timer is open. Setting `secondScreen`, shortcut S.

## Automatic checks

`.github/workflows/check.yml` runs on every push: the unit tests, a build of the Android and the Windows app,
and the shared test cases for the web version. A red cross next to a commit on GitHub means one of them failed.

The shared test cases are in `app/src/test/resources/vectors.json`. The apps run them in `VectorsTest.kt`, the web
version in `tools/vectors_web_test.js` (`npm install --no-save jsdom`, then `node tools/vectors_web_test.js`).
When a rule changes (how a time is read, how a run is judged), add a case there, so both keep giving the same answer.

## Meeting mode: new workbooks

The midweek program comes from `docs/meetings/<lang>.json` on the website. The apps and the web version
download it when meeting mode opens and keep the last copy, so it works offline. No new app version is needed.

When a new workbook is out (every two months):

1. Download the PDF from jw.org for each language (e.g. `mwb_N_202701.pdf`, `mwb_E_202701.pdf`, `mwb_X_202701.pdf`).
2. `pip install pdfplumber`, then `python3 tools/workbook_to_json.py no mwb_N_202701.pdf` (and `en`, `de`).
   It prints every week; check the titles and minutes. Weeks that ended more than 8 weeks ago are dropped.
3. Commit and push `docs/meetings/`. GitHub Pages publishes it within a few minutes.

The file name must contain the year and month (`202701`). Only part titles, minutes and song numbers are stored.
The weekend meeting is built into the app (song, public talk 30, song, Watchtower Study 60, song).
