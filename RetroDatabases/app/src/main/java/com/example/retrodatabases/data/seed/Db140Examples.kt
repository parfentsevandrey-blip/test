package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 640 to 650: the Jin shu's 130 juan, split into annals, treatises, biographies and records of regimes, summed in code. */
val db140Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.EXAMPLE,
        section = "Chinese",
        title = "The Jin shu's 130 juan, split into annals, treatises, biographies and records of regimes, summed in code",
        originalTitle = "Jin shu",
        author = "Fang Xuanling and a team of compilers",
        year = 648,
        summary = "The four parts of the Jin shu, 10, 20, 70 and 30 juan, are added up by a short program to its total of 130 juan.",
        details = "The divisions are the ones usually given for the official history of the Jin dynasty, and the program prints a running total after each part. The example checks only the addition, so its total matches the figure given for the whole work.",
        instructions = """
            1. Save the example as jin_parts.py.
            2. Run it: python3 jin_parts.py
        """,
        example = """
            PARTS = [("annals", 10), ("treatises", 20), ("biographies", 70), ("records of regimes", 30)]
            total = 0
            for name, juan in PARTS:
                total += juan
                print(name, juan, "juan, running total", total)
            print("total juan:", total)
        """,
    ),

)
