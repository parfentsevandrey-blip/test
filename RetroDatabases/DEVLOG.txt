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
| CP-10 | 2026-10-15 | 16,206 | 272 | 82 | 118 | 16,678 | +2,005 (database 5: 99 records in eleven files, migration 4→5, tests +23) |
| CP-11 | 2026-10-15 | 17,552 | 295 | 82 | 118 | 18,047 | +1,369 (database 6: 60 records in eleven files, migration 5→6, tests +23) |
| CP-12 | 2026-10-15 | 18,903 | 320 | 82 | 118 | 19,423 | +1,376 (database 7: 49 records in eleven files, migration 6→7, tests +25) |
| CP-13 | 2026-10-15 | 20,288 | 343 | 82 | 118 | 20,831 | +1,408 (database 8: 50 records in eleven files, migration 7→8, tests +23) |

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

## CP-10: database 5, 2020 to 2026

Status: database 5 is in the app: 99 records, a migration for installs made at version 4, and tests for all five databases. The build passes, 18 of 18 unit tests pass, and the Kotlin compiler gives no warnings. 21 of the 99 new examples run and pass; the other 78 are checked by reading or have no code at all.

Mood: pleased that the catalogue now reaches the current month, with Go 1.26, TypeScript 7 and macOS 27 in it. Less pleased with how much of this period I could only check by search. Two dates from memory were wrong: the Raspberry Pi 500 launched at the end of 2024, not in November, and Playwright's 1.0 date is disputed, so the record gives only the year.

Date: 2026-10-15. Totals: Kotlin main 16,206 (CP-09: 14,224), Kotlin test 272 (249), XML 82, Gradle and project files 118, total 16,678. The +2,005 is mostly the eleven new Kotlin files for database 5, plus the catalogue, the database class and the tests (+23).

### What changed

- A fifth database, "Database 5: 2020s coding catalogue", with the same ten kinds. 99 records: languages 20, systems 15, devices 13, tools 14, practices 11, guides 5, examples 7, projects 4, flex 5, cross-code 5. 30 of them are rare.
- Eleven files `Db5*.kt`: ten record files and `Db5Catalog.kt`, which joins them into `db5Records`. `SeedCatalog.kt` lists the fifth database, with the colour #7986CB, and adds its records to `seedRecords`.
- Database version 5 with `MIGRATION_4_5`. An install at version 4 gets database 5 from it, and older installs run the earlier migrations first, as before.
- The years run from 2020 to 2026, both included. Database 4 already has two records from 2020 (the Apple M1 Mac and macOS Big Sur). I left them there, because the range is inclusive on both sides, as the 2010 overlap between databases 3 and 4 already was.
- The newest records are from 2026: Linux 7.0 in April, Go 1.26 in February, TypeScript 7 in July, and macOS 27, announced in June.
- Tests cover five databases. `SeedDataTest` checks that database 5 has its own records and that they fall between 2020 and 2026. `AppDatabaseSeedTest` checks its record count, and it has a new test that removes database 5 and runs `MIGRATION_4_5`. 18 unit tests in total.

### Facts checked by web search

- Python 3.14: generally available on 7 October 2025. The free-threaded build is officially supported but still opt-in (PEP 779). Template strings (PEP 750) and deferred annotations are in the same release.
- Java 25 LTS: generally available on 16 September 2025, with 18 JEPs. Compact source files, flexible constructor bodies and scoped values are final. Java 26 came out on 17 March 2026 as a short-term release; I did not include it.
- Go 1.25: released on 12 August 2025 with no new syntax. Go 1.26: released on 10 February 2026, with the expression form of new and self-referential generic types. The sandbox has Go 1.24, so the Go 1.26 record has no example.
- .NET 10 and C# 14: generally available on 11 November 2025 (several write-ups agree). Microsoft's announcement gives support until 10 November 2028.
- TypeScript: Microsoft announced the Go port in March 2025. TypeScript 6.0 was the last JavaScript-based release, and 7.0 reached general availability on 8 July 2026 (secondary sources).
- Rust 1.85.0 shipped on 20 February 2025 and made the 2024 edition stable.
- Kotlin 2.0: the documentation dates the release to 21 May 2024, while InfoWorld reports the announcement on 23 May. The record uses 21 May.
- Swift 6: general availability is confirmed, but none of the results gave the date. The record says September 2024 from memory.
- Dart 3: announced at I/O on 10 May 2023.
- Gleam 1.0: 4 March 2024 (Wikipedia; InfoQ also dates it to March 2024).
- Mojo: publicly available in early May 2023 (sources give 2 or 3 May), with a Linux build in September 2023. One source claims a 1.0 release in 2026, which I could not confirm, so the record does not mention it.
- Nim 2.0: released in early August 2023 (the release article is dated 1 August, and DevClass covered it on 3 August).
- Typst: became open source in March 2023 under the Apache 2.0 licence (one wiki source). LWN calls that month an early beta.
- HarmonyOS NEXT: a developer preview in January 2024 and a developer beta around June 2024. The results did not show whether a stable version shipped, so the record stops at the beta.
- WASI 0.2: the vote is dated 24 January 2024 on the wasi.dev changelog and 25 January by the Bytecode Alliance. One interfaces page says February, so the record says January 2024.
- Valkey: appeared on 27 to 28 March 2024 as a fork of Redis 7.2.4, under the BSD licence, with the Linux Foundation as steward.
- OpenTofu: HashiCorp moved Terraform to the Business Source License on 10 August 2023. The fork moved to the Linux Foundation in September 2023, and version 1.6.0 came out on 10 January 2024.
- Model Context Protocol: published by Anthropic on 25 November 2024. In December 2025 the project moved to the Agentic AI Foundation under the Linux Foundation.
- Windows 10: support ended on 14 October 2025. Consumers could join an extended security updates programme for one more year.
- macOS: Tahoe (macOS 26) was announced at WWDC in June 2025 and skipped 16. macOS 27 Golden Gate was announced at WWDC on 8 June 2026, runs only on Apple silicon, and installs from 14 September 2026.
- Linux 7.0: released on 12 April 2026 after seven release candidates. The major number changed for numbering reasons.
- XZ Utils: disclosed on 29 March 2024 (CVE-2024-3094, CVSS 10.0, versions 5.6.0 and 5.6.1). Wikipedia's infobox places the discovery on or before 27 March, while the body and most write-ups say 29 March. The record uses 29 March.
- Git 2.28: the init.defaultBranch setting, announced in a blog post on 27 July 2020. GitHub switched new repositories to main from 1 October 2020, according to a FAQ; GitHub's own post did not turn up.
- Raspberry Pi Pico 2: unveiled on 8 August 2024 at five dollars. The RP2350 can run Arm Cortex-M33 or RISC-V cores.
- Raspberry Pi 500: launched in December 2024 with a 15.6-inch monitor (reports dated 9 and 10 December). My memory said November, so the record says the end of 2024.
- Copilot+ PCs: announced on 20 May 2024 and on sale from 18 June 2024, with Snapdragon X chips and a 45 TOPS NPU (a vendor claim).
- Framework Laptop 13: first orders shipped in July 2021 (Wikipedia and Framework's blog).
- Analogue Pocket: shipping began in mid-December 2021, after delays announced in May and October.
- Raspberry Pi Zero 2 W: announced on 28 October 2021 at fifteen dollars, with a quad-core Cortex-A53 at 1 GHz. The five-fold speed-up is a vendor figure.
- Playdate: pushed back into 2022 in November 2021 after a battery defect. One result gives 18 April 2022 for the first shipments, and I did not use the day. More than 70,000 units had shipped by February 2024.
- Matter 1.0: published in early October 2022 (trade press on 5 and 6 October).
- WebGPU: switched on by default in Chrome 113 in 2023, first on ChromeOS, macOS and Windows. The results gave no day, so the record says 2023.
- Playwright: the 1.0 date is disputed. One article says May 2020, and others give July 2020 or January 2020. The record gives only the year.
- Backstage: public launch on 16 March 2020 (Spotify's anniversary post), and a CNCF Sandbox project in September 2020.
- Cursor: Anysphere was founded in 2022 by four MIT classmates, and Cursor shipped in 2023 as a fork of Visual Studio Code.
- PyPI trusted publishing: introduced in April 2023 (the launch post is dated 20 April). GitHub Actions was the only provider until April 2024.
- Bun 1.0: released on 8 September 2023. One result quotes a Wikipedia snapshot describing a company acquisition in December 2025. I found no second source, so the record does not mention it.
- uv: first public release on 15 or 16 February 2024 (InfoWorld dates its launch story to 16 February).
- Deno 2: released on 10 October 2024.
- Codex CLI: released on 16 April 2025 as an open-source tool that runs locally.
- Gemini CLI: public preview on 25 June 2025, under the Apache 2.0 licence, using Gemini 2.5 Pro.
- Claude Code: research preview in February 2025 (the MIT agent index gives 24 February), and general availability in May 2025 (Wikipedia).
- Copilot coding agent: unveiled at Microsoft Build on 19 May 2025.
- Vibe coding: the term came from a post by Andrej Karpathy on 2 February 2025.
- Docker Desktop: from 31 August 2021, companies with 250 or more employees, or more than 10 million dollars in revenue, needed a paid plan. The deadline to move was 31 January 2022.
- Dates I did not search for, from memory: the 2020 to 2023 language releases (C++20, PHP 8.0, Ruby 3.0, Swift 5.5, Python 3.10, Java 17 and 21, Go generics), Windows 11, Android 12, Linux 6.1, Kubernetes 1.24, visionOS, ChatGPT, GitHub Copilot, NIST SP 800-207, Executive Order 14028, Log4Shell, passkeys, WCAG 2.2, the RFCs behind HTTP/3, Git 2.34's SSH signing, and the hardware dates for Quest 2 and 3, Steam Deck, Pi 5 and Vision Pro.

### How I checked it

- Unit tests: 18 of 18 pass (AppDatabaseSeedTest 6, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the compiler gave no warnings.
- Examples: a script takes every database 5 example from the Kotlin source and runs what this sandbox can run. 21 pass: C++20 with g++ (a ranges pipeline), PHP 8.0 (match and named arguments), Ruby 3.0 (endless methods), Rust 2024 with rustc (let chains), a toy language model that samples words from a small vocabulary (the script checks that every word comes from it), match statements, nested f-string quotes on Python 3.13, records and sealed classes with javac and java, virtual threads on Java 21, top-level await with Node.js, let-else with rustc, strict SQLite tables, a password generator (the script checks the length and the alphabet), itertools.batched, RETURNING in SQLite, logical assignment, range over integers in Go, a Git default branch named main, a TypeScript file run with Bun, a Go c-shared library called from Python, and a C library called from Bun through bun:ffi.
- The other 78 are not run here. The sandbox has no Swift, Kotlin, Dart or Deno, and its javac is version 21, so the Java 25 compact source file and the Python 3.14 template string are only read. The Playwright, Backstage, GitHub Actions, WIT, ArkTS and visionOS snippets need their frameworks, and the AI tools need accounts.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- Two dates from memory were wrong: the Raspberry Pi 500 launched in December 2024, and Playwright's 1.0 date is disputed. The record gives only the year for Playwright.
- Several results were vendor pages or third-party blogs. Where sources split on a date, I used the most consistent one and wrote the difference into the record or this log.
- I only noticed this session that rustc is in the sandbox. That means the database 4 Rust example can now be run, but I have not done that yet. It is the first thing I will check at the next checkpoint.

### Notes

- 30 of 99 records are rare. That is a judgement call again, and I'll compare it with the other databases before the next release.
- The 2020 overlap with database 4 is deliberate, because the ranges are inclusive, as they were for 2010.
- Not verified yet: running on a device or emulator, the migration from version 4 installs on a real device, the UI with five databases in the list and in the filters, lint, and the release build.

## CP-11: database 6, 1950 to 1960

Status: database 6 is in the app: 60 records, a migration for installs made at version 5, and tests for all six databases. The build passes, 19 of 19 unit tests pass, and the Kotlin compiler gives no warnings. 17 of the 60 new examples run and pass; the other 43 are only read, or have no code at all. The Rust example from database 4, which I promised to check at this checkpoint, also compiles and runs now.

Mood: the 1950s were the hardest decade to check so far. Many dates disagree between sources, sometimes by a year, and several hits just repeat the same Wikipedia text. I dropped the Friden Flexowriter and the FORTRAN Monitor System because I could not find a date I trust for either. Database 1 already has FORTRAN, COBOL, ALGOL 60, Lisp, garbage collection and APL, so those stayed out, and a few of the decade's best-known names were not available as a result.

Date: 2026-10-15. Totals: Kotlin main 17,552 (CP-10: 16,206), Kotlin test 295 (272), XML 82, Gradle and project files 118, total 18,047. The +1,369 is the eleven new Kotlin files for database 6, plus the catalogue and the database class (main +1,346), and the tests (+23). I checked the counting method first: run on the CP-10 commit, it gives exactly the CP-10 numbers.

### What changed

- A sixth database, "Database 6: 1950s coding catalogue", with the same ten kinds. 60 records: languages 9, systems 5, devices 17, tools 3, practices 7, guides 6, examples 4, projects 4, flex 3, cross-code 2. 29 of them are rare.
- Eleven files `Db6*.kt`: ten record files and `Db6Catalog.kt`, which joins them into `db6Records`. `SeedCatalog.kt` lists the sixth database, with the colour #FFD54F, and adds its records to `seedRecords`.
- Database version 6 with `MIGRATION_5_6`. An install at version 5 gets database 6 from it, and older installs run the earlier migrations first, as before.
- The years run from 1950 to 1960, both included. The database 1 records from that period stay where they are, so I did not repeat them.
- Tests cover six databases. `SeedDataTest` checks the ids of database 6 and that its records fall between 1950 and 1960. `AppDatabaseSeedTest` checks its record count, and it has a new test that removes database 6 and runs `MIGRATION_5_6`. 19 unit tests in total.
- The database 4 Rust example, which was only read until now, was run by hand: `rustc --edition 2021` compiles it and it prints `first: Ann`. That makes 24 runnable examples in database 4, not 23. I did not try the 2024 edition for it.

### Facts checked by web search

- Speedcoding: Backus and IBM, 1953, for the IBM 701. About 310 words of storage, ten to twenty times slower than machine code, described in a 1954 article in the JACM.
- Glennie's Autocode: the 1952 system for the Manchester Mark 1, with lecture notes dated December 1952. Sources disagree on whether it was a compiler or only a symbolic assembler.
- Mark 1 Autocode: one source gives March 1954 for the release, another gives 1954 for the planning and 1955 for the development. The record uses 1954.
- FLOW-MATIC: 1955 in the Wikipedia infobox, while hopl.info dates the B-0 compiler to 1956 and the design to 1957. COBOL took several of its features.
- IPL: the Logic Theorist of 1956. Start dates range from 1954 to 1956, and the JOHNNIAC proofs began in late 1956 and early 1957.
- IT: begun at Purdue on a Datatron 205, moved to the IBM 650 at Carnegie Tech. Perlis's essay puts its completion after Thanksgiving 1956, the dictionaries say 1957.
- COMIT: work ran from 1957 to 1965. Yngve's paper is dated 1957 in one source and 1958 in another, so the record gives the language's year.
- ALGOL 58: the Zurich meeting of late May and early June 1958. The name ALGOL took about a year to become official.
- JOVIAL: work began in 1959 at the System Development Corporation under Jules Schwartz. The meaning of the name is disputed.
- SHARE: delegates of the 704 installations met at RAND in August 1955, and the first manual came out in 1956. SOS for the 709 is dated 1959. IBM stopped supporting it in 1962.
- SAP: credited to Roy Nutt of United Aircraft. One source gives 1953 for the design, and SHARE's distribution is dated 1956. A 704 needed at least 4K words of 36-bit core to run it.
- SOAP: 1955 in HOPL. Most references name Stan Poley, but the 1955 IBM bulletin is credited to other people in some bibliographies. Knuth's SOAP III (1958) and SuperSOAP (1959); SOAP III became CASE SOAP III after IBM objected to the name.
- IBSYS: 1960 only in reference works. I found no primary IBM source for the date, and the record says so.
- SAGE: the design began in October 1952 as Whirlwind II. The first centre became operational on 1 July 1958, and the full system was complete in 1963. One popular page gives 1957, and it contradicts itself on the same page.
- SABRE: most sources give 1960 for its first working version.
- UNIVAC I: the Census Bureau accepted it on 31 March 1951 and the dedication was on 14 June 1951. Sources disagree on when the hardware physically arrived. The memory held 1,000 words of twelve characters, in mercury delay lines.
- Ferranti Mark 1: the first machine went to Manchester University in February 1951. BINAC and the Z4 were earlier commercial digital machines, each with serious drawbacks.
- LEO I: a successful trial on 5 September 1951, and the bakery calculations were handed over at the end of November. Another source gives 28 to 30 November for the first complete program.
- SWAC: Robinson's program found M521 and M607 on 30 January 1952. The Lucas–Lehmer test goes back to Lehmer's design.
- ILLIAC I: dated to 1952. The Illiac Suite is dated 1957 by most accounts. Some sources give 9 August 1956 for the first performance, by students.
- MANIAC I: built at Los Alamos in 1952 on the IAS design. The Metropolis paper is from 1953, from my memory: the search did not return the paper itself.
- IBM 701: announced in 1952 as the Defense Calculator. The 704 replaced it in 1954.
- IBM 704: introduced in 1954, with April or May given as the announcement month. 36-bit words, magnetic core memory, three index registers.
- IBM 650: first use in 1954 according to most references. SOAP and IT were both written for it.
- RAMAC 305: unveiled on 14 September 1956. The 350 disk unit held five million characters on fifty 24-inch platters: 5 MB at eight bits per character, about 3.75 MB at six data bits.
- TX-0: the MIT Museum and a 1974 history date its design and construction to 1956. Its memory held 65,536 words of 18 bits. The 1958 memory swap left it with a 4,096-word core.
- Setun: work started in 1956, a working machine existed by late 1958, and about fifty machines came out of a plant in Kazan from 1959. Sources give 1958 or 1959 for the first build.
- The first integrated circuit: Kilby's demonstration on 12 September 1958, on germanium. Noyce's silicon version came in 1959.
- IBM 1401: unveiled on 5 October 1959. One source gives 1960 for the first shipments, and estimates of the number built run from about 10,000 to about 15,000.
- IBM 7090: the transistor version of the 709. SOS was adapted for it.
- OXO: A. S. Douglas built it on the EDSAC in 1952. Whether it counts as the first video game depends on the definition.
- Lehmer's multiplicative generator: his 1951 suggestion, checked through secondary sources only. The constants 16807 and 2 to the power 31 minus 1 are the 1988 'minimal standard' choice, not Lehmer's.
- Gray code: the patent was granted in 1953. The patent number is from my memory.
- Not searched, from memory: the Lucas (1878) and Lehmer (1930) dates for their test, and the dates of the A-0 compiler (1951 to 1952). The Wilkes, Wheeler and Gill book (1951) was confirmed by search.

### How I checked it

- Unit tests: 19 of 19 pass (AppDatabaseSeedTest 7, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the build log has no compiler warnings.
- Examples: a script takes every database 6 example from the Kotlin source and runs what this sandbox can run. 17 pass: Newton's square roots, the Monte Carlo estimate of pi (within 0.02 of pi), 36-bit addition, sorting cards by columns, the toy machine's instruction cycle, the octal dump, the drum timing guide, the IPL free list, the Metropolis sampler (the mean energy must fall between 0.45 and 0.55), the drum placement planner, noughts and crosses (the last line must be a draw), the two-pass assembler, the Lucas–Lehmer test, the Gray code, the multiplicative generator, a NASM routine called from C, and a C Monte Carlo loop called from Python through ctypes.
- The other 43 are not run here. The sandbox has no Fortran, COBOL, Lisp, Algol or Pascal compiler and no Speedcoding interpreter, so those records and the machine descriptions are read only. The checks compare exact output, so a wrong expected line fails the check. The random-number examples check a range instead.
- Database 4 Rust example: extracted by a script, compiled with `rustc --edition 2021` and run.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- Several dates are disputed between sources: Mark 1 Autocode, FLOW-MATIC, IPL, IT, COMIT, SOAP, SAP, UNIVAC I, LEO I, SAGE, ILLIAC I and the IBM 704 announcement. Each record uses one year and names the dispute in its details.
- IBSYS has its 1960 date only from reference works, and the record says so.
- I dropped two candidates because I could not find a date I trust: the Friden Flexowriter, which has no 1950 source, and the FORTRAN Monitor System, which has no date at all.
- Speedcoding has no example. I did not want to invent a syntax for it, so the record says what the interpreter did instead.
- The SOAP record and the drum placement project both rest on the same 1955 IBM 650 material, so they are not two independent sources.

### Notes

- 29 of 60 records are rare, a higher share than database 5 (30 of 99). The 1950s have the fewest sources I could check, and most of its items are little-known.
- Database 6 starts in 1950, which database 1 also covers. The overlap is deliberate, because the ranges are inclusive, as before.
- Not verified yet: running on a device or emulator, the migration from version 5 on a real device, the UI with six databases in the list and in the filters, lint, and the release build.

## CP-12: database 7, 1960 to 1970

Status: database 7 is in the app: 49 records, a migration for installs made at version 6, and tests for all seven databases. The build passes, 20 of 20 unit tests pass, and the Kotlin compiler gives no warnings. 19 of the 49 new records have examples that run and pass; the other 30 are only read, or have no code at all.

Mood: the 1960s were easier to check than the 1950s, because more of the material survives in print. The trouble was the firsts. First time-sharing system, first personal computer, first supercomputer, first operating system written in a high-level language, first use of semaphores: every one of them is contested, and the sources disagree about which machine or paper counts. I wrote the disagreements into the records instead of picking a winner. I also cut two things. Dartmouth's time-sharing system sat too close to BASIC in database 1, and I could not confirm a date for the Apollo Guidance Computer hardware, so only its software is in.

Date: 2026-10-15. Totals: Kotlin main 18,903 (CP-11: 17,552), Kotlin test 320 (295), XML 82, Gradle and project files 118, total 19,423. The +1,376 is the eleven new Kotlin files for database 7, plus the catalogue and the database class (main +1,351), and the tests (+25).

### What changed

- A seventh database, "Database 7: 1960s coding catalogue", with the same ten kinds. 49 records: languages 7, systems 5, devices 10, tools 3, practices 4, guides 4, examples 6, projects 4, flex 4, cross-code 2. 26 of them are rare.
- Eleven files `Db7*.kt`: ten record files and `Db7Catalog.kt`, which joins them into `db7Records`. `SeedCatalog.kt` lists the seventh database, with the colour #F06292, and adds its records to `seedRecords`.
- Database version 7 with `MIGRATION_6_7`. An install at version 6 gets database 7 from it, and older installs run the earlier migrations first.
- The years run from 1960 to 1970, both included. Database 6 has IBSYS and SABRE from 1960, so that year is shared by the two databases, as 2010 and 2020 were shared before.
- Database 1 already has many records from these years (BASIC, PL/I, APL, ALGOL 68, Simula 67, Logo, BCPL, B, Multics, OS/360, System/360, PDP-11, Pascal, Forth and others). I left them where they are and did not repeat any of them.
- Tests cover seven databases. `SeedDataTest` checks the ids of database 7 and that its records fall between 1960 and 1970. `AppDatabaseSeedTest` checks its record count, and it has a new test that removes database 7 and runs `MIGRATION_6_7`. 20 unit tests in total.

### Facts checked by web search

- CTSS: a prototype in November 1961 on a modified IBM 709 with four terminals, the 7090 from the fall of 1962, service to Computation Center users from the summer of 1963, and the 7094 'red machine' until July 1973.
- Atlas: the first machine went into service in 1962. The Atlas Supervisor is credited as the earliest system to adopt paging, but that depends on how paging is defined. An IEEE Milestone, dedicated in Manchester in 2022, calls virtual memory the chief innovation.
- THE: Dijkstra's group built it in 1965 and 1966, and the paper appeared in Communications of the ACM in 1968. Standard references credit it with one of the first uses of semaphores. I have not checked that claim against the paper.
- ARPANET: the first message went from UCLA to SRI at about 10:30 p.m. on 29 October 1969. The system crashed after 'lo', and the whole word arrived about an hour later. By 5 December 1969 the network had four sites.
- RFC 1: dated 7 April 1969, titled 'Host Software', written by Steve Crocker. Jon Postel took over as editor in early 1970.
- Software engineering: the NATO conference at Garmisch in October 1968. Bertrand Meyer argues that the phrase was in use before it.
- SNOBOL: Farber, Griswold and Polonsky at Bell Labs. The versions ran from 1962 to 1967, and SNOBOL4 was finished on the IBM 360 in 1967. Sources disagree on when SNOBOL4 work started.
- JOSS: designed by Cliff Shaw between about 1960 and 1963, with a beta run on the JOHNNIAC in May 1963. Some reference works give 1966.
- MUMPS: built in 1966 and 1967 at Massachusetts General Hospital, first on a PDP-7. Meditech was founded in 1969 with Pappalardo's help, and the standards date from 1976 and 1977.
- TECO: Dan Murphy named it in 1962, and the work ran into 1963. The first Emacs was written as TECO macros. One Unix history post gives 1964.
- RUNOFF: Jerome Saltzer wrote it in 1964 for CTSS, in MAD and FAP, with TYPSET alongside. Accounts disagree on how it reached Unix.
- LINC: the first unit was finished in March 1962. Wesley Clark had the concept and Charles Molnar did the engineering. The project moved to MIT in 1963 and to Washington University in 1964, and DEC sold it from late 1964.
- CDC 6600: released in September 1964, with about 400,000 transistors, a 100-nanosecond cycle, ten peripheral processors, and the speed lead until the CDC 7600 in 1969.
- PDP-8: launched on 22 March 1965. The launch price is $18,000 to $18,500, depending on the source.
- Data General Nova: the company was founded in 1968 and the Nova came out in 1969. Launch prices range from about $8,000 to $9,200.
- The computer mouse: first shown on 9 December 1968 at the Fall Joint Computer Conference, with NLS running on an SDS 940 at SRI. Bill English built the first prototype in 1964.
- The Interface Message Processor: built by BBN under a DARPA contract. UCLA's unit was IMP number one.
- Apollo Guidance Computer software: during the Apollo 11 descent on 20 July 1969 the computer raised 1202 alarms. The 1969 MIT analysis counts four 1202s and one 1201. The waitlist handled brief tasks of about four milliseconds or less.
- Knuth–Morris–Pratt: the full paper appeared in 1977 in the SIAM Journal on Computing. Wikipedia dates the Morris and Pratt technical report to 1970, and another source puts the conception in 1974.
- Bresenham: worked out at IBM in 1962 for a plotter driven by an IBM 1401, and published in 1965 in the IBM Systems Journal.
- Not searched, from memory: the years of GPSS (1961), CPL (1963), ALGOL W and ISWIM (1966), the Burroughs B5000 (1961), the PDP-10 (1966), the Teletype Model 33 (1963), the Backus–Naur form (1960), software components (1968) and the NATO report (1969). Also the algorithm years (quicksort 1961, heapsort 1964, the Kalman filter 1960, Levenshtein 1965, the FFT 1965, tries 1960, Floyd's cycle finding 1967, Strassen 1969, union-find 1964, Gale and Shapley 1962, Floyd and Warshall 1962, the Bloom filter 1970, the dining philosophers 1965, Dijkstra's semaphores 1965) and the ELIZA program (1966).

### How I checked it

- Unit tests: 20 of 20 pass (AppDatabaseSeedTest 8, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the build log has no compiler warnings.
- Examples: a script takes every database 7 example from the Kotlin source and runs what this sandbox can run. 19 pass, each against an exact expected output: quicksort, heapsort, Levenshtein distance, the Knuth–Morris–Pratt search, the fast Fourier transform, tries, paging, the time-sharing scheduler, the Kalman filter, ELIZA, Gale and Shapley, the TECO-style editor, the dining philosophers, Bresenham's line, Strassen's multiplications, union-find, the C and Python shortest-paths comparison, and the awk pipeline around the C Bloom filter. The tortoise-and-hare program is checked against a brute-force search, not against a number I wrote down.
- The other 30 are not run here. The sandbox has no compiler for GPSS, SNOBOL, JOSS, CPL, ALGOL W, MUMPS or ISWIM, and no TECO or RUNOFF, so those records, the systems, the devices and the packet guide are only read. The practices are descriptions.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- My hand-calculated shortest paths for the C example were wrong in two rows. Node 2 reaches node 0 more cheaply through node 3 (cost 3) than on the direct edge (cost 5), and I had missed that. The C program and the Python driver agree on the correct matrix, and the verifier now expects it.
- Several firsts are disputed, and each record names the dispute in its details: the first time-sharing system, the first personal computer (LINC), the first supercomputer (CDC 6600), the first operating system written in a high-level language (B5000), the first use of semaphores (THE) and the origin of the term software engineering.
- The years of most algorithms and several machines come from memory, as listed above. The verifier checks the output of the examples exactly, but it cannot check the years in the records, so those still need a reader.
- Knuth–Morris–Pratt has two dates. The record uses 1970, for the Morris and Pratt report, and the details name the 1977 paper.
- IBSYS and SABRE from database 6 also carry 1960, which is inside this database's range. The overlap is deliberate.

### Notes

- 26 of 49 records are rare.
- Not verified yet: running on a device or emulator, the migration from version 6 on a real device, the UI with seven databases in the list and in the filters, lint, and the release build.

## CP-13: database 8, 1970 to 1980

Status: database 8 is in the app: 50 records, a migration for installs made at version 7, and tests for all eight databases. The build passes, 21 of 21 unit tests pass, and the Kotlin compiler gives no warnings. 20 of the 50 new records have code that runs and passes; the other 30 are languages, systems, devices, tools and practices, which have no code to run.

Mood: mostly calm. The dithering example failed its check on the first run, which I should have expected from one-row bands.

Date: 2026-10-15. Totals: Kotlin main 20,288 (CP-12: 18,903), Kotlin test 343 (320), XML 82, Gradle and project files 118, total 20,831. The +1,408 is the eleven new Kotlin files for database 8 (1,366 lines together), plus the catalogue, the database class and the seed list (main +19), and the tests (+23).

### What changed

- An eighth database, "Database 8: 1970s coding catalogue", with the same ten kinds. 50 records: languages 6, systems 5, devices 10, tools 5, practices 4, guides 5, examples 6, projects 4, flex 3, cross-code 2. 22 of them are rare.
- Eleven files `Db8*.kt`: ten record files and `Db8Catalog.kt`, which joins them into `db8Records`. `SeedCatalog.kt` lists the eighth database, with the colour #A1887F, and adds its records to `seedRecords`. The colour list already had a green close to the one I first thought of, so I chose brown.
- Database version 8 with `MIGRATION_7_8`. An install at version 7 gets database 8 from it, and older installs run the earlier migrations first.
- The years run from 1970 to 1980, both included, so 1970 is shared with database 7, as the other boundary years were. Database 1 has 131 records from these years. I read all their titles before choosing topics, and I did not repeat any of them.
- Tests cover eight databases. `SeedDataTest` checks the ids of database 8 and that its records fall between 1970 and 1980. `AppDatabaseSeedTest` checks its record count, and it has a new test that removes database 8 and runs `MIGRATION_7_8`. 21 unit tests in total.

### Facts checked by web search

- Searched, and the records were written from the results: System R (1974), INGRES (1973), VM/370 (1972), TENEX (1972), the IBM System/38 (1978), Maple (1980), Ratfor and Pascal-S (both 1975), Smalltalk-72 (1972), the Hunt and McIlroy paper behind diff (1976), N-version programming (1977) and the Monty Hall problem (Selvin's letter, 1975).

### Not searched, from memory

- Everything else: the ten devices, QUEL, Communicating Sequential Processes, Altair BASIC, Structured design, Software Tools, multi-user dungeons, ed, WordStar and VisiCalc. The years of the guides and algorithms: Diffie and Hellman (1976), RSA (1977), Lamport clocks (1978), the relational join (1974), message lookup (1972), Boyer–Moore (1977), Aho–Corasick (1975), Pollard's rho (1975), Miller (1976) and Rabin (1980), Tarjan (1972), Ziv and Lempel (1978), Gosper's hack in HAKMEM (1972) and Floyd and Steinberg (1976). The years of the projects: Mastermind (1970), Zork (1977), the VisiCalc-style spreadsheet (1979) and Rogue (1980). The 1990 Parade column of Marilyn vos Savant.

### How I checked it

- Unit tests: 21 of 21 pass (AppDatabaseSeedTest 9, RecordFilterTest 5, SeedDataTest 7). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the build log has no compiler warnings.
- Examples: a script takes every database 8 example from the Kotlin source and runs what this sandbox can run. 20 pass. 17 are checked against exact output: the guides on Diffie–Hellman, RSA, Lamport clocks, joins and message lookup, Boyer–Moore, Aho–Corasick, Pollard's rho, Miller–Rabin, Tarjan's components, LZ78, Gosper's hack, Mastermind, the text adventure, the spreadsheet, the C and Python LZ78 round trip and the C and awk factoring pipeline. I worked out the Diffie–Hellman and RSA numbers, the LZ78 tokens and the Mastermind feedback by hand before the run, and they matched. The other three are checked by properties: the dungeon map must be 40 by 12 with one player, the black share of each dithering band must be within 5 percent of its grey level, and the Monty Hall win rates must be within one percentage point of one third and two thirds.
- The other 30 records have no code, so there was nothing to run.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- The dithering example failed its check on the first run. I had written it with one-row bands, and error diffusion needs more rows than that to settle: the first row came out all black. The example now uses four rows per band and prints 16 lines of 32 characters, and the check compares each band as a whole.
- Re-reading the records found three wording errors. The code was right in each case. The RSA detail called the modulus "the product of the primes minus one", where it should be (p - 1) times (q - 1). The Lamport detail said the example passes one message, but it passes two. The Gosper detail said the example lists the four-bit numbers, but it prints the first five. I corrected all three.
- The Monty Hall detail said that a later letter gave the puzzle its name. I could not back that up with a source, so I removed it. The record now says what Selvin's 1975 letter did and names the 1990 column as the point where the puzzle became famous.

### Notes

- 22 of 50 records are rare, a judgement call again, which I'll compare with the other databases before the next release.
- "Erlang" and "Software inspections" appear in both database 1 and database 2. They were there before this checkpoint. The uniqueness test is per database and passes, so I left them alone.
- I checked the counting method again on the CP-12 commit first, and it gives exactly the CP-12 numbers.
- Not verified yet: running on a device or emulator, the migration from version 7 on a real device, the UI with eight databases in the list and in the filters, lint, and the release build.
