package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 825: the years from Lothair's school ordinance at Olona in 825 to his death in 855. */
val db122Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Lothair's school ordinance at Olona in 825 to the death of Lothair I in 855",
        originalTitle = "Years between two dated events",
        author = "Lothair I",
        year = 825,
        summary = "Lothair's school ordinance at Olona in 825 and his death in 855 are 30 years apart, and a short program prints that span.",
        details = "Lothair I was king of Italy when the ordinance was issued, and the example uses the two years as commonly given. The span counts whole years between the two dates, so it does not depend on the months of either event.",
        instructions = """
            1. Save the example as olona_years.py.
            2. Run it: python3 olona_years.py
        """,
        example = """
            OLONA = 825          # the school ordinance of the assembly at Olona, May 825
            LOTHAIR_DIED = 855   # the death of Lothair I, as commonly given
            print("years from the Olona ordinance to the death of Lothair I:", LOTHAIR_DIED - OLONA)
        """,
    ),

)
