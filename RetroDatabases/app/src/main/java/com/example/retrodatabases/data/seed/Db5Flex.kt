package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of 2020 to 2026: showpieces that squeeze the most out of a few lines of code. */
val db5Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.FLEX,
        section = "Python",
        title = "Batching with itertools.batched",
        originalTitle = "itertools.batched (Python 3.12)",
        author = "The Python core developers",
        year = 2023,
        summary = "A function that splits any iterable into fixed-size groups, with a shorter last group when the count does not divide evenly.",
        details = "Python 3.12 added itertools.batched. It yields tuples of a chosen size from any iterable, so records can be processed in chunks " +
            "without counting indexes by hand.",
        instructions = """
            1. Save the example as batches.py.
            2. Run it with Python 3.12 or newer: python3 batches.py
        """,
        example = """
            from itertools import batched

            for group in batched(range(1, 11), 4):
                print(group)
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.FLEX,
        section = "Databases",
        title = "Getting inserted rows back from SQLite",
        originalTitle = "RETURNING clause (SQLite 3.35)",
        author = "The SQLite project",
        year = 2021,
        summary = "An INSERT that returns the values it stored, so the new row's id comes back in the same statement.",
        details = "SQLite 3.35, released in March 2021, added RETURNING to INSERT, UPDATE and DELETE. The statement hands back the affected rows, " +
            "so an application can read generated ids without a second query.",
        example = """
            import sqlite3

            db = sqlite3.connect(":memory:")
            db.execute("CREATE TABLE tasks (id INTEGER PRIMARY KEY, title TEXT)")
            row = db.execute("INSERT INTO tasks (title) VALUES (?) RETURNING id, title", ("write the log",)).fetchone()
            print(row)
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.FLEX,
        section = "JavaScript",
        title = "Logical assignment operators",
        originalTitle = "ES2021 logical assignment",
        author = "TC39 (ECMAScript 2021)",
        year = 2021,
        summary = "Operators such as ??= and ||= that assign only when a condition on the current value holds.",
        details = "ECMAScript 2021 added the logical assignment operators. A ??= b assigns only when the left side is null or undefined, while ||= and &&= test for truthiness. " +
            "They save a line and make defaults easy to read.",
        example = """
            const settings = { theme: null, size: 0 };
            settings.theme ??= "dark";
            settings.size ||= 12;
            settings.size &&= settings.size * 2;
            console.log(settings);
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.FLEX,
        section = "Go",
        title = "Range over integers in Go",
        originalTitle = "Go 1.22 range over int",
        author = "The Go team",
        year = 2024,
        summary = "A for loop that ranges over a number, so counting loops read as range loops.",
        details = "Go 1.22, released in February 2024, let range take an integer, which counts from zero up to one less than the value. " +
            "The same release gave each loop iteration its own copy of the loop variable, which fixed a long-standing closure trap.",
        example = """
            package main

            import "fmt"

            func main() {
                for i := range 3 {
                    fmt.Println("step", i)
                }
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.FLEX,
        section = "Web styling",
        title = "The :has selector",
        originalTitle = "CSS :has() relational pseudo-class",
        author = "The W3C CSS Working Group",
        year = 2023,
        summary = "A selector that matches a parent by what it contains, such as a card that holds an image.",
        details = ":has() reached Safari and Chrome in 2022 and Firefox in late 2023, so it works in all major browsers from 2024. " +
            "Styles can now depend on children, which used to need a script or a class added by hand.",
        example = """
            /* A card gets a highlighted border only when it contains an image. */
            .card:has(img) {
              border: 2px solid #3a7bd5;
            }
        """,
    ),
)
