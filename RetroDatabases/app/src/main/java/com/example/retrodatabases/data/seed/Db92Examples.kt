package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 1120 to 1130: the years of the two continuations of the Peterborough annals, counted in code. */
val db92Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.EXAMPLE,
        section = "Annals",
        title = "The years of the Peterborough annals, counted by continuation",
        originalTitle = "Anglo-Saxon Chronicle, manuscript E",
        author = "Monks of Peterborough Abbey",
        year = 1121,
        summary = "A short program counts the years of the two continuations of the Peterborough annals, the first to 1131 and the second from 1132 to 1154.",
        details = "The annals run to 1154, the First Continuation ends in 1131, and the Second, by another writer, covers 1132 to 1154. The example starts the First Continuation at 1121, the year used for the record, and counts the years inclusively, which gives eleven years for the first and twenty-three for the second.",
        instructions = """
            1. Save the example as peterborough_years.py.
            2. Run it: python3 peterborough_years.py
        """,
        example = """
            first = (1121, 1131)
            second = (1132, 1154)
            years_first = first[1] - first[0] + 1
            years_second = second[1] - second[0] + 1
            print("first continuation, years:", years_first)
            print("second continuation, years:", years_second)
            print("years in all:", years_first + years_second)
        """,
    ),

)
