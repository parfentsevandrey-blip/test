package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of 2010 to 2020: showpieces that squeeze the most out of a few lines of code. */
val db4Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.FLEX,
        section = "Python",
        title = "The walrus operator in a loop",
        originalTitle = "Assignment expressions (:=)",
        author = "Python community (PEP 572)",
        year = 2019,
        summary = "A loop that reads until an empty value, binding each value to a name inside the loop condition.",
        details = "Python 3.8, released in October 2019, added the := operator, which assigns a value and returns it in the same expression. " +
            "Putting the assignment in the while condition removes the usual read-before-the-loop line.",
        instructions = """
            1. Save the example as walrus.py.
            2. Run it: python3 walrus.py
        """,
        example = """
            # Read values until an empty one, using the walrus operator (Python 3.8).
            data = ["alpha", "beta", "", "gamma"]
            it = iter(data)
            while (line := next(it, "")) != "":
                print(line.upper())
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.FLEX,
        section = "Games in JavaScript",
        title = "Game of Life in a few lines of JavaScript",
        originalTitle = "Conway's Game of Life (js13k style)",
        author = "Contest practice of the js13k games competition",
        year = 2012,
        summary = "A glider moves across a grid after one generation, computed by counting neighbours in a few short loops.",
        details = "The js13k contest, which asks for a game in 13 kilobytes of JavaScript, began in 2012. Entries squeezed everything into a few lines, " +
            "and Conway's Game of Life was a favourite: each cell lives or dies by counting its eight neighbours. This example prints one generation of a glider.",
        instructions = """
            1. Save the code as life.js.
            2. Run it: node life.js
        """,
        example = """
            // One generation of Conway's Game of Life on a small wrapping grid.
            const size = 6;
            const grid = Array.from({ length: size }, () => Array(size).fill(0));
            [[1, 0], [2, 1], [0, 2], [1, 2], [2, 2]].forEach(([x, y]) => { grid[y][x] = 1; });

            const next = grid.map((row, y) => row.map((cell, x) => {
              let n = 0;
              for (let dy = -1; dy <= 1; dy++) {
                for (let dx = -1; dx <= 1; dx++) {
                  if (dx !== 0 || dy !== 0) {
                    n += grid[(y + dy + size) % size][(x + dx + size) % size];
                  }
                }
              }
              return n === 3 || (cell === 1 && n === 2) ? 1 : 0;
            }));

            next.forEach(row => console.log(row.map(c => (c ? "#" : ".")).join("")));
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.FLEX,
        section = "Databases",
        title = "Full-text search in one SQLite table",
        originalTitle = "FTS5 virtual table",
        author = "SQLite project",
        year = 2015,
        summary = "A virtual table that indexes every word of its text columns, so a query can match any word of a note.",
        details = "SQLite 3.9, released in October 2015, added the FTS5 full-text module. A table declared with USING fts5 keeps an index of the words in its columns, " +
            "and MATCH searches that index with OR, AND and phrase queries. The example searches three notes for two alternative words.",
        instructions = """
            1. Save the example as search.py.
            2. Run it: python3 search.py
            3. The output lists the notes that mention a compiler or a map.
        """,
        example = """
            import sqlite3

            db = sqlite3.connect(":memory:")
            db.execute("CREATE VIRTUAL TABLE notes USING fts5(title, body)")
            db.executemany("INSERT INTO notes (title, body) VALUES (?, ?)", [
                ("Shopping", "milk, eggs and bread"),
                ("Ideas", "a compiler for the toy language"),
                ("Travel", "train tickets and a map of the coast"),
            ])
            for (title,) in db.execute("SELECT title FROM notes WHERE notes MATCH ? ORDER BY title", ("compiler OR map",)):
                print(title)
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.FLEX,
        section = "Python",
        title = "Sorting a dictionary by its values",
        originalTitle = "Ordered dictionaries (Python 3.7)",
        author = "Python community",
        year = 2018,
        summary = "One line that turns a dictionary into a new one ordered by value, from the highest score down.",
        details = "Python 3.7 guaranteed that dictionaries keep their insertion order, so a sorted copy of a dictionary stays in the order it was built. " +
            "The sorted call takes the items and a key function, and dict rebuilds the mapping from the sorted pairs.",
        instructions = """
            1. Save the example as sort_scores.py.
            2. Run it: python3 sort_scores.py
        """,
        example = """
            scores = {"ann": 31, "bob": 12, "cy": 45}
            top = dict(sorted(scores.items(), key=lambda item: item[1], reverse=True))
            print(top)
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.FLEX,
        section = "Web styling",
        title = "CSS custom properties in a few rules",
        originalTitle = "CSS variables (custom properties)",
        author = "W3C CSS Working Group",
        year = 2016,
        summary = "A value declared once on the root and changed by a single class, so the whole stylesheet follows one setting.",
        details = "CSS custom properties, also called variables, were adopted by browsers between 2014 and 2017, and Chrome and Safari had them in 2016. " +
            "A property name that starts with two dashes holds a value, and var() reads it. A class can redefine the value for itself and everything inside it.",
        example = """
            :root {
              --accent: #d33;
              --gap: 12px;
            }
            .card {
              border-left: 4px solid var(--accent);
              padding: var(--gap);
            }
            .card.quiet {
              --accent: #888;
            }
        """,
    ),
)
