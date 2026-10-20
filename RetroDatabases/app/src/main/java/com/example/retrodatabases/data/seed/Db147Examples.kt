package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 570 to 580: the reign of Khosrow I of Persia, from his accession in 531 to his death in 579, counted in code. */
val db147Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.EXAMPLE,
        section = "Persian",
        title = "The reign of Khosrow I of Persia from 531 to 579, 48 years counted in code",
        originalTitle = "The reign of Khosrow I of Persia from 531 to 579, 48 years counted in code",
        author = "Persian and Arab chroniclers",
        year = 579,
        summary = "Khosrow I, the Sasanian king, came to the throne in 531 and died in 579, a reign of 48 years that a short program works out from the two dates.",
        details = "The two dates are the usual reckoning of his reign, and the count is the plain difference between them, so it ignores the months within each year. The year 579 is the end of the reign, which is when the count closes.",
        instructions = """
            1. Save the example as khosrow_reign.py.
            2. Run it: python3 khosrow_reign.py
        """,
        example = """
            ACCESSION = 531  # Khosrow I succeeds Kavadh I
            DEATH = 579      # Khosrow I dies
            print("reign of Khosrow I:", DEATH - ACCESSION, "years")
        """,
    ),

)
