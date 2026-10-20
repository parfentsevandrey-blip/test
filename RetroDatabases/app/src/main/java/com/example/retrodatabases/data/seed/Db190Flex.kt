package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 140 to 150: the years from Marcion's canon to An Shigao's move to Luoyang, counted in one line of arithmetic. */
val db190Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Marcion's canon to An Shigao at Luoyang",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 148,
        summary = "Marcion's canon is dated about 140 and An Shigao's move to Luoyang to 148, and a short program counts the eight years between them.",
        details = "Both dates are used as commonly stated: 140 is a round year for Marcion's canon, and 148 is the year that accounts give for An Shigao's move to Luoyang. The example subtracts one year from the other, so the result can be checked by hand.",
        instructions = """
            1. Save the example as marcion_shigao.py.
            2. Run it: python3 marcion_shigao.py
        """,
        example = """
            print("years from Marcion's canon to An Shigao at Luoyang:", 148 - 140)
        """,
    ),

)
