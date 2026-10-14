# Development log: Retro Databases

Solo development log for the Android app in this folder. Entries are added at checkpoints (CP-NN), in order.

## Conventions

- Each checkpoint records line counts for the tracked sources: Kotlin (`app/src/main/java`, `app/src/test`), XML (`app/src/main/res`, `AndroidManifest.xml`), and hand-written Gradle and project files.
- Counts come from `wc -l` on the working tree. Build output, the generated Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`) and this log are excluded from the totals.
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

### What changed

- The record kind is an enum with ten values: LANGUAGE, SYSTEM, DEVICE, TOOL, PRACTICE, GUIDE, EXAMPLE, PROJECT, FLEX, CROSS. Each record also has a section (a subgroup inside its kind) and a rare flag.
- Records gained an `instructions` field for step-by-step text. `details` now explains how a record works.
- Seed data for database 1: 165 records. Languages 34, systems 23, devices 27, tools 18, practices 39, guides 5, examples 5, projects 4, flex 5, cross-technology 5. 55 records are flagged rare.
- Seed insert runs as SQL in `onCreate` and binds 12 columns, plus a literal for `favorite`.
- UI: kind filter chips with counts, a rare filter, section headers in the list, an instructions block on the detail screen, and a monospace code block with horizontal scroll.

### Size by area (Kotlin main, 4,269 lines)

- Seed data: 3,141 lines (73.6%). The ten catalogue files hold 3,077 of them.
- UI: 919 lines (21.5%).
- Data layer: 209 lines (4.9%).

### Notes

- Seed data is most of the code. I keep examples as raw strings so the same text can be extracted and executed during verification.
- Examples in languages that are not installed in the build sandbox can only be checked by reading. They stay open until they are run somewhere.
- Rare flags: 55 of 165 records. The flag shows as a badge and has its own filter.
- The database schema is still version 1. No installs exist yet, so no migration is needed.

## CP-02: example verification and unit tests

Status: examples executed, bugs fixed, unit tests written, Gradle wrapper generated.

### Example verification

- Extracted every example from the seed files and executed the 32 that this sandbox can run: gcc 13, mawk 1.3.4, dash, make 4.3, perl 5.38, bc, bison, and SQLite 3.45 through Python.
- 32 passed. 133 entries cannot run here (Forth, Prolog, Lisp, Scheme, Smalltalk, BASIC, APL, Logo, Icon, Miranda, PostScript, Pascal, Modula-2, Ada, COBOL and others, plus text-only entries such as the emulator guide and the Forth stack trace). They stay on the open list.
- Five bugs were found by running the examples and fixed:
  1. The sine-table Makefile listed `sine.h` first, so plain `make` built only the header and never produced `prog`. `prog` is now the first target.
  2. The fixed-point example declared variables at file scope with non-constant initialisers, which C rejects. The code is now inside `main` and prints 576.
  3. bc at `scale=50` printed `...937508` where the correct digits are `...937510`. The example now uses `scale=60`, which is exact to 60 decimals. The entry was renamed to sixty digits.
  4. The normal-forms example created the table `orders` twice. The denormalised version is now `orders_flat`.
  5. The harness expected a filename prefix from grep. grep omits it when only one file matches, so the harness check was corrected. The example itself was fine.
- Four of the five bugs were in the example text. The first and fourth are easy to miss when reading, so every executable example is now run before it is accepted.

### Unit tests

- `RecordFilterTest`: search, case-insensitive Cyrillic matching, kind filter, favourite and rare filters, and their combinations.
- `SeedDataTest`: database one exists, titles are unique, years are in range, every kind has records, projects carry instructions and code, rare records exist.
- `AppDatabaseSeedTest` (Robolectric): the first open seeds database one with every record, and the favourite toggle persists. This exercises the real seed SQL and Room's schema validation.

### Notes

- The first Gradle wrapper attempt was rate-limited by Maven Central (HTTP 429). A second attempt worked. I did not change the project configuration to avoid the limit.

### Next checkpoint

- CP-03: build and unit test results.

## CP-03: first build attempt (failed before compilation)

Date: 2026-10-14. Totals unchanged from CP-02: no source files changed since then.

### What happened

- `./gradlew assembleDebug testDebugUnitTest` failed while Gradle configured the root project. It could not download plugin dependencies from `repo.maven.apache.org` and received HTTP 429 (Too Many Requests). Compilation did not start, so the code has not been compiled yet.
- The wrapper step hit the same limit once and succeeded on a second try. This time the limit held for the whole resolution.

### Decision

- Retrying unchanged would hit the same limit. The next attempt points Gradle's Maven Central repository at `repo1.maven.org` through a per-user init script outside the repository. The project declares only `mavenCentral()`, so this change does not reach version control.

### Open items

- Build through the mirror, record the compile result, fix any compile errors, run the unit tests.
- Pack the text bundle after the build is green.

## CP-04: first successful build and unit tests

Status: debug APK assembles and all 14 unit tests pass.

Non-blank lines at CP-04: 4,229 (Kotlin 3,911, tests 143, XML 74, Gradle and project files 101).

### What happened

- Dependency downloads were rate-limited (HTTP 429) by Maven Central and by repo1.maven.org in four build attempts. Gradle caches each artifact it downloads, so every retry moved forward, and the fifth attempt finished the dependency phase.
- Attempt 4 failed at compilation with two unresolved references in `RecordsScreen.kt`: a wrong import of `item` (a member of `LazyListScope`, so it needs no import) and a missing import of `RoundedCornerShape`. Both are fixed in `7e76f19`.
- Attempt 5 compiled the main sources and assembled the debug APK. The unit-test task then reported one failure: `AppDatabaseSeedTest.firstOpenSeedsDatabaseOneWithEveryRecord` could not download Robolectric's Android runtime (`android-all-instrumented`, 199.5 MB) because of the same rate limit. The second database test passed in that run.
- A rerun after the runtime finished caching passed all tests. The test task executed in that run, and the result files were written at that time.

### Results

- `assembleDebug`: `app/build/outputs/apk/debug/app-debug.apk`, 10.1 MB.
- `testDebugUnitTest`: 14 of 14 passed. `SeedDataTest` 7, `RecordFilterTest` 5, `AppDatabaseSeedTest` 2. The database test runs the real seed SQL and Room's schema validation on Robolectric.

### Notes

- Not verified yet: the app running on a device or emulator (no emulator in this sandbox), the UI behaviour, lint, and the release build.
- The sandbox builds use a per-user Gradle init script that points Maven Central requests at repo1.maven.org. It lives outside the repository and is not committed, so the project files are unchanged.
- The Kotlin Gradle plugin writes `.kotlin/` session data during builds. `.gitignore` now excludes it (`802d0f1`).

### Next

- Commit the packed source file `RetroDatabases_source.txt` together with this log.

## CP-05: coverage audit and 155 added records

Status: built, all 14 unit tests pass, debug APK assembles.

Date: 2026-10-14. Totals: Kotlin main 6,236 (CP-04: 4,277), Kotlin test 169, XML 82, Gradle and project files 118, total 6,605. The +1,959 delta equals the size of the six new Kotlin files, and no other source changed.

### What changed

- Five seed files with new records: `Db1ExtraDevices.kt` (72), `Db1ExtraSystems.kt` (25), `Db1ExtraLanguages.kt` (22), `Db1ExtraTools.kt` (15) and `Db1ExtraPractices.kt` (21).
- `Db1Additions.kt` joins the five lists into `db1Additions`. `Db1Catalog.kt` adds that list to `db1Records`; the change is one line.
- Database 1 now has 320 records (165 before). Rare-flagged records rise from 55 to 181; 126 of the new records are rare.
- New sections include regional home computers, Eastern-bloc and Soviet machines, Lisp machines, processor families, Unix variants, microkernels, networks, formal methods, and BBS and demoscene culture.

### Audit

- Compared the existing 165 records with reference lists of 1980s home computers, operating systems, processors, languages and tools, and with the standards and practices of the decade.
- Searched for facts on the machines least likely to be known: Yugoslav, Bulgarian, Czechoslovak, East German, Chinese and Soviet computers, and the Iskra-1030.
- Titles are unique across all 320 records. A grep over the seed files confirmed this before the build, and `SeedDataTest` enforces it.

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

- Pravetz 8D is an Oric Atmos clone, not an Apple II clone. Its year (1985) is an estimate; no source gave one.
- The Iskra-1030 is no longer described as a school computer, which the sources did not support. It is now described as used in institute computing labs.
- The CEC-I university attribution was removed; the sources do not support it.
- Parlog was not used by the ICOT project. The entry now says it is related work.
- The TI-99/4A is dated 1981, the year of the 4A. The 1979 date belongs to the TI-99/4.
- Common Lisp is described as the 1984 book that unified the dialects. The ANSI standard came later.
- RAD is dated to James Martin's 1982 book.
- Removed claims I could not support: "hundreds of sprites" on the X68000, "basis of the Korean market" for the SPC-1000, "main place" for Usenet, "thousands of accounting systems" for Clipper, the wording that autoconf and sendmail grew out of m4, and the ITS "first text editor" wording.
- Author names corrected from memory, not searched: Franz Lisp, Hope, Modula-3, mutation testing, gawk and uucp.

### Examples and tests

- New executable example: the C++ entry (a counter class). It was extracted from the Kotlin source, compiled with `g++ -std=c++17 -Wall -Wextra` and run: it prints `2`. It is the only new entry with executable code; the other 154 new entries are reading-only.
- `./gradlew assembleDebug testDebugUnitTest` finished with BUILD SUCCESSFUL. Results: AppDatabaseSeedTest 2 of 2, RecordFilterTest 5 of 5, SeedDataTest 7 of 7. Debug APK: 10.2 MB.

### Notes

- About 145 of the new entries were written from memory and not checked against a source in this checkpoint. Dates and names are the first thing to check when an error is reported.
- The rare flag now covers 59 of 72 new devices. Most of these machines were sold in one region or in small numbers. The flag is a judgement call and may need a review pass.
- Not verified yet: running on a device or emulator, the new sections in the UI, lint, and the release build.
- `DEVLOG.txt` is a plain-text copy of this log, and the packed bundle includes it.
