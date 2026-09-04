# Optireader

A modern, offline-first EPUB and PDF reader for Android, built with Jetpack Compose and the Readium toolkit.

## Features

- **EPUB & PDF reading** with smooth paged and swipe layouts
- **Library & shelves** — organise your books into folders, auto-scan your device for files
- **Reading stats** — track reading speed (WPM), time spent, and progress per book
- **Bookmarks** — save and jump back to any position
- **TBR recommendations** — an on-device engine that suggests what to read next from your library
- **Text tools** — highlight, dictionary lookup, Google search, find-in-book, and read-aloud via the system text-selection menu
- **User dictionary** — add your own words and definitions
- **Day/night themes** with per-book appearance settings
- **First-run import wizard** to get your library set up quickly

## Requirements

- Android 9.0 (API 28) or newer
- ~130 MB free storage for the app

## Install

Download the latest signed APK from the [Releases page](https://github.com/MaanavNagda/Optireader/releases):

- **[Download Optireader APK](https://github.com/MaanavNagda/Optireader/releases/latest/download/Optireader-release.apk)**

Then open the downloaded file on your phone and allow installation from unknown sources when prompted.

> The APK is signed with a release keystore and is ready for sideloading or distribution.

## Build from source

Prerequisites: JDK 17+ and Android SDK (compileSdk 36).

```bash
git clone https://github.com/MaanavNagda/Optireader.git
cd Optireader
./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/Optireader-release.apk`.

> **Note:** this repository uses [Git LFS](https://git-lfs.com) for the bundled dictionary database (`app/src/main/assets/oewn.sqlite`). If the database appears as a tiny text file after cloning, run `git lfs pull` or make sure `git lfs install` was run before cloning.

To sign a release build yourself, create a `keystore.properties` file (see `keystore.properties.example`) and place your keystore in `release/`.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Readium Kotlin Toolkit (EPUB rendering)
- Room (local database)
- Coil (image loading)
- Retrofit + Jackson (metadata)
