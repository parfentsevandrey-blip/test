package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1042: the days from the death of Michael IV to the accession of Constantine IX. */
val db100Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Michael IV to the accession of Constantine IX",
        originalTitle = "Days between two fixed dates",
        author = "Michael IV and Constantine IX Monomachos",
        year = 1042,
        summary = "Michael IV died on 10 December 1041 and Constantine IX came to the throne on 11 June 1042, and a short program counts the 183 days between the two dates.",
        details = "Both dates are given as commonly stated, and neither 1041 nor 1042 was a leap year, so the span holds no 29 February. The example tests date arithmetic and says nothing about the reign that followed.",
        instructions = """
            1. Save the example as michael_constantine.py.
            2. Run it: python3 michael_constantine.py
        """,
        example = """
            from datetime import date

            michael = date(1041, 12, 10)      # Michael IV's death, as commonly given
            constantine = date(1042, 6, 11)   # Constantine IX's accession, as commonly given
            print("days from Michael IV's death to Constantine IX's accession:", (constantine - michael).days)
        """,
    ),

)
