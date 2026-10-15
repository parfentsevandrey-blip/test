# Retro Databases

An Android application in Kotlin, Jetpack Compose and Room. Inside are databases of coding history, listed under *What is inside*.

## What is inside

<!-- BEGIN GENERATED OVERVIEW -->
Inside are 54 databases of coding history, from 1500 to 2036. The table gives each one's years, records and rare records. The kinds and titles of every database are in [CATALOGUE.md](CATALOGUE.md).

| Database | Years | Records | Rare |
|---|---|---:|---:|
| 1 | 1950 to 1990 | 320 | 181 |
| 2 | 1990 to 1999 | 142 | 68 |
| 3 | 2000 to 2010 | 141 | 56 |
| 4 | 2010 to 2020 | 109 | 38 |
| 5 | 2020 to 2026 | 99 | 30 |
| 6 | 1950 to 1960 | 60 | 29 |
| 7 | 1960 to 1970 | 49 | 26 |
| 8 | 1970 to 1980 | 50 | 22 |
| 9 | 2026 to 2036 | 50 | 6 |
| 10 | 1940 to 1950 | 50 | 10 |
| 11 | 1930 to 1940 | 50 | 9 |
| 12 | 1920 to 1930 | 50 | 7 |
| 13 | 1910 to 1920 | 50 | 10 |
| 14 | 1900 to 1910 | 50 | 9 |
| 15 | 1890 to 1900 | 50 | 9 |
| 16 | 1880 to 1890 | 50 | 8 |
| 17 | 1870 to 1880 | 50 | 10 |
| 18 | 1860 to 1870 | 50 | 10 |
| 19 | 1850 to 1860 | 50 | 8 |
| 20 | 1840 to 1850 | 50 | 8 |
| 21 | 1830 to 1840 | 50 | 9 |
| 22 | 1820 to 1830 | 50 | 10 |
| 23 | 1810 to 1820 | 50 | 10 |
| 24 | 1800 to 1810 | 50 | 8 |
| 25 | 1790 to 1800 | 50 | 10 |
| 26 | 1780 to 1790 | 50 | 10 |
| 27 | 1770 to 1780 | 50 | 9 |
| 28 | 1760 to 1770 | 50 | 8 |
| 29 | 1750 to 1760 | 50 | 9 |
| 30 | 1740 to 1750 | 50 | 9 |
| 31 | 1730 to 1740 | 50 | 8 |
| 32 | 1720 to 1730 | 50 | 8 |
| 33 | 1710 to 1720 | 50 | 8 |
| 34 | 1700 to 1710 | 50 | 10 |
| 35 | 1690 to 1700 | 50 | 8 |
| 36 | 1680 to 1690 | 50 | 9 |
| 37 | 1670 to 1680 | 50 | 8 |
| 38 | 1660 to 1670 | 50 | 9 |
| 39 | 1650 to 1660 | 50 | 9 |
| 40 | 1640 to 1650 | 50 | 9 |
| 41 | 1630 to 1640 | 50 | 9 |
| 42 | 1620 to 1630 | 50 | 10 |
| 43 | 1610 to 1620 | 50 | 9 |
| 44 | 1600 to 1610 | 50 | 9 |
| 45 | 1590 to 1600 | 50 | 10 |
| 46 | 1580 to 1590 | 50 | 9 |
| 47 | 1570 to 1580 | 50 | 8 |
| 48 | 1560 to 1570 | 50 | 8 |
| 49 | 1550 to 1560 | 50 | 9 |
| 50 | 1540 to 1550 | 50 | 9 |
| 51 | 1530 to 1540 | 50 | 8 |
| 52 | 1520 to 1530 | 50 | 8 |
| 53 | 1510 to 1520 | 50 | 9 |
| 54 | 1500 to 1510 | 50 | 9 |
| **All** | | **3,270** | **856** |
<!-- END GENERATED OVERVIEW -->


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

<!-- BEGIN GENERATED STRUCTURE -->
- `app/src/main/java/com/example/retrodatabases/data` — Room: entities, DAO, converters, the database itself, seeding on first launch and one generic migration for each version from 1 to 54 (`seedMigrations`).
- `app/src/main/java/com/example/retrodatabases/data/seed` — the records of all 54 databases: `Db1*.kt` to `Db54*.kt`, one file per kind; `SeedCatalog.kt`, generated from `tools/databases.json`; the helper that builds a record (`SeedEntry.kt`).
- `tools` — `databases.json` (the databases, their years and colours) and `build_catalogue.py`, which regenerates `SeedCatalog.kt`, the overview in this file and `CATALOGUE.md`, and checks every record.
<!-- END GENERATED STRUCTURE -->
- `app/src/main/java/com/example/retrodatabases/ui` — navigation, the ViewModel, filtering, screens and components.
- `app/src/test` — tests of the filters, the data and the database itself (Robolectric).
- `DEVLOG.md` — the development log with the growth of lines of code by checkpoint.
- `RetroDatabases_source.txt` — all the source code in one text file.

## Examples in the catalogue

The code in the examples and instructions was checked by running it wherever the build environment had the tools: gcc, g++, mawk, sh, make, perl, bc, bison, SQLite, Java (javac and java), PHP, Node.js, Ruby, Python, Go, Git, curl, rustc, NASM and Bun. In database 1, 32 examples run and pass. In database 2, 27 examples run and pass. In database 3, 19 examples run and pass. In database 4, 24 examples run and pass. In database 5, 21 examples run and pass. In database 6, 17 examples run and pass. In database 7, 19 examples run and pass. In database 8, 20 examples run and pass. In database 9, 20 examples run and pass. In database 10, 20 examples run and pass. In database 11, 20 examples run and pass. In database 12, 20 examples run and pass. In database 13, 20 examples run and pass. In database 14, 20 examples run and pass. In each of databases 15 to 54, 20 examples run and pass. The other records describe things that cannot run in that environment (Turbo Pascal, Visual Basic, DOS, Windows APIs, consoles, hardware and products, which have no code at all, and the early languages such as Speedcoding, JOVIAL or MUMPS, which have no compiler there).

For languages that are not installed in the build environment (Forth, Prolog, Lisp, Smalltalk, Erlang, Lua, Haskell, OCaml, C#, Scala, Swift, Zig, Kotlin, Dart, Elixir, Julia and others), the examples were checked only by reading, and this is noted in the development log.

## How to add a database

1. Write the record files in `data/seed`: one file per kind (`Db<N>Languages.kt` to `Db<N>Catalog.kt`), each record with `databaseId = N`.
2. Add the database to `tools/databases.json`: its id, its years (`from`, `to`), its name, its description, a colour no other database uses, and `"quota": true` for a 50-record batch.
3. Run `python3 tools/build_catalogue.py`. It regenerates `SeedCatalog.kt`, the overview and structure blocks of this file and the generated part of `CATALOGUE.md`, and it checks every record.
4. Run the tests. No migration is written by hand: `seedMigrations` has one migration for every version up to `DATABASE_VERSION`, so an install made at any earlier version receives the new databases.
