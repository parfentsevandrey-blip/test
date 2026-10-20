package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 80 to 84: the years between two books of the decade, from Wang Chong's Lunheng to Frontinus's Strategemata. */
val db196Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Wang Chong's Lunheng to Frontinus's Strategemata",
        originalTitle = "Years between two books",
        author = "Wang Chong and Frontinus",
        year = 84,
        summary = "The Lunheng is dated to about 80 and the Strategemata to 84, so a one-line program finds the four years between the two books.",
        details = "Both years are the usual dates given for the two books, and the program makes no claim beyond the subtraction. Accounts differ on the exact year of each work, so the answer checks the arithmetic and does not fix the interval.",
        instructions = """
            1. Save the example as book_years.py.
            2. Run it: python3 book_years.py
        """,
        example = """
            lunheng = 80        # Wang Chong's Lunheng, usually dated to about 80
            strategemata = 84   # Frontinus's Strategemata, dated here to 84
            print("years from the Lunheng to the Strategemata:", strategemata - lunheng)
        """,
    ),

)
