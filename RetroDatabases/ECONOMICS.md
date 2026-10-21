# Economics databases

This document describes the economics part of the catalogue: what it is, how its records are written, what is done, and what is needed to continue. The coding databases (1 to 204) are described in [README.md](README.md) and [CATALOGUE.md](CATALOGUE.md).

## What is planned

The economics databases ask one question for every part of society: what does each economic model of humanity do well and badly there, and what would a compromise between them look like?

- Thirty-six economic models, from gift and household economies to market socialism, the commons and degrowth. `tools/economics/models.json` lists each one with its family, canonical year, original title, author, core idea and key sources.
- Sixteen spheres of society, with seventy-five practices inside them. `tools/economics/spheres.json` lists them; each practice has a scope sentence that its records must stay inside.
- Database 205 holds the models: each model's best and worst practice for the economy as a whole, and one general compromise.
- Database 206 holds one compromise for each sphere, and one that applies to all spheres.
- Databases 207 to 281 hold one practice each. Each has, for every model, a best and a worst practice for that practice, and one compromise for the practice. Databases 207 to 212 are the six education practices.

The planned size is 77 databases and 2,829 records: 37 in each model or practice database (36 models and one compromise), and 17 in database 206.

## How a record is written

A model record in a practice database has these fields:

- **Title**: the model name and the practice's short name, for example "Feudalism: schooling". Database 205 uses the model name alone.
- **Section**: the model's family. **Year, author and original title** come from `models.json`; the writer does not enter them.
- **Summary**: the best practice the model gives for the practice, 15 to 70 words. It names a documented case (a place, a law, a programme, a year) when one exists.
- **Details**: the worst practice or outcome, 25 to 110 words, ending with `Source:` and the author and year of the source. When no documented case exists, the record says so and gives the inference: "Inferred from its principle that ...; no documented case found."

The compromise record of a practice database has these fields:

- **Summary**: the design proposed for the practice, 20 to 90 words.
- **Details**: `Keeps:` the best practices kept, naming the models they come from; `Blocks:` the worst practices blocked, and the safeguard for each; `Test:` what to measure, and over what period; `Source:` the sources of the principles. 40 to 170 words in all.

Rules for every record:

- Every model gets a best and a worst practice. The same standard applies to every model, whatever its family.
- Every year, number, name, law and event is checked against a source before it is written. A figure is written only when a cited source gives it. A claim that cannot be checked is removed, not softened.
- No dollar signs, no Cyrillic letters, no line breaks, and none of the process phrases that the generator refuses.
- The compromise is a proposal, not an established model. Where it is untested, the record says so.

The generator (`tools/build_catalogue.py`) checks word counts, the `Source:` and `Keeps:`/`Blocks:` markers, years inside the database's range, unique titles, and the banned characters and phrases. It does not check facts. Facts are checked by search, before the file is written and again by a second reviewer.

## Status

Done and pushed:

- The model list, the sphere and practice map, and the spec rows for databases 205 to 212 (`tools/economics`, `tools/databases.json`).
- Generator support: each economics database is read from one JSON file, checked, and written as `Db<id>Economics.kt`, with its section in `CATALOGUE.md` and its counts in the README overview.
- Two record kinds: "Economic models" (`MODEL`) and "Compromises" (`COMPROMISE`), with colours, the assistant's labels and its search words.
- `EconomicsSeedTest`, which checks the record counts and the `Source:` and `Keeps:`/`Blocks:` markers, and a correction to `SeedDataTest`, which had required every kind in every database.

Not done:

- No economics record exists. `SeedCatalog.kt` still lists 204 databases, so `EconomicsSeedTest` fails and the generator's check fails for databases 205 to 212, which have no JSON file yet.
- The JSON files for databases 205 and 207 to 212 are not written. Database 206 depends on the practice compromises, so it is not started.
- The model list (years, authors and original titles) was written from memory. It has not been checked against sources.

Writing stopped at the web-search budget. The budget is shared by the whole turn. The first batch of seven writers used it before any file was written: database 212's writer stopped after 20 searches, and the other six were stopped by hand. The facts those writers reported as checked are recorded in the DEVLOG, not in any record.

## How to resume

1. Raise the web-search budget (`CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION`). Checking every documented case by search needs far more searches than the coding batches did.
2. Check `tools/economics/models.json` against sources, then write databases 205 and 207 to 212, one JSON file each, following the rules above.
3. Check every record by search. Give each file to a reviewer who did not write it, and let the reviewer correct the file in place.
4. Write database 206 last, from the verified compromises of the practice databases and the general compromise of database 205.
5. Run `python3 tools/build_catalogue.py`. It writes the Kotlin files, the catalogue and the README blocks, and runs the checks.
6. Run `./gradlew testDebugUnitTest assembleDebug`. All tests should pass, including `EconomicsSeedTest`.
7. Add a DEVLOG checkpoint, regenerate the source pack, and commit.

Databases 213 to 281 follow the same steps, in batches of about ten.
