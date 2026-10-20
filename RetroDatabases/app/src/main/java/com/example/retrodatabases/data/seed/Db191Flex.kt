package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 130 to 140: the years from Theon's book to the seismoscope, counted in code. */
val db191Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Theon's book to Zhang Heng's seismoscope, counted in code",
        originalTitle = "Years between two works",
        author = "Theon of Smyrna and Zhang Heng",
        year = 132,
        summary = "The placed year of Theon's book, 130, and the traditional year of the seismoscope, 132, give a difference of 2 years, and a short program does the subtraction.",
        details = "Both years are as the records give them, one placed and one traditional, so the difference is arithmetic on those two years and says nothing about how far apart the two works were in fact. The example prints the difference, which can be checked by hand from the two years.",
        instructions = """
            1. Save the example as theon_seismoscope.py.
            2. Run it: python3 theon_seismoscope.py
        """,
        example = """
            theon = 130          # placed year of Theon's book
            seismoscope = 132    # traditional year of the seismoscope
            print("years from Theon's book to the seismoscope:", seismoscope - theon)
        """,
        rare = true,
    ),

)
