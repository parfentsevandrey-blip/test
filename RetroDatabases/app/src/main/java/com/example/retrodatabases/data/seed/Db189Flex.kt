package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 150 to 160: the years from the Nabonassar epoch of 747 BC to Justin Martyr's First Apology, in one line of arithmetic. */
val db189Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Nabonassar epoch of 747 BC to Justin Martyr's First Apology",
        originalTitle = "Years between two dated points",
        author = "Chronographers of the period",
        year = 155,
        summary = "The Nabonassar era began in 747 BC, and Justin Martyr's First Apology is dated about 155, so a short program counts the years between them with no year zero.",
        details = "Counted with astronomical numbering, in which 1 BC is year 0, 747 BC is year minus 746, and the difference to year 155 is 901 years. The date of the Apology is given as about 155, so the count is only as exact as that date.",
        instructions = """
            1. Save the example as nabonassar_years.py.
            2. Run it: python3 nabonassar_years.py
        """,
        example = """
            EPOCH = -746    # 747 BC in astronomical numbering, where 1 BC is year 0
            APOLOGY = 155   # AD 155, the date usually given for the First Apology
            print("years from the Nabonassar epoch to Justin Martyr's First Apology:", APOLOGY - EPOCH)
        """,
        rare = true,
    ),

)
