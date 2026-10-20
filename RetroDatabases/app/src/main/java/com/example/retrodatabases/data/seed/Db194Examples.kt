package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Example of 100 to 110: the years of the First Great Qiang Rebellion in the Han northwest, counted in code inclusively and by difference. */
val db194Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "The First Great Qiang Rebellion, 107 to 118, counted year by year in code",
        originalTitle = "First Great Qiang Rebellion",
        author = "No single author",
        year = 107,
        summary = "The First Great Qiang Rebellion in the Han northwest is dated from 107 to 118, and a short program counts its years both inclusively and by difference.",
        details = "Accounts differ on the end year, and a wider reading that counts later uprisings runs to 169. The example uses 107 to 118 as commonly given and prints the count inclusively and by difference, so the counting convention is visible.",
        instructions = """
            1. Save the example as qiang_years.py.
            2. Run it: python3 qiang_years.py
        """,
        example = """
            start = 107   # the revolt begins, as commonly given
            end = 118     # the revolt is quelled, as commonly given
            years = list(range(start, end + 1))
            print("years counted inclusively:", len(years))
            print("years by difference:", end - start)
            print("first and last year:", years[0], years[-1])
        """,
        rare = true,
    ),

)
