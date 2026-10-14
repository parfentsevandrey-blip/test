# Retro Databases

An Android application in Kotlin, Jetpack Compose and Room. Inside are four databases of coding history:

- "Database 1" is a catalogue of 80s coding;
- "Database 2" is a catalogue of 90s coding;
- "Database 3" is a catalogue of coding from 2000 to 2010;
- "Database 4" is a catalogue of coding from 2010 to 2020.

## What is inside

The catalogue of database 1 (80s) is split by kind of record:

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

The catalogue of database 2 (90s) has the same ten kinds:

| Kind | Records | What it is |
|---|---:|---|
| Languages | 20 | Java, Perl, Python, Ruby, PHP/FI, JavaScript, Visual Basic 4, Delphi, C++98, and some forgotten languages |
| Systems | 20 | Linux, the BSDs, Windows NT and 95, Plan 9, BeOS, Solaris, Oracle 7, PostgreSQL, MySQL, Apache |
| Devices | 32 | Pentium, PowerPC 601, Alpha, consoles, graphics and sound cards, handhelds, clusters |
| Tools | 24 | CVS, Perforce, Vim, NetBeans, Cygwin, trackers, Winamp, mIRC, ICQ, zlib and others |
| Practices | 20 | Extreme Programming, Scrum, design patterns, UML, the CHAOS report, COCOMO II |
| Guides | 5 | How CGI, Java bytecode, a BSP tree, the mode 13h screen and Winsock work |
| Examples | 7 | Visit counter, word count with Hashtable, PHP table, SQL join, DOS backup |
| Projects | 4 | A web server in Perl, an ASCII raycaster, a WAD lister, a BBS door game |
| Flex | 5 | Mandelbrot in JavaScript, a Perl quine, compile-time primes, plasma and fire |
| Cross-code | 5 | Java calling C through JNI, PHP calling C, Perl writing a C header, Lua in C, Visual Basic calling a DLL |

The catalogue of database 3 (2000–2010) has the same ten kinds:

| Kind | Records | What it is |
|---|---:|---|
| Languages | 20 | C#, Visual Basic .NET, Java 5, Scala, Groovy, Python 2.0, PHP 5, Go, Clojure, Processing, and some esoteric languages |
| Systems | 20 | Windows 2000, XP and Vista, Mac OS X, iPhone OS, Android, Linux 2.6, Ubuntu, Gentoo, Knoppix, Solaris, Haiku, PostgreSQL, SQLite, the Google File System, Cassandra, MongoDB, Xen |
| Devices | 31 | Pentium 4, Athlon 64, Core 2 Duo, Cell, consoles from PlayStation 2 to PlayStation 3, iPod, iPhone, Kindle, Eee PC, OLPC XO-1, Blue Gene/L, Arduino |
| Tools | 24 | Subversion, Git, Mercurial, Ant, Maven, Selenium, Eclipse, IntelliJ IDEA, Visual Studio 2005, Xcode, Rails, WordPress, Django, Audacity, CUDA |
| Practices | 20 | Agile Manifesto, test-driven development, continuous integration, Kanban, domain-driven design, MapReduce, Ajax, Web 2.0, CMMI, OWASP Top 10, Stack Overflow |
| Guides | 5 | How Git stores history, how MapReduce counts words, how the Google File System splits a file, how Ajax works, how an ORM maps classes to tables |
| Examples | 7 | RSS parsing, a SQL window function, a PHP 5 exception, a first Git commit, Ajax, a Maven pom.xml, a generator expression |
| Projects | 4 | A podcast reader, a CamelCase wiki, a Google Maps mash-up, a Firefox extension |
| Flex | 5 | A Sudoku solver, Ruby metaprogramming, one-line Git statistics, a 1 KB demo, mod_rewrite |
| Cross-code | 5 | Python calling C through ctypes, C embedding Python, a Git hook in Python, JavaScript calling Java, C# calling C |

The catalogue of database 4 (2010 to 2020) has the same ten kinds:

| Kind | Records | What it is |
|---|---:|---|
| Languages | 20 | Rust, Swift, Zig, Pony, Crystal, TypeScript, Kotlin, Dart, Elixir, Julia, Solidity, Java 8, Python 3.6, ES2015, and some esoteric ones |
| Systems | 16 | Windows 8, Chrome OS, macOS Big Sur, Android 5.0 with ART, systemd, Wayland, Fuchsia, WebAssembly, Kubernetes, AWS Lambda, Firecracker, Java 9 modules |
| Devices | 19 | iPad, Raspberry Pi, Arduino Uno, Pebble, Google Glass, Oculus Rift DK1, Leap Motion, Ouya, Apple Watch, ESP8266, AR.Drone, Apple M1 Mac |
| Tools | 14 | Vagrant, Ansible, Docker, Visual Studio Code, Android Studio, Let's Encrypt, Jenkins, Yarn, React Native, GitHub Actions, Terraform, Git LFS, Redux, Figma |
| Practices | 14 | Responsive web design, Git flow, GitHub flow, README-driven development, the Twelve-Factor App, microservices, Semantic Versioning 2.0, Hacktoberfest, left-pad, multi-stage Docker builds |
| Guides | 5 | How JWT tokens are signed, how a promise chain runs, how Docker layers an image, how a CSS grid places items, how TLS 1.3 takes one round trip |
| Examples | 7 | async and await in Python, dataclasses, the var keyword in Java, std::optional, PHP 7 types, git worktree, a Go worker pool |
| Projects | 4 | A static site generator, a command-line task list, a Chrome extension that counts tabs, a Go file hasher |
| Flex | 5 | The walrus operator, Game of Life in JavaScript, full-text search in SQLite, sorting a dictionary, CSS custom properties |
| Cross-code | 5 | Go calling C with cgo, a Node.js addon through N-API, a Python module written in Rust, C compiled to WebAssembly, a Git clean filter in Python |

In total there are 712 records: 320 in database 1 (181 of them flagged as rare), 142 in database 2 (68 rare), 141 in database 3 (56 rare) and 109 in database 4 (38 rare). Each record has a year, an author, a section, a short summary, details, instructions (for projects, guides and examples) and a code example.

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

- `app/src/main/java/com/example/retrodatabases/data` — Room: entities, DAO, converters, the database itself, seeding on first launch and the migrations from versions 1, 2 and 3 to version 4.
- `app/src/main/java/com/example/retrodatabases/data/seed` — the records of all four databases: `Db1*.kt`, `Db2*.kt`, `Db3*.kt` and `Db4*.kt`, one file per kind; the list of databases and their records (`SeedCatalog.kt`); the helper that builds a record (`SeedEntry.kt`).
- `app/src/main/java/com/example/retrodatabases/ui` — navigation, the ViewModel, filtering, screens and components.
- `app/src/test` — tests of the filters, the data and the database itself (Robolectric).
- `DEVLOG.md` — the development log with the growth of lines of code by checkpoint.
- `RetroDatabases_source.txt` — all the source code in one text file.

## Examples in the catalogue

The code in the examples and instructions was checked by running it wherever the build environment had the tools: gcc, g++, mawk, sh, make, perl, bc, bison, SQLite, Java (javac and java), PHP, Node.js, Ruby, Python, Go, Git and curl. In database 1, 32 examples run and pass. In database 2, 27 examples run and pass. In database 3, 19 examples run and pass. In database 4, 23 examples run and pass. The other records describe things that cannot run in that environment (Turbo Pascal, Visual Basic, DOS, Windows APIs, consoles, hardware and products, which have no code at all).

For languages that are not installed in the build environment (Forth, Prolog, Lisp, Smalltalk, Erlang, Lua, Haskell, OCaml, C#, Scala, Rust, Swift, Zig, Kotlin, Dart, Elixir, Julia and others), the examples were checked only by reading, and this is noted in the development log.

## How to add a database

1. Create files in `data/seed` with the records. Pass the database id to `entry()`, for example `databaseId = 3`; a record without it goes to database 1.
2. Add the database to `seedDatabases` and its records to `seedRecords` (file `SeedCatalog.kt`).
3. A new install writes all seeded databases when the file is created. An install made before that does not get them by itself: raise the version in `AppDatabase` and add a `Migration` that inserts the new database. `MIGRATION_1_2`, `MIGRATION_2_3` and `MIGRATION_3_4` are the examples: each adds one database, and an install made at version 1 runs them in order.
