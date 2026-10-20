package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 590 to 600: the 163 years that Evagrius's Ecclesiastical History covers from 431 to 594, counted in code. */
val db145Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.EXAMPLE,
        section = "Greek",
        title = "Evagrius's history, which runs from 431 to 594, measured by a short program",
        originalTitle = "Ekklesiastike historia",
        author = "Evagrius Scholasticus",
        year = 594,
        summary = "A short program measures the span of Evagrius's Ecclesiastical History, which covers the years 431 to 594, and prints its six books beside it.",
        details = "The span is the one given in the usual description of the work, and some catalogues open the narrative in 428 with the Nestorian dispute, so the start is conventional. The program only subtracts the two years, and it prints the six books of the work as a fixed number.",
        instructions = """
            1. Save the example as evagrius_span.py.
            2. Run it: python3 evagrius_span.py
        """,
        example = """
            START = 431  # the year the history's span is usually given as opening
            END = 594    # the last year that the history covers
            print("years covered from 431 to 594:", END - START)
            print("books in the history:", 6)
        """,
    ),

)
