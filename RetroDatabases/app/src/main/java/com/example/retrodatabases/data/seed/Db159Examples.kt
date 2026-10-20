package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of the 450s: the fourteen days of the Vandal sack of Rome, counted from 2 June 455 in code. */
val db159Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.EXAMPLE,
        section = "History",
        title = "The fourteen days of the Vandal sack of Rome, counted from 2 June 455",
        originalTitle = "Vandal sack of Rome",
        author = "Gaiseric and the Vandals",
        year = 455,
        summary = "The Vandal sack of Rome began on 2 June 455 and lasted fourteen days, and a short program counts the days from its first day to its last.",
        details = "The count uses the Python calendar with the first and last days as commonly given, so the output shows date arithmetic and not the loot or the damage. Procopius reports the sack as lasting fourteen days, and the program prints both dates before it counts the gap.",
        instructions = """
            1. Save the example as sack.py.
            2. Run it: python3 sack.py
        """,
        example = """
            from datetime import date

            start = date(455, 6, 2)
            end = date(455, 6, 16)
            print("first day:", start.day, start.month, start.year)
            print("last day:", end.day, end.month, end.year)
            print("days of the sack:", (end - start).days)
        """,
        rare = true,
    ),

)
