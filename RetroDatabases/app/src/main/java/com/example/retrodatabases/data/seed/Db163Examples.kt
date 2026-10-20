package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 410 to 418: the seven books of Orosius's history and the years its last books span, counted in code. */
val db163Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.EXAMPLE,
        section = "History",
        title = "The seven books of Orosius's history and the years from 146 BC to AD 417",
        originalTitle = "Historiae adversus paganos",
        author = "Paulus Orosius",
        year = 417,
        summary = "Orosius's Seven Books of History against the Pagans, written at Augustine's request, runs down to about 417, and a short program counts its books and the years its last books span.",
        details = "Augustine asked Orosius for the history in 416, and its last three books run from the end of the Third Punic War in 146 BC to the years around 417. Accounts differ on the end: the narrative is said to stop in 417, while most sources give 418 as the year of completion, so the example uses 417.",
        instructions = """
            1. Save the example as orosius_books.py.
            2. Run it: python3 orosius_books.py
        """,
        example = """
            books = 7
            START = -145   # 146 BC, with 1 BC counted as year 0
            END = 417      # AD 417, where one account ends the narrative
            print("books:", books)
            print("years from 146 BC to AD 417:", END - START)
        """,
        rare = true,
    ),

)
