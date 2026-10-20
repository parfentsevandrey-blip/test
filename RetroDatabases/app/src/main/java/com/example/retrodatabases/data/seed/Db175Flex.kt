package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 290 to 300: the years from Zhang Hua's Bowuzhi to the tetrarchy, about three. */
val db175Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Zhang Hua's Bowuzhi to the tetrarchy of Diocletian",
        originalTitle = "Years between two dates",
        author = "Zhang Hua and Diocletian",
        year = 293,
        summary = "The Bowuzhi of about 290 and the tetrarchy of 293 are three years apart, and a short program counts the years between them.",
        details = "The Bowuzhi's date is only approximate, so the count of three years is approximate too, and the example uses 290 as the working year. The tetrarchy's year is the year of the naming of the Caesars, whose most common date is 1 March 293.",
        instructions = """
            1. Save the example as bowuzhi_tetrarchy.py.
            2. Run it: python3 bowuzhi_tetrarchy.py
        """,
        example = """
            bowuzhi = 290     # the Bowuzhi, dated about 290 in the reference works
            tetrarchy = 293   # the tetrarchy, set up when the Caesars were named in 293
            print("years from the Bowuzhi to the tetrarchy:", tetrarchy - bowuzhi)
        """,
        rare = true,
    ),

)
