package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 770 to 780: the Lombard kingdom's 206 years in Italy, from 568 to 774, split at the years 600 and 700. */
val db127Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.EXAMPLE,
        section = "Lombard",
        title = "The Lombard kingdom's 206 years in Italy, split at the years 600 and 700 in code",
        originalTitle = "Langobardi in Italia",
        author = "Alboin and Desiderius",
        year = 774,
        summary = "The Lombard kingdom in Italy ran from the Lombard entry in 568 to the fall of Pavia in 774, and a short program splits its 206 years at the years 600 and 700.",
        details = "The Lombards entered Italy under Alboin in 568, and the kingdom ended when Pavia fell in 774, so the program takes the two dates as they are commonly given. The shares it prints are 32 years to 600, 100 years from 600 to 700 and 74 years from 700 to 774, and together they make the full span.",
        instructions = """
            1. Save the example as lombard_centuries.py.
            2. Run it: python3 lombard_centuries.py
        """,
        example = """
            ENTRY = 568   # the Lombards enter Italy under Alboin, as commonly dated
            END = 774     # Pavia falls and the Lombard kingdom ends, as commonly dated
            TURNS = [600, 700]
            shares = [TURNS[0] - ENTRY, TURNS[1] - TURNS[0], END - TURNS[1]]
            print("years to 600:", shares[0])
            print("years from 600 to 700:", shares[1])
            print("years from 700 to 774:", shares[2])
            print("total years in Italy:", sum(shares))
        """,
    ),

)
