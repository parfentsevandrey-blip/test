package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 324: the days between the battles of Adrianople and Chrysopolis, both in Constantine's war with Licinius. */
val db172Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Adrianople to the battle of Chrysopolis",
        originalTitle = "Days between two battles",
        author = "Constantine and Licinius",
        year = 324,
        summary = "The battle of Adrianople on 3 July 324 and the battle of Chrysopolis on 18 September 324 lie 77 days apart, and a short program counts the days between them.",
        details = "Both battles belong to Constantine's war with Licinius in 324, and the two dates fall in one year, so the count is the same whichever calendar is used for them. The example uses the Python calendar and prints the count, which can be checked by counting the months by hand.",
        instructions = """
            1. Save the example as adrianople_chrysopolis.py.
            2. Run it: python3 adrianople_chrysopolis.py
        """,
        example = """
            from datetime import date

            adrianople = date(324, 7, 3)     # the battle of Adrianople, 3 July 324
            chrysopolis = date(324, 9, 18)   # the battle of Chrysopolis, 18 September 324
            print("days from Adrianople to Chrysopolis:", (chrysopolis - adrianople).days)
        """,
        rare = true,
    ),

)
