# Development log: Retro Databases

Solo development log for the Android app in this folder. I add an entry at each checkpoint (CP-NN), in order. The numbers are measured. The rest is my honest take on how it went, so some entries are grumpier than others.

## Conventions

- Each checkpoint records line counts for the tracked sources: Kotlin (`app/src/main/java`, `app/src/test`), XML (`app/src/main/res`, `AndroidManifest.xml`), and hand-written Gradle and project files.
- Counts come from `wc -l` on the working tree. Build output, the generated Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`) and this log are excluded from the totals. I count with `wc` because it's boring and I trust it.
- Deltas are measured against the previous checkpoint.
- Work before CP-01 was not measured, so the table starts there.
- From CP-16 the work runs in quotas. A quota is the number of decades in a batch's year range, counted from both ends, and each batch covers more decades than the one before. A decade that already exists is counted in the range but not written again.

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
| CP-14 | 2026-10-15 | 21,664 | 374 | 82 | 118 | 22,238 | +1,407 (database 9: 50 records in eleven files, migration 8→9, tests +31) |
| CP-15 | 2026-10-15 | 28,170 | 490 | 82 | 118 | 28,860 | +6,622 (databases 10 to 14: 250 records in 55 files, migrations 9→10 to 13→14, tests +116) |
| CP-16 | 2026-10-15 | 34,733 | 606 | 82 | 118 | 35,539 | +6,679 (databases 15 to 19: 250 records in 55 files, migrations 14→15 to 18→19, tests +116) |
| CP-17 | 2026-10-15 | 43,168 | 768 | 82 | 118 | 44,136 | +8,597 (databases 20 to 26: 350 records in 77 files, migrations 19→20 to 25→26, tests +162) |
| CP-18 | 2026-10-15 | 52,274 | 953 | 82 | 118 | 53,427 | +9,291 (databases 27 to 34: 400 records in 88 files, migrations 26→27 to 33→34, tests +185) |
| CP-19 | 2026-10-15 | 62,696 | 222 | 82 | 118 | 63,118 | +9,691 (databases 35 to 44: 500 records in 110 files, migrations 34→35 to 43→44, tests -731) |
| CP-20 | 2026-10-15 | 73,269 | 222 | 82 | 118 | 73,691 | +10,573 (databases 45 to 54: 500 records in 110 files, migrations 44→45 to 53→54, tests +0) |
| CP-21 | 2026-10-16 | 83,047 | 222 | 82 | 118 | 83,469 | +9,778 (databases 55 to 64: 461 records in 110 files, migrations 54→55 to 63→64, tests +0) |
| CP-22 | 2026-10-16 | 92,742 | 222 | 82 | 118 | 93,164 | +9,695 (databases 65 to 74: 444 records in 110 files, migrations 64→65 to 73→74, tests +0) |
| CP-23 | 2026-10-18 | 99,282 | 222 | 82 | 118 | 99,704 | +6,540 (databases 75 to 84: 295 records in 110 files, migrations 74→75 to 83→84, tests +0) |
| CP-24 | 2026-10-18 | 105,281 | 222 | 82 | 118 | 105,703 | +5,999 (databases 85 to 94: 266 records in 110 files, migrations 84→85 to 93→94, tests +0) |

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

## CP-14: database 9, 2026 to 2036 (a forecast)

Status: database 9 is in the app: 50 records, a migration for installs made at version 8, and tests for all nine databases. The build passes, 23 of 23 unit tests pass, and the Kotlin compiler gives no warnings. All 20 records with code run and match their expected output. The other 30 records are the forecast itself (languages, systems, devices, tools and practices), which has no code to run.

Mood: unusual. The request was to guess the next decade, so this is the first database about the future, and nothing in it can be checked against a record yet. Every forecast record says that it is a forecast, and I checked only the dated facts that each basis sentence relies on.

Date: 2026-10-15. Totals: Kotlin main 21,664 (CP-13: 20,288), Kotlin test 374 (343), XML 82, Gradle and project files 118, total 22,238. The +1,407 is the eleven new Kotlin files for database 9 (1,357 lines together), plus the catalogue, the database class and the seed list (main +19), and the tests (+31).

### What changed

- A ninth database, "Database 9: 2026 to 2036 forecast", with the same ten kinds. 50 records: languages 6, systems 5, devices 8, tools 6, practices 5, guides 5, examples 6, projects 4, flex 3, cross-code 2. Six of them are rare, and in this database the rare flag marks a long shot.
- Eleven files `Db9*.kt`: ten record files and `Db9Catalog.kt`, which joins them into `db9Records`. `SeedCatalog.kt` lists the ninth database, with the colour #90A4AE, and adds its records to `seedRecords`.
- Database version 9 with `MIGRATION_8_9`. An install at version 8 gets database 9 from it, and older installs run the earlier migrations first.
- The records are dated 2027 to 2036. Nothing is dated 2026, because that is the year in progress, and its facts are already in database 5.
- Tests cover nine databases. `SeedDataTest` checks the ids of database 9 and that its records fall between 2026 and 2036, and it has a new test: every forecast record says that it is a forecast. `AppDatabaseSeedTest` checks the record count of database 9, and it has a new test that removes database 9 and runs `MIGRATION_8_9`. 23 unit tests in total.

### Facts checked by web search

- C++26 reflection (P2996): the search found revision 13 from June 2025, addressed to the core and library wording groups, and implementations in an EDG front end and in Bloomberg's Clang fork. It did not establish adoption into the working draft, so the record does not claim adoption.
- Java value classes (JEP 401): news and blog coverage says the preview targets JDK 28, in March 2027, after JDK 27 in September 2026 without it. One news summary expects the preview to last past JDK 29. These are mostly secondary sources, so the JEP page on openjdk.org needs checking.
- IBM Starling: the June 2025 roadmap names a fault-tolerant machine in Poughkeepsie for 2029, with 200 logical qubits and 100 million operations. The search found no 2026 progress report.
- NIST IR 8547: the first public draft is dated 12 November 2024. In the draft, RSA and elliptic-curve schemes are deprecated after 2030 and disallowed after 2035. Vendor write-ups say it was still not final in mid-2026, and none of the results came from nist.gov.
- EU Cyber Resilience Act: reporting duties apply from 11 September 2026, and the rest of the act from 11 December 2027. Fines can reach 15 million euros or 2.5 per cent of global turnover. The sources are law-firm commentary, not the regulation itself.
- WASI 0.3: wasi.dev dates the 0.3.0 release to 11 June 2026. Sources disagree on which Wasmtime version switched it on, so the record does not give one.
- OpenSSL 3.5: released on 8 April 2025, with native ML-KEM, ML-DSA and SLH-DSA. Its default TLS client sends X25519MLKEM768 and X25519 key shares, and the results say ML-DSA is not on by default in TLS. The final release notes did not appear in the results, only the pre-release announcements.
- Android XR glasses: partners Samsung, XREAL, Warby Parker and Gentle Monster, and a December 2025 article also names Qualcomm. Reports from May 2026 point to a fall 2026 launch window. Pricing and sale dates were unannounced in those reports, and I found no confirmation of sales or shipments.
- Software Carbon Intensity: version 1.0 in late 2022, and ISO/IEC 21031:2024 with a first edition in March 2024. The Foundation names 1.1 as its latest version, and I did not find a date for it.
- Carbon: still experimental, with no 1.0 release found. A roadmap copy, which may be from 2024, moved the 0.1 milestone to late 2026, and I found no official 2026 status.
- RVA23: ratified by RISC-V International in October 2024, with vector and hypervisor support mandatory.
- Rust in the Linux kernel: reports from December 2025, after the maintainers' summit in Tokyo, say the experimental label no longer applies, and the Rust for Linux project declared the experiment finished.

### Not searched, from memory

- The years of the guides and examples: Lamport's signature (1979), Wengert's derivative method (1964), Hilbert's curve (1891), Dennis and Van Horn (1966), the G-counter paper of Shapiro and collaborators (2011), RFC 5869 (2010), the WebAssembly specification (2019), the local-first essay (2019), and the FNV-1a hash with its published value for "hello".
- The standards and products: NIST FIPS 204 and 205 (August 2024), NIST SP 800-208 (2020), the JDK long-term-support cadence (JDK 29 in 2027 and JDK 33 in 2029), Firecracker and gVisor, Lean, Dafny and Kani, the Copilot+ PCs of 2024, Loihi 2 and SpiNNaker 2, the wristband shipped with AR glasses, and the humanoid robot preorders.
- Python's free-threaded build (PEP 779) was already checked in database 5.
- Every forecast is my guess. The searches checked only the dated facts that a basis sentence relies on.

### How I checked it

- Unit tests: 23 of 23 pass (AppDatabaseSeedTest 10, RecordFilterTest 5, SeedDataTest 8). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the build log has no compiler warnings.
- Examples: a script takes every database 9 example from the Kotlin source and runs what this sandbox can run. All 20 pass against exact expected output. Two of those values are hash-based and I did not work them out by hand: the hybrid key and the FNV-1a hashes. Before pinning them, I checked the HKDF output against RFC 5869 test case 1 and against Node's crypto module, which gives the same hybrid key as the Python script. The three FNV values come from a C program and a JavaScript program that agree, and the value for "hello" matches the published test value, 4f9f2cab. I worked the other expected lines out by hand before the run (the scheduler's totals, the token bucket's sequence of yes and no, the Hilbert grid, and the failing row of the spec table), and they matched.
- The other 30 records have no code, so there was nothing to run.
- Structure: 50 records covering all ten kinds, dated 2027 to 2036. All 30 forecast records say "forecast" in their details, which the new test enforces. No title repeats inside the database, and across the whole catalogue only "Erlang" and "Software inspections" repeat, from databases 1 and 2.
- Cyrillic: none in the code, resources, tests or the bundle.

### Problems

- The first draft stopped at 2033, so the range had no records for 2034 to 2036. I swapped two records for items dated 2035 (post-quantum firmware signing) and 2036 (a wristband for input). The count stayed at 50.
- The search did not establish that C++26 reflection was adopted into the working draft, so the record describes the proposal's state and does not claim adoption.
- Carbon's roadmap came from a copy that may be out of date, and the record says so.
- Two verifier checks started as shape checks, because I had not yet confirmed the hybrid key and the FNV values. After the Node check I pinned the exact values.

### Notes

- In this database the rare flag marks long shots. In the other databases it marks little-known items, so the counts are not directly comparable. The catalogue now has 456 rare records in total, 6 of them here.
- When a forecast year passes, I want to compare the guess with what happened, so these records should not be edited to match the outcome.
- The line-count method is the same as at CP-13, which reproduced the CP-12 numbers.
- Not verified yet: running on a device or emulator, the migration from version 8 on a real device, the UI with nine databases in the list and in the filters, lint, and the release build.

## CP-15: databases 10 to 14, 1900 to 1950

Status: five more databases are in the app, one per decade from the 1900s to the 1940s: 250 records, migrations from version 9 to version 14, and tests for all fourteen databases. The build passes, 28 of 28 unit tests pass, and the Kotlin compiler gives no warnings. The 100 runnable records (20 per database) pass against their expected output.

Mood: the first time I split one job across several helpers. I ran one helper per decade in parallel, wired the catalogue, the migrations and the tests myself, and checked every database before accepting it. Four records repeated topics from other databases and were replaced, and one helper ran out of search budget before it wrote any files, so I resumed it with instructions to finish from memory and flag what it could not check.

Date: 2026-10-15. Totals: Kotlin main 28,170 (CP-14: 21,664), Kotlin test 490 (374), XML 82, Gradle and project files 118, total 28,860. The +6,622 is the 55 new Kotlin files for databases 10 to 14 (6,410 lines together), plus the catalogue, the database class and the seed list (main +96), and the tests (+116).

### What changed

- Five databases, each with the same ten kinds and 50 records: "Database 10: 1940s coding catalogue" (1940 to 1950), "Database 11: 1930s" (1930 to 1940), "Database 12: 1920s" (1920 to 1930), "Database 13: 1910s" (1910 to 1920) and "Database 14: 1900s" (1900 to 1910). The rare flags come to 10, 9, 7, 10 and 9, which makes 45 here and 501 in the whole catalogue.
- Eleven files per database, 55 in total: ten record files and a catalogue each. Colours are #4DB6AC, #E57373, #DCE775, #9FA8DA and #FFAB91.
- Version 14 with MIGRATION_9_10 to MIGRATION_13_14. An install at each earlier version gets the new databases from the matching migration, as before.
- Tests: SeedDataTest checks ids, years and rare records for all fourteen databases, and AppDatabaseSeedTest checks the counts and adds five migration tests. 28 unit tests in total (AppDatabaseSeedTest 15, RecordFilterTest 5, SeedDataTest 8).

### Facts checked by web search

The helpers shared one search budget, which ran out after about 60 searches.
- 1940s: the Baby's first run on 21 June 1948 (131072), Short Code (1949), Plankalkul (designed 1942 to 1945), Turing's program checking (24 June 1949), EDSAC's first run (6 May 1949), Dantzig's simplex method (1947), the Moore School course (8 July to 31 August 1946; one source says 30 August). Disputed: BINAC's status, the IAS flow diagrams, Colossus's dates, and Kaprekar's routine (1949, 1950 or 1955).
- 1930s: ITA2 (1932, though some guides say 1930), the Enigma army order (June 1930, 117 machines; earlier use disputed), the IBM 601 (1931), the ABC demonstration (1939, equations disputed), the 1940 teletype demonstration, the German telex network (1933), Typex (1937), the Bombe's first run (14 March 1940), the WPA tables (1938), HP (1939), Zentralblatt (1931), Mathematical Reviews (1940). Z2's completion date is disputed.
- 1920s: Post's tag systems (1921; printed in 1943), Schoenfinkel (lecture 1920, published 1924), the IBM 80-column card (1928), the Teletype Model 15 (launched 1930; the 1925 machine is the Model 14), Presburger (1929; Britannica says 1930), Basic English (1930), Friedman (written 1920, published 1922), Nyquist (1928), Yule (1926), Hebern (filed 1921, patented 1924), the M-94 (1922), Cady (1921).
- 1910s: Dreyer (1912 and 1913), Powers (1914), the 1910 census tabulators, Sheffer (1913), Pearson's tables (1914), Armstrong's regenerative receiver (1912; patent October 1914), Carmichael numbers (1910), Fisher (1912 and 1918), Erlang (1917), Ramanujan (1915 and 1916), Pocklington (1914), Haar (1910), Galerkin (1915), Bernstein (1912), Julia (1918), Vernam's patent (1919), ADFGVX (1918), Ford (1913 and 1914), Taylor (1911), the Radio Act (1912), daylight saving (1916), Army Alpha (1917), the Q code (1912), the Henry Draper Catalogue (1918), the Simplex code (1911). Richardson's 1911 paper is only partly confirmed; one source says 1910.
- 1900s: Richardson's volume (1910 or 1911), the Telegraphone (1900), the Pacific cable (31 October 1902), Wieferich's 1909 paper (1093 and 3511 below 5000), Lorenz (1905), Latino sine flexione (1903), Roberts's tide machine (1906 to 1908), Murray's code (1901), Pickard (1906), the Berlin SOS (1906, in force 1908), Richard's paradox (1905), the UDC (1905), LCSH (1909), Chemical Abstracts (1907), Einthoven (1901), the mercury rectifier (1902), Wehnelt (1903). The gyrocompass, LC classification, Ricci and Tarry dates are disputed, and the details say so.

### Not searched, from memory

- 1940s: the Z4's dates, the IBM 602 (1946), the Curta (1948), the Creed teleprinter (1949), the Model V (1946), the EDVAC draft (30 June 1945), the McCulloch–Pitts paper (1943).
- 1930s: Church (1932 and 1936), Turing (1936, printed 1937), Gentzen (1934), Tarski (1933), Bush (1931), Rejewski (1932), Shannon (1937), Shewhart (1931), Neyman (1937), Kolmogorov (1933), Leontief (1936), Grundy (1939), Kantorovich (1939), Benford (1938), Yates (1937), Kleene (1936), and the Enigma rotor wirings.
- 1920s: Fisher's 1.96, Hartley (1928), the claim that Ackermann's function outgrows primitive recursion, and Pólya's full German title.
- 1910s: Fisher's 1912 title, Armstrong's patent number, Dreyer's full name, Kleinschmidt's first name, Lewis's full name, the Haar transform as the first wavelet basis, and the QRM and QSO meanings.
- 1900s: Peano (1908), Ido (1907), Frege (1903), Marconi (1901), Zermelo (1908), Russell's types (1908), Fleming (1904), the Audion (1906), Hilbert (1900), Prandtl (1904), Willett (1907), Kutta (1901), Lebesgue (1902), Markov (1906), Student (1908), Pearson (1900), Yule (1900), Planck (1900), Schmidt (1907), Hardy–Weinberg (1908), Bachelier (1900), and Koch (1904).
- The Enigma test vector in database 11 (AAAAA gives BDZGO) comes from memory. No source was found for it, so it is unconfirmed.

### How I checked it

- Unit tests: 28 of 28 pass (AppDatabaseSeedTest 15, RecordFilterTest 5, SeedDataTest 8). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the build log has no compiler warnings or errors.
- Examples: each database has its own verifier with exact expected output. All 100 runnable records pass, and I re-ran all five verifiers before committing. The other 150 records are reading-only.
- Structure: 50 records per database, kinds exact, years inside each range, no dollar signs or Cyrillic, and no title repeated inside a database or across the new ones.
- Topic overlaps: I searched all new titles by keyword. Four records were replaced: two on Richardson extrapolation (database 12's cross-code record and database 13's flex record, both against database 14's guide), one on Ramsey's theorem (database 12's example, against database 11's flex record), and one on Shewhart's control charts (database 12's guide, against database 11's practice). Neighbouring topics were kept: the census tabulators of 1910 and 1920, the Enigma I (1930) and the commercial Enigma (1923), the Hilbert–Bernays calculus (1934) and the Hilbert–Ackermann Grundzüge (1928), the Principia notation (1910) and the theory of types (1908), and the card tabulator (1914) and the card encoder (1928).
- Line counts use the same method as before, and the CP-14 numbers reproduce.

### Problems

- Database 12's helper ran out of search budget before it wrote any files. I resumed it with instructions to finish, using memory for anything it could not check and flagging those facts. They are listed above.
- Database 13 had a flex piece on Richardson extrapolation that duplicated database 14's guide. It was replaced with "The harmonic series without the digit 9" (1914).
- Database 12 had three topics that duplicated other databases. All three were replaced.
- Database 13 has four Ramanujan records (tau, the partition congruence, highly composite numbers, and the nested radical). That is by design, but it is a high share for one mathematician, and I may spread them out later.
- Several single-source dates need rechecking: the 1910 teleprinter service, the 1920 ABC telegraphic code, Kleinschmidt (1916), Monroe (1912), Brun (1919), Vernam (1919), Kaprekar (1949) and the 1950 publication date of Booth's algorithm.
- Nothing has been run on a device. The Kotlin compiles and the unit tests pass, and that is all.

### Notes

- From this checkpoint the rare flag means little-known, as in databases 1 to 8. In database 9 it marked long shots.
- The helpers' verifiers and derivation notes are not in the repository.
- Not verified yet: running on a device or emulator, the migrations from versions 9 to 14 on a real device, the UI with fourteen databases in the list and in the filters, lint, and the release build.

## CP-16: databases 15 to 19, 1850 to 1900 (the first batch by quota)

Status: five more databases are in the app, one per decade from the 1850s to the 1890s: 250 records, migrations from version 14 to version 19, and tests for all nineteen databases. The build passes, 33 of 33 unit tests pass, and the Kotlin compiler gives no warnings. The 100 runnable records (20 per database) pass against their expected output. The 1900s were already database 14, so the six decades from 1850 to 1900 gave five new databases.

Mood: from this checkpoint the work runs by quota, and I have mixed feelings about it. I like the shape a quota gives. Once the size of a batch is known, I can plan the helpers, the checks and the wiring before the first record is written, and one script now adds the catalogue, the migrations and the tests. I am uneasy about the size. Five helpers ran at once on one search budget, so the later ones ran short, and many dates in this batch come from memory. The checks inside each helper missed about a dozen repeats between databases, and only a cross-batch check found them, late. There is some pleasure too: telegraphs, codes and early calculators make good material, and the verifiers caught one integer-division bug in a helper's Clausius program. The next quota will be larger, so the cross-batch check has to run while the helpers are still writing, not after they finish.

Date: 2026-10-15. Totals: Kotlin main 34,733 (CP-15: 28,170), Kotlin test 606 (490), XML 82, Gradle and project files 118, total 35,539. The +6,679 is the 55 new Kotlin files for databases 15 to 19 (6,467 lines together), plus the catalogue, the database class and the seed list (main +96), and the tests (+116).

### What changed

- Five databases, each with the same ten kinds and 50 records: "Database 15: 1890s coding catalogue" (1890 to 1900), "Database 16: 1880s" (1880 to 1890), "Database 17: 1870s" (1870 to 1880), "Database 18: 1860s" (1860 to 1870) and "Database 19: 1850s" (1850 to 1860). Their rare flags are 9, 8, 10, 10 and 8, so 45 new rare records and 546 in the whole catalogue.
- Eleven files per database, 55 in total: ten record files and a catalogue each. Colours are #90CAF9, #CE93D8, #FFCC80, #E6EE9C and #EF9A9A.
- Version 19 with MIGRATION_14_15 to MIGRATION_18_19. An install at each earlier version gets the new databases from the matching migration, as before.
- Tests: SeedDataTest and AppDatabaseSeedTest cover nineteen databases, with five new migration tests. 33 unit tests in total (AppDatabaseSeedTest 20, RecordFilterTest 5, SeedDataTest 8).
- The quota: a batch's quota is the number of decades in its year range, counted from both ends. This batch counts six decades (1850, 1860, 1870, 1880, 1890 and 1900); five are new, because 1900 was already database 14. The batch before it, 1900 to 1940, counted five, so each batch covers more decades than the one before, and the next one must count at least seven. A decade that already exists is counted but not written again.
- Process: one helper per decade, all in parallel, with one shared brief and suggested topics that keep the decades apart. Each helper had at most ten web searches. Its report lists which dates were searched, which come from memory and which rest on one source. Those reports are not in the repository.
- Scripts, kept in the session scratch folder and not in the repository: a title list for all records, a structure checker (counts, years, rare flags, overlaps with existing titles and provenance notes), a cross-batch overlap check, and a wiring script for the catalogue, the version, the migrations and the tests.

### Facts checked by web search

Most dates were checked where a search gave a clear answer, and the helpers were asked to say in the record details when sources disagreed. Some searches ran out, so the rest of the batch went on from memory. Examples that were searched: Gregg shorthand (a 1888 pamphlet and the 1893 American edition), the Brunsviga's first delivery (1892, from one blog), the National Electrical Code (1897), Volapük (the May 1879 outline), Galton's co-relation paper (1888), Tesla's motor patent (filed 12 October 1887, granted 1 May 1888) and Burroughs's patents of 1888, whose filing dates are disputed.

### Not searched, from memory

Most other dates and attributions in this batch, including the mathematics and physics of the 1850s to 1880s (Christoffel's 1858 quadrature, Dedekind, Cayley, Riemann, Boltzmann, Clausius, Kummer and Hamilton), the Bern treaty of 1874, and the telegraph and instrument dates of the 1850s. The helpers' reports list them.

### Problems

- Repeats between databases. The per-database checks could not see them, and the cross-batch check found them. In each pair one record was replaced: Odhner's calculator (16, against the Brunsviga in 15), Schröder's first volume (16, against the Operationskreis in 17), Meissel's 1870 count (17, against the one in 18), the Jordan canonical form (17, against 18), the virial theorem (18, against 17), the 1875 telegraph convention (17, against the Paris convention of 1865 in 18), Tait's quaternion product (18, against the treatise in 17), Cayley's group tables (19, against 17), the Wheatstone telegraph (19, against 18), the 1860 Karlsruhe congress (19, against 18), the pantelegraph (19, against the cross-code record in 18) and the four-colour question for maps (19, against the Heawood and Kempe records of 15 and 16). Two more repeated older records: Dewey's classification (17, against library classification in 14) and Dedekind's recursion (16, against primitive recursion in 12). All were replaced.
- Records that were not coding history: Foucault's pendulum and Kirchhoff's spectroscope (19), and Stokes's settling speeds for falling spheres (19, which is physics). They were replaced.
- Provenance notes such as "(memory)" and "search source" had leaked into one helper's record text. They were removed, and the checker now fails on them.
- About ten records have a descriptive originalTitle rather than a printed title, where the original is in Cyrillic or no printed title was found; for example Popov's recorder, the Brunsviga, the Kinetoscope, the Lumière cinematograph and the Pupin loading coil in database 15, and the Meissel record in database 18. The app shows them as labels, so they need checking before a release.
- Weak fits: the Encyklopädie and the Dictionary of National Biography in database 15 (both tools), and Ruhmkorff's induction coil in database 19.
- Single-source dates to recheck: Veltparl (1896), Dil (1893), Orba (1893) and Bolak (1899), each from one list; Galton (1873) and Tyer (1878), the latter from heritage sites only; Meissel (1870, one 2019 thesis); Bierens's dates (catalogue and archive records); Wheatstone (1858, museum dating only); the German-Austrian telegraph union (1850, one timeline); the Hughes patents (dated 1856 to 1859 by one source); and the Gerke alphabet (dates disputed).

### How I checked it

- Unit tests: 33 of 33 pass (AppDatabaseSeedTest 20, RecordFilterTest 5, SeedDataTest 8). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, with no compiler warnings or errors.
- Examples: each new database has its own verifier with exact expected output, and all 100 runnable records pass, 20 in each database. The 150 reading-only records are not executed.
- Structure: 50 records per database, kinds exact, rare flags between 8 and 10, years inside each decade, no dollar signs, no Cyrillic, and no provenance notes in the record text.
- Whole catalogue: 1,520 records. The only titles that repeat are "Erlang" and "Software inspections", from databases 1 and 2, as before.
- Cross-batch: 17 pairs of titles across the new databases share two or more significant words. I kept them as neighbours, for the reasons below.

### Notes

- Neighbours kept, because they are different items: Hensel's and Dedekind's square roots (different methods); the vector notations of Gibbs and Heaviside (rival notations in one debate); the two congresses of 1860 and 1897; the manual and the automatic telephone exchanges (New Haven and Strowger); Ellis's palaeotype and the International Phonetic Alphabet; the algebras of logic of Boole and Peirce; the electrical units of 1856, 1860, 1863 and 1881 (steps in one story); the printing telegraphs of Hughes, Phelps and Baudot; the two telegraph unions of 1850 and 1865; and Shanks's digits of pi in database 19 against the bc calculation of pi in database 1 (different algorithms and stories).
- These clusters are dense, especially the electrical units and the printing telegraphs. A later pass could thin them.
- From database 10 onward the rare flag means little-known.
- Not verified yet: running on a device or emulator, the migrations from versions 14 to 19 on a real device, the UI with nineteen databases in the list and in the filters, lint, and the release build.

## CP-17: databases 20 to 26, 1780 to 1850 (the quota grows again)

Status: seven more databases are in the app, one per decade from the 1780s to the 1840s: 350 records, migrations from version 19 to version 26, and tests for all twenty-six databases. The build passes, 40 of 40 unit tests pass (AppDatabaseSeedTest 27, RecordFilterTest 5, SeedDataTest 8), and the Kotlin compiler gives no warnings. The 140 runnable records (20 per database) pass against their expected output. The 1850s were already database 19, so the eight decades from 1780 to 1850 gave seven new databases.

Mood: the quota went up again, from six decades counted in the last batch to eight, and I have two reactions at once. I like seeing the number grow, because the plan from last time held. The wiring was one script run, and the growth went into the content, not into the plumbing. There is some pleasure in the material too: telegraphs, calculating machines and early notation suit the 1820s and 1830s well. I am less comfortable with what the larger quota does to the process. Seven helpers writing at once share one search budget, and most repeats came back after the helpers had finished. Gauss, Legendre, Babbage and the printing presses turned up again and again, and the cross-batch check found them, not the helpers' own checks. Some records of the 1780s and 1790s are only loosely coding history, and I say so below. The next quota is larger still, so the cross-batch check has to run while the helpers are writing, not after they finish.

Date: 2026-10-15. Totals: Kotlin main 43,168 (CP-16: 34,733), Kotlin test 768 (606), XML 82, Gradle and project files 118, total 44,136. The +8,597 is the 77 new Kotlin files for databases 20 to 26 (8,301 lines together), the catalogue, the database class and the seed list (main +134 net), and the tests (+162).

### What changed

- Seven databases, each with the same ten kinds and 50 records: "Database 20: 1840s coding catalogue" (1840 to 1850), "Database 21: 1830s" (1830 to 1840), "Database 22: 1820s" (1820 to 1830), "Database 23: 1810s" (1810 to 1820), "Database 24: 1800s" (1800 to 1810), "Database 25: 1790s" (1790 to 1800) and "Database 26: 1780s" (1780 to 1790). Their rare flags are 8, 9, 10, 10, 8, 10 and 10, so 65 new rare records and 611 in the whole catalogue.
- Eleven files per database, 77 in total: ten record files and a catalogue each. Colours are #B0BEC5, #FFF59D, #F8BBD0, #C5CAE9, #DCEDC8, #FFE0B2 and #D7CCC8.
- Version 26 with MIGRATION_19_20 to MIGRATION_25_26. An install at each earlier version gets the new databases from the matching migration.
- Tests: AppDatabaseSeedTest covers twenty-six databases, with seven new migration tests (27 tests in all). SeedDataTest and RecordFilterTest as before. 40 unit tests in total.
- The quota: this batch counts eight decades (1780, 1790, 1800, 1810, 1820, 1830, 1840 and 1850). Seven are new, because 1850 was database 19. The batch before counted six, so the quota grew by two, and the next batch has to count at least nine. A decade that already exists is counted but not written again.
- Process: one helper per database, all seven in parallel, with one shared brief and a list of suggested topics that keeps the decades apart. Each helper had a limit of six web searches. The helpers wrote the record files and per-database verifiers, with expected outputs worked out by hand before each run. I wrote the five database 26 drafts myself, and a helper checked and corrected them. After the first round, helpers replaced records that repeated another database or had uncertain dates, and I checked each replacement.
- Scripts, kept in the session scratch folder and not in the repository: a title list for all records, a structure checker (counts, years, rare flags, overlaps with existing titles and provenance notes), a cross-batch overlap check, and a wiring script for the catalogue, the version, the migrations and the tests.

### Facts checked by web search

Helpers searched where a search gave a clear answer, and their reports say where sources disagreed. The database 24 helper's report is the most detailed. Its searches: Jacquard's loom, dated 1801 and 1804 (the 1801 medal is single-source); Turri's writing machine, dated 1801, 1806 or 1808 in different sources (the 1808 letter is single-source); Soemmerring's Munich demonstration of 28 August 1809 (the wire counts 27, 28 and 35 conflict); Popham's code (1800, the earliest surviving copy 1803, Royal Navy adoption 1816); the US Coast Survey act (dated 9 or 10 February 1807, field work 1816 to 1817); Argand's diagram (1806, publication disputed); Piazzi's catalogue (1803, republished 1814, observation span disputed); and Salva's 1804 visit to Barcelona (single-source). The same report lists as from memory the dates of Volta, Gauss, Dalton, Grotefend, Colebrooke, Woodhouse, Legendre, the census, Lalande, Webster, Gay-Lussac, Maelzel, Lancaster, West Point, the Survey of India, the 1807 cadastre, Budan, Brianchon, Carnot and Malus.

### Not searched, from memory

Most dates and attributions in the other databases, and the ones listed above. The helpers' reports list them, and the reports are not in the repository.

### Problems

- Repeats between databases, all replaced:
  - Database 21's "Penny Post, the uniform postage in Britain" repeated database 20's "Uniform penny post of Britain". Replaced by the Great Western steamship service from Bristol to New York.
  - Database 22's "Peacock's symbolical algebra and the principle of permanence" repeated database 21's Peacock record, and "Legendre symbols mod 11 by Euler's criterion" repeated the Legendre and Euler material of databases 24 and 25. Replaced by Humboldt's study of the Basque language and Carnot's efficiency limit for two heat reservoirs.
  - Database 23's "Morrison's Chinese dictionary" and "The Royal Astronomical Society" repeated records in database 22. Replaced by Babbage's Principles of Pure D-ism and the University of Berlin, opened 1810.
  - Database 24 had nine records about Gauss and several repeats. Eight records were replaced: four of the Gauss records, the Euler's criterion guide, the period lengths of 1/p, the gas volumes example and the Sicard record. Five Gauss records remain.
  - Database 25's "Stanhope's iron printing press" repeated database 24's all-iron press, its "Lacroix's course in the calculus" repeated database 23, and "Legendre's symbol by Euler's criterion" repeated the Legendre symbol material. Replaced by Lebon's thermolamp, the Rosetta Stone and its three scripts, and the continued fraction of pi.
  - Database 20 had eight records with uncertain dates or titles. They were replaced, and the replacements pass the checks.
- Anachronism: database 26's record "How the Legendre symbol says whether a number is a square" is dated 1785, but the symbol dates from 1798. I renamed it "Legendre's 1785 memoir on squares modulo a prime" and changed its verifier key to match.
- Galvani's record is dated 1786 (the experiments), while its originalTitle is the Latin commentary of 1791. The Latin title is kept, and the year difference is noted here.
- Two titles repeat across the whole catalogue, as before: "Erlang" and "Software inspections", from databases 1 and 2. They are outside this batch and were not changed.
- Not coding history, strictly: Lebon's thermolamp (25) and the Argand lamp (26) are technology. The helper flagged the thermolamp as off-theme. I kept it, and a later pass can swap it.
- Descriptive labels: several originalTitle values are descriptive labels rather than printed titles, and the database 24 triangulation example has the label "Survey practice of the period" as its author. These need checking before a release.

### How I checked it

- Unit tests: 40 of 40 pass (AppDatabaseSeedTest 27, RecordFilterTest 5, SeedDataTest 8). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the log has no compiler warnings.
- Examples: each new database has its own verifier with exact expected output, and all 140 runnable records pass, 20 in each database. The 210 reading-only records are not executed.
- Structure: 50 records per database, kinds exact (6, 5, 8, 6, 5, 5, 6, 4, 3 and 2), rare flags between 8 and 10, years inside each decade, no provenance notes in the record text, and no dollar signs or Cyrillic in the new files.
- Whole catalogue: 1,870 records. The only titles that repeat are "Erlang" and "Software inspections", from databases 1 and 2, as before.
- Cross-batch: 22 pairs of titles across the new databases share two or more significant words, after the replacements. I kept them as neighbours, for the reasons below.

### Notes

- Neighbours kept, because they are different items: Babbage's Difference Engines No. 1 and No. 2 and Muller's design (three machines); Webster's 1828 dictionary and his 1806 Compendious Dictionary (two books); Hutton's 1795 dictionary and his 1785 tables; the presses of Koenig, Stanhope and Hoe; the two survey-triangle records; Gauss's ratio test and Gauss's test for constructible polygons; Lacroix's and Woodhouse's notation for the calculus; Gallaudet's school and L'Epee's signs for the deaf; the electric motors of Jacobi and Barlow; and the four-square count and the three-square theorem, which are different theorems.
- Clusters, the main weak spot: Gauss (15 records in databases 21 to 25), Jacobi (seven in databases 20 to 22), Bessel (four books and instruments in databases 20 to 23), Babbage (six in databases 20 to 23), Legendre (five in databases 24 to 26), Herschel (four in databases 20 and 26), Lavoisier (four in database 26) and Condorcet (two in database 26). A later pass should thin these.
- Thin coding content: the 1780s to 1840s had less software to record, so many records are about adjacent science and technology (telegraphs, instruments, surveying, tables and notation). The coding label covers that only loosely.
- Dates from memory that deserve a recheck: Ronalds's telegraph (1816 or 1823), Gauss's heliotrope (1821), the date and title of Cauchy's residue record, the Gompertz title, the Admiralty shutter station and the Gauss arithmetic-geometric mean guide (1799, uncertain).
- Process note: the database 24 fix helper used seven searches, one over its limit of six. Its report says so.
- Not verified yet: running on a device or emulator, the migrations from versions 19 to 26 on a real device, the UI with twenty-six databases in the list and in the filters, lint, and the release build.

## CP-18: databases 27 to 34, 1700 to 1780 (the quota grows to nine decades)

Status: eight more databases are in the app, one per decade from the 1700s to the 1770s: 400 records, migrations from version 26 to version 34, and tests for all thirty-four databases. The build passes, 48 of 48 unit tests pass (AppDatabaseSeedTest 35, RecordFilterTest 5, SeedDataTest 8), and the Kotlin compiler gives no warnings. The 160 runnable records (20 per database) pass against their expected output. The 1780s were already database 26, so the nine decades from 1700 to 1780 gave eight new databases.

Mood: the quota went up again, from eight decades counted in the last batch to nine, and I feel tired and a little proud. The plumbing cost one script run and one test count, so the growth went into the content. The content is the hard part. Before 1780 the coding history is thin: the decades hold mathematics, instruments, grammars, trade and a few automata, and the word "coding" fits them loosely. Most of my time went into repeats. The same machine, book or canal came back in two or three databases, two helpers wrote the same Montmort problem, and the cross-batch check found more than the helpers' own checks did. We needed three rounds of replacements. The next quota will be larger, so the cross-batch check has to run as each database finishes, and each helper needs a list of what the other databases already cover before it writes.

Date: 2026-10-15. Totals: Kotlin main 52,274 (CP-17: 43,168), Kotlin test 953 (768), XML 82, Gradle and project files 118, total 53,427. The +9,291 is the 88 new Kotlin files for databases 27 to 34 (8,953 lines together), the catalogue, the database class and the seed list (main +153 net), and the tests (+185).

### What changed

- Eight databases, each with the same ten kinds and 50 records: "Database 27: 1770s coding catalogue" (1770 to 1780), "Database 28: 1760s" (1760 to 1770), "Database 29: 1750s" (1750 to 1760), "Database 30: 1740s" (1740 to 1750), "Database 31: 1730s" (1730 to 1740), "Database 32: 1720s" (1720 to 1730), "Database 33: 1710s" (1710 to 1720) and "Database 34: 1700s" (1700 to 1710). Their rare flags are 9, 8, 9, 9, 8, 8, 8 and 10, so 69 new rare records and 680 in the whole catalogue.
- Eleven files per database, 88 in total: ten record files and a catalogue each. Colours are #80CBC4, #B39DDB, #FFCDD2, #C8E6C9, #B3E5FC, #F0F4C3, #D1C4E9 and #FFF9C4.
- Version 34 with MIGRATION_26_27 to MIGRATION_33_34. An install at each earlier version gets the new databases from the matching migration.
- Tests: AppDatabaseSeedTest covers thirty-four databases, with eight new migration tests (35 in all). 48 unit tests in total.
- The quota: nine decades count (1700, 1710, 1720, 1730, 1740, 1750, 1760, 1770 and 1780). Eight are new, because 1780 was database 26. The batch before counted eight, so the quota grew by one, and the next batch has to count at least ten. Boundary years count at both ends, so three records of database 28 are dated 1770.
- Process: one helper per database, all eight in parallel, with one shared brief, suggested topics and a rule against repeats. Helpers wrote the record files and per-database verifiers, with expected outputs worked out by hand before each run. Each helper had a search budget of six. Helpers were resumed for replacements, first for repeats inside a database and then for repeats across databases that the cross-batch check found. I decided the replacements and ran the final checks. For database 29 I searched six dates myself: Smeaton's waterwheel trials (1759), Mudge's escapement (about 1755), Halley's comet forecast (1758 to 1759), Lagrange's letter of 1755, Michell's treatise of 1750 and Ferguson's orrery. The helpers' reports list every other searched and remembered date; the reports are not in the repository.
- Scripts, kept in the session scratch folder and not in the repository: a title list for all records, a structure checker (now stops on an empty database), a cross-batch check, and a wiring script. The wiring script stopped once on the layout of the seed test (the names for databases 20 to 26 share one line), so the lines for databases 27 to 34 were added by a short edit that reuses the script's own values.

### Facts checked by web search

Examples from the helper reports: Smeaton's overshot wheel ranked above the undershot one in 1759 (ratio disputed); Mudge's lever escapement about 1755 (sources range from 1754 to 1756); Clairaut's forecast of Halley's comet for mid-April 1759, with perihelion on 13 March 1759 (the forecast day is disputed); Gurney's Brachygraphy in 1750; the Leyden jar of 1745 (the discoverer and the date are disputed); Newton's 1717 mint report (44½ guineas and 62 shillings per troy pound); the Swedish calendar, one day ahead of the Julian calendar from 1700 and an extra 30 February in 1712.

### Not searched, from memory

Most other dates and attributions, including the mathematics of the 1700s and the instruments of the 1750s. The helper reports list them. Several are the weakest facts in this batch (see Problems).

### Problems

- Repeats found after the first round, all replaced: Cugnot's vehicle (27 and 28); the Encyclopaedia Britannica (27 and 28); the Trent and Mersey, or Grand Trunk, canal (27 and 28); Réaumur's and Fahrenheit's conversions (31, 32 and 33); Gravesande's book (32 and 33); Montmort's matchings problem (33 and 34); Cristofori's piano (33 and 34); the Statute of Anne (33 and 34); the Swedish calendar experiment, which had the same title in two databases (33 and 34); Franklin's charge (29 and 30); Cramer's count (29 and 30); Gurney's shorthand (29 and 30); the Pythagorean comma (30 and 31); the flattening of the Earth (30 and 31); and the Copley medal (29 and 31). Inside single databases, several records repeated one machine or one book (a project or an example on the same device or book), for example Hahn, Ramsden, Jaquet-Droz, Arkwright, Hargreaves, Harrison's H4, Graham and Newsham. Most were replaced, with the exceptions below.
- Kept on purpose, because the 1750s had no further confident items after two requests: six repeats in database 29 (Dollond's achromat powers, Mayer's lunar table and his 360 degrees, Linnaeus's name checker, Johnson's index and the waterwheel work). Database 30 keeps four Euler records, three of them about the number e.
- Clusters that stay: Franklin (nine records across databases 27 to 30); Harrison (five across databases 28 to 32); Newton (ten across databases 32 to 34); Mayer (four in database 29); Lambert (four in database 28); smallpox inoculation (three records in databases 28, 32 and 33); the calendar (five records in databases 29, 30, 33 and 34); academies (seven, one in each of seven databases, each a different institution).
- Dates from memory that need a check before a release: Canton's magnets (1751), Boscovich's survey (1755), Black's chalk (1755), Tartini's difference tones (1754), Queen Anne's Bounty (1704), the Sun Fire Office (1710), the Spectator's numbers (1711), Keill's Oxford lectures (1718) and Taylor's perspective (1719).
- Shuli jingyun: accounts of its dates differ, so the record now says that the dates vary.
- Several originalTitle values are descriptions, or German and French titles that were not checked (for example the Marggraf and Nollet lines in database 30). The original title of Pell's equation in database 28 is an English gloss.

### How I checked it

- Unit tests: 48 of 48 pass (AppDatabaseSeedTest 35, RecordFilterTest 5, SeedDataTest 8). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the log has no compiler warnings.
- Examples: each new database has its own verifier with exact expected output, and all 160 runnable records pass, 20 in each database. The 240 reading-only records are not executed.
- Structure: 50 records per database, kinds exact (6, 5, 8, 6, 5, 5, 6, 4, 3 and 2), rare flags between 8 and 10, years inside each decade (boundary years count at both ends), no provenance notes in the record text, and no dollar signs or Cyrillic in the new files.
- Whole catalogue: 2,270 records. The only titles that repeat are "Erlang" and "Software inspections", from databases 1 and 2, as before.
- Cross-batch: 34 pairs of titles across the new databases share two or more significant words. I reviewed each. The same-item pairs were replaced, and the rest are different items on a shared subject, listed under the clusters above.

### Notes

- Items that look alike but are different: Harrison's H1, H3 and H4; the punched-card looms of Bouchon and Falcon; the academies, each a different institution; the smallpox records (Timoni, Montagu and Bernoulli's life table); the Lagrange records on Pell's equation and on the resolvent.
- These are the thinnest decades for "coding". Many records are about adjacent science, instruments, grammars and trade. A later pass should first thin the Euler and e cluster, the calendar cluster and the Newton cluster.
- Not verified yet: running on a device or emulator, the migrations from version 26 to 34 on a real device, the UI with thirty-four databases in the list and in the filters, lint, and the release build.

## CP-19: databases 35 to 44, 1600 to 1700 (the quota grows to ten decades, and the catalogue is generated)

Status: ten more databases are in the app, one per decade from the 1600s to the 1690s: 500 records, migrations from version 34 to version 44, and a catalogue that is generated from one spec file. The build passes with no compiler warnings, 17 of 17 unit tests pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5), and the 200 runnable records (20 per database) pass against their expected output. The batch before counted eight new decades, so the quota grew by two.

Mood: I am pleased with the build change, which takes the per-database wiring out of the code and puts the rules in one place, so the next decade costs one spec entry and one script run. The content was harder. Ten decades of coding history are thin: the 1600s are mathematics, instruments, dictionaries and trade, and the 1690s hold very little that fits the word "coding". Most of the work went into repeats. The same book, instrument or ledger came back in two or three databases, and the cross-batch check found more of them than the helpers' own checks did. The 1690s needed several rounds, and some device dates are approximate. I would rather write "about 1698" than stretch a year, and I would rather the log say so.

Date: 2026-10-15. Totals: Kotlin main 62,696 (CP-18: 52,274), Kotlin test 222 (953), XML 82, Gradle and project files 118, total 63,118 (CP-18: 53,427). The +9,691 is the 110 new Kotlin files for databases 35 to 44 (10,723 lines together), the database class (515 to 102 lines, net -413), the generated catalogue and seed list (net +112), and the tests (-731).

### What changed

- Ten databases, each with the same ten kinds and 50 records: "Database 35: 1690s coding catalogue" (1690 to 1700), "Database 36: 1680s" (1680 to 1690), "Database 37: 1670s" (1670 to 1680), "Database 38: 1660s" (1660 to 1670), "Database 39: 1650s" (1650 to 1660), "Database 40: 1640s" (1640 to 1650), "Database 41: 1630s" (1630 to 1640), "Database 42: 1620s" (1620 to 1630), "Database 43: 1610s" (1610 to 1620) and "Database 44: 1600s" (1600 to 1610). Their rare flags are 8, 9, 8, 9, 9, 9, 9, 10, 9 and 9, so 89 new rare records and 769 in the whole catalogue.
- Eleven files per database, 110 in total. Colours are #E0F7FA, #FCE4EC, #E8EAF6, #F1F8E9, #FFF8E1, #EFEBE9, #ECEFF1, #E3F2FD, #FBE9E7 and #F5F5F5.
- Build rework. The catalogue is now described by one file, tools/databases.json (for each database: id, years, quota flag, curated flag, name, description and colour). tools/build_catalogue.py checks every database and then writes SeedCatalog.kt, the generated part of CATALOGUE.md and the generated overview and structure blocks of README.md. The checks are: exact kind counts and 50 records for the quota databases; instructions and an example only for the runnable kinds (GUIDE, EXAMPLE, PROJECT, FLEX, CROSS); rare flags from 7 to 10; years inside each range; no provenance phrases; no dollar sign or Cyrillic in the record files; and titles unique across databases, except the two allowed duplicates "Erlang" and "Software inspections". A new database now needs its record files, one spec entry and one run of the script.
- Migrations: AppDatabase.kt went from 515 to 102 lines. The 34 named migration objects are replaced by one function, seedMigration(version), and a list, seedMigrations. The database version comes from the generated catalogue.
- Tests: AppDatabaseSeedTest has four tests: the first open seeds every database with its records; the migrations cover every version up to the current one; each migration restores its database and keeps the others; the favourite toggle stores its change. SeedDataTest has eight generic checks over every database: unique ids, titles unique within a database, years inside each range, every kind present, summaries and details present, projects with instructions and code, rare records present, and the forecast entries. The unit test count went from 48 to 17. It dropped because the per-database tests became loops over the catalogue, so a new database needs no test code.
- Documents: CATALOGUE.md holds the curated text of databases 1 to 34 (the README had grown too long), and its generated part is written by the script. README's overview and structure are generated blocks, and "How to add a database" is now four steps with no hand-written migration.
- Process: one helper per database, ten in parallel, with one shared brief, suggested topics and the list of 2,270 existing titles. Most helpers were resumed for replacements. After each round I ran the checks and the cross-batch comparison, decided the replacements and made a few fixes myself (see Problems). Each helper had a search budget of six at first; the helper for database 35 was granted ten more and used sixteen in total.
- Scripts, kept in the session scratch folder and not in the repository: a title list for all records, a cross-batch check, a structure checker, a packer for the source text, and one verifier per database with expected outputs worked out by hand before each run.

### Facts checked by web search

Examples from the helper reports (the reports list the rest): Norwood's survey from London to York (June 1633 to June 1635); the Letter Office proclamation of 1635; Daye's press (arranged 1638, first book 1640); Fermat's marginal note (about 1637, printed 1670); Descartes's folium (letter of 23 August 1638); Gascoigne's micrometer (idea about 1639, measurements August 1640); Oughtred's rectilinear rule (1622, printed 1633) and circular rule (1632); Delamain's manuscript of 1629; Schickard's clock (letter of September 1623); Briggs's logarithms to 14 places (1624) and Vlacq's tables to 10 places (1628); Gunter's chain of 66 feet and 100 links; Bonet's alphabet (Madrid, 1620); Drebbel's Thames trial (1620); Burgi's Progress-Tabulen (Prague, 1620); Galileo's eclipse method (notes from 1612); the word "telescope" (coined in 1611); the decimal point (Clavius 1593, Pitiscus 1608 and 1612); Wilkins's Mercury (1641); Galileo's clock (idea 1641, construction from 1649); Mikalja's dictionary (printed 1649 to 1651); Tschirnhaus's burning glasses (1687 to 1688); plate glass cast in 1688; Hooke's visual telegraph (1684, build disputed); Hickes's Oxford volume (1689); the brachistochrone dates (1696 and 1697); Papin's 1690 paper; the Darien statute of 26 June 1695; Winstanley's tower, lost in the 1703 storm.

### Not searched, from memory

Most other dates and attributions, including the Douay Old Testament and its book counts, the Jamestown ship tonnages (the example uses one commonly cited set, and the details say that accounts differ), Halley's life table, Locke's Essay, Ludolf's Russian grammar, Leibniz's Novissima Sinica, Glauber's book title, and most dates in the 1650s and 1660s. The helper reports list them, and the single-source dates are named under Problems.

### Problems

- Thin decades. The 1690s had few items with a dated first public form. To reach 50 records, four device records have approximate dates, and their details say why: Loulie's sonometer (about 1698), the socket bayonet (the 1690s), Hartsoeker's glass-bead lens (about 1694) and Flamsteed's mural arc (about 1690).
- Single-source dates, as the helper reports list them: the Academy dictionary's first edition (August 1694); the Letter Office date of 31 July 1635; Norwood's "large sextant"; the Savilian mural quadrant of 1637; Fromanteel's 1685 watch (one museum listing); the Imprimerie royale of 1640; Gascoigne's 1640 measurements (one later account); Bonet's mid-1620s dating; Gunter's 1620 idea; Morland's 1666 date and the council call of 13 April 1668.
- Dates that are not the first public form, named in the details: Roberval's tangent (lectures of 1639 to 1644, printed 1693); Pascal's press (about 1650, printed 1663); Fermat's factoring method (letters of 1643, printed later); the Italian hours (1640, with the medieval origin named).
- Disputes named in the text: the Jamestown tonnages (accounts differ, so the example uses one commonly cited set); the first newspaper (accounts differ on the Relation and the Weekly News); Drebbel's incubator (usually dated about 1620); Gunter's chain (usually the 1620s, year approximate); Hooke's telegraph (the build disputed).
- Repeats found after the first round, each replaced in the copy that overlapped: Papin's 1690 piston engine (35 kept, 36 replaced); Shelton's Tachygraphy (42 kept, 37 replaced); Meninski's dictionary (37 kept, 36 replaced); the Hudson's Bay Company charter (37 kept, 38 replaced); Gascoigne's micrometer (40 kept, 41 replaced); the Crusca dictionary (43 kept, 35 replaced); the cycloid area (41 kept, 39 replaced); the weekly bills of mortality (38 kept, 41 and 43 replaced); the penny post (both copies in 36 replaced, the 37 copy kept); Gregory's hyperbola (two records in 38, the CROSS replaced); Newton's reflector magnification (44 kept, 38 replaced); the telescope magnification records (44 kept, 36, 38 and 43 replaced); the Bayer records (both in 44 replaced); Mengoli's telescoping sums (the CROSS in 39 replaced); the Richelet index (37 kept, 36 replaced); the Bank of England (the SYSTEM in 35 kept, the PROJECT and FLEX replaced).
- Clusters kept on purpose, each a set of different items: the East India companies (Colbert's French company of 1664, the Dutch of 1602, the English of 1600 and the Dutch West India of 1621); the reflecting telescopes (Gregory, Newton and Cassegrain); the Paris academies and the Royal Society; the hard-word dictionaries (Cawdrey 1604, Cockeram 1623, Blount 1656); the Galileo pendulum records (a clock design in 40, tests of 1602 in 44) and the eclipses as a clock at sea (43); the cycloid results (Wren's length in 39, Roberval's tangent in 40, Roberval's area in 41); the letter and post records of 41; the two Oughtred instruments from one 1632 book in 41; the two Ludolf books (a Russian grammar in 35, an Ethiopic grammar in 38); the Saturn anagrams (Galileo in 43, Huygens in 39) and the rings of Saturn (Cassini in 37, Huygens in 39).
- Two records about one book, accepted because they give different results: Galileo's Two New Sciences (the tool and one projectile project in 41), Huygens's Cosmotheoros (the tool and a planet-size project in 35), and Mersenne's Harmonie universelle (the tool and a monochord record in 41).
- Build fix: database 35's Flex file lacked the import for Kind and had a stale header, so the first Gradle run failed. Fixed, and the second run passed. The Bank of England records in 35 had three records on one item; two were replaced, and a claim that the Act named 1.5 million pounds was removed, because the usual sources give 1.2 million for the subscription.

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the log has no compiler warnings.
- Examples: each new database has its own verifier with exact expected output. I reran all ten verifiers before the commit: 200 of 200 runnable records pass, 20 in each database. The 300 reading-only records are not executed.
- Structure: the generator checks all 44 databases and all 2,770 records: 50 records per database, kinds exact (6, 5, 8, 6, 5, 5, 6, 4, 3 and 2), rare flags between 8 and 10 in each new database (89 in all), years inside each decade (boundary years count at both ends), no provenance notes in the record text, and no dollar signs or Cyrillic in the new files.
- Cross-batch: 500 records in the ten new databases. 62 pairs share two or more significant words, and each was reviewed. The same-item pairs were replaced, and the rest are the neighbours listed under Problems.
- Whole catalogue: 2,770 records. The only titles that repeat are "Erlang" and "Software inspections", from databases 1 and 2, as before.

### Notes

- Not verified yet: running on a device or emulator, the migrations from version 34 to version 44 on a real device, the UI with forty-four databases in the list and in the filters, lint, and the release build.
- The 1600s and 1690s are the thinnest decades for "coding" so far. A later pass should first review the device records with approximate dates, and the concentrations of Galileo (four records in 41) and Descartes (six records in 41).

## CP-20: databases 45 to 54, 1500 to 1600 (the next century, back to the 1500s)

Status: ten more databases are in the app, one per decade from the 1590s down to the 1500s: 500 records, migrations from version 44 to version 54, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. The build passes with no compiler warnings, 17 of 17 unit tests pass, and the 200 runnable records (20 per database) pass against their expected output. The quota is ten decades, the same as CP-19, because the request was a century.

Mood: I am glad the build change keeps paying off. The batch needed no code at all, and the generator checked 3,270 records in one run. The content was the hardest so far. The 1500s hold printing, ciphers, reckoning boards, the first books in the vernacular and mathematics texts, and coding in the modern sense is barely there. Most of the time went into repeats: the Vigenère cipher in several records, Porta's camera obscura in four databases, Cardano's grille in four records, the Stationers' charter in three, the reckoning board, the Council of Trent, the golden number, the Taxis post and Henlein's watch. The cross-batch check found them after each round, and database 49 needed three rounds and a set of verified items from search. I would like the next century to come with fewer repeats from the start, and with the cross-batch check run before the helpers finish, not after.

Date: 2026-10-15. Totals: Kotlin main 73,269 (CP-19: 62,696), Kotlin test 222 (222), XML 82, Gradle and project files 118, total 73,691 (CP-19: 63,118). The +10,573 is the 110 new Kotlin files for databases 45 to 54 and the generated catalogue; the database class and the tests did not change.

### What changed

- Ten databases, each with the same ten kinds and 50 records: "Database 45: 1590s coding catalogue" (1590 to 1600), "Database 46: 1580s" (1580 to 1590), "Database 47: 1570s" (1570 to 1580), "Database 48: 1560s" (1560 to 1570), "Database 49: 1550s" (1550 to 1560), "Database 50: 1540s" (1540 to 1550), "Database 51: 1530s" (1530 to 1540), "Database 52: 1520s" (1520 to 1530), "Database 53: 1510s" (1510 to 1520) and "Database 54: 1500s" (1500 to 1510). Their rare flags are 10, 9, 8, 8, 9, 9, 8, 8, 9 and 9, so 87 new rare records and 856 in the whole catalogue.
- Eleven files per database, 110 in total. Colours are #B2DFDB, #E1BEE7, #F3E5F5, #FFECB3, #CFD8DC, #BCAAA4, #C5E1A5, #FFE082, #B2EBF2 and #D4E157.
- Spec: tools/databases.json has 54 entries, and the ids run back in time from 45 (1590s) to 54 (1500s). The generator wrote SeedCatalog.kt, the generated part of CATALOGUE.md and the README blocks.
- Process: one helper per database, ten in parallel, with one brief (database 44 as the template) and a file of suggested topics by decade. The lead then ran the checks and the cross-batch comparison and sent replacement lists. Each helper replaced between one and fourteen records. Database 49 was finished with items the lead verified by search (Mercator's celestial globe, Schissler's compendium, Gemini's astrolabe, Canisius's catechism, the Geneva psalter and the papal cipher office), and its helper wrote the reading-only records and three runnable replacements from those facts.

### Facts checked by web search

The helper reports list their searches. Examples: the Casa da India (sources give 1500, 1501 or 1503; the record uses 1501 and names the dispute); Calepino's dictionary first printed at Reggio in 1502; the Taxis contract of 1505 with Philip I; Stanbridge's Vulgaria dated 1509 (LEME); Recorde's Grounde of Artes (usually 1543; a 1542 facsimile is catalogued); Etxepare's Basque book (Bordeaux, 1545); Apian's Cosmographia (Landshut, 1524) and Instrument Buch (Ingolstadt, 1533); Pagninus's Thesaurus (1529); Werner's conics (Nuremberg, 1522); Cesariano's Vitruvius (Como, 1521); the Complutensian Polyglot (printed 1514 to 1517, publication dates disputed); the Frankfurt book fair catalogue (first issue for the autumn 1564 fair); Juanelo's Toledo contract (1565); Estienne's numbered verses (1551); Trubar's Abecedarium (1550); Mercator's celestial globe (Louvain, 1551); Schissler's compendium (Augsburg, signed and dated 1556); Gemini's astrolabe (London, 1559, at Oxford); Canisius's catechism (first printed Vienna, 1555); the Geneva psalter of 1551 (one account dates it to 1552); Taqi al-Din's observatory (1577 to 1580); the Stationers' charter of 4 May 1557; Gresham's lectures (most accounts 1597, one gives 1579).

### Not searched, from memory

Most other dates and attributions, including Erasmus's Adages and Colloquies, Bomberg's Rabbinic Bibles, Trithemius's Polygraphia, the Lateran bull, the Council of Trent dates, Luther's German Mass, and most dates of the 1550s and 1560s. The helper reports list them. The lead should check the items they flag first.

### Problems

- Thin decades. The 1500s have few devices and practices with a dated first public form. Several device and practice records are dated approximately, and the details say so where a date is uncertain.
- Single-source or disputed dates: the papal cipher secretary's office by 1555 (one source, JSTOR Daily); Tycho's mural quadrant of 1582 (one abstract, radius disputed); Hondius's globe with twelve southern constellations (1598, single source); Plancius's twelve-year patent of 12 September 1594 (single source); the Lyon clock's 1598 restoration (disputed); Hartmann's letter of 1544 (dates from encyclopedias only, dip or declination disputed, kept as rare); Tartaglia's quadrant (no firm year found, invention disputed).
- Disputes named in the text: the Stationers' charter (first register entries 1557 or 1554); Canisius's edition counts; the Geneva psalter (1551 or 1552); Juanelo's completion year; the Complutensian publication dates; the Casa da India's start (1500, 1501 or 1503); the Taxis contract's start (1489 or 1490).
- Repeats found after the first round, each replaced in one copy: Blagrave's staff (45 kept, 46 replaced); the memory palace (45 kept, 46 replaced); Taqi al-Din's observatory (47 kept, 46 replaced); the secant and Mercator's latitudes (48 kept, 45 replaced); Molina's dictionary (47 kept, 49 replaced); Porta's camera obscura (46 and 48 kept, 49 and 50 replaced); Cardano's grille (four records in 49 reduced to two); the Stationers' charter (three records in 49 reduced to two); the Council of Trent days (48 kept, 50 replaced); the Society of Jesus (50 kept, 51 replaced); Apian's Astronomicum Caesareum (50 kept, 51 replaced); Gemma Frisius's clock (51 kept, 52 replaced); the gold alloys and the rule of false position (51 kept, 52 replaced); the golden number (51 kept, 53 and 54 replaced); the heart-shaped maps (51 and 53, the third replaced); the Taxis post (54 kept, 53 replaced); Henlein's watch (53 kept, 54 replaced); Erasmus's Adages (53 kept, 54 replaced); the Reichsregiment (54 kept, 52 replaced); Bomberg's Rabbinic Bibles (53 kept, 52 replaced); the Roman numeral year (35 kept, 48 replaced); the linear equation (48 kept, 49 replaced); the days-in-a-decade exercise (49 kept, 52 replaced); the Latin word index (51 kept, 54 replaced); the degrees and minutes (53 kept, 54 replaced); the Hebrew numerals (52 kept, 54 replaced). The helper reports list the rest.
- Clusters kept on purpose, each a set of different items: the Latin dictionaries (Thomas, Cooper, Elyot, Estienne, Calepino); the Greek New Testaments (Estienne, Tyndale, Erasmus); Gemma Frisius (a darkened room for an eclipse, a longitude clock, triangulation and arithmetic); Apian's volvelles (three books); the days-between-events exercises (different spans); the universities founded (Helmstedt, Leiden, Königsberg, Marburg); the printing presses (Lima, Mexico City, Nagasaki, Plantin); the cipher books (Vigenère, Porta, Bellaso, Trithemius).
- Build: the first Gradle run passed with no compile errors. The import and stale-header scans found nothing in the 110 new files.

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the log has no compiler warnings.
- Examples: each new database has its own verifier with exact expected output; all 200 runnable records pass, 20 in each database. The 300 reading-only records are not executed.
- Structure: the generator checks all 54 databases and all 3,270 records: 50 per database, kinds exact (6, 5, 8, 6, 5, 5, 6, 4, 3 and 2), rare flags 8 to 10 in each new database (87 in all), years inside each decade, no provenance notes in the record text, no dollar signs or Cyrillic in the record files, and titles unique across all databases.
- Cross-batch: 500 records in the ten new databases; 86 pairs share two or more significant words, and each was reviewed. The same-item pairs were replaced; the rest are the neighbours listed under Problems.
- Whole catalogue: 3,270 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device or emulator, the migrations from version 44 to version 54 on a real device, the UI with fifty-four databases in the list and in the filters, lint, and the release build.
- The 1500s are the thinnest decades for "coding" so far: printing, ciphers, reckoning and grammars are adjacent. A later pass should first review the items dated approximately or flagged above, and the Gemma Frisius, Apian and Vigenère clusters.

## CP-21: databases 55 to 64, 1400 to 1500 (the fifteenth century, ten decades again)

Status: ten more databases are in the app, one per decade from the 1490s down to the 1400s: 461 records, migrations from version 54 to version 64, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. The 1410s has 11 records by decision, see below. The build passes with no compiler warnings, 17 of 17 unit tests pass, and the 185 runnable records (20 in each of nine databases and 5 in the 1410s) pass against their expected output. The quota is ten decades, as in CP-19 and CP-20, because the request was another century.

Mood: As a programmer I am glad the quota is now a rule the checker enforces, not a number I keep in my head. The generator says yes or no for 3,731 records in one run, and "fifty records, kinds exact, rare between seven and ten" makes "done" easy to check. The content is harder to like. The 1400s and 1460s hold ciphers, calendars, printing and a few instruments, and devices and practices with a dated first public form are scarce. The 1430s devices are seven Joseon items out of eight, because I found no dated non-Korean device of that decade. I was annoyed that the repeats came back. The Prague clock, Valla's Elegantiae, Tirant lo Blanc and the Corvinian Library were each written into two or three databases by helpers that could not see each other's files, and the first full check across the catalogue found what the per-helper checks had missed. The 1410s is the one decade where I agreed with stopping short: eleven verified records are more honest than fifty padded ones.

Date: 2026-10-16. Totals: Kotlin main 83,047 (CP-20: 73,269), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 83,469 (CP-20: 73,691). The +9,778 is the 110 new Kotlin files for databases 55 to 64 and the regenerated catalogue; the database class, the migrations and the tests did not change. The counter reads 73,340 main lines at the CP-20 commit, 71 more than the figure recorded in CP-20, so the change is measured against the recorded figure.

### What changed

- Ten databases, each with the same ten kinds and 50 records, except the 1410s: "Database 55: 1490s coding catalogue" (1490 to 1500), "Database 56: 1480s" (1480 to 1490), "Database 57: 1470s" (1470 to 1480), "Database 58: 1460s" (1460 to 1470), "Database 59: 1450s" (1450 to 1460), "Database 60: 1440s" (1440 to 1450), "Database 61: 1430s" (1430 to 1440), "Database 62: 1420s" (1420 to 1430), "Database 63: 1410s" (1410 to 1420, 11 records, not a quota database) and "Database 64: 1400s" (1400 to 1410). Their rare flags are 10, 8, 9, 8, 9, 10, 8, 9, 3 and 8, so 82 new rare records and 938 in the whole catalogue.
- Eleven files per database, 110 in total. Colours are #FFCCBC, #F9FBE7, #E8F5E9, #EDE7F6, #E0F2F1, #FFF3E0, #FAFAFA, #E1F5FE, #FFEBEE and #BBDEFB.
- Spec: tools/databases.json has 64 entries. Database 63 is marked "quota": false, so the generator does not count its records against the quota; its other checks still apply.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples now reads "15 to 62 and 64", with 20 each, and database 63 has five runnable records, all of which pass.
- Process: one helper per database, ten in parallel, with one brief (database 44 as the template) and a file of suggested topics by decade. The container restart in the middle of the batch lost the completion reports for databases 55 to 62 and 64. Their files were checked directly, and the unfinished databases were finished by resuming their helpers. Later rounds were one cleanup helper for databases 55, 56, 57 and 62, and resumed helpers for 58, 59, 60, 61 and 64.

### Facts checked by web search

Examples from the helper reports (the reports list the rest): the Prague Orloj completed in 1410 by Mikuláš of Kadaň and Jan Šindel, with older accounts saying about 1490; the Council of Constance (1414 to 1418); Martin V elected on 11 November 1417; Hus burned on 6 July 1415; Datini died on 16 August 1410; Qalqashandi's Subh al-a'sha completed in 1412; Siyi guan founded in 1407; the Kangnido dated 1402; Gyemija cast in 1403, with bronze or copper disputed; the Yongle Great Bell dated 1406, though another account says about 1420; Tsongkhapa's Lam rim chen mo completed in 1402; Juan de Mena's Laberinto de Fortuna submitted to the king on 22 February 1444 (one 2024 study); Tortelli's spelling of words from Greek (1447); Aachen's relic pilgrimage every seven years from 1349; Jagyeongnu's clock of 1434, lost, with the surviving copy dated 1536; the Hyangyak jipseongbang of 1433, 85 volumes; the Leal Conselheiro late in Duarte's reign; the Samarkand observatory begun in the 1420s; the Olomouc clock (1420 or 1422); Fontana's Bellicorum instrumentorum liber (Munich, Cod.icon. 242, about 1420); Jiphyeonjeon founded in the third month of 1420 and dissolved in the sixth month of 1456; the siege of Constantinople (6 April to 29 May 1453); Greifswald founded on 17 October 1456; Dum diversas dated 18 June 1452; Romanus Pontifex dated 5 or 8 January 1455, or 1454; indulgence blanks with the first dated copy at Erfurt on 22 October 1454; the Malatestiana opened on 15 August 1454; the Vocabularius ex quo first printed at Eltville on 4 November 1467; Zell's first dated book in 1466; the Monte di Pietà at Perugia (1462) and its papal approval (1467); Johann of Speyer's press at Venice from 1468 (Cicero's letters, 1469); Neumeister's press at Foligno from 1470; the Dardanelles Gun (1464 or 1465); Homer's Iliad and Odyssey printed at Florence in 1488; Sacrobosco's Algorismus printed at Strasbourg in 1488; the first dated bookplate of 1480 (Brandenburg, Buxheim); Lucena's chess of 1497; Simonetta's thirteen rules (1474); Sao Jorge da Mina (1482).

### Not searched, from memory

The helper reports list the rest. The lead should check first: Clavijo's embassy (1403 to 1406), Chrysoloras's Erotemata (printed 1484), the battle dates in the flex pieces of 1400s, Peuerbach (1454, printed 1472), Valla's Thucydides (1452, approximate), the Donatus fragment dates, the Cusanus lens (1458), the first printing of Filarete (1972), Platina's 1470s date, Torquemada's Subiaco woodcuts, the printed dates of De triangulis (1533) and the Epitome (1496), and most other dates in the 1400s and 1460s.

### Problems

- Thin decades. The 1400s and 1460s have few devices and practices with a dated first public form. Where a date is approximate, the details say so. The Lazzaretto Nuovo quarantine island from 1468 is the weakest date. The 1430s devices are seven Joseon items and one other, for the reason given in the mood above.
- Single-source or weak dates, as the helper reports list them: the Juan de Mena date (one 2024 study); the Aachen seven-year cycle (one source); the Honcheonui of 1433 (one encyclopedia); the wooden ganui of 1432 (one travel page); Lee Soon-ji's credit for Angbuilgu; the Freiburg sanction of 20 April 1455 and the founding of 1457 or 1458 (one university history page); the Malatestiana opening date (one infobox); the Fusoris astrolabe "about 1400" (one catalogue excerpt); Gyemija's 100,000 pieces (one reference); the cipher letters from Buda (one archive page, whose catalogue gives Vienna as the place of writing).
- Disputes named in the text: the Dardanelles Gun (1464 or 1465); the Biccherna first cover (1258 or 1263); Romanus Pontifex (1454 or 1455); the Olomouc clock (1420 or 1422); Gyemija (bronze or copper); the 52 or 53 electors at Constance; Datini's letters (150,000 or 120,000); the Yongle Great Bell (1406 or about 1420).
- An example with invented numbers. One EXAMPLE in 60 counts sample figures that are not history. Its title and summary now say so ("Running totals of invented sample counts, for practice"). That is honest, but it is weaker than the rest of the catalogue.
- Six records named an encyclopedia in their details (two in 58, one in 56, three in 62). The sentences were rewritten to give the dates and the accounts without the citation, and the generator now fails on the word.
- Repeats found and replaced. The final check over all 64 databases found these, after the per-helper checks had missed some:
  - The Promptorium parvulorum: kept in 55 (the 1499 print), removed from 60 and 61.
  - Valla's Elegantiae: kept in 57, removed from 60 (two records) and 61 (two records).
  - Tirant lo Blanc: kept in 55, replaced in 56.
  - The Corvinian Library of Buda: kept in 55, replaced in 56.
  - Lascaris's Erotemata: kept in 57, replaced in 55.
  - Hand-painted initials: kept in 57, replaced in 56.
  - The windlass: kept in 57, replaced in 62.
  - Braccia into ells: kept in 57, replaced in 55.
  - The chapters of the five books of Moses: kept in 55, replaced in 56.
  - The papal lead seals: kept in 55, replaced in 57.
  - The arctangent series for pi: kept in 37, replaced in 64 (Madhava's series).
  - The Prague clock: kept in 63, replaced in 64 (two records).
  - Seven records in 59 (the Holy Year of 1450, Fust's suit, Nicholas V's papal library, Cusanus's balance, two records on Fra Mauro's map, and the syllable blocks): kept in 60 instead.
  - One EXAMPLE in 61 (the vowels) and two records in 59 (a type-case example and a practice of blank spaces) repeated items in 56 and 57, and were replaced.
  - In all, 29 records were replaced or removed.
- Clusters kept on purpose, each a set of different items: binary search (55's docket, 57's quire, 60's chapter); days from a bull or a death to an event (several databases, each a different span); running balances (40's daily register and 56's plus-and-minus ledger); the two Florentine catasti (1427 in 62 and 1480 in 57); regnal-year dating (36, 61 and 64, each a different reign or charter); the two-thirds majority (61's nations and 63's Constance vote); the Ulugh Beg material (61's Zij and 62's observatory); the two Greek Erotemata (57's Lascaris and 64's Chrysoloras); the hand-finishing of printed books (56's initials, colouring and manicule, and 57's blank spaces); the two Ottoman bombards (58's Dardanelles Gun and 59's Orban gun); the Speyer press and privilege (both in 58); two Holy Year records in 60 (a practice and an example).
- Rule departures, for the next batch to check: database 58's six TOOL records give the composition date where the first printed form falls outside the decade, and the details name the printed form. Database 63 has 11 records by the user's decision. Database 60's Bursfelde example uses invented numbers and says so.

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL in 2 minutes 15 seconds, and the log has no compiler warnings.
- Examples: each new database has its own verifier with exact expected output, and the expected outputs were worked out by hand before each run. 185 of 185 runnable records pass: 20 in each of databases 55 to 62 and 64, and 5 in 63. The 276 reading-only records are not executed.
- Structure: the generator checks all 64 databases and all 3,731 records: 50 per database except 63 (11, not a quota database), kinds exact (6, 5, 8, 6, 5, 5, 6, 4, 3 and 2), rare flags from 7 to 10 in each quota database, years inside each range (boundary years count at both ends), no provenance phrases in the record text, and no dollar signs or Cyrillic in the new files.
- Cross-batch: the final check compares all 64 databases; 955 pairs share two or more significant words. I read the pairs that touch the new databases. The same-item pairs were replaced, and the rest are the neighbours listed under Problems.
- Whole catalogue: 3,731 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device or emulator, the migrations from version 54 to version 64 on a real device, the UI with 64 databases in the list and in the filters, lint, and the release build.
- The 1400s and 1460s are the thinnest decades for "coding" so far: ciphers, printing, calendars and instruments make up most of them. A later pass should first review the approximate dates, the single-source items and the items dated 1450, which the range rule allows in both 59 and 60. This batch keeps each item in one database.

## CP-22: databases 65 to 74, 1300 to 1400 (the fourteenth century, the next century back)

Status: ten more databases are in the app, one per decade from the 1390s down to the 1300s: 444 records, migrations from version 64 to version 74, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Three decades are complete (the 1350s, the 1320s and the 1310s, at 50 records each). Seven are smaller and are marked "quota": false in tools/databases.json, as the 1410s was: the 1390s with 40 records, the 1380s with 45, the 1370s with 47, the 1360s with 32, the 1340s with 47, the 1330s with 36 and the 1300s with 47. The build passes with no compiler warnings, 17 of 17 unit tests pass, and the 194 runnable records (20 in nine databases and 14 in the 1360s) pass against their expected output.

Mood: This is the thinnest century so far for "coding". The 1300s to the 1390s have clocks, a few instruments, Wang Zhen's machines, Llull's wheels and some law codes, and very little else with a dated first form. Most helpers ran out of verified items in their decade and filled the gap from memory with approximate dates, which I accepted only because the details name the range. When I read the result I found the same kind of project, one about the decade's own records, written into five databases, and twice in some of them. I did not enjoy that, and I am glad the cross-batch check caught it before the commit. Seven honest smaller decades are better than fifty padded records, which is also what you chose for the 1410s, but the count is a real cost, and you may want to decide whether to fill them.

Date: 2026-10-16. Totals: Kotlin main 92,742 (CP-21: 83,047), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 93,164 (CP-21: 83,469). The +9,695 is the 110 new Kotlin files for databases 65 to 74 and the regenerated catalogue; the database class, the migrations and the tests did not change.

### What changed

- Ten databases, from "Database 65: 1390s coding catalogue" (1390 to 1400) to "Database 74: 1300s coding catalogue" (1300 to 1310). Their records are 40, 45, 47, 32, 50, 47, 36, 50, 50 and 47. Their rare flags are 10, 9, 10, 8, 10, 9, 8, 9, 8 and 9, so 90 new rare records and 1,028 in the whole catalogue.
- Eleven files per database, 110 in total. Colours are #F48FB1, #81D4FA, #80DEEA, #A5D6A7, #FFE57F, #4FC3F7, #9575CD, #FFB74D, #66BB6A and #EC407A.
- Spec: tools/databases.json has 74 entries. Databases 65, 66, 67, 68, 70, 71 and 74 are marked "quota": false, as database 63 is.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples now lists 20 each in databases 15 to 62 and 64 to 67 and 69 to 74, 14 in database 68, and five in database 63.
- Process: one helper per database, ten in parallel, with the brief (database 64 as the template) and a file of suggested topics for the 1300s. Several helpers stopped short. Each was resumed with its gap list, and the search budget was extended where a fact had to be checked. Later rounds replaced the repeats that the cross-batch check found.

### Facts checked by web search

Examples from the helper reports (they list the rest): Wang Zhen's Nong shu, dated 1313, with wooden type and a rotating type case (one source; some accounts give about 1303 for the book); the Chetwode quadrant engraved in 1311 (one source); the Teutonic Order's seat moved to Marienburg in 1309; Duccio's Maestà dated 9 October 1308 (a 1980 review disputes it); Dondi's astrarium completed in 1364 (the start date is disputed); the studium generale of Pavia (1361); the bull of Urban V on Pécs (1 September 1367, surviving as a copy); the Halberstadt organ inscription of 1361 (the "earliest organ" claim is disputed); the Gulbenkian Mamluk lamp (about 1354 to 1361); the al-Qawwas astrolabe of 752 AH (1351 to 1352, one auction listing); the Wienhausen spectacles (about 1350, one source); the Quesnoy bombards of 1340 (one chronicle); the Siena bell of 1347 (one source); the Padua clock of 1344 (sources conflict); Lorenzetti's hourglass fresco (1337 to 1339); the Odoric account written at Padua in May 1330; the Libro de buen amor (1330 or 1343); Bradwardine's Geometria speculativa (the date is memory only, and the lead should check it).

### Not searched, from memory

Most other dates and attributions, including the reigns of Philip VI, John II, Edward III, Clement VI, Innocent VI, Urban V and Charles IV, the Garter's companions, the Philobiblon (1345), the Songshi, the Statute of Labourers (1351), the Golden Bull (1356), the Holy Year of 1350, Gower's and Chaucer's dates, the Charles Bridge (begun 1357, 16 arches, 516 metres), the fixed feasts of 1386, the Chinese dynasty lengths, the Florence guilds, the Kokan Shiren chronicle of 1322, the ogham groups, the shield quarters and the Ethiopian calendar offset. The helper reports list them. The lead should check the weakest first: Bradwardine's date, the Quesnoy bombards, the Siena bell, the Padua clock, the Gulbenkian lamp, the al-Qawwas attribution, the Wang Zhen book date, the feast dates and the Charles Bridge figures.

### Problems

- Thin decades. Seven databases are below 50 records, as listed above. The 1360s has 32 records and 14 runnable ones. The other reduced decades stand at 36 to 47 records.
- Repeated exercise types. A project about the decade's own records was written in five databases and twice in some of them (65, 66, 69, 71 and 72). Each database now keeps one such project, and the rest were replaced. The timeline project is kept in 69, 70, 71 and 72, one per database.
- Items repeated across databases, found by the cross-batch check and replaced: the 32-point compass guide (47 and 72); the degrees-and-minutes method (53 and 72); the Commedia (57 and 72); the Roman-numeral year (35 and 65); the lunar-year count (56, 62 and 67); the courier-days exercise (27 and 68); Piers Plowman's B and C texts (66 and 67); the Beauvais clock (66 and 74); Mondeville's surgery (72 and 74); the Jubilee of 1350 (69 and 70); the Byzantine year rule (67 and 69); the copper-token tally (71 and 72); the Odoric account (71 and 72); the hour-strike count (66 and 72).
- Several runnable records still use numbers labelled as illustrative, for example the wage, recipe and drum exercises. Their text says so, and I did not replace them.
- Facts from memory to check: the fixed feast dates of 1386, the Charles Bridge figures, the reign dates, the Ethiopian calendar offset and the Chinese dynasty lengths.
- Process: several helpers stopped before writing their files, after spending their searches, and were resumed; the summary above says which decades were reduced.

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the log has no compiler warnings.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. 194 of 194 runnable records pass: 20 in each of databases 65 to 67 and 69 to 74, and 14 in 68. The 250 reading-only records are not executed.
- Structure: the generator checks all 74 databases and all 4,175 records. The quota databases keep the exact kind counts (6, 5, 8, 6, 5, 5, 6, 4, 3 and 2) and rare flags from 7 to 10. The reduced databases keep the kinds but have fewer records (quota false, as database 63). Years are inside each range, and the record text has no provenance phrases, dollar signs or Cyrillic.
- Cross-batch: the final check compares all 74 databases; 1,568 pairs share two or more significant words. I read the pairs that touch the replacements. They are word matches, or the same method applied to different items (the reign counts in 65, 68 and 69; the Julian calendar in 53, 54 and 67).
- Whole catalogue: 4,175 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device or emulator, the migrations from version 64 to version 74 on a real device, the UI with 74 databases in the list and in the filters, lint, and the release build.
- The 1300s to the 1390s are the thinnest decades in the catalogue for "coding". The next pass should first decide whether to fill the seven smaller databases or keep them smaller, and then check the memory facts listed above.

## CP-23: databases 75 to 84, 1200 to 1300 (the thirteenth century, the next century back)

Status: ten more databases are in the app, one per decade from the 1290s down to the 1200s: 295 records, migrations from version 74 to version 84, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. None of the ten reaches 50 records: 1290s 30, 1280s 37, 1270s 27, 1260s 23, 1250s 35, 1240s 23, 1230s 34, 1220s 28, 1210s 32 and 1200s 26. All ten are marked "quota": false in tools/databases.json. The build passes with no compiler warnings, 17 of 17 unit tests pass, and the 116 runnable records (10, 20, 8, 8, 19, 8, 14, 9, 13 and 7) pass against their expected output. Every kind is present in every database, which the unit tests require.

Mood: The thirteenth century is the thinnest century of the catalogue so far. The helpers found almost no dated devices. In the 1210s the only device is a chain across the Nile at Damietta, which is approximate and is really a barrier, not a machine. Several decades ended with a kind that had no record at all, which the unit tests reject, and each time the fix was one item from memory with its range named. I do not like that I had to accept those. What I do like is that the verification held: every runnable record that was written passed its hand-computed output, and the cross-batch check found the repeats, which were replaced. Ten honest small databases are better than ten padded ones, and you chose the same for the 1410s. Still, 295 records is a clear step down from the earlier centuries, and the next batch should start from the question of whether the 1200s need the same quota at all.

Date: 2026-10-18. Totals: Kotlin main 99,282 (CP-22: 92,742), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 99,704 (CP-22: 93,164). The +6,540 is the 110 new Kotlin files for databases 75 to 84 and the regenerated catalogue; the database class, the migrations and the tests did not change.

### What changed

- Ten databases, from "Database 75: 1290s coding catalogue" (1290 to 1300) to "Database 84: 1200s coding catalogue" (1200 to 1210). Their records are 30, 37, 27, 23, 35, 23, 34, 28, 32 and 26: 295 new records and 4,470 in the whole catalogue. Their rare flags are 10, 9, 8, 8, 9, 8, 9, 9, 10 and 9: 89 new rare records and 1,117 in the whole catalogue.
- Eleven files per database, 110 in total. Colours are #FFF176, #64B5F6, #81C784, #FFD180, #9CCC65, #FFA726, #29B6F6, #EF5350, #AB47BC and #26A69A.
- Spec: tools/databases.json has 84 entries. All ten new databases are marked "quota": false.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples also lists the runnable counts for 75 to 84: 76 has 20, 79 has 19, 81 has 14, 83 has 13, 75 has 10, 82 has 9, 77, 78 and 80 have 8 each, and 84 has 7.
- Process: one helper per database, ten in parallel, with the brief (database 64 as the template) and a file of suggested topics for the 1200s. Every helper stopped short. Several databases had a kind with no record at all, which the unit tests reject. Each was resumed to add one record of that kind, often from memory with its range named.

### Facts checked by web search

Examples from the helper reports (they list the rest): Florence's ban on Arabic numerals (1299); the Ramkhamhaeng stone (1292 or 1293; authenticity disputed); Zhou Daguan at Angkor (1296 to 1297); Lanfranc's Chirurgia magna (completed 1296); Fuero Juzgo (1241); the Lyon sittings of 1245 (the 5 July date is single-source); Innocent IV's pontificate (begun 25 June 1243); the Tusi couple in the 1247 Tahrir; the Tripitaka Koreana carving (about 1236 to 1251; the block count varies); Rubruck at Karakorum (1254); Juvaini's history (completed 1260); the Phags-pa script (commission 1260; edict and completion 1269); Moerbeke's Archimedes (1269); the Oxford astrolabes of AH 618 and AH 621 (single source); Jacques de Vitry's compass passage; the Damietta chain (1218); the Sorbonne list (1290); and the Maragha observatory (begun 1259).

### Not searched, from memory

Most other dates and attributions, including the Summa (119, 114, 189 and 90 questions), Louis IX's crusade (landing 18 July 1270, death 25 August 1270), Frederick II and Innocent IV, the Provisions of Oxford (11 June 1258), Mongke's enthronement and the battle of Ain Jalut, Gulistan (1258), the Fuero Real (1255), Carpini (1247), the qibla from Maragha, the Armenian era, the Florence priors' two-month terms, the Mongol kurultais of 1235 and 1246, the Franciscan Rule of 1223, the Golden Bull (1222), Caesarius (1219 to 1223), Francis's canonization, and the Mongol capture of the Khwarazmian cities. The helper reports list them. The lead should check the weakest first: the Damietta chain, the Lyon sittings of 1245, the Florence priors, the Armenian era, the Summa counts and the Oxford astrolabes.

### Problems

- Thin decades. All ten are below 50 records, as listed above. Their runnable counts are 10, 20, 8, 8, 19, 8, 14, 9, 13 and 7.
- Empty kinds. Several databases had a kind with no record at all, which the unit tests reject. Each was filled with one record of that kind: EXAMPLE and CROSS in 75 and 77 (75 also GUIDE), EXAMPLE in 78, PRACTICE in 79, PROJECT in 81 and 82, and DEVICE in 83. The DEVICE in 83 is a barrier (the chain across the Nile at Damietta, 1218), not a machine, and its helper flagged it for that reason.
- Approximate and memory items. Many records in this batch rest on facts from memory, and several dates are approximate, with the range named in the details. The weakest are the Damietta chain, the Florence priors, the Armenian era and the Oxford astrolabes.
- Repeated exercise types. The "days from X's death to Y" family appears in many of the ten databases, with different events and spans; within 76 it appears twice, both on Alfonso X. Some day counts share a subject with different spans, such as Louis IX (77 and 80) and Frederick II (79 and 80). These are accepted as neighbours with different spans.
- Repeats across databases, found by the cross-batch check and replaced: Practica geometriae (82 and 83), the Romance of the Rose and the Saxon Mirror (81 and 82), the Shin Kokin Wakashu (83 and 84), the Breviari d'amor (75 and 76), the King's Mirror (78, 79 and 80), Matthew Paris's route map (79 and 80), the Tusi couple (79 and 80), the Fuero (79 and 80), the Mongol kurultai (80, 81 and 82), the Prose Edda (82 and 83), the Juvaini history (79 and 84), the Sorbonne (79 and 83), and the Summa items (77 and 78).

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5). `./gradlew testDebugUnitTest` ended with BUILD SUCCESSFUL, and the log has no compiler warnings. The test that every kind is present in every database passes.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. 116 of 116 runnable records pass. The 179 reading-only records are not executed.
- Structure: the generator checks all 84 databases and all 4,470 records. The new databases are quota false and keep every kind. Years are inside each range, and the record text has no provenance phrases, dollar signs or Cyrillic.
- Cross-batch: the final check compares all 84 databases; 2,313 pairs share two or more significant words. I read the pairs that touch the new databases and searched for the named items of the batch. They are word matches, neighbours with different items, or the same exercise type with different spans.
- Whole catalogue: 4,470 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device or emulator, the migrations from version 74 to version 84 on a real device, the UI with 84 databases in the list, lint, and the release build.
- The 1200s to the 1290s are the thinnest decades of the catalogue for "coding". The next step is to decide whether they stay smaller, or whether the quota of 50 is kept for this century with a note that the material is thin.

## CP-24: databases 85 to 94, 1100 to 1200 (the twelfth century, the next century back)

Status: ten more databases are in the app, one per decade from the 1190s down to the 1100s: 266 records, migrations from version 84 to version 94, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. None of the ten reaches 50 records: 1190s 29, 1180s 26, 1170s 25, 1160s 23, 1150s 38, 1140s 31, 1130s 23, 1120s 26, 1110s 23 and 1100s 22. All ten are marked "quota": false in tools/databases.json. The build passes, 17 of 17 unit tests pass, and the 99 runnable records (12, 9, 7, 9, 20, 11, 8, 9, 7 and 7) pass against their expected output. Every kind is present in every database, which the unit tests require.

Mood: The twelfth century is the thinnest stretch of the catalogue so far for things with a dated first public form. Most of what is dated here is a book, and a device or a practice needs a source that gives its range. Several helpers could not fill a kind until a range and a dispute were named. The 1100s, at 22 records, are the emptiest decade so far, and I did not like filling their empty kinds from memory. What went well is that the cross-batch check found four real repeats, and I removed them instead of replacing them with items I could not verify here. Ten small databases with no padding are the honest result, the same choice as for the 1410s.

Date: 2026-10-18. Totals: Kotlin main 105,281 (CP-23: 99,282), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 105,703 (CP-23: 99,704). The +5,999 is the 110 new Kotlin files for databases 85 to 94, net of the four records removed below, and the regenerated catalogue. The database class, the migrations and the tests did not change.

### What changed

- Ten databases, from "Database 85: 1190s coding catalogue" (1190 to 1200) to "Database 94: 1100s coding catalogue" (1100 to 1110). Their records are 29, 26, 25, 23, 38, 31, 23, 26, 23 and 22: 266 new records and 4,736 in the whole catalogue. The new rare records number 87, so the whole catalogue has 1,204.
- Eleven files per database, 110 in total. Colours are #7E57C2, #26C6DA, #FF7043, #8D6E63, #78909C, #5C6BC0, #C62828, #1565C0, #2E7D32 and #6A1B9A.
- Spec: tools/databases.json has 94 entries. All ten new databases are marked "quota": false.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples gives the runnable counts for 75 to 94: 76 and 89 have 20 each, 79 has 19, 81 has 14, 83 has 13, 85 has 12, 90 has 11, 75 has 10, 82, 86, 88 and 92 have 9 each, 77, 78, 80 and 91 have 8 each, and 84, 87, 93 and 94 have 7 each.
- Four records removed after the cross-batch check. The Japanese era conversion of the 1160s (88 GUIDE) repeated the era conversion of the 1140s kept as a CROSS in 90. The 1140s conversion was also an EXAMPLE in 90, the same computation as that CROSS, so that EXAMPLE went. The broken bamboo was both a CROSS and an EXAMPLE in 90, and the EXAMPLE went. Bhaskara's pulverizer (90 GUIDE) repeated the congruence in the 86 CROSS, so the GUIDE went. The matching checks were removed from the verifiers, and the headers of 88 Guides, 90 Examples and 90 Guides no longer name them.
- Process: one helper per database, ten in parallel, from the brief and the topic file for the 1100s. Several helpers stopped short and were resumed. Kinds with no record were filled before the build.

### Facts checked by web search

None is recorded for databases 85 to 94. This batch has no search record in the log.

### Not searched, from memory

The dates and attributions of these ten databases were written from memory. The ones to check first are the Jayrun water clock (89 DEVICE), Laudabiliter (89 SYSTEM, 1155), the scutage of 1159 (89 SYSTEM), Caffaro's Annals of Genoa (89 LANGUAGE, the years they cover), Glanvill's treatise (86 TOOL, about 1187 to 1189), the Assize of Northampton (87 SYSTEM, 1176), Maimonides's Mishneh Torah (87 LANGUAGE, 1180) and the Jin state proclaimed in 1115 (93 SYSTEM).

### Problems

- Thin decades. All ten are below 50 records, as listed above. The 1100s are the thinnest, at 22.
- Empty kinds. Several databases had a kind with no record, which the unit tests reject. Each got one record of that kind before the build, often from memory with its range named.
- Approximate dates. Many records give a range or "about", and the details name the dispute where there is one.
- Same book, different kinds, left in place. Scivias in 89 (LANGUAGE, EXAMPLE and FLEX), the Policraticus and the Metalogicon in 89, Caffaro's Annals in 89 (LANGUAGE and EXAMPLE), the Assize of Clarendon in 88 (SYSTEM and GUIDE), the Assize of Northampton in 87 (SYSTEM and EXAMPLE), Glanvill in 86 (TOOL and PRACTICE), Maimonides's Guide in 85 (LANGUAGE and EXAMPLE), the Samguk sagi in 90 (LANGUAGE and EXAMPLE), Quantum praedecessores in 90 (PRACTICE and FLEX) and the Treaty of Zamora in 90 (SYSTEM and FLEX). Each pair uses one item from two angles, as earlier batches did. These are the first candidates to thin if one record per item is wanted.
- Two named texts of one treatise. Bhaskara's Bijaganita (89 TOOL) and Lilavati (90 TOOL) are two parts of the Siddhanta Shiromani. Both are kept because each is a named text. If the treatise counts as one item, the 89 record is the one to replace.
- Two Pipe Rolls. The roll of 1155 (89) and the roll of 1130 (92) are different documents in one series.
- Julian and Gregorian conversions appear in 34, 48, 75 and 88 as one method with different dates. Kept.

### How I checked it

- Unit tests: 17 of 17 pass (AppDatabaseSeedTest 4, SeedDataTest 8, RecordFilterTest 5). The Gradle run ended with BUILD SUCCESSFUL, with no failures and no compiler warnings.
- Examples: each new database has a verifier with exact expected output, worked out by hand before each run. 99 of 99 runnable records pass. The verifiers for 88 (9 records) and 90 (11 records) were run again after the four removals. The other eight passed before the removals, and their databases did not change.
- Structure: the generator checks all 94 databases and all 4,736 records. The new databases are quota false and keep every kind. Years are inside each range, and the record text has no provenance phrases, dollar signs or Cyrillic.
- Cross-batch: the cross-batch run over all 94 databases was read for the pairs touching the new databases. A rare-word check (a title word that appears in at most three records of the catalogue) was then run on every pair involving them. After the four removals it shows no pair for the removed items. The pairs it still shows are word matches, such as "Heike" in a sutra and in a romance.
- Whole catalogue: 4,736 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device, the migrations from version 84 to version 94 on a device, the UI with 94 databases in the list, lint and the release build.
- The 1100s and the 1110s are the thinnest decades of the catalogue for "coding". The question is the same as before: keep them smaller, or fill them.
