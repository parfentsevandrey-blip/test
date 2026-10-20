package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 660 to 670: the fourteen Umayyad caliphs of Damascus, from Muawiya I to Marwan II, counted in code. */
val db138Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.EXAMPLE,
        section = "Umayyad",
        title = "The fourteen Umayyad caliphs of Damascus, from Muawiya I in 661 to Marwan II in 750, counted in code",
        originalTitle = "al-khulafa al-umawiyyun",
        author = "The Umayyad caliphs of Damascus",
        year = 661,
        summary = "The Umayyad caliphs of Damascus are fourteen in the usual list, from Muawiya I to Marwan II, and a short program counts them and the years they span.",
        details = "The list runs from the first caliph, Muawiya I, to the last, Marwan II, who lost the throne in 750, a span of 89 years from 661. The program prints the count, the first and last names and the span, so it depends only on the list and the two years.",
        instructions = """
            1. Save the example as caliphs_count.py.
            2. Run it: python3 caliphs_count.py
        """,
        example = """
            CALIPHS = [
                "Muawiya I",
                "Yazid I",
                "Muawiya II",
                "Marwan I",
                "Abd al-Malik",
                "al-Walid I",
                "Sulayman",
                "Umar II",
                "Yazid II",
                "Hisham",
                "al-Walid II",
                "Yazid III",
                "Ibrahim",
                "Marwan II",
            ]

            print("caliphs counted:", len(CALIPHS))
            print("first:", CALIPHS[0])
            print("last:", CALIPHS[-1])
            print("years from 661 to 750:", 750 - 661)
        """,
    ),

)
