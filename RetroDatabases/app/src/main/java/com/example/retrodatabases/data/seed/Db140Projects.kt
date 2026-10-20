package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 640 to 650: the Councils of Toledo from 633 to 653, with the gaps between them counted by a short program. */
val db140Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.PROJECT,
        section = "Visigothic",
        title = "The Councils of Toledo from 633 to 653, the gaps between them counted by a short program",
        originalTitle = "Concilium Toletanum",
        author = "Visigothic bishops and kings",
        year = 646,
        summary = "The Visigothic councils of Toledo, held in 633, 636, 638, 646 and 653, are spaced out by a short program that prints each gap and the total span.",
        details = "The councils were church assemblies held in the Visigothic kingdom of Spain, and the seventh of them met in 646. The gaps the program prints are 3, 2, 8 and 7 years, which add up to a span of 20 years from the first council to the last.",
        rare = true,
        instructions = """
            1. Save the example as toledo_gaps.py.
            2. Run it: python3 toledo_gaps.py
        """,
        example = """
            COUNCILS = [("Fourth", 633), ("Fifth", 636), ("Sixth", 638), ("Seventh", 646), ("Eighth", 653)]
            gaps = []
            for (name_a, year_a), (name_b, year_b) in zip(COUNCILS, COUNCILS[1:]):
                gaps.append(year_b - year_a)
                print(name_a, "council", year_a, "to", name_b, "council", year_b, ":", year_b - year_a, "years")
            print("longest gap:", max(gaps), "years")
            print("total span:", COUNCILS[-1][1] - COUNCILS[0][1], "years")
        """,
    ),

)
