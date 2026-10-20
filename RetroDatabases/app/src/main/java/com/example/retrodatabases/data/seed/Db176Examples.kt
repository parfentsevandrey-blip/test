package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 280 to 290: the years and decades in the span of the Three Kingdoms, from 220 to 280, counted in code. */
val db176Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "The span of the Three Kingdoms, from 220 to 280, counted in code",
        originalTitle = "Years in a span of two dates",
        author = "No single author",
        year = 280,
        summary = "The Three Kingdoms are conventionally dated from 220 to 280, and a short program counts the years of that span and the decades in it.",
        details = "The span is counted from 220 up to but not including 280, so it holds 60 years and six decades, and counting both ends would give 61, the error the example avoids. The start is Cao Pi's Wei in 220 and the end is the Jin conquest of Wu in 280, both conventional dates.",
        instructions = """
            1. Save the example as span.py.
            2. Run it: python3 span.py
        """,
        example = """
            start = 220   # conventional start of the Three Kingdoms, with Cao Pi's Wei
            end = 280     # the Jin conquest of Wu, the conventional end
            years = list(range(start, end))
            print("years in the span:", len(years))
            print("decades in the span:", len(years) // 10)
        """,
    ),

)
