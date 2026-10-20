package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 550 to 560: the eleven years from Justinian's Pragmatic Sanction of 554 to his death in 565. */
val db149Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.FLEX,
        section = "Byzantine",
        title = "Years from the Pragmatic Sanction of 554 to Justinian's death in 565",
        originalTitle = "Years between two dated events of Justinian's reign",
        author = "Justinian and the chroniclers",
        year = 554,
        summary = "The Pragmatic Sanction of 554 and Justinian's death in 565 are eleven years apart, and a one-line program prints the gap.",
        details = "Both dates are standard in the sources, with the sanction issued in August 554 and the emperor's death in November 565. The program only subtracts the two years, so the result counts the years between the dates and does not weigh anything that happened in between.",
        instructions = """
            1. Save the example as sanction_death.py.
            2. Run it: python3 sanction_death.py
        """,
        example = """
            SANCTION = 554  # Justinian's Pragmatic Sanction for Italy, issued in August 554
            DEATH = 565     # Justinian's death, in November 565
            print("years from the Pragmatic Sanction of 554 to Justinian's death in 565:", DEATH - SANCTION)
        """,
    ),

)
