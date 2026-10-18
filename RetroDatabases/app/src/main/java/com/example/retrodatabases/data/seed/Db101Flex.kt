package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1037 to 1040: days from Conrad II's Constitutio de feudis to the battle of Dandanaqan. */
val db101Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Conrad II's Constitutio de feudis to the battle of Dandanaqan",
        originalTitle = "Days between two fixed dates",
        author = "Conrad II and Tughril",
        year = 1040,
        summary = "Conrad II's Constitutio is dated 28 May 1037 and the battle of Dandanaqan 23 May 1040, and a short program counts the 1091 days between the two dates.",
        details = "Both dates are given as commonly stated, and the span holds one leap day, 29 February 1040, which the Python calendar counts in the same way as the Julian reckoning. The count tests date arithmetic, not the law or the battle.",
        instructions = """
            1. Save the example as constitutio_dandanaqan.py.
            2. Run it: python3 constitutio_dandanaqan.py
        """,
        example = """
            from datetime import date

            constitutio = date(1037, 5, 28)    # Conrad II's Constitutio de feudis, as commonly dated
            dandanaqan = date(1040, 5, 23)     # the battle of Dandanaqan, as commonly dated
            print("days from the Constitutio to Dandanaqan:", (dandanaqan - constitutio).days)
        """,
    ),

)
