package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 60 to 69: the years from Columella's farm manual to the placed year of Hero's dioptra. */
val db198Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Columella's farm manual to Hero's dioptra",
        originalTitle = "Years between two dates",
        author = "No single author",
        year = 62,
        summary = "Columella's manual is dated to 60 and the dioptra to its placed year of 62, and a short program counts the years between the two.",
        details = "Both years are the ones given in the records of this database, and the dioptra's year is a placed anchor, so the count is only as firm as that anchor. The program prints 2 years, the difference between 62 and 60.",
        instructions = """
            1. Save the example as years_gap.py.
            2. Run it: python3 years_gap.py
        """,
        example = """
            columella = 60   # the year given for the farm manual
            dioptra = 62     # the placed year of the dioptra, from its eclipse
            print("years from Columella's manual to the dioptra:", dioptra - columella)
        """,
    ),

)
