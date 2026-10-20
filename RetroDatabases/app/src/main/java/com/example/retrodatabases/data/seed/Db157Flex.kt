package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 470 to 480: the years from the deposition of Romulus Augustulus to the murder of Julius Nepos. */
val db157Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the deposition of Romulus Augustulus to the murder of Julius Nepos",
        originalTitle = "Years from the deposition of Romulus Augustulus to the murder of Julius Nepos",
        author = "No single author",
        year = 476,
        summary = "The deposition of Romulus Augustulus in 476 and the murder of Julius Nepos in 480 are four years apart, and a short program counts them.",
        details = "Both years are the commonly given dates, and the count is four years on that reckoning. The example takes the two years as plain numbers, so it tests the subtraction and not the history itself.",
        instructions = """
            1. Save the example as romulus_nepos.py.
            2. Run it: python3 romulus_nepos.py
        """,
        example = """
            romulus = 476   # the deposition of Romulus Augustulus, as commonly dated
            nepos = 480     # the murder of Julius Nepos in Dalmatia, as commonly dated
            print("years from Romulus to Nepos:", nepos - romulus)
        """,
    ),

)
