# Development log: Retro Databases

Solo development log for the Android app in this folder. I add an entry at each checkpoint (CP-NN), in order. The numbers are measured. The rest is my honest take on how it went, so some entries are grumpier than others.

## Conventions

- Each checkpoint records line counts for the tracked sources: Kotlin (`app/src/main/java`, `app/src/test`), XML (`app/src/main/res`, `AndroidManifest.xml`), and hand-written Gradle and project files.
- Counts come from `wc -l` on the working tree. Build output, the generated Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`) and this log are excluded from the totals. I count with `wc` because it's boring and I trust it.
- Deltas are measured against the previous checkpoint.
- Work before CP-01 was not measured, so the table starts there.

## Growth in lines of code

| Checkpoint | Date | Kotlin main | Kotlin test | XML | Gradle and project files | Total | Delta |
|---|---|---:|---:|---:|---:|---:|---|
| CP-01 | 2026-10-14 | 4,269 | 0 | 82 | 115 | 4,466 | first measurement |
| CP-02 | 2026-10-14 | 4,277 | 169 | 82 | 115 | 4,643 | +177 (tests +169, examples +8) |
| CP-03 | 2026-10-14 | 4,277 | 169 | 82 | 115 | 4,643 | 0 (no source changes; first build attempt failed) |
| CP-04 | 2026-10-14 | 4,277 | 169 | 82 | 118 | 4,646 | +3 (.gitignore rule for generated Kotlin session data) |
| CP-05 | 2026-10-14 | 6,236 | 169 | 82 | 118 | 6,605 | +1,959 (155 new records in five seed files) |
| CP-06 | 2026-10-14 | 6,257 | 169 | 82 | 118 | 6,626 | +21 (translation; long strings re-wrapped) |
| CP-07 | 2026-10-14 | 9,174 | 199 | 82 | 118 | 9,573 | +2,947 (database 2: 142 records in ten files, migration 1→2, tests +30) |
| CP-08 | 2026-10-14 | 11,798 | 226 | 82 | 118 | 12,224 | +2,651 (database 3: 141 records in eleven files, migration 2→3, tests +27) |
| CP-09 | 2026-10-14 | 14,224 | 249 | 82 | 118 | 14,673 | +2,449 (database 4: 109 records in eleven files, migration 3→4, tests +23) |

Non-blank lines: CP-01 4,078; CP-02 4,227.

## CP-01: catalogue model and content (not yet compiled)

Status: sources written, not built, examples not yet executed.

Mood: happy with the data model, nervous because nothing has been compiled yet.

### What changed

- The record kind is an enum with ten values: LANGUAGE, SYSTEM, DEVICE, TOOL, PRACTICE, GUIDE, EXAMPLE, PROJECT, FLEX, CROSS. Each record also has a section (a subgroup inside its kind) and a rare flag.
- Records gained an `instructions` field for step-by-step text. `details` now explains how a record works, and that's the part I enjoyed writing most.
- Seed data for database 1: 165 records. Languages 34, systems 23, devices 27, tools 18, practices 39, guides 5, examples 5, projects 4, flex 5, cross-technology 5. 55 records are flagged rare.
- Seed insert runs as SQL in `onCreate` and binds 12 columns, plus a literal for `favorite`.
- UI: kind filter chips with counts, a rare filter, section headers in the list, an instructions block on the detail screen, and a monospace code block with horizontal scroll.

### Size by area (Kotlin main, 4,269 lines)

- Seed data: 3,141 lines (73.6%). The ten catalogue files hold 3,077 of them.
- UI: 919 lines (21.5%).
- Data layer: 209 lines (4.9%).

### Notes

- Most of the code is seed data, which is a bit scary. I keep examples as raw strings so the same text can be pulled out and executed during verification.
- Writing 165 records before the first compile was a choice I wouldn't repeat. Some of it will break once the compiler sees it, and I already expect that.
- Examples in languages that aren't installed in the build sandbox can only be checked by reading them. They stay open until I can run them somewhere.
- Rare flags: 55 of 165 records. The flag shows as a badge and has its own filter.
- The database schema is still version 1. Nothing is installed yet, so no migration is needed.

## CP-02: example verification and unit tests

Status: examples executed, bugs fixed, unit tests written, Gradle wrapper generated.

Mood: humbled. I trusted my own examples right up until I ran them.

### Example verification

- I pulled every example out of the seed files and executed the 32 that this sandbox can run: gcc 13, mawk 1.3.4, dash, make 4.3, perl 5.38, bc, bison, and SQLite 3.45 through Python.
- 32 passed. 133 entries can't run here (Forth, Prolog, Lisp, Scheme, Smalltalk, BASIC, APL, Logo, Icon, Miranda, PostScript, Pascal, Modula-2, Ada, COBOL and others, plus text-only entries such as the emulator guide and the Forth stack trace). They stay on the open list.
- Running the examples turned up five bugs. Some of them I would have shipped without noticing:
  1. The sine-table Makefile listed `sine.h` first, so plain `make` built only the header and never produced `prog`. `prog` is now the first target. It looked completely fine when I read it.
  2. The fixed-point example declared variables at file scope with non-constant initialisers, which C rejects. The code is now inside `main` and prints 576.
  3. bc at `scale=50` printed `...937508` where the correct digits are `...937510`. I was sure the precision was enough, and it wasn't. The example now uses `scale=60`, which is exact to 60 decimals, and the entry was renamed to sixty digits.
  4. The normal-forms example created the table `orders` twice. The denormalised version is now `orders_flat`.
  5. The harness expected a filename prefix from grep. grep leaves the prefix out when only one file matches, so the harness was wrong, not the example. I fixed the check and felt a bit stupid about it.
- Four of the five bugs were in the example text. The Makefile and the duplicate table are exactly the kind of thing that only shows up when the code actually runs, so every executable example now runs before it's accepted.

### Unit tests

- `RecordFilterTest`: search, case-insensitive Cyrillic matching, kind filter, favourite and rare filters, and their combinations.
- `SeedDataTest`: database one exists, titles are unique, years are in range, every kind has records, projects carry instructions and code, rare records exist.
- `AppDatabaseSeedTest` (Robolectric): the first open seeds database one with every record, and the favourite toggle persists. This runs the real seed SQL and Room's schema validation, so it's the test I trust most.

### Notes

- The tests are written but not run yet. That's the next job, and it's going to be fun with this sandbox.
- The example runner lives outside the repo, with the other scratch files. It's not committed because it's a harness, not part of the app.

## CP-03: first build attempt (failed before compilation)

Date: 2026-10-14. Totals unchanged from CP-02: no source files changed since then.

Mood: annoyed at Maven Central, not at my code.

### What happened

- `./gradlew assembleDebug testDebugUnitTest` failed while Gradle configured the root project. It couldn't download plugin dependencies from `repo.maven.apache.org` and got HTTP 429 (Too Many Requests). Compilation never started, so the code still hasn't been compiled.
- The wrapper step hit the same limit once and succeeded on a second try. This time the limit held for the whole resolution.
- It's a strange kind of failure: the build dies before my code gets a chance to be wrong. Nothing I changed in the project could fix it, so I stopped looking at the code and started looking at the network.

### Decision

- Retrying unchanged would just hit the same limit. The next attempt points Gradle's Maven Central repository at `repo1.maven.org` through a per-user init script outside the repository. The project declares only `mavenCentral()`, so this change never reaches version control.
- I don't love a workaround I can't commit, but waiting for a rate limit to reset on its own wasn't going to happen on my schedule.

### Open items

- Build through the mirror, record the compile result, fix any compile errors, run the unit tests.
- Pack the text bundle after the build is green.

## CP-04: first successful build and unit tests

Status: debug APK assembles and all 14 unit tests pass.

Mood: relieved, then irritated again by a 200 MB download.

Non-blank lines at CP-04: 4,229 (Kotlin 3,911, tests 143, XML 74, Gradle and project files 101).

### What happened

- Dependency downloads were rate-limited (HTTP 429) by Maven Central and by repo1.maven.org in four build attempts. Gradle caches each artifact it downloads, so every retry moved a little further, and the fifth attempt finished the dependency phase. It felt like the build was crawling up a hill.
- Attempt 4 failed at compilation with two unresolved references in `RecordsScreen.kt`: a wrong import of `item` (a member of `LazyListScope`, so it needs no import at all) and a missing import of `RoundedCornerShape`. Both are fixed in `7e76f19`. Embarrassing, but the fix was two lines.
- Attempt 5 compiled the main sources and assembled the debug APK. The unit-test task then reported one failure: `AppDatabaseSeedTest.firstOpenSeedsDatabaseOneWithEveryRecord` couldn't download Robolectric's Android runtime (`android-all-instrumented`, 199.5 MB) because of the same rate limit. The second database test passed in that run. I was glad the failure was in a download and not in my code.
- A rerun after the runtime finished caching passed all tests. The test task executed in that run, and the result files were written at that time.
- For a while I was also confused by notifications from an earlier background build loop, which looked like the current build finishing. They weren't. I checked the logs and the timestamps before believing anything. Lesson: trust the log file, not the notification.

### Results

- `assembleDebug`: `app/build/outputs/apk/debug/app-debug.apk`, 10.1 MB.
- `testDebugUnitTest`: 14 of 14 passed. `SeedDataTest` 7, `RecordFilterTest` 5, `AppDatabaseSeedTest` 2. The database test runs the real seed SQL and Room's schema validation on Robolectric.

### Notes

- Not verified yet: the app on a device or emulator (there's no emulator in this sandbox), the UI behaviour, lint, and the release build. Green unit tests don't tell me how the screens actually feel.
- The sandbox builds use a per-user Gradle init script that points Maven Central requests at repo1.maven.org. It lives outside the repository and isn't committed, so the project files are unchanged.
- The Kotlin Gradle plugin writes `.kotlin/` session data during builds. It kept showing up as untracked files and the stop hook kept complaining. `.gitignore` now excludes it (`802d0f1`). The hook is quiet now, and so am I.

### Next

- Commit the packed source file `RetroDatabases_source.txt` together with this log. (Done with CP-05.)

## CP-05: coverage audit and 155 added records

Status: built, all 14 unit tests pass, debug APK assembles.

Mood: pleased with the build, a bit embarrassed by my own first draft.

Date: 2026-10-14. Totals: Kotlin main 6,236 (CP-04: 4,277), Kotlin test 169, XML 82, Gradle and project files 118, total 6,605. The +1,959 delta is exactly the six new Kotlin files: `Db1Catalog.kt` changed in place, so its line count didn't move.

### What changed

- Five seed files with new records: `Db1ExtraDevices.kt` (72), `Db1ExtraSystems.kt` (25), `Db1ExtraLanguages.kt` (22), `Db1ExtraTools.kt` (15) and `Db1ExtraPractices.kt` (21).
- `Db1Additions.kt` joins the five lists into `db1Additions`. `Db1Catalog.kt` adds that list to `db1Records`; the change is one line.
- Database 1 now has 320 records (165 before). Rare-flagged records rise from 55 to 181; 126 of the new records are rare.
- New sections include regional home computers, Eastern-bloc and Soviet machines, Lisp machines, processor families, Unix variants, microkernels, networks, formal methods, and BBS and demoscene culture.

### How I did the audit

- I compared the existing 165 records with reference lists of 1980s home computers, operating systems, processors, languages and tools, and with the standards and practices of the decade. Then I wrote the missing ones from memory. That was the fun part and also the risky part.
- I searched for facts on the machines I was least sure about: Yugoslav, Bulgarian, Czechoslovak, East German, Chinese and Soviet computers, and the Iskra-1030.
- Titles are unique across all 320 records. A grep over the seed files confirmed this before the build, and `SeedDataTest` enforces it.
- My own tally came to 155, and grep agreed. Small relief.

### Facts checked by web search

- Galeb: 6502, made by PEL Varaždin. Sources disagree on the year: 1981 in one infobox, 1984 in a catalogue of Yugoslav systems. The entry uses 1981.
- Orao: 6502 at 1 MHz, 1984. Standard school computer in Croatia and Vojvodina from 1985 to 1991.
- Pecom 64: CDP1802B processor, 32 KB RAM, CDP1869 and CDP1870 video chips, 1985.
- Tesla PMD 85: MHB8080A, a Czechoslovak copy of the Intel 8080, 2.048 MHz, produced from 1985 to 1989. One listing gives 1983 as the first year.
- Matra Alice: Motorola 6803, 6847 video, a copy of the TRS-80 MC-10, sold in France from 1983.
- Philips P2000T: Z80, Mullard SAA5050 teletext chip, built-in tape drive. Wikipedia gives March 1980 as the launch; a museum dates the T model to 1981.
- Robotron Z1013: 1985, U880 (an East German Z80 clone). Variants Z1013.16 (1987) and Z1013.64 (1988).
- Iskra-1030: IBM PC/XT-compatible, 8086 clone at 4.77 MHz, designed in Leningrad and built in Smolensk and Kursk. English Wikipedia gives 1989 as the release year.
- CEC-I: Apple II-compatible, 6502A, 64 KB. Sources disagree on the maker and date (1986 or November 1987).

### Corrections made during review

Going back over my own draft was humbling.

- Pravetz 8D: I had it as an Apple II clone. It's an Oric Atmos clone. I was wrong, and I'd written it with total confidence, which is the worst combination. Its year (1985) is an estimate; no source gave one.
- Iskra-1030: I had called it a school computer. The sources don't say that, so it's now described as used in institute computing labs.
- CEC-I: I had attributed it to Tsinghua University. The sources don't back that up, so I removed it.
- Parlog: I had it as part of the ICOT project. It was related work, not part of it.
- TI-99/4A: dated 1981 now, the year of the 4A. The 1979 date belongs to the TI-99/4.
- Common Lisp: described as the 1984 book that unified the dialects. The ANSI standard came later.
- RAD: dated to James Martin's 1982 book.
- Removed claims I couldn't back up: "hundreds of sprites" on the X68000, "basis of the Korean market" for the SPC-1000, "main place" for Usenet, "thousands of accounting systems" for Clipper, the wording that autoconf and sendmail grew out of m4, and the ITS "first text editor" wording.
- Author names corrected from memory, not searched: Franz Lisp, Hope, Modula-3, mutation testing, gawk and uucp.
- My first replacement script skipped one line: the Z1013 summary, because its prefix also matched the Radio-86RK entry. The guard did its job. I fixed that line by hand.

### Examples and tests

- New executable example: the C++ entry (a counter class). I extracted it from the Kotlin source, compiled it with `g++ -std=c++17 -Wall -Wextra`, and ran it. It prints `2`. It compiled on the first try, which I'll take. It's the only new entry with executable code; the other 154 new entries are reading-only.
- `./gradlew assembleDebug testDebugUnitTest` finished with BUILD SUCCESSFUL in 22 seconds. Results: AppDatabaseSeedTest 2 of 2, RecordFilterTest 5 of 5, SeedDataTest 7 of 7. Debug APK: 10.2 MB.

### Notes

- About 145 of the new entries were written from memory and not checked against a source in this checkpoint. That's most of the list, and it's the honest part of this checkpoint. Dates and names are the first thing to check when an error is reported.
- The rare flag now covers 59 of 72 new devices. Most of these machines were sold in one region or in small numbers. It's a judgement call and I might regret it, so it gets a review pass later.
- Next time I'll search first and write second. I did it the other way round this time, which meant fixing things instead of getting them right the first time.
- Not verified yet: running on a device or emulator, the new sections in the UI, lint, and the release build.
- `DEVLOG.txt` is a plain-text copy of this log, and the packed bundle includes it.

## CP-06: everything in English

Status: the app's code, resources, tests, seed data and README are all in English. The build passes and all 14 unit tests pass.

Mood: tedious, then satisfying when the build came back green on the first try.

Date: 2026-10-14. Totals: Kotlin main 6,257 (CP-05: 6,236), Kotlin test 169, XML 82, Gradle and project files 118, total 6,626. The +21 comes from long strings that I re-wrapped, because a Russian sentence and its English version did not fit the same number of lines.

### What changed

- No Cyrillic is left in the app: sources, comments, resources, tests, seed data, code examples and the README. A script checked the whole project folder. The only other matches in the same git repository are in unrelated projects, which I did not touch.
- UI labels and the app name are English: "Retro Databases", the kind labels (Languages, Systems, Devices, Tools, Practices, Guides, Examples, Projects, Flex, Cross-code) and all the interface strings.
- The 320 seed records are translated: titles, authors, sections, summaries, details, instructions, comments and string literals inside examples. Each section has one English name, so the grouping in the list works as before. There are 54 section names and no duplicate titles.
- The database name is "Database 1: 80s coding catalogue".
- The case-insensitive search test checks Latin letters now. It used to check Cyrillic case folding, which the English data no longer needs.
- The README is in English, and its counts are updated to 320 records and 181 rare. It still said 165 after CP-05, which was my mistake.

### How I checked it

- Each seed file was translated in chunks and rebuilt. A script compared every file with git HEAD: the same number of records, the same years, kinds and rare flags, and every changed line had Cyrillic before and none after. Where a long string was re-wrapped, the script reports the extra lines as expected.
- `assembleDebug` passes, and the 14 unit tests pass (AppDatabaseSeedTest 2, RecordFilterTest 5, SeedDataTest 7).
- The example checker finds examples by title. I mapped the old titles to the new ones using the record order, which did not change, and updated the one check that looked for a Russian section header and Russian output. 32 examples run and pass, 0 fail. The other 288 are still reading-only in this sandbox.
- The C++ example is not in the checker. I compiled it with g++ again and it still prints 2.

### Notes

- Some names are my own transliteration (Vector-06C, Radio-86RK, Iskra-1030, and the Soviet chip names). Check them against a source if they matter.
- Not verified yet: running on a device or emulator, the UI itself, lint, and the release build.

## CP-07: database 2, the 1990s

Status: database 2 is in the app: 142 records, a migration for installs made before it, and tests for both databases. The build passes, 15 of 15 unit tests pass, and 27 of the 142 new examples run and pass.

Mood: pleased with most of the content, then annoyed with myself. The example checker hung on a web server it had started and never stopped, and the next run failed for a reason that took me a while to find.

Date: 2026-10-14. Totals: Kotlin main 9,174 (CP-06: 6,257), Kotlin test 199 (169), XML 82, Gradle and project files 118, total 9,573. The +2,947 is mostly the ten new Kotlin files for database 2, which come to 2,884 lines together, plus the seed helper, the catalogue, the database class and the tests (+30).

### What changed

- A second database, "Database 2: 90s coding catalogue", with the same ten kinds. 142 records: languages 20, systems 20, devices 32, tools 24, practices 20, guides 5, examples 7, projects 4, flex 5, cross-code 5. 68 of them are rare.
- Ten files `Db2*.kt` hold the records. `Db2Catalog.kt` joins them into `db2Records`. `SeedCatalog.kt` lists both databases and combines all the records into `seedRecords`.
- `entry()` takes a `databaseId`, which defaults to 1, so database 1 is unchanged. I also added a `<DOLLAR>` marker next to `<TAB>`. Perl, PHP and shell examples are full of `$`, and inside a Kotlin raw string `$name` starts a template, so the marker keeps those examples readable. Instructions get both markers too. No instruction in database 1 contains either one.
- Database version 2. `MIGRATION_1_2` adds database 2 to an install made at version 1 and keeps its favourites and the records of database 1. The insert code is shared by `onCreate` and the migration, so the two cannot drift apart.
- Tests. `SeedDataTest` checks both databases: ids, titles unique inside each database, years in 1950–1990 for database 1 and 1990–1999 for database 2, every kind in both, and rare records in both. `AppDatabaseSeedTest` checks the record counts of both databases, and it runs the migration on a file where database 2 has been removed.

### Facts checked by web search

- Five entries were checked against sources: FreeBSD 2.0 (announced 22 November 1994); Windows CE 1.0 (released in November 1996, although one page says 1997 and looks wrong); Impulse Tracker (Jeffrey "Pulse" Lim, 1995, freeware with paid extras); NetBeans (started as the student project Xelfi in Prague in 1996, company founded in 1997, Sun bought it in 1999); Net Yaroze (Japan in June 1996, other countries in 1997, so the record uses 1996).
- Two candidates were dropped. Minix 2: the sources gave 1996 and 1997 for its first release. Scream Tracker 3: the search did not confirm its author.
- The other 137 records were written from memory, and I have not checked their dates one by one. The ones I would check first: BeOS R3, AmigaOS 3.5, FastTracker 2, Blender, CVS and the first release of Lua.

### How I checked it

- Unit tests: 15 of 15 pass (AppDatabaseSeedTest 3, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL.
- Examples: a script takes every database 2 example and instruction from the Kotlin source, applies the same markers, and runs whatever this sandbox can run. 27 pass: Java with javac and java, a JNI library built against the JDK headers, Perl (a CGI script with environment variables, a visit counter, a quine, and a small HTTP server checked with curl), PHP, Python, Ruby, JavaScript with Node, C++98 with g++, C with cc, awk, a SQL join in SQLite, a bytecode dump with javap, and a WAD lister that reads a file the script builds itself. The other 115 are reading-only: Turbo Pascal, Visual Basic, DOS, the Windows APIs, Erlang, Lua, Haskell, OCaml and the rest.
- Cyrillic: none in the code, resources or tests. The bundle's header lines were Russian too, because `pack_sources.sh` wrote them. I translated the header, so the bundle has no Cyrillic either.

### Problems

- The example checker hung. I had written one check that read the server's stderr before deciding whether the answer was right. The server never exits, so the read waited forever. I stopped the checker, but its Perl server kept running and held port 8791. The next run got "address already in use", and curl was answered by the old server, which had lost its folder, so I got a 404 and spent a while wondering why. The fixes: the check reads stderr only when it fails, each run picks a free port, and the old server is gone. Lesson: when I kill a parent process, its children keep running unless I kill them too.

### Notes

- 115 of 142 records are reading-only, so most of database 2 has not been executed. The 27 that run cover the languages and tools that exist in this sandbox.
- The rare flag covers 68 of 142 records. It's a judgement call again, and I'll review it against the database 1 flags.
- Not verified yet: the migration on a real device from a version 1 install (the unit test runs the migration on a test file, which is not the same), the UI with two databases in the list and in the filters, lint, and the release build.

## CP-08: database 3, the 2000s

Status: database 3 is in the app: 141 records, a migration for installs made at version 2, and tests for all three databases. The build passes and 16 of 16 unit tests pass. 19 of the 141 new examples run and pass; the other 122 are checked by reading or have no code at all.

Mood: pleased with how much of the decade fits into one list, from Windows XP to Cassandra. Annoyed with one example that printed nothing, because I forgot that `git shortlog` reads its input from standard input when no revision is given.

Date: 2026-10-14. Totals: Kotlin main 11,798 (CP-07: 9,174), Kotlin test 226 (199), XML 82, Gradle and project files 118, total 12,224. The +2,651 is mostly the eleven new Kotlin files for database 3 (2,606 lines together), plus the catalogue, the database class and the tests (+27).

### What changed

- A third database, "Database 3: 2000s coding catalogue", with the same ten kinds. 141 records: languages 20, systems 20, devices 31, tools 24, practices 20, guides 5, examples 7, projects 4, flex 5, cross-code 5. 56 of them are rare.
- Eleven files `Db3*.kt`: ten record files and `Db3Catalog.kt`, which joins them into `db3Records`. `SeedCatalog.kt` lists the third database and adds its records to `seedRecords`.
- Database version 3 with `MIGRATION_2_3`. An install at version 2 gets database 3 from it. An install at version 1 runs `MIGRATION_1_2` and then `MIGRATION_2_3`, because Room runs the migrations in order. The unit tests cover the second migration on a test file; the chain from version 1 has not been run on a device.
- The years of database 3 are 2000 to 2010, both included. Windows 2000 (February 2000) and the Eee PC (2007) are in; anything from 1999 is out.
- Tests cover three databases. `SeedDataTest` checks the years of database 3, and every kind and the rare flags in each database. `AppDatabaseSeedTest` checks the record counts of all three databases and has a new test that removes database 3 and runs the migration again. 16 unit tests in total.

### Facts checked by web search

- Nine entries were checked against sources: Knoppix (first release in September 2000); Gentoo Linux (version 1.0 on 31 March 2002); OpenBeOS, later Haiku (launched in August 2001, renamed in 2004); Pharo (forked from Squeak in March 2008, Pharo 1.0 in April 2010, so the record uses the fork year); Scala (first public release in January 2004; one source says 2003, and I followed the 2004 date from the language's own ten-year post); Clojure (public release in October 2007; one source gives 1.0 in May 2009); Xen (first public release in October 2003; the date of version 1.0 is disputed between 2003 and 2004, so the record uses 2003); Notepad++ (first public build on SourceForge in November 2003; the infoboxes say 24 November, and one page dates the start of work to September); CUDA (first public SDK in February 2007, version 1.0 in mid-2007, with the sources disagreeing on the day).
- A few machine-written encyclopaedia pages came up in the searches. They say they may contain errors, so I did not use them.
- The other 132 records were written from memory, and I have not checked their dates one by one. The ones I would check first are the Pentium 4, the Itanium, the Cell, the Earth Simulator, the OLPC XO-1 and the early Ubuntu and Linux 2.6 dates.

### How I checked it

- Unit tests: 16 of 16 pass (AppDatabaseSeedTest 4, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL.
- Examples: a script takes every database 3 example from the Kotlin source and runs what this sandbox can run. 19 pass: Java 5 (javac and java), Python 2.0, the Python 2.4 generator expression, Go (go run), an RSS parser, a SQL window function in SQLite, a PHP 5 exception class, a first Git commit, the Git object demo, a MapReduce word count, a podcast reader and a CamelCase wiki (both run on local files that the script writes), a Sudoku solver (its output is compared with the known solution), Ruby metaprogramming, one-line Git statistics, a 1 KB JavaScript demo, ctypes calling a C library, a C program that embeds Python, and a Git pre-commit hook written in Python, which the script installs and then tries to commit through.
- The other 122 are not executed. 95 of them are systems, devices, tools and practices, which have no example at all. The other 27 are languages, guides, examples, projects and cross-code entries that need a compiler or service this sandbox does not have, or that have no code.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- `git shortlog -sn` with no revision reads from standard input when that is not a terminal. My own run and the checker both printed nothing. The fix is `git shortlog -sn HEAD`, which prints the counts. The checker caught it on the first run, and the rest of database 3 passed the first time.

### Notes

- 122 of 141 records are not executed, which is most of the decade. Systems, devices, tools and practices can't run in a sandbox anyway, so the examples there would only be documentation.
- The rare flag covers 56 of 141 records. That is a judgement call again, and I will compare it with the flags of databases 1 and 2 before the next release.
- Not verified yet: the migration on a real device from version 1 and version 2 installs, the UI with three databases in the list and in the filters, lint, and the release build.

## CP-09: database 4, the 2010s

Status: database 4 is in the app: 109 records, a migration for installs made at version 3, and tests for all four databases. The build passes, 17 of 17 unit tests pass, and the Kotlin compiler gives no warnings. 23 of the 109 new examples run and pass; the other 86 are checked by reading or have no code at all.

Mood: pleased with how far the decade reaches, from Rust and Kubernetes to Figma and the M1 Mac. Annoyed with myself. My first draft had several dates wrong, and the searches caught them. One of my own examples printed the wrong output, because `rstrip(" \t")` does not remove the newline at the end of a line.

Date: 2026-10-14. Totals: Kotlin main 14,224 (CP-08: 11,798), Kotlin test 249 (226), XML 82, Gradle and project files 118, total 14,673. The +2,449 is mostly the eleven new Kotlin files for database 4, plus the catalogue, the database class and the tests (+23).

### What changed

- A fourth database, "Database 4: 2010s coding catalogue", with the same ten kinds. 109 records: languages 20, systems 16, devices 19, tools 14, practices 14, guides 5, examples 7, projects 4, flex 5, cross-code 5. 38 of them are rare.
- Eleven files `Db4*.kt`: ten record files and `Db4Catalog.kt`, which joins them into `db4Records`. `SeedCatalog.kt` lists the fourth database and adds its records to `seedRecords`.
- Database version 4 with `MIGRATION_3_4`. An install at version 3 gets database 4 from it. Installs at version 1 or 2 run the earlier migrations first, as before.
- The years run from 2010 to 2020, both included. The oldest records date from 2010, and the newest two from November 2020: the Apple M1 Mac and macOS Big Sur. Nothing else in the database is dated 2020, so I added those two after the first draft.
- Tests cover four databases. `SeedDataTest` checks the years of database 4, and `AppDatabaseSeedTest` checks its record count. It also has a new test that removes database 4 and runs `MIGRATION_3_4` on the file. 17 unit tests in total.

### Facts checked by web search

- TypeScript: the first public version, 0.8, came out in October 2012. Wikipedia's infobox gives 1 October 2012, and Microsoft's DevBlog has the 0.8.1 post from 15 November 2012. Version 1.0 shipped at Build in 2014. One slideshare gives 2016, which I treated as an outlier. The record uses 0.8 and 2012.
- Pony: Wikipedia's infobox gives 28 April 2015 for the first appearance. The results held no earlier date, so the record uses 2015. I had 2014 from memory.
- Idris: Wikipedia gives 2007 for the first appearance, with a University of St Andrews index dated 12 December 2007. One low-quality site says 2009. Version 1.0 came out around April 2017 (InfoQ Japan). 2007 is outside the decade, so the record is Idris 1.0 from 2017.
- Elixir: public debut on 25 May 2012 (the v0.5.0 post and a message to the Erlang mailing list). Work started in early 2011, and v0.3.0 existed by April 2011. I could not confirm a v0.1, so the record does not use one.
- Solidity: version 0.1.0 on 7 August 2015 (iq.wiki). A Wikipedia mirror gives August 2014, which looks like the date of the proposal. The record uses 2015.
- Scratch 2.0: relaunch on 9 May 2013. The Scratch forum, EdSurge and i-programmer agree.
- Phoenix: version 1.0 in 2015 (InfoQ). The first commits are from May 2014 (Packt), and one podcast says the project started in 2011. The record mentions only the 1.0 year.
- systemd: the release dates conflict: 30 March 2010 (infobox), 18 May 2010 (an industry article, for version 1) and 7 July 2010 (a CentOS changelog, "First upstream release"). The record uses 2010 and says the sources differ.
- Fuchsia: the code appeared in a public repository in August 2016 with no announcement. The first product to run it was the Nest Hub, in 2021. Wikipedia gives 25 May 2021 as the initial release.
- Wayland 1.0 with Weston: 22 October 2012 (Phoronix and LWN).
- WebAssembly: a demo in 2015 ran a game in several browsers. Browsers agreed the design in late February 2017 (a mailing-list message from Mozilla dated 28 February), and the first versions shipped in March 2017. BleepingComputer reports full support by October 2017.
- Vagrant: version 0.1.0 on 7 or 8 March 2010 (LWN, Open Hub, Wikipedia). Version 1.0 in March 2012. HashiCorp was founded in November 2012.
- Ansible: most sources give 20 February 2012 for the first release. One documentation pull request gives 9 March 2012 for version 0.0.1. Red Hat bought AnsibleWorks in October 2015.
- Let's Encrypt: the public beta opened on 3 December 2015 (letsencrypt.org, Fedora Magazine). One repost says 2 December, which I ignored.
- Arduino Uno: unveiled at Maker Faire in September 2010. One book says 2011, which I believe is a typo.
- Oculus Rift DK1: shipping began in late March 2013. The sources give 28 or 29 March.
- Leap Motion: public launch on 22 July 2013. Two sources say the first units went out on 19 July.
- ESP8266: the ESP-01 module from Ai-Thinker drew hobbyists in August 2014, and the Espressif SDK followed in October 2014. I did not find a release date for the chip itself.
- AR.Drone: shown at CES in January 2010 and on sale later that year at 299 dollars (PCWorld's E3 report).
- Firecracker: unveiled at re:Invent in late November 2018 (eWeek dates it to 26 November) and released under Apache 2.0. A third-party page gives a star count that looked unreliable, so I did not use it.
- Terraform: first release on 28 July 2014 (infobox), with providers for AWS and DigitalOcean (HashiCorp's own keynote).
- Redux: first public release on 2 June 2015 (infobox and a GitHub topic page). A tutorial site dates the announcement to May 2015.
- Figma: invite-only preview from 3 December 2015, first public release on 27 September 2016 (infobox). Product Hunt listed it on 28 September, so the record says "late September 2016".
- Git LFS: public in April 2015 (GitHub's blog, 8 April 2015), with version 1.0 for all GitHub.com repositories in October 2015. A GitLab post dates the earlier private work to September 2013.
- Contributor Covenant: started in 2014 by Coraline Ada Ehmke, according to several sources. I did not find a date for version 1.0, so the record gives only 2014. OpenAFS adopted it in September 2015.
- Progressive web apps: the name is credited to Frances Berriman and Alex Russell in 2015. Wikipedia dates the wider rollout to 2016.
- Hacktoberfest: first-year participants were 676 in one source and 768 in DigitalOcean's recap. The record says the figures disagree.
- Checked earlier in this work and kept as recorded: Hexagony (2015), Redox OS (April 2015), Tizen (2012, with the sources a month apart), Firefox OS (2013), Zig (2016, with one article saying 2015), Semantic Versioning 2.0 (2013, with one arXiv snippet saying 2011) and Crystal (June 2014).
- Not searched: the Apple M1 Mac and macOS Big Sur (both November 2020), and most of the other records, which are from memory. The ones I would check first: Chrome OS, Windows 8, Kubernetes 1.0, AWS Lambda, Ouya, the Jenkins rename, the Git 2.5 release of `git worktree`, and the SQLite 3.9 release of FTS5.

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 5, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL.
- Examples: a script takes every database 4 example from the Kotlin source, applies the same markers and runs what this sandbox can run. 23 pass: C++11 with g++, Java 8 and the `var` keyword with javac and java, Python 3.6, async and await, dataclasses, the walrus loop, a sorted dictionary, a JWT signed with HMAC-SHA256 (the script checks that the signature verifies), Node.js (the ES2015 counter, the promise order demo and one Game of Life generation), PHP 7 types, std::optional with g++ and C++17, a worker pool and a cgo program with `go run`, SQLite full-text search, git worktree, git flow and a Git clean filter run in a real repository, a static site generator and a task list (both run on files the script writes), and a Go file hasher, whose output is compared with the SHA-256 of "hello".
- The other 86 are not run here: languages with no compiler in this sandbox (Rust, Swift, Zig, Pony, Crystal, TypeScript, Kotlin, Dart, Hack, Elm, Elixir, Julia, Idris, Solidity, Scratch and Hexagony), devices and systems that have no code, tools that need an account, and the guides and examples that need Docker, OpenSSL or a browser.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- My first draft had dates wrong, all from memory. Pony was 2014 (the searches give 2015). Idris was 2011, but its first appearance is 2007, so the record is now Idris 1.0 from 2017. TypeScript's original title said 1.0, and the first public version was 0.8. The Phoenix line put its first release in 2014, and version 1.0 came in 2015. The Python 3.6 entry said Python 3 had replaced Python 2 in new projects, which the sources do not support, so I reworded it.
- My Git clean filter example had a bug. `line.rstrip(" \t")` does not remove the newline at the end of a line, so the stored file kept its trailing spaces and got an extra newline. The verifier compares the exact output and caught it on the first run. The fix is `rstrip(" \t\n")`. The filter itself did run, so the relative path to the script in the repository config was right.
- The two 2020 records came after the first draft, so their dates are from memory. I have not searched for them.

### Notes

- 38 of 109 records are rare. That is a judgement call again, and I'll compare it with the other databases before the next release.
- Not verified yet: running on a device or emulator, the migration chain from version 1, 2 and 3 installs on a real device, the UI with four databases in the list and in the filters, lint, and the release build.
- Line counts use the same method as before: tracked and untracked files from `git ls-files`, with the README, this log and the bundle left out.
