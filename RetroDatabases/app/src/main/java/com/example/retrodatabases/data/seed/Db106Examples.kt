package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 980 to 990: the years of Otto II as sole ruler, counted in code. */
val db106Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.EXAMPLE,
        section = "Germany",
        title = "Otto II's sole rule, from 973 to 983, worked out in code",
        originalTitle = "Otto II's sole rule, from 973 to 983, worked out in code",
        author = "Otto II",
        year = 983,
        summary = "A short program works out the years from 973, when Otto II became sole ruler, to 983, the year of his death, and prints the count.",
        details = "Otto II had been king from 961 and was crowned emperor in 967, and he ruled alone from the death of his father Otto I in 973 until his own death at Rome in December 983. The program uses whole years only.",
        instructions = """
            1. Save the example as otto_years.py.
            2. Run it: python3 otto_years.py
        """,
        example = """
            start = 973
            death = 983
            print("sole ruler from", start, "to", death)
            print("years as sole ruler:", death - start)
        """,
    ),

)
