# Economics databases

This document describes the economics part of the catalogue: what it is, how its records are written and checked, what is done, what is weak, and how to continue. The coding databases (1 to 204) are described in [README.md](README.md) and [CATALOGUE.md](CATALOGUE.md).

## What is planned

The economics databases ask one question for every part of society: what does each economic model of humanity do well and badly there, and what would a compromise between them look like?

- **Thirty-six economic models**, from gift and household economies to market socialism, the commons and degrowth. `tools/economics/models.json` lists each one with its family, canonical year, original title, author, core idea and sources.
- **Sixteen spheres of society**, with **seventy-five practices** inside them. `tools/economics/spheres.json` lists them; each practice has a scope sentence that its records must stay inside.
- **Database 205** holds the models: each model's best and worst practice for the economy as a whole, and one general compromise.
- **Database 206** holds one compromise for each sphere, and one that applies to all spheres.
- **Databases 207 to 281** hold one practice each: for every model, a best and a worst practice for that practice, and one compromise for the practice.

The planned size is 77 databases. Each model or practice database has 37 records (36 models and one compromise); database 206 has 17. That makes 2,829 records in all.

## What is done

Batch 1 is written, reviewed and generated: databases **205** (the models), **206** (the compromise across the spheres) and **207 to 212** (the six education practices: schooling, higher education, vocational training, early childhood care, adult learning, and teacher pay). That is 276 records, in the app and in `RetroDatabases_source.txt`.

Databases 213 to 281 are not started.

## How the records are built

The method has four parts. Each part has its own file or step, so that each claim can be traced.

1. **Models.** `tools/economics/models.json` holds the 36 models. Four entries have been checked by web search: social market economy (1947, the book; the term first appeared in print in 1946), market socialism (1938, the book), Islamic economics (1963) and Buddhist economics (1966). The other entries are from memory, and the file says so.

2. **Case pools.** `tools/economics/pools/db<id>.json` holds the documented cases for one database. Each case gives what the cited source reports, the source, a caveat, and the models it bears on. Cases are found by web search; a pool has three to eleven cases. Only pool cases may be named in a record as documented facts. Where a source disagrees with another, or gives a figure that is not comparable, the caveat says so, and the record must say so too.

3. **Records.** Each model record has two fields. The **best practice** (the record's summary) is what the model would produce for the practice. The **worst practice** (the record's details) is what it has produced or would produce, and it ends with a `Source:` line. A field is either:
   - a named pool case, stated within its claim and caveat; or
   - an **inference** from the model's core idea. Such a field begins "Inferred from its principle that" and ends "There is no documented case in the pool." Its source is the model's own source in `models.json`.

   Each compromise has four parts: `Keeps:` (the best practices it keeps, naming the models they come from), `Blocks:` (the worst practices it blocks, and the safeguard for each), `Test:` (what to measure, and over what period), and `Source:`.

4. **Review.** Every database file was checked against its pool by a second agent that did not write it. The reviewer could not search the web. The reviewer checked that each named case says only what its pool claim says, that each inference states no fact, that the source lines are right, that the balance holds between models, and that every compromise refers to records that exist. Reviewers corrected 205, 207 to 212, and the generator's checks (word limits, banned words and characters) were run on every file.

The generator, `tools/build_catalogue.py`, then writes the Kotlin for each database (`Db205Economics.kt` and so on), the catalogue section and the README counts. It refuses a word count outside its limits, a dollar sign, Cyrillic text, a line break, the word "Wikipedia" and the banned process phrases.

## What batch 1 shows

- **Records:** 276 in batch 1, with 6,532 records in the app overall.
- **Documented cases:** 49 of the 276 records name a pool case. The rest are inferences, and each says so. The counts by database are: 205, 9; 206, 0; 207, 14; 208, 8; 209, 4; 210, 6; 211, 4; 212, 4.
- **Compromises:** 205 has one general compromise; 207 to 212 each have one practice compromise; 206 has the education compromise drawn from 207 to 212, fifteen principle-level compromises for the other spheres, and one for all spheres.

## What is weak

- **Documented coverage is low.** Only 49 of 276 records name a case. Most of the content is principle-based, not evidence-based. A reader must read the labels.
- **The pools are small**, three to eleven cases per database, and they cluster on some models. Mixed economies and socialist models have the most cases; several market and religious models have none.
- **Some sources are weak or unconfirmed.** The Nazi-economy case comes from exam-preparation material; the Komvux case from a source flagged for missing citations; the London Economics link needs checking before it is cited; the Soviet teacher-pay case gives no figure; and most entries in `models.json` are from memory.
- **Compromises are proposals**, not established models. Their test periods are conditions of the proposal, not findings.
- **Database 206** is principle-level for fifteen of its sixteen spheres. It has no case pool for them.

## How to continue

Two directions are open, and the choice is yours:

1. **Deepen batch 1.** Add cases to the pools by web search, then rewrite the records that gain a case, and review them again. Each pool needs several searches per database; the budget for a turn is the limit.
2. **Continue with databases 213 to 281**, in batches of about ten. For each database:
   1. Build its case pool by web search (`tools/economics/pools/db<id>.json`).
   2. Write the 37 records from the pool, with no searching (the writer's rules are in `tools/economics/`).
   3. Review the file against its pool, with no searching.
   4. Run `python3 tools/build_catalogue.py`, then `./gradlew testDebugUnitTest assembleDebug`.
   5. Add a DEVLOG checkpoint, regenerate the source pack, and commit.

Two items apply to either direction: check the 34 entries in `models.json` that are still from memory, and update database 206 when a sphere gets a practice database.
