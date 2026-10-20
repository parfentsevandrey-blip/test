package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 60 to 69: the Neronia's five-year festival years counted in code from the festival's founding in 60. */
val db198Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.EXAMPLE,
        section = "Festivals",
        title = "The Neronia's five-yearly festival years in the 60s, counted in code",
        originalTitle = "Neronia",
        author = "Nero, who founded the festival",
        year = 60,
        summary = "Nero founded the Neronia, a festival held every fifth year from 60, and a short program lists the festival years that fall in the 60s.",
        details = "The Neronia were founded in 60 as a festival held every five years. The program takes the cycle from the founding, so the years are 60, 65 and 70, and only the first two fall in the 60s. The output lists what the cycle gives and does not claim that each festival was held as scheduled.",
        instructions = """
            1. Save the example as neronia.py.
            2. Run it: python3 neronia.py
        """,
        example = """
            founded = 60     # the Neronia begin in this year, as commonly given
            cycle = 5        # the festival comes round every five years
            years = [founded + step * cycle for step in range(3)]
            in_decade = [year for year in years if 60 <= year <= 69]
            print("festival years:", years)
            print("festival years in the 60s:", in_decade)
            print("count in the 60s:", len(in_decade))
        """,
        rare = true,
    ),

)
