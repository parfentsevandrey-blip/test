package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 460 to 470: the 605 days between the death of Libius Severus and the accession of Anthemius. */
val db158Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.FLEX,
        section = "Intervals",
        title = "Days from Libius Severus's death to Anthemius's accession, 465 to 467",
        originalTitle = "Days between two dates",
        author = "Libius Severus and Anthemius",
        year = 467,
        summary = "A short program counts the 605 days from the death of Libius Severus on 15 August 465 to the start of Anthemius's western reign on 12 April 467.",
        details = "Both dates are taken as given in the accounts that date them, with the accession taken as the start of the reign. Other accounts date the start of the vacancy to November 465, which would give a shorter interval. The example uses Python's date arithmetic, which matches the Julian calendar for these years because the leap years agree and no century year falls between the dates.",
        instructions = """
            1. Save the example as severus_anthemius.py.
            2. Run it: python3 severus_anthemius.py
        """,
        example = """
            from datetime import date

            severus = date(465, 8, 15)     # Libius Severus's death, as one account dates it
            anthemius = date(467, 4, 12)   # the start of Anthemius's western reign
            print("days from Severus's death to Anthemius's accession:", (anthemius - severus).days)
        """,
    ),

)
