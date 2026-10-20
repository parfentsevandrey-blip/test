package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 520 to 530: the years of Justinian's reign from 527 to 565, counted in code. */
val db152Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.EXAMPLE,
        section = "Byzantine",
        title = "The years of Justinian's reign from 527 to 565, counted in code",
        originalTitle = "The years of Justinian's reign from 527 to 565, counted in code",
        author = "Byzantine historians",
        year = 527,
        summary = "Justinian's reign ran from 527 to 565, and a short program subtracts the two dates to give its length in years.",
        details = "Justinian became co-emperor with Justin I in April 527, ruled alone after Justin's death that year, and died in 565. The program subtracts the two dates, which gives 38 years.",
        rare = true,
        instructions = """
            1. Save the example as justinian_reign.py.
            2. Run it: python3 justinian_reign.py
        """,
        example = """
            reign_start = 527  # Justinian rules with Justin I from April 527, and alone after Justin's death that year
            reign_end = 565    # Justinian dies in 565
            print("Justinian reigned from", reign_start, "to", reign_end)
            print("years of the reign:", reign_end - reign_start)
        """,
    ),

)
