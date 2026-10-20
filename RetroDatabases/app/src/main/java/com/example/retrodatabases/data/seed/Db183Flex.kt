package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 210 to 220: the years from Caracalla's Constitutio Antoniniana to his death. */
val db183Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Constitutio Antoniniana to the death of Caracalla",
        originalTitle = "Years between two dated events",
        author = "Caracalla",
        year = 217,
        summary = "The years from Caracalla's Constitutio Antoniniana of 212 to his death in 217 come to five, and a short program counts them.",
        details = "Both years are given as commonly stated: most scholars date the Constitutio to 212, and Caracalla is killed in 217. The example uses years only, so the count does not depend on the day of either event.",
        instructions = """
            1. Save the example as years_gap.py.
            2. Run it: python3 years_gap.py
        """,
        example = """
            constitutio = 212    # the Constitutio Antoniniana, as commonly dated
            death = 217          # the death of Caracalla, as commonly dated
            print("years from the Constitutio to the death of Caracalla:", death - constitutio)
        """,
    ),

)
