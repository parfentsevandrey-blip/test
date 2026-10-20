package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 160 to 170: the years from Gaius's Institutes to the Roman embassy of 166, counted in one line of code. */
val db188Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Gaius's Institutes to the Roman embassy of 166",
        originalTitle = "Years between two dates",
        author = "Gaius and the envoys of 166",
        year = 166,
        summary = "Gaius's Institutes date to about 161 and the Roman embassy reached the Han court in 166, so a short program counts five years between them.",
        details = "Both years are given as stated: 161 for the Institutes, which sources place between 160 and 162, and 166 for the embassy in the ninth year of Yanxi. The program subtracts one year from the other, so the answer is exact for the years as written and says nothing about months.",
        instructions = """
            1. Save the example as years_gap.py.
            2. Run it: python3 years_gap.py
        """,
        example = """
            gaius = 161       # the Institutes, about 161
            embassy = 166     # the envoys at the Han court, ninth year of Yanxi
            print("years from Gaius's Institutes to the embassy:", embassy - gaius)
        """,
    ),

)
