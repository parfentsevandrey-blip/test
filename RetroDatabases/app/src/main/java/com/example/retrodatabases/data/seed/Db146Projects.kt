package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 580 to 590: the reigns of the Sasanian king Hormizd IV and the Byzantine emperor Maurice, counted and ranked by a short program. */
val db146Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.PROJECT,
        section = "Persian and Byzantine",
        title = "The reigns of the Sasanian king Hormizd IV and the Byzantine emperor Maurice, 579 to 602, counted and ranked in code",
        originalTitle = "Hormizd IV and Maurice",
        author = "Persian and Byzantine chroniclers",
        year = 582,
        summary = "The reigns of Hormizd IV, from 579 to 590, and Maurice, from 582 to 602, are counted in years, summed and ranked by a short program.",
        details = "The year 582 is the accession of Maurice, the anchor for this record, and the two reigns overlap from 582 until Hormizd was overthrown in 590. The program counts 11 years for Hormizd and 20 for Maurice, 31 in all, and names the longer reign.",
        instructions = """
            1. Save the example as reigns_579_602.py.
            2. Run it: python3 reigns_579_602.py
        """,
        example = """
            REIGNS = [
                ("Hormizd IV", 579, 590),
                ("Maurice", 582, 602),
            ]

            lengths = [(name, end - start) for name, start, end in REIGNS]
            for name, years in lengths:
                print(name, years, "years")
            print("total:", sum(years for _, years in lengths), "years")
            longest = max(lengths, key=lambda item: item[1])
            print("longest:", longest[0], longest[1], "years")
        """,
    ),

)
