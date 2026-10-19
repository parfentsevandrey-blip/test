package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 810 to 820: the years from Louis the Pious's Ordinatio of 817 to his death in 840. */
val db123Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Louis the Pious's Ordinatio of 817 to his death in 840",
        originalTitle = "Years between two Frankish dates",
        author = "Louis the Pious",
        year = 817,
        summary = "Louis the Pious issued the Ordinatio imperii in 817 and died in 840, and a short program counts the 23 years between the two dates.",
        details = "The Ordinatio was the succession plan that made Lothair co-emperor, and 840 is the usual date of Louis's death. The example prints the difference between the two years.",
        instructions = """
            1. Save the example as ordinatio_span.py.
            2. Run it: python3 ordinatio_span.py
        """,
        example = """
            ORDINATIO = 817   # Louis the Pious issues the Ordinatio imperii
            DEATH = 840       # Louis the Pious dies, as usually given
            print("years from the Ordinatio to the death of Louis:", DEATH - ORDINATIO)
        """,
    ),

)
