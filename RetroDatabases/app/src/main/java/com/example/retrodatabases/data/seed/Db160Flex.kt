package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 440 to 450: the years from Leo's accession in 440 to the Robber Council of 449, subtracted in one line. */
val db160Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Nine years from Leo's accession in 440 to the Robber Council of 449",
        originalTitle = "Years between two dated events",
        author = "Pope Leo I and Dioscorus of Alexandria",
        year = 440,
        summary = "Leo became pope in 440 and the Robber Council met in 449, so a one-line program finds that the two dates lie nine years apart.",
        details = "Both years are given as commonly stated, and the example subtracts the earlier year from the later one, which is the only arithmetic it does. The result is a plain difference of two years, so it does not depend on the day of either event.",
        instructions = """
            1. Save the example as leo_robber.py.
            2. Run it: python3 leo_robber.py
        """,
        example = """
            leo_accession = 440    # Leo I becomes pope, as commonly given
            robber_council = 449   # the Robber Council meets, as commonly given
            print("years from Leo's accession to the Robber Council:", robber_council - leo_accession)
        """,
    ),

)
