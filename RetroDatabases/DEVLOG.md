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
