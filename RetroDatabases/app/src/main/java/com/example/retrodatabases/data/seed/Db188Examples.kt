package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 160 to 170: the nine years of the Yanxi era, 158 to 166, counted in code, with the Roman embassy in its ninth year. */
val db188Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.EXAMPLE,
        section = "Eras",
        title = "The nine years of the Yanxi era, 158 to 166, counted in code",
        originalTitle = "Yanxi era years",
        author = "No single author",
        year = 166,
        summary = "The Hou Hanshu dates the Roman embassy to the ninth year of the Yanxi era, which is 166, and a short program counts the nine years of the era with their Christian years.",
        details = "The era is taken to begin in 158, so its first year is 158 and its ninth year is 166, the year of the embassy recorded in the Hou Hanshu. The example lists each year of the era and prints the count, so the answer depends only on the start year.",
        rare = true,
        instructions = """
            1. Save the example as yanxi.py.
            2. Run it: python3 yanxi.py
        """,
        example = """
            START = 158   # Yanxi year 1, as commonly given
            years = [(n, START + n - 1) for n in range(1, 10)]
            print("years in the Yanxi era counted:", len(years))
            for n, ad in years:
                print("Yanxi", n, "=", ad)
            print("the embassy, in Yanxi 9, falls in", years[8][1])
        """,
    ),

)
