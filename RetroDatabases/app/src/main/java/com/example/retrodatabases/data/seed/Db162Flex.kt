package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 420 to 430: the years from the Liu Song's founding to the Vandal crossing, and the years from Honorius's death to Augustine's. */
val db162Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the founding of the Liu Song in 420 to the Vandal crossing in 429",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 420,
        summary = "The Liu Song was founded in 420 and the Vandals crossed into Africa in 429, and a short program counts the years between them.",
        details = "Both years are the usual dates of the two events, and the program counts years only, so the answer does not depend on the months. The count is a plain subtraction of two fixed years.",
        instructions = """
            1. Save the example as liu_vandal.py.
            2. Run it: python3 liu_vandal.py
        """,
        example = """
            liu_song = 420   # founding of the Liu Song, as commonly given
            vandals = 429    # the Vandal crossing into Africa, as commonly given
            print("years from the Liu Song to the Vandal crossing:", vandals - liu_song)
        """,
    ),

    entry(
        databaseId = 162,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the death of Honorius in 423 to the death of Augustine in 430",
        originalTitle = "Years between two deaths",
        author = "Honorius and Augustine",
        year = 423,
        summary = "Honorius died in 423 and Augustine died in 430, and a short program counts the years between the two deaths.",
        details = "Both years are the usual dates of the two deaths, and the program counts years only. The span is seven years, which the example prints from the two fixed years.",
        instructions = """
            1. Save the example as honorius_augustine.py.
            2. Run it: python3 honorius_augustine.py
        """,
        example = """
            honorius = 423     # death of Honorius at Ravenna, as commonly given
            augustine = 430    # death of Augustine at Hippo, as commonly given
            print("years from the death of Honorius to the death of Augustine:", augustine - honorius)
        """,
    ),

)
