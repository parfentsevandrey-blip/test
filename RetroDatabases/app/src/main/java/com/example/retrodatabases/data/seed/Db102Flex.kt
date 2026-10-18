package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1020 to 1030: the years between the Ring of the Dove and the death of Ibn Sina. */
val db102Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Ring of the Dove to the death of Ibn Sina",
        originalTitle = "Years from the Ring of the Dove to the death of Ibn Sina",
        author = "Ibn Hazm and Ibn Sina",
        year = 1022,
        summary = "The Ring of the Dove is dated 1022 and Ibn Sina died in 1037, and a short program counts the 15 years between the two dates.",
        details = "Both dates are given as commonly stated, and the program counts whole years, so it checks the arithmetic and not the lives or the books. The Canon of Medicine, dated about 1025, falls between the two dates.",
        instructions = """
            1. Save the example as ring_avicenna.py.
            2. Run it: python3 ring_avicenna.py
        """,
        example = """
            ring_year = 1022           # the Ring of the Dove, as commonly dated
            avicenna_death = 1037      # the death of Ibn Sina, as commonly given
            print("years from the Ring to Ibn Sina's death:", avicenna_death - ring_year)
        """,
    ),

)
