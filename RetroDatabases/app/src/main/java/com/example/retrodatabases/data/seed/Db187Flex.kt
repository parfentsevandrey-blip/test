package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 178: the years from Tatian's harmony of about 170 to the sale of offices under Emperor Ling. */
val db187Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Tatian's Diatessaron to the sale of offices under Ling",
        originalTitle = "Years between two dates",
        author = "No single author",
        year = 178,
        summary = "Tatian's harmony is given the round date 170 and the sale of offices the date 178, so a short program prints the eight years between them.",
        details = "The date 170 is a round figure within a dispute that runs from about 166 to about 180, so the eight years are a rough span and not a measured one. The example is a single line of subtraction over the two commonly given years.",
        instructions = """
            1. Save the example as years_diatessaron.py.
            2. Run it: python3 years_diatessaron.py
        """,
        example = """
            tatian = 170    # Tatian's Diatessaron, as commonly given
            sale = 178      # the sale of offices under Ling, as commonly given
            print("years from the Diatessaron to the sale of offices:", sale - tatian)
        """,
    ),

)
