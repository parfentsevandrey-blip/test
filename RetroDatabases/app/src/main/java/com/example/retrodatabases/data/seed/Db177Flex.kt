package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 270 to 279: the years from Porphyry's Isagoge to the find of the Bamboo Annals. */
val db177Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Isagoge to the find of the Bamboo Annals",
        originalTitle = "Years between two dated events",
        author = "Porphyry and the Bamboo Annals",
        year = 279,
        summary = "The Isagoge is dated about 270 and the Bamboo Annals were found in 279, and a short program counts the nine years between the two dated events.",
        details = "Both years are given as commonly stated, and the example uses plain integers, so the count is exact for the years as written. Accounts differ on the find, and some give 281, which would make the count eleven years.",
        instructions = """
            1. Save the example as isagoge_annals.py.
            2. Run it: python3 isagoge_annals.py
        """,
        example = """
            isagoge = 270   # the Isagoge, about 270, as commonly given
            find = 279      # the find of the Bamboo Annals, as commonly given
            print("years from the Isagoge to the find:", find - isagoge)
        """,
    ),

)
