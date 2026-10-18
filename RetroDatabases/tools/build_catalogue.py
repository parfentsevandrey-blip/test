#!/usr/bin/env python3
"""Builds the catalogue from tools/databases.json and the record files, then checks every record.

Usage:
  python3 tools/build_catalogue.py          rewrite the generated parts, then run the checks
  python3 tools/build_catalogue.py --check  run the checks only and write nothing

Rewrites, from the spec and the record files:
  app/src/main/java/com/example/retrodatabases/data/seed/SeedCatalog.kt  (the whole file)
  CATALOGUE.md        the sections between the GENERATED CATALOGUE markers (databases not curated by hand)
  README.md           the blocks between the GENERATED OVERVIEW and GENERATED STRUCTURE markers

Checks: database ids and colours, the record files of every database, and for quota databases
(the 50-record batches) the kind counts, the runnable records, the rare flags, the years and the
text rules. Titles repeated anywhere in the catalogue are reported too.
Exit status is 1 when a check fails, so the script can run before the Gradle build.
"""
import argparse
import collections
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SEED = ROOT / "app/src/main/java/com/example/retrodatabases/data/seed"
SPEC = ROOT / "tools/databases.json"
CATALOGUE = ROOT / "CATALOGUE.md"
README = ROOT / "README.md"
SEED_CATALOG = SEED / "SeedCatalog.kt"

KINDS = ["LANGUAGE", "SYSTEM", "DEVICE", "TOOL", "PRACTICE", "GUIDE", "EXAMPLE", "PROJECT", "FLEX", "CROSS"]
KIND_LABELS = {"LANGUAGE": "Languages", "SYSTEM": "Systems", "DEVICE": "Devices", "TOOL": "Tools",
               "PRACTICE": "Practices", "GUIDE": "Guides", "EXAMPLE": "Examples", "PROJECT": "Projects",
               "FLEX": "Flex", "CROSS": "Cross-code"}
QUOTA_COUNTS = {"LANGUAGE": 6, "SYSTEM": 5, "DEVICE": 8, "TOOL": 6, "PRACTICE": 5,
                "GUIDE": 5, "EXAMPLE": 6, "PROJECT": 4, "FLEX": 3, "CROSS": 2}
RUNNABLE = {"GUIDE", "EXAMPLE", "PROJECT", "FLEX", "CROSS"}
FILE_SUFFIXES = ["Languages", "Systems", "Devices", "Tools", "Practices", "Guides", "Examples",
                 "Projects", "Flex", "Cross", "Catalog"]
FORBIDDEN = ["(memory)", "memory, not checked", "search source", "single source", "not checked",
             "checked in this batch", "web search", "wikipedia"]
ALLOWED_DUPLICATE_TITLES = {"Erlang", "Software inspections"}  # already in databases 1 and 2

ENTRY = re.compile(r"^    entry\(\n(.*?)^    \),", re.S | re.M)
TITLE = re.compile(r'title = "([^"]*)"')
KIND = re.compile(r"kind = Kind\.(\w+)")
YEAR = re.compile(r"year = (\d{3,4})")
DATABASE_ID = re.compile(r"databaseId = (\d+)")
CYRILLIC = re.compile(r"[\u0400-\u04FF]")


def load_spec(problems):
    rows = json.loads(SPEC.read_text(encoding="utf-8"))
    ids = [row["id"] for row in rows]
    if ids != list(range(1, len(rows) + 1)):
        problems.append("spec: ids must run 1 to N in order")
    colours = [row["colorHex"] for row in rows]
    if len(set(colours)) != len(colours):
        problems.append("spec: a colour is used twice")
    names = [row["name"] for row in rows]
    if len(set(names)) != len(names):
        problems.append("spec: a database name is used twice")
    for row in rows:
        if not re.fullmatch(r"#[0-9A-Fa-f]{6}", row["colorHex"]):
            problems.append(f"database {row['id']}: colour {row['colorHex']!r} is not #RRGGBB")
        if row["from"] > row["to"]:
            problems.append(f"database {row['id']}: years run backwards")
        if row["quota"] and row["to"] - row["from"] != 10:
            problems.append(f"database {row['id']}: a quota database covers ten years")
    return rows


def parse_records(db_id):
    """Every record of one database, read from its Kotlin files."""
    records = []
    for path in sorted(SEED.glob(f"Db{db_id}*.kt")):
        if not re.match(rf"Db{db_id}[A-Z]", path.name):
            continue  # Db1Languages.kt must not pick up Db10Languages.kt
        for block in ENTRY.findall(path.read_text(encoding="utf-8")):
            records.append({
                "db": db_id,
                "title": TITLE.search(block).group(1),
                "kind": KIND.search(block).group(1),
                "year": int(YEAR.search(block).group(1)),
                "rare": "rare = true" in block,
                # entry() defaults to database 1 when a record names no databaseId (old records of database 1)
                "database_id": int(DATABASE_ID.search(block).group(1)) if DATABASE_ID.search(block) else 1,
                "runnable": "instructions = " in block,
                "has_example": "example = " in block,
                "text": block,
                "file": path.name,
            })
    return records


def check_database(row, records, problems):
    n = row["id"]
    if not records:
        problems.append(f"database {n}: no records found in the seed folder")
        return
    if row["quota"]:
        for suffix in FILE_SUFFIXES:
            if not (SEED / f"Db{n}{suffix}.kt").exists():
                problems.append(f"database {n}: missing file Db{n}{suffix}.kt")
    if not re.search(rf"val db{n}Records\b", "\n".join(p.read_text(encoding="utf-8")
                                                       for p in SEED.glob(f"Db{n}*.kt"))):
        problems.append(f"database {n}: no val db{n}Records in its catalogue file")
    for record in records:
        if record["database_id"] != n:
            problems.append(f"database {n}: {record['title']!r} has databaseId {record['database_id']}")
    titles = [r["title"] for r in records]
    for title, count in collections.Counter(titles).items():
        if count > 1:
            problems.append(f"database {n}: title {title!r} appears {count} times")
    if not row["quota"]:
        return
    for record in records:
        if record["runnable"] != (record["kind"] in RUNNABLE):
            problems.append(f"database {n}: {record['title']!r} has instructions only if runnable")
        if record["has_example"] != (record["kind"] in RUNNABLE):
            problems.append(f"database {n}: {record['title']!r} has an example only if runnable")
    counts = collections.Counter(r["kind"] for r in records)
    for kind in KINDS:
        if counts[kind] != QUOTA_COUNTS[kind]:
            problems.append(f"database {n}: {kind} has {counts[kind]} records, expected {QUOTA_COUNTS[kind]}")
    if len(records) != 50:
        problems.append(f"database {n}: {len(records)} records, expected 50")
    rare = sum(1 for r in records if r["rare"])
    if not 7 <= rare <= 10:
        problems.append(f"database {n}: {rare} rare records, expected 7 to 10")
    for record in records:
        if not row["from"] <= record["year"] <= row["to"]:
            problems.append(f"database {n}: {record['title']!r} is dated {record['year']}, "
                            f"outside {row['from']} to {row['to']}")
        for phrase in FORBIDDEN:
            if phrase in record["text"].lower():
                problems.append(f"database {n}: {record['title']!r} contains {phrase!r}")
        if "$" in record["text"]:
            problems.append(f"database {n}: {record['title']!r} contains a dollar sign")
        if CYRILLIC.search(record["text"]):
            problems.append(f"database {n}: {record['title']!r} contains Cyrillic")


def check_catalogue(rows, all_records, problems):
    """A title may not appear in two databases; repeats inside one database are checked there."""
    databases_of = collections.defaultdict(set)
    for record in all_records:
        databases_of[record["title"]].add(record["db"])
    for title, owners in databases_of.items():
        if len(owners) > 1 and title not in ALLOWED_DUPLICATE_TITLES:
            problems.append(f"title {title!r} appears in databases {sorted(owners)}")


def wrap_names(names):
    """Joins names with ' + ' into lines of at most 120 characters."""
    lines, current = [], "    "
    for i, name in enumerate(names):
        piece = name + (" + " if i < len(names) - 1 else "")
        if current.strip() and len(current) + len(piece.rstrip()) > 120:
            lines.append(current.rstrip())
            current = "        "
        current += piece
    lines.append(current.rstrip())
    return "\n".join(lines)


def seed_catalog_text(rows):
    entities = []
    for row in rows:
        entities.append(
            "    DatabaseEntity(\n"
            f"        id = {row['id']},\n"
            f'        name = "{row["name"]}",\n'
            f'        description = "{row["description"]}",\n'
            f'        colorHex = "{row["colorHex"]}",\n'
            "    ),\n")
    years = "".join(f"    {r['id']}L to {r['from']}..{r['to']},\n" for r in rows)
    names = [f"db{r['id']}Records" for r in rows]
    return (
        "package com.example.retrodatabases.data.seed\n\n"
        "import com.example.retrodatabases.data.DatabaseEntity\n"
        "import com.example.retrodatabases.data.RecordEntity\n\n"
        "// Generated by tools/build_catalogue.py from tools/databases.json. Do not edit by hand.\n"
        "// To add a database: add its entry to tools/databases.json, write its record files\n"
        "// (databaseId = the new id, one file per kind) and run python3 tools/build_catalogue.py.\n\n"
        f"/** Version of the database file: the number of the newest seeded database. */\n"
        f"const val DATABASE_VERSION = {len(rows)}\n\n"
        "/** The catalogue of databases that are created on the first launch, in id order. */\n"
        "val seedDatabases: List<DatabaseEntity> = listOf(\n"
        + "".join(entities) +
        ")\n\n"
        "/** Years each seeded catalogue covers. The tests check the records against them. */\n"
        "val seedYears: Map<Long, IntRange> = mapOf(\n"
        + years +
        ")\n\n"
        "/** Records of every seeded database. They are inserted together with the databases. */\n"
        "val seedRecords: List<RecordEntity> =\n"
        + wrap_names(names) + "\n"
    )


def sentence_list(items):
    if len(items) <= 1:
        return "".join(items)
    return ", ".join(items[:-1]) + " and " + items[-1]


def catalogue_section(row, records):
    n = row["id"]
    lines = [f"The catalogue of database {n} ({row['from']} to {row['to']}) has the same ten kinds:", "",
             "| Kind | Records | What it is |", "|---|---:|---|"]
    for kind in KINDS:
        titles = [r["title"] for r in records if r["kind"] == kind]
        lines.append(f"| {KIND_LABELS[kind]} | {len(titles)} | {sentence_list(titles)} |")
    return "\n".join(lines) + "\n"


def replace_block(text, begin, end, body, path):
    if begin not in text or end not in text:
        sys.exit(f"{path.name}: add the markers {begin!r} and {end!r} once, where the block belongs")
    head, rest = text.split(begin, 1)
    _, tail = rest.split(end, 1)
    return head + begin + "\n" + body + end + tail


def overview_block(rows, by_db):
    total = sum(len(by_db[r["id"]]) for r in rows)
    rare_total = sum(1 for r in rows for x in by_db[r["id"]] if x["rare"])
    lines = [f"Inside are {len(rows)} databases of coding history, from {min(r['from'] for r in rows)} "
             f"to {max(r['to'] for r in rows)}. The table gives each one's years, records and rare records. "
             "The kinds and titles of every database are in [CATALOGUE.md](CATALOGUE.md).", "",
             "| Database | Years | Records | Rare |", "|---|---|---:|---:|"]
    for r in rows:
        records = by_db[r["id"]]
        rare = sum(1 for x in records if x["rare"])
        lines.append(f"| {r['id']} | {r['from']} to {r['to']} | {len(records)} | {rare} |")
    lines.append(f"| **All** | | **{total:,}** | **{rare_total}** |")
    return "\n".join(lines) + "\n"


def structure_block(rows):
    last = len(rows)
    return (
        f"- `app/src/main/java/com/example/retrodatabases/data` — Room: entities, DAO, converters, the database "
        f"itself, seeding on first launch and one generic migration for each version from 1 to {last} "
        f"(`seedMigrations`).\n"
        f"- `app/src/main/java/com/example/retrodatabases/data/seed` — the records of all {last} databases: "
        f"`Db1*.kt` to `Db{last}*.kt`, one file per kind; `SeedCatalog.kt`, generated from `tools/databases.json`; "
        f"the helper that builds a record (`SeedEntry.kt`).\n"
        f"- `tools` — `databases.json` (the databases, their years and colours) and `build_catalogue.py`, which "
        f"regenerates `SeedCatalog.kt`, the overview in this file and `CATALOGUE.md`, and checks every record.\n")


def catalogue_body(rows, by_db):
    sections = []
    for row in rows:
        if row["curated"]:
            continue  # written by hand in CATALOGUE.md, above the generated part
        sections.append(catalogue_section(row, by_db[row["id"]]))
    return "\n".join(sections)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="run the checks only; write nothing")
    args = parser.parse_args()

    problems = []
    rows = load_spec(problems)
    by_db = {row["id"]: parse_records(row["id"]) for row in rows}
    all_records = [r for records in by_db.values() for r in records]
    for row in rows:
        check_database(row, by_db[row["id"]], problems)
    check_catalogue(rows, all_records, problems)

    if problems:
        print("checks failed:")
        for problem in problems:
            print("  -", problem)
        sys.exit(1)
    print(f"checks passed: {len(rows)} databases, {len(all_records)} records")

    if args.check:
        return
    SEED_CATALOG.write_text(seed_catalog_text(rows), encoding="utf-8")
    CATALOGUE.write_text(replace_block(CATALOGUE.read_text(encoding="utf-8"),
                                       "<!-- BEGIN GENERATED CATALOGUE -->", "<!-- END GENERATED CATALOGUE -->",
                                       catalogue_body(rows, by_db), CATALOGUE), encoding="utf-8")
    text = README.read_text(encoding="utf-8")
    text = replace_block(text, "<!-- BEGIN GENERATED OVERVIEW -->", "<!-- END GENERATED OVERVIEW -->",
                         overview_block(rows, by_db), README)
    text = replace_block(text, "<!-- BEGIN GENERATED STRUCTURE -->", "<!-- END GENERATED STRUCTURE -->",
                         structure_block(rows), README)
    README.write_text(text, encoding="utf-8")
    print("wrote SeedCatalog.kt, the generated part of CATALOGUE.md and the blocks of README.md")


if __name__ == "__main__":
    main()
