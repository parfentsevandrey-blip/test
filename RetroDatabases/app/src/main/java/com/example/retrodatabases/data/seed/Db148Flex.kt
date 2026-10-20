package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 560 to 570: the years from Justinian's death in 565 to the Sogdian embassy of 568, by one line of arithmetic. */
val db148Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Justinian's death in 565 to the Sogdian embassy of 568",
        originalTitle = "Years between two dated events of the 560s",
        author = "Compiler of the program",
        year = 565,
        summary = "Justinian's death in 565 and the Sogdian embassy of 568 are three years apart, and a short program prints the gap.",
        details = "The two dates come from different accounts, and the program only subtracts the two years, so a reader can check the three against the dates.",
        rare = true,
        instructions = """
            1. Save the example as justinian_sogdian.py.
            2. Run it: python3 justinian_sogdian.py
        """,
        example = """
            JUSTINIAN_DEATH = 565   # Justinian's death
            SOGDIAN_EMBASSY = 568   # the Sogdian embassy at Constantinople
            print("years from Justinian's death to the Sogdian embassy:", SOGDIAN_EMBASSY - JUSTINIAN_DEATH)
        """,
    ),

)
