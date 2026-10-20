package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 230 to 240: the years from the Gordian revolt of 238 to the death of Gordian III in 244. */
val db181Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the revolt of Gordian I and II in 238 to the death of Gordian III in 244",
        originalTitle = "Years between two dated events",
        author = "The Gordian emperors",
        year = 238,
        summary = "The revolt of Gordian I and II in 238 and the death of Gordian III in 244 are six years apart, and a short program counts the years between them.",
        details = "Both years are given as commonly stated, and the example uses the years alone, so it tests subtraction rather than the events. Accounts of how Gordian III died differ, and this record gives no account of his death.",
        instructions = """
            1. Save the example as gordian_years.py.
            2. Run it: python3 gordian_years.py
        """,
        example = """
            revolt = 238   # the revolt of Gordian I and II in Africa, as commonly given
            death = 244    # the death of Gordian III, as commonly given
            print("years from the revolt to the death:", death - revolt)
        """,
    ),

)
