package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 600 to 610: the years from Kanroku's calendar books of 602 to Docho's water mill of 610. */
val db144Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Kanroku's calendar books of 602 to Docho's water mill of 610",
        originalTitle = "Years between two entries of the Nihon Shoki",
        author = "Compilers of the Nihon Shoki",
        year = 602,
        summary = "The calendar books brought by Kanroku in 602 and the water mill built by Docho in 610 are eight years apart, and a short program prints the gap.",
        details = "Both dates come from the same chronicle, so the span compares two entries of one text and does not show that the two events were linked. The program only subtracts the dates, which a reader can check against the two entries.",
        rare = true,
        instructions = """
            1. Save the example as kanroku_docho.py.
            2. Run it: python3 kanroku_docho.py
        """,
        example = """
            KANROKU = 602  # the calendar books brought by Kanroku, as the chronicle dates them
            DOCHO = 610    # the water mill built by Docho, as the chronicle dates it
            print("years from Kanroku's books of 602 to Docho's mill of 610:", DOCHO - KANROKU)
        """,
    ),

)
