package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 720 to 730: three Chinese reference compilations from 727 to 983, the years between them summed and ranked by a short program. */
val db132Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.PROJECT,
        section = "Chinese",
        title = "Three Chinese reference compilations from 727 to 983, the years between them counted and ranked",
        originalTitle = "Chuxueji, Kaiyuan Zhanjing and Taiping Yulan",
        author = "Tang and Song compilers",
        year = 727,
        summary = "The Chuxueji of 727, the Kaiyuan Zhanjing of 729 and the Taiping Yulan of 983 are three Chinese reference compilations, and a short program counts and ranks the years between them.",
        details = "The Chuxueji and the Kaiyuan Zhanjing are Tang works of the Kaiyuan period, dated here to 727 and 729, and the Taiping Yulan is a Song encyclopedia of 983. The spans are 2 years and 254 years, the total from 727 to 983 is 256 years, and the longest span runs from the Kaiyuan Zhanjing to the Taiping Yulan.",
        rare = true,
        instructions = """
            1. Save the example as compilations.py.
            2. Run it: python3 compilations.py
        """,
        example = """
            COMPILATIONS = [
                ("Chuxueji", 727),
                ("Kaiyuan Zhanjing", 729),
                ("Taiping Yulan", 983),
            ]

            gaps = {}
            for (name_a, year_a), (name_b, year_b) in zip(COMPILATIONS, COMPILATIONS[1:]):
                gaps[name_a + " to " + name_b] = year_b - year_a
            for label, years in gaps.items():
                print(label, years, "years")
            print("total from 727 to 983:", COMPILATIONS[-1][1] - COMPILATIONS[0][1], "years")
            longest = max(gaps, key=gaps.get)
            print("longest gap:", longest, gaps[longest], "years")
        """,
    ),

)
