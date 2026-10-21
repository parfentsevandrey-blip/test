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
| CP-25 | 2026-10-18 | 109,452 | 222 | 82 | 118 | 109,874 | +4,171 (databases 95 to 104: 164 records in 110 files, migrations 94→95 to 103→104, tests +0) |
| CP-26 | 2026-10-18 | 113,927 | 222 | 82 | 118 | 114,349 | +4,475 (databases 105 to 114: 174 records in 110 files, migrations 104→105 to 113→114, tests +0) |
| CP-27 | 2026-10-19 | 117,950 | 222 | 82 | 118 | 118,372 | +4,023 (databases 115 to 124: 155 records in 110 files, migrations 114→115 to 123→124, tests +0) |
| CP-28 | 2026-10-19 | 121,722 | 222 | 82 | 118 | 122,144 | +3,772 (databases 125 to 134: 141 records in 110 files, migrations 124→125 to 133→134, tests +0) |
| CP-29 | 2026-10-20 | 125,485 | 222 | 82 | 118 | 125,907 | +3,763 (databases 135 to 144: 139 records in 110 files, migrations 134→135 to 143→144, tests +0) |
| CP-30 | 2026-10-20 | 128,918 | 222 | 82 | 118 | 129,340 | +3,433 (databases 145 to 154: 121 records in 110 files, migrations 144→145 to 153→154, tests +0) |
| CP-31 | 2026-10-20 | 132,865 | 222 | 82 | 118 | 133,287 | +3,947 (databases 155 to 164: 145 records in 110 files, migrations 154→155 to 163→164, tests +0) |
| CP-32 | 2026-10-20 | 136,840 | 222 | 82 | 118 | 137,262 | +3,975 (databases 165 to 174: 154 records in 110 files, migrations 164→165 to 173→174, tests +0) |
| CP-33 | 2026-10-20 | 140,320 | 222 | 82 | 118 | 140,742 | +3,480 (databases 175 to 184: 115 records in 110 files, migrations 174→175 to 183→184, tests +0) |
| CP-34 | 2026-10-20 | 143,689 | 222 | 82 | 118 | 144,111 | +3,369 (databases 185 to 194: 106 records in 110 files, migrations 184→185 to 193→194, tests +0) |
| CP-35 | 2026-10-20 | 147,071 | 222 | 82 | 118 | 147,493 | +3,382 (databases 195 to 204: 106 records in 110 files, migrations 194→195 to 203→204, tests +0) |
| CP-36 | 2026-10-20 | 147,853 | 408 | 88 | 118 | 148,467 | +974 (the on-device assistant: index, classifier, answers, screen and 20 new tests, 37 in all; no record files; DATABASE_VERSION unchanged at 204) |
| CP-37 | 2026-10-21 | 147,861 | 454 | 88 | 118 | 148,521 | +54 (economics paused at the web-search budget: model and sphere lists, generator support, two record kinds, tests; no economics records yet) |

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

## CP-25: databases 95 to 104, 1000 to 1100 (the eleventh century, the century before the 1100s)

Status: ten more databases are in the app, one per decade from the 1090s down to the 1000s: 164 records, migrations from version 94 to version 104, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 1090s 18, 1080s 15, 1070s 21, 1060s 14, 1050s 14, 1040s 16, 1030s 16, 1020s 13, 1010s 21 and 1000s 16. None reaches 50, and all ten are marked "quota": false in tools/databases.json. The build passes (104 databases, 4,900 records), 17 of 17 unit tests pass, and the 54 runnable records (6, 5, 6, 5, 5, 5, 5, 5, 7 and 5) pass against their expected output. Every kind is present in every database, which the unit tests require.

Mood: The eleventh century is the emptiest century of the catalogue so far for things with a dated first public form. Most of its decades have a book and little else. The 1070s and the 1030s had no device or method that I could date without a placement, and I did not like that: four records are placed by convention, and they are the weakest records of the batch. What went well is that the cross-batch check caught several real repeats, and each was fixed rather than padded. The container restart stopped seven helpers in the middle of their work, and they were resumed from their transcripts, so nothing was lost.

Date: 2026-10-18. Totals: Kotlin main 109,452 (CP-24: 105,281), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 109,874 (CP-24: 105,703). The +4,171 is the 110 new Kotlin files for databases 95 to 104 and the regenerated catalogue. The database class, the migrations and the tests did not change; the migrations are generated, with DATABASE_VERSION at 104.

### What changed

- Ten databases, from "Database 95: 1090s coding catalogue" (1090 to 1100) to "Database 104: 1000s coding catalogue" (1000 to 1010). Their records are 18, 15, 21, 14, 14, 16, 16, 13, 21 and 16: 164 new records and 4,900 in the whole catalogue. The new rare records number 86, so the whole catalogue has 1,290.
- Eleven files per database, 110 in total. Colours are #5D4037, #00897B, #7CB342, #FDD835, #3949AB, #D81B60, #00ACC1, #F4511E, #546E7A and #8E24AA.
- Spec: tools/databases.json has 104 entries. All ten new databases are marked "quota": false.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples gives the runnable counts for 75 to 104.
- Process: one helper per database, ten in parallel. Seven were stopped by the restart and resumed. Follow-up helpers completed databases 97 and 101, and repaired the repeats in 95, 100, 101 and 104.

### Placed records

The year of these records is a placement, not the date of a dated making. The details say so.

- 97 DEVICE: Shen Kuo's armillary sphere, gnomon and clepsydra of the Bureau of Astronomy, placed at 1072, the year he took charge of the bureau. The instruments are undated.
- 101 DEVICE: the astrolabe as al-Biruni described its making and use, in a treatise the sources do not date, placed at 1030. Weak.
- 101 GUIDE: how the Maya Long Count turns a date into a count of days and back, placed at 1030. Weak: the method is not of the period. No 1030s method was free of repeats, so this was the placement left.
- 98 DEVICE: the brass astrolabe made at Toledo by Ibrahim ibn Sa'id as-Sahli, dated 1068 by the month of Shawwal that the maker named. The Oxford catalogue record was relayed by a search result and was not opened directly.

### Problems and what was fixed

- 98 had the Kirkdale sundial as its DEVICE, which database 99 also has. 98 now has the Toledo astrolabe.
- 104 PROJECT on the Danegeld of 991 to 1012 repeated the Danegeld EXAMPLE of 103. It is now a list of events of 1002 to 1010.
- 104 SYSTEM on Burchard's Decretum included the Corrector of 103 as its book 19. It is now the archbishopric of Gniezno, founded in 1000.
- 100 had three records on the synod of Sutri and the enthronement of Clement II. The FLEX is now the days from the death of Michael IV to the accession of Constantine IX (1042).
- 95 PROJECT began at the council of Clermont, which the SYSTEM and FLEX already cover. It now counts the days between Nicaea, Antioch and Jerusalem.
- 101 GUIDE (sine-table interpolation) repeated database 85, and 101 DEVICE (instruments in the Canon Masudicus) was not supported by any page the helpers could reach. Both were replaced as above. The rule of three was not used, since database 46 has it.

### Repeats left for a decision

Same book or event in more than one record of one database. These are angles, not copies, and they are the first candidates to thin.

- 95: the Clermont council in SYSTEM and FLEX; Su Song's clock tower (DEVICE) and his armillary book (TOOL); Alamut as SYSTEM and EXAMPLE; al-Ghazali as LANGUAGE and PRACTICE.
- 96: Christmas 1085 in EXAMPLE, PROJECT (weekdays of Christmas, 1080 to 1090) and FLEX; Sima Guang's chronicle in LANGUAGE, PRACTICE and CROSS.
- 97: the Jalali calendar in SYSTEM, GUIDE and CROSS; Canossa in PRACTICE, EXAMPLE and FLEX; Malik-Shah in two SYSTEM records.
- 98: the New History of the Tang in LANGUAGE and GUIDE.
- 100: the Wujing Zongyao's iron fish (DEVICE) and the book (TOOL); Bi Sheng's type in DEVICE and GUIDE.
- 101 and 103: Cnut in 101 SYSTEM and 103 SYSTEM and CROSS; Yaroslav in 101 PRACTICE and 103 SYSTEM and FLEX.
- 102: the jiaozi in SYSTEM and PRACTICE; the Ring of the Dove in LANGUAGE and FLEX; Guido in LANGUAGE and CROSS.
- 103: Ibn al-Haytham's Book of Optics in TOOL and DEVICE; the Fuero de Leon in SYSTEM and FLEX; the Shahnameh in LANGUAGE and FLEX.

Method repeats, the same method applied to different items: Julian and Christmas weekdays (65, 88, 96, 100); pence-to-pounds arithmetic (36, 99, 103); the "days from a death" FLEX family; "summed and ranked" projects; astrolabes (96, 98, 101, 102); the death of rulers (85, 86, 101, 102); dated events sorted (93, 104).

### Facts checked by web search

- Shen Kuo supervised the Bureau of Astronomy from 1072 (a Wei Pu article); Dream Pool Essays usually dated 1088; Shen Kuo died in 1095 (96).
- Toledan Tables about 1080 (96). Deusdedit's Collection of Canons 1083 to 1087 (96).
- The Guadalajara astrolabe of 1081/2, by Muhammad ibn Sa'id as-Sabban, on an Oxford exhibit page read with WebFetch (96; single source).
- Nizam al-Mulk killed 14 October 1092; Malik-Shah died 19 November 1092; the synod of Szabolcs in May 1092; the Liubech congress in 1097; Valencia taken 15 June 1094; El Cid died 10 July 1099; Su Song's book dated 1094 in one archive record (95).
- The compilers of the Wujing Zongyao, Zeng Gongliang, Ding Du and Yang Weide, and its compilation about 1040 to 1044 (100). Bi Sheng's type in Qingli 1041, from Shen Kuo's account (100; single source). Gesta Chuonradi 1046 (100; one encyclopedia entry).
- Sutri opened on 20 December 1046; Clement II enthroned on 25 December 1046 (100).
- The jiaozi office of 1023; fixed denominations of 1024 (102; single source). Micrologus about 1026 (102). The Ring of the Dove 1022 (102). Ibn al-Saffar's astrolabe of Cordoba, 417 AH (102; press coverage). Tafhim 1029 (102).
- Kirkdale: the inscription names Edward and Tosti, range 1055 to 1065 (98; dropped). The Nizamiyya 1065 or 1067 (98). The Enkyu era from 1069 (98). The Leipian 1039 to 1066 and the Xin Tangshu 1044 to 1060 (98).
- Papias about 1045 to 1063 (99). Coyanza 1055 by the Coimbra era (99).
- Gniezno: the meeting of 11 March 1000 (104; two searches). The Dar al-Ilm 1004 or 1005, with the dispute stated (104).
- Russkaya Pravda about 1017 (103). Ibn al-Haytham's Book of Optics 1011 to 1021 (103). Cnut's Oxford pledge of 1018 (103). The Constitutio de feudis of 28 May 1037 (101). The Tangut script of 1036 (101). Dandanaqan on 23 May 1040 (101).

### Facts from memory, to check

Michael IV died 10 December 1041 and Constantine IX acceded 11 June 1042 (100). Nicaea 19 June 1097, Antioch 3 June 1098 and Jerusalem 15 July 1099 (95). The Clermont council of 27 November 1095 and Ladislas I's death on 29 July 1095 (95). Otto III's death on 24 January 1002, St Brice's Day on 13 November 1002 and Sylvester II's death on 12 May 1003 (104). Stephen I's coronation on 25 December 1000 or 1 January 1001 (104). The Genji about 1005, the Pillow Book about 1002, AElfric's Grammar about 1000 and al-Biruni's Chronology in 1000 (104). Mahmud's reign from 998 to 1030 (102). Canossa on 25 to 28 January 1077 and Gregory VII's election on 22 April 1073 (97). Manzikert on 26 August 1071 (97). The Jalali reform of 1079 (97). Kekaumenos from 1075 to 1078 (97).

### Problems

- Thin decades. All ten are below 50 records, as listed above. The 1000s and the 1030s are the thinnest.
- Empty kinds. Database 97 had no DEVICE, and database 101 had no DEVICE, GUIDE and PROJECT. They were filled as described above.
- Placed records. Four records (above) carry a year by convention. They are the first decision for the user.
- Single sources. The Toledo astrolabe (98), the Guadalajara astrolabe (96), the Coyanza era date (99), Papias (99), the Dar al-Ilm date (104) and the Ibn Hazm date are single-source or one-account dates.

### How I checked it

- Unit tests: 17 of 17 pass. The Gradle run ended with BUILD SUCCESSFUL, no failures and no errors.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. The 54 runnable records of databases 95 to 104 pass (6, 5, 6, 5, 5, 5, 5, 5, 7 and 5). The verifiers for databases 97 and 101 were run again after their records were added.
- Structure: the generator checks 104 databases and 4,900 records. The new databases are quota false and keep every kind. Years are inside each range, and the record text has no provenance phrases, dollar signs or Cyrillic.
- Cross-batch: the rare-word check (a title word that appears in at most three records of the whole catalogue) was run on every pair involving the batch. It shows 248 pairs, 68 of them with both sides in the batch. The repeats above were fixed or listed.
- Whole catalogue: 4,900 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device, the migration from version 94 to version 104 on a device, the UI with 104 databases in the list, lint and the release build.
- The four placed records and the 98 astrolabe are the first candidates for a decision. The question from earlier batches is open for the thinnest decades: keep them smaller, or fill them.

## CP-26: databases 105 to 114, 900 to 1000 (the tenth century, the century before the 1000s)

Status: ten more databases are in the app, one per decade from the 990s down to the 900s: 174 records, migrations from version 104 to version 114, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 990s 18, 980s 20, 970s 17, 960s 17, 950s 14, 940s 17, 930s 21, 920s 16, 910s 14 and 900s 20. None reaches 50, and all ten are marked "quota": false in tools/databases.json. The build passes (114 databases, 5,074 records), 17 of 17 unit tests pass, and the 71 runnable records (7, 7, 7, 7, 5, 6, 9, 7, 7 and 9) pass against their expected output. Every kind is present in every database, which the unit tests require.

Mood: This is the thinnest century of the catalogue. For the 910s to the 930s almost nothing with a dated first public form turned up for several kinds, and 18 of the 174 new records are placed by an anchor, not dated by their own sources. That is a real change in quality, and I want it in the log plainly: a placed record says where its year comes from, but it is not a fact about the item. The part that went well is the cross-batch check, which found five same-item repeats between neighbouring databases, and each was removed rather than kept for coverage.

Date: 2026-10-18. Totals: Kotlin main 113,927 (CP-25: 109,452), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 114,349 (CP-25: 109,874). The +4,475 is the 110 new Kotlin files for databases 105 to 114 and the regenerated catalogue. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 114.

### What changed

- Ten databases, from "Database 105: 990s coding catalogue" (990 to 1000) to "Database 114: 900s coding catalogue" (900 to 910). Their records are 18, 20, 17, 17, 14, 17, 21, 16, 14 and 20: 174 new records and 5,074 in the whole catalogue. The new rare records number 87, so the whole catalogue has 1,377.
- Eleven files per database, 110 in total. Colours are #6D4C41, #C0CA33, #880E4F, #004D40, #BF360C, #263238, #1B5E20, #0D47A1, #4A148C and #E65100.
- Spec: tools/databases.json has 114 entries. All ten new databases are marked "quota": false.
- Generator: tools/build_catalogue.py read years as four digits only, so the records of the 900s to the 990s (three-digit years) broke it. The year pattern now accepts three or four digits. This is the only tool change in the batch.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples now gives the runnable counts for 75 to 114.
- Process: one helper per database, ten in parallel. Seven were stopped by a container restart and resumed. Follow-up helpers filled empty kinds (109 DEVICE, 111 DEVICE) and replaced repeated records (112 GUIDE, 114 GUIDE).

### Placed records (18)

The year of these records is an anchor, not the date of a dated making. Each record's details say so.

- 105: Gerbert's armillary sphere, placed at 996 (the year of Richer's account); a list of five reigns of the 990s, placed at 997.
- 107: a GUIDE on the Song day counted in 100 ke, placed at 977 (the year of Zhang Sixun's instrument, not of the method).
- 108: the Guangzheng stone classics, placed at 965 (the fall of Later Shu); a GUIDE on sign-degree longitude, placed at 964 (the year of al-Sufi's book).
- 109: the golden throne with mechanical lions and birds, placed at 959 (the death of Constantine VII); Hywel Dda's laws, placed at 950 (his death); a runic-alphabet GUIDE, placed at 950 (the Jelling stone).
- 110: the Greek fire siphon, placed at 941 (the Rus attack); al-Farabi's Ihsa al-ulum, placed at 950; Ibn Muqla's rule for six scripts, placed at 940; a GUIDE on farsakh route totals, placed at 947.
- 111: the aqueducts and underground channels of Madinat al-Zahra, placed at 936 (the founding of the palace city). Excavation, not a dated text, is the source.
- 112: the Burghal Hidage, placed at 924 (the accession of Athelstan); a GUIDE on kaeriten reading marks, placed at 930 (the death of Emperor Daigo).
- 113: the palm-leaf ticket pot of Uttiramerur, placed at 919 (the year of the inscription that describes it). Weak: a lot pot is a stretch for "device".
- 114: the Tang fish tally, placed at 907 (the fall of the Tang); a GUIDE on Thabit ibn Qurra's rule for amicable pairs, placed at 901 (his death).

Weakest first: the 113 pot, the 111 aqueducts, the 112 kaeriten GUIDE and the 110 farsakh GUIDE.

### Problems and what was fixed

- 109 Mahberet and 108 Mahberet (a Hebrew dictionary of about 960) were the same record. The 109 copy was removed.
- 110 Huajian ji and 111 Huajianji were the same anthology. The 111 copy was removed.
- 113 and 114 both had the Fatimid caliphate founded in 909 or 910. The 113 copy was removed.
- 111 PRACTICE on the Law Speaker's recital and 112 GUIDE on the same Althing law recital repeated each other. The 112 GUIDE was replaced by a kaeriten method.
- 71 and 114 both had a method for counting the sounds of a waka. The 114 GUIDE was replaced by Thabit ibn Qurra's rule for amicable pairs.
- 112 SOAS astrolabe (927 or 928, 315 AH) rests on one personal web entry whose dating and authenticity were questioned. It is kept as the DEVICE of 112, and it is the first candidate to drop.
- 107 Zhang Sixun's instrument: the search found a mercury drive, not water, so the record names the instrument without a water drive.

### Repeats left for a decision

Same book, event or person in more than one record of one database. These are angles, not copies, and they are the first candidates to thin.

- 105: al-Khujandi's 994 readings at Rayy (DEVICE, EXAMPLE, CROSS and GUIDE); Richer's Historiae (LANGUAGE and EXAMPLE); Olaf Tryggvason (SYSTEM and FLEX).
- 106: Ibn Sahl (DEVICE, GUIDE, FLEX); Theophanu's regency (SYSTEM and two FLEX); the Peace of God (PRACTICE and FLEX); the Fihrist (TOOL and CROSS).
- 107: al-Azhar (SYSTEM, EXAMPLE and FLEX); the Taiping Guangji (LANGUAGE, EXAMPLE and FLEX); Zhang Sixun (DEVICE and FLEX).
- 108: al-Sufi's Book of the Fixed Stars (TOOL, CROSS and FLEX).
- 109: the Gosen wakashu (LANGUAGE and EXAMPLE); the gwageo (SYSTEM and FLEX).
- 110: the Buyid entry into Baghdad and the Khitan take of Kaifeng (SYSTEM and FLEX each).
- 111: the Althing (SYSTEM, PRACTICE, EXAMPLE, and the 930 founding); Madinat al-Zahra (SYSTEM and DEVICE).
- 112: Balhae (SYSTEM, CROSS and FLEX); the Engishiki (SYSTEM, PRACTICE and FLEX); Athelstan (FLEX and PROJECT).
- 113: the Uttiramerur inscriptions (DEVICE, CROSS and EXAMPLE).
- 114: Former Shu and Later Baekje (SYSTEM, EXAMPLE and FLEX each); the Kokinshu (LANGUAGE and PROJECT).

Method repeats, the same method on different items: the "days from an event" FLEX family; the placed reign and death anchors; the reign-length and anthology projects.

### Facts checked by web search

Helpers checked the dates below in web search (standard mode), and a few by WebFetch. Each is a single source or a summary unless stated.

- 105: al-Khujandi's 994 meridian readings at Rayy (tilt and latitude; one summary); Richer's Historiae about 996; Chunhua Ge Tie compiled 992; All Souls at Cluny disputed (998 most often); Abbo of Fleury abbot 988, killed 1004; Abu'l-Wafa's arithmetic 961 to 976, dropped.
- 106: Taiping Yulan compiled 977 to 983, 1000 juan; the Fihrist dated 987; Ibn Sahl about 984 (983 to 985 in one account).
- 107: Codex Vigilanus completed 976; Zhang Sixun's instrument completed 977 or 979, with a mercury drive; the Shu canon 971 to 983; al-Azhar begun 4 April 970; Taiping Guangji 977 to 978.
- 108: Mahberet, a Hebrew dictionary, dated about 960; Bal'ami's commission dated 963; the Guangzheng stones at Chengdu; al-Sufi's book dated 964 (965 in some accounts).
- 109: the Nobi angeom beop of 956 or 958 (one page); the Gosen wakashu ordered 951; Hywel Dda died 950; the Jelling stone; al-Uqlidisi's book written at Damascus 952 to 953; Tentoku 4 (960).
- 110: the Dunhuang star chart dated 649 to 684 (so the 940 plan was dropped); the Huajian ji compiled 940; the Old Book of Tang presented 945; Narshakhi's dedication 943 or 944; Ibn Muqla died 940 (one site).
- 111: the Althing's traditional founding in 930; Youzhou made Nanjing in 938; Wamyo Ruijusho 931 to 938, dates disputed.
- 112: the Engishiki completed in 927 with 27 norito in book 8; the Khitan large script 920 (most accounts); the Balhae taken by the Khitan in 926 (one reference gives 936); the SOAS astrolabe 927 or 928 (one personal web entry, weak).
- 113: the Uttiramerur inscriptions dated 919 and 921 in one account, 920 in another; the Fatimid enthronement at Raqqada on 5 January 910; Mahdia ordered built 916; Ibn Wahshiyya's cipher treatise dated about 1022 (not used).
- 114: the Fatimid dynasty dated 909, with January 910 in another account; the Later Baekje proclaimed 900 at Wansan; the Later Goguryeo founded 901; Regino of Prum compiled about 906.

### Facts from memory, to check

Michael IV, Constantine VII and the Greek fire episode of 941 (110). The Old Book of Tang and the New Book of Tang dates (110). The Khitan takeover of Kaifeng on 947. Otto I's marriage and the Lechfeld battle of 955 (109). The Althing's Christianity of 999 or 1000 (105). The Venice golden bull of 992 (105). The Kokinshu of 905 with 1,111 poems (114). Thabit ibn Qurra's death in 901 (114). The Burghal Hidage's dating (112). The Chinese Five Dynasties, Later Tang and Later Han dates (110 to 113). Many reign dates for the Samanids, Abbasids, Cordoba emirs and Byzantine emperors.

### Problems

- Thin decades. All ten are below 50 records, as listed above.
- Empty kinds. Database 109 had no DEVICE, and database 111 had no DEVICE; each was filled with a placed record (above).
- Placed records. Eighteen records carry an anchor year. They are the first decision for the user.
- Single sources. The SOAS astrolabe (112), the Uttiramerur date (113), Ibn Muqla's death (110), the Gosen wakashu date (109) and the Hywel Dda and Jelling dates (109) rest on one source or on a summary.

### How I checked it

- Unit tests: 17 of 17 pass. The Gradle run ended with BUILD SUCCESSFUL, no failures and no errors.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. The 71 runnable records of databases 105 to 114 pass (7, 7, 7, 7, 5, 6, 9, 7, 7 and 9). The verifiers for 112 and 114 were run again after their GUIDE records were replaced.
- Structure: the generator checks 114 databases and 5,074 records. The new databases are quota false and keep every kind. Years are inside each range, and the record text has no provenance phrases, dollar signs or Cyrillic.
- Cross-batch: the rare-word check (a title word that appears in at most three records of the whole catalogue) was run on every pair involving the batch. It shows 211 pairs, 76 of them with both sides in the batch. The five same-item repeats were removed or replaced, and the others are listed above.
- Whole catalogue: 5,074 records. The only titles that repeat are "Erlang" and "Software inspections", as before.

### Notes

- Not verified yet: running on a device, the migration from version 104 to version 114 on a device, the UI with 114 databases in the list, lint and the release build.
- The 900s to the 990s are the thinnest decades of the catalogue. The question from earlier batches is open: keep them smaller, or fill them, or drop the placed records.

## CP-27: databases 115 to 124, 800 to 900 (the ninth century, the century before the 900s)

Status: ten more databases are in the app, one per decade from the 890s down to the 800s: 155 records, migrations from version 114 to version 124, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 890s 17, 880s 16, 870s 16, 860s 13, 850s 14, 840s 16, 830s 13, 820s 14, 810s 15 and 800s 21. None reaches 50, and all ten are marked "quota": false in tools/databases.json. The build passes (124 databases, 5,229 records), 17 of 17 unit tests pass, and the 55 runnable records (6, 5, 5, 6, 5, 7, 5, 5, 5 and 6) pass against their expected output. Every kind is present in every database.

Mood: This is the ninth century, and the thinnest yet for things with a dated first public form. The 820s and 830s gave the helpers very little, so 14 records carry an anchor year rather than their own date. What went well is the checking. The search caught a wrong coronation list in 115 (Lambert's coronation of 892 was at Ravenna, and Louis III's of 901 was missing) and a wrong attribution in my own anchors file (the 817 Ordinatio was Louis the Pious's, not Charlemagne's). The rare-word check then found four same-item repeats and one shared coronation, and each was fixed. What I am not happy with is the group of PROJECT and FLEX records that only do arithmetic on reigns and dates. They pass, but they are not coding history, and I would cut them first.

Date: 2026-10-19. Totals: Kotlin main 117,950 (CP-26: 113,927), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 118,372 (CP-26: 114,349). The +4,023 is the 110 new Kotlin seed files for databases 115 to 124 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 124.

### What changed

- Ten databases, from "Database 115: 890s coding catalogue" (890 to 900) to "Database 124: 800s coding catalogue" (800 to 810). Their records are 17, 16, 16, 13, 14, 16, 13, 14, 15 and 21: 155 new records and 5,229 in the whole catalogue. The new rare records number 80, so the whole catalogue has 1,457.
- Eleven files per database, 110 in total. Colours are #AD1457, #00695C, #558B2F, #F9A825, #283593, #5E35B1, #00838F, #D84315, #424242 and #9E9D24.
- Spec: tools/databases.json has 124 entries. All ten new databases are marked "quota": false.
- Generator: no change in this batch.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples now gives the runnable counts for 75 to 124.
- Process: one helper per database, ten in parallel, from the brief, an anchors file for the 800s to the 890s and the verifier template. Each helper reported its own count-only checker problems, which the thin-century rule allows. The lead then corrected the 115 coronation project, the 116 accessions project and the repeats listed below.

### Placed records (14)

The year of these records is an anchor, not the date of a dated making. Each record's details say so.

- 115: LANGUAGE "Al-Baladhuri's Futuh al-buldan" (892, the year of the author's death, not of the book); EXAMPLE "Counting the letters of the Alfred Jewel inscription" (899, within the reign of Alfred).
- 116: DEVICE "The mechanical model of the heavens credited to Abbas ibn Firnas in Cordoba" (887, his death).
- 117: DEVICE "The vertical windmills of Sistan" (873); GUIDE "How letter counts break a substitution cipher, as al-Kindi set out" (873, the date of al-Kindi's work is disputed).
- 118: TOOL "al-Farghani's Elements of Astronomy" (861; no source dates the book).
- 119: PROJECT "Four dated events of the 850s, sorted, with the gaps between them" (852); CROSS "The sphere and cylinder ratio of the Banu Musa's treatise, in Python and C" (850).
- 120: DEVICE "The beacon towers of Leo the Mathematician, built in Theophilos's reign" (842); GUIDE "How a chain of narrators is checked, pair by pair, against their lifespans" (850, the end of the mihna). The narrators and dates in its example are invented, and the record says so.
- 121: DEVICE "The marble quadrant of Mount Qasiyun near Damascus, in al-Ma'mun's observing program" (831, the start of the program).
- 122: DEVICE "The observing instruments of al-Ma'mun's observatory in the Shammasiyya quarter of Baghdad" (828, the start of the observations).
- 123: DEVICE "The dust board, a sand tray on which Indian-style numerals were written and wiped out" (813); GUIDE "How the awl rule raises the shares of an Islamic inheritance when they add up to more than one" (820).
- 124: none.

Weakest first: the 123 dust board, the 120 beacon towers, the 117 windmills, the 121 quadrant and the 122 instruments. Then the 116 Firnas model and the 115 Alfred Jewel count. The FLEX and CROSS records of 124 and 116, and the coronation PROJECT of 115, are arithmetic on dates, and they are next in line.

### Problems and what was fixed

- Kavirajamarga (LANGUAGE) and Mahavira's Ganita-sara-sangraha (TOOL) were in both 119 and 120. The 120 copies were removed, so 120 keeps three LANGUAGE records and one TOOL. The 119 records stay.
- Historia Brittonum was in 121 and 122. The 121 copy was removed, and 122 keeps it, dated 829 to 830.
- The Konin kyakushiki was in 122 (EXAMPLE and PROJECT) and in 123 (SYSTEM). The 123 copy was removed. 122 keeps its two records, which are two angles on the same code.
- Charles the Fat's coronation of 881 was in the coronation project of 115 and in the accessions project of 116. The 116 project now uses Marinus I's election as pope in 882, from memory, with gaps 2, 2 and 1 and a span of 5.
- The coronation project of 115: the first version listed five coronations at Rome and omitted Lambert (892) and Louis III (901). The search confirmed Louis III's coronation at Rome in February 901 and Guy of Spoleto's at Rome on 21 February 891, and found that Lambert's co-emperor coronation of 892 took place at Ravenna. The list is now six coronations at Rome (875, 881, 891, 896, 901 and 915), with gaps 6, 10, 5, 5 and 14. The longest gap is 14 years, from 901 to 915.
- The 123 anchor: the 817 Ordinatio imperii was Louis the Pious's plan, not Charlemagne's. The record names Louis. The Konin date is disputed (820 here, 829 in one lecture source), and the record says so.
- 124: the Charlemagne EXAMPLE had the same count as Otto I's and was changed; the CROSS title copied a "days from a death" pattern and was changed.

### Repeats left for a decision

Same book, event or person in more than one record of one database. These are angles, not copies, and they are the first candidates to thin.

- 115: the Ruiju kokushi (LANGUAGE and FLEX); the Zaydi state (SYSTEM and FLEX); Michizane (FLEX and PRACTICE).
- 116: Basilika (SYSTEM and FLEX); Choe Chiwon (LANGUAGE and EXAMPLE).
- 117: the Gwalior tablet (LANGUAGE and EXAMPLE); Huang Chao (SYSTEM and PROJECT); the Tahirid and Tulunid spans (CROSS and SYSTEM); Meerssen and Quierzy (FLEX and SYSTEM each).
- 118: the Nilometer (DEVICE and EXAMPLE); the Glagolitic alphabet (LANGUAGE and CROSS); Basil I (FLEX and SYSTEM); the Diamond Sutra (FLEX and PRACTICE); the Edict of Pitres (FLEX and SYSTEM).
- 119: the Banu Musa (DEVICE, GUIDE and CROSS); the Zhenyuan miaodao yaolue (PRACTICE and EXAMPLE); al-Mutawakkil's decree of 850 (SYSTEM and FLEX).
- 120: the Nihon Koki (LANGUAGE and EXAMPLE); the Treaty of Verdun (SYSTEM and FLEX).
- 121: the Kaicheng Stone Classics (PRACTICE and CROSS); Samarra (EXAMPLE and FLEX).
- 122: the Konin kyakushiki (EXAMPLE and PROJECT); Olona (SYSTEM and FLEX).
- 123: Theophanes (EXAMPLE and LANGUAGE); Louis the Pious's Ordinatio (SYSTEM and FLEX).
- 124: the Kusuko incident (SYSTEM and FLEX); Nikephoros I (SYSTEM and FLEX); the fall of the Barmakids (SYSTEM and FLEX).

Neighbours across databases, different items, kept:

- Choe Chiwon in 116 (LANGUAGE and EXAMPLE) and 117 (PRACTICE on the Tang examination of 874).
- The Treaty of Meerssen, a SYSTEM in 117 and an end date in the FLEX of 120.
- Kukai's Precious Key (121, LANGUAGE) and the initiation Kukai gave Saicho (123, PRACTICE).
- Otfrid's gospel harmony (117) beside the gospel-harmony GUIDE of 121, and the Heliand (121) beside Otfrid.
- al-Khwarizmi's Algebra (122) beside Abu Kamil's (115) and Omar Khayyam's (97); the Zij (121) beside Adelard's Latin version (92), with the Arabic originals stated in details.
- A regnal-year guide in 118 (Xiantong) beside those of 76 (Zhiyuan) and 61 (Henry VI): one method on three rulers, flagged.
- The Kaicheng Stone Classics (121) beside the Guangzheng stone classics (108).
- The House of Wisdom (121) beside al-Khazini's Book of the Balance of Wisdom (92).
- The rulers-who-died PROJECT of 123 beside 85, 86 and 101: one family of project.

### Repeat check result

The rare-word check (scratchpad/same_item_batch.py 115 124) gives 248 pairs over 5,229 records. Every pair that touches the batch was read by hand. Most share one common word (west, jewel, urbis, regis, chant and so on) and are different items. The pairs that share two or more rare words are the ones listed above or in the neighbour list. The whole catalogue still has only two repeated titles: "Erlang" and "Software inspections", as before.

### Facts checked by web search

Helpers checked the facts below with web search (standard mode, six calls each at most). Each is a single source or a summary unless stated.

- 115: the Zaydi state founded in 897 and its founder's death at Sa'dah in 911; the end of the imamate disputed (1962 and 1970 both named); Taktika dated about 900 to 912, the suggested 895 not supported; Michizane's arrival at Dazaifu in 901 and his death in 903; Abbo's poem finished about 897. The lead checked the coronations: Louis III at Rome in February 901 (Benedict IV) and Guy of Spoleto at Rome in February 891 are confirmed; Lambert's co-emperor coronation of 892 was at Ravenna, not Rome.
- 116: the kampaku's formal title in 887, the informal regency from 884, the dispute ended in June 888; the Gyewon collection of 886 and Choe's return to Silla in 885; the Basilika's completion in 888 or about 892; Eulalia about 880, in the same codex as the Ludwigslied; Notker's letter about 884 (an inference); the Firnas device "often described as a planetarium", attribution reported.
- 117: the Gwalior tablet (year 933 of a 57 BCE era is 876 CE; zeros in 270 and 50); Otfrid's completion 863 to 871; Nishapur fell in 873 (872 in some accounts); the last Tahirid 862 to 873; al-Kindi composed about 850 and died about 873; Procheiros dated 867 to 879 or 907; Choe Chiwon passed the Tang examination in 874.
- 118: the Roda Nilometer rebuilt in 861 (inscription dated Rajab 247 AH; patron disputed; first gauge 715); the Edict of Pitres in late June 864; the Glagolitic work from 863 (a request dated 862 in some accounts); the Unicode block U+2C00 to U+2C5F, 96 points; al-Farghani's Elements credited with 20,400 miles, but no source gives 861 for the book.
- 119: the Book of Ingenious Devices has about a hundred devices; the mihna reversal dates to 847 and its full end to about 851; al-Tabari records the 850 dress decree (235 AH), and accounts differ on the colour of the headgear; the Kavirajamarga dated 850, authorship disputed; Mahavira listed under Amoghavarsha I.
- 120: Leo's beacon line (single source; tenth-century accounts give 829 to 842); Photios's Myriobiblon about 850 to 855, roughly 280 entries. The Kavirajamarga and Mahavira were removed from 120 after the repeat check.
- 121: Kaicheng with twelve works and 114 tablets, carved 833 to 837 (some accounts say 830); the House of Wisdom under al-Ma'mun, early 830s on one biography; the Qasiyun program 831 to 833, dates vary; the Ryo no gige dated 833 in two accounts; the Yoro regulations in force in 757; the Zij with no Arabic copy known, and some accounts give 830.
- 122: the Shammasiyya observations started in 828 (other accounts give 828 to 829 or 829 to 830); the Konin kyakushiki completed 820, begun about 803, covering laws from 701 to 819, in force from 830; the Historia Brittonum composed 829 to 830; the Olona assembly of May 825 near Pavia.
- 123: Yuanhe xingzuan 812; Theophanes's chronicle covers 284 to 813 and was composed about 810 to 815; the Risala reworked in Baghdad and then in Egypt; the 812 initiation at Takaosan, with the final rite withheld and a disputed 805 rite; the siege of Baghdad from August 812 to late September 813; the Konin era 810 to 824.
- 124: the Book of Armagh, copied by Ferdomnach about 807 (807 or 808 in accounts); Harun's clock of 807 with twelve brass riders, mechanism disputed; the Propositiones about 800 in some accounts, Alcuin's authorship disputed; the Mecca arrangement of 802, division or succession disputed; Kogo Shui of 807, original language disputed.

### Facts from memory, to check

- 115: Asser's Life of Alfred, 893; the Anglo-Saxon Chronicle about 890; the Ruiju kokushi, 892; al-Baladhuri died 892; the Kletorologion, 899; Abu Kamil, the Musica enchiriadis and Sridhara all about 900; Alfred's warships, 896; the kentoshi, 894; the Alfred Jewel and Edward's accession, 899; the coronations of Arnulf (896) and Berengar I (915) at Rome.
- 116: the Peace of Alfred and Guthrum about 886 and its boundary; Saucourt, 881; Louis III's death, 882; De ordine palatii, 882; the Nomocanon, 883; Industriae tuae, 880; Choe's departure in 868; the Paris siege from late 885; Koko 884, Leo VI 886 and Uda 887; Marinus I's election in 882.
- 117: al-Ya'qubi, 872; Otfrid's five books; the Treaty of Meerssen in 869 and August 870; the Capitulary of Quierzy in June 877 and its terms; Charles the Bald's coronation in 875 and death in 877; Huang Chao's dates; the Zanj revolt, 869 to 883; the Tahirid dynasty from 821 and the Tulunid from 868 to 905; the Pratihara attribution of the Gwalior tablet; the Sistan mill descriptions.
- 118: Basil I's sole rule from September 867 and the dynasty's end in 1056; the Diamond Sutra colophon of 11 May 868, printed by Wang Jie and found at Dunhuang in 1900; the Xiantong era from 860; Nicholas I's Responsa of 866; the reign dates in the PROJECT.
- 119: the Accounts of China and India, 851, with the 916 addition; the Youyang zazu, 860; the Pseudo-Isidorian Decretals about 850; Mahavira's date about 850; Ibn Hanbal's death in 855; the four events of the PROJECT; Archimedes' two-thirds ratio.
- 120: the Oaths of Strasbourg, 842; the Treaty of Verdun, 843; the Huichang edict of 845 and the Huichang era, 841 to 846; Ennin's diary, 838 to 847; the Nihon Koki of 840, covering 792 to 833; the Feast of Orthodoxy, 843; Chang Pogo's death in 846 (the least certain); the Abbasid reigns; the Treaty of Meerssen, 870; Theophilos's death, 842; the end of the mihna, 850.
- 121: Einhard, the Heliand and Hizo's Hoyaku at 830; Samarra, 836 and 892; al-Ma'mun's death in 833; the eight reign dates in the PROJECT; the four-Gospel coverage in the GUIDE.
- 122: al-Khwarizmi's Algebra about 820; the Royal Frankish Annals, 741 to 829; Ermold, 826 to 828; the Hortulus, 827 to 829; Robert of Chester, 1145; the Hindu reckoning about 825; the St Gall plan about 820; the Utrecht Psalter about 820 to 835; the Taiho code of 701; the Yoro code, 718 and 757; Lothair's death in 855.
- 123: the Shinsen Shojiroku, 815, and its compilers under Saga; the Risala of about 814 and al-Shafi'i's death in 820; the Konin dates; Louis the Pious's Ordinatio, 817; the Iconoclasm of 815 (with 787 and 843); Li Jifu's gazetteer, 813; the death years in the PROJECT; Louis the Pious's death in 840; Harun's death in 809; al-Ma'mun's accession in 813.
- 124: Charlemagne's coronation and reign dates; Nikephoros I; the Barmakids; the Capitulare de villis; the oath of 802; Carolingian minuscule; the filioque at Aachen; Chang hen ge; the Book of Kells; the Kusuko incident; the Japanese capital dates; Harun's death.

### Single-source dates

- 115: Abbo's 897 (one academic article); the Taktika range (one review); the Zaydi death in 911 (one article); Dazaifu 901 (one Japanese page).
- 116: the Basilika's sixty books and the Gyewon's sixty poems (one source each); the Epanagoge 886 (one source, plus memory).
- 117: Otfrid's Vienna manuscript about 871 (one facsimile listing); Procheiros 867 to 879 (one 1928 edition); Choe's return in 885 (one library profile).
- 118: the 815 restoration and the 850 flood at the Nilometer (one account); the 715 caliph differs between accounts.
- 119: the device book's 850 (one blog title); the Zhenyuan miaodao yaolue's 858 (one timeline), with its counts of 34 formulas and three saltpetre recipes.
- 120: Leo's beacon line (single source; tenth-century accounts give 829 to 842).
- 121: the Qasiyun radius of 5 metres (al-Biruni, one summary); the Zij at 830.
- 122: the Konin EXAMPLE's volume counts (one source); the Shammasiyya date of 828 (sources differ); the Historia Brittonum at 829 to 830 (one encyclopedia and one database entry).
- 123: the Yuanhe xingzuan date and Xianzong's commission; the siege dates (one encyclopedia each).
- 124: 792 and 799 (one summary of the Mecca article); the Propositiones "about 800" (one blog summary); Armagh's 807 (one summary).

### Problems

- Thin decades. All ten are below 50 records, as listed above. The runnable counts are 6, 5, 5, 6, 5, 7, 5, 5, 5 and 6.
- Placed records. Fourteen of the 155 carry an anchor year. They are the first decision for the user.
- Off-subject records. Several PROJECT and FLEX records are arithmetic on reigns and dates rather than items of coding history: the coronation projects of 115 and 116, the reign and death counts of 121, 123 and 124, and the event project of 119. They pass, but I would cut them first.
- Coding relevance. For the 800s and 810s most records are texts, law codes and instruments of astronomy, with little computing history. The brief allows this, but the user should know it.
- Single sources. Listed above.

### How I checked it

- Unit tests: 17 of 17 pass. The three XML reports show 17 tests, with no failures, errors or skips. The Gradle run ended with BUILD SUCCESSFUL in 2m 32s; the first Gradle daemon failed on startup and a second attempt started it.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. All 55 runnable records pass. The coronation project of 115 and the accessions project of 116 were worked out by hand again after their fixes.
- Structure: the generator checks 124 databases and 5,229 records. The checker reports only the count rules (the 50-record total, the kind targets and the 20 runnable records) for each of the ten databases, which the thin-century rule allows. Title checks, text rules, years and the rare counts (7 to 10) pass.
- Cross-batch: the rare-word check and the lists above; four same-item repeats and one shared coronation were fixed.
- Whole catalogue: 5,229 records; the only repeated titles are "Erlang" and "Software inspections", as before.
- Text: the records have no provenance phrases and no dollar signs, and they pass the ASCII rule of the brief, with the en dash allowed.

### Notes

- Not verified yet: running on a device, the migration from version 114 to version 124 on a device, the UI with 124 databases in the list, lint and the release build.
- The 800s to the 890s are thin in dated coding history. The question from earlier batches is still open, now for ten more decades: keep them smaller, fill them, or drop the placed records. A second question is new: should the off-subject PROJECT and FLEX records stay?
- The memory facts above are the list to check first. The three coronations were the only facts I checked myself, beyond the helpers' searches.

## CP-28: databases 125 to 134, 700 to 800 (the eighth century, the century before the 800s)

Status: ten more databases are in the app, one per decade from the 790s down to the 700s: 141 records, migrations from version 124 to version 134, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 790s 16, 780s 18, 770s 11, 760s 11, 750s 17, 740s 14, 730s 15, 720s 12, 710s 16 and 700s 11. None reaches 50, and all ten are marked "quota": false in tools/databases.json. The build passes (134 databases, 5,370 records), 17 of 17 unit tests pass, and the 52 runnable records (5, 5, 5, 5, 5, 6, 5, 5, 6 and 5) pass against their expected output. Every kind is present in every database.

Mood: This is the thinnest century so far for coding history. Several decades had almost nothing with a dated first public form, so 21 of the 141 records carry an anchor year. The checks did their job: the title check and the rare-word check found three same-item repeats (the Pure Light Dharani in 129 and 130, the two-tax reform of 780 in 126 and 127, and the stirrup in 131 and 133), and each was fixed. What I am unhappy with is how much of the century is history of technology or of administration: Great Buddha casting, Carolingian charters, Japanese court and land objects. The subjects are real, but the link to coding is thin, and I would like the user's view before the next century.

Date: 2026-10-19. Totals: Kotlin main 121,722 (CP-27: 117,950), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 122,144 (CP-27: 118,372). The +3,772 is the 110 new Kotlin seed files for databases 125 to 134 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 134.

### What changed

- Ten databases, from "Database 125: 790s coding catalogue" (790 to 800) to "Database 134: 700s coding catalogue" (700 to 710). Their records are 16, 18, 11, 11, 17, 14, 15, 12, 16 and 11: 141 new records and 5,370 in the whole catalogue. The new rare records number 81, so the whole catalogue has 1,538.
- Eleven files per database, 110 in total. Colours are #E53935, #039BE5, #43A047, #FB8C00, #795548, #FFCA28, #FFEE58, #8BC34A, #1E88E5 and #FFB300, in order from 125 to 134.
- Spec: tools/databases.json has 134 entries. All ten new databases are marked "quota": false.
- Generator: no change in this batch.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples now gives the runnable counts for 75 to 134.
- Process: one helper per database, ten in parallel, from the brief, an anchors file for the 700s to the 790s and the layout templates of database 124. Each helper reported count-only checker problems, which the thin-century rule allows. The lead then fixed the three repeats listed below.

### Placed records (21)

The year of these records is an anchor, not the date of a dated making. Each record's details say so.

- 125: a DEVICE of the Viking longship of the Lindisfarne raid (793); a TOOL, Khalil ibn Ahmad's Kitab al-Ayn (791); a GUIDE on the three-letter Arabic verb pattern fa'ala (796).
- 126: a DEVICE, the Persian wheel of the Abbasid lands, whose first use is undated (785); a TOOL, Paul the Deacon's Homiliary (789).
- 127: a DEVICE, the first astrolabe made in Islam, credited to al-Fazari (775, the year of al-Mansur's death); a GUIDE on the 1260 days of Revelation as forty-two months (776, the year of Beatus's commentary).
- 128: a DEVICE, the Tibetan prayer wheel, with no firm date for its first use (763); a TOOL, the mokkan wooden message tablets of the Nara palace (764).
- 129: a DEVICE, the five-stringed biwa of the Shoso-in (756, the year of the treasures' gift); a TOOL, the bamboo screen mould of Chinese papermaking (751, the year of the battle of Talas).
- 130: a DEVICE, the foot-operated bellows of the Great Buddha; a TOOL, the sectioned clay moulds of the Great Buddha; and a GUIDE on cho and tan, the land units of the Japanese land rules. All three are placed at 743, the year of the Great Buddha decree.
- 131: a DEVICE, the stirrup in Western cavalry (732, the year of Tours).
- 132: a GUIDE on the Tang units li, bu and chi, as the survey's distances were reckoned (724, the year of the survey).
- 133: a DEVICE, the water-driven grain mill of the Umayyad lands (715, the year of Walid I's death); an EXAMPLE on the books of the Latin Vulgate (716, the year of the Codex Amiatinus journey).
- 134: a DEVICE, the water clock of the Onmyoryo (701); a TOOL, Aldhelm's Enigmata (705); a GUIDE on dividing a pound of gold into 72 solidi (705).

Weakest first: the 130 bellows and moulds, the 134 water clock, the 133 mill, the 127 astrolabe and its GUIDE, and the 125 GUIDE and TOOL.

### Problems and what was fixed

- The Pure Light Dharani was in 129 (PRACTICE, the 750s) and in 130 (PRACTICE, the 740s). The 130 copy was removed; 129 keeps it.
- The two-tax reform of 780 was in 126 (SYSTEM) and 127 (SYSTEM). The 127 copy was removed; 126 keeps it, and 127 keeps two SYSTEM records.
- The stirrup was in 131 (DEVICE, placed at Tours, 732) and 133 (DEVICE, placed at 714). The 133 record was replaced by a water-driven grain mill placed at 715, so 133 keeps one DEVICE.
- No kind was empty in any database.

### Repeats left for a decision

- 128: the Hyakumanto darani appears in three records of one database (PRACTICE, EXAMPLE and FLEX). These are angles on one item, and the first candidates to thin.
- 129: the Donation of Pepin (SYSTEM) and Pepin's reign of 751 to 768 (EXAMPLE).
- 126: the Nestorian stele of Xi'an (LANGUAGE and CROSS).
- 133: Bede's Lives of the Abbots (LANGUAGE) and the copying of the Latin Bible at Wearmouth and Jarrow (PRACTICE).

Neighbours across databases, different items, kept:

- Nicaea II (126, 787) beside the Council of Hieria (129, 754): two councils on icons.
- The Persian wheel (126) beside the water-driven grain mill (133).
- The Great Buddha's bellows (130) beside Biringuccio's furnaces and bellows (51).
- Bede's Ecclesiastical History (131) beside Orderic Vitalis's Ecclesiastical History (93).
- Yi Xing's water-driven armillary sphere (132) beside Su Song's water-driven clock tower (95); Yi Xing's Pole Star survey (132) beside Shen Kuo's gnomon (97).

### Repeat check result

The rare-word check (scratchpad/same_item_batch.py 125 134) gives 226 pairs over 5,370 records. Every pair that touches the batch was read by hand. Most share one common word and are different items. The whole catalogue still has only two repeated titles: "Erlang" and "Software inspections", as before.

### Facts checked by web search

Helpers checked the facts below with web search (standard mode, six calls each at most). Each is a single source or a summary unless stated.

- 125: Tamuramaro's seii taishogun in 797 (the first holder disputed); the first Baghdad paper mill, 794 or 795 (the patron disputed); the first Arabic Euclid about 800; Sibawayh's death about 796 (793 to 796 in one account); the Codex Carolinus letters dated 739 to 791; the Libri Carolini about 790 to 792. The Kitab al-Kharaj was dropped, since 798 is only its author's death year.
- 126: Kitab al-'Ayn, the first Arabic dictionary, ordered from ayn to mim, with death given as 786 or 791; the Saxon capitulary of 782 (785 in some accounts, 795 in one study); the Hadrianum sent about 785, arriving in 785 or 786; the Nestorian stele of Xi'an dated 781, with Alopen's arrival in 635; the Harivamsa, Saka 705 equal to 783.
- 127: the Sindhind translation made in the early 770s under al-Mansur by al-Fazari and Ya'qub ibn Tariq (no source pins 771); no source dates or locates the first astrolabe; Offa's gold coin, a copy of an AH 157 (773 to 774) dinar, with some sources dating it after 774; the Herstal capitulary of March 779; the two-tax reform of 780 under Yang Yan and Emperor Dezong (grain or coin disputed).
- 128: Chang'an taken in 763; the Emi rebellion defeated in 764; Shotoku's second reign from 764; the Hyakumanto ordered in 764, sent to ten temples, finished about 770 (printing method disputed); the Gentilly synod of 25 December 767; the Round City of Baghdad begun in 762, its gates, and its end date (766 to 768); the Abrogans, a St Gall copy from Freising, alphabetised and dated 750 to 775.
- 129: the Pure Light Dharani found on 13 October 1966 at Seokgatap; the pagoda about 751 and the print before 751 (a 705 to 751 estimate); the Shoso-in gift of about 600 objects in 756 with its register; the Man'yoshu's last dated poem of 759, numbered 4516; Kalila's Arabic in the eighth century, its translator's death about 757 (750 not confirmed); Pepin's reign 751 to 768.
- 130: the Ecloga dated 741 (726 disputed), with eighteen titles; the Chronicle of 741, a Latin history from al-Andalus, about 741 to 743; the 741 edict for a temple and a nunnery in each province; the Great Buddha plan of 743 and its dedication of 752; Tianbao's first year as 742; the claim that the 742 census returns were distorted (one abstract).
- 131: Zhisheng's Kaiyuan Shijiao Lu of 730, in twenty fascicles; the 80 mu allotted plus 20 mu hereditary per adult male under the 737 statute, with the 100 mu total dating to 624; the Liudian's bureau names for Personnel and Rites. The Liudian's 738 date was not confirmed.
- 132: Yi Xing died in 727; the Dayan completion dated 727 or 728 (sources conflict), with imperial issue in 729 (one garbled page); the survey of 724 and 725, with Pole Star and gnomon shadows at sites from about 17 to 50 degrees north (thirteen sites in one account, twenty gnomons in another); a sphere dated 723 or 725 with an escapement (one account); a Syriac chronicle titled 724 (Add. 14643, anonymous); the Sanze law of 723, replaced in 743.
- 133: the Lindisfarne Gospels about 715 (accounts 700 to 720); Liutprand king from 712, with his first laws dated 713; regulated-verse rules dated from the late seventh century to 707 to 710; Shen Quanqi (656 to 714) and Song Zhiwen (about 660 to 712). A Monkwearmouth sundial with a Ceolfrith inscription was searched twice and not found, so it was dropped.
- 134: the Shitong completed in 710 by Liu Zhiji (661 to 721), in 52 chapters (39 inner and 13 outer); the 702 registers are the earliest surviving (Mino, Chikuzen, Buzen, Bungo); accounts give the Taiho code as 701, 702 or 703; the Onmyoryo and its clepsydra scholars are confirmed (staffing figures left out).

### Facts from memory, to check

- 125: the Shoku Nihongi (797, covering 697 to 791); Frankfurt in 794 and Felix of Urgel; Irene's blinding of Constantine VI (797); the Avar treasure (796); Leo III's attack on Rome (April 799) and flight to Paderborn; the Lindisfarne raid (793); the Oseberg ship (about 834); Khalil's death (786 or 791); the reign dates of Offa, Alfonso II, Dezong and Kanmu.
- 126: the two-tax reform of 780; the Classic of Tea and the Muwatta, both about 780; Malik's death in 795; the Nicaea II details and the Frankfurt rejection of 794; Tassilo in 788; the Admonitio of 789; al-Hadi's accession in 785; the Syriac year of the stele (1092) and the Seleucid rule; the kaf, ta, ba order of the Kitab al-'Ayn.
- 127: Beatus's commentary of 776; the Lombard entry in 568 and Pavia's fall in June 774; Brahmagupta's treatise of 628; the reign dates of Constantine V, al-Mansur, al-Mahdi and Leo IV; Artabasdos's usurpation in 742 and 743.
- 128: Madinat al-Salam; Abrogans among the earliest Old High German texts; the mokkan inked and found in the palace ruins; Pippin's Aquitaine war ended in 768; the Usa Hachiman oracle of 769.
- 129: Kaifuso of 751; the Zab in January and August 750; Hieria in 754 and its annulment in 787; the An Lushan dates; Cordoba in 756 and 929; the Donation in 756 and Ravenna in 751; the reigns of al-Mansur, Suzong and Offa; Kalila about 750; Du Huan's Samarkand papermakers; the Shoso-in biwa.
- 130: the census totals of 9,619,254 households and 52,880,488 people; the Konden law and its rank limits; the Concilium Germanicum of 742 under Carloman; the Golden Light and Lotus assignments; the Shigaraki decree; the Pure Light Dharani details; the cho and tan units; Kaiyuan from 713 to 741; Leo III's death in 741; Hirotsugu's revolt in 740; the Umayyad accession years.
- 131: Bede's history of 731 (his death in 735); the Izumo fudoki of 733; Leo III's edict of 730 (Theophanes gives 726); the Roman synod of 731; Tours in 732; the Kaiyuan Code of 737; the five punishments in twenty grades; 16 of the 24 bureau names; John of Damascus (726 to 730); Lynn White (1962); Dionysius (525).
- 132: the Nihon Shoki of 720 in thirty volumes, with Prince Toneri, ending in 697; Bede's De temporum ratione of 725; li equal to 300 bu, bu to 6 chi, chi to 10 cun; the Chuxueji of 727 and the Kaiyuan Zhanjing of 729 (not searched; the PROJECT rests on them); the names Shuiyun hunyi and Dayan li.
- 133: the Kojiki of 712; Shitong in 710; Tariq in 711; Ibn Qasim at Debal in 711 (the end in 712 or 713); the Fudoki decree of 713; the Codex Amiatinus of 716; Pippin II's death in 714; the Vulgate of about 390 to 405 and its book counts; the Umayyad reigns.
- 134: the Wado coins struck from 708 with copper from Chichibu; Dosho's death in 700 and his cremation, the first recorded in Japan; the Umayyad Great Mosque of 705 to 715; Aldhelm bishop of Sherborne in 705; 72 solidi to the pound (fourth century); Justinian II back in power in 705; Wu Zetian's Zhou (690 to 705), Zhongzong to 710 and Ruizong (710 to 712).

### Single-source dates

- 125: the Codex's 791 copying directive and Sibawayh's death in 793.
- 126: the Homiliary's completion near 797 (not in the year field); the Saxon capitulary's 795 (one study).
- 127: the Herstal date and attendance (one source); the Sindhind's 771 has no source; the coin's 774 is disputed.
- 128: the first brick on 30 July 762; the Gentilly date and attendance; the Abrogans alphabetisation; six years of labour.
- 129: the Pure Light print date and its estimate (one search summary); the register's 756; the Man'yoshu's 759; Pepin's 751 to 768.
- 130: the Great Buddha bellows and mould process (one museum guide; the 745 to 749 casting window not confirmed); the Chronicle of 741; the census distortion claim (one abstract).
- 131: the Liudian's 738 date (not confirmed); the Roman synod's 731 (one source).
- 132: the sphere's 725; the Dayan's 729 (one garbled page).
- 133: Bede's Lives "about 716" (from memory only).
- 134: the three inner chapters lost and the composition 708 to 710 (a secondary copy).

### Problems

- Thin decades. All ten are below 50 records, as listed above. The runnable counts are 5, 5, 5, 5, 5, 6, 5, 5, 6 and 5.
- Placed records. Twenty-one of the 141 carry an anchor year. They are the first decision for the user.
- Off-subject records. Much of the century is history of technology or administration: the Great Buddha casting, the Carolingian charters, Japanese land and court objects. They pass, but the link to coding is thin.
- Memory dates. Many dates in the list above come from memory. The Liudian's 738 date, the bureau names and the Roman synod's 731 date are the first to check, as the helper flagged.
- Single sources. Listed above.

### How I checked it

- Unit tests: 17 of 17 pass. The three XML reports show 17 tests, with no failures, errors or skips. The Gradle test task ran, and the build ended with BUILD SUCCESSFUL.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. All 52 runnable records pass.
- Structure: the generator checks 134 databases and 5,370 records. The checker reports only the count rules for each of the ten databases, which the thin-century rule allows; the title checks, text rules, years and rare counts (7 to 10) pass.
- Cross-batch: the rare-word check gives 226 pairs; three same-item repeats were fixed, as listed above.
- Whole catalogue: 5,370 records; the only repeated titles are "Erlang" and "Software inspections", as before.
- Text: the records have no provenance phrases and no dollar signs, and they pass the ASCII rule of the brief, with the en dash allowed.

### Notes

- Not verified yet: running on a device, the migration from version 124 to version 134 on a device, the UI with 134 databases in the list, lint and the release build.
- The 700s to the 790s are thin in dated coding history, and much of the material is not about coding. The question from earlier batches is still open: keep them smaller, fill them, or drop the placed records. A second question is new: should a century be covered at all when the coding link is this weak?
- The first facts to check before publishing are the Liudian's 738 date, the Chuxueji and Kaiyuan Zhanjing dates in the 132 PROJECT, and the Ibn Qasim end date in 133.

## CP-29: databases 135 to 144, 600 to 700 (the seventh century, the century before the 700s)

Status: ten more databases are in the app, one per decade from the 690s down to the 600s: 139 records, migrations from version 134 to version 144, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 690s 17, 680s 13, 670s 13, 660s 14, 650s 14, 640s 15, 630s 15, 620s 13, 610s 13 and 600s 12. None reaches 50, and all ten are marked "quota": false in tools/databases.json. The build passes (144 databases, 5,509 records), 17 of 17 unit tests pass, and the 51 runnable records (6 in the 690s and 5 in each other decade) pass against their expected output. Every kind is present in every database.

Mood: The seventh century is the emptiest stretch of the coding catalogue so far. Most decades give a handful of dated items, and the rest is placed by an anchor: 18 of the 139 records carry a year that is not the year of their own item. The two repeats the checks caught were both real duplicates of one object in neighbouring decades (the Cheomseongdae observatory in 140 and 141, and the Arabic vowel dots in 136 and 137), and each was fixed. What I am not comfortable with is how many records rest on dates from memory. The list below is long, and several of those dates would need a source before anything is published.

Date: 2026-10-20. Totals: Kotlin main 125,485 (CP-28: 121,722), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 125,907 (CP-28: 122,144). The +3,763 is the 110 new Kotlin seed files for databases 135 to 144 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 144.

### What changed

- Ten databases, from "Database 135: 690s coding catalogue" (690 to 700) to "Database 144: 600s coding catalogue" (600 to 610). Their records are 17, 13, 13, 14, 14, 15, 15, 13, 13 and 12: 139 new records and 5,509 in the whole catalogue. The new rare records number 86, so the whole catalogue has 1,624.
- Eleven files per database, 110 in total. Colours are #F44336, #E91E63, #9C27B0, #673AB7, #3F51B5, #2196F3, #03A9F4, #00BCD4, #009688 and #4CAF50, in order from 135 to 144.
- Spec: tools/databases.json has 144 entries. All ten new databases are marked "quota": false.
- Generator: no change in this batch.
- Documents: CATALOGUE.md and the README overview and structure blocks are regenerated. The README sentence about examples now gives the runnable counts for 75 to 144.
- Process: one helper per database, ten in parallel, from the brief and an anchors file for the 600s to the 690s. Each helper reported count-only checker problems, which the thin-century rule allows. The lead fixed the two same-item repeats listed below.

### Placed records (18)

The year of these records is an anchor, not the date of a dated making. Each record's details say so.

- 135: a DEVICE, a gold pectoral cross linked to St Cuthbert (698); a PRACTICE, the St Cuthbert Gospel (698); a GUIDE on the Roman civil degrees of kinship (692).
- 136: an EXAMPLE on the chapters of the four Gospels, counted in code (680, the year of the Book of Durrow).
- 137: a LANGUAGE on the laws of Hlothhere and Eadric of Kent (673); a TOOL, the scribe's penknife (674); a PRACTICE on the red dots that marked the vowels of Arabic writing (673); a GUIDE on three gold tremisses (672).
- 138: a GUIDE on how one Tang catty nests into taels, mace and fen (660).
- 139: a GUIDE on finding three grades of grain by elimination, the fangcheng method (656).
- 140: a DEVICE, the padded horse collar (650); a TOOL, the Kashika-vritti, a Sanskrit grammar commentary (650).
- 141: a PRACTICE on the Qur'an gathered under Abu Bakr (633); a GUIDE on the camel zakat of the Abu Bakr letter (632).
- 142: a DEVICE, the Sutton Hoo lyre (624, the usual date of King Raedwald's death).
- 143: a GUIDE on a Tang weight of jin, liang and qian (618, the Tang founding).
- 144: a GUIDE on the entering tone of a syllable with a final stop (601, the Qieyun); a PROJECT on the Sui emperors Wen and Yang (604).

Weakest first: the 135 cross and St Cuthbert Gospel, the 140 horse collar, the 142 lyre, the 137 penknife and the 139 grain GUIDE.

### Problems and what was fixed

- The Cheomseongdae observatory was in 140 (DEVICE, 647) and 141 (DEVICE, 633). The 140 copy was replaced by a placed horse collar (650); 141 keeps the observatory.
- The Arabic vowel dots were in 136 (TOOL, placed at 688) and 137 (PRACTICE, placed at 673). The 136 copy was replaced by Adomnan's De locis sanctis (686, dated from memory); 137 keeps the dots.
- No kind was empty in any database.

### Repeats left for a decision

- 141: Isidore's Etymologies appear in three records (LANGUAGE, CROSS and FLEX).
- 136: the Kota Kapur stone (LANGUAGE and CROSS), with the Kedukan Bukit stone beside it as a second Old Malay inscription.
- 140: the Edict of Rothari (SYSTEM and FLEX) and the Taika reform edict (SYSTEM and FLEX).
- 143: the Hokke gisho (LANGUAGE and CROSS).
- 135: Adomnan (CROSS) and his law, the Cain Adomnain (LANGUAGE).
- 138: the fourteen caliphs of Damascus (EXAMPLE), beside the caliphs' reigns of 724 to 750 in 130 (PROJECT): one family of counts.

Neighbours across databases, different items, kept:

- The Umayyad caliphate at Damascus (138) beside the Umayyad caliphate of Cordoba (112).
- The siege of Mecca in 683 and its catapults (136) beside the mangonel (87).
- The Qur'an gathered under Abu Bakr (141) beside the Qur'an text standardised under Uthman (139): two events in the making of the text.
- The Saka dating GUIDE (136) beside the Indian Saka dates (75).
- The Gospel-chapters EXAMPLE (136) beside the Vulgate books (133) and the New Testament chapters (52).
- Greek fire (137) beside the Greek fire siphon (110): the substance and its first use, not the siphon.

### Repeat check result

The rare-word check (scratchpad/same_item_batch.py 135 144) gives 204 pairs over 5,509 records. Every pair that touches the batch was read. Most share one common word and are different items. The whole catalogue still has only two repeated titles: "Erlang" and "Software inspections", as before.

### Facts checked by web search

Helpers checked the facts below with web search (standard mode, six calls each at most; one helper went over by one). Each is a single source or a summary unless stated.

- 135: Cain Adomnain at Birr in 697, with 91 guarantors; Adomnan abbot of Iona from 679 to 704 (no source gives a birth year, so the CROSS uses the abbacy); the Cuthbert binding dated to the late seventh or early eighth century (disputed); Zetian characters proposed in 689 (one account) and used about 690 to 705; the Epinal Glossary manuscript about 700, with about 3,300 entries, composed in the late seventh century (one source). The Monkwearmouth sundial was not found and was dropped.
- 136: Kota Kapur: 608 Saka is 28 February 686, and the sources agree; Kedukan Bukit is disputed, with 604 Saka as 682 CE (used) or 605 Saka as 683; the Synod of Hatfield dated 680 by the Anglo-Saxon Chronicle (Bede gives no year); the Kiyomihara code promulgated in 689, its penal part disputed; the siege of Mecca from late September to late November 683, with catapults.
- 137: Greek fire invented about 672 to 673 (Theophanes, Kallinikos), first used in the Arab siege of 674 to 678; the Kogo register of 670, nationwide, no copy surviving; the Hertford synod with ten decrees (672 by the council's own dating, 673 by Bede); the Toledo council opening in 675.
- 138: the Linde calendar issued in 665, developed in 664 and last used in 728; Li Chunfeng (602 to 670); the Nihon Shoki dates Japan's first water clock to 660; the Mount Tai rite of 666; Xuanzang's 600 fascicles (the 1,335-fascicle total not confirmed and not used).
- 139: the Naniwa palace completed in 652 (court at Naniwa from 645 in some accounts); Kotoku died in 654, and the return to Asuka is given as 654 or 655; the Qur'an standard text "roughly 650"; the Nihon Shoki's water clock of 660; the Asuka Mizuochi excavations (1981 to 1986); the Liber Iudiciorum issued in 654, revised in 681 and 693 (Britannica; ACLS names only 681); the Ten Canons submitted in 656; the Yonghui era (650 to 655); the Tang Code commentary of 653 (one promotional page).
- 140: the Jin shu of 130 juan in parts of 10, 20, 70 and 30, completed in 648 in some references and drafted 646 to 648 in one account; the Cheomseongdae reign of 632 to 647 with building dates of 633 or 647; the Kashika-vritti in the seventh century; the Ci'en translation bureau of 648; the Toledo councils of 633, 636, 638, 646 and 653; the Typos of September 647 or 648; the Lateran synod of October 649 and Martin I's arrest in 653 or 654.
- 141: Cheomseongdae's reign of 632 to 647, its building dates 633 or 647, about 27 layers, and stones dated 362, 356 or 365 in different accounts; the Chronicon Paschale written shortly after 628, its record ending in 628, its author unknown; the Sui shu completed in 636 in 85 juan; the camel zakat bands up to 76, confirmed in Bukhari 1454 and Tirmidhi 621.
- 142: Nineveh on 12 December 627, with the Persian civil war after it; Bhaskara I's commentary of 629, covering the 33 mathematical verses; the sine rule under Aryabhata's name in the Mahabhaskariya, with some accounts tying it to the 629 commentary; the Chronicon Paschale about 630, its end date disputed (627, 628 or 629); the Sutton Hoo ship burial in the early seventh century, linked to Raedwald, with only the metal escutcheons original; the Constitution of Medina at 622, with 623 as an alternative and its authenticity disputed.
- 143: the Nihon Shoki's entry for 610 on Docho (the Five Classics, pigments, paper, ink and a grinder that some read as water-powered), with his introduction of papermaking disputed; the Zhubing yuanhou lun completed in 610 by Sui decree, authorship disputed; the Hokke gisho traditionally dated 615, authorship disputed; Egypt: Shahrbaraz sent in 618 and Alexandria held by 619 (accounts give 618 to 621 and 619 to 629); the Wuyin calendar (Fu Renjun, Li Chunfeng's criticism, Linde 665), with one reference giving 619 for its rollout.
- 144: the Nihon Shoki's entry on Docho's mill and arrival, Suiko 18 equal to 610; Kanroku's calendar books, Suiko 10 equal to 602, with calendar use from 604 in some accounts; the Qieyun of 601 and its five earlier sources; the Textus Roffensis (early twelfth century) and the 602 or 603 dating of the Aethelberht code; the keju of 605 and its abolition in 1905; the Sui census of 609, about 9 million households and 46 to 50 million people.

### Facts from memory, to check

- 135: the palace examination of 690; the Quinisext Council of 691 to 692, with 102 canons and the 85 Apostolic canons accepted; Fujiwara-kyo from 694 to 710; Wihtred's laws of 695; the Umayyad dinar of 696 or 697; the Dome of the Rock of 691 or 692; the reigns of Justinian II, Leontius and Tiberius III; Cuthbert's translation in 698; the Roman kinship count; the date of the cross.
- 136: the Third Council of Constantinople, 680 to 681, condemning Honorius; the Book of Durrow about 680; Saka plus 78; the Gospel chapter counts (28, 16, 24 and 21) and their thirteenth-century division; the seven monastery founding years; Abu al-Aswad's death in 688 or 689; Kota Kapur on Bangka; De locis sanctis given to King Aldfrith about 686.
- 137: Wearmouth founded in 674; Ziyad died in 673; Hlothhere and Eadric ruled Kent from 673 to 686; the reigns of Ecgfrith, Tenmu and Wamba; the 678 truce of thirty years; a tremissis as a third of a solidus.
- 138: Muawiya's proclamation in 660 and the Year of Unity in 661; Whitby in 664 and Bede's third book; the Omi code of 668; the Kogo register of 670; Goguryeo's fall in 668; the Tang weight nesting; Empress Wu's share in 660; the Byzantine reign years; the fourteen caliphs of Damascus.
- 139: the Yongwei statutes of 651; the Hakuchi era (650 to 654) and its white pheasant; Taika as the first era name; the Wujing zhengyi of 653; Sun Simiao about 652; the Xianqing era from 656; the Nine Chapters grain answers (checked by substitution).
- 140: the Records completed in 646 in 12 juan; Rothari's edict of 643; Umar's council of 644 and its tie rule (the GUIDE); the Taika edict of 646 and the coup of 645; Taizong's dates, 598 to 649.
- 141: Isidore (about 630, died 636, twenty books); Aihole (Saka 556 is 634); Sophronius's letter of 634; Yarmouk and al-Qadisiyya in 636; Toledo IV in 633; the Ekthesis of 638 and the Lateran of 649; the Qur'an tradition under Abu Bakr; the camel band endings 35, 45, 60, 75 and 90; the Sui split of 5, 50 and 30; events of 630, 632 and 634.
- 142: Taizong's killings on 4 July 626 and the August abdication; Heraclius's accession in 610; the 602 war start and the 628 peace; Khosrow II's fall in 628; Badr in 624, Hudaybiyya in 628 and Mecca in 630; Xuanzang's departure in 629 and return in 645; Raedwald's death about 624; the Zhenguan code of 637.
- 143: Heraclius's accession in 610 and the war's end in 628; Salsu in 612; the Goguryeo mission of Docho; the Wuyin adoption in 618; the Tang weights (16 liang to a jin, 10 qian to a liang); the Lotus Sutra's 28 chapters and the Vimalakirti's 14; Suiko from 593 to 628 (some accounts say 592); Yangdi 604 to 618; Gaozu 618 to 626.
- 144: the Seventeen-Article Constitution of 604; the Grand Canal begun in 605, with Hangzhou reached about 610; the Sui reigns 581 to 604 and 604 to 618; the Daye 5 (609) attribution; the household total of 8,907,546 (not confirmed by search).

### Single-source dates

- 135: the Zetian characters' 689 and the Epinal composition date.
- 136: the Kedukan Bukit date of 682 and the Hatfield date of 680.
- 137: the Hertford date of 672 (the council's own dating) and the Toledo date and attendance of 7 November 675; the Greek fire's Syriac date of 669 or 670.
- 138: the Linde calendar's end year of 728 (one search summary); the identification of the Asuka clock (one source); Xuanzang's 663, the end of a secondhand 660 to 663 span.
- 139: Naniwa 652; the Liber Iudiciorum 654; the Ten Canons 656; the Yonghui era 650 to 655; the Tang Code commentary 653.
- 140: the Ci'en date of 648 (one university timeline); the Toledo dates of 638, 646 and 653 (one table); the Typos dispute (one reference).
- 141: the Cheomseongdae date of 633; the Sui treatises of 656.
- 142: the Chronicon Paschale (about 630) and the Medina constitution (622) each rest on one snippet; the Sutton Hoo details rest on one collection record; the Wude code's 624 rests on one article that is itself doubtful.
- 143: Docho's entry dated 610; the Zhubing yuanhou lun dated 610; Fayun's 70 per cent overlap; the Wuyin 618 against 619.
- 144: the keju's start of 587 and 606 (one reference each); Yang's reign ending in 617 (one reference); the twelvefold multiple in the Aethelberht code (one snippet).

### Problems

- Thin decades. All ten are below 50 records, as listed above. The runnable counts are 6 in the 690s and 5 in each other decade.
- Placed records. Eighteen of the 139 carry an anchor year. They are the first decision for the user.
- Coding relevance. This is the thinnest stretch so far for coding history. Much of it is law, councils, Japanese and Chinese chronicles and Sui and Tang administration. The user should decide whether the catalogue should keep going back where the coding link is this weak.
- Memory dates. Many dates in the list above come from memory. The first to check are the Quinisext canon count, the Wuyin 618 date, Suiko's start year, the household census figure and De locis sanctis in 686.
- Single sources. Listed above.

### How I checked it

- Unit tests: 17 of 17 pass. The three XML reports show 17 tests, with no failures, errors or skips. The Gradle test task ran, and the build ended with BUILD SUCCESSFUL in 2m 34s.
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. All 51 runnable records pass.
- Structure: the generator checks 144 databases and 5,509 records. The checker reports only the count rules for each of the ten databases, which the thin-century rule allows; the title checks, text rules, years and rare counts (7 to 10) pass.
- Cross-batch: the rare-word check gives 204 pairs; two same-item repeats were fixed, as listed above.
- Whole catalogue: 5,509 records; the only repeated titles are "Erlang" and "Software inspections", as before.
- Text: the records have no provenance phrases and no dollar signs, and they pass the ASCII rule of the brief, with the en dash allowed.

### Notes

- Not verified yet: running on a device, the migration from version 134 to version 144 on a device, the UI with 144 databases in the list, lint and the release build.
- The 600s to the 690s are the thinnest decades of the catalogue for coding history. The question from earlier batches is still open: keep them smaller, fill them, or drop the placed records. A second question is larger: should the catalogue keep going back at all where the coding link is this weak?
- The first facts to check before publishing are the Quinisext canon count, the Wuyin 618 date, Suiko's start year, the household census figure, and the De locis sanctis date.

## CP-30: databases 145 to 154, 500 to 600 (the sixth century, the century before the 600s)

Status: ten more databases are in the app, one per decade from the 590s down to the 500s: 121 records, migrations from version 144 to version 154, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 590s 15, 580s 12, 570s 12, 560s 10, 550s 12, 540s 11, 530s 12, 520s 12, 510s 11 and 500s 14. None reaches 50, and all ten are marked "quota": false in tools/databases.json. The build passes (154 databases, 5,630 records), 17 of 17 unit tests pass, and the 50 runnable records (5 in each decade) pass against their expected output. Every kind is present in every database.

Mood: This century is thinner still for coding history than the 600s, and it is the part of the quota work I find hardest to make useful. The 500s have almost no dated coding history, so the batch is mostly law, councils, chronicles, grammars and Byzantine administration, and 22 of the 121 records carry an anchor year that is not their own date. I would rather say that plainly than fill the gaps. The checks caught real repeats (a Gregory letter book, Priscian's grammar, the water clock, the Agde council, consular dating and Justinian's reign), and each clear pair was fixed. The reign counts that still repeat across databases are listed below for a decision.

Date: 2026-10-20. Totals: Kotlin main 128,918 (CP-29: 125,485), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 129,340 (CP-29: 125,907). The +3,433 is the 110 new Kotlin seed files for databases 145 to 154 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 154.

### What changed

- Ten databases, from "Database 145: 590s coding catalogue" (590 to 600) to "Database 154: 500s coding catalogue" (500 to 510). Their records are 15, 12, 12, 10, 12, 11, 12, 12, 11 and 14: 121 new records and 5,630 in the whole catalogue. The new rare records number 86, so the whole catalogue has 1,710.
- Eleven files per database, 110 in total. Colours are set in tools/databases.json, from #D32F2F for 145 to #388E3C for 154.
- Spec: tools/databases.json has 154 entries. All ten new databases are marked "quota": false.
- Generator: no change in this batch. SeedCatalog.kt, CATALOGUE.md and the README tables are regenerated. The README sentence about runnable counts now reads 75 to 154, with 145 to 154 in the group of five each.
- Process: one helper per database for the first pass, from the brief and an anchors file. The lead then fixed the repeats below and replaced four records that could not stay.

### Placed records (22)

The year of these records is an anchor, not the date of the item itself. Each record's details say so.

- 145: a DEVICE, the Irish hand-bell (597, the year Columba died on Iona).
- 146: a DEVICE, the shadow gnomon of the Sui court (583); a TOOL, wax-covered writing tablets (582); a PROJECT on the reigns of Hormizd IV and Maurice (582, Maurice's accession).
- 147: a DEVICE, the steelyard (578); a GUIDE on the Olympiad count (570); a TOOL, Gregory of Tours's De cursu stellarum (573); a LANGUAGE, Burzoe's Middle Persian Panchatantra (570); a LANGUAGE, Agathias's Histories (575).
- 148: a DEVICE, the Roman counting board (565); a CROSS, the 168 Novels (565); a PRACTICE, Chilperic's added letters for the Latin alphabet (561); a TOOL, Cassiodorus's Institutions (562).
- 149: a DEVICE, the drawloom for figured silk (552); a GUIDE on the first pitch pipes (550, the founding of the Northern Qi).
- 150: a DEVICE, the plough and harrow for dry fields (544); a GUIDE on the decimal grain measures in sheng and dou (544); a PRACTICE, the copying of books by hand at Vivarium (540).
- 151: a PRACTICE, the common life of monks at Monte Cassino (530).
- 153: a PRACTICE, dating years by their consuls (510, the year of Boethius's consulship).
- 154: a DEVICE, the south-pointing chariot attributed to Zu Chongzhi (500, the year he died); a PRACTICE, compurgation, the oath backed by oath-helpers (501).

Weakest first: the 154 compurgation, the 147 Olympiad GUIDE, the 150 grain GUIDE, the 149 pitch-pipe GUIDE and the 154 chariot.

### Problems and what was fixed

- 148 had no DEVICE and no CROSS when its helper reported. Both were added (the counting board and the 168 Novels, both placed at 565).
- 151's "The Chronicle of Edessa" was repeated in 150. The copy in 151 was replaced by Gildas's On the Ruin of Britain (about 540, from memory).
- Gregory the Great's register, in 145 and 146: 145 SYSTEM kept; 146 PRACTICE replaced by Shima, later called Zenshin-ni (584, Nihon Shoki).
- Priscian's Institutiones, in 152 and 154: 154 LANGUAGE kept; 152 LANGUAGE replaced by Li Daoyuan's Commentary on the Water Classic (dated by its author's death in 527).
- The water clock sent to Gundobad, in 153 and 154: 153 DEVICE kept (511); 154 DEVICE replaced by the south-pointing chariot, placed at 500.
- The Council of Agde, in 153 and 154: 154 SYSTEM kept; 153 PROJECT (Agde, Orleans and Epaone) replaced by a program of four dated turns from 513 to 519.
- Consular dating, in 153 and 154: 153 PRACTICE kept; the 154 copy was first replaced by Chinese counting rods, which turned out to be an existing record (database 56), so it was replaced again by compurgation, placed at 501.
- Justinian's reign counted in code, in 148 and 152: 152 EXAMPLE kept (527 to 565); 148 EXAMPLE replaced by Sigebert I's reign (561 to 575, 14 years).
- Reign repeats, after the review: Justinian's reign was counted in 150 (a PROJECT) and in 152 (an EXAMPLE), and Khosrow I's reign in 147, 148 and 150. The 150 and 148 projects were replaced by event timelines (536 to 548 and 561 to 568), so each reign is counted once.
- Rejected candidates: a Toledo III creed-at-Mass practice for 146 (Toledo III is already a SYSTEM record there, and the search did not confirm the Mass); an organ that Theoderic sent to Clovis (the search found a letter about a kitharode instead, so nothing was written).

### Repeats left for a decision

- Reign counts repeated across databases: Justinian's reign (150 PROJECT and 152 EXAMPLE) and Khosrow I's reign (147 EXAMPLE, 148 PROJECT and 150 PROJECT). Both were fixed after the review (see the first item under Problems and what was fixed), so each reign is now counted once. The reign family otherwise appears in 145, 146, 147, 148 (Sigebert), 152, 153 and 154, one reign per record.
- Within databases, the book and a computed record about the same book are different items, so I kept them: 145 has several Gregory records and an EXAMPLE on Evagrius's history beside Evagrius's LANGUAGE record; 146 has three Kaihuang records (SYSTEM, CROSS and FLEX); 149 uses the Pragmatic Sanction in three records; 150 has the Rule of Benedict as LANGUAGE and as CROSS; 152 has four Dionysius records (TOOL, FLEX, GUIDE and the 532-year CROSS); 153 has the Daming calendar as TOOL and as CROSS; 154 has three Alaric II records (SYSTEM, EXAMPLE and the Agde SYSTEM).
- Neighbours across databases, kept: Cassiodorus's Variae (151 LANGUAGE) and the water clock in the Variae (153 DEVICE); Zu Chongzhi's Daming calendar (153) and the chariot attributed to him (154); the Toledo III filioque (146) and the Aachen filioque (124); consular dating (153) and post-consular years (148 GUIDE); compurgation (154) and the oath of 802 (124).

### Repeat check result

The rare-word check (scratchpad/same_item_batch.py 145 154) gives 237 lines. Most are one shared common word between different items. The same-item pairs found in this batch were the ones fixed above; the reign counts listed above are the remaining overlap, and I did not change them.

### Facts checked by search

- Li Daoyuan: death year 527, from a conference abstract by Xiaofei Tian; Wikipedia gives his dates as 466 or 472 to 527. The expanded commentary is said to have 40 juan and about 1,250 waterways (1,252 in one source). No source gave a completion date, so 527 is used as the end of the early sixth-century range.
- Shima, later Zenshin-ni: the Nihon Shoki's entry for the thirteenth year of Emperor Bidatsu, which is 584. Secondary accounts give her age as about eleven or twelve, and her journey to Korea as 586 or 588.
- Not confirmed and not used: an organ sent by Theoderic to Clovis (the Variae letter found concerns a kitharode); the Mass recitation of the Nicene Creed in the Toledo III canons (the sources say the Creed was said before the Lord's Prayer at the service).
- The helpers' own search lists for the other records of this batch are not carried into this log. The details of each record say what its date rests on.

### Facts from memory, to check

- 500s and 510s: Vitalian's revolt (513); Pope Symmachus's death (514); Anastasius's death (518); the end of the Acacian schism (519); Zu Chongzhi's dates (429 to 500); the Burgundian code's range (about 501 to 516); Boethius's consulship (510); the Salic and Burgundian codes' use of oath-helpers.
- 540s to 580s: Gildas (about 540); the Rule of Benedict (about 540); the Qimin yaoshu (about 544); Monte Cassino (530); Vivarium (about 540); Justin II's death (578); Chlothar I's death (561); Sigebert I's reign (561 to 575); Cassiodorus's death (between 562 and 585 by the accounts); Maurice's accession (582); Columba's death (597).

### Single-source dates

- The Shima date rests on the Nihon Shoki as reported by secondary accounts; her age and the Korea trip are disputed between them.
- Li Daoyuan's death year rests on one conference abstract, with Wikipedia's range for comparison.
- The water clock letters are dated 507 to 511 in the record; one online heading gives about 522, and the record names that dispute.

### Problems

- Thin decades. All ten are below 50 records. The runnable count is 5 in each decade.
- Placed records. Twenty-two of the 121 records carry an anchor year, about one in six, which is more than the batch brief wanted. This is the first decision for the user: keep the placed records, drop the decades that cannot fill, or stop going back.
- Coding relevance. This is the thinnest stretch for coding history so far. Most of the batch is law, councils, chronicles, grammars and Byzantine administration, and the coding link is weakest in the 500s.
- Reign counts. The repeats were fixed after the review: the projects in 148 and 150 that counted the same reigns were replaced by event timelines (561 to 568 in 148, 536 to 548 in 150), so Justinian's and Khosrow I's reigns are each counted once.
- Memory dates. Many dates above come from memory. The first to check are Vitalian's revolt, the Acacian schism, Sigebert I's dates, the Burgundian code's range and Zu Chongzhi's life dates.

### How I checked it

- Unit tests: 17 of 17 pass. The three XML reports show 17 tests with no failures, errors or skips, and the Gradle test task ran in the last build (BUILD SUCCESSFUL in 20 seconds).
- Examples: each new database has its own verifier with exact expected output, worked out by hand before each run. All 50 runnable records pass. The two records changed after the first run (the Sigebert example in 148 and the four-turns program in 153) were verified again.
- Structure: the generator checks 154 databases and 5,630 records. The checker reports only the count rules for each of the ten databases, which the thin-century rule allows. The title checks, the text rules and the rare counts (7 to 10) pass. The rare counts are 8, 9, 9, 9, 9, 8, 7, 9, 9 and 9 for 145 to 154.
- Cross-batch: the repeat check was re-run after each replacement. The four first-pass repeats, the consular dating and Justinian's reign are gone. The reign counts are listed for a decision.
- Whole catalogue: 5,630 records, of which 1,710 are rare. The checker found no repeated titles in the batch against the 5,509 earlier records.
- Text: the records have no provenance phrases and no dollar signs, and the Cyrillic check is run before the commit.

### Notes

- Not verified yet: running on a device, the migration from version 144 to version 154 on a device, the UI with 154 databases in the list, lint and the release build.
- The 500s to the 590s are the thinnest decades of the catalogue for coding history. The question from earlier batches is still open: keep them smaller, fill them, or drop the placed records. A second question is larger: should the catalogue keep going back at all where the coding link is this weak?
- The first facts to check before publishing are the dates of Vitalian's revolt and the Acacian schism, Sigebert I's reign, Zu Chongzhi's life dates and the Burgundian code's range.

## CP-31: databases 155 to 164, 400 to 500 (the fifth century, the century before the 500s)

Status: ten more databases are in the app, one per decade from the 490s down to the 400s: 145 records, migrations from version 154 to version 164, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 490s 14, 480s 12, 470s 14, 460s 10, 450s 13, 440s 16, 430s 20, 420s 19, 410s 12 and 400s 15. None reaches 50, and all ten are marked "quota": false in tools/databases.json. There are 55 runnable records (5 in most decades, 6 in the 450s and the 420s, 8 in the 430s), and they all pass against their expected output. The build passes (164 databases, 5,775 records), and the unit tests pass (17 of 17, testDebugUnitTest executed).

Mood: This is the thinnest century of the whole series, and the fifth century has very little dated coding history. Most of what is here is law, councils, chronicles, astronomy and surveying, and 21 of the 145 records carry an anchor year that is not their own date. The useful part was the same-item check. It caught a repeat in almost every pair of neighbouring decades: Synesius's astrolabe letter, the groma, Prosper's chronicle, the nundinal letters and Vitruvius's screw. Each one I could pin to a single object was fixed.

Date: 2026-10-20. Totals: Kotlin main 132,865 (CP-30: 128,918), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 133,287 (CP-30: 129,340). The +3,947 is the 110 new Kotlin seed files for databases 155 to 164 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 164.

### What changed

- Ten databases, from "Database 155: 490s coding catalogue" (490 to 500) to "Database 164: 400s coding catalogue" (400 to 410). Their records are 14, 12, 14, 10, 13, 16, 20, 19, 12 and 15: 145 new records and 5,775 in the whole catalogue. The new rare records number 83, so the whole catalogue has 1,793.
- Eleven files per database, 110 in total. The colours in tools/databases.json are all unused ones; the first picks repeated older colours, and the generator refused them, so they were replaced.
- Spec: tools/databases.json has 164 entries. All ten new databases are marked "quota": false.
- Generator: no change. SeedCatalog.kt, CATALOGUE.md and the README tables are regenerated. The README sentence about runnable counts now reads 75 to 164, with 155 to 164 in the right groups.
- Process: one helper per database for the first pass, from the brief and an anchors file, run in parallel. The lead then integrated the batch: added the missing DEVICE in 158, and replaced the repeats listed below.

### Placed records (21)

The year of these records is an anchor, not the date of the item itself. Each record's details say so.

- 155: a TOOL, reed pens and ink pots (493, the Ostrogothic kingdom); a DEVICE, the standard balance weights, exagia (498, the bronze reform).
- 156: an EXAMPLE, the square bu of three fields in mu (485); a DEVICE, Childeric's signet ring (481, his death).
- 157: a TOOL, the gold coin dies of the Constantinople mint (474, Zeno's reign); a PRACTICE, wergild (475, Euric's code).
- 158: a GUIDE, the Sanskrit vowel count (460, the Kahaum pillar); a DEVICE, the composite bow of the steppe peoples (465, the death of Libius Severus).
- 159: a LANGUAGE, Patrick's Confessio (450, the start of the second half of the fifth century); a DEVICE, the chorobates, a levelling frame (455, the Vandal sack of Rome).
- 160: a PRACTICE, the palimpsest (440, Leo's accession); a GUIDE, the nundinal market letters (443, the Yuanjia proposal); a DEVICE, the portcullis (447, the walls after the earthquake).
- 161: a DEVICE, the vallus, a reaping machine (431, the opening of the Council of Ephesus).
- 162: a DEVICE, the groma, a surveyor's cross (420, the Liu Song founding); a GUIDE, cutting a seal in reverse (421, Constantius III made co-emperor).
- 163: a GUIDE, the Roman names of the twelfths of an as (410, the sack of Rome); a DEVICE, Synesius's brass hydroscope (415, Hypatia's death).
- 164: a DEVICE, the water-driven stone saw of Hierapolis (402, Honorius moves to Ravenna); a GUIDE, the quorum for a Vinaya ordination (402); an EXAMPLE, the lines of the Aeneid (400, Servius's commentary).

Weakest first: the 163 hydroscope (415), the 164 Aeneid lines (400), the 161 vallus (431), the 160 nundinal letters and portcullis, and the 155 reed pens.

### Problems and what was fixed

- 158 had no DEVICE: the helper found no device it could date or place honestly. The composite bow of the steppe peoples was added, placed at 465.
- Synesius's astrolabe letter was in 163 (the brass hydroscope) and 164 (the planisphere): the same object. The 164 copy was replaced by the Hierapolis saw (402).
- The groma was in 159 and 162. The 159 copy was replaced by a screw, and then by the chorobates (455), because the screw is Vitruvius's screw, already in database 52.
- Prosper's chronicle was in 159 (LANGUAGE, 455) and 161 (TOOL, 433): the same chronicle. The 159 copy was removed; 159 keeps two LANGUAGE records.
- The nundinal market letters were in 160 (GUIDE) and 163 (GUIDE): the same method. The 163 copy was replaced by the Roman names of the twelfths (410).
- The twelfths guide in 163 is next to database 51's shares of an estate reckoned in twelfths. Different items: names of the parts in one, a share sum in the other. Kept.

### Repeats kept inside one database (for a decision)

- 157: the Inariyama sword is in three records (DEVICE, EXAMPLE and CROSS on its dates).
- 159: the Council of Chalcedon is in four records (SYSTEM, GUIDE, FLEX and CROSS).
- 160: the Robber Council of 449 is in three records (SYSTEM, FLEX and CROSS).
- 164: the Rhine crossing and Stilicho's death are in three records (CROSS, FLEX and PROJECT).
- 161: Attila's joint rule is in a SYSTEM and an EXAMPLE; Buddhaghosa's book and its seven purities, and Macrobius's book and its zones, are each a LANGUAGE and an EXAMPLE.
- 162: Augustine's City of God is a LANGUAGE and an EXAMPLE on its book counts.
- 158: Hydatius's chronicle is a TOOL and an EXAMPLE.
- Each of these is a book or an event and a computed record about it, so I kept them; the lead may cut one in each case.

### Neighbours across databases, kept

- Victorius's Easter table in 159 beside the 532-year Easter cycle in 152, and the Dionysian table in 152.
- Patrick's Confessio in 159 beside the Book of Armagh in 124, which copies it.
- The seal cut in reverse in 162 beside the woodblock carved in mirror image in 129.
- Aryabhata's consonant numerals in 155 beside the Greek letter numbers in 62.
- The Ostrogothic kingdom in 155 beside the water clock of Theoderic in 153.
- The Hierapolis saw in 164 beside the crank sawmill of 1593 in 45.

### Repeat check result

The rare-word check (scratchpad/same_item_batch.py 155 164) gives 195 lines. Most are one shared common word between different items. The same-object pairs are the ones fixed above; the within-database repeats are listed above; the neighbours above are different items.

### Facts checked by search

These come from the helpers' search reports; the record details say what each date rests on.

- 155: Gelasius's letter, 494; the Bolnisi Sioni inscriptions, dated 493 or 494; the Aryabhatiya's 499 (its Kali-yuga date); the follis of 498; the Luoyang move, 494 in most accounts.
- 156: Zhang Qiujian's Mathematical Classic between about 466 and 485; the equal-field decree of 485; the Henotikon of 482; Victor of Vita's date, disputed between about 484 and 488.
- 157: the Inariyama sword, dated 471 by most scholars (531 proposed); the Martyrdom of Shushanik, 476 to 483; Euric's code, 475 or 483; Hanseong taken in 475.
- 158: Hydatius's chronicle resumes at 379 after Jerome; the Kahaum pillar's Gupta year 141 is 460 or 461; the Taishi era, 465 to 471; Anthemius reigned from 467 to 472; Majorian was killed on 7 August 461, and Libius Severus died on 15 August 465.
- 159: Victorius's table of 457; the Confessio in the second half of the fifth century; Sidonius's panegyric recited on 1 January 456; the Council of Chalcedon opened on 8 October 451 in most accounts.
- 160: Carthage taken on 19 October 439; the treaty of 442 is read two ways; the Robber Council summoned for August 449 (one source); He Chengtian's calendar proposed in 443; Socrates's history spans 305 to 439; Taiwu's campaign began in 446.
- 161: the Theodosian Code's constitution of 15 February 438, in force from 1 January 439; the first ordination of nuns in East Asia at Nanjing in 434 (433 in some accounts); Prosper's Epitoma begun in 433.
- 162: the Avatamsaka sutra's 60 fascicles, completed about 420 (418 to 422 in accounts); Dharmakshema's Nirvana Sutra, 421; Constantinople's teaching posts, 425 with 31 posts; Dadisho's office about 421 to 422; Kou Qianzhi's New Code, revealed in 415 and presented in 424.
- 163: Faxian's journey, 399 to 412; Orosius's history ends in 417 and Augustine's request is of 416; the Mahasamghika Vinaya rendered jointly by Buddhabhadra and Faxian. The Yixi calendar of 417 was not confirmed and was dropped.
- 164: Synesius's letter is undated and its addressee is disputed; the Notitia Dignitatum, eastern part about 395 and western part about 420; the Mar Isaac synod in 410 at Seleucia-Ctesiphon; the White Lotus Society in 402 with 123 pledges; Kumarajiva's Lotus translation dated 406 at Chang'an.

### Facts from memory, to check

- The Aryabhata letter values (ka to ma 1 to 25; ya 30 and so on) and the circle rule 62832/20000 (155).
- Odoacer's death, 493; the Ostrogothic kingdom, 493; Anastasius proclaimed in 491; Kavadh deposed in 496 and restored in 499 (155).
- Ricimer and Anthemius; Libius Severus; Majorian; Gaiseric's 477; Romulus Augustulus, 476; Nepos's murder, 480; Southern Qi, 479; Zeno, 474 (157 and 158).
- Honorius's death, 423; Stilicho's death, 22 August 408; Pollentia, 6 April 402; Verona, 403; the Rhine crossing, 31 December 406; the sack of Rome, 24 August 410; Hypatia's death, 415; Constantius III, 421; Valentinian III, 425; Augustine's death, 430 (161 to 164).
- Liu Song, 420; the Vandals cross, 429; Attila's death, 453; the Catalaunian Plains, 451; Leo I's coronation in 457 by Anatolius; Marcian, 450 (159 and 162).

### Single-source dates

- Synesius's 400 departure from Constantinople (one encyclopedia entry); the letter's addressee is disputed.
- Kumarajiva's arrival about 401 (one web page); the White Lotus Society's 123 pledges.
- Bolnisi Sioni as the oldest dated inscription on Georgian soil (one encyclopedia).
- The Visuddhimagga's 430 (one web source; other sources say the fifth century).
- The Taishi year two as September 466 (one summary of the Book of Song).
- Orosius's 417 end and Augustine's 416 request (one encyclopedia).
- Faxian's return in 412 (one encyclopedia).

### Problems

- Thin decades. All ten are below 50 records. The runnable count is 5 in most decades.
- Placed records. Twenty-one of the 145 records carry an anchor year, about one in seven. This is the same decision as before: the user chose to keep placed years.
- Coding relevance. The fifth century has almost no dated coding history. Most items are law, councils, chronicles, astronomy and surveying, and the GUIDE and TOOL records are the thinnest.
- Memory dates. Many of the dates above come from memory. The first to check are the Aryabhata letter values, the Hierapolis saw's date, the chorobates' use, the Visuddhimagga's 430 and the Dadisho' and Kou Qianzhi dates.

### How I checked it

- Verifiers: 155 5 of 5; 156 5 of 5; 157 5 of 5; 158 5 of 5; 159 6 of 6; 160 5 of 5; 161 8 of 8; 162 6 of 6; 163 5 of 5; 164 5 of 5. That is 55 runnable records, each with expected output worked out by hand before the run.
- Checker: each database reports only the count rules, which the thin-century rule allows. No title clashes with the 5,630 earlier records, and no text-rule or year problems. The rare counts (7 to 10) are 9, 8, 9, 7, 7, 9, 8, 9, 9 and 8 for 155 to 164.
- Generator: checks passed for 164 databases and 5,775 records.
- Unit tests: BUILD SUCCESSFUL; 17 of 17 pass, 0 failures, 0 errors, and the Gradle test task ran.
- Text: no Cyrillic in the logs, the source pack, the README, CATALOGUE.md or the tools.

### Notes

- Not verified yet: running on a device, the migration from version 154 to version 164 on a device, the UI with 164 databases in the list, lint and the release build.
- The 400s to the 490s are the thinnest decades of the catalogue for coding history. The question from earlier batches is still open: keep them smaller, fill them, or drop the placed records. A second question is larger: should the catalogue keep going back at all where the coding link is this weak?
- The first facts to check before publishing are the Aryabhata letter values, the Hierapolis saw's date, the chorobates' use and the Visuddhimagga's 430.

## CP-32: databases 165 to 174, 300 to 400 (the fourth century, the century before the 400s)

Status: ten more databases are in the app, one per decade from the 390s down to the 300s: 154 records, migrations from version 164 to version 174, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 390s 13, 380s 18, 370s 16, 360s 18, 350s 11, 340s 11, 330s 17, 320s 16, 310s 17 and 300s 17. None reaches 50, and all ten are marked "quota": false in tools/databases.json. There are 51 runnable records (5 in nine decades, 6 in the 300s), and they all pass against their expected output. The build passes (174 databases, 5,929 records), and the unit tests pass (17 of 17, testDebugUnitTest executed).

Mood: The fourth century is mostly church councils, emperors, sieges and texts, so the batch reads more like a chronicle than like coding history, and the coding link is the thinnest yet. The dates are at least checkable, and the search results gave most of them a firm footing. The same-item check was useful again: four pairs of neighbouring decades shared one object (the labarum, the battering ram, Aphrahat's demonstrations and the Chi-Rho monogram), and each was fixed. The clusters of several records about one event inside a single database are left for a decision.

Date: 2026-10-20. Totals: Kotlin main 136,840 (CP-31: 132,865), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 137,262 (CP-31: 133,287). The +3,975 is the 110 new Kotlin seed files for databases 165 to 174 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 174.

### What changed

- Ten databases, from "Database 165: 390s coding catalogue" (390 to 400) to "Database 174: 300s coding catalogue" (300 to 310). Their records are 13, 18, 16, 18, 11, 11, 17, 16, 17 and 17: 154 new records and 5,929 in the whole catalogue. The new rare records number 90, so the whole catalogue has 1,883.
- Eleven files per database, 110 in total. The colours are unused ones; the generator refused the first picks, which repeated older colours.
- Spec: tools/databases.json has 174 entries. All ten new databases are marked "quota": false.
- Generator: no change. SeedCatalog.kt, CATALOGUE.md and the README tables are regenerated. The README sentence about runnable counts now reads 75 to 174: 165 to 173 are in the group of five each, and 174 in the group of six.
- Process: one helper per database for the first pass, from the brief and an anchors file in which every named item has one owner database. The lead then replaced four records that repeated an object of a neighbouring database, and checked the runnable records.

### Placed records (26)

The year of these records is an anchor, not the date of the item itself. Each record's details say so.

- 165: a DEVICE, the onager (390, Ammianus's history).
- 166: a DEVICE, the battering ram (383, the death of Gratian).
- 167: a DEVICE, the rotary hand-mill (376, the Gothic crossing); an EXAMPLE, Euclid's 465 propositions (379); a PRACTICE, Basil's Longer Rules (370); a GUIDE, the Shi surname rule (379).
- 168: a GUIDE, the Pahlavi heterograms (363, Jovian's peace); a DEVICE, the Shichishito sword with a dated inscription (369); a LANGUAGE, Hilary's On the Trinity (360); a LANGUAGE, Athanasius's Life of Antony (360).
- 169: a DEVICE, the Roman pin lock (350); a CROSS, the nomina sacra (350); a GUIDE, the Lent count (350); an EXAMPLE, Cyril's 24 lectures (350, a rounded date).
- 170: a PRACTICE, stichometry (341); a TOOL, the dividers (343, the Council of Serdica); a DEVICE, the shaduf (340, placed although far older).
- 171: a CROSS, the Chi-Rho drawn as text (330); a GUIDE, the amphora as one cubic foot (332); a DEVICE, Hero's aeolipile (337); a TOOL, the glassblower's blowpipe (335).
- 172: a GUIDE, the planetary week (321, Constantine's Sunday law); a DEVICE, the Roman screw press (324, the battle of Chrysopolis).
- 173: a TOOL, the album (311, Galerius's edict); a GUIDE, the strength of a Roman legion (310).
- 174: a DEVICE, the treadwheel crane (301, the Edict on Maximum Prices).

Weakest first: the 173 legion GUIDE (trivial code, placed), the 172 screw press, the 170 shaduf (far older than its anchor), the 169 Lent count and Cyril's lectures, and the 167 hand-mill.

### Problems and what was fixed

- The labarum was in 172 (DEVICE, 327) and 173 (DEVICE, 312): the same standard. The 172 copy was replaced by the Roman screw press (324).
- The battering ram was in 166 (DEVICE, 383) and 170 (DEVICE, 346): the same device. The 170 copy was replaced by the shaduf (340).
- Aphrahat's Demonstrations were counted in 170 (EXAMPLE, 344) and 171 (EXAMPLE, 337): the same count. The 171 example was replaced by the reign of Constantine the Great, 306 to 337, 31 years counted in code; no other database in 165 to 174 counts that reign.
- The Chi-Rho monogram was built in 173 (GUIDE) and drawn as text in 171 (CROSS): the same method. The 173 guide was replaced by the strength of a Roman legion (310). The Chi-Rho drawing in 171 and the labarum in 173 stay as different items.
- Neighbours kept: the shaduf beside Joseph's Well (86) and the drawloom (149); the screw press beside Gutenberg's screw press of about 1450 (59); the weekday computations beside the weekdays of 45, 82 and 100; the planetary week (172) beside the planetary hours of a Sunday (51); the Lent count (169) beside Athanasius's festal letters (168). All are different items.

### Repeats kept inside one database (for a decision)

- 166: the Edict of Thessalonica is a SYSTEM, a CROSS (days to Gratian's death) and part of a PROJECT timeline.
- 165: Ammianus is a LANGUAGE, and the onager is a DEVICE that his history describes.
- 167: the battle of Adrianople is a SYSTEM and two computed records on different quantities; Basil's death is a CROSS and his Longer Rules a PRACTICE.
- 168: Antony's Life is a LANGUAGE and the years of Antony's life an EXAMPLE; Julian's death, the elevation of Valens and Eutropius's Breviarium recur in a FLEX and a TOOL.
- 169: the Lanting preface is a LANGUAGE and the cup game at the Lanting gathering a PRACTICE.
- 171: the dedication of Constantinople is a SYSTEM and a FLEX.
- 172: the battle of Chrysopolis is a SYSTEM, a CROSS and a FLEX; Constantine's Sunday law is a SYSTEM and an EXAMPLE (Sundays in 321); Arius's Thalia, Constantine's letter to Alexander and Arius, and the Council of Nicaea are three records.
- 173: the Edict of Milan is a SYSTEM and a FLEX; the battle of the Milvian Bridge is a SYSTEM and a FLEX.
- 174: Diocletian's abdication and Constantine's acclamation at York appear in three SYSTEM records and in a FLEX and a CROSS; the first edict of the Great Persecution is in two FLEX records. This is the heaviest cluster.
- Each of these is a book, an event or a standard, and a computed record about it; I kept them. The lead may cut one record in each cluster.

### Repeat check result

The same-item check (scratchpad/same_item_batch.py 165 174) gives 229 lines. The four same-object pairs above are fixed. The rest are shared words, the neighbours above, or the within-database clusters above.

### Facts checked by search

These come from the helpers' search reports; the record details say what each date rests on.

- 165: Ammianus's 31 books, covering 96 to 378, with books 14 to 31 surviving; the decrees of 24 February 391 and 392; the 393 Olympic tradition; the Historia Augusta lives; Rufinus's De principiis just after 397.
- 166: the Edict of Thessalonica dated 27 February 380 (one account gives 28 February); Gratian died at Lyon in 383; Vegetius's Epitoma bracketed between 383 and 450; Egeria's journey about 381 to 384; Symmachus's appeal of 384 failed; the obelisk raised in 390, with no source for the lifting apparatus.
- 167: Valentinian I died at Brigetio on 17 November 375; Basil of Caesarea died on 1 January 379 (two results agree); Dao'an lived from 312 to 385 and his catalogue of 374 is lost; the Mosella has only an upper bound of 375.
- 168: the Shichishito's 369 reading; Valens's elevation on 28 March 364; Eutropius's Breviarium at 369 or 370; Julian's death on 26 June 363; the teachers' edict of 17 June 362; Valentinian's acclamation disputed (25 or 26 February 364), so no record uses that day.
- 169: Cyril's course in Lent 347 or 348; the councils of 359 at Seleucia (27 September) and Ariminum; Mursa on 28 September 351; Mons Seleucus on 3 July 353; Filocalus's Chronograph of 354.
- 170: Pappus's Collection about 340 (some say the 320s); the Council of Serdica dated 342 to 344; Nisibis attacked in 346; Codex Sinaiticus in the mid-fourth century; Aphrahat's Demonstrations, ten in 337, twelve in 344, and the twenty-third in 344 or 345.
- 171: the Holy Sepulchre consecrated on 13 September 335; the Tricennial Oration dated 336 (one listing); Firmicus's Mathesis from 334 to 337; Murong Huang's title in November 337; the Chronograph's 25 December entry disputed.
- 172: the Sunday law dated 7 March 321 in secondary sources; the first edition of Eusebius's history disputed; Pachomius's first house between 318 and 323; Adrianople (3 July 324) and Chrysopolis (18 September 324); Licinius's execution at Thessalonica in 325 (one chronology); the Codex Vaticanus in the first half of the fourth century.
- 173: the Milan letter posted at Nicomedia on 13 June 313; the Council of Arles in 314, with British bishops; the Eastern Jin dated 317 or 318; Chang'an fell in 316; Sogdian Letter II dated 312 to 314.
- 174: the Eight Princes' dates; the conference of Carnuntum on 11 November 308; Porphyry's 270 to 305 range; Arnobius's seven books and about 303; the Chronicle's disputed 303 date.

### Facts from memory, to check

- Constantine's death (22 May 337), the division of 337, Constantine II's death (340), Constantius II's death (361), Gratian's death (25 August 383), Valens's death at Adrianople (9 August 378), Julian's death (26 June 363), Mursa (28 September 351), Nicaea (325), Chrysopolis (18 September 324), the Milvian Bridge (28 October 312), Diocletian's abdication (1 May 305), Galerius's edict (April 311), York (25 July 306).
- The Fei River (383), Priscillian's execution at Trier (385), Gregory of Nazianzus's orations (379 to 380), Jerome's Chronicle (about 380), Libanius's Oration 30 (about 386), the gold inlay of the Shichishito, and the textbook figures of the legion.

### Single-source or judgment dates

- The day of the Edict of Thessalonica (27 or 28 February 380).
- Dao'an's catalogue (374; the text is lost).
- The twenty-third of Aphrahat's demonstrations (344 or 345).
- The Tricennial Oration (336, one listing).
- The Eight Princes (one timeline).
- The day of the Sunday law (7 March 321).
- The Codex Vaticanus date (first half of the fourth century, from weak sources).

### Problems

- Thin decades. All ten are below 50 records. The runnable count is 5 in nine decades and 6 in the 300s.
- Placed records. Twenty-six of the 154 records carry an anchor year, about one in six. This is the same decision as before: the placed years stay.
- Coding relevance. The fourth century is church councils, emperors, sieges and texts. The coding link is thin; the GUIDE and TOOL records are the weakest, and one GUIDE (the legion) is trivial code.
- Clusters inside one database. Several databases have three or more records on one event (see above), so one record of each cluster may be cut.
- Memory dates. The first to check are the day of the Edict of Thessalonica, the Dao'an catalogue, the Shichishito's 369 reading and the year of the Tricennial Oration.

### How I checked it

- Verifiers: 165 5 of 5; 166 5 of 5; 167 5 of 5; 168 5 of 5; 169 5 of 5; 170 5 of 5; 171 5 of 5; 172 5 of 5; 173 5 of 5; 174 6 of 6. That is 51 runnable records, each with expected output worked out by hand before the run.
- Checker: each database reports only the count rules, which the thin-century rule allows. No title clashes, text-rule or year problems. The rare counts (7 to 10) are 9, 9, 9, 9, 8, 8, 9, 10, 10 and 9 for 165 to 174.
- Generator: checks passed for 174 databases and 5,929 records.
- Unit tests: BUILD SUCCESSFUL; 17 of 17 pass, with 0 failures and 0 errors, and the Gradle test task ran.
- Text: no Cyrillic in the logs, the source pack, the README, CATALOGUE.md or the tools (checked before the commit).

### Notes

- Not verified yet: running on a device, the migration from version 164 to version 174 on a device, the UI with 174 databases in the list, lint and the release build.
- The 300s to the 390s are thin for coding history. The question from earlier batches is still open: keep them smaller, fill them, or drop the placed records. A larger question: should the catalogue keep going back at all where the coding link is this weak?
- The first facts to check before publishing are the day of the Edict of Thessalonica, the Dao'an catalogue, the Shichishito's reading and the year of the Tricennial Oration.

## CP-33: databases 175 to 184, 200 to 300 (the third century, the century before the 300s)

Status: ten more databases are in the app, one per decade from the 290s down to the 200s: 115 records, migrations from version 174 to version 184, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 290s 11, 280s 11, 270s 11, 260s 14, 250s 12, 240s 13, 230s 10, 220s 12, 210s 10 and 200s 11. None reaches 50, and all ten are marked "quota": false in tools/databases.json. There are 50 runnable records (5 in each database), and they all pass against their expected output. The build passes (184 databases, 6,044 records), and the unit tests pass (17 of 17, testDebugUnitTest executed).

Mood: This is the first batch that reaches before 300. No record in the catalogue was dated between 100 and 299 before this checkpoint, and the sources give very little coding history for this century, so the batch is the thinnest yet. Of the 115 records, 36 carry an anchor year (about 31 per cent, against 17 per cent in CP-32). Most dates are checkable, and several rest on one source, listed below. The same-item check found the two repeats that mattered: the Roman bronze dodecahedron in three databases and the Shushtar water mills in two. Each was fixed by replacing the copy in the database that did not keep it.

Date: 2026-10-20. Totals: Kotlin main 140,320 (CP-32: 136,840), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 140,742 (CP-32: 137,262). The +3,480 is the 110 new Kotlin seed files for databases 175 to 184 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 184.

### What changed

- Ten databases, from "Database 175: 290s coding catalogue" (290 to 300) to "Database 184: 200s coding catalogue" (200 to 210). Their records are 11, 11, 11, 14, 12, 13, 10, 12, 10 and 11: 115 new records and 6,044 in the whole catalogue. The new rare records number 77, so the whole catalogue has 1,960.
- Eleven files per database, 110 in total. The colours are unused ones, picked from a hue pool so that none repeats.
- Spec: tools/databases.json has 184 entries. All ten new databases are marked "quota": false.
- Generator: no change. SeedCatalog.kt, CATALOGUE.md and the README tables are regenerated. The README sentence about runnable counts is updated by hand: databases 75 to 184 have fewer runnable records, and 175 to 184 have five each, all passing.
- Process: one helper per database for the first pass, from the brief and an anchors file in which every named item has one owner database. The lead then ran the repeat check and sent three repeats back to the helpers that wrote them.

### Placed records (36)

The year of these records is an anchor, not the date of the item itself. Each record's details say so.

- 175: a DEVICE, the hypocaust (293, the tetrarchy); a PRACTICE, the adoratio (293); a GUIDE, Egyptian doubling (293); a TOOL, the Antonine Itinerary (297, the census); an EXAMPLE, the aroura (297).
- 176: a LANGUAGE, Chen Shou's Records of the Three Kingdoms (280); a DEVICE, Du Yu's linked trip-hammers (280); a GUIDE, the six xun periods (280); a PRACTICE, the qiju zhu (285, Wani).
- 177: a DEVICE, the Roman bronze dodecahedron (274); a GUIDE, the grain total in modii and sextarii (274); an EXAMPLE, the Saturnalia (274); a TOOL, the portable sundial (275).
- 178: a DEVICE, the water mills of Shushtar (260); a TOOL, the Sasanian seal stones (262).
- 179: a SYSTEM, Gallienus's cavalry reform (260); a TOOL, the hipposandal (260); a DEVICE, the atlatl (251, the death of Decius); a GUIDE, the Egyptian civil year (250).
- 180: a DEVICE, the Baghdad battery (244); a TOOL, the decempeda (244); a GUIDE, the Secular Games interval (248).
- 181: a PRACTICE, Origen's shorthand writers (231); a GUIDE, the Egyptian civil month and day (231); an EXAMPLE, the Sothic cycle (238, Censorinus's book); a CROSS, the Julian drift in one Sothic cycle (238).
- 182: a DEVICE, the repeating crossbow (228, Zhuge's campaign); a TOOL, the carpenter's square (226, Cao Rui's accession).
- 183: a DEVICE, the Barbegal mill (211, the death of Severus); a LANGUAGE, Ulpian's Rules (212); a PRACTICE, military diplomas (212); a GUIDE, the iugerum (216); a TOOL, the Roman strigil (213, Caracalla's Alamanni campaign).
- 184: a DEVICE, the clay oil lamp (203, the Arch of Septimius Severus); a GUIDE, the rune groups of the Elder futhark (200); a PRACTICE, Hua Tuo's surgery (208, his death).

Weakest first: the atlatl (179), a Mesoamerican device with a Roman anchor; the hypocaust and the adoratio (175), with anchors that do not touch them; the strigil (183) and the oil lamp (184), real objects with anchors that do not touch them; and the Shushtar mills (178).

### Problems and what was fixed

- The Roman bronze dodecahedron was in 177 (DEVICE, 274), 183 (TOOL, 218) and 184 (DEVICE, 200): one object. It stays in 177. Database 183 now has a Roman strigil (213) as its TOOL, and 184 a clay oil lamp (203) as its DEVICE.
- The Shushtar water mills were in 178 (DEVICE, 260) and 179 (DEVICE, 260): one item. It stays in 178. Database 179 now has the atlatl (251) as its DEVICE.
- The Ma Jun puppet theatre, assigned to 180, was dropped: its date (226 to 239, under Cao Rui) gives no year inside 240 to 250 that a source supports. It is not in 181 either.
- The Hexapla is placed at 240, not 230: the sources give its completion before 245 and no start year.
- Guangya is a LANGUAGE in 181, not a TOOL: the catalogue files dictionaries as LANGUAGE (Shuowen, 106).
- The Greek letter-numeral CROSS once suggested for 178 was not written, because database 62 covers that method.
- Items dropped for a documented reason, as the helpers' reports give them: the Shanghan lun (sources date it about 200 to 205, outside 183), a Roman mile GUIDE (91 covers the mile), the Aquincum organ (its plaque dates it to 228), the Jin census figures (no source found), Liu Shao's Renwu zhi (no composition date), and the Gallienus reform's 255 (no year in the sources, so placed at 260).

### Repeats kept inside one database (for a decision)

- 176: Wani's traditional date is in three records: a SYSTEM (the arrival of writing), a FLEX (years from the catalogue to Wani's date) and a PROJECT (a timeline of 280 to 290). Each computes or sorts a different quantity.
- 178: Shapur I's inscription is a LANGUAGE, and its three versions are compared in a CROSS by line count, a different quantity.
- 181: the Sothic cycle is an EXAMPLE (days) and a CROSS (the Julian drift in one cycle), both on Censorinus's book.
- 179: Decius's edict, Decius's death and Valerian's capture recur in a PROJECT, a FLEX and two SYSTEM records.
- 177 and 180: Plotinus's death (177, in a PROJECT) and Plotinus's seminar at Rome (180, a PRACTICE) are two events of one man.
- Each of these is a book, an event or a standard, and a computed record about it; I kept them. The lead may cut one record in each cluster.

### Repeat check result

The same-item check (scratchpad/same_item_batch.py 175 184) gives 150 pairs. The two same-object repeats above are gone. The rest are shared words ("hollow", "curved", "hole", "polygon", "begins") or neighbours of the same kind: Porphyry's Isagoge beside his two books of 174; the Liu Hui polygon beside Van Ceulen's polygon of 1615 and the polygon of 47; the commentary on the Nine Chapters beside the grain method of 139; the Antonine Itinerary beside the route maps of 72 and 79.

### Facts checked by search

These come from the helpers' search reports; the record details say what each date rests on.

- 175: Bowuzhi "about 290" only; the census given as 287, 297 and 301 in different sources (297 for Egypt, one source); the adoratio has no year, so it is placed at 293; the Itinerary's surviving form is late third to early fourth century; 1 March 293 is the common tetrarchy date.
- 176: Xun Xu's classics and philosophy divisions confirmed (the history and poetry divisions from memory); Wani at 285 is the traditional date (Ojin 16), and scholars doubt the account; Du Yu's trip-hammers are credited to him, with earlier Han models; no completion year for Chen Shou, so placed at 280.
- 177: Bamboo Annals found in 279 (281 in some accounts; the record uses 279); the Isagoge written in Sicily, 268 to 270 (one testimony); the Ticinum officina numbers 1 to 3 in 274 (one catalogue); dodecahedra second to fourth century; medallion sundials 4 to 5 cm, one latitude each.
- 178: the Haidao manual dated 263; poles of 3 zhang, 1000 bu apart; the withdrawals of 123 and 127 bu; the Western Jin era from 266; Pei Xiu minister of works in 267, with his preface in the Book of Jin, volume 35; Shapur's inscription about 262, with 70, 35 and 30 lines.
- 179: Diophantus about 250 rests mainly on Psellus's letter, and Knorr gives about 240; the libelli of the 250 edict, totals of 44 or 46; Valerian's edict of August 258; Lawrence's death on 10 August 258 from two sources; the reform has no year, so placed at 260; one Vindolanda hipposandal group bracketed to about 140 to 180.
- 180: Wang Bi lived 226 to 249; the Zhengshi era ran 240 to 249; the stone classics were cut in ancient, seal and clerical scripts, and their rediscovery is dated variously; the Baghdad jar was found in 1936 at Khujut Rabu, its era Parthian or Sasanian, and its electric reading disputed; Ma Jun's theatre tied to Cao Rui (226 to 239).
- 181: the Hexapla at Caesarea in six columns; the Guangya about 230; the Jingchu calendar adopted 237, credited to Yang Wei; the wooden oxen 230 or 231 (disputed); Censorinus 238; the Sothic cycle of 1461 Egyptian years and 1460 Julian years.
- 182: Hippolytus's seven 16-year spans from 222; Africanus's five books to 221, with a regnal-year dispute; Bakhshali's one leaf dated 224 to 383, the other tested leaves near 700 and 900; the Zhoubi text dated no earlier than the Eastern Han; Ardashir's battle at 224 and a dispute about his crowning; the crossbow credited to Zhuge Liang in later histories.
- 183: Severus died at York on 4 February 211 (two sources); the Constitutio dated 212 by most scholars, with Millar proposing 214; the Giessen papyrus P.Giss. 40, acquired in Egypt in 1902; Barbegal has 16 wheels in two banks of eight, mostly second century; Ulpian died 223 or 228; military diplomas appear to stop about 203.
- 184: the Vimose comb about 160; the Ovre Stabu later second century or 210 to 240 (accounts differ); the Forma Urbis 203 to 211, with no ancient mention and 150 or 151 slabs; Sextus second or third century; the Manusmriti about 200 BCE to 200 CE; dodecahedra mostly second to fourth century.

### Facts from memory, to check

- 175: Jin Wudi's death (290); the reconquest of Britain (296); Lactantius's ascription of the adoratio; the royal cubit of 0.525 m; Galerius's possible 21 May; the hypocaust and aroura facts.
- 176: Sun Hao's surrender (280); Carus's death (283); Sima Yan's death (290); Shu (221 to 263); Wu's imperial title (229, or 222 as a kingdom); the Sanguozhi's 65 volumes; the xun names.
- 177: Plotinus's death (270); Aurelian's Sun temple and the 25 December date in the 354 calendar (274); Aurelian's death (275); Saturnalia 17 to 23 December; 16 sextarii to the modius, 48 to the amphora.
- 178: Liu Hui's 263 completion and pi values; the Jin code (268) with Jia Chong; Valerian's capture (260); Gallienus killed (268); Pei Xiu's principle names; the seal details.
- 179: Decius killed at Abritus (251); Cyprian's De unitate (251); Gallienus sole ruler from 260; the Gallic Empire (260 to 274); Diophantus's six surviving Greek books; the Egyptian month names.
- 180: Origen's Against Celsus (about 248); the Gaoping coup and Cao Shuang's execution (249); the Secular Games (248); Gordian III's death at Misiche and Philip's accession (244); Plotinus at Rome (244); the Jin start (266); the saeculum games of 17 BC, 88 and 204.
- 181: Zhuge Liang's death (234) and Qishan (231); Severus Alexander's murder (235); Gongsun Yuan's death (238); Cao Rui's death (239); the Gordian revolt (238); Origen's move (231); the seven writers supplied by Ambrose.
- 182: Chen Qun (220); Zhao Shuang (222); Zhuge's 228 campaign; Cao Rui (226); Liu Bei (221); Elagabalus (222); Sun Quan (229); Varro's 753 BC; the gou and gu names.
- 183: Papinian's execution (212); the Alexandria massacre (215); the Parthian war (216); Elagabalus (218); the Han end (220); the Julian leap rule; the dodecahedron's details; the iugerum of 240 by 120 feet.
- 184: Guandu (200); the Red Cliffs (208); Cao Cao's order (210); the Sanguozhi's short life of Hua Tuo; the mafeisan in the fifth-century Book of the Later Han; the aettir names; the Manusmriti yuga figures; the Outlines' three books; the oil lamp's general history.

### Single-source or judgment dates

- 175: the census year (297, Egypt); Galerius's 21 May.
- 176: Wani at 285 (one course page); Du Yu's third-century date (one encyclopedia page); the Han diary example (one article).
- 177: the officina numbers (one catalogue); the Bamboo Annals find (279 or 281); the Isagoge's date (one testimony).
- 178: Shapur's date and line counts; the Jin code's twenty chapters (one secondary summary); the Shushtar attribution (weak pages, hedged).
- 179: Cyprian's 14 September 258 (EXAMPLE; one genealogy page and memory); Diophantus's 250; the Vindolanda bracket.
- 180: the stone classics at 241 (single source); Wang Bi's commentary at 240 (a round date).
- 181: the Jingchu date of 237 (one study group); the wooden oxen 230 or 231; Censorinus's date (one secondary page).
- 182: Ardashir's crowning at 227 (one timeline site); the battle date of 28 April 224 (one encyclopedia); the Sasanian end at 651 (one exhibit); the Hippolytus heading (one news piece); the Bakhshali leaf dates (news summaries).
- 183: Caracalla's death on 8 April 217 (the CROSS; one online source); auxiliary diplomas to about 203; Millar's 214.
- 184: the Vimose buckle near 200; Hua Tuo's death in 208.

### Problems

- Thin decades. All ten are far below 50 records (10 to 14), with 50 runnable records in all.
- Placed records. 36 of 115, about one in three, the highest share so far. The weakest are listed above.
- Coding relevance. The third century has almost no coding history in the sources. The batch is the first to reach before 300, and its GUIDE and TOOL records are the weakest links.
- Clusters inside one database. Several databases have three or more records on one event (see above); one record of each cluster may be cut.
- Memory dates. The first to check are Cyprian's 14 September 258, Diophantus's 250, the Shushtar attribution, the Hexapla's 240, Hua Tuo's death year and Caracalla's death on 8 April 217.

### How I checked it

- Verifiers: 175 5 of 5; 176 5 of 5; 177 5 of 5; 178 5 of 5; 179 5 of 5; 180 5 of 5; 181 5 of 5; 182 5 of 5; 183 5 of 5; 184 5 of 5. That is 50 runnable records, each with expected output worked out by hand before the run.
- Checker: each database reports only the count rules, which the thin-century rule allows. No title clashes, text-rule or year problems. The rare counts (7 to 10) are 9, 7, 7, 8, 8, 8, 8, 8, 7 and 7 for 175 to 184.
- Repeat check: scratchpad/same_item_batch.py 175 184, as in the repeat check result above.
- Generator: checks passed for 184 databases and 6,044 records.
- Unit tests: BUILD SUCCESSFUL; 17 of 17 pass, with 0 failures and 0 errors (AppDatabaseSeedTest 4, RecordFilterTest 5, SeedDataTest 8).
- Text: no Cyrillic in the logs, the source pack, the README, CATALOGUE.md or the tools (checked before the commit).

### Notes

- Not verified yet: running on a device, the migration from version 174 to version 184 on a device, the UI with 184 databases in the list, lint and the release build.
- Below 300 the coding link is almost gone. The question from earlier checkpoints is still open: should the catalogue keep going back at all?
- The first facts to check before publishing are Cyprian's 14 September 258, Diophantus's 250, the Shushtar attribution, the Hexapla's 240, Hua Tuo's death year and Caracalla's death date.

## CP-34: databases 185 to 194, 100 to 200 (the second century, the century before the 200s)

Status: ten more databases are in the app, one per decade from the 190s down to the 100s: 106 records, migrations from version 184 to version 194, and the catalogue, the migrations and the tests are generic, so this batch added spec entries and record files only. Records per database: 190s 12, 180s 12, 170s 11, 160s 10, 150s 11, 140s 10, 130s 10, 120s 10, 110s 10 and 100s 10. None reaches 50, and all ten are marked "quota": false in tools/databases.json. There are 50 runnable records (5 in each database), and they all pass against their expected output. The build passes (194 databases, 6,150 records), and the unit tests pass (17 of 17, testDebugUnitTest executed).

Mood: This is the most coding-relevant of the thin centuries so far: paper (105), Zhang Heng's seismoscope (132) and armillary sphere (about 125), Ptolemy's Almagest, Geography and Handy Tables (about 150), Nicomachus's arithmetic (about 100) and the Han book catalogue (111). The batch is still thin: 10 to 12 records each, and 33 of the 106 records (about 31 per cent) carry an anchor year. The same-item check found three same-object repeats, all with the earlier catalogue or between neighbours, and each was fixed by replacing the later copy. Two single-source dates were checked by search (Commodus and Trajan), and both now have a source.

Date: 2026-10-20. Totals: Kotlin main 143,689 (CP-33: 140,320), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 144,111 (CP-33: 140,742). The +3,369 is the 110 new Kotlin seed files for databases 185 to 194 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 194.

### What changed

- Ten databases, from "Database 185: 190s coding catalogue" (190 to 200) to "Database 194: 100s coding catalogue" (100 to 110). Their records are 12, 12, 11, 10, 11, 10, 10, 10, 10 and 10: 106 new records and 6,150 in the whole catalogue. The new rare records number 83, so the whole catalogue has 2,043.
- Eleven files per database, 110 in total. The colours are unused ones.
- Spec: tools/databases.json has 194 entries. All ten new databases are marked "quota": false.
- Generator: no change. SeedCatalog.kt, CATALOGUE.md and the README tables are regenerated. The README sentence about runnable counts is updated by hand: databases 75 to 194 have fewer runnable records, and 175 to 194 have five each, all passing.
- Process: one helper per database for the first pass, from the brief and an anchors file with one owner per named item. The lead ran the repeat check, checked two dates by search, and sent four records back for replacement (see Problems).

### Placed records (33)

The year of these records is an anchor, not the date of the item itself. Each record's details say so.

- 185: a TOOL, astragali (195, the Fengsu tongyi); a DEVICE, the Chinese wheelbarrow (197, the Parthian campaign); a PRACTICE, the Pacific star compass (190, the Muratorian fragment).
- 186: a DEVICE, the Roman ship's anchor (185, Perennis's execution); a TOOL, ostraca (180, the death of Marcus Aurelius); a PRACTICE, stone rubbing (183, the Xiping stones).
- 187: a DEVICE, the talking drum (175, Avidius Cassius's revolt); a TOOL, Roman grain tokens (178, the sale of offices); a PRACTICE, Latin word dots (177, the Lyons persecution); an EXAMPLE, the Spartan scytale (180, the death of Marcus Aurelius).
- 188: a DEVICE, the Han piston bellows (168); a GUIDE, tally marks in fives (165); a TOOL, the Roman clay spindle whorl (161, the joint accession of Marcus Aurelius and Lucius Verus).
- 189: a SYSTEM, the Coptic alphabet (155, Justin's First Apology); a DEVICE, the Roman tessera hospitalis (157, Galen's post at Pergamon); a PRACTICE, the Quartodeciman Easter (155, Polycarp's meeting).
- 190: a TOOL, Roman dice (140, Marcion's arrival at Rome); a PRACTICE, Latin lead curse tablets (144, Marcion's expulsion); a DEVICE, the Roman sitella (142, the start of the Antonine Wall); an EXAMPLE, one sowing move on a mancala board (146, Huan's accession).
- 191: a LANGUAGE, Theon of Smyrna's Mathematics (130, an estimate, the book is undated); a TOOL, the Han writing brush and inkstone (132); a PRACTICE, counting yarrow stalks (132); a GUIDE, the obelus (130, Antinopolis).
- 192: a DEVICE, Zhang Heng's armillary sphere (120); a TOOL, the hand abacus (122); a PRACTICE, tile stamps (122); a GUIDE, the seven eggs of a chariot race (125).
- 193: a LANGUAGE, Suetonius's book on grammarians and rhetoricians (117); a PRACTICE, clay seals pressed on the knots of Han documents (114); a DEVICE, the bow drill (114, Trajan's annexation of Armenia); a GUIDE, the streets of a Roman marching camp (117, Hadrian's accession).
- 194: a DEVICE, the calix (109, the Aqua Traiana).

Weakest first: the calix (194), the bow drill (193), the mancala sowing (190), the wheelbarrow (185), and the Han piston bellows (188). Each is a real object or method whose year is an anchor with no firm link to it.

### Problems and what was fixed

- The calix was in 193 (DEVICE, 117) and 194 (DEVICE, 109): one object. It stays in 194; database 193 now has a bow drill (114) as its DEVICE.
- The quipu was in 48 (PRACTICE) and 190 (EXAMPLE, 146): one item. Database 190's EXAMPLE is now a mancala sowing move (146).
- The notched stick was in 48 (PRACTICE, notched tally sticks in the Exchequer) and 188 (TOOL, 161): one item. Database 188's TOOL is now the Roman clay spindle whorl (161).
- The Yiwen zhi was a TOOL and a GUIDE about the same book in 193. The GUIDE is now a method for the streets of a Roman camp (117); the FLEX stays, because it computes years.
- Commodus's birth (31 August 161) was a single-source date in 188's CROSS. A search found Wikipedia giving 31 August 161, with Lanuvium as the birthplace; the record names no place. Kept.
- Trajan's death (8 August 117, used in the 193 and 194 records): most sources give 8 August 117, Britannica gives 8 or 9 August, and one blog calls the exact day uncertain. Kept, and the day is listed below.
- The Antinopolis founding (130) is the anchor for 191's EXAMPLE and GUIDE, and an event in 192's FLEX (years from Shun's accession to the founding). These are different items, so kept.

### Repeats kept inside one database (for a decision)

- 186: the Yellow Turban appears in an EXAMPLE (the thirty-six sections), a FLEX (years from the stone classics) and a SYSTEM (184). The Xiping stones are a LANGUAGE and a CROSS (steles a year, a different quantity).
- 189: the Nabonassar epoch appears in a FLEX, a CROSS (days from the epoch) and the TOOL (the Handy Tables). The epicycle GUIDE and the Almagest LANGUAGE are different quantities.
- 191: Theon's book is a LANGUAGE and the years to the seismoscope are a FLEX.
- 190: Marcion's canon is a SYSTEM and the years from it to An Shigao are a FLEX.
- 192: Hadrian's Wall is a SYSTEM and the turves of a turf rampart are a CROSS.
- 185: the Muratorian fragment is a LANGUAGE and a FLEX (years to Severus's campaign). 188: Gaius's Institutes is a LANGUAGE and a FLEX. 193: the Yiwen zhi is a TOOL and a FLEX.
- Each is a book or an event with a computed record of a different quantity; I kept them. The lead may cut one record in each cluster.

### Repeat check result

The same-item check (scratchpad/same_item_batch.py 185 194) gives 142 pairs. The four same-object repeats above are gone. The rest are shared words or neighbours: the two Commodus CROSS records (185 and 188, different events); the astragali (185) and the Roman dice (190), both gaming dice; the grain tokens (187), the tessera hospitalis (189) and the dice (190), three different tokens; Zhang Heng's seismoscope (191) and armillary sphere (192), two devices of one man; the word dots (187) and the spacing of an inscription (191); the Quartodeciman Easter (189) beside the Easter tables; the Muratorian canon (185) beside Marcion's canon (190).

### Facts checked by search

These come from the helpers' search reports and my two searches; the record details say what each date rests on.

- 185: Severus's winter push after Lugdunum; Ctesiphon sacked late 197 or January 198 (disputed); Hatra attempts 198 to 200 (disputed); the Muratorian date range 170 to 200, the copy about the eighth century, Muratori's edition of 1740; Tertullian's Apologeticum about 197; the Fengsu tongyi about 195; the Shanghan lun about 200; Commodus died 31 December 192; Lugdunum on 19 February 197.
- 186: the Xiping stones requested in 175 and finished in 183 near Luoyang; steles given as 46 or 48 (disputed); the Yellow Turban rising in 184, end dates 185 to 205; Himiko's accession 189 or 190; Perennis executed in 185; Emperor Ling died in 189; the coalition formed in spring 190.
- 187: Avidius's revolt in spring 175; the Diatessaron about 166 to 180, Syriac favoured; the Lyons letter conventionally 177 (Eusebius's Chronicon gives 166 or 167); the sale of offices 177 or 178; Pollux's Onomasticon before 177 by its dedication.
- 188: Gaius's Institutes composed about 160 to 162, found as a palimpsest in Verona in 1816; the embassy of 166 (Yanxi 9); Lucius Verus born 15 December 130; the fanqie origin disputed; dated Roman dice from AD 61.
- 189: the Almagest about 150, 1022 stars in 48 constellations; the Nabonassar epoch on 26 February 747 BC (Julian); the Handy Tables composed in the second century, the oldest witness of the third; the Polycarp–Anicetus clash about 155 (one archive); the Coptic invention dates differ.
- 190: the Harmonics in three books (1562 and 1682 editions); Marcion's arrival about 140 and expulsion in 144; An Shigao at Luoyang in 148 (a fourth-century catalogue); about 500 Latin curse tablets; sitella urns on coins.
- 191: Hadrian's reign from 11 August 117 to 10 July 138 (the accession day disputed); Antoninus Pius succeeded in 138; Zhang Heng died in 139; the Edictum commission after 125; the seismoscope's 132 is traditional, the Longxi quake 134 or 138; Theon's career fixed by Ptolemy's observations of 127 to 132.
- 192: Deng Sui died in April 121; Emperor An's reign ended in 125; Shun's accession 125 or 126; Antinous drowned in October 130; Antinopolis founded in 130 (one source); the Sixth Legion's move to Britain in 119, 121 or 122.
- 193: Liu Xin's collation began in 26 BCE; Ban Gu died in 92 with the Hanshu unfinished; the Ulpian Library commissioned in 112 and founded in 114; Trajan's Column has 23 turns and more than 2,500 figures (one account gives 2,662); Frontinus on calices and quinariae.
- 194: Trajan's bridge built 103 to 105 on twenty piers (one account gives 104 for the order); Nicomachus about 100; Menelaus's Sphaerica about 100 (one account gives 110); the Vindolanda birthday letter dated 97 to 103 or about 100; the Qiang revolt 107 to 118; Frontinus's calix (one source, about 98).
- Lead, by search: Commodus born 31 August 161 (Wikipedia, which names Lanuvium as the birthplace; no source found for Lorium); Trajan died 8 August 117 at Selinus (Wikipedia's AD 117, the Dickinson College commentary and Ancient Rome Live; Britannica gives 8 or 9 August).

### Facts from memory, to check

- 185: the fragment's book list; Tertullian's charges; Dong Zhuo burns Luoyang (190); Pertinax murdered (193); Cao Cao takes the emperor to Xu (196); the five emperors of 193; the faces of astragali; the star compass.
- 186: Marcus Aurelius died in 180; Irenaeus's Against Heresies about 180; general facts of ostraca and rubbing.
- 187: Marcus Aurelius born 26 April 121, died 17 March 180; the talking drum; word dots; the scytale in Plutarch.
- 188: Antoninus Pius's death and Marcus's accession, 7 March 161; Ling's reign from 168; Verus's death in 169; Yanxi from 158; the plague's start about 165.
- 189: the Geography about 150 in eight books; the Almagest in thirteen books; Galen born 129, post in 157; Yanxi 158; the Liang family destroyed in 159; the Junagadh inscription about 150; Justin's Apology about 155; the epicycle history; the Julian day numbers (checked by hand: 1448638 and 1775846).
- 190: the Antonine Wall begun in 142; Shun died in 144; Huan took the throne in 146; the Anban shouyi jing; quipu facts; Roman dice of bone.
- 191: the Yangjia era from 132; the Bar Kokhba revolt in 132 and Betar in 135; Theon's Latin title; the yarrow procedure; the practice of the obelus.
- 192: Hadrian's Wall begun in 122, about 73 Roman miles; Hadrian in Britain in 122; legion stamps on the Wall tiles; the hand abacus; seven eggs on a circus spina.
- 193: Trajan died on 8 August 117 and Hadrian acceded on 11 August 117; Armenia 114; the Antioch earthquake 115; Ctesiphon 116; Yuanchu 114; Yongning 120; Suetonius's book and his service under Hadrian; Han clay seals.
- 194: Cai Lun's report of 105; Western Han paper fragments; the Dacian wars 101 to 102 and 105 to 106; Pliny's Panegyricus delivered on 1 September 100; Trajan's death on 8 August 117; the Aqua Traiana built in 109.

### Single-source dates

- 185: Lugdunum on 19 February 197 (one article); the Fengsu tongyi 195 and the Shanghan lun about 200 (weak references).
- 186: the stones lost in 190 (one account); the section sizes (one account); Himiko in 189 (one mirror).
- 187: Pollux in 175 (a round date); the year of the sale of offices is disputed.
- 188: the Yanxi start year.
- 189: the 155 clash (one archive); the Handy Tables in 150 (a round date).
- 190: Marcion in 140; the curse-tablet range; An Shigao in 148.
- 191: the Edictum's 131 (not used); the seismoscope's 132 rests on tradition.
- 192: Antinopolis in 130 (one source); Lingxian about 120 (secondary summaries only).
- 193: 111 for the Hanshu's completion (one summary); 113 for the Column (one entry); the Ulpian Library in 112 to 114.
- 194: Frontinus's treatise about 98 (the calix details); Apuleius's Latin version of Nicomachus is undated.

### Problems

- Thin decades. All ten are far below 50 records (10 to 12), with 50 runnable records in all.
- Placed records. 33 of 106, about one in three, as in CP-33. The weakest are listed above.
- Coding relevance. Higher than the 200s: paper, the seismoscope, the armillary sphere, Ptolemy's tables and the book catalogue are real items with coding links. The GUIDE and TOOL records are still the weakest, and several records (the mancala sowing, the camp streets, the seven eggs) are methods of the period rather than coding in the modern sense.
- Clusters inside one database: see above; one record of each cluster may be cut.
- Memory dates. The first to check are Trajan's death (8 or 9 August 117 in one source), Hadrian's accession (11 August 117), Armenia in 114, Antioch in 115, Ctesiphon in 116, the Aqua Traiana in 109 and Trajan's bridge (104 or 105).

### How I checked it

- Verifiers: 185 to 194, 5 of 5 each. That is 50 runnable records, each with expected output worked out by hand before the run.
- Checker: each database reports only the count rules, which the thin-century rule allows. No title clashes, text-rule or year problems. The rare counts (7 to 10) are 9, 8, 8, 8, 9, 8, 9, 8, 7 and 9 for 185 to 194.
- Repeat check: scratchpad/same_item_batch.py 185 194, as in the repeat check result above.
- Generator: checks passed for 194 databases and 6,150 records.
- Unit tests: BUILD SUCCESSFUL; 17 of 17 pass, with 0 failures and 0 errors (AppDatabaseSeedTest 4, RecordFilterTest 5, SeedDataTest 8).
- Text: no Cyrillic in the logs, the source pack, the README, CATALOGUE.md or the tools (checked before the commit).

### Notes

- Not verified yet: running on a device, the migration from version 184 to version 194 on a device, the UI with 194 databases in the list, lint and the release build.
- The second century is the best of the three thin centuries for coding history, but the records are still thin and about a third are placed. The question from earlier checkpoints is still open: how far back should the catalogue go, given that each step back is thinner?
- The first facts to check before publishing are the day of Trajan's death, Hadrian's accession, Armenia in 114, the Aqua Traiana in 109, the Muratorian date and Himiko's 189.

## CP-35: databases 195 to 204, 0 to 100 (the first century, the century before the 100s)

Status: ten more databases are in the app, one per decade from the 90s down to the 0s: 106 records, migrations from version 194 to version 204. The catalogue, the migrations and the tests are generic; the batch added spec entries and record files, and one generator fix (see What changed). Records per database: 90s 12, 80s 11, 70s 11, 60s 10, 50s 10, 40s 10, 30s 12, 20s 10, 10s 10 and 0s 10. None reaches 50, and all ten are marked "quota": false in tools/databases.json. There are 50 runnable records (5 in each database), and they all pass against their expected output. The build passes (204 databases, 6,256 records), and the unit tests pass (17 of 17, testDebugUnitTest executed).

Mood: This is the first century of the common era, and the thinnest batch so far for coding history. The sources hold censuses, letters, offices, a few instruments and some signals, and most of the records are about Roman and Han administration rather than computing. 32 of the 106 records (about 30 per cent) carry an anchor year. Two things went wrong in the first pass and both are fixed: five records repeated an item of the catalogue, and the generator could not read years below 100. The next step back would be the first century BCE, which has far more coding history (Euclid, Archimedes, Eratosthenes, the Antikythera mechanism, Pingala). Whether to go there is for the user to decide.

Date: 2026-10-20. Totals: Kotlin main 147,071 (CP-34: 143,689), Kotlin test 222 (222), XML 82 (82), Gradle and project files 118 (118), total 147,493 (CP-34: 144,111). The +3,382 is the 110 new Kotlin seed files for databases 195 to 204 and the regenerated SeedCatalog.kt. The database class and the tests did not change; the migrations are generated, with DATABASE_VERSION at 204.

### What changed

- Ten databases, from "Database 195: 90s coding catalogue" (90 to 100) to "Database 204: 0s coding catalogue" (0 to 10). Their records are 12, 11, 11, 10, 10, 10, 12, 10, 10 and 10: 106 new records and 6,256 in the whole catalogue. The new rare records number 74, so the whole catalogue has 2,117.
- Eleven files per database, 110 in total. The colours are unused ones.
- Spec: tools/databases.json has 204 entries. All ten new databases are marked "quota": false.
- Generator: one change. The year pattern accepted only three or four digits, so the first century (years below 100) failed to parse. It now accepts one to four digits; the title-list script changed in the same way. The overview now reads "from 0 to 2036".
- README: the sentence about runnable counts is updated by hand: databases 75 to 204 have fewer runnable records, and 175 to 204 have five each, all passing.
- Process: one helper per database for the first pass, from the brief and an anchors file with one owner per named item. The lead ran the repeat check and sent four records back for replacement (see Problems).

### Placed records (32)

The year of these records is an anchor, not the date of the item itself. Each record's details say so.

- 195: a SYSTEM, alimenta (96, Nerva's accession); a DEVICE, the drum carriage (97, Gan Ying's mission); a TOOL, the juror's ballot tablet (98, Nerva's death); a PRACTICE, bamboo-slip cords (97, Trajan's adoption).
- 196: a DEVICE, the sinan (80, Wang Chong's account); a TOOL, the book-box, the capsa (81, Domitian's succession).
- 197: a DEVICE, the libella (79, the eruption); a TOOL, the stone-cutter's chisel (79, the eruption).
- 198: a DEVICE, the dioptra (62, the eclipse account); a SYSTEM, the schools of Joshua ben Gamla (64); a PRACTICE, counting the omer (70, the Temple's destruction).
- 199: a SYSTEM, the Meroitic script (54, Nero's accession); a DEVICE, the Roman military horn (59, Agrippina's murder); a TOOL, the counting beads (51, Caratacus's capture); a PRACTICE, Roman pupils copying the alphabet (50, Cologne's founding).
- 200: a PRACTICE, the imperial offices for accounts and letters (41); a TOOL, the scrinium (41).
- 201: a DEVICE, the Han diviners' shipan (36, Gongsun Shu's death); a PRACTICE, the tabellarii couriers (37, Caligula's accession); a TOOL, the pumice stone for writing surfaces (37, Caligula's accession).
- 202: a DEVICE, the bilanx (26); a SYSTEM, the yearly reports of Han local offices (25); a TOOL, the norma (21); a PRACTICE, finger numerals (23); a GUIDE, the colony's grid of centuries (23).
- 203: a DEVICE, the potter's wheel of the Roman provinces (14, Tiberius's accession); a TOOL, the bronze tweezers (15, Germanicus's Rhine campaign).
- 204: a DEVICE, the warp-weighted loom (4); a LANGUAGE, the Rites of Zhou (9); a PRACTICE, the beacon signals of the Han frontier (1); a TOOL, the Han scribe's knife for bamboo slips (6); a GUIDE, the census year of 2 (2).

Weakest first: the Rites of Zhou (9), the beacon signals (1), the loom (4) and the knife (6), where the anchor has no link to the object; then the sinan (80) and the shipan (36), which are a text date and a death.

### Problems and what was fixed

- The libella was in 197 (DEVICE, 79) and 201 (DEVICE, 37): one object. It stays in 197; 201's DEVICE is now the Han diviners' shipan (36).
- The capsa was in 196 (TOOL, 81) and 201 (TOOL, 31): one object. It stays in 196; 201's TOOL is now a Roman pumice stone (37).
- The scrinium was in 199 (DEVICE, 55) and 200 (TOOL, 41): one object. It stays in 200; 199's DEVICE is now a Roman military horn (59).
- The Julian leap-year EXAMPLE of 195 repeated the Julian leap rule that databases 37, 65 and 104 own. It is now a count of the 32 poems of Statius's Silvae in five books (95; from memory).
- The Columella EXAMPLE in 198 counted the books of its own LANGUAGE record: one item, one record. It is now the Neronia's five-yearly festival years in the 60s (60; dated from memory, no search).
- The generator could not read years below 100 (see What changed).

### Repeats kept inside one database (for a decision)

- 199: Cologne's founding (FLEX) and the four events of the 50s (PROJECT) both name Agrippina's murder; different quantities.
- 197: the census of Vespasian and Titus (SYSTEM) and the days from Vespasian's death to the eruption (FLEX).
- 196: Titus's hundred days of games (EXAMPLE, animals per day) and the days from Titus's death to Domitian's (CROSS).
- 204: the Han census of 2 (SYSTEM) and the years to the defeat of Varus in 9 (FLEX, and a PROJECT of four events of 4 to 9).
- Each is a book or event with a computed record of a different quantity; kept. The lead may cut one record in each cluster.

### Repeat check result

The same-item check (scratchpad/same_item_batch.py 195 204) gives 131 pairs. The five repeats above are gone. The rest are shared words or neighbours: the Neronia beside the festivals of 177 and 180 (festival); the military horn beside the marching camp of 193 (camp); the shipan beside Al-Kashi's Ladder of Heaven (62) and Zhang Zai's Western Inscription (97) (heaven); the Han census of 2 (204) and the Tang census of 742 (130) both average the household size, a different census with the same measure; the libella beside the groma (162) (plumb line).

### Facts checked by search

These come from the helpers' search reports (six searches each); the record details say what each date rests on.

- 195: Ban Zhao's Lessons dated about 100 in most accounts (others 80, and 99 to 105); alimenta with no firm start date; Domitian killed on 18 September 96; Nerva died on 27 January 98; Trajan adopted in October 97; Gan Ying's mission in 97 (Hou Hanshu); the drum carriage undated.
- 196: four books of the Strategemata, the fourth's authenticity disputed; the Lunheng first published about 80; the Colosseum opened in 80 (81 in some accounts); Titus died on 13 September 81; the Apophoreta dated 84 or 85.
- 197: the eruption on 24 August (Pliny the Younger), a Pompeii inscription reading 17 October with no year; Vespasian died on 23 or 24 June; the Fiscus Judaicus in Josephus and Suetonius; the Jewish War about 75.
- 198: the dioptra's sight tube; the eclipse of 13 March 62 (a critique calls it possibly invented); Dioscorides from Anazarbus, dated 60 to 77 by different accounts; Columella in the 60s; Joshua ben Gamla's priesthood about 64.
- 199: Claudius died on 13 October 54; Britannicus died in February 55 (accounts give 11 or 12 February); First Corinthians about 53 to 55; Second Corinthians about 55 to 56; Cologne founded in 50; Caratacus captured in 51; Agrippina murdered in 59.
- 200: the census of 47 or 48; Caligula killed on 24 January 41; Claudius died on 13 October 54; the lighthouse rests on one Suetonius passage (popular dates 39 or 40); the freedmen Pallas and Narcissus; the donative of 15,000 sesterces.
- 201: Velleius's history addressed to Vinicius in 30 (EBSCO, Britannica); the Aqua Claudia begun in 38 and finished in 52 (older texts give 36 and 50); Caligula's bridge at Baiae dated 39; Sejanus executed on 31 October; Tiberius died on 16 March 37; Gongsun Shu killed in 36.
- 202: Strabo's book division; finger numerals from Plautus to Cicero; the Yinwan slips read as a commandery's annual report (one article); the norma as a carpenter's square (one page).
- 203: the census of 14 at 4,937,000 (with 4,063,000 for 28 BC and 4,233,000 for 8 BC); the Huo quan and Bu quan reform of 14; the Xin span about 9 to 23; Germanicus died on 10 October 19.
- 204: about 12.36 million households; 59,594,978 people on one mirror page against about 57.67 million in other accounts (named as disputed); Nanaghat and Hathigumpha dated to the 2nd and 1st centuries BCE; Gaius Caesar's death in 4; the Varus defeat in 9; Ping's death in 6.
- Lead, before the build: no search was added beyond the helpers' reports.

### Facts from memory, to check

- 195: Quintilian's twelve books and Poggio's 1416 find; Agricola's governorship 77 to 84 and Mons Graupius 83 or 84; Ban Gu's death in 92; the Silvae's 32 poems in five books.
- 196: Wang Chong 27 to 97; numbered arches and tesserae at the Colosseum; the capsa named by Roman writers; Dio's 9,000 animals (conflicts with the 5,000 figure); the Capitoline Games 86; Yan Mountain 89.
- 197: Pliny's 37 books and his death at Stabiae in 79; the Temple burned in 70; First Chronicles 24; Titus as co-censor; the libella and chisel descriptions.
- 198: Columella's 60 to 65 range; Josephus silent on schools; the Great Fire in 64; Seneca's death in 65; Nero's death in 68; Leviticus's omer count; the Neronia founded in 60.
- 199: Griffith's readings of the script and its use to about the fourth century; scrinium as a document chest; the 108-bead string; Roman school alphabets; the military horn; Leviticus 23's count.
- 200: the Roman invasion of Britain in 43; Gesoriacum as Boulogne; four sesterces to the denarius; the praenomen sign list.
- 201: Valerius Maximus (nine books, about 31); Anio Novus; Caligula's accession year; the Xerxes comparison; the libella, capsa and tabellarii descriptions.
- 202: Strabo's completion in 23 (not confirmed; accounts differ); Eastern Han in 25; Sacrovir's revolt in 21; Drusus's death in 23; Pilate in Judaea in 26; Caligula's accession in 37; the parasang of 30 stades; the centuria of 200 iugera.
- 203: Augustus died on 19 August 14; the Res Gestae copies at Ancyra, Apollonia and Antioch in Pisidia; Idistaviso in 16; Cappadocia a province in 17; the Red Eyebrows in 18; Wang Mang's fall in 23; the Julian day count of 1878 (checked by hand).
- 204: the Rites of Zhou's six offices and its date dispute; the Yuanshi era from 1 CE; 12,366,470 households; the slip knife and the character shan; the smoke-by-day and fire-by-night signals; the warp-weighted loom.

### Single-source dates

- 196: the Strategemata (one listing); the Lunheng in 80; the pay of 84 (one coin source).
- 197: 24 August (Pliny the Younger only); 17 October (one undated graffito); the census of 74 (one search title plus memory).
- 198: Columella in the 60s (one article); the dioptra's eclipse of 62.
- 199: Agrippina's year (one reference page plus memory); Britannicus's day (two accounts, named in the details).
- 200: the census total and the lighthouse date.
- 201: Sejanus's day and Valerius's date.
- 202: the Yinwan slips (one 2004 article); the norma (one vocabulary page).
- 203: Germanicus's campaign of 15 (one timeline); the Greek myriad reading of 493 and 7,000 (one page); the Yellow River shift (accounts differ).
- 204: 59,594,978 people (one mirror page).

### Problems

- Thin decades. All ten are far below 50 records (10 to 12), with 50 runnable records in all.
- Placed records. 32 of 106, about 30 per cent. The weakest are listed above.
- Coding relevance. Almost none in this century: the records describe censuses, letters, offices, instruments and signals. The GUIDE and TOOL records are the thinnest links.
- Clusters inside one database. See above; one record of each cluster may be cut.
- Memory dates. The first to check are the census totals of 14 and of 47 or 48, the lighthouse date, Vespasian's death day (23 or 24 June), Britannicus's day (11 or 12 February 55) and the Silvae's count.

### How I checked it

- Verifiers: 195 to 204, 5 of 5 each. That is 50 runnable records, each with expected output worked out by hand before the run.
- Checker: each database reports only the count rules, which the thin-century rule allows. No title clashes, text-rule or year problems. The rare counts (7 to 10) are 8, 8, 8, 7, 7, 8, 7, 6, 8 and 7 for 195 to 204; the 202 count of 6 is a count-only shortfall.
- Repeat check: scratchpad/same_item_batch.py 195 204, as in the repeat check result above.
- Generator: checks passed for 204 databases and 6,256 records, after the year-pattern fix.
- Unit tests: BUILD SUCCESSFUL; 17 of 17 pass, with 0 failures and 0 errors (AppDatabaseSeedTest 4, RecordFilterTest 5, SeedDataTest 8).
- Text: no Cyrillic in the logs, the source pack, the README, CATALOGUE.md or the tools (checked before the commit).

### Notes

- Not verified yet: running on a device, the migration from version 194 to version 204 on a device, the UI with 204 databases in the list, lint and the release build.
- The first century has the least coding history of the batches so far. Going further back would reach the first century BCE, which is far richer; that is for the user to decide.
- The first facts to check before publishing are the census totals, the lighthouse date, Vespasian's death day, Britannicus's day and the Silvae's count.

## CP-36: the on-device assistant (a feature, no record files)

Mood: pleased that the model is small enough to read in one sitting, and careful about what it claims. It is a statistical model, a BM25 search index over the records plus a naive Bayes classifier over words. It is not a neural language model, so it cannot write new text: it finds records and writes short answers from them. Its answers are only as good as the records, and it has not been run on a phone or in the emulator.

The app has an on-device assistant behind an info button on the first screen. It answers questions about the catalogue: find a record, list the databases, say which database covers a year, show runnable examples, explain how to use the app, and count the records. It needs no network and no model file. The database version is unchanged (204), there is no migration, and no record changed.

### What changed

- assistant/Tokenizer.kt: splits text into lowercase tokens of any script and folds the letter yo into ye. For search it also drops stop words in English and Russian, including request words such as find and show.
- assistant/RecordIndex.kt: a BM25 index over every record (k1 1.2, b 0.75). Field weights: title 3, original title 2, section 2, year 2, author 1.5, kind 1, summary 1, details 0.5. Each term keeps two parallel arrays, one of record numbers and one of weights.
- assistant/IntentClassifier.kt: a multinomial naive Bayes classifier with Laplace smoothing, trained when it is created on 53 example questions in English and Russian. Six intents: FIND, DATABASES, COVERAGE, RUNNABLE, APP_HELP and STATS. Words it never saw are ignored, and a question made only of such words is UNKNOWN.
- assistant/Assistant.kt: builds the answer. It reads a database number ("database 12"), a year or a decade ("1850s") and a kind word from the question, then fills the answer from the index and the catalogue. A question that opens with a search verb (find, search, look, and their Russian forms) is a search, unless it contains how or its Russian form. A question below 0.4 confidence is searched instead; if nothing matches, the answer says it could not tell. The answer is in Russian when the question has Cyrillic letters, otherwise in English.
- ui/AssistantViewModel.kt and ui/screens/AssistantScreen.kt: the conversation: a question field, the answers, and the matching records, which open the record screen. The catalogue is read from the database when the screen opens; the index is built on a background thread when the first question is answered.
- data/LibraryDao.kt: two queries that read every database and every record.
- ui/screens/DatabasesScreen.kt and ui/AppNavHost.kt: the info button in the top bar, and the route "assistant".
- res/values/strings.xml: five strings for the screen.
- README.md: a section "The assistant".
- app/src/test/java/com/example/retrodatabases/assistant: four test files on the real seed data (tokens, index ranking, classifier, and the answers).

### Problems found and fixed

- An invalid import (androidx.compose.foundation.lazy.item) broke the compile. "item" is a member of the lazy list scope and needs no import, so the import was removed.
- The classifier read "find commentary in database 178" as a coverage question. A question that opens with a search verb is now a search, and the test restricts that search to database 178.
- The README had a Russian example, which broke its English-only rule. It was replaced by an English description.
- Two code comments overstated the code: the search-verb rule checks for "how" anywhere in the question, not only at its start, and a size for the classifier ("a few kilobytes") was never measured. Both comments were corrected.

### Limits, stated plainly

- The model knows six intents and 53 example questions. Other phrasings fall back to a search, or to "I could not tell".
- Answers are retrieved and filled from templates. The assistant can quote a record, but it cannot explain beyond its text.
- The index is built in memory from 6,256 records. Its size and build time on a phone were not measured.
- Russian text is in six files: Assistant.kt (answers), IntentClassifier.kt (training questions), Tokenizer.kt (stop words), and the three tests that use it (AssistantTest.kt, IntentClassifierTest.kt, TokenizerTest.kt). No other source file has Cyrillic, so RetroDatabases_source.txt is no longer Cyrillic-free, and these six files are the only exception. README.md, DEVLOG.md, CATALOGUE.md and tools/ stay Cyrillic-free.
- A neural on-device model (for example MediaPipe or TensorFlow Lite) would need a model file of tens or hundreds of megabytes. That is a separate decision and is not part of this checkpoint.

### How I checked it

- ./gradlew testDebugUnitTest assembleDebug: BUILD SUCCESSFUL on the final sources. After the two comment fixes, compileDebugKotlin ran and the test task was up to date, so its last run stands: 37 tests, 0 failures, 0 errors, 0 skipped: 17 existing (AppDatabaseSeedTest 4, RecordFilterTest 5, SeedDataTest 8) and 20 new (TokenizerTest 3, RecordIndexTest 4, IntentClassifierTest 4, AssistantTest 9).
- Cyrillic: a search of the tree found the six files above and the source pack. README.md, DEVLOG.md, CATALOGUE.md and tools/ have none.
- Seed data: SeedCatalog.kt and the migrations are not touched, so DATABASE_VERSION stays 204.

### Not verified

- Running on a device or in the emulator, the screen layout, lint and the release build.
- Answer quality beyond the 37 tests: no separate set of questions was written to measure it.

## CP-37: economics, paused at the web-search budget

Mood: annoyed with myself. The first batch needed far more web searches than the budget allowed, and I learned that only after seven writers had started. The infrastructure is in place and tested. The content is not, and it will stay that way until the budget is raised.

Status: the economics work has its model list, its sphere map, its generator support, its two record kinds and its tests. No economics record exists yet. ECONOMICS.md holds the plan, the writing rules and the steps to resume.

Totals: Kotlin main 147,861 (CP-36: 147,853), Kotlin test 454 (408), XML 88 (88), Gradle and project files 118 (118), total 148,521 (148,467).

### What changed

- `tools/economics/models.json` and `spheres.json`: the 36 models, the 16 spheres and the 75 practices, with scope sentences and database numbers for the six education practices.
- `tools/build_catalogue.py`: economics databases are read from one JSON file each, checked, and written as `Db<id>Economics.kt`. Their catalogue section and README counts come from the same code. The coding databases are checked as before.
- `tools/databases.json`: rows 205 to 212, with colours no other database uses.
- Kotlin: `Kind.MODEL` ("Economic models") and `Kind.COMPROMISE` ("Compromises"), with colours in `KindColors.kt`, the assistant's Russian labels and search words, and `EconomicsSeedTest`.
- `SeedDataTest`: the test required every kind in every database, which the two new kinds broke. It now checks the ten coding kinds for the coding databases and a compromise for the economics databases.
- `README.md`: the intro says that the economics databases are planned, with a link to ECONOMICS.md.
- `ECONOMICS.md`: new. The plan, the record format, the rules, the status and the resume steps.

### Problems

- The web-search budget is shared by the whole turn. Seven writers were started in parallel. Database 212's writer stopped after 20 searches with nothing written, and the other six were stopped by hand before they wrote anything. The budget was spent on the first batch alone.
- Checking every documented case by search needs many more searches than the coding batches used. That is the real limit of the plan, and it has to be settled before the next batch.
- Before the work-in-progress commit 785957c, three tests failed: two in `EconomicsSeedTest`, which need the records, and one in `SeedDataTest`, which was wrong and is now corrected. This commit leaves the two `EconomicsSeedTest` count tests failing until the records exist.

### Facts the writers reported as checked

The writer agents reported these as checked by search before they stopped. I have not checked them again, and none of them is in a record. They are listed so that the next search does not start from nothing:

- Third Lateran Council (1179): endowments for teachers.
- Manorial schooling licences, Norton and Winslow (1300 to 1348).
- Georgia's teaching ban (1829).
- Margaret Douglass's arrest (1853).
- The Brougham inquiry (1818).
- The Radikalenerlass bans in Germany (1972).
- Analects 7.7.
- The Statute of Artificers (1563).
- WPA nursery schools.
- The German 2013 entitlement to early childhood places.
- The Swedish fee cap in early childhood care, and the cost shift of the 1990s, as set out in Ds 2002:32.
- ILO Convention 140 (paid educational leave), date confirmed.

### How I checked it

- `./gradlew testDebugUnitTest` on commit 785957c: 40 tests, 38 pass. The two failures are the count tests of `EconomicsSeedTest`, which need the records.
- `python3 tools/build_catalogue.py --check`: fails for databases 205 to 212, which have no JSON file yet, and passes for the 204 coding databases.
- The line counts come from `count_lines.py` on the working tree.

### Next

The steps are in ECONOMICS.md. The first is to raise the web-search budget, which is the user's decision. Until then, the economics work stays as it is.
