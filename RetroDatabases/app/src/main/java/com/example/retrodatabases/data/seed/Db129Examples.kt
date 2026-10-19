package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 750 to 760: Pepin the Short's rule of 17 years, 751 to 768, divided at the Donation of 756 and counted in code. */
val db129Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.EXAMPLE,
        section = "Carolingian",
        title = "Pepin's rule of 17 years, 751 to 768, divided at the Donation of 756",
        originalTitle = "Pepin's reign",
        author = "Pepin the Short",
        year = 751,
        summary = "Pepin the Short's rule as king of the Franks ran from 751 to 768, and a short program divides its 17 years at the Donation of 756.",
        details = "The count uses the usual dates of his election in 751 and of his death in 768, and the division falls at the grant of 756 to the papacy. The program only subtracts the dates, so its figures show the arithmetic and do not claim that the grant changed the length of his rule.",
        instructions = """
            1. Save the example as pepin_reign.py.
            2. Run it: python3 pepin_reign.py
        """,
        example = """
            PEPIN_KING = 751    # Pepin is raised to the Frankish kingship, as commonly dated
            DONATION = 756      # the Donation of Pepin to the papacy, as commonly dated
            PEPIN_DEATH = 768   # Pepin dies, as commonly dated
            print("years as king of the Franks, 751 to 768:", PEPIN_DEATH - PEPIN_KING)
            print("years before the Donation:", DONATION - PEPIN_KING)
            print("years after the Donation:", PEPIN_DEATH - DONATION)
        """,
    ),

)
