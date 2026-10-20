package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 620 to 630: the years from Xuanzang's departure for India in 629 to his return in 645. */
val db142Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Xuanzang's departure for India in 629 to his return to Chang'an in 645",
        originalTitle = "Years of a pilgrimage",
        author = "Xuanzang",
        year = 629,
        summary = "Xuanzang's departure in 629 and his return in 645 are sixteen years apart, and a short program prints the gap.",
        details = "The two dates are the usual ones for the start and end of his journey, so the span is only the arithmetic of those dates. The program prints the difference, which a reader can check against the two years.",
        instructions = """
            1. Save the example as xuanzang_years.py.
            2. Run it: python3 xuanzang_years.py
        """,
        example = """
            DEPARTURE = 629  # Xuanzang leaves Chang'an for India, as usually dated
            RETURN = 645     # Xuanzang returns to Chang'an, as usually dated
            print("years from the departure of 629 to the return of 645:", RETURN - DEPARTURE)
        """,
    ),

)
