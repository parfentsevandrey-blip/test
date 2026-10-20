package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 150 to 160: the years between Galen's birth at Pergamon and his post there, counted in code. */
val db189Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.EXAMPLE,
        section = "Pergamon",
        title = "Galen's years from his birth at Pergamon to his post there, counted in code",
        originalTitle = "Galen's years at Pergamon",
        author = "Galen of Pergamon",
        year = 157,
        summary = "Galen was born at Pergamon in 129 and took up his post as physician to the gladiators there in 157, and a short program counts the years between the two dates.",
        details = "Accounts give 129 as the birth year, and the post is commonly dated to 157 from Galen's own account. The example counts years between the two dates, so it is a count of years and not Galen's exact age on any day of 157.",
        instructions = """
            1. Save the example as galen_years.py.
            2. Run it: python3 galen_years.py
        """,
        example = """
            BIRTH = 129   # Galen's birth at Pergamon, as commonly given
            POST = 157    # his post as physician to the gladiators there, as commonly given
            print("born in", BIRTH)
            print("post taken up in", POST)
            print("years between them:", POST - BIRTH)
        """,
        rare = true,
    ),

)
