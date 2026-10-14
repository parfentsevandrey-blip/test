# Retro Databases

An Android application in Kotlin, Jetpack Compose and Room. Inside is "Database 1": a catalogue of 80s coding.

## What is inside

The catalogue is split by kind of record:

| Kind | Records | What it is |
|---|---:|---|
| Languages | 56 | Languages of the 1950s–80s, from mass-market to almost forgotten |
| Systems | 48 | Operating systems, Unix variants, research environments, DBMSs, networks |
| Devices | 99 | Mainframes, Soviet machines, PCs, home computers, processors, peripherals |
| Tools | 33 | make, lint, yacc, GCC, editors, debugger, TeX, sed and others |
| Practices | 60 | Methods, design, testing, standards, processes, BBS and demoscene culture |
| Guides | 5 | How pipelines, recursive descent, the Forth stack, Makefiles and emulators work |
| Examples | 5 | Short recipes: word frequencies, sed, grep on logs, qsort, GROUP BY |
| Projects | 4 | Game of Life in awk, a maze in C, an address book in sh, a yacc calculator |
| Flex | 5 | The maximum from the minimum: a fractal with one operator, a quine, Mandelbrot in awk |
| Cross-code | 5 | Experiments with mixed languages: C with awk, make with awk, Forth inside C, bc with awk |

In total there are 320 records, 181 of them flagged as rare. Each record has a year, an author, a section, a short summary, details, instructions (for projects and guides) and a code example.

In the app:
- the list of databases with the number of records;
- search by title, original title, author, year and section (case-insensitive);
- filters by kind, favorites and rare records;
- records grouped by section within each kind;
- favorites are saved in the database.

## Build and run

Requirements: Android Studio (the latest version), JDK 17, Android SDK 35 (platform 35 and build-tools 35.0.0). The minimum Android version is 8.0 (API 26).

1. Open the `RetroDatabases` folder in Android Studio and wait for the Gradle sync to finish.
2. Press Run on an emulator or a device.

From the command line:

```
./gradlew assembleDebug        # build the APK
./gradlew testDebugUnitTest    # tests
```

The `local.properties` file with the SDK path is created by Android Studio automatically.

## Structure

- `app/src/main/java/com/example/retrodatabases/data` — Room: entities, DAO, converters, the database itself and seeding on first launch.
- `app/src/main/java/com/example/retrodatabases/data/seed` — the records of database 1, one file per kind, and the list of databases.
- `app/src/main/java/com/example/retrodatabases/ui` — navigation, the ViewModel, filtering, screens and components.
- `app/src/test` — tests of the filters, the data and the database itself (Robolectric).
- `DEVLOG.md` — the development log with the growth of lines of code by checkpoint.
- `RetroDatabases_source.txt` — all the source code in one text file.

## Examples in the catalogue

The code in the examples and instructions was checked by running it: gcc, g++, mawk, sh, make, perl, bc, bison and SQLite. For languages that are not installed in the build environment (Forth, Prolog, Lisp, Smalltalk, BASIC and others), the examples were checked only by reading, and this is noted in the development log.

## How to add a database

1. Create a file in `data/seed` with the records and the `databaseId` of the new database. The `entry()` function in `SeedEntry.kt` currently fixes `databaseId = 1`: for a second database, add a parameter.
2. Add the database to `seedDatabases` (file `SeedCatalog.kt`).
3. Databases from the seed lists are written to the database only when it is created. In copies that are already installed, a new database appears after the database version is raised in `AppDatabase` and a `Migration` is written.
