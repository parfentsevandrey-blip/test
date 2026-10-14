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
