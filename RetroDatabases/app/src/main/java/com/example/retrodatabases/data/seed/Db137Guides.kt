package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 670 to 680: how three Visigothic gold tremisses made one solidus, worked out by a program, placed at 672. */
val db137Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.GUIDE,
        section = "Visigothic",
        title = "How three gold tremisses made one solidus, the Visigothic coin rule, worked out by a program",
        originalTitle = "tremissis",
        author = "The Visigothic royal mint",
        year = 672,
        summary = "A Visigothic gold tremissis was a third of a solidus, so a short program counts how many tremisses a given number of solidi makes.",
        details = "Tremisses were a third of the solidus, the gold coin of the Roman and Byzantine world, and the coins of this rule are not dated to one year. The year 672, when Wamba became king, is the placed year of this record, the start of a documented reign and not the date of any coin.",
        rare = true,
        instructions = """
            1. Save the example as tremisses.py.
            2. Run it: python3 tremisses.py
        """,
        example = """
            TREMISSES_PER_SOLIDUS = 3

            def tremisses(solidi):
                return solidi * TREMISSES_PER_SOLIDUS

            for solidi in (1, 2, 5):
                print("solidi:", solidi, "tremisses:", tremisses(solidi))
            print("one tremissis as a fraction of a solidus:", f"1/{TREMISSES_PER_SOLIDUS}")
        """,
    ),

)
